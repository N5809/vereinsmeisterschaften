package de.dlrg.vereinsmeisterschaft.service;

import com.fasterxml.jackson.databind.*;
import de.dlrg.vereinsmeisterschaft.model.BackupModels.*;
import de.dlrg.vereinsmeisterschaft.model.Models.*;
import de.dlrg.vereinsmeisterschaft.repository.RaceRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Versionierte Anwendungsdaten als JSON; niemals SQL aus einer Datei ausführen. */
@Service
public class BackupService {
    private static final String FORMAT="vereinsmeisterschaft-backup";
    private static final int MAX_BYTES=20*1024*1024,MAX_ITEMS=20000;
    private final RaceRepository repo;
    private final RaceService races;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    public BackupService(RaceRepository repo,RaceService races,TransactionTemplate tx,ObjectMapper json) {
        this.repo=repo;this.races=races;this.tx=tx;this.json=json;
    }
    private Counts counts() {
        return new Counts(count("participant"),count("heat"),repo.db().queryForObject("SELECT COUNT(*) FROM lane_result WHERE result_status<>'PENDING'",Long.class),count("discipline"),count("competition_class"));
    }
    private long count(String table) {return repo.db().queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    private boolean allowed() {try{races.requireDataChangeAllowed();return true;}catch(IllegalStateException e){return false;}}
    public DataSummary summary() {synchronized(races){Counts c=counts();return new DataSummary(c,c.empty(),allowed());}}
    public BackupFile export(Selection requested) {
        if(requested==null || !requested.any())throw new IllegalArgumentException("Bitte mindestens einen Inhalt auswählen.");
        Selection selected=requested.withDependencies();
        synchronized(races) {
            List<StoredParticipant> people=selected.participants()?repo.db().query("SELECT * FROM participant ORDER BY id",(r,n)->new StoredParticipant(
                    new Participant(r.getString("id"),r.getString("first_name"),r.getString("last_name"),r.getInt("birth_year"),r.getString("category"),r.getString("training_group")),r.getBoolean("deleted"))):List.of();
            Set<String> active=new HashSet<>();people.stream().filter(p->!p.deleted()).forEach(p->active.add(p.person().id()));
            List<Heat> heats=new ArrayList<>();
            if(selected.heats()) for(Heat h:repo.heats()) {
                if(!selected.results() && Set.of("ABORTED","FALSE_START").contains(h.status()))continue;
                List<Lane> lanes=selected.results()?h.lanes():h.lanes().stream().filter(l->active.contains(l.participantId())).map(l->new Lane(l.lane(),l.participantId(),l.firstName(),l.lastName(),l.birthYear(),l.category(),l.trainingGroup(),"PENDING",null,null,null,false,"")).toList();
                if(!selected.results() && lanes.isEmpty())continue;
                String status=!selected.results()||h.status().equals("ARMED")?"PLANNED":h.status().equals("RUNNING")?"INTERRUPTED":h.status();
                heats.add(new Heat(h.id(),h.number(),h.discipline(),h.label(),status,selected.results()?h.startMs():null,
                        selected.results()?h.bootId():null,selected.results()?h.startQualityMs():null,selected.results()?h.falseStartOf():null,lanes));
            }
            List<String> disciplines=selected.disciplines()?repo.disciplines().stream().map(Discipline::name).toList():List.of();
            List<NewCompetitionClass> classes=selected.classes()?repo.competitionClasses().stream().map(c->new NewCompetitionClass(c.trainingGroup(),c.category())).toList():List.of();
            return new BackupFile(FORMAT,1,Instant.now().toString(),selected,people,List.copyOf(heats),disciplines,classes);
        }
    }
    public byte[] write(Selection selected) {
        try {
            byte[] bytes=json.writerWithDefaultPrettyPrinter().writeValueAsBytes(export(selected));
            if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Die Sicherung überschreitet 20 MB. Weniger Inhalte auswählen.");
            return bytes;
        } catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalStateException("Sicherung konnte nicht erstellt werden.");}
    }
    public BackupFile read(byte[] bytes) {
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Bitte eine Sicherungsdatei bis 20 MB auswählen.");
        BackupFile file;
        try {
            file=json.readerFor(BackupFile.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
        } catch(Exception e){throw new IllegalArgumentException("Keine lesbare Sicherungsdatei dieses Programms. Bitte eine exportierte JSON-Datei auswählen.");}
        validate(file);return file;
    }
    public BackupPreview preview(byte[] bytes) {
        BackupFile file=read(bytes);
        synchronized(races){return new BackupPreview(file.exportedAt(),file.selection(),contents(file),counts().empty()&&allowed());}
    }
    private Counts contents(BackupFile f) {
        return new Counts(f.participants().size(),f.heats().size(),f.heats().stream().flatMap(h->h.lanes().stream()).filter(l->!l.status().equals("PENDING")).count(),f.disciplines().size(),f.classes().size());
    }
    public void reset(String confirmation) {
        if(!"ALLES LÖSCHEN".equals(confirmation))throw new IllegalArgumentException("Zur Bestätigung exakt ALLES LÖSCHEN eingeben.");
        synchronized(races) {
            races.requireDataChangeAllowed();
            tx.executeWithoutResult(s->{
                for(String table:List.of("processed_event","lane_result","heat","participant","discipline","competition_class","audit_log"))repo.db().update("DELETE FROM "+table);
                newDataId();
            });
            races.dataChanged();
        }
    }
    public void restore(byte[] bytes,String confirmation) {
        if(!"WIEDERHERSTELLEN".equals(confirmation))throw new IllegalArgumentException("Zur Bestätigung exakt WIEDERHERSTELLEN eingeben.");
        BackupFile f=read(bytes);
        synchronized(races) {
            races.requireDataChangeAllowed();
            if(!counts().empty())throw new IllegalStateException("Wiederherstellung nur bei leerem Datenbestand. Vorhandene Daten zuerst sichern und über Alles löschen zurücksetzen.");
            tx.executeWithoutResult(s->{
                f.classes().forEach(c->repo.ensureClass(c.trainingGroup(),c.category()));
                f.disciplines().forEach(repo::insertDiscipline);
                for(StoredParticipant stored:f.participants()) {
                    repo.insert(stored.person());
                    if(stored.deleted())repo.db().update("UPDATE participant SET deleted=TRUE WHERE id=?",stored.person().id());
                }
                for(Heat h:f.heats()) {
                    String status=h.status().equals("ARMED")?"PLANNED":h.status().equals("RUNNING")?"INTERRUPTED":h.status();
                    repo.db().update("""
                        INSERT INTO heat(id,heat_number,discipline,label,heat_status,start_ms,server_boot_id,start_quality_ms,false_start_of)
                        VALUES (?,?,?,?,?,?,?,?,?)
                        """,h.id(),h.number(),h.discipline(),h.label(),status,h.startMs(),h.bootId(),h.startQualityMs(),h.falseStartOf());
                    for(Lane l:h.lanes())repo.db().update("""
                        INSERT INTO lane_result(heat_id,lane_number,participant_id,first_name,last_name,birth_year,category,training_group,
                        result_status,stop_ms,elapsed_ms,quality_ms,needs_review,note) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """,h.id(),l.lane(),l.participantId(),l.firstName(),l.lastName(),l.birthYear(),l.category(),l.trainingGroup(),l.status(),l.stopMs(),l.elapsedMs(),l.qualityMs(),l.needsReview(),l.note());
                }
                repo.db().update("DELETE FROM audit_log");repo.audit("admin","Sicherung vom "+f.exportedAt()+" wiederhergestellt.");
                newDataId();
            });
            races.dataChanged();
        }
    }
    private void newDataId() {repo.db().update("MERGE INTO app_meta(meta_key,meta_value) KEY(meta_key) VALUES ('data_id',?)",UUID.randomUUID().toString());}
    private void validate(BackupFile f) {
        if(f==null||!FORMAT.equals(f.format())||f.version()!=1)throw new IllegalArgumentException("Unbekanntes Sicherungsformat oder nicht unterstützte Version.");
        try{Instant.parse(f.exportedAt());}catch(Exception e){throw new IllegalArgumentException("Datum der Sicherung ist ungültig.");}
        if(f.selection()==null||!f.selection().any()||!f.selection().equals(f.selection().withDependencies()))bad("Inhalte und Abhängigkeiten");
        bounded(f.participants(),"Teilnehmer");bounded(f.heats(),"Läufe");bounded(f.disciplines(),"Disziplinen");bounded(f.classes(),"Wertungsklassen");
        Selection s=f.selection();
        if(!s.participants()&&!f.participants().isEmpty()||!s.heats()&&!f.heats().isEmpty()||!s.disciplines()&&!f.disciplines().isEmpty()||!s.classes()&&!f.classes().isEmpty())bad("Auswahl der Inhalte");
        Set<List<String>> classes=new HashSet<>();
        for(NewCompetitionClass c:f.classes()) {
            if(c==null)bad("Wertungsklasse");
            RaceService.text(c.trainingGroup(),"Trainingsgruppe",100,false);RaceService.text(c.category(),"Wertung",60,false);
            if(c.trainingGroup()==null||c.category()==null||!classes.add(List.of(c.trainingGroup(),c.category())))bad("Doppelte oder ungültige Wertungsklasse");
        }
        Set<String> disciplines=new HashSet<>();
        for(String d:f.disciplines())if(d==null||!d.equals(RaceService.text(d,"Disziplin",120,true))||!disciplines.add(d.toLowerCase(Locale.ROOT)))bad("Doppelte oder ungültige Disziplin");
        Map<String,StoredParticipant> people=new HashMap<>();
        for(StoredParticipant p:f.participants()) {
            if(p==null||p.person()==null||!RaceService.validate(p.person()).equals(p.person()))bad("Teilnehmerdaten");
            if(people.put(p.person().id(),p)!=null||!classes.contains(List.of(p.person().trainingGroup(),p.person().category())))bad("Teilnehmer-ID oder zugehörige Wertungsklasse");
        }
        Map<String,Heat> heats=new HashMap<>();Set<Long> numbers=new HashSet<>();
        for(Heat h:f.heats()) {
            if(h==null)bad("Lauf");uuid(h.id());
            if(h.number()<1||h.number()>1_000_000_000||!numbers.add(h.number())||heats.put(h.id(),h)!=null)bad("Laufnummer oder Lauf-ID");
            if(h.discipline()==null||!disciplines.contains(h.discipline().toLowerCase(Locale.ROOT)))bad("Disziplin eines Laufes fehlt");
            RaceService.text(h.label(),"Laufbezeichnung",120,false);
            if(h.label()==null||h.status()==null||!Set.of("PLANNED","ARMED","RUNNING","FINISHED","ABORTED","INTERRUPTED","FALSE_START").contains(h.status()))bad("Laufstatus");
            finite(h.startMs(),false);finite(h.startQualityMs(),true);
            if(h.bootId()!=null)uuid(h.bootId());
            bounded(h.lanes(),"Bahnen");if(h.lanes().size()>3||h.lanes().isEmpty()&&!Set.of("ABORTED","FALSE_START").contains(h.status()))bad("Bahnbelegung");
            Set<Integer> lanes=new HashSet<>();Set<String> swimmers=new HashSet<>();
            for(Lane l:h.lanes()) {
                if(l==null||l.lane()<1||l.lane()>3||!lanes.add(l.lane())||!swimmers.add(l.participantId())||!people.containsKey(l.participantId()))bad("Bahn oder Teilnehmer eines Laufes");
                Participant snapshot=new Participant(l.participantId(),l.firstName(),l.lastName(),l.birthYear(),l.category(),l.trainingGroup());
                if(!RaceService.validate(snapshot).equals(snapshot)||!classes.contains(List.of(l.trainingGroup(),l.category())))bad("Teilnehmerdaten eines Laufes");
                if(l.status()==null||!Set.of("PENDING","OK","DNS","DNF","DSQ").contains(l.status()))bad("Ergebnisstatus");
                if(l.status().equals("OK")?(l.elapsedMs()==null||l.elapsedMs()<=0||l.elapsedMs()>86_400_000):l.elapsedMs()!=null)bad("Ergebniszeit");
                finite(l.stopMs(),false);finite(l.qualityMs(),true);RaceService.text(l.note(),"Hinweis",500,false);
                if(l.note()==null)bad("Hinweis");
                if(Set.of("PLANNED","ARMED").contains(h.status())&&(!l.status().equals("PENDING")||l.stopMs()!=null||l.qualityMs()!=null||l.needsReview()||!l.note().isEmpty()||people.get(l.participantId()).deleted()))bad("Geplanter Lauf enthält Ergebnisdaten oder gelöschte Teilnehmer");
                if(h.status().equals("FINISHED")&&l.status().equals("PENDING"))bad("Beendeter Lauf enthält offene Bahn");
            }
            if(!s.results()&&(!h.status().equals("PLANNED")||h.startMs()!=null||h.startQualityMs()!=null||h.bootId()!=null||h.falseStartOf()!=null))bad("Ergebnisse wurden nicht ausgewählt");
        }
        for(Heat h:f.heats())if(h.falseStartOf()!=null) {
            Heat source=heats.get(h.falseStartOf());
            if(source==null||!source.status().equals("FALSE_START")||source.number()>=h.number())bad("Verknüpfung einer Fehlstart-Wiederholung");
        }
    }
    private static void bounded(List<?> values,String field) {if(values==null||values.size()>MAX_ITEMS)bad(field);}
    private static void uuid(String value) {try{if(value==null||!UUID.fromString(value).toString().equals(value))bad("ID");}catch(IllegalArgumentException e){bad("ID");}}
    private static void finite(Double n,boolean quality) {if(n!=null&&(!Double.isFinite(n)||n<0||quality&&n>20000))bad("Zeitstempel oder Zeitabgleich");}
    private static void bad(String field) {throw new IllegalArgumentException("Ungültige Sicherung: "+field+". Es wurde nichts eingelesen.");}
}

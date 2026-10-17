package de.dlrg.vereinsmeisterschaft.service;

import de.dlrg.vereinsmeisterschaft.model.Models.*;
import de.dlrg.vereinsmeisterschaft.repository.RaceRepository;
import jakarta.annotation.PostConstruct;
import java.time.Year;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Alle Zustandswechsel sind serialisiert und ihre Datenbankaenderungen atomar. */
@Service
public class RaceService {
    private final RaceRepository repo;
    private final TransactionTemplate tx;
    private final ServerClock clock;
    private final double maxRoundtrip;
    private final int maxSeconds;
    private String activeId;
    private String dataId;
    private final Map<String,Presence> devices=new HashMap<>();
    private static class Presence {
        String connection; double seen; Double rtt; boolean valid; boolean ready;
    }
    public RaceService(RaceRepository repo,TransactionTemplate tx,ServerClock clock,
                       @Value("${app.max-roundtrip-ms:100}") double maxRoundtrip,
                       @Value("${app.max-race-seconds:1800}") int maxSeconds) {
        this.repo=repo; this.tx=tx; this.clock=clock; this.maxRoundtrip=maxRoundtrip; this.maxSeconds=maxSeconds;
    }
    @PostConstruct
    public void recover() {
        tx.executeWithoutResult(s->{
            if(repo.db().queryForObject("SELECT COUNT(*) FROM app_meta WHERE meta_key='data_id'",Integer.class)==0)
                repo.db().update("INSERT INTO app_meta(meta_key,meta_value) VALUES ('data_id',?)",UUID.randomUUID().toString());
            repo.db().update("""
                INSERT INTO competition_class(training_group,category)
                SELECT DISTINCT p.training_group,p.category FROM participant p
                WHERE NOT EXISTS (SELECT 1 FROM competition_class c WHERE c.training_group=p.training_group AND c.category=p.category)
                """);
            // Freitext-Disziplinen aus älteren Versionen einmalig in den Katalog übernehmen.
            for(String name:repo.db().queryForList("SELECT DISTINCT discipline FROM heat ORDER BY discipline",String.class))
                if(repo.disciplineName(name).isEmpty()) repo.insertDiscipline(name);
            int n=repo.db().update("UPDATE heat SET heat_status='INTERRUPTED' WHERE heat_status='RUNNING'");
            repo.db().update("UPDATE heat SET heat_status='PLANNED' WHERE heat_status='ARMED'");
            if(n>0) repo.audit("system",n+" laufende Läufe durch Serverneustart unterbrochen. Zeiten prüfen.");
        });
        dataId=repo.db().queryForObject("SELECT meta_value FROM app_meta WHERE meta_key='data_id'",String.class);
    }
    public synchronized List<Participant> participants() { return repo.participants(); }
    public synchronized List<Heat> heats() { return repo.heats(); }
    public synchronized List<Map<String,Object>> audit() { return repo.audit(); }
    public synchronized List<Discipline> disciplines() { return repo.disciplines(); }
    public synchronized void addDiscipline(String value) {
        String name=text(value,"Disziplin mit Strecke",120,true);
        if(repo.disciplineName(name).isPresent()) throw new IllegalArgumentException("Diese Disziplin ist bereits vorhanden.");
        tx.executeWithoutResult(s->{repo.insertDiscipline(name);repo.audit("admin","Disziplin angelegt: "+name);});
    }
    public synchronized void deleteDiscipline(String value) {
        String name=repo.disciplineName(text(value,"Disziplin",120,true))
                .orElseThrow(()->new IllegalArgumentException("Disziplin nicht gefunden."));
        if(repo.db().queryForObject("SELECT COUNT(*) FROM heat WHERE LOWER(discipline)=?",Integer.class,name.toLowerCase(Locale.ROOT))>0)
            throw new IllegalStateException("Diese Disziplin wird bereits in Läufen verwendet und bleibt zur Nachvollziehbarkeit erhalten.");
        tx.executeWithoutResult(s->{repo.db().update("DELETE FROM discipline WHERE name=?",name);repo.audit("admin","Disziplin gelöscht: "+name);});
    }
    public synchronized List<GroupRanking> rankings() {
        return rankings("classes");
    }
    public synchronized List<GroupRanking> rankings(String mode) {
        if(!Set.of("classes","groups").contains(mode)) throw new IllegalArgumentException("Unbekannte Ergebnisansicht.");
        return StandingsCalculator.calculate(repo.participants(),repo.heats(),repo.disciplines(),mode);
    }
    public synchronized List<DisciplineRanking> disciplineRankings() {
        return StandingsCalculator.byDiscipline(repo.participants(),repo.heats(),repo.disciplines());
    }
    public synchronized List<CompetitionClass> competitionClasses() {return repo.competitionClasses();}
    public synchronized void addCompetitionClass(NewCompetitionClass c) {
        String group=text(c.trainingGroup(),"Trainingsgruppe",100,true),category=text(c.category(),"Wertung",60,true);
        if(repo.classExists(group,category)) throw new IllegalArgumentException("Diese Wertungsklasse ist bereits vorhanden.");
        tx.executeWithoutResult(s->{repo.ensureClass(group,category);repo.audit("admin","Wertungsklasse angelegt: "+group+" – "+category);});
    }
    public synchronized void deleteCompetitionClass(String group,String category) {
        if(!repo.classExists(group,category)) throw new IllegalArgumentException("Wertungsklasse nicht gefunden.");
        int people=repo.db().queryForObject("SELECT COUNT(*) FROM participant WHERE training_group=? AND category=?",Integer.class,group,category);
        if(people>0) throw new IllegalStateException("Diese Wertungsklasse ist bereits Teilnehmern zugeordnet und bleibt erhalten.");
        tx.executeWithoutResult(s->{repo.db().update("DELETE FROM competition_class WHERE training_group=? AND category=?",group,category);repo.audit("admin","Wertungsklasse gelöscht: "+group+" – "+category);});
    }
    public synchronized void requireDataChangeAllowed() {
        if(activeId!=null && Set.of("ARMED","RUNNING").contains(repo.heat(activeId).status()))
            throw new IllegalStateException("Zuerst den aktivierten oder laufenden Lauf abschließen oder abbrechen.");
    }
    public synchronized void dataChanged() {
        activeId=null;devices.values().forEach(d->d.ready=false);clock.renewBootId();
        dataId=repo.db().queryForObject("SELECT meta_value FROM app_meta WHERE meta_key='data_id'",String.class);
    }

    public static Participant validate(Participant p) {
        if(p==null) throw new IllegalArgumentException("Teilnehmer fehlt.");
        String id=text(p.id(),"Teilnehmer-ID",40,true);
        if(!id.matches("[\\p{L}\\p{N}_-]+")) throw new IllegalArgumentException("Teilnehmer-ID: nur Buchstaben, Zahlen, _ und - verwenden.");
        if(p.birthYear()<1900 || p.birthYear()>Year.now().getValue()) throw new IllegalArgumentException("Ungültiger Jahrgang für "+id+".");
        return new Participant(id,text(p.firstName(),"Vorname",100,true),text(p.lastName(),"Nachname",100,true),
                p.birthYear(),text(p.category(),"Wertung",60,false),text(p.trainingGroup(),"Trainingsgruppe",100,false));
    }
    public synchronized int addParticipants(List<Participant> incoming) {
        if(incoming==null || incoming.isEmpty() || incoming.size()>2000) throw new IllegalArgumentException("Bitte 1 bis 2000 Teilnehmer importieren.");
        List<Participant> people=incoming.stream().map(RaceService::validate).toList();
        Set<String> ids=new HashSet<>();
        for(Participant p:people) {
            if(!ids.add(p.id())) throw new IllegalArgumentException("ID doppelt in der Datei: "+p.id());
            if(repo.participantIdUsed(p.id())) throw new IllegalArgumentException("ID bereits vergeben (auch gelöschte Teilnehmer): "+p.id()+". Bitte eine neue ID verwenden. Es wurde nichts importiert.");
        }
        tx.executeWithoutResult(s->{people.forEach(p->{repo.ensureClass(p.trainingGroup(),p.category());repo.insert(p);}); repo.audit("admin",people.size()+" Teilnehmer hinzugefügt.");});
        return people.size();
    }
    public synchronized void deleteParticipant(String id) {
        Participant p=repo.participant(id).orElseThrow(()->new IllegalArgumentException("Teilnehmer nicht gefunden oder bereits gelöscht."));
        List<Long> active=repo.db().queryForList("""
            SELECT h.heat_number FROM heat h JOIN lane_result l ON l.heat_id=h.id
            WHERE l.participant_id=? AND h.heat_status IN ('ARMED','RUNNING') ORDER BY h.heat_number
            """,Long.class,id);
        if(!active.isEmpty()) throw new IllegalStateException("Teilnehmer ist in Lauf "+active.getFirst()+" aktiviert oder in der Zeitnahme. Zuerst diesen Lauf abschließen oder abbrechen.");
        tx.executeWithoutResult(s->{
            List<String> planned=repo.db().queryForList("""
                SELECT h.id FROM heat h JOIN lane_result l ON l.heat_id=h.id
                WHERE l.participant_id=? AND h.heat_status='PLANNED'
                """,String.class,id);
            repo.db().update("""
                DELETE FROM lane_result WHERE participant_id=? AND heat_id IN
                (SELECT id FROM heat WHERE heat_status='PLANNED')
                """,id);
            int cancelled=0;
            for(String heatId:planned) {
                cancelled+=repo.db().update("""
                    UPDATE heat SET heat_status='ABORTED' WHERE id=? AND heat_status='PLANNED'
                    AND NOT EXISTS (SELECT 1 FROM lane_result WHERE heat_id=?)
                    """,heatId,heatId);
            }
            // Historische Ergebnisse und ihre Fremdschlüssel bleiben gültig; IDs werden nicht neu vergeben.
            repo.db().update("UPDATE participant SET deleted=TRUE WHERE id=?",id);
            repo.audit("admin","Teilnehmer gelöscht: "+p.firstName()+" "+p.lastName()+" ("+id+"). Aus "+planned.size()+" geplanten Läufen entfernt; "+cancelled+" dadurch leere Läufe abgebrochen. Vorhandene Ergebnisse bleiben erhalten.");
        });
    }
    public synchronized Heat createHeat(NewHeat request) {
        if(request==null) throw new IllegalArgumentException("Lauf fehlt.");
        String discipline=repo.disciplineName(text(request.discipline(),"Disziplin (inkl. Strecke)",120,true))
                .orElseThrow(()->new IllegalArgumentException("Bitte zuerst unter Disziplinen eine Disziplin anlegen und auswählen."));
        String label=text(request.label(),"Bezeichnung",120,false);
        List<LaneAssignment> assignments=request.lanes();
        if(assignments==null || assignments.isEmpty() || assignments.size()>3) throw new IllegalArgumentException("Bitte 1 bis 3 Bahnen besetzen.");
        Set<Integer> numbers=new HashSet<>(); Set<String> ids=new HashSet<>();
        Map<Integer,Participant> people=new TreeMap<>();
        for(LaneAssignment a:assignments) {
            if(a==null || a.lane()<1 || a.lane()>3 || !numbers.add(a.lane()) || !ids.add(a.participantId()))
                throw new IllegalArgumentException("Bahn oder Teilnehmer doppelt bzw. ungültig.");
            people.put(a.lane(),repo.participant(a.participantId()).orElseThrow(()->new IllegalArgumentException("Teilnehmer fehlt: "+a.participantId())));
        }
        String id=UUID.randomUUID().toString();
        tx.executeWithoutResult(s->{
            // Explizite Nummern beginnen nach einem vollständigen Zurücksetzen wieder bei 1.
            Long number=repo.db().queryForObject("SELECT COALESCE(MAX(heat_number),0)+1 FROM heat",Long.class);
            repo.db().update("INSERT INTO heat(id,heat_number,discipline,label,heat_status) VALUES (?,?,?,?,'PLANNED')",id,number,discipline,label);
            people.forEach((lane,p)->repo.db().update("""
                INSERT INTO lane_result(heat_id,lane_number,participant_id,first_name,last_name,birth_year,category,training_group)
                VALUES (?,?,?,?,?,?,?,?)
                """,id,lane,p.id(),p.firstName(),p.lastName(),p.birthYear(),p.category(),p.trainingGroup()));
            repo.audit("admin","Lauf angelegt: "+id+" / "+discipline);
        });
        return repo.heat(id);
    }
    public synchronized void arm(String id) {
        if(activeId!=null && Set.of("ARMED","RUNNING").contains(repo.heat(activeId).status()))
            throw new IllegalStateException("Zuerst den aktiven Lauf abschließen oder abbrechen.");
        Heat heat=repo.heat(id);
        if(!heat.status().equals("PLANNED")) throw new IllegalStateException("Nur geplante Läufe können aktiviert werden.");
        tx.executeWithoutResult(s->{
            repo.db().update("UPDATE heat SET heat_status='ARMED',server_boot_id=? WHERE id=?",clock.bootId(),id);
            repo.audit("admin","Lauf "+heat.number()+" aktiviert.");
        });
        activeId=id; devices.values().forEach(d->d.ready=false);
    }
    public synchronized void abort(String id) {
        Heat heat=repo.heat(id);
        if(!Set.of("ARMED","RUNNING").contains(heat.status())) throw new IllegalStateException("Dieser Lauf kann nicht abgebrochen werden.");
        tx.executeWithoutResult(s->{
            repo.db().update("UPDATE heat SET heat_status='ABORTED' WHERE id=?",id);
            repo.audit("admin","Lauf "+heat.number()+" abgebrochen; vorhandene Zeiten bleiben erhalten.");
        });
        devices.values().forEach(d->d.ready=false);
    }
    public synchronized Heat repeat(String id) {
        Heat h=repo.heat(id);
        List<LaneAssignment> lanes=h.lanes().stream().filter(l->repo.participant(l.participantId()).isPresent())
                .map(l->new LaneAssignment(l.lane(),l.participantId())).toList();
        if(lanes.isEmpty()) throw new IllegalStateException("Dieser Lauf hat keine angemeldeten Teilnehmer mehr und kann nicht kopiert werden.");
        return createHeat(new NewHeat(h.discipline(),h.label(),lanes));
    }
    public synchronized void correct(String id,int lane,Correction c) {
        Heat h=repo.heat(id);
        if(!Set.of("RUNNING","FINISHED","INTERRUPTED").contains(h.status())) throw new IllegalStateException("Korrekturen sind in laufenden, beendeten oder unterbrochenen Läufen möglich.");
        if(c==null || !Set.of("OK","DNS","DNF","DSQ").contains(c.status())) throw new IllegalArgumentException("Status muss OK, DNS, DNF oder DSQ sein.");
        String reason=text(c.reason(),"Korrekturgrund",450,true);
        if(c.status().equals("OK") && (c.elapsedMs()==null || c.elapsedMs()<=0 || c.elapsedMs()>maxSeconds*1000L))
            throw new IllegalArgumentException("Eine gültige Zeit in Millisekunden ist erforderlich.");
        Lane old=h.lanes().stream().filter(l->l.lane()==lane).findFirst().orElseThrow(()->new IllegalArgumentException("Bahn nicht besetzt."));
        Long elapsed=c.status().equals("OK")?c.elapsedMs():null;
        tx.executeWithoutResult(s->{
            repo.db().update("UPDATE lane_result SET result_status=?,elapsed_ms=?,needs_review=FALSE,note=? WHERE heat_id=? AND lane_number=?",c.status(),elapsed,"Manuell: "+reason,id,lane);
            repo.audit("admin","Lauf "+h.number()+", Bahn "+lane+": "+old.status()+" / "+old.elapsedMs()+" ms -> "+c.status()+" / "+elapsed+" ms. Grund: "+reason);
            finishIfComplete(id);
        });
    }
    private void finishIfComplete(String id) {
        Integer count=repo.db().queryForObject("SELECT COUNT(*) FROM lane_result WHERE heat_id=? AND result_status='PENDING'",Integer.class,id);
        if(count!=null && count==0) repo.db().update("UPDATE heat SET heat_status='FINISHED' WHERE id=? AND heat_status IN ('RUNNING','INTERRUPTED')",id);
    }

    public synchronized boolean connect(String role,String connection) {
        Presence old=devices.get(role);
        if(old!=null && clock.now()-old.seen<8000) return false;
        Presence d=new Presence(); d.connection=connection; d.seen=clock.now(); devices.put(role,d); return true;
    }
    public synchronized void disconnect(String role,String connection) {
        Presence d=devices.get(role);
        if(d!=null && Objects.equals(d.connection,connection)) devices.remove(role);
    }
    public synchronized void heartbeat(String role,String connection,Double rtt,boolean valid) {
        Presence d=requireConnection(role,connection);
        d.seen=clock.now(); d.rtt=rtt!=null && Double.isFinite(rtt) && rtt>=0?rtt:null; d.valid=valid;
        // Eine aktive Messung behaelt ihren eingefrorenen Abgleich auch bei Funkproblemen.
        if(activeId!=null && repo.heat(activeId).status().equals("ARMED") && !healthy(d)) d.ready=false;
    }
    private Presence requireConnection(String role,String connection) {
        Presence d=devices.get(role);
        if(d==null || !Objects.equals(d.connection,connection)) throw new IllegalStateException("Diese Verbindung ist nicht mehr aktiv.");
        return d;
    }
    public synchronized void ready(String role,String connection,String heatId,boolean ready) {
        Presence d=requireConnection(role,connection);
        Heat h=requireActive(heatId);
        if(!h.status().equals("ARMED")) throw new IllegalStateException("Bereitschaft nur vor dem Start ändern.");
        if(role.equals("admin")) throw new IllegalArgumentException("Die Verwaltung ist kein Zeitnehmer.");
        if(role.startsWith("lane") && h.lanes().stream().noneMatch(l->role.equals("lane"+l.lane())))
            throw new IllegalArgumentException("Diese Bahn ist nicht besetzt.");
        if(ready && !healthy(d)) throw new IllegalStateException("Verbindung/Zeitabgleich noch nicht bereit.");
        d.ready=ready;
    }
    private boolean healthy(Presence d) {
        return d!=null && clock.now()-d.seen<6000 && d.valid && d.rtt!=null && d.rtt<=maxRoundtrip;
    }
    private Heat requireActive(String id) {
        if(id==null || !Objects.equals(id,activeId)) throw new IllegalStateException("Dieser Lauf ist nicht aktiv.");
        return repo.heat(id);
    }
    public synchronized void timing(String role,String connection,TimingEvent e) {
        requireConnection(role,connection);
        if(e==null || e.eventId()==null) throw new IllegalArgumentException("Ereignis-ID fehlt.");
        try { UUID.fromString(e.eventId()); } catch(Exception ex) { throw new IllegalArgumentException("Ungültige Ereignis-ID."); }
        // Bereits gespeicherte Ereignisse werden bestaetigt, aber nie erneut angewendet.
        var existing=repo.db().queryForList("SELECT * FROM processed_event WHERE event_id=?",e.eventId());
        if(!existing.isEmpty()) {
            var x=existing.getFirst();
            if(!Objects.equals(x.get("ACTOR"),role) || !Objects.equals(x.get("HEAT_ID"),e.heatId()) || !Objects.equals(x.get("EVENT_TYPE"),e.type()))
                throw new IllegalArgumentException("Ereignis-ID gehört zu einer anderen Aktion.");
            return;
        }
        if(!clock.bootId().equals(e.bootId())) throw new IllegalStateException("Server wurde neu gestartet. Zeit nur nach manueller Prüfung übernehmen.");
        if(!Double.isFinite(e.atMs()) || !Double.isFinite(e.qualityMs()) || e.qualityMs()<0 || e.qualityMs()>10000)
            throw new IllegalArgumentException("Ungültiger Zeitstempel/Zeitabgleich.");
        if("FALSE_START".equals(e.type())) { falseStart(role,e); return; }
        Heat h=requireActive(e.heatId());
        double now=clock.now();
        if(e.atMs()>now+maxRoundtrip || now-e.atMs()>maxSeconds*1000L)
            throw new IllegalArgumentException("Zeitstempel liegt außerhalb des zulässigen Bereichs.");
        if(e.type().equals("START")) {
            if(!role.equals("starter")) throw new IllegalArgumentException("Nur der Starter darf starten.");
            if(!h.status().equals("ARMED")) throw new IllegalStateException("Lauf ist nicht startbereit.");
            if(now-e.atMs()>5000 || e.qualityMs()>maxRoundtrip/2 || e.needsReview()) throw new IllegalStateException("Startsignal oder Zeitabgleich zu alt/ungenau.");
            List<String> required=new ArrayList<>(List.of("starter")); h.lanes().forEach(l->required.add("lane"+l.lane()));
            for(String r:required) if(!healthy(devices.get(r)) || !devices.get(r).ready)
                throw new IllegalStateException(r+" ist nicht verbunden und bereit. Lauf gegebenenfalls abbrechen.");
            tx.executeWithoutResult(s->{
                repo.db().update("UPDATE heat SET heat_status='RUNNING',start_ms=?,start_quality_ms=? WHERE id=?",e.atMs(),e.qualityMs(),h.id());
                recordEvent(role,e); repo.audit(role,"Lauf "+h.number()+" gestartet.");
            });
        } else if(e.type().equals("STOP")) {
            if(!role.matches("lane[1-3]")) throw new IllegalArgumentException("Nur eine Bahn darf ihre Zeit stoppen.");
            if(!h.status().equals("RUNNING") || h.startMs()==null) throw new IllegalStateException("Lauf ist nicht mehr in der Messung. Lokal gespeicherte Zeit prüfen.");
            int lane=Integer.parseInt(role.substring(4));
            Lane l=h.lanes().stream().filter(a->a.lane()==lane).findFirst().orElseThrow(()->new IllegalArgumentException("Bahn ist nicht besetzt."));
            if(!l.status().equals("PENDING")) throw new IllegalStateException("Für diese Bahn ist bereits ein Ergebnis gespeichert.");
            long elapsed=Math.round(e.atMs()-h.startMs());
            if(elapsed<=0 || elapsed>maxSeconds*1000L) throw new IllegalArgumentException("Ungültige Schwimmzeit.");
            double quality=e.qualityMs()+(h.startQualityMs()==null?0:h.startQualityMs());
            String note=text(e.note(),"Hinweis",450,false);
            boolean review=e.needsReview() || quality>maxRoundtrip;
            tx.executeWithoutResult(s->{
                repo.db().update("UPDATE lane_result SET result_status='OK',stop_ms=?,elapsed_ms=?,quality_ms=?,needs_review=?,note=? WHERE heat_id=? AND lane_number=?",e.atMs(),elapsed,quality,review,note,h.id(),lane);
                recordEvent(role,e); finishIfComplete(h.id()); repo.audit(role,"Lauf "+h.number()+": "+elapsed+" ms gespeichert.");
            });
        } else throw new IllegalArgumentException("Unbekanntes Zeitereignis.");
    }
    private void falseStart(String role,TimingEvent e) {
        if(!role.equals("starter")) throw new IllegalArgumentException("Nur der Starter darf einen Fehlstart melden.");
        Heat h=requireActive(e.heatId());
        if(!Set.of("RUNNING","FINISHED").contains(h.status())) throw new IllegalStateException("Ein Fehlstart kann nur nach dem Start des aktuellen Laufes gemeldet werden.");
        String repeatedId=tx.execute(s->{
            Heat repeated=repeat(h.id());
            repo.db().update("UPDATE heat SET heat_status='FALSE_START' WHERE id=?",h.id());
            repo.db().update("UPDATE heat SET heat_status='ARMED',server_boot_id=?,false_start_of=? WHERE id=?",clock.bootId(),h.id(),repeated.id());
            recordEvent(role,e);
            repo.audit("starter","Fehlstart in Lauf "+h.number()+". Zeiten werden nicht gewertet. Wiederholung als Lauf "+repeated.number()+" sofort aktiviert.");
            return repeated.id();
        });
        activeId=repeatedId;devices.values().forEach(d->d.ready=false);
    }
    private void recordEvent(String role,TimingEvent e) {
        repo.db().update("INSERT INTO processed_event(event_id,heat_id,event_type,actor,event_ms) VALUES (?,?,?,?,?)",e.eventId(),e.heatId(),e.type(),role,e.atMs());
    }
    public synchronized State state(String role) {
        Heat active=activeId==null?null:repo.heat(activeId);
        if(active!=null && role.startsWith("lane")) {
            Heat h=active;
            active=new Heat(h.id(),h.number(),h.discipline(),h.label(),h.status(),h.startMs(),h.bootId(),h.startQualityMs(),h.falseStartOf(),
                    h.lanes().stream().filter(l->role.equals("lane"+l.lane())).toList());
        }
        List<Device> list=new ArrayList<>();
        for(String r:List.of("starter","lane1","lane2","lane3")) {
            Presence d=devices.get(r); long age=d==null?Long.MAX_VALUE:(long)Math.max(0,clock.now()-d.seen);
            boolean online=d!=null && age<6000;
            list.add(new Device(r,online,online && d.ready,d==null?null:d.rtt,d!=null&&d.valid,age));
        }
        return new State("STATE",clock.bootId(),dataId,clock.now(),active,list,maxRoundtrip,maxSeconds);
    }
    public static String text(String value,String field,int limit,boolean required) {
        String s=value==null?"":value.trim();
        if((required && s.isEmpty()) || s.length()>limit || s.chars().anyMatch(c->Character.isISOControl(c)))
            throw new IllegalArgumentException(field+": bitte "+(required?"1":"0")+" bis "+limit+" Zeichen eingeben.");
        return s;
    }
}

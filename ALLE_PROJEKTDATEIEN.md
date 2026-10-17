# Alle Projektdateien zum Kopieren

Alle Pfade sind relativ zum Projekt-Hauptverzeichnis mit der `pom.xml`. Die ZIP enthält den vollständigen `src/`-Ordner. Für Einrichtung und Bedienung bitte zuerst `README.md` lesen.

## .gitignore

```text
/target/
/data/
/.idea/
*.iml
*.log
```

## pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>
    <groupId>de.dlrg</groupId>
    <artifactId>vereinsmeisterschaft</artifactId>
    <version>1.0.0</version>
    <name>Vereinsmeisterschaft</name>
    <description>Lokale Wettkampfverwaltung mit drei Bahnen</description>
    <properties><java.version>21</java.version><poi.version>5.5.1</poi.version></properties>
    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-websocket</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
        <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>
        <dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId><version>${poi.version}</version></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    </dependencies>
    <build><plugins>
        <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
    </plugins></build>
</project>
```

## src/main/java/de/dlrg/vereinsmeisterschaft/VereinsmeisterschaftApplication.java

```java
package de.dlrg.vereinsmeisterschaft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VereinsmeisterschaftApplication {
    public static void main(String[] args) {
        SpringApplication.run(VereinsmeisterschaftApplication.class, args);
    }
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/config/WebConfig.java

```java
package de.dlrg.vereinsmeisterschaft.config;

import de.dlrg.vereinsmeisterschaft.web.RaceSocketHandler;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import org.springframework.context.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebConfig implements WebSocketConfigurer {
    private final RaceSocketHandler socket;
    public WebConfig(RaceSocketHandler socket) { this.socket=socket; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Gleiches Origin bleibt Pflicht; kein setAllowedOrigins("*").
        registry.addHandler(socket,"/ws").addInterceptors(new HttpSessionHandshakeInterceptor());
    }
    @Bean public OncePerRequestFilter localApiFilter() {
        return new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
                response.setHeader("X-Content-Type-Options","nosniff");
                response.setHeader("X-Frame-Options","DENY");
                response.setHeader("Referrer-Policy","no-referrer");
                response.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
                if(request.getRequestURI().startsWith("/api/")) {
                    response.setHeader("Cache-Control","no-store");
                    String origin=request.getHeader("Origin");
                    if(origin!=null) {
                        try {
                            URI uri=URI.create(origin);
                            if(!request.getScheme().equals(uri.getScheme()) || !request.getHeader("Host").equalsIgnoreCase(uri.getRawAuthority())) {
                                response.sendError(403,"Fremder Ursprung nicht erlaubt."); return;
                            }
                        } catch(Exception ex) { response.sendError(403); return; }
                    }
                }
                chain.doFilter(request,response);
            }
        };
    }
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/model/BackupModels.java

```java
package de.dlrg.vereinsmeisterschaft.model;

import de.dlrg.vereinsmeisterschaft.model.Models.*;
import java.util.List;

public final class BackupModels {
    private BackupModels() {}
    public record Selection(boolean participants,boolean heats,boolean results,boolean disciplines,boolean classes) {
        public Selection withDependencies() {
            return new Selection(participants||heats||results,heats||results,results,disciplines||heats||results,classes||participants||heats||results);
        }
        public boolean any() {return participants||heats||results||disciplines||classes;}
    }
    public record StoredParticipant(Participant person,boolean deleted) {}
    public record BackupFile(String format,int version,String exportedAt,Selection selection,
                             List<StoredParticipant> participants,List<Heat> heats,List<String> disciplines,List<NewCompetitionClass> classes) {}
    public record Counts(long participants,long heats,long results,long disciplines,long classes) {
        public boolean empty() {return participants+heats+results+disciplines+classes==0;}
    }
    public record DataSummary(Counts counts,boolean empty,boolean changeAllowed) {}
    public record BackupPreview(String exportedAt,Selection selection,Counts contents,boolean restoreAllowed) {}
    public record Confirmation(String confirmation) {}
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/model/Models.java

```java
package de.dlrg.vereinsmeisterschaft.model;

import java.util.List;

/** Kleine unveraenderliche Datentraeger; keine Lombok-Installation erforderlich. */
public final class Models {
    private Models() {}
    public record Participant(String id, String firstName, String lastName, int birthYear,
                              String category, String trainingGroup) {}
    public record Discipline(String name, long heatCount) {}
    public record NewDiscipline(String name) {}
    public record CompetitionClass(String trainingGroup, String category, long participantCount) {}
    public record NewCompetitionClass(String trainingGroup, String category) {}
    public record Lane(int lane, String participantId, String firstName, String lastName,
                       int birthYear, String category, String trainingGroup, String status,
                       Double stopMs, Long elapsedMs, Double qualityMs, boolean needsReview, String note) {}
    public record Heat(String id, long number, String discipline, String label, String status,
                       Double startMs, String bootId, Double startQualityMs, String falseStartOf, List<Lane> lanes) {}
    public record LaneAssignment(int lane, String participantId) {}
    public record NewHeat(String discipline, String label, List<LaneAssignment> lanes) {}
    public record Correction(String status, Long elapsedMs, String reason) {}
    public record TimingEvent(String type, String eventId, String heatId, String bootId,
                              double atMs, double qualityMs, boolean needsReview, String note) {}
    public record Device(String role, boolean online, boolean ready, Double roundtripMs,
                         boolean timingValid, long lastSeenAgeMs) {}
    public record State(String type, String bootId, String dataId, double serverNow, Heat activeHeat,
                        List<Device> devices, double maxRoundtripMs, int maxRaceSeconds) {}
    public record ScoredTime(String discipline, String heatId, Long heatNumber, Long elapsedMs, String status) {}
    public record Standing(Integer place, Participant participant, long totalMs, int completed,
                           boolean complete, List<ScoredTime> times) {}
    public record GroupRanking(String trainingGroup, String category, List<String> disciplines, List<Standing> standings) {}
    public record DisciplineStanding(int place, Participant participant, ScoredTime time) {}
    public record DisciplineRanking(String discipline, List<DisciplineStanding> standings) {}
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/repository/RaceRepository.java

```java
package de.dlrg.vereinsmeisterschaft.repository;

import de.dlrg.vereinsmeisterschaft.model.Models.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@DependsOnDatabaseInitialization
public class RaceRepository {
    private final JdbcTemplate db;
    public RaceRepository(JdbcTemplate db) { this.db = db; }
    public JdbcTemplate db() { return db; }

    public List<Participant> participants() {
        return db.query("SELECT * FROM participant WHERE deleted=FALSE ORDER BY last_name,first_name,id", (r,n) -> participant(r));
    }
    public Optional<Participant> participant(String id) {
        return db.query("SELECT * FROM participant WHERE id=? AND deleted=FALSE", (r,n) -> participant(r),id).stream().findFirst();
    }
    public boolean participantIdUsed(String id) {
        return db.queryForObject("SELECT COUNT(*) FROM participant WHERE id=?",Integer.class,id)>0;
    }
    private Participant participant(ResultSet r) throws SQLException {
        return new Participant(r.getString("id"),r.getString("first_name"),r.getString("last_name"),
                r.getInt("birth_year"),r.getString("category"),r.getString("training_group"));
    }
    public void insert(Participant p) {
        db.update("INSERT INTO participant(id,first_name,last_name,birth_year,category,training_group) VALUES (?,?,?,?,?,?)",p.id(),p.firstName(),p.lastName(),
                p.birthYear(),p.category(),p.trainingGroup());
    }
    public List<Discipline> disciplines() {
        return db.query("""
            SELECT d.name,(SELECT COUNT(*) FROM heat h WHERE LOWER(h.discipline)=d.name_key) AS heat_count
            FROM discipline d ORDER BY d.name_key
            """,(r,n)->new Discipline(r.getString("name"),r.getLong("heat_count")));
    }
    public Optional<String> disciplineName(String name) {
        if(name==null) return Optional.empty();
        return db.query("SELECT name FROM discipline WHERE name_key=?",(r,n)->r.getString("name"),
                name.toLowerCase(Locale.ROOT)).stream().findFirst();
    }
    public void insertDiscipline(String name) {
        db.update("INSERT INTO discipline(name,name_key) VALUES (?,?)",name,name.toLowerCase(Locale.ROOT));
    }
    public List<CompetitionClass> competitionClasses() {
        return db.query("""
            SELECT c.training_group,c.category,(SELECT COUNT(*) FROM participant p
            WHERE p.training_group=c.training_group AND p.category=c.category) AS people
            FROM competition_class c ORDER BY c.training_group,c.category
            """,(r,n)->new CompetitionClass(r.getString("training_group"),r.getString("category"),r.getLong("people")));
    }
    public boolean classExists(String group,String category) {
        return db.queryForObject("SELECT COUNT(*) FROM competition_class WHERE training_group=? AND category=?",Integer.class,group,category)>0;
    }
    public void ensureClass(String group,String category) {
        if(!classExists(group,category)) db.update("INSERT INTO competition_class(training_group,category) VALUES (?,?)",group,category);
    }
    public List<Heat> heats() {
        return db.query("SELECT * FROM heat ORDER BY heat_number DESC", (r,n) -> heat(r));
    }
    public Heat heat(String id) {
        return db.query("SELECT * FROM heat WHERE id=?",(r,n)->heat(r),id).stream().findFirst()
                .orElseThrow(()->new IllegalArgumentException("Lauf nicht gefunden."));
    }
    private Heat heat(ResultSet r) throws SQLException {
        String id=r.getString("id");
        return new Heat(id,r.getLong("heat_number"),r.getString("discipline"),r.getString("label"),
                r.getString("heat_status"),(Double)r.getObject("start_ms"),r.getString("server_boot_id"),
                (Double)r.getObject("start_quality_ms"),r.getString("false_start_of"),lanes(id));
    }
    private List<Lane> lanes(String id) {
        return db.query("SELECT * FROM lane_result WHERE heat_id=? ORDER BY lane_number",(r,n)->new Lane(
                r.getInt("lane_number"),r.getString("participant_id"),r.getString("first_name"),
                r.getString("last_name"),r.getInt("birth_year"),r.getString("category"),r.getString("training_group"),
                r.getString("result_status"),(Double)r.getObject("stop_ms"),(Long)r.getObject("elapsed_ms"),
                (Double)r.getObject("quality_ms"),r.getBoolean("needs_review"),r.getString("note")),id);
    }
    public void audit(String actor,String text) {
        db.update("INSERT INTO audit_log(actor,action_text) VALUES (?,?)",actor,text);
    }
    public List<Map<String,Object>> audit() {
        return db.queryForList("SELECT id,recorded_at,actor,action_text FROM audit_log ORDER BY id DESC LIMIT 200");
    }
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/service/AccessService.java

```java
package de.dlrg.vereinsmeisterschaft.service;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccessService {
    public static final String ROLE = "vmRole";
    private final Map<String,String> codes = new LinkedHashMap<>();
    public AccessService(@Value("${app.admin-password:}") String password) {
        SecureRandom random=new SecureRandom();
        if(password.isBlank()) {
            byte[] bytes=new byte[8]; random.nextBytes(bytes); password=HexFormat.of().formatHex(bytes);
        }
        codes.put("admin",password);
        for(String role:List.of("starter","lane1","lane2","lane3")) {
            String code;
            do { code=String.format("%06d",random.nextInt(1_000_000)); } while(codes.containsValue(code));
            codes.put(role,code);
        }
        LoggerFactory.getLogger(getClass()).info("VERWALTUNG: http://localhost:8080 | Admin-Passwort: {}",password);
    }
    public boolean valid(String role,String code) {
        String expected=codes.get(role);
        return expected!=null && code!=null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),code.getBytes(StandardCharsets.UTF_8));
    }
    public String role(HttpSession session) {
        Object role=session==null?null:session.getAttribute(ROLE);
        if(role==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Bitte anmelden.");
        return role.toString();
    }
    public void admin(HttpSession session) {
        if(!"admin".equals(role(session))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Nur die Verwaltung darf diese Aktion ausführen.");
    }
    public Map<String,String> stationCodes() {
        var copy=new LinkedHashMap<>(codes); copy.remove("admin"); return copy;
    }
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/service/BackupService.java

```java
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
```

## src/main/java/de/dlrg/vereinsmeisterschaft/service/ExcelService.java

```java
package de.dlrg.vereinsmeisterschaft.service;

import de.dlrg.vereinsmeisterschaft.model.Models.Participant;
import java.io.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ExcelService {
    private static final String[] HEADERS={"Teilnehmer-ID","Vorname","Nachname","Jahrgang","Wertung","Trainingsgruppe"};
    public List<Participant> read(InputStream input) throws IOException {
        try(Workbook book=new XSSFWorkbook(input)) {
            if(book.getNumberOfSheets()==0) throw new IllegalArgumentException("Die Arbeitsmappe enthält kein Tabellenblatt.");
            Sheet sheet=book.getSheetAt(0);
            if(sheet.getLastRowNum()>2000) throw new IllegalArgumentException("Maximal 2000 Teilnehmerzeilen erlaubt. Leere formatierte Zeilen am Ende gegebenenfalls löschen.");
            Row header=sheet.getRow(0);
            if(header==null) throw new IllegalArgumentException("Die erste Zeile muss die Spaltenüberschriften enthalten.");
            DataFormatter formatter=new DataFormatter(Locale.GERMANY);
            Map<String,Integer> columns=new HashMap<>();
            for(Cell cell:header) {
                String name=normalize(formatter.formatCellValue(cell));
                if(!name.isBlank() && columns.put(name,cell.getColumnIndex())!=null)
                    throw new IllegalArgumentException("Doppelte Spaltenüberschrift: "+name);
            }
            for(String required:List.of("teilnehmerid","vorname","nachname","jahrgang"))
                if(!columns.containsKey(required)) throw new IllegalArgumentException("Spalte fehlt: "+required+". Bitte die Vorlage verwenden.");
            List<Participant> list=new ArrayList<>();
            for(int n=1;n<=sheet.getLastRowNum();n++) {
                Row row=sheet.getRow(n); if(row==null) continue;
                String[] values=new String[HEADERS.length];
                for(int i=0;i<HEADERS.length;i++) {
                    Integer c=columns.get(normalize(HEADERS[i])); Cell cell=c==null?null:row.getCell(c);
                    if(cell!=null && cell.getCellType()==CellType.FORMULA)
                        throw new IllegalArgumentException("Zeile "+(n+1)+": bitte feste Werte statt Formeln verwenden.");
                    values[i]=cell==null?"":formatter.formatCellValue(cell).trim();
                }
                if(Arrays.stream(values).allMatch(String::isBlank)) continue;
                try {
                    int year=Integer.parseInt(values[3]);
                    list.add(RaceService.validate(new Participant(values[0],values[1],values[2],year,values[4],values[5])));
                } catch(IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Zeile "+(n+1)+": "+(ex instanceof NumberFormatException?"Jahrgang muss eine vierstellige Jahreszahl sein.":ex.getMessage()));
                }
            }
            return list;
        }
    }
    private String normalize(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-\\uFEFF]",""); }

    public byte[] template(boolean demo) throws IOException {
        try(Workbook book=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            Sheet sheet=book.createSheet("Teilnehmer");
            CellStyle heading=book.createCellStyle(); Font font=book.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
            heading.setFont(font); heading.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex()); heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row row=sheet.createRow(0); row.setHeightInPoints(24);
            for(int i=0;i<HEADERS.length;i++) { Cell c=row.createCell(i); c.setCellValue(HEADERS[i]); c.setCellStyle(heading); sheet.setColumnWidth(i,(i==5?24:19)*256); }
            CellStyle text=book.createCellStyle(); text.setDataFormat(book.createDataFormat().getFormat("@")); sheet.setDefaultColumnStyle(0,text);
            if(demo) {
                String[][] examples={{"1001","Mia","Beispiel","2016","weiblich","Hechte"},{"1002","Leon","Muster","2016","männlich","Hechte"},{"1003","Emma","Test","2017","weiblich","Hechte"},
                        {"1004","Finn","Beispiel","2015","männlich","Tümmler"},{"1005","Lina","Muster","2014","weiblich","Tümmler"},{"1006","Noah","Test","2015","männlich","Tümmler"}};
                for(int n=0;n<examples.length;n++) { Row r=sheet.createRow(n+1); for(int c=0;c<6;c++) { Cell cell=r.createCell(c); if(c==3) cell.setCellValue(Integer.parseInt(examples[n][c])); else cell.setCellValue(examples[n][c]); } }
            }
            sheet.createFreezePane(0,1); sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,demo?6:1,0,5));
            book.write(out); return out.toByteArray();
        }
    }
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/service/RaceService.java

```java
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
```

## src/main/java/de/dlrg/vereinsmeisterschaft/service/ServerClock.java

```java
package de.dlrg.vereinsmeisterschaft.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Epochenaehnliche Referenzzeit, die innerhalb dieses Serverstarts monoton bleibt. */
@Component
public class ServerClock {
    private final long originNanos = System.nanoTime();
    private final long originMillis = System.currentTimeMillis();
    private volatile String bootId = UUID.randomUUID().toString();
    public double now() { return originMillis + (System.nanoTime() - originNanos) / 1_000_000.0; }
    public String bootId() { return bootId; }
    public void renewBootId() {bootId=UUID.randomUUID().toString();}
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/service/StandingsCalculator.java

```java
package de.dlrg.vereinsmeisterschaft.service;

import de.dlrg.vereinsmeisterschaft.model.Models.*;
import java.text.Collator;
import java.util.*;

/** Gesamtzeit: je Disziplin der letzte gültige, vollständig beendete Versuch. */
public final class StandingsCalculator {
    private StandingsCalculator() {}
    private record Attempt(Heat heat,Lane lane) {
        boolean valid() {
            return heat.status().equals("FINISHED") && lane.status().equals("OK")
                    && !lane.needsReview() && lane.elapsedMs()!=null && lane.elapsedMs()>0;
        }
    }
    private static final class Group {
        final String trainingGroup,category;
        Group(String trainingGroup,String category) {this.trainingGroup=trainingGroup;this.category=category;}
        final List<Participant> people=new ArrayList<>();
        final Set<String> required=new HashSet<>();
        final Map<String,Map<String,Attempt>> latest=new HashMap<>(),valid=new HashMap<>();
    }
    public static List<GroupRanking> calculate(List<Participant> people,List<Heat> heats,List<Discipline> catalogue) {
        return calculate(people,heats,catalogue,"groups");
    }
    public static List<GroupRanking> calculate(List<Participant> people,List<Heat> heats,List<Discipline> catalogue,String mode) {
        boolean byClass=mode.equals("classes");
        Collator names=Collator.getInstance(Locale.GERMAN);
        Comparator<String> order=(a,b)->{int n=names.compare(a,b);return n==0?a.compareTo(b):n;};
        Map<String,Group> groups=new TreeMap<>(order);
        for(Participant p:people) groups.computeIfAbsent(bucket(p.trainingGroup(),p.category(),byClass),k->new Group(p.trainingGroup(),byClass?p.category():null)).people.add(p);
        Map<String,String> disciplineNames=new HashMap<>();
        catalogue.forEach(d->disciplineNames.put(key(d.name()),d.name()));
        for(Heat h:heats) {
            if(Set.of("ABORTED","FALSE_START").contains(h.status())) continue;
            String discipline=key(h.discipline());
            disciplineNames.putIfAbsent(discipline,h.discipline());
            for(Lane l:h.lanes()) {
                Group g=groups.get(bucket(l.trainingGroup(),l.category(),byClass));if(g==null) continue;
                // Bereits geplante Disziplinen verhindern einen Sieg durch fehlende Teilnahmen.
                g.required.add(discipline);
                Attempt a=new Attempt(h,l);
                remember(g.latest,l.participantId(),discipline,a);
                if(a.valid()) remember(g.valid,l.participantId(),discipline,a);
            }
        }
        List<GroupRanking> output=new ArrayList<>();
        for(var entry:groups.entrySet()) {
            Group g=entry.getValue();
            List<String> required=g.required.stream().sorted((a,b)->order.compare(disciplineNames.get(a),disciplineNames.get(b))).toList();
            List<Standing> rows=new ArrayList<>();
            for(Participant p:g.people) {
                List<ScoredTime> times=new ArrayList<>();long total=0;int completed=0;
                for(String discipline:required) {
                    Attempt valid=g.valid.getOrDefault(p.id(),Map.of()).get(discipline);
                    Attempt latest=g.latest.getOrDefault(p.id(),Map.of()).get(discipline);
                    Attempt shown=valid!=null?valid:latest;
                    Long elapsed=valid==null?null:valid.lane().elapsedMs();
                    if(elapsed!=null) {total+=elapsed;completed++;}
                    String status=valid!=null?"OK":latest==null?"MISSING":latest.lane().needsReview()?"REVIEW":
                            !latest.lane().status().equals("PENDING")?latest.lane().status():latest.heat().status();
                    times.add(new ScoredTime(disciplineNames.get(discipline),shown==null?null:shown.heat().id(),
                            shown==null?null:shown.heat().number(),elapsed,status));
                }
                rows.add(new Standing(null,p,total,completed,!required.isEmpty() && completed==required.size(),List.copyOf(times)));
            }
            rows.sort((a,b)->{
                int n=Boolean.compare(b.complete(),a.complete());if(n!=0)return n;
                n=a.complete()?Long.compare(a.totalMs(),b.totalMs()):Integer.compare(b.completed(),a.completed());if(n!=0)return n;
                n=order.compare(a.participant().lastName(),b.participant().lastName());if(n!=0)return n;
                n=order.compare(a.participant().firstName(),b.participant().firstName());
                return n!=0?n:a.participant().id().compareTo(b.participant().id());
            });
            List<Standing> ranked=new ArrayList<>();Long previous=null;int position=0,place=0;
            for(Standing row:rows) {
                Integer rank=null;
                if(row.complete()) {
                    position++;
                    if(previous==null || row.totalMs()!=previous) place=position;
                    previous=row.totalMs();rank=place;
                }
                ranked.add(new Standing(rank,row.participant(),row.totalMs(),row.completed(),row.complete(),row.times()));
            }
            output.add(new GroupRanking(g.trainingGroup,g.category,required.stream().map(disciplineNames::get).toList(),List.copyOf(ranked)));
        }
        return List.copyOf(output);
    }
    private static String bucket(String group,String category,boolean byClass) {return group+(byClass?"\u0000"+category:"");}
    public static List<DisciplineRanking> byDiscipline(List<Participant> people,List<Heat> heats,List<Discipline> catalogue) {
        Map<String,Participant> registered=new HashMap<>();people.forEach(p->registered.put(p.id(),p));
        Map<String,Map<String,Attempt>> times=new HashMap<>();
        Map<String,String> names=new HashMap<>();catalogue.forEach(d->names.put(key(d.name()),d.name()));
        for(Heat h:heats) for(Lane l:h.lanes()) {
            Attempt attempt=new Attempt(h,l);
            if(registered.containsKey(l.participantId()) && attempt.valid()) {
                String discipline=key(h.discipline());names.putIfAbsent(discipline,h.discipline());
                remember(times,l.participantId(),discipline,attempt);
            }
        }
        List<DisciplineRanking> output=new ArrayList<>();
        for(String discipline:names.keySet().stream().sorted(Comparator.comparing(names::get,String.CASE_INSENSITIVE_ORDER)).toList()) {
            List<DisciplineStanding> rows=new ArrayList<>();
            for(Participant p:people) {
                Attempt a=times.getOrDefault(p.id(),Map.of()).get(discipline);
                if(a!=null)rows.add(new DisciplineStanding(0,p,new ScoredTime(names.get(discipline),a.heat().id(),a.heat().number(),a.lane().elapsedMs(),"OK")));
            }
            rows.sort(Comparator.comparingLong((DisciplineStanding r)->r.time().elapsedMs())
                    .thenComparing(r->r.participant().lastName(),String.CASE_INSENSITIVE_ORDER).thenComparing(r->r.participant().id()));
            List<DisciplineStanding> ranked=new ArrayList<>();Long previous=null;int place=0,position=0;
            for(DisciplineStanding row:rows) {
                position++;if(previous==null || !previous.equals(row.time().elapsedMs()))place=position;
                previous=row.time().elapsedMs();ranked.add(new DisciplineStanding(place,row.participant(),row.time()));
            }
            if(!ranked.isEmpty())output.add(new DisciplineRanking(names.get(discipline),List.copyOf(ranked)));
        }
        return List.copyOf(output);
    }
    private static String key(String text) {return text.toLowerCase(Locale.ROOT);}
    private static void remember(Map<String,Map<String,Attempt>> target,String person,String discipline,Attempt attempt) {
        target.computeIfAbsent(person,k->new HashMap<>()).merge(discipline,attempt,
                (old,newer)->{
                    Double a=old.heat().startMs(),b=newer.heat().startMs();
                    if(a!=null && b!=null && !a.equals(b)) return b>a?newer:old;
                    return newer.heat().number()>old.heat().number()?newer:old;
                });
    }
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/web/ApiController.java

```java
package de.dlrg.vereinsmeisterschaft.web;

import de.dlrg.vereinsmeisterschaft.model.Models.*;
import de.dlrg.vereinsmeisterschaft.service.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final RaceService races; private final AccessService access; private final ExcelService excel; private final RaceSocketHandler socket;
    public ApiController(RaceService races,AccessService access,ExcelService excel,RaceSocketHandler socket) { this.races=races; this.access=access; this.excel=excel; this.socket=socket; }
    public record Login(String role,String code) {}
    @PostMapping("/login") public Map<String,String> login(@RequestBody Login login,HttpServletRequest request) {
        HttpSession old=request.getSession();
        Long last=(Long)old.getAttribute("loginAttempt"); long now=System.currentTimeMillis();
        if(last!=null && now-last<1000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Bitte kurz warten.");
        old.setAttribute("loginAttempt",now);
        if(login==null || !access.valid(login.role(),login.code())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Rolle oder Zugangscode falsch.");
        socket.disconnectHttpSession(old.getId()); old.invalidate();
        request.getSession(true).setAttribute(AccessService.ROLE,login.role());
        return Map.of("role",login.role());
    }
    @PostMapping("/logout") public Map<String,Boolean> logout(HttpServletRequest request) {
        HttpSession s=request.getSession(false); if(s!=null) { socket.disconnectHttpSession(s.getId()); s.invalidate(); }
        return Map.of("ok",true);
    }
    @GetMapping("/me") public Map<String,String> me(HttpServletRequest r) { return Map.of("role",access.role(r.getSession(false))); }
    @GetMapping("/state") public State state(HttpServletRequest r) { return races.state(access.role(r.getSession(false))); }
    @GetMapping("/admin/participants") public List<Participant> participants(HttpSession s) { access.admin(s); return races.participants(); }
    @PostMapping("/admin/participants") public Map<String,Integer> participant(@RequestBody Participant p,HttpSession s) {
        access.admin(s); int n=races.addParticipants(List.of(p)); socket.broadcast(); return Map.of("added",n);
    }
    @DeleteMapping("/admin/participants/{id}") public Map<String,Boolean> deleteParticipant(@PathVariable String id,HttpSession s) {
        access.admin(s); races.deleteParticipant(id); socket.broadcast(); return Map.of("ok",true);
    }
    @PostMapping("/admin/import") public Map<String,Integer> upload(@RequestParam("file") MultipartFile file,HttpSession s) throws IOException {
        access.admin(s);
        if(file.isEmpty() || file.getSize()>5*1024*1024 || file.getOriginalFilename()==null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException("Bitte eine .xlsx-Datei auswählen.");
        List<Participant> people;
        try(var input=file.getInputStream()) { people=excel.read(input); }
        catch(IllegalArgumentException ex) { throw ex; }
        catch(Exception ex) { throw new IllegalArgumentException("Die Excel-Datei konnte nicht gelesen werden. Bitte eine unverschlüsselte .xlsx-Datei mit der Vorlagenstruktur verwenden."); }
        int n=races.addParticipants(people); socket.broadcast(); return Map.of("added",n);
    }
    @GetMapping("/admin/template.xlsx") public ResponseEntity<byte[]> template(@RequestParam(defaultValue="false") boolean demo,HttpSession s) throws IOException {
        access.admin(s); return download(excel.template(demo),demo?"teilnehmer-beispiel.xlsx":"teilnehmer-vorlage.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }
    @GetMapping("/admin/heats") public List<Heat> heats(HttpSession s) { access.admin(s); return races.heats(); }
    @GetMapping("/admin/disciplines") public List<Discipline> disciplines(HttpSession s) { access.admin(s); return races.disciplines(); }
    @PostMapping("/admin/disciplines") public Map<String,Boolean> addDiscipline(@RequestBody NewDiscipline d,HttpSession s) {
        access.admin(s); races.addDiscipline(d.name()); socket.broadcast(); return Map.of("ok",true);
    }
    @DeleteMapping("/admin/disciplines") public Map<String,Boolean> deleteDiscipline(@RequestParam String name,HttpSession s) {
        access.admin(s); races.deleteDiscipline(name); socket.broadcast(); return Map.of("ok",true);
    }
    @GetMapping("/admin/classes") public List<CompetitionClass> classes(HttpSession s) {access.admin(s);return races.competitionClasses();}
    @PostMapping("/admin/classes") public Map<String,Boolean> addClass(@RequestBody NewCompetitionClass c,HttpSession s) {
        access.admin(s);races.addCompetitionClass(c);socket.broadcast();return Map.of("ok",true);
    }
    @DeleteMapping("/admin/classes") public Map<String,Boolean> deleteClass(@RequestParam String group,@RequestParam String category,HttpSession s) {
        access.admin(s);races.deleteCompetitionClass(group,category);socket.broadcast();return Map.of("ok",true);
    }
    @GetMapping("/admin/rankings") public List<GroupRanking> rankings(@RequestParam(defaultValue="classes") String mode,HttpSession s) { access.admin(s); return races.rankings(mode); }
    @GetMapping("/admin/discipline-rankings") public List<DisciplineRanking> disciplineRankings(HttpSession s) {access.admin(s);return races.disciplineRankings();}
    @GetMapping("/admin/rankings.csv") public ResponseEntity<byte[]> rankingCsv(@RequestParam(defaultValue="classes") String mode,HttpSession s) {
        access.admin(s);
        StringBuilder out=new StringBuilder("\uFEFFWertungsklasse;Trainingsgruppe;Platz;Teilnehmer-ID;Vorname;Nachname;Jahrgang;Wertung;Disziplinen_gueltig;Disziplinen_erforderlich;Gesamtzeit_ms;Gesamtzeit_s;Status;Einzelzeiten\r\n");
        for(GroupRanking group:races.rankings(mode)) for(Standing row:group.standings()) {
            Participant p=row.participant();
            String times=row.times().stream().map(t->t.discipline()+": "+(t.elapsedMs()==null?t.status():String.format(Locale.GERMANY,"%.3f s (Lauf %d)",t.elapsedMs()/1000.0,t.heatNumber())))
                    .collect(java.util.stream.Collectors.joining(" | "));
            List<Object> cells=Arrays.asList(group.category()==null?"Alle Wertungen":group.trainingGroup()+" – "+group.category(),group.trainingGroup(),row.place(),p.id(),p.firstName(),p.lastName(),p.birthYear(),p.category(),
                    row.completed(),group.disciplines().size(),row.complete()?row.totalMs():null,row.complete()?String.format(Locale.GERMANY,"%.3f",row.totalMs()/1000.0):null,
                    row.complete()?"Vollständig":"Unvollständig",times);
            out.append(cells.stream().map(ApiController::csvCell).collect(java.util.stream.Collectors.joining(";"))).append("\r\n");
        }
        return download(out.toString().getBytes(StandardCharsets.UTF_8),"gesamtwertung.csv","text/csv;charset=UTF-8");
    }
    @GetMapping("/admin/discipline-rankings.csv") public ResponseEntity<byte[]> disciplineCsv(@RequestParam(defaultValue="") String discipline,HttpSession s) {
        access.admin(s);
        StringBuilder out=new StringBuilder("\uFEFFDisziplin;Platz;Teilnehmer-ID;Vorname;Nachname;Trainingsgruppe;Wertung;Zeit_ms;Zeit_s;Lauf\r\n");
        for(DisciplineRanking group:races.disciplineRankings()) {
            if(!discipline.isEmpty() && !group.discipline().equals(discipline))continue;
            for(DisciplineStanding row:group.standings()) {
                Participant p=row.participant();ScoredTime t=row.time();
                out.append(Arrays.asList(group.discipline(),row.place(),p.id(),p.firstName(),p.lastName(),p.trainingGroup(),p.category(),t.elapsedMs(),
                        String.format(Locale.GERMANY,"%.3f",t.elapsedMs()/1000.0),t.heatNumber()).stream().map(ApiController::csvCell)
                        .collect(java.util.stream.Collectors.joining(";"))).append("\r\n");
            }
        }
        return download(out.toString().getBytes(StandardCharsets.UTF_8),"disziplinvergleich.csv","text/csv;charset=UTF-8");
    }
    @PostMapping("/admin/heats") public Heat create(@RequestBody NewHeat h,HttpSession s) { access.admin(s); Heat result=races.createHeat(h); socket.broadcast(); return result; }
    @PostMapping("/admin/heats/{id}/arm") public Map<String,Boolean> arm(@PathVariable String id,HttpSession s) { access.admin(s); races.arm(id); socket.broadcast(); return Map.of("ok",true); }
    @PostMapping("/admin/heats/{id}/abort") public Map<String,Boolean> abort(@PathVariable String id,HttpSession s) { access.admin(s); races.abort(id); socket.broadcast(); return Map.of("ok",true); }
    @PostMapping("/admin/heats/{id}/repeat") public Heat repeat(@PathVariable String id,HttpSession s) { access.admin(s); Heat h=races.repeat(id); socket.broadcast(); return h; }
    @PostMapping("/admin/heats/{id}/lanes/{lane}") public Map<String,Boolean> correct(@PathVariable String id,@PathVariable int lane,@RequestBody Correction c,HttpSession s) {
        access.admin(s); races.correct(id,lane,c); socket.broadcast(); return Map.of("ok",true);
    }
    @GetMapping("/admin/access") public Map<String,Object> codes(HttpSession s,HttpServletRequest request) {
        access.admin(s); List<String> urls=new ArrayList<>();
        try {
            var interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements()) { var net=interfaces.nextElement(); if(!net.isUp() || net.isLoopback()) continue;
                var ips=net.getInetAddresses(); while(ips.hasMoreElements()) { var ip=ips.nextElement(); if(ip instanceof Inet4Address) urls.add(request.getScheme()+"://"+ip.getHostAddress()+":"+request.getLocalPort()); }
            }
        } catch(SocketException ignored) {}
        return Map.of("codes",access.stationCodes(),"urls",urls);
    }
    @GetMapping("/admin/audit") public List<Map<String,Object>> audit(HttpSession s) { access.admin(s); return races.audit(); }
    @GetMapping("/admin/results.csv") public ResponseEntity<byte[]> csv(HttpSession s) {
        access.admin(s);
        StringBuilder out=new StringBuilder("\uFEFFLauf;Disziplin;Bezeichnung;Laufstatus;Bahn;Teilnehmer-ID;Vorname;Nachname;Jahrgang;Wertung;Trainingsgruppe;Status;Zeit_ms;Zeit_s;Pruefen;Abgleichindikator_ms;Hinweis\r\n");
        for(Heat h:races.heats()) for(Lane l:h.lanes()) {
            List<Object> values=Arrays.asList(h.number(),h.discipline(),h.label(),h.status(),l.lane(),l.participantId(),l.firstName(),l.lastName(),l.birthYear(),l.category(),l.trainingGroup(),l.status(),l.elapsedMs(),l.elapsedMs()==null?null:String.format(Locale.GERMANY,"%.3f",l.elapsedMs()/1000.0),l.needsReview()?"JA":"NEIN",l.qualityMs(),l.note());
            out.append(values.stream().map(ApiController::csvCell).collect(java.util.stream.Collectors.joining(";"))).append("\r\n");
        }
        return download(out.toString().getBytes(StandardCharsets.UTF_8),"ergebnisse.csv","text/csv;charset=UTF-8");
    }
    private static String csvCell(Object value) {
        String s=value==null?"":value.toString();
        if(value instanceof String && s.matches("^[=+@\\-\\t\\r].*")) s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
    private static ResponseEntity<byte[]> download(byte[] bytes,String name,String type) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").contentType(MediaType.parseMediaType(type)).body(bytes);
    }
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/web/ApiErrors.java

```java
package de.dlrg.vereinsmeisterschaft.web;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> bad(IllegalArgumentException e) { return error(400,e.getMessage()); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict(IllegalStateException e) { return error(409,e.getMessage()); }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> status(ResponseStatusException e) { return error(e.getStatusCode().value(),e.getReason()); }
    @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<?> json() { return error(400,"Ungültige Eingabe. Zahlen und Pflichtfelder prüfen."); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) public ResponseEntity<?> large() { return error(413,"Datei zu groß. Excel-Dateien dürfen höchstens 5 MB, Sicherungen höchstens 20 MB groß sein."); }
    private ResponseEntity<?> error(int code,String message) { return ResponseEntity.status(code).body(Map.of("message",message==null?"Anfrage fehlgeschlagen.":message)); }
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/web/DataController.java

```java
package de.dlrg.vereinsmeisterschaft.web;

import de.dlrg.vereinsmeisterschaft.model.BackupModels.*;
import de.dlrg.vereinsmeisterschaft.service.*;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/data")
public class DataController {
    private final BackupService backups;private final AccessService access;private final RaceSocketHandler socket;
    public DataController(BackupService backups,AccessService access,RaceSocketHandler socket) {this.backups=backups;this.access=access;this.socket=socket;}
    @GetMapping public DataSummary summary(HttpSession s) {access.admin(s);return backups.summary();}
    @PostMapping("/export") public ResponseEntity<byte[]> export(@RequestBody Selection selection,HttpSession s) {
        access.admin(s);byte[] bytes=backups.write(selection);
        String name="vereinsmeisterschaft-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+".json";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"")
                .contentType(MediaType.APPLICATION_JSON).body(bytes);
    }
    @PostMapping("/preview") public BackupPreview preview(@RequestParam("file") MultipartFile file,HttpSession s) throws IOException {
        access.admin(s);return backups.preview(file.getBytes());
    }
    @PostMapping("/restore") public Map<String,Boolean> restore(@RequestParam("file") MultipartFile file,@RequestParam String confirmation,HttpSession s) throws IOException {
        access.admin(s);backups.restore(file.getBytes(),confirmation);socket.broadcast();return Map.of("ok",true);
    }
    @PostMapping("/reset") public Map<String,Boolean> reset(@RequestBody Confirmation confirmation,HttpSession s) {
        access.admin(s);backups.reset(confirmation.confirmation());socket.broadcast();return Map.of("ok",true);
    }
}
```

## src/main/java/de/dlrg/vereinsmeisterschaft/web/RaceSocketHandler.java

```java
package de.dlrg.vereinsmeisterschaft.web;

import com.fasterxml.jackson.databind.*;
import de.dlrg.vereinsmeisterschaft.model.Models.TimingEvent;
import de.dlrg.vereinsmeisterschaft.service.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

@Component
public class RaceSocketHandler extends TextWebSocketHandler {
    private final RaceService races; private final ServerClock clock; private final ObjectMapper json;
    private final Map<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
    public RaceSocketHandler(RaceService races,ServerClock clock,ObjectMapper json) { this.races=races; this.clock=clock; this.json=json; }
    private String role(WebSocketSession s) { return String.valueOf(s.getAttributes().get(AccessService.ROLE)); }
    @Override public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        String role=role(raw);
        if(!List.of("admin","starter","lane1","lane2","lane3").contains(role)) { raw.close(CloseStatus.POLICY_VIOLATION.withReason("Bitte anmelden.")); return; }
        if(!races.connect(role,raw.getId())) { raw.close(CloseStatus.POLICY_VIOLATION.withReason("Rolle bereits verbunden. Alten Tab schließen.")); return; }
        var safe=new ConcurrentWebSocketSessionDecorator(raw,5000,512*1024);
        sessions.put(raw.getId(),safe); send(safe,races.state(role)); broadcast();
    }
    @Override protected void handleTextMessage(WebSocketSession raw,TextMessage message) {
        double received=clock.now();
        WebSocketSession session=sessions.get(raw.getId()); if(session==null) return;
        String eventId="";
        try {
            JsonNode n=json.readTree(message.getPayload());
            String type=n.path("type").asText(); String role=role(session); eventId=n.path("eventId").asText("");
            if(type.equals("SYNC")) {
                double c0=n.path("c0").asDouble(Double.NaN);
                if(!Double.isFinite(c0)) throw new IllegalArgumentException("Ungültige Synchronisierungsanfrage.");
                Double rtt=n.hasNonNull("rtt")?n.get("rtt").asDouble():null;
                races.heartbeat(role,session.getId(),rtt,n.path("timingValid").asBoolean(false));
                send(session,Map.of("type","SYNC","c0",c0,"s1",received,"s2",clock.now(),"bootId",clock.bootId()));
            } else if(type.equals("READY")) {
                races.ready(role,session.getId(),n.path("heatId").asText(),n.path("ready").asBoolean());
                broadcast();
            } else if(type.equals("START") || type.equals("STOP") || type.equals("FALSE_START")) {
                races.timing(role,session.getId(),json.treeToValue(n,TimingEvent.class));
                send(session,Map.of("type","ACK","eventId",eventId)); broadcast();
            } else throw new IllegalArgumentException("Unbekannte Nachricht.");
        } catch(IllegalArgumentException | IllegalStateException ex) {
            send(session,Map.of("type","ERROR","eventId",eventId,"message",ex.getMessage()));
        } catch(Exception ex) {
            LoggerFactory.getLogger(getClass()).error("WebSocket-Verarbeitung fehlgeschlagen",ex);
            send(session,Map.of("type","ERROR","eventId",eventId,"message","Ereignis konnte nicht gespeichert werden. Lokal behalten und in der Verwaltung prüfen."));
        }
    }
    @Override public void afterConnectionClosed(WebSocketSession s,CloseStatus status) {
        sessions.remove(s.getId()); races.disconnect(role(s),s.getId()); broadcast();
    }
    @Override public void handleTransportError(WebSocketSession s,Throwable error) throws Exception { s.close(CloseStatus.SERVER_ERROR); }
    public void disconnectHttpSession(String id) {
        sessions.values().stream().filter(s->Objects.equals(s.getAttributes().get("HTTP.SESSION.ID"),id)).forEach(s->{
            try { s.close(CloseStatus.NORMAL); } catch(Exception ignored) {} });
    }
    public void broadcast() { sessions.values().forEach(s->send(s,races.state(role(s)))); }
    @Scheduled(fixedDelay=2000) public void tick() { broadcast(); }
    private void send(WebSocketSession s,Object data) {
        try { if(s.isOpen()) s.sendMessage(new TextMessage(json.writeValueAsString(data))); }
        catch(Exception ex) { try { s.close(CloseStatus.SERVER_ERROR); } catch(Exception ignored) {} }
    }
}
```

## src/main/resources/application.properties

```properties
spring.application.name=vereinsmeisterschaft
server.address=0.0.0.0
server.port=8080
server.servlet.session.timeout=12h
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=strict
spring.datasource.url=jdbc:h2:file:./data/wettkampf;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0
spring.datasource.username=sa
spring.datasource.password=
spring.sql.init.mode=always
spring.h2.console.enabled=false
spring.servlet.multipart.max-file-size=25MB
spring.servlet.multipart.max-request-size=26MB
server.error.include-message=never
# Ohne gesetztes Passwort wird bei jedem Serverstart ein neues in der Konsole angezeigt.
# Optional in IntelliJ als Umgebungsvariable setzen: VM_ADMIN_PASSWORD=dein-passwort
app.admin-password=${VM_ADMIN_PASSWORD:}
# Konfigurierbare Grenzen fuer die Startfreigabe; keine Genauigkeitsgarantie.
app.max-roundtrip-ms=100
app.max-race-seconds=1800
```

## src/main/resources/schema.sql

```sql
CREATE TABLE IF NOT EXISTS participant (
 id VARCHAR(40) PRIMARY KEY,
 first_name VARCHAR(100) NOT NULL,
 last_name VARCHAR(100) NOT NULL,
 birth_year INT NOT NULL,
 category VARCHAR(60) NOT NULL,
 training_group VARCHAR(100) NOT NULL,
 deleted BOOLEAN NOT NULL DEFAULT FALSE
);
-- Vorhandene Datenbanken der ersten Version ohne Datenverlust erweitern.
ALTER TABLE participant ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS discipline (
 name VARCHAR(120) PRIMARY KEY,
 name_key VARCHAR(240) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS competition_class (
 training_group VARCHAR(100) NOT NULL,
 category VARCHAR(60) NOT NULL,
 PRIMARY KEY(training_group,category)
);
CREATE TABLE IF NOT EXISTS app_meta (
 meta_key VARCHAR(40) PRIMARY KEY,
 meta_value VARCHAR(100) NOT NULL
);
CREATE TABLE IF NOT EXISTS heat (
 id VARCHAR(36) PRIMARY KEY,
 heat_number BIGINT GENERATED BY DEFAULT AS IDENTITY UNIQUE,
 discipline VARCHAR(120) NOT NULL,
 label VARCHAR(120) NOT NULL,
 heat_status VARCHAR(20) NOT NULL,
 start_ms DOUBLE PRECISION,
 server_boot_id VARCHAR(36),
 start_quality_ms DOUBLE PRECISION,
 false_start_of VARCHAR(36),
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE heat ADD COLUMN IF NOT EXISTS false_start_of VARCHAR(36);
CREATE TABLE IF NOT EXISTS lane_result (
 heat_id VARCHAR(36) NOT NULL REFERENCES heat(id),
 lane_number INT NOT NULL CHECK(lane_number BETWEEN 1 AND 3),
 participant_id VARCHAR(40) NOT NULL REFERENCES participant(id),
 first_name VARCHAR(100) NOT NULL,
 last_name VARCHAR(100) NOT NULL,
 birth_year INT NOT NULL,
 category VARCHAR(60) NOT NULL,
 training_group VARCHAR(100) NOT NULL,
 result_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 stop_ms DOUBLE PRECISION,
 elapsed_ms BIGINT,
 quality_ms DOUBLE PRECISION,
 needs_review BOOLEAN NOT NULL DEFAULT FALSE,
 note VARCHAR(500) NOT NULL DEFAULT '',
 PRIMARY KEY(heat_id,lane_number),
 UNIQUE(heat_id,participant_id)
);
CREATE TABLE IF NOT EXISTS processed_event (
 event_id VARCHAR(36) PRIMARY KEY,
 heat_id VARCHAR(36) NOT NULL,
 event_type VARCHAR(12) NOT NULL,
 actor VARCHAR(12) NOT NULL,
 event_ms DOUBLE PRECISION NOT NULL,
 received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS audit_log (
 id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
 recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 actor VARCHAR(40) NOT NULL,
 action_text VARCHAR(1000) NOT NULL
);
```

## src/main/resources/static/bahn.html

```html
<!doctype html>
<html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Bahn · Vereinsmeisterschaft</title><link rel="stylesheet" href="/css/app.css"><script type="module" src="/js/station.js"></script></head>
<body class="station-page" data-station="lane"><header class="app-header"><div><span class="eyebrow">DLRG · ZEITNAHME</span><h1 id="station-title">Bahn</h1></div><button id="logout" class="quiet">Abmelden</button></header><main class="station">
<div class="station-meta"><span id="connection" class="badge">Verbinde …</span><span id="clock-status" class="small muted">Zeitabgleich …</span></div>
<div id="toast" class="toast" role="alert" hidden></div><div class="heat-info"><span id="heat-number" class="eyebrow dark">WARTE AUF LAUF</span><h2 id="swimmer">Noch kein Lauf aktiviert</h2><p id="discipline" class="muted"></p></div>
<div id="timer" class="timer" aria-label="Gemessene Zeit">00:00,00</div><p id="measurement-status" class="measurement-status">Die Verwaltung bereitet den Lauf vor.</p>
<button id="ready" class="secondary wide" disabled>Bereitschaft bestätigen</button><button id="timing-button" class="timing-button stop" disabled>STOPP</button>
<p class="small muted">Beim Anschlag sofort auf STOPP drücken. Nur ein Stopp pro Lauf. Die Seite geöffnet und den Bildschirm eingeschaltet lassen.</p><button id="wake" class="quiet wide">Bildschirm wach halten</button><p id="wake-note" class="small muted"></p>
<section id="pending-panel" class="panel" hidden><h3>Lokal gesicherte Ereignisse</h3><div id="pending-list"></div></section>
</main></body></html>
```

## src/main/resources/static/css/app.css

```css
:root{font-family:Inter,Segoe UI,system-ui,-apple-system,sans-serif;color:#162b3f;background:#f2f5f7;font-synthesis:none;--navy:#162b3f;--red:#cf1731;--teal:#087b73;--line:#dce4e9;--muted:#627383}
*{box-sizing:border-box}body{margin:0}button,input,select,textarea{font:inherit}button,.button{border:0;border-radius:9px;padding:12px 17px;cursor:pointer;text-decoration:none;font-weight:650;display:inline-flex;align-items:center;justify-content:center;gap:7px;line-height:1.25;touch-action:manipulation}button:disabled{opacity:.42;cursor:not-allowed}button:focus-visible,a:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible{outline:3px solid #279faf;outline-offset:3px}.primary{background:var(--navy);color:white}.secondary{background:#e5eeef;color:#125c59}.quiet{background:#e9eef2;color:#344c5f}.danger{background:#fbe5e8;color:#a20d24}.wide{width:100%}h1,h2,h3,p{margin-top:0}h1{font-size:26px;letter-spacing:-.7px;margin-bottom:0}h2{font-size:23px;letter-spacing:-.45px;margin-bottom:16px}h3{font-size:17px;margin-bottom:14px}p{line-height:1.55}.small{font-size:13px}.muted{color:var(--muted)}.error{color:#ae1029;font-size:14px;line-height:1.5}.eyebrow{display:block;font-size:11px;font-weight:750;letter-spacing:1.7px;margin-bottom:9px;color:#bfcdd8}.eyebrow.dark{color:#627b8b}.app-header{background:var(--navy);border-top:6px solid var(--red);color:white;padding:24px max(24px,calc((100vw - 1200px)/2));display:flex;justify-content:space-between;align-items:center;gap:20px}.header-actions,.actions{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.app-header .quiet{background:#304559;color:#fff}.app-header .badge{color:#fff;background:#304559}.manager{max-width:1248px;padding:0 24px 50px;margin:auto}.tabs{display:flex;gap:7px;border-bottom:1px solid var(--line);padding:22px 0 17px;margin-bottom:26px;overflow-x:auto}.tabs button{background:transparent;white-space:nowrap;color:#526879}.tabs .selected{background:white;color:var(--navy);box-shadow:0 2px 5px #162b3f0d}.section-heading{display:flex;justify-content:space-between;gap:14px;align-items:center;margin-bottom:17px}.section-heading h2{margin-bottom:0}.section-heading p{margin:8px 0 0}.panel{background:white;border:1px solid var(--line);border-radius:14px;padding:23px;margin-bottom:20px;box-shadow:0 3px 10px #162b3f04}.empty{padding:40px;text-align:center;color:var(--muted)}.badge{display:inline-block;border-radius:30px;background:#e7edf1;padding:7px 12px;font-size:12px;font-weight:700;white-space:nowrap}.badge.good{background:#d8f1e8;color:#12634b}.badge.warn{background:#fff1cf;color:#795a08}.badge.bad{background:#fbe3e7;color:#9d1830}.device-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:16px}.device-card{background:white;border:1px solid var(--line);border-radius:12px;padding:18px}.device-card h3{margin-bottom:12px}.device-card p{margin:10px 0 0;font-size:13px;color:var(--muted)}.two-columns{display:grid;grid-template-columns:1fr 1fr;gap:20px}.three-columns{display:grid;grid-template-columns:repeat(3,1fr);gap:15px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.form-grid button{align-self:end;margin-bottom:15px}label{display:block;font-size:13px;font-weight:650;margin-bottom:16px}input,select,textarea{display:block;width:100%;margin-top:7px;border:1px solid #c7d3dc;border-radius:8px;background:#fff;padding:11px 12px;color:var(--navy);min-height:44px}textarea{min-height:85px;resize:vertical}input[type=file]{font-size:13px;padding:10px}input[type=search]{max-width:460px}.search-label{margin:10px 0 20px}.table-wrap{overflow-x:auto;background:white;border:1px solid var(--line);border-radius:12px;margin-bottom:20px}table{border-collapse:collapse;width:100%;font-size:14px;text-align:left}thead{background:#eaf0f3;color:#526879;font-size:12px}th,td{padding:14px 16px;border-bottom:1px solid #e9eef2;vertical-align:middle}tbody tr:last-child td{border:0}td small{display:block;color:var(--muted);margin-top:5px}td .actions{flex-wrap:nowrap}td button{padding:8px 11px;font-size:12px}summary{cursor:pointer;font-weight:650}details[open]>summary{margin-bottom:20px}.count{font-size:15px;color:var(--muted)}.audit{font-size:12px;max-height:300px;overflow:auto;line-height:1.5}.audit p{border-bottom:1px solid var(--line);padding-bottom:9px}.toast{padding:15px 18px;border:1px solid #b7dacb;border-radius:10px;background:#e0f3ea;color:#12583e;margin-bottom:18px;line-height:1.5}.toast.error{background:#fce8eb;color:#98132a;border-color:#edc0c8;font-size:14px}.heat-lanes{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.heat-lane{padding:18px;background:#f5f8fa;border-radius:10px}.heat-lane strong{display:block;margin:9px 0}.heat-lane .lane-time{font-size:25px;font-variant-numeric:tabular-nums;font-weight:700}.heat-lane p{margin-bottom:6px}.code{font-family:ui-monospace,Consolas,monospace;font-size:22px;letter-spacing:2px}.login-page{min-height:100dvh;display:grid;place-items:center;padding:25px;background:linear-gradient(140deg,#142a3e 0%,#244c60 100%)}.login-card{background:#fff;width:min(100%,460px);border-radius:18px;border-top:7px solid var(--red);padding:36px;box-shadow:0 20px 70px #061e2c45}.login-card h1{font-size:34px;margin-bottom:10px}.login-card .eyebrow{color:#617c8c}.login-card form{margin:28px 0 15px}.login-card>.small{margin:22px 0 0}.station-page .app-header{padding:17px 22px}.station{max-width:650px;margin:0 auto;padding:22px}.station-meta{display:flex;gap:10px;justify-content:space-between;align-items:center;flex-wrap:wrap;margin-bottom:28px}.heat-info{text-align:center;min-height:100px}.heat-info h2{font-size:30px;margin-bottom:8px}.heat-info p{margin-bottom:8px}.timer{text-align:center;font-size:clamp(49px,12vw,86px);font-weight:750;letter-spacing:-3px;font-variant-numeric:tabular-nums;line-height:1.3;white-space:nowrap}.measurement-status{text-align:center;min-height:48px;font-size:15px;font-weight:650;margin:14px 0 20px}.timing-button{width:100%;height:190px;border-radius:17px;font-size:48px;letter-spacing:2px;user-select:none;-webkit-user-select:none;margin:14px 0 20px;box-shadow:0 6px 0 #0002}.timing-button:active:not(:disabled){transform:translateY(3px);box-shadow:0 3px 0 #0002}.start{background:#087b62;color:#fff}.stop{background:var(--red);color:white}.timing-button:disabled{background:#cbd5dc;color:#536776;box-shadow:none;opacity:1}.station .panel{padding:17px}.station .panel p:last-child{margin-bottom:0}.pending-item{border-top:1px solid var(--line);padding:12px 0}.pending-item pre{overflow-wrap:anywhere;white-space:pre-wrap;font-size:11px}.pending-item button{font-size:12px;padding:8px}.lane-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:9px 0;border-bottom:1px solid var(--line)}.lane-row:last-child{border:0}dialog{border:1px solid var(--line);border-radius:16px;width:min(94vw,520px);padding:27px;color:var(--navy)}dialog::backdrop{background:#112b40a6}[hidden]{display:none!important}
@media(max-width:800px){.device-grid{grid-template-columns:1fr 1fr}.two-columns{grid-template-columns:1fr}.heat-lanes{grid-template-columns:1fr}.heat-lane{display:grid;grid-template-columns:1fr 1fr;gap:0 12px}.three-columns{grid-template-columns:1fr}.app-header{padding:20px}.manager{padding:0 16px 35px}.section-heading{align-items:flex-start;flex-direction:column}.app-header h1{font-size:22px}.header-actions{gap:8px}.header-actions .badge{display:none}.table-wrap{font-size:13px}th,td{padding:12px}.login-card{padding:27px}.form-grid{grid-template-columns:1fr 1fr}.tabs{margin-bottom:20px}.station{padding:19px}.station .app-header{padding:17px}.timing-button{height:175px}}
@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto!important}}

td .icon-button{width:44px;height:44px;padding:10px}.icon-button svg{flex-shrink:0}.icon-button:hover:not(:disabled){background:#f5cbd3}

.discipline-entry{display:flex;align-items:flex-end;gap:16px}.discipline-entry label{flex:1;margin-bottom:0}.discipline-entry button{min-height:44px}.discipline-entry+p{margin:16px 0 0}.result-tabs{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:22px}.ranking-group{margin:28px 0 32px}.ranking-group h3{font-size:21px;margin:0}.ranking-group .section-heading{margin-bottom:12px}.rank-number{font-size:22px;font-weight:750;font-variant-numeric:tabular-nums}.winner{background:#eff8f3}.winner .rank-number{color:#12634b}.score-time{display:grid;grid-template-columns:minmax(110px,1fr) auto;gap:3px 18px;margin:7px 0}.score-time strong,.total-time{white-space:nowrap;font-variant-numeric:tabular-nums}.score-time small{grid-column:1/-1;margin:0 0 5px}.total-time{font-size:20px}.false-start-button{min-height:60px;margin:0 0 22px;border:2px solid #e7a3ae}.station:has(#false-start:not([hidden])) .timing-button{height:130px}.false-start-button:hover:not(:disabled){background:#f5cbd3}
@media(max-width:800px){.discipline-entry{align-items:stretch;flex-direction:column}.score-time{gap:3px 10px}.total-time{font-size:17px}}

.full-width{grid-column:1/-1}.result-controls{display:flex;gap:18px;flex-wrap:wrap}.result-controls label{min-width:260px;flex:1}.data-counts{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin:0 0 22px}.data-count{background:white;border:1px solid var(--line);border-radius:10px;padding:15px;display:flex;flex-direction:column;gap:6px}.data-count strong{font-size:25px;font-variant-numeric:tabular-nums}.data-count span{font-size:12px;color:var(--muted)}.backup-options{display:grid;gap:12px;margin:20px 0}.backup-options label{display:flex;align-items:center;gap:10px;margin:0;font-size:15px}.backup-options input[type=checkbox]{display:inline-block;width:20px;height:20px;min-height:0;margin:0;padding:0;accent-color:var(--teal)}#backup-preview{border-top:1px solid var(--line);padding-top:22px;margin-top:22px}#backup-preview .data-counts{grid-template-columns:repeat(2,minmax(0,1fr))}.reset-panel{border-color:#edbdc7}.tabs{gap:3px}.tabs button{padding:12px 11px}.ranking-group>h3{margin:0 0 16px}
@media(max-width:800px){.data-counts{grid-template-columns:repeat(2,minmax(0,1fr))}.result-controls{display:block}.result-controls label{min-width:0}}
```

## src/main/resources/static/index.html

```html
<!doctype html>
<html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Anmelden · Vereinsmeisterschaft</title><link rel="stylesheet" href="/css/app.css"><script type="module" src="/js/login.js"></script></head>
<body class="login-page"><main class="login-card">
    <div class="eyebrow">DLRG · LOKALE ZEITNAHME</div><h1>Vereins&shy;meisterschaft</h1>
    <p class="muted">Ein Wettkampf. Alle Bahnen im Blick.</p>
    <form id="login-form">
        <label>Deine Station<select id="role"><option value="admin">Verwaltung am Laptop</option><option value="starter">Starter</option><option value="lane1">Bahn 1</option><option value="lane2">Bahn 2</option><option value="lane3">Bahn 3</option></select></label>
        <label>Passwort / Zugangscode<input id="code" type="password" autocomplete="current-password" required autofocus></label>
        <button class="primary wide" type="submit">Station öffnen</button>
    </form><p id="login-error" class="error" role="alert"></p>
    <p class="small muted">Das Admin-Passwort steht beim Start in der IntelliJ-Konsole. Die Verwaltung zeigt die Zugangscodes für die Handys an.</p>
</main></body></html>
```

## src/main/resources/static/js/common.js

```javascript
export const $=selector=>document.querySelector(selector);
export const escapeHtml=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const roleLabel=role=>({admin:'Verwaltung',starter:'Starter',lane1:'Bahn 1',lane2:'Bahn 2',lane3:'Bahn 3'}[role]||role);
export const statusLabel=status=>({PLANNED:'Geplant',ARMED:'Startvorbereitung',RUNNING:'Läuft',FINISHED:'Beendet',ABORTED:'Abgebrochen',FALSE_START:'Fehlstart',INTERRUPTED:'Unterbrochen',PENDING:'Offen',OK:'Zeit vorhanden',DNS:'Nicht gestartet',DNF:'Nicht beendet',DSQ:'Disqualifiziert',MISSING:'Zeit fehlt',REVIEW:'Prüfung erforderlich'}[status]||status);
export const destination=role=>role==='admin'?'/manager.html':role==='starter'?'/starter.html':'/bahn.html';
export async function api(path,options={}) {
    const init={...options,headers:{...options.headers}};
    if(options.body && !(options.body instanceof FormData)) { init.body=JSON.stringify(options.body); init.headers['Content-Type']='application/json'; }
    const response=await fetch('/api'+path,init);
    const data=await response.json().catch(()=>({message:'Der Server hat keine gültige Antwort geliefert.'}));
    if(!response.ok) { if(response.status===401 && path!=='/login' && location.pathname!=='/' && location.pathname!=='/index.html') location.href='/'; throw new Error(data.message||'Anfrage fehlgeschlagen.'); }
    return data;
}
export async function ensureRole(expected) {
    const me=await api('/me');
    if(expected==='lane'?!/^lane[1-3]$/.test(me.role):me.role!==expected) { location.href=destination(me.role); throw new Error('Falsche Ansicht.'); }
    $('#logout')?.addEventListener('click',async()=>{ if(!confirm('Station abmelden? Während eines Laufes sollte die Seite geöffnet bleiben.')) return; await api('/logout',{method:'POST'}); location.href='/'; });
    return me.role;
}
export function formatTime(ms) {
    if(ms==null || !Number.isFinite(ms)) return '—';
    const cs=Math.floor(Math.max(0,ms)/10), minutes=Math.floor(cs/6000),seconds=Math.floor(cs/100)%60;
    return `${String(minutes).padStart(2,'0')}:${String(seconds).padStart(2,'0')},${String(cs%100).padStart(2,'0')}`;
}
export function parseTime(text) {
    const value=text.trim().replace(',','.');
    if(!/^\d+(?::[0-5]\d)?(?:\.\d{1,3})?$/.test(value)) throw new Error('Zeit als Sekunden oder mm:ss,hh eingeben.');
    const parts=value.split(':'); return Math.round((parts.length===2?Number(parts[0])*60+Number(parts[1]):Number(parts[0]))*1000);
}
export function formatTimeMs(ms) {return ms==null||!Number.isFinite(ms)?'—':formatTime(ms)+Math.floor(Math.max(0,ms))%10;}
export function toast(message,error=false) { const el=$('#toast'); if(el) { el.textContent=message; el.classList.toggle('error',error); el.hidden=false; } }
function uuid() {
    // crypto.randomUUID ist auf HTTP-LAN-Adressen nicht immer verfuegbar.
    const bytes=new Uint8Array(16); crypto.getRandomValues(bytes); bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
    const s=[...bytes].map(x=>x.toString(16).padStart(2,'0')).join(''); return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`;
}
export function instantButton(element,fn) {
    element.addEventListener('pointerdown',e=>{ if(element.disabled || !e.isPrimary || e.button!==0) return; const at=performance.now(); e.preventDefault(); fn(at); });
    element.addEventListener('keydown',e=>{ if(element.disabled || e.repeat || !['Enter',' '].includes(e.key)) return; const at=performance.now(); e.preventDefault(); fn(at); });
}

/** Zeitabgleich auf Anwendungsebene. RTT/2 ist nur ein Indikator, keine Fehlergarantie. */
export class LiveConnection {
    constructor(role) {
        this.role=role;this.state=null;this.ws=null;this.samples=[];this.best=null;this.lastSync=0;this.lastStatePerf=0;this.frozen=null;this.suspended=false;this.stopped=new Map();this.started=new Map();this.halted=false;
        this.pendingKey='vm.pending.'+role;this.dataKey='vm.dataId.'+role;this.dataId=null;this.voidedEvents=new Set();
        try{this.dataId=localStorage.getItem(this.dataKey);}catch{}
        try { this.pending=JSON.parse(localStorage.getItem(this.pendingKey)||'[]'); if(!Array.isArray(this.pending)) this.pending=[]; }
        catch { this.pending=[]; }
        this.onState=()=>{};this.onChange=()=>{};this.onError=message=>toast(message,true);this.onPending=()=>{};
        this.lastPerf=performance.now();this.lastWall=Date.now();
        document.addEventListener('visibilitychange',()=>{
            if(document.hidden) this.flagSuspension();
            else { this.samples=[];this.best=null;this.sync(); }
        });
        window.addEventListener('pagehide',()=>this.flagSuspension());
    }
    start() {
        this.connect(); this.onPending();
        this.timer=setInterval(()=>{
            const p=performance.now(),w=Date.now();
            if(Math.abs((w-this.lastWall)-(p-this.lastPerf))>150 || p-this.lastPerf>5000) this.flagSuspension();
            this.lastPerf=p;this.lastWall=w;
            if(this.state?.activeHeat?.status==='ARMED' && this.frozen && p-this.frozen.created>120000) { this.setReady(false);this.onError('Bereitschaft abgelaufen. Bitte erneut bestätigen.'); }
            this.sync();this.flush();this.onChange();
        },1000);
    }
    connect() {
        if(this.halted) return;
        const ws=new WebSocket(`${location.protocol==='https:'?'wss':'ws'}://${location.host}/ws`); this.ws=ws;
        ws.onopen=()=>{this.samples=[];this.best=null;this.lastSync=0;this.sync();this.flush();this.onChange();};
        ws.onmessage=e=>{try{this.message(JSON.parse(e.data));}catch(error){this.onError('Antwort konnte nicht verarbeitet werden: '+error.message);}};
        ws.onclose=e=>{
            this.lastSync=0;
            if(e.code===1008) {this.halted=true;this.onError(e.reason||'Rolle bereits geöffnet. Alten Tab schließen und diese Seite neu laden.');}
            else setTimeout(()=>this.connect(),1200);
            this.onChange();
        };
        ws.onerror=()=>{};
    }
    connected() { return this.ws?.readyState===WebSocket.OPEN && this.lastSync>0 && performance.now()-this.lastSync<5000; }
    send(message) { if(this.ws?.readyState===WebSocket.OPEN) this.ws.send(JSON.stringify(message)); }
    sync() {
        if(this.ws?.readyState!==WebSocket.OPEN) return;
        const running=this.state?.activeHeat?.status==='RUNNING';
        this.send({type:'SYNC',c0:performance.now(),rtt:this.best?.rtt??null,timingValid:running?!!this.frozen&&!this.suspended:this.clockReady()});
    }
    clockReady() { return !document.hidden && this.lastSync>0 && this.samples.length>=3 && this.best!=null && this.best.rtt<=(this.state?.maxRoundtripMs??100) && performance.now()-this.lastSync<5000; }
    message(n) {
        const now=performance.now();
        if(n.type==='SYNC') {
            if(this.state && n.bootId!==this.state.bootId) {this.flagSuspension();this.samples=[];this.best=null;return;}
            const rtt=(now-n.c0)-(n.s2-n.s1), offset=((n.s1-n.c0)+(n.s2-now))/2;
            if(Number.isFinite(rtt) && Number.isFinite(offset) && rtt>=-1 && rtt<1500) {
                this.lastSync=now;this.samples=this.samples.filter(s=>now-s.at<15000);
                this.samples.push({rtt:Math.max(0,rtt),offset,at:now});this.samples=this.samples.slice(-20);
                this.best=this.samples.reduce((a,b)=>a.rtt<=b.rtt?a:b);
                if(this.samples.length<8) setTimeout(()=>this.sync(),60);
            }
            this.onChange();
        } else if(n.type==='STATE') {
            const previous=this.state;
            const replaced=!!this.dataId && !!n.dataId && this.dataId!==n.dataId;
            if(replaced) {
                this.pending.forEach(p=>this.voidedEvents.add(p.event.eventId));
                this.pending=[];this.started.clear();this.stopped.clear();this.frozen=null;this.samples=[];this.best=null;this.lastSync=0;this.suspended=false;
                this.persist();this.onPending();
            }
            if(n.dataId){this.dataId=n.dataId;try{localStorage.setItem(this.dataKey,n.dataId);}catch{}}
            if(previous && previous.bootId!==n.bootId && !replaced) {this.frozen=null;this.samples=[];this.best=null;this.suspended=true;this.onError('Serverneustart erkannt. Vorhandene Messung in der Verwaltung prüfen.');}
            if(previous?.activeHeat?.id!==n.activeHeat?.id) {this.frozen=null;this.suspended=false;}
            const falseStartOf=n.activeHeat?.falseStartOf;
            if(falseStartOf) {
                const obsolete=this.pending.filter(p=>p.event.heatId===falseStartOf);
                if(obsolete.length) {
                    obsolete.forEach(p=>this.voidedEvents.add(p.event.eventId));
                    this.pending=this.pending.filter(p=>p.event.heatId!==falseStartOf);this.persist();this.onPending();
                }
                this.started.delete(falseStartOf);this.stopped.delete(falseStartOf);
            }
            this.state=n;this.lastStatePerf=now;this.onState(n);this.onChange();
        } else if(n.type==='ACK') {
            this.pending=this.pending.filter(p=>p.event.eventId!==n.eventId);this.persist();this.onPending();this.onChange();
        } else if(n.type==='ERROR') {
            if(this.voidedEvents.has(n.eventId)) return;
            const item=this.pending.find(p=>p.event.eventId===n.eventId);
            if(item) {item.status='rejected';item.error=n.message;this.persist();this.onPending();}
            this.onError(n.message);this.onChange();
        }
    }
    flagSuspension() {
        const status=this.state?.activeHeat?.status;
        if(status==='RUNNING') this.suspended=true;
        if(status==='ARMED') {if(this.frozen) this.setReady(false);this.frozen=null;}
        this.samples=[];this.best=null;this.onChange();
    }
    setReady(ready) {
        const heat=this.state?.activeHeat;
        if(!heat || heat.status!=='ARMED') return;
        if(ready && (!this.clockReady() || !this.connected())) throw new Error('Zeitabgleich noch nicht stabil. Bitte kurz warten.');
        this.frozen=ready?{heatId:heat.id,offset:this.best.offset,qualityMs:this.best.rtt/2,created:performance.now()}:null;
        if(ready) this.suspended=false;
        // WebSocket erhält die Reihenfolge: der Server sieht den frischen Abgleich vor READY.
        this.sync();this.send({type:'READY',heatId:heat.id,ready});this.onChange();
    }
    hasCalibration() {return this.frozen?.heatId===this.state?.activeHeat?.id;}
    estimatedNow() {
        const offset=this.frozen?.offset??this.best?.offset;
        return offset==null?(this.state?.serverNow??0)+(performance.now()-this.lastStatePerf):performance.now()+offset;
    }
    startRace(at) {
        const h=this.state?.activeHeat;
        if(!h || h.status!=='ARMED' || !this.hasCalibration() || !this.connected() || !this.clockReady() || this.started.has(h.id)) return;
        const required=['starter',...h.lanes.map(l=>'lane'+l.lane)];
        if(!required.every(r=>this.state.devices.some(d=>d.role===r&&d.online&&d.ready))) throw new Error('Es sind noch nicht alle Stationen bereit.');
        const e=this.event('START',at);this.started.set(h.id,e);this.saveEvent(e);
    }
    stopRace(at) {
        const h=this.state?.activeHeat;
        if(!h || h.status!=='RUNNING' || !this.hasCalibration() || this.stopped.has(h.id) || this.localEvent('STOP',h.id)) return;
        const e=this.event('STOP',at);this.stopped.set(h.id,e);this.saveEvent(e);
    }
    falseStart() {
        const h=this.state?.activeHeat;
        if(this.role!=='starter' || !h || !['RUNNING','FINISHED'].includes(h.status)) return;
        if(!this.connected()) throw new Error('Fehlstart konnte nicht übertragen werden. Verbindung zum Laptop prüfen.');
        if(this.pending.some(p=>p.event.type==='FALSE_START'&&p.event.heatId===h.id)) return;
        this.saveEvent({type:'FALSE_START',eventId:uuid(),heatId:h.id,bootId:this.state.bootId,
            atMs:this.estimatedNow(),qualityMs:0,needsReview:false,note:''});
    }
    event(type,at) {
        return {type,eventId:uuid(),heatId:this.state.activeHeat.id,bootId:this.state.bootId,atMs:at+this.frozen.offset,qualityMs:this.frozen.qualityMs,needsReview:this.suspended,note:this.suspended?'Browser war im Hintergrund, pausiert oder Uhrverhalten auffällig. Zeit prüfen.':''};
    }
    saveEvent(event) {
        this.pending.push({event,heatNumber:this.state.activeHeat.number,elapsedMs:event.type==='STOP'?Math.round(event.atMs-this.state.activeHeat.startMs):null,status:'pending',error:''});this.persist();this.onPending();this.onChange();this.flush();
    }
    localEvent(type,heatId) { return this.pending.find(p=>p.event.type===type && p.event.heatId===heatId)?.event || (type==='STOP'?this.stopped:this.started).get(heatId); }
    persist() {
        try {localStorage.setItem(this.pendingKey,JSON.stringify(this.pending));}
        catch {this.onError('Der Browser konnte die lokale Sicherung nicht schreiben. Seite offen lassen und das Ergebnis am Laptop kontrollieren.');}
    }
    flush() {
        if(this.ws?.readyState!==WebSocket.OPEN) return;
        for(const item of this.pending) if(item.status==='pending') this.send(item.event);
    }
    discard(eventId) {this.pending=this.pending.filter(p=>p.event.eventId!==eventId);this.persist();this.onPending();this.onChange();}
}
```

## src/main/resources/static/js/data.js

```javascript
import {$,api,escapeHtml,toast} from './common.js';

let summary=null,preview=null,restoreFile=null;
const titles={participants:'Teilnehmer (inkl. Archiv)',heats:'Läufe',results:'Ergebnisse',disciplines:'Disziplinen',classes:'Wertungsklassen'};
function countsMarkup(counts){return Object.entries(titles).map(([key,label])=>`<div class="data-count"><strong>${counts[key]}</strong><span>${label}</span></div>`).join('');}
function restoreReady(){
    const possible=!!preview&&!!restoreFile&&summary?.empty&&summary?.changeAllowed;
    $('#restore-confirmation').disabled=!possible;
    $('#restore-button').disabled=!possible||$('#restore-confirmation').value!=='WIEDERHERSTELLEN';
    $('#restore-hint').textContent=!preview?'Datei auswählen und prüfen.':!summary?.empty?'Der Datenbestand ist nicht leer. Vor der Wiederherstellung sichern und über „Alles löschen“ zurücksetzen.':'Die Sicherung ist geprüft und kann in den leeren Datenbestand eingelesen werden.';
}
export function renderDataSummary(value){
    summary=value;$('#data-counts').innerHTML=countsMarkup(value.counts);
    $('#reset-open').disabled=!value.changeAllowed;
    $('#reset-hint').textContent=value.changeAllowed?'Teilnehmer, Läufe, Ergebnisse, Disziplinen, Wertungsklassen und Protokoll werden gelöscht.':'Zuerst den aktivierten oder laufenden Lauf abschließen oder abbrechen.';
    restoreReady();
}
export function initDataTools(onChanged){
    const keys=Object.keys(titles);
    const field=key=>$('#export-'+key);
    function selected(){return Object.fromEntries(keys.map(key=>[key,field(key).checked]));}
    function dependencies(){
        const results=field('results').checked,heats=field('heats').checked||results,people=field('participants').checked||heats;
        if(results)field('heats').checked=true;
        if(heats){field('participants').checked=true;field('disciplines').checked=true;}
        if(people)field('classes').checked=true;
        field('heats').disabled=results;field('participants').disabled=heats;field('disciplines').disabled=heats;field('classes').disabled=people;
        $('#export-button').disabled=!Object.values(selected()).some(Boolean);
    }
    keys.forEach(key=>field(key).addEventListener('change',dependencies));dependencies();
    $('#backup-export-form').addEventListener('submit',async e=>{
        e.preventDefault();const button=$('#export-button');button.disabled=true;
        try {
            const response=await fetch('/api/admin/data/export',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(selected())});
            if(!response.ok){const error=await response.json();throw new Error(error.message||'Export fehlgeschlagen.');}
            const blob=await response.blob(),url=URL.createObjectURL(blob),link=document.createElement('a');
            link.href=url;link.download=response.headers.get('Content-Disposition')?.match(/filename="([^"]+)"/)?.[1]||'vereinsmeisterschaft-sicherung.json';
            document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
            toast('Sicherungsdatei heruntergeladen. Sie kann über „Wiederherstellen“ eingelesen werden.');
        }catch(error){toast(error.message,true);}finally{dependencies();}
    });
    $('#backup-file').addEventListener('change',()=>{restoreFile=null;preview=null;$('#backup-preview').hidden=true;$('#restore-confirmation').value='';restoreReady();});
    $('#backup-preview-form').addEventListener('submit',async e=>{
        e.preventDefault();const file=$('#backup-file').files[0];if(!file)return;
        const button=$('#preview-button');button.disabled=true;preview=null;restoreFile=null;restoreReady();
        try {
            const data=new FormData();data.append('file',file);
            const result=await api('/admin/data/preview',{method:'POST',body:data});
            if($('#backup-file').files[0]!==file)return;
            preview=result;restoreFile=file;
            $('#backup-preview').innerHTML=`<h3>Geprüfte Sicherung</h3><p>Erstellt am ${escapeHtml(new Date(result.exportedAt).toLocaleString('de-DE'))}</p><div class="data-counts">${countsMarkup(result.contents)}</div><p class="small muted">${result.selection.results?'Mit Ergebniszeiten und historischen Versuchen.':'Ohne Ergebniszeiten. Gesicherte Läufe werden als geplante Läufe vorbereitet.'}</p>`;
            $('#backup-preview').hidden=false;restoreReady();
        }catch(error){$('#backup-preview').hidden=true;toast(error.message,true);}finally{button.disabled=false;}
    });
    $('#restore-confirmation').addEventListener('input',restoreReady);
    $('#restore-form').addEventListener('submit',async e=>{
        e.preventDefault();if(!restoreFile||!preview)return;
        const button=$('#restore-button');button.disabled=true;
        try {
            const data=new FormData();data.append('file',restoreFile);data.append('confirmation',$('#restore-confirmation').value);
            await api('/admin/data/restore',{method:'POST',body:data});
            preview=null;restoreFile=null;$('#backup-preview').hidden=true;$('#backup-preview-form').reset();$('#restore-form').reset();
            await onChanged();toast('Sicherung wiederhergestellt. Die Stationen warten auf den nächsten aktivierten Lauf.');
        }catch(error){toast(error.message,true);}finally{restoreReady();}
    });
    $('#reset-open').addEventListener('click',()=>{$('#reset-confirmation').value='';$('#reset-error').textContent='';$('#reset-submit').disabled=true;$('#reset-dialog').showModal();});
    $('#reset-cancel').addEventListener('click',()=>$('#reset-dialog').close());
    $('#reset-confirmation').addEventListener('input',()=>$('#reset-submit').disabled=$('#reset-confirmation').value!=='ALLES LÖSCHEN');
    $('#reset-form').addEventListener('submit',async e=>{
        e.preventDefault();const button=$('#reset-submit');button.disabled=true;
        try {
            await api('/admin/data/reset',{method:'POST',body:{confirmation:$('#reset-confirmation').value}});
            $('#reset-dialog').close();await onChanged();toast('Alle Wettkampfdaten gelöscht. Der Datenbestand ist jetzt leer.');
        }catch(error){$('#reset-error').textContent=error.message;button.disabled=false;}
    });
}
```

## src/main/resources/static/js/login.js

```javascript
import {api, destination} from './common.js';
const role=document.querySelector('#role');
const requested=new URLSearchParams(location.search).get('role');
if([...role.options].some(o=>o.value===requested)) role.value=requested;
document.querySelector('#login-form').addEventListener('submit',async e=>{
    e.preventDefault(); const button=e.currentTarget.querySelector('button'); button.disabled=true;
    try { const me=await api('/login',{method:'POST',body:{role:role.value,code:document.querySelector('#code').value}}); location.href=destination(me.role); }
    catch(error) { document.querySelector('#login-error').textContent=error.message; }
    finally { button.disabled=false; }
});
```

## src/main/resources/static/js/manager.js

```javascript
import {$,api,ensureRole,LiveConnection,escapeHtml,formatTime,formatTimeMs,parseTime,roleLabel,statusLabel,toast} from './common.js';
import {initDataTools,renderDataSummary} from './data.js';
await ensureRole('admin');
const live=new LiveConnection('admin');let participants=[],heats=[],disciplines=[],classes=[],rankings=[],comparisons=[],lastSignature='',refreshing=false,refreshAgain=false;
const html=escapeHtml;
const trashIcon='<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M3 6h18M9 6V4h6v2M5 6l1 14h12l1-14M10 10v6M14 10v6"/></svg>';
const color=status=>status==='FINISHED'?'good':status==='RUNNING'?'good':['ABORTED','INTERRUPTED','FALSE_START'].includes(status)?'bad':'warn';
const badge=status=>`<span class="badge ${color(status)}">${html(statusLabel(status))}</span>`;
const name=p=>`${p.firstName} ${p.lastName}`;
const classKey=c=>JSON.stringify([c.trainingGroup,c.category]);
const classLabel=c=>(c.trainingGroup||'Ohne Trainingsgruppe')+' – '+(c.category||'Ohne Wertung');
initDataTools(refresh);
function tab(id) {document.querySelectorAll('.tab-panel').forEach(s=>s.hidden=s.id!=='tab-'+id);document.querySelectorAll('[data-tab]').forEach(b=>b.classList.toggle('selected',b.dataset.tab===id));}
document.querySelector('.tabs').addEventListener('click',e=>{const id=e.target.closest('[data-tab]')?.dataset.tab;if(id) tab(id);});
$('#people-search').addEventListener('input',renderPeople);$('#results-search').addEventListener('input',renderResults);$('#group-filter').addEventListener('change',renderSelects);
$('#ranking-mode').addEventListener('change',()=>refresh().catch(e=>toast(e.message,true)));
$('#comparison-discipline').addEventListener('change',renderResults);
$('#result-tabs').addEventListener('click',e=>{
    const button=e.target.closest('[data-result-view]');if(!button)return;
    const overall=button.dataset.resultView==='overall';
    $('#overall-results').hidden=!overall;$('#individual-results').hidden=overall;
    document.querySelectorAll('[data-result-view]').forEach(b=>{const selected=b===button;b.className=selected?'primary':'quiet';b.setAttribute('aria-pressed',String(selected));});
});
live.onError=message=>toast(message,true);
live.onChange=()=>{const connected=live.connected();$('#connection').textContent=connected?'Live verbunden':'Verbindung unterbrochen';};
live.onState=state=>{
    renderLive(state);
    const signature=JSON.stringify([state.dataId,state.activeHeat]);
    if(signature!==lastSignature) {lastSignature=signature;refresh().catch(e=>toast(e.message,true));}
};
async function refresh() {
    if(refreshing) {refreshAgain=true;return;}
    refreshing=true;
    try {
        const mode=$('#ranking-mode').value;let data;
        [participants,heats,disciplines,classes,rankings,comparisons,data]=await Promise.all([api('/admin/participants'),api('/admin/heats'),api('/admin/disciplines'),api('/admin/classes'),api('/admin/rankings?mode='+(mode==='disciplines'?'classes':mode)),api('/admin/discipline-rankings'),api('/admin/data')]);
        if(mode!==$('#ranking-mode').value){refreshAgain=true;return;}
        renderPeople();renderDisciplines();renderClasses();renderHeats();renderResults();renderGroups();renderSelects();renderDataSummary(data);
        const audit=await api('/admin/audit');
        $('#audit').innerHTML=audit.map(a=>`<p><strong>${html(a.RECORDED_AT)} · ${html(a.ACTOR)}</strong><br>${html(a.ACTION_TEXT)}</p>`).join('')||'<p>Noch keine Einträge.</p>';
    } finally {refreshing=false;if(refreshAgain){refreshAgain=false;await refresh();}}
}
function renderPeople() {
    $('#people-count').textContent=participants.length;
    const q=$('#people-search').value.trim().toLocaleLowerCase('de');
    const list=participants.filter(p=>Object.values(p).join(' ').toLocaleLowerCase('de').includes(q));
    $('#people-body').innerHTML=list.map(p=>`<tr><td>${html(p.id)}</td><td><strong>${html(name(p))}</strong></td><td>${p.birthYear}</td><td>${html(p.category)}</td><td>${html(p.trainingGroup)}</td><td><button type="button" class="danger icon-button" data-delete-person="${html(p.id)}" aria-label="${html(name(p))} löschen" title="Teilnehmer löschen">${trashIcon}</button></td></tr>`).join('')||'<tr><td colspan="6">Noch keine passenden Teilnehmer. Importiere die Excel-Datei oder erfasse eine Nachmeldung.</td></tr>';
}
$('#people-body').addEventListener('click',async e=>{
    const button=e.target.closest('[data-delete-person]');if(!button)return;
    const person=participants.find(p=>p.id===button.dataset.deletePerson);if(!person)return;
    if(!confirm(`${name(person)} (ID ${person.id}) wirklich löschen?\n\nDie Person wird aus der Teilnehmerliste und aus geplanten Läufen entfernt. Dadurch leere Läufe werden abgebrochen. Vorhandene Ergebnisse bleiben erhalten. Die Teilnehmer-ID bleibt reserviert.`))return;
    button.disabled=true;
    try{
        await api('/admin/participants/'+encodeURIComponent(person.id),{method:'DELETE'});
        await refresh();toast('Teilnehmer gelöscht. Vorhandene Ergebnisse bleiben erhalten.');
    }catch(error){toast(error.message,true);button.disabled=false;}
});
function renderGroups() {
    const current=$('#group-filter').value;
    $('#group-filter').innerHTML='<option value="">Alle Trainingsgruppen</option>'+[...new Set(participants.map(p=>p.trainingGroup).filter(Boolean))].sort().map(g=>`<option value="${html(g)}">${html(g)}</option>`).join('');
    $('#group-filter').value=[...$('#group-filter').options].some(o=>o.value===current)?current:'';
}
function renderDisciplines() {
    $('#discipline-count').textContent=disciplines.length;
    $('#discipline-body').innerHTML=disciplines.map(d=>`<tr><td><strong>${html(d.name)}</strong></td><td>${d.heatCount}</td><td><button type="button" class="danger icon-button" data-delete-discipline="${html(d.name)}" aria-label="${html(d.name)} löschen" title="${d.heatCount?'Bereits in Läufen verwendet':'Disziplin löschen'}" ${d.heatCount?'disabled':''}>${trashIcon}</button></td></tr>`).join('')||'<tr><td colspan="3">Noch keine Disziplinen angelegt.</td></tr>';
    const current=$('#discipline').value;
    $('#discipline').innerHTML=`<option value="">${disciplines.length?'Disziplin auswählen':'Zuerst unter Disziplinen eine Disziplin anlegen'}</option>`+disciplines.map(d=>`<option value="${html(d.name)}">${html(d.name)}</option>`).join('');
    $('#discipline').value=disciplines.some(d=>d.name===current)?current:'';
    const compared=$('#comparison-discipline').value;
    $('#comparison-discipline').innerHTML='<option value="">Alle Disziplinen · getrennte Listen</option>'+disciplines.map(d=>`<option value="${html(d.name)}">${html(d.name)}</option>`).join('');
    $('#comparison-discipline').value=disciplines.some(d=>d.name===compared)?compared:'';
}
function renderClasses(){
    $('#class-count').textContent=classes.length;
    $('#class-body').innerHTML=classes.map(c=>`<tr><td><strong>${html(classLabel(c))}</strong></td><td>${c.participantCount}</td><td><button type="button" class="danger icon-button" data-delete-class="${html(classKey(c))}" aria-label="${html(classLabel(c))} löschen" title="${c.participantCount?'Bereits Teilnehmern zugeordnet':'Wertungsklasse löschen'}" ${c.participantCount?'disabled':''}>${trashIcon}</button></td></tr>`).join('')||'<tr><td colspan="3">Noch keine Wertungsklassen angelegt.</td></tr>';
    const current=$('#person-class').value;
    $('#person-class').innerHTML='<option value="">Wertungsklasse auswählen</option>'+classes.map(c=>`<option value="${html(classKey(c))}">${html(classLabel(c))}</option>`).join('');
    $('#person-class').value=classes.some(c=>classKey(c)===current)?current:'';
    $('#training-groups').innerHTML=[...new Set(classes.map(c=>c.trainingGroup).filter(Boolean))].map(g=>`<option value="${html(g)}"></option>`).join('');
}
$('#class-form').addEventListener('submit',async e=>{
    e.preventDefault();const button=e.currentTarget.querySelector('button');button.disabled=true;
    try{await api('/admin/classes',{method:'POST',body:{trainingGroup:$('#class-group').value,category:$('#class-category').value}});$('#class-form').reset();await refresh();toast('Wertungsklasse angelegt.');}
    catch(error){toast(error.message,true);}finally{button.disabled=false;}
});
$('#class-body').addEventListener('click',async e=>{
    const button=e.target.closest('[data-delete-class]');if(!button)return;
    const [group,category]=JSON.parse(button.dataset.deleteClass);
    if(!confirm(`Wertungsklasse „${group} – ${category}“ löschen?`))return;
    button.disabled=true;
    try{await api('/admin/classes?group='+encodeURIComponent(group)+'&category='+encodeURIComponent(category),{method:'DELETE'});await refresh();toast('Wertungsklasse gelöscht.');}
    catch(error){toast(error.message,true);button.disabled=false;}
});
$('#discipline-form').addEventListener('submit',async e=>{
    e.preventDefault();const button=e.currentTarget.querySelector('button');button.disabled=true;
    try{await api('/admin/disciplines',{method:'POST',body:{name:$('#discipline-name').value}});$('#discipline-form').reset();await refresh();toast('Disziplin angelegt und bei der Laufplanung auswählbar.');}
    catch(error){toast(error.message,true);}finally{button.disabled=false;}
});
$('#discipline-body').addEventListener('click',async e=>{
    const button=e.target.closest('[data-delete-discipline]');if(!button)return;
    const name=button.dataset.deleteDiscipline;
    if(!confirm(`Disziplin „${name}“ wirklich löschen?`))return;
    button.disabled=true;
    try{await api('/admin/disciplines?name='+encodeURIComponent(name),{method:'DELETE'});await refresh();toast('Disziplin gelöscht.');}
    catch(error){toast(error.message,true);button.disabled=false;}
});
function renderSelects() {
    const selected=[1,2,3].map(i=>$('#lane-'+i)?.value||'');
    const group=$('#group-filter').value;
    $('#lane-selects').innerHTML=[1,2,3].map(i=>`<label>Bahn ${i}<select id="lane-${i}"><option value="">Bahn frei lassen</option>${participants.filter(p=>!group||p.trainingGroup===group||p.id===selected[i-1]).map(p=>`<option value="${html(p.id)}">${html(p.lastName+', '+p.firstName+' · '+p.birthYear+' · '+p.id)}</option>`).join('')}</select></label>`).join('');
    selected.forEach((value,i)=>$('#lane-'+(i+1)).value=participants.some(p=>p.id===value)?value:'');
}
$('#import-form').addEventListener('submit',async e=>{
    e.preventDefault();const button=e.currentTarget.querySelector('button');button.disabled=true;
    try {const data=new FormData();data.append('file',$('#import-file').files[0]);const result=await api('/admin/import',{method:'POST',body:data});toast(result.added+' Teilnehmer importiert.');$('#import-form').reset();await refresh();}
    catch(error){toast(error.message,true);}finally{button.disabled=false;}
});
$('#person-form').addEventListener('submit',async e=>{
    e.preventDefault();const form=e.currentTarget;const body=Object.fromEntries(new FormData(form));body.birthYear=Number(body.birthYear);
    const [group,category]=JSON.parse($('#person-class').value);body.trainingGroup=group;body.category=category;
    try{await api('/admin/participants',{method:'POST',body});form.reset();toast('Teilnehmer hinzugefügt.');await refresh();}catch(error){toast(error.message,true);}
});
$('#heat-form').addEventListener('submit',async e=>{
    e.preventDefault();const button=e.currentTarget.querySelector('button');button.disabled=true;
    try{
        const lanes=[1,2,3].map(lane=>({lane,participantId:$('#lane-'+lane).value})).filter(l=>l.participantId);
        const result=await api('/admin/heats',{method:'POST',body:{discipline:$('#discipline').value,label:$('#heat-label').value,lanes}});
        [1,2,3].forEach(i=>$('#lane-'+i).value='');toast('Lauf '+result.number+' angelegt. Zum Starten unten aktivieren.');await refresh();
    }catch(error){toast(error.message,true);}finally{button.disabled=false;}
});
function renderHeats() {
    $('#heats-body').innerHTML=[...heats].sort((a,b)=>a.number-b.number).map(h=>`<tr><td><strong>#${h.number}</strong></td><td>${html(h.discipline)}<small>${html(h.label)}</small></td><td>${h.lanes.map(l=>`<div>${l.lane}: ${html(name(l))}</div>`).join('')}</td><td>${badge(h.status)}</td><td><div class="actions">${h.status==='PLANNED'?`<button class="primary" data-action="arm" data-id="${h.id}">Aktivieren</button>`:''}${['ARMED','RUNNING'].includes(h.status)?`<button class="danger" data-action="abort" data-id="${h.id}">Abbrechen</button>`:''}<button class="quiet" data-action="repeat" data-id="${h.id}">Kopieren</button></div></td></tr>`).join('')||'<tr><td colspan="5">Noch keine Läufe geplant.</td></tr>';
}
$('#heats-body').addEventListener('click',async e=>{
    const button=e.target.closest('[data-action]');if(!button)return;
    const {action,id}=button.dataset;
    if(action==='abort' && !confirm('Lauf wirklich abbrechen? Bereits erfasste Zeiten bleiben sichtbar. Für einen Neustart anschließend den Lauf kopieren.'))return;
    button.disabled=true;
    try{await api(`/admin/heats/${id}/${action}`,{method:'POST'});await refresh();if(action==='arm')tab('overview');toast(action==='arm'?'Lauf aktiviert. Stationen können ihre Bereitschaft bestätigen.':action==='abort'?'Lauf abgebrochen.':'Lauf als neuen geplanten Lauf kopiert.');}catch(error){toast(error.message,true);button.disabled=false;}
});
function renderLive(state) {
    const h=state.activeHeat;
    $('#live-status').textContent=h?statusLabel(h.status):'Kein Lauf aktiv';$('#live-status').className='badge '+(h?color(h.status):'');
    $('#devices').innerHTML=state.devices.map(d=>`<div class="device-card"><h3>${roleLabel(d.role)}</h3><span class="badge ${d.online?(d.ready?'good':'warn'):'bad'}">${d.online?(d.ready?'Bereit':'Verbunden'):'Offline'}</span><p>${d.roundtripMs==null?'Zeitabgleich ausstehend':`RTT ${d.roundtripMs.toFixed(0)} ms · ${d.timingValid?'Abgleich bereit':'Abgleich prüfen'}`}</p></div>`).join('');
    if(!h){$('#active-heat').innerHTML='Lege unter „Läufe“ einen Lauf an und aktiviere ihn.';return;}
    $('#active-heat').classList.remove('empty');
    $('#active-heat').innerHTML=`<div class="section-heading"><div><h3>Lauf ${h.number} · ${html(h.discipline)}</h3><p class="small muted">${html(h.label)}</p></div></div><div class="heat-lanes">${[1,2,3].map(i=>{const l=h.lanes.find(x=>x.lane===i);return `<div class="heat-lane"><span class="eyebrow dark">BAHN ${i}</span><strong>${l?html(name(l)):'Frei'}</strong><div class="lane-time" data-live-lane="${i}">${l?formatTime(l.elapsedMs):'—'}</div><p class="small muted">${l?html(statusLabel(l.status))+(l.needsReview?' · Prüfen':''):''}</p></div>`;}).join('')}</div>`;
}
function animate() {
    const h=live.state?.activeHeat;
    if(h) for(const el of document.querySelectorAll('[data-live-lane]')) {
        const l=h.lanes.find(l=>l.lane===Number(el.dataset.liveLane));
        el.textContent=!l?'—':l.elapsedMs!=null?formatTime(l.elapsedMs):l.status==='PENDING'&&h.status==='RUNNING'?formatTime(live.estimatedNow()-h.startMs):l.status==='PENDING'?'00:00,00':l.status;
    }
    requestAnimationFrame(animate);
}
function renderResults() {
    const q=$('#results-search').value.toLocaleLowerCase('de');
    renderRankings(q);
    const rows=heats.flatMap(h=>h.lanes.map(l=>({h,l}))).filter(({h,l})=>[h.discipline,h.number,l.firstName,l.lastName,l.birthYear,l.category,l.trainingGroup].join(' ').toLocaleLowerCase('de').includes(q));
    rows.sort((a,b)=>a.h.discipline.localeCompare(b.h.discipline,'de')||b.l.birthYear-a.l.birthYear||a.l.category.localeCompare(b.l.category,'de')||(a.l.elapsedMs??Infinity)-(b.l.elapsedMs??Infinity)||a.h.number-b.h.number);
    $('#results-body').innerHTML=rows.map(({h,l})=>`<tr><td>#${h.number} / ${l.lane}<small>${html(statusLabel(h.status))}</small></td><td><strong>${html(name(l))}</strong><small>${html(l.trainingGroup)}</small></td><td>${html(h.discipline)}</td><td>${l.birthYear}<small>${html(l.category)}</small></td><td><strong>${formatTime(l.elapsedMs)}</strong>${l.needsReview?'<small class="error">Prüfen</small>':''}</td><td>${html(statusLabel(l.status))}<small>${html(l.note)}</small></td><td>${['RUNNING','FINISHED','INTERRUPTED'].includes(h.status)?`<button class="quiet" data-edit-heat="${h.id}" data-edit-lane="${l.lane}">Korrigieren</button>`:'—'}</td></tr>`).join('')||'<tr><td colspan="7">Noch keine Ergebnisse vorhanden.</td></tr>';
}
function renderRankings(q) {
    const mode=$('#ranking-mode').value;
    $('#comparison-discipline-label').hidden=mode!=='disciplines';
    $('#ranking-export').href=mode==='disciplines'?'/api/admin/discipline-rankings.csv?discipline='+encodeURIComponent($('#comparison-discipline').value):'/api/admin/rankings.csv?mode='+mode;
    $('#ranking-note').textContent=mode==='disciplines'?'Vergleich derselben Disziplin über alle Trainingsgruppen und Wertungen. Pro Person zählt die letzte gültige Zeit; unterschiedliche Disziplinen bleiben in getrennten Listen.':'Alle bereits geplanten Disziplinen der '+(mode==='classes'?'Wertungsklasse':'Trainingsgruppe')+' gehören zur Wertung. Fehlende oder ungeprüfte Zeiten stehen ohne Platzierung unten. Die Rangliste ist bis zum Abschluss aller Läufe vorläufig. Zeitgleichheit ergibt denselben Platz.';
    if(mode==='disciplines'){renderComparisons(q);return;}
    const sections=rankings.map(group=>{
        const label=group.category==null?(group.trainingGroup||'Ohne Trainingsgruppe'):classLabel(group);
        const rows=group.standings.filter(row=>[label,...group.disciplines,...Object.values(row.participant)].join(' ').toLocaleLowerCase('de').includes(q));
        if(!rows.length)return '';
        return `<section class="ranking-group" data-ranking-group="${html(group.trainingGroup)}" data-ranking-category="${html(group.category??'')}"><div class="section-heading"><div><h3>${html(label)}</h3><p class="small muted">${group.disciplines.length?group.disciplines.map(html).join(' · '):'Für diese Gruppe sind noch keine Disziplinen geplant.'}</p></div><span class="badge">${group.standings.filter(r=>r.complete).length} / ${group.standings.length} vollständig</span></div><div class="table-wrap"><table><thead><tr><th>Platz</th><th>Schwimmer</th><th>Einzelzeiten</th><th>Gesamtzeit</th><th>Wertung</th></tr></thead><tbody>${rows.map(row=>`<tr data-ranking-person="${html(row.participant.id)}" class="${row.place===1?'winner':''}"><td><span class="rank-number">${row.place??'—'}</span></td><td><strong>${html(name(row.participant))}</strong><small>${row.participant.birthYear} · ${html(row.participant.category)} · ID ${html(row.participant.id)}</small></td><td>${row.times.map(t=>`<div class="score-time"><span>${html(t.discipline)}</span><strong>${t.elapsedMs==null?html(statusLabel(t.status)):formatTimeMs(t.elapsedMs)}</strong>${t.heatNumber!=null?`<small>Lauf ${t.heatNumber}</small>`:''}</div>`).join('')||'—'}</td><td><strong class="total-time">${row.complete?formatTimeMs(row.totalMs):'—'}</strong>${!row.complete&&row.completed?`<small>Teilzeit: ${formatTimeMs(row.totalMs)}</small>`:''}</td><td><span class="badge ${row.complete?'good':'warn'}">${row.complete?'Vollständig':group.disciplines.length?'Unvollständig':'Noch keine Läufe'}</span><small>${row.completed} / ${group.disciplines.length} gültige Zeiten</small></td></tr>`).join('')}</tbody></table></div></section>`;
    }).join('');
    $('#rankings').innerHTML=sections||'<div class="panel empty">Noch keine passenden Teilnehmer für die Gesamtwertung.</div>';
}
function renderComparisons(q){
    const selected=$('#comparison-discipline').value;
    $('#rankings').innerHTML=comparisons.filter(group=>!selected||group.discipline===selected).map(group=>{
        const rows=group.standings.filter(row=>[group.discipline,...Object.values(row.participant)].join(' ').toLocaleLowerCase('de').includes(q));
        if(!rows.length)return '';
        return `<section class="ranking-group" data-comparison-discipline="${html(group.discipline)}"><h3>${html(group.discipline)}</h3><div class="table-wrap"><table><thead><tr><th>Platz</th><th>Schwimmer</th><th>Trainingsgruppe</th><th>Wertung</th><th>Zeit</th><th>Lauf</th></tr></thead><tbody>${rows.map(row=>`<tr data-comparison-person="${html(row.participant.id)}" class="${row.place===1?'winner':''}"><td><span class="rank-number">${row.place}</span></td><td><strong>${html(name(row.participant))}</strong><small>${row.participant.birthYear} · ID ${html(row.participant.id)}</small></td><td>${html(row.participant.trainingGroup)}</td><td>${html(row.participant.category)}</td><td><strong class="total-time">${formatTimeMs(row.time.elapsedMs)}</strong></td><td>${row.time.heatNumber}</td></tr>`).join('')}</tbody></table></div></section>`;
    }).join('')||'<div class="panel empty">Noch keine passenden gültigen Zeiten für diesen Disziplinvergleich.</div>';
}
$('#results-body').addEventListener('click',e=>{
    const button=e.target.closest('[data-edit-heat]');if(!button)return;
    const h=heats.find(x=>x.id===button.dataset.editHeat),l=h.lanes.find(x=>x.lane===Number(button.dataset.editLane));
    $('#correction-title').textContent=`Lauf ${h.number} · Bahn ${l.lane}`;$('#correction-heat').value=h.id;$('#correction-lane').value=l.lane;
    $('#correction-status').value=l.status==='PENDING'?'OK':l.status;$('#correction-time').value=l.elapsedMs==null?'':(l.elapsedMs/1000).toFixed(3).replace('.',',');
    $('#correction-reason').value='';$('#correction-error').textContent='';$('#correction-dialog').showModal();
});
$('#correction-cancel').addEventListener('click',()=>$('#correction-dialog').close());
$('#correction-form').addEventListener('submit',async e=>{
    e.preventDefault();
    try{const status=$('#correction-status').value;const elapsedMs=status==='OK'?parseTime($('#correction-time').value):null;await api(`/admin/heats/${$('#correction-heat').value}/lanes/${$('#correction-lane').value}`,{method:'POST',body:{status,elapsedMs,reason:$('#correction-reason').value}});$('#correction-dialog').close();await refresh();toast('Korrektur gespeichert und protokolliert.');}catch(error){$('#correction-error').textContent=error.message;}
});
async function access() {
    const data=await api('/admin/access');
    $('#codes').innerHTML=Object.entries(data.codes).map(([role,code])=>`<tr><td>${roleLabel(role)}</td><td class="code">${html(code)}</td></tr>`).join('');
    $('#addresses').innerHTML=data.urls.map(url=>`<p><a href="${html(url)}" target="_blank" rel="noopener">${html(url)}</a></p>`).join('')||'<p>Keine Netzwerkadresse gefunden. WLAN-/Hotspot-Verbindung am Laptop prüfen.</p>';
}
await Promise.all([refresh(),access()]);live.start();animate();
```

## src/main/resources/static/js/station.js

```javascript
import {$,ensureRole,LiveConnection,roleLabel,statusLabel,formatTime,escapeHtml,instantButton,toast} from './common.js';
const role=await ensureRole(document.body.dataset.station);
const starter=role==='starter';$('#station-title').textContent=roleLabel(role);document.title=roleLabel(role)+' · Vereinsmeisterschaft';
const live=new LiveConnection(role);
live.onState=render;live.onChange=render;live.onPending=renderPending;live.onError=message=>toast(message,true);
let wakeLock=null;
async function wake() {
    if(!('wakeLock' in navigator) || !window.isSecureContext) {$('#wake-note').textContent='Bei dieser Verbindung bitte die automatische Bildschirmsperre in den Geräteeinstellungen ausschalten.';return;}
    try {wakeLock=await navigator.wakeLock.request('screen');$('#wake-note').textContent='Bildschirm wird wach gehalten, solange diese Seite sichtbar ist.';wakeLock.addEventListener('release',()=>{$('#wake-note').textContent='Bildschirmsperre wieder möglich. Bitte erneut aktivieren.';});}
    catch {$('#wake-note').textContent='Wachhalten nicht verfügbar. Bitte über die Geräteeinstellungen lösen.';}
}
$('#wake').addEventListener('click',wake);
$('#ready').addEventListener('click',()=>{try{const mine=live.state?.devices.find(d=>d.role===role);live.setReady(!mine?.ready);wake();}catch(e){toast(e.message,true);}});
instantButton($('#timing-button'),at=>{try{starter?live.startRace(at):live.stopRace(at);}catch(e){toast(e.message,true);}});
$('#false-start')?.addEventListener('click',()=>{try{live.falseStart();}catch(e){toast(e.message,true);}});
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&wakeLock) wake();});

function render() {
    const state=live.state,h=state?.activeHeat,lane=h?.lanes[0],myDevice=state?.devices.find(d=>d.role===role);
    const connected=live.connected();$('#connection').textContent=connected?'Verbunden':'Verbindung unterbrochen';$('#connection').className='badge '+(connected?'good':'bad');
    $('#clock-status').textContent=!connected&&live.hasCalibration()?'Zeitbasis für diesen Lauf gespeichert':live.clockReady()?`Zeitabgleich bereit · RTT ${live.best.rtt.toFixed(0)} ms`:'Zeitabgleich läuft …';
    $('#ready').disabled=!h || h.status!=='ARMED' || !connected || !live.clockReady() || (!starter&&!lane);
    $('#ready').textContent=myDevice?.ready?'Bereit ✓ · zurücknehmen':'Bereitschaft bestätigen';
    $('#ready').hidden=!!h && !['PLANNED','ARMED'].includes(h.status);
    $('#timing-button').disabled=true;
    $('#timing-button').textContent=starter?'START':'STOPP';
    if(starter) {
        const pending=live.pending.some(p=>p.event.type==='FALSE_START'&&p.event.heatId===h?.id);
        $('#false-start').hidden=!h || !['RUNNING','FINISHED'].includes(h.status);
        $('#false-start').disabled=!connected || pending;
        $('#false-start').textContent=pending?'Fehlstart wird übertragen …':'Fehlstart · Lauf wiederholen';
    }
    if(!h) {$('#heat-number').textContent='WARTE AUF LAUF';$('#swimmer').textContent='Noch kein Lauf aktiviert';$('#discipline').textContent='';$('#measurement-status').textContent='Die Verwaltung bereitet den Lauf vor.';if(starter) $('#lane-overview').innerHTML='';return;}
    $('#heat-number').textContent=`LAUF ${h.number} · ${statusLabel(h.status).toUpperCase()}`;
    $('#swimmer').textContent=starter?(h.label||h.discipline):lane?`${lane.firstName} ${lane.lastName}`:'Bahn nicht besetzt';
    $('#discipline').textContent=h.discipline+(lane&&!starter?` · Jahrgang ${lane.birthYear} · ${lane.category}`:'');
    const local=live.localEvent(starter?'START':'STOP',h.id);
    const queue=local?live.pending.find(p=>p.event.eventId===local.eventId):null;
    let message='';
    if(h.status==='ARMED') {
        const required=['starter',...h.lanes.map(l=>'lane'+l.lane)];
        const allReady=required.every(r=>state.devices.some(d=>d.role===r&&d.ready&&d.online));
        message=myDevice?.ready?(starter?(allReady?'Alle bereit. Beim Pfeifen START drücken.':'Warte auf die Bereitschaft der Bahnen.'):'Bereit. Warte auf den Starter.'):(h.falseStartOf?'Wiederholung nach Fehlstart. Alle Stationen bitte erneut Bereitschaft bestätigen.':'Name und Disziplin prüfen, dann Bereitschaft bestätigen.');
        if(starter) $('#timing-button').disabled=!(allReady && connected && live.clockReady() && live.hasCalibration() && !local);
        if(starter&&local) message=queue?.status==='rejected'?'START NICHT BESTÄTIGT! Lauf in der Verwaltung prüfen.':'Start wird übertragen …';
    } else if(h.status==='RUNNING') {
        if(starter) {message='Lauf gestartet. Bahnen nehmen die Zeiten.';$('#timing-button').textContent='GESTARTET';}
        else if(!lane) message='Diese Bahn ist in diesem Lauf frei.';
        else if(lane.status!=='PENDING') {message=`Am Laptop gespeichert: ${statusLabel(lane.status)}${lane.needsReview?' · Prüfung erforderlich':''}`;$('#timing-button').textContent='GESPEICHERT';}
        else if(local) {message=queue?.status==='rejected'?'Stopp abgelehnt. Lokale Sicherung unten prüfen.':queue?'Gestoppt · lokal gesichert. Übertragung zum Laptop läuft …':'Stopp am Laptop angekommen.';$('#timing-button').textContent='GESTOPPT';}
        else if(!live.hasCalibration()) message='Zeitabgleich dieses Laufes fehlt, z. B. nach Neuladen. Ersatzzeit nehmen und Verwaltung informieren.';
        else {message=connected?'Beim Anschlag STOPP drücken.':'Funkverbindung weg. STOPP bleibt möglich; die Zeit wird lokal gesichert.';$('#timing-button').disabled=false;}
        if(live.suspended) message+=' Seite wurde pausiert: Ergebnis muss geprüft werden.';
    } else if(h.status==='FINISHED') {
        message=starter?'Alle Ergebnisse liegen am Laptop vor.':lane?`Ergebnis gespeichert: ${statusLabel(lane.status)}${lane.needsReview?' · Bitte prüfen lassen.':''}`:'Lauf beendet.';$('#timing-button').textContent='BEENDET';
    } else if(['ABORTED','FALSE_START'].includes(h.status)) {message='Lauf abgebrochen. Warte auf die Wiederholung.';$('#timing-button').textContent='ABGEBROCHEN';}
    else message='Messung unterbrochen. Verwaltung informieren.';
    $('#measurement-status').textContent=message;
    if(starter) $('#lane-overview').innerHTML=h.lanes.map(l=>{const d=state.devices.find(x=>x.role==='lane'+l.lane);return `<div class="lane-row"><div><strong>Bahn ${l.lane}</strong><br><span class="small">${escapeHtml(l.firstName)} ${escapeHtml(l.lastName)}</span></div><span class="badge ${l.status!=='PENDING'?'good':d?.ready?'good':d?.online?'warn':'bad'}">${l.status!=='PENDING'?escapeHtml(l.status==='OK'?formatTime(l.elapsedMs):statusLabel(l.status)):d?.ready?'Bereit':d?.online?'Wartet':'Offline'}</span></div>`;}).join('');
}
function animate() {
    const h=live.state?.activeHeat;
    let ms=0;
    if(h) {
        const lane=h.lanes[0],stop=!starter?live.localEvent('STOP',h.id):null;
        if(!starter && lane?.elapsedMs!=null) ms=lane.elapsedMs;
        else if(stop && h.startMs!=null) ms=stop.atMs-h.startMs;
        else if(h.startMs!=null && h.status==='RUNNING') ms=live.estimatedNow()-h.startMs;
        else if(starter && h.status==='FINISHED') ms=Math.max(0,...h.lanes.map(l=>l.elapsedMs??0));
        else if(starter && live.localEvent('START',h.id) && h.status==='ARMED') ms=live.estimatedNow()-live.localEvent('START',h.id).atMs;
    }
    $('#timer').textContent=formatTime(ms);requestAnimationFrame(animate);
}
function renderPending() {
    $('#pending-panel').hidden=live.pending.length===0;
    $('#pending-list').innerHTML=live.pending.map(p=>`<div class="pending-item"><strong>${p.event.type==='START'?'Start':p.event.type==='FALSE_START'?'Fehlstart':'Stopp'} · Lauf ${escapeHtml(p.heatNumber??'?')} · ${p.status==='rejected'?'Prüfung erforderlich':'wartet auf Bestätigung'}</strong>${p.elapsedMs!=null?`<p>Lokale Zeit: <strong>${formatTime(p.elapsedMs)}</strong> (${p.elapsedMs} ms)</p>`:''}<p class="small">${escapeHtml(p.error||'Das Ereignis bleibt bis zur Bestätigung auf diesem Handy gespeichert.')}</p><details><summary class="small">Messdaten zur Wiederherstellung</summary><pre>${escapeHtml(JSON.stringify(p.event,null,2))}</pre></details><button class="quiet" data-discard="${escapeHtml(p.event.eventId)}">Nach Prüfung entfernen</button></div>`).join('');
}
$('#pending-list').addEventListener('click',e=>{const id=e.target.closest('[data-discard]')?.dataset.discard;if(id && confirm('Nur entfernen, wenn das Ergebnis am Laptop geprüft oder manuell übernommen wurde. Fortfahren?')) live.discard(id);});
live.start();animate();
if(!window.isSecureContext) $('#wake-note').textContent='Für lokale HTTP-Verbindungen: Bildschirmsperre bitte in den Geräteeinstellungen ausschalten.';
```

## src/main/resources/static/manager.html

```html
<!doctype html>
<html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Verwaltung · Vereinsmeisterschaft</title><link rel="stylesheet" href="/css/app.css"><script type="module" src="/js/manager.js"></script></head>
<body><header class="app-header"><div><span class="eyebrow">DLRG · WETTKAMPFBÜRO</span><h1>Vereinsmeisterschaft</h1></div><div class="header-actions"><span id="connection" class="badge">Verbinde …</span><button id="logout" class="quiet">Abmelden</button></div></header>
<main class="manager"><nav class="tabs" aria-label="Verwaltung"><button class="selected" data-tab="overview">Übersicht</button><button data-tab="people">Teilnehmer</button><button data-tab="disciplines">Disziplinen</button><button data-tab="classes">Wertungsklassen</button><button data-tab="heats">Läufe</button><button data-tab="results">Ergebnisse</button><button data-tab="access">Handys verbinden</button><button data-tab="data">Daten &amp; Sicherung</button></nav>
<div id="toast" role="status" class="toast" hidden></div>
<section id="tab-overview" class="tab-panel"><div class="section-heading"><div><span class="eyebrow dark">LIVE</span><h2>Aktueller Lauf</h2></div><span id="live-status" class="badge">Kein Lauf aktiv</span></div>
<div id="active-heat" class="panel empty">Lege unter „Läufe“ einen Lauf an und aktiviere ihn.</div>
<h2>Stationen</h2><div id="devices" class="device-grid"></div>
<p class="muted small">Der Starter kann starten, sobald alle besetzten Bahnen und er selbst „Bereit“ bestätigt haben. Nach einem Lauf aktiviert die Verwaltung den nächsten.</p>
<details class="panel"><summary>Änderungsprotokoll</summary><div id="audit" class="audit"></div></details></section>
<section id="tab-people" class="tab-panel" hidden><div class="section-heading"><h2>Teilnehmer <span id="people-count" class="count">0</span></h2><div class="actions"><a class="button secondary" href="/api/admin/template.xlsx">Excel-Vorlage</a><a class="button quiet" href="/api/admin/template.xlsx?demo=true">Beispieldatei</a></div></div>
<div class="two-columns"><form id="import-form" class="panel"><h3>Excel-Datei importieren</h3><p class="small muted">Erstes Blatt, eine Zeile pro Person. Neue IDs werden ergänzt. Bei doppelten IDs oder ungültigen Zeilen bleibt der gesamte Import unverändert.</p><label>Datei<input id="import-file" type="file" accept=".xlsx" required></label><button class="primary">Teilnehmer importieren</button></form>
<details class="panel"><summary>Einzelne Person / Nachmeldung</summary><form id="person-form" class="form-grid"><label>Teilnehmer-ID<input name="id" maxlength="40" required></label><label>Jahrgang<input name="birthYear" type="number" min="1900" max="2100" required></label><label>Vorname<input name="firstName" maxlength="100" required></label><label>Nachname<input name="lastName" maxlength="100" required></label><label class="full-width">Wertungsklasse<select id="person-class" required><option value="">Zuerst Wertungsklasse anlegen</option></select></label><button class="primary">Person hinzufügen</button></form></details></div>
<label class="search-label">Teilnehmer suchen<input id="people-search" type="search" placeholder="Name, Jahrgang oder Gruppe"></label><div class="table-wrap"><table><thead><tr><th>ID</th><th>Name</th><th>Jahrgang</th><th>Wertung</th><th>Gruppe</th><th>Aktion</th></tr></thead><tbody id="people-body"></tbody></table></div><p class="small muted">Mit dem Papierkorb entfernst du eine Person aus der Teilnehmerliste und aus geplanten Läufen. Vorhandene Ergebnisse bleiben erhalten.</p></section>
<section id="tab-disciplines" class="tab-panel" hidden><div class="section-heading"><h2>Disziplinen <span id="discipline-count" class="count">0</span></h2></div>
<form id="discipline-form" class="panel"><h3>Disziplin anlegen</h3><div class="discipline-entry"><label>Name mit Strecke<input id="discipline-name" maxlength="120" placeholder="z. B. 50 m Freistil" required></label><button class="primary">Disziplin hinzufügen</button></div><p class="small muted">Diese Disziplinen stehen beim Planen eines Laufes zur Auswahl. Bereits verwendete Disziplinen bleiben für die Auswertung erhalten.</p></form>
<div class="table-wrap"><table><thead><tr><th>Disziplin</th><th>Läufe</th><th>Aktion</th></tr></thead><tbody id="discipline-body"></tbody></table></div></section>
<section id="tab-classes" class="tab-panel" hidden><div class="section-heading"><h2>Wertungsklassen <span id="class-count" class="count">0</span></h2></div>
<form id="class-form" class="panel"><h3>Wertungsklasse anlegen</h3><div class="form-grid"><label>Trainingsgruppe<input id="class-group" maxlength="100" list="training-groups" placeholder="z. B. Grauwale" required></label><label>Wertung<input id="class-category" maxlength="60" list="categories" placeholder="z. B. männlich" required></label></div><button class="primary">Wertungsklasse hinzufügen</button><p class="small muted">Die Kombination ergibt z. B. „Grauwale – männlich“. Beim Excel-Import entstehen passende Wertungsklassen automatisch aus Trainingsgruppe und Wertung.</p></form>
<div class="table-wrap"><table><thead><tr><th>Wertungsklasse</th><th>Teilnehmer</th><th>Aktion</th></tr></thead><tbody id="class-body"></tbody></table></div></section>
<section id="tab-heats" class="tab-panel" hidden><h2>Läufe planen</h2><form id="heat-form" class="panel"><div class="form-grid"><label>Disziplin mit Strecke<select id="discipline" required><option value="">Zuerst eine Disziplin anlegen</option></select></label><label>Bezeichnung (optional)<input id="heat-label" placeholder="z. B. Hechte · Durchgang 1" maxlength="120"></label><label>Teilnehmerauswahl filtern<select id="group-filter"><option value="">Alle Trainingsgruppen</option></select></label></div><div id="lane-selects" class="three-columns"></div><button class="primary">Lauf anlegen</button></form>
<div class="table-wrap"><table><thead><tr><th>Lauf</th><th>Disziplin</th><th>Bahnen</th><th>Status</th><th>Aktionen</th></tr></thead><tbody id="heats-body"></tbody></table></div></section>
<section id="tab-results" class="tab-panel" hidden><div class="section-heading"><div><h2>Ergebnisse</h2><p class="muted small">Wertungsklassen, Gesamtlisten je Trainingsgruppe oder derselbe Wettkampf im Disziplinvergleich. Pro Disziplin zählt die letzte gültige Zeit.</p></div><div class="actions"><a id="ranking-export" class="button primary" href="/api/admin/rankings.csv?mode=classes">Ansicht als CSV</a><a class="button quiet" href="/api/admin/results.csv">Einzelzeiten als CSV</a></div></div>
<div id="result-tabs" class="result-tabs" role="group" aria-label="Ergebnisansicht"><button class="primary" data-result-view="overall" aria-pressed="true">Gesamtwertung</button><button class="quiet" data-result-view="individual" aria-pressed="false">Einzelzeiten / Korrekturen</button></div>
<label class="search-label">Ergebnisse filtern<input id="results-search" type="search" placeholder="Name, Disziplin oder Gruppe"></label>
<div id="overall-results"><div class="result-controls"><label>Ergebnisansicht<select id="ranking-mode"><option value="classes">Nach Wertungsklassen</option><option value="groups">Gesamtliste je Trainingsgruppe</option><option value="disciplines">Disziplinvergleich · alle Trainingsgruppen</option></select></label><label id="comparison-discipline-label" hidden>Disziplin<select id="comparison-discipline"><option value="">Alle Disziplinen · getrennte Listen</option></select></label></div><p id="ranking-note" class="small muted">Alle für eine Wertungsklasse bereits geplanten Disziplinen gehören zur Wertung. Fehlende oder ungeprüfte Zeiten stehen ohne Platzierung unten. Die Rangliste ist bis zum Abschluss aller Läufe vorläufig. Zeitgleichheit ergibt denselben Platz.</p><div id="rankings"></div></div>
<div id="individual-results" hidden><div class="table-wrap"><table><thead><tr><th>Lauf / Bahn</th><th>Schwimmer</th><th>Disziplin</th><th>Jahrgang / Wertung</th><th>Zeit</th><th>Status</th><th>Bearbeiten</th></tr></thead><tbody id="results-body"></tbody></table></div><p class="small muted">„Prüfen“ kennzeichnet Auffälligkeiten während der Messung. Fehlstarts, abgebrochene und unterbrochene Läufe zählen nicht zur Gesamtwertung. Korrekturen erfordern einen Grund und werden protokolliert.</p></div></section>
<section id="tab-access" class="tab-panel" hidden><h2>Handys verbinden</h2><div class="panel"><p>Alle Geräte mit demselben Hotspot verbinden. Auf den Handys eine passende Laptop-Adresse öffnen:</p><div id="addresses"></div><p class="small muted">Bei mehreren Adressen die IPv4-Adresse des Hotspot-/WLAN-Adapters wählen. Auf Handys funktioniert „localhost“ nicht.</p></div><div class="table-wrap"><table><thead><tr><th>Station</th><th>Zugangscode</th></tr></thead><tbody id="codes"></tbody></table></div><div class="panel"><h3>Vor dem ersten Lauf</h3><p>Auf jedem Handy Station auswählen, Code eingeben und die Seite geöffnet lassen. Automatische Bildschirmsperre und Energiesparen während der Zeitnahme ausschalten. Je Rolle nur einen Tab öffnen.</p><p class="small muted">Codes werden bei einem Serverneustart erneuert. Verwende ein eigenes, passwortgeschütztes WLAN. Die lokale HTTP-Verbindung ist nicht verschlüsselt.</p></div></section>
<section id="tab-data" class="tab-panel" hidden><h2>Daten &amp; Sicherung</h2><div id="data-counts" class="data-counts"></div>
<div class="two-columns"><form id="backup-export-form" class="panel"><h3>Konfiguration und Daten exportieren</h3><p class="small muted">Wähle den Inhalt der Sicherungsdatei. Ohne Ergebnisse werden die Läufe ohne Zeiten für einen neuen Wettkampf vorbereitet.</p><div class="backup-options"><label><input id="export-participants" type="checkbox" checked>Teilnehmer</label><label><input id="export-heats" type="checkbox" checked>Läufe</label><label><input id="export-results" type="checkbox" checked>Ergebnisse</label><label><input id="export-disciplines" type="checkbox" checked>Disziplinen</label><label><input id="export-classes" type="checkbox" checked>Wertungsklassen</label></div><p class="small muted">Ergebnisse benötigen Läufe; Läufe benötigen Teilnehmer und Disziplinen; Teilnehmer benötigen Wertungsklassen. Diese Abhängigkeiten werden automatisch mit ausgewählt. Die Datei enthält personenbezogene Daten.</p><button id="export-button" class="primary">Sicherung herunterladen</button></form>
<div class="panel"><h3>Sicherung wiederherstellen</h3><form id="backup-preview-form"><label>Sicherungsdatei<input id="backup-file" type="file" accept=".json,application/json" required></label><button id="preview-button" class="secondary">Datei prüfen</button></form><div id="backup-preview" hidden></div><form id="restore-form"><p id="restore-hint" class="small muted">Datei auswählen und prüfen.</p><label>Zur Bestätigung WIEDERHERSTELLEN eingeben<input id="restore-confirmation" autocomplete="off" disabled required></label><button id="restore-button" class="primary" disabled>Sicherung wiederherstellen</button></form></div></div>
<div class="panel reset-panel"><h3>Alle Wettkampfdaten löschen</h3><p id="reset-hint" class="small muted">Teilnehmer, Läufe, Ergebnisse, Disziplinen, Wertungsklassen und Protokoll werden gelöscht.</p><p class="small">Lade vorher bei Bedarf eine Sicherung herunter. Bereits heruntergeladene Dateien bleiben auf deinem Gerät.</p><button id="reset-open" type="button" class="danger">Alles löschen …</button></div></section>
</main><dialog id="reset-dialog"><form id="reset-form"><h2>Alle Wettkampfdaten löschen?</h2><p>Diese Aktion entfernt alle Teilnehmer, Läufe, Ergebnisse, Disziplinen, Wertungsklassen und das Änderungsprotokoll. Du kannst vorher unter Daten &amp; Sicherung eine Sicherung herunterladen.</p><label>Zur Bestätigung ALLES LÖSCHEN eingeben<input id="reset-confirmation" autocomplete="off" required></label><div class="actions"><button id="reset-cancel" type="button" class="secondary">Abbrechen</button><button id="reset-submit" class="danger" disabled>Jetzt alles löschen</button></div><p id="reset-error" class="error" role="alert"></p></form></dialog><dialog id="correction-dialog"><form id="correction-form"><h2 id="correction-title">Ergebnis korrigieren</h2><input id="correction-heat" type="hidden"><input id="correction-lane" type="hidden"><label>Ergebnisstatus<select id="correction-status"><option value="OK">Zeit vorhanden</option><option value="DNS">Nicht gestartet (DNS)</option><option value="DNF">Nicht beendet (DNF)</option><option value="DSQ">Disqualifiziert (DSQ)</option></select></label><label>Zeit (Sekunden oder mm:ss,hh)<input id="correction-time" placeholder="z. B. 42,37 oder 1:02,37" inputmode="decimal"></label><label>Grund / Bestätigung der Prüfung<textarea id="correction-reason" maxlength="450" required></textarea></label><div class="actions"><button type="button" id="correction-cancel" class="secondary">Abbrechen</button><button class="primary">Korrektur speichern</button></div><p id="correction-error" class="error" role="alert"></p></form></dialog>
<datalist id="training-groups"></datalist><datalist id="categories"><option value="weiblich"></option><option value="männlich"></option><option value="offen"></option></datalist>
</body></html>
```

## src/main/resources/static/starter.html

```html
<!doctype html>
<html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Starter · Vereinsmeisterschaft</title><link rel="stylesheet" href="/css/app.css"><script type="module" src="/js/station.js"></script></head>
<body class="station-page" data-station="starter"><header class="app-header"><div><span class="eyebrow">DLRG · ZEITNAHME</span><h1 id="station-title">Starter</h1></div><button id="logout" class="quiet">Abmelden</button></header><main class="station">
<div class="station-meta"><span id="connection" class="badge">Verbinde …</span><span id="clock-status" class="small muted">Zeitabgleich …</span></div>
<div id="toast" class="toast" role="alert" hidden></div><div class="heat-info"><span id="heat-number" class="eyebrow dark">WARTE AUF LAUF</span><h2 id="swimmer">Noch kein Lauf aktiviert</h2><p id="discipline" class="muted"></p></div>
<div id="timer" class="timer" aria-label="Gemessene Zeit">00:00,00</div><p id="measurement-status" class="measurement-status">Die Verwaltung bereitet den Lauf vor.</p>
<button id="ready" class="secondary wide" disabled>Bereitschaft bestätigen</button><button id="timing-button" class="timing-button start" disabled>START</button>
<button id="false-start" type="button" class="danger wide false-start-button" hidden>Fehlstart · Lauf wiederholen</button>
<div id="lane-overview" class="panel"></div><p class="small muted">Beim Pfeifen auf START drücken. Der Start gilt erst nach Bestätigung durch den Laptop. Bei einem Fehlstart „Fehlstart · Lauf wiederholen“ drücken. Alle Stationen bestätigen danach erneut Bereitschaft. Seite geöffnet und Bildschirm eingeschaltet lassen.</p><button id="wake" class="quiet wide">Bildschirm wach halten</button><p id="wake-note" class="small muted"></p>
<section id="pending-panel" class="panel" hidden><h3>Lokal gesicherte Ereignisse</h3><div id="pending-list"></div></section>
</main></body></html>
```

## src/test/java/de/dlrg/vereinsmeisterschaft/BackupServiceTest.java

```java
package de.dlrg.vereinsmeisterschaft;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlrg.vereinsmeisterschaft.model.BackupModels.*;
import de.dlrg.vereinsmeisterschaft.model.Models.*;
import de.dlrg.vereinsmeisterschaft.repository.RaceRepository;
import de.dlrg.vereinsmeisterschaft.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:vm-backup-tests;DB_CLOSE_DELAY=-1","app.admin-password=test-password-only"})
class BackupServiceTest {
    @Autowired RaceRepository repo;
    @Autowired RaceService races;
    @Autowired BackupService backups;
    @Autowired ServerClock clock;
    @Autowired ObjectMapper json;
    @Autowired TransactionTemplate tx;
    static final Selection ALL=new Selection(true,true,true,true,true);
    @BeforeEach void setup(){
        tx.executeWithoutResult(s->{for(String t:List.of("processed_event","lane_result","heat","participant","discipline","competition_class","audit_log"))repo.db().update("DELETE FROM "+t);});
        races.dataChanged();
        for(String role:List.of("starter","lane1","lane2","lane3")) {
            races.disconnect(role,"test-"+role);assertTrue(races.connect(role,"test-"+role));races.heartbeat(role,"test-"+role,10.0,true);
        }
    }
    void seed(){
        races.addDiscipline("50 m Freistil");races.addDiscipline("25 m Brust");
        races.addParticipants(List.of(new Participant("1","Mia","Beispiel",2016,"weiblich","Grauwale"),new Participant("2","Leon","Muster",2016,"männlich","Grauwale"),new Participant("3","Emma","Test",2017,"weiblich","Hechte")));
    }
    Heat planned(){return races.createHeat(new NewHeat("50 m Freistil","Test",List.of(new LaneAssignment(1,"1"),new LaneAssignment(2,"2"))));}
    void start(Heat h){
        races.arm(h.id());
        for(String role:List.of("starter","lane1","lane2")){races.heartbeat(role,"test-"+role,10.0,true);races.ready(role,"test-"+role,h.id(),true);}
        races.timing("starter","test-starter",new TimingEvent("START",UUID.randomUUID().toString(),h.id(),clock.bootId(),clock.now(),5,false,""));
    }
    Heat finished(){Heat h=planned();start(h);races.correct(h.id(),1,new Correction("OK",12345L,"Testzeit"));races.correct(h.id(),2,new Correction("DNF",null,"Nicht beendet"));return repo.heat(h.id());}
    byte[] encode(BackupFile f)throws Exception{return json.writeValueAsBytes(f);}
    @Test void fullBackupRestoresParticipantsClassesHeatsTimesAndDeletedState(){
        seed();Heat h=finished();races.deleteParticipant("3");
        var expected=races.rankings();byte[] bytes=backups.write(ALL);
        BackupFile file=backups.read(bytes);assertEquals(3,file.participants().size());
        backups.reset("ALLES LÖSCHEN");assertTrue(backups.summary().empty());
        backups.restore(bytes,"WIEDERHERSTELLEN");
        assertEquals(h,repo.heat(h.id()));assertEquals(expected,races.rankings());assertEquals(2,races.participants().size());
        assertTrue(repo.participantIdUsed("3"));assertNull(races.state("admin").activeHeat());
        assertEquals(1,repo.audit().size());
    }
    @Test void configurationBackupHasNoTimesNotesReviewMarkersOrFalseStartHistory(){
        seed();Heat h=finished();
        races.timing("starter","test-starter",new TimingEvent("FALSE_START",UUID.randomUUID().toString(),h.id(),clock.bootId(),clock.now(),0,false,""));
        Heat repeat=races.state("admin").activeHeat();races.abort(repeat.id());planned();
        byte[] bytes=backups.write(new Selection(true,true,false,true,true));
        BackupFile f=backups.read(bytes);assertEquals(1,f.heats().size());
        for(Heat value:f.heats()) {
            assertEquals("PLANNED",value.status());assertNull(value.startMs());assertNull(value.bootId());assertNull(value.falseStartOf());
            for(Lane l:value.lanes()){assertEquals("PENDING",l.status());assertNull(l.elapsedMs());assertNull(l.stopMs());assertNull(l.qualityMs());assertFalse(l.needsReview());assertEquals("",l.note());}
        }
        backups.reset("ALLES LÖSCHEN");backups.restore(bytes,"WIEDERHERSTELLEN");
        assertTrue(races.rankings().stream().flatMap(g->g.standings().stream()).noneMatch(Standing::complete));
    }
    @Test void everyNonemptyCheckboxCombinationProducesARestorableSelfContainedFile(){
        seed();finished();byte[] baseline=backups.write(ALL);
        for(int mask=1;mask<32;mask++) {
            Selection requested=new Selection((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0);
            byte[] bytes=backups.write(requested);BackupFile f=backups.read(bytes);
            assertEquals(requested.withDependencies(),f.selection());
            backups.reset("ALLES LÖSCHEN");backups.restore(bytes,"WIEDERHERSTELLEN");
            assertEquals(f.participants().size(),repo.db().queryForObject("SELECT COUNT(*) FROM participant",Integer.class));
            assertEquals(f.heats().size(),races.heats().size());assertEquals(f.disciplines().size(),races.disciplines().size());
            assertEquals(f.classes().size(),races.competitionClasses().size());
            backups.reset("ALLES LÖSCHEN");backups.restore(baseline,"WIEDERHERSTELLEN");
        }
    }
    @Test void resetNeedsExactConfirmationAndRestartsHeatNumbersAtOne(){
        seed();planned();String boot=clock.bootId(),data=races.state("admin").dataId();
        assertThrows(IllegalArgumentException.class,()->backups.reset("ja"));assertEquals(1,races.heats().size());
        backups.reset("ALLES LÖSCHEN");assertTrue(backups.summary().empty());assertTrue(repo.audit().isEmpty());
        assertNotEquals(boot,clock.bootId());assertNotEquals(data,races.state("admin").dataId());
        seed();assertEquals(1,planned().number());
    }
    @Test void resetIsBlockedDuringReadinessAndDuringTiming(){
        seed();Heat h=planned();races.arm(h.id());
        assertThrows(IllegalStateException.class,()->backups.reset("ALLES LÖSCHEN"));assertEquals(1,races.heats().size());
        races.abort(h.id());Heat next=planned();start(next);
        assertThrows(IllegalStateException.class,()->backups.reset("ALLES LÖSCHEN"));assertEquals("RUNNING",repo.heat(next.id()).status());
    }
    @Test void restoreNeverMergesIntoExistingDataEvenWhenOnlyACatalogueExists(){
        seed();byte[] bytes=backups.write(ALL);backups.reset("ALLES LÖSCHEN");races.addDiscipline("Vorhanden");
        assertFalse(backups.preview(bytes).restoreAllowed());
        assertThrows(IllegalStateException.class,()->backups.restore(bytes,"WIEDERHERSTELLEN"));
        assertEquals(List.of("Vorhanden"),races.disciplines().stream().map(Discipline::name).toList());assertTrue(races.participants().isEmpty());
    }
    @Test void malformedDuplicateAndMissingReferenceFilesLeaveTheDatabaseEmpty()throws Exception{
        seed();finished();BackupFile good=backups.export(ALL);backups.reset("ALLES LÖSCHEN");
        assertThrows(IllegalArgumentException.class,()->backups.restore("{}".getBytes(),"WIEDERHERSTELLEN"));
        var duplicate=new BackupFile(good.format(),good.version(),good.exportedAt(),good.selection(),List.of(good.participants().getFirst(),good.participants().getFirst()),good.heats(),good.disciplines(),good.classes());
        assertThrows(IllegalArgumentException.class,()->backups.restore(encode(duplicate),"WIEDERHERSTELLEN"));
        var missing=new BackupFile(good.format(),good.version(),good.exportedAt(),good.selection(),good.participants(),good.heats(),List.of(),good.classes());
        assertThrows(IllegalArgumentException.class,()->backups.restore(encode(missing),"WIEDERHERSTELLEN"));
        assertTrue(backups.summary().empty());
    }
    @Test void databaseFailureRollsBackAllPreviouslyInsertedContent(){
        seed();byte[] bytes=backups.write(ALL);backups.reset("ALLES LÖSCHEN");
        repo.db().execute("ALTER TABLE participant ADD CONSTRAINT test_restore_failure CHECK(first_name<>'Mia')");
        try{assertThrows(RuntimeException.class,()->backups.restore(bytes,"WIEDERHERSTELLEN"));assertTrue(backups.summary().empty());}
        finally{repo.db().execute("ALTER TABLE participant DROP CONSTRAINT test_restore_failure");}
    }
    @Test void activeBackupRestoresAsInterruptedWithoutResumingAClock(){
        seed();Heat h=planned();start(h);byte[] bytes=backups.write(ALL);String oldBoot=clock.bootId();
        races.abort(h.id());backups.reset("ALLES LÖSCHEN");backups.restore(bytes,"WIEDERHERSTELLEN");
        assertEquals("INTERRUPTED",repo.heat(h.id()).status());assertNull(races.state("admin").activeHeat());
        assertTrue(races.state("admin").devices().stream().noneMatch(Device::ready));
        TimingEvent oldStop=new TimingEvent("STOP",UUID.randomUUID().toString(),h.id(),oldBoot,clock.now(),5,false,"");
        assertThrows(IllegalStateException.class,()->races.timing("lane1","test-lane1",oldStop));
    }
    @Test void previewIsReadOnlyAndShowsTheActualExportedContent(){
        seed();finished();byte[] bytes=backups.write(new Selection(false,false,false,true,false));
        BackupPreview preview=backups.preview(bytes);assertEquals(2,preview.contents().disciplines());assertEquals(0,preview.contents().participants());
        assertFalse(preview.restoreAllowed());assertEquals(3,races.participants().size());assertEquals(1,races.heats().size());
    }
    @Test void emptySelectionUnknownVersionTrailingDataAndWrongConfirmationAreRejected()throws Exception{
        assertThrows(IllegalArgumentException.class,()->backups.write(new Selection(false,false,false,false,false)));
        seed();BackupFile good=backups.export(ALL);byte[] bytes=encode(good);backups.reset("ALLES LÖSCHEN");
        assertThrows(IllegalArgumentException.class,()->backups.restore(bytes,"ja"));
        var future=new BackupFile(good.format(),99,good.exportedAt(),good.selection(),good.participants(),good.heats(),good.disciplines(),good.classes());
        assertThrows(IllegalArgumentException.class,()->backups.read(encode(future)));
        assertThrows(IllegalArgumentException.class,()->backups.read((new String(bytes,java.nio.charset.StandardCharsets.UTF_8)+" {}").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertTrue(backups.summary().empty());
    }
    @Test void classCatalogueIsCreatedFromParticipantsAndProtectsExistingAssignments(){
        seed();assertEquals(3,races.competitionClasses().size());
        races.addCompetitionClass(new NewCompetitionClass("Beluga","offen"));
        assertThrows(IllegalArgumentException.class,()->races.addCompetitionClass(new NewCompetitionClass("Beluga","offen")));
        races.deleteCompetitionClass("Beluga","offen");
        assertThrows(IllegalStateException.class,()->races.deleteCompetitionClass("Grauwale","weiblich"));
        assertEquals(3,races.competitionClasses().size());
    }
}
```

## src/test/java/de/dlrg/vereinsmeisterschaft/RaceServiceTest.java

```java
package de.dlrg.vereinsmeisterschaft;

import de.dlrg.vereinsmeisterschaft.model.Models.*;
import de.dlrg.vereinsmeisterschaft.repository.RaceRepository;
import de.dlrg.vereinsmeisterschaft.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:vm-tests;DB_CLOSE_DELAY=-1","app.admin-password=test-password-only"})
class RaceServiceTest {
    @Autowired RaceRepository repo;
    @Autowired TransactionTemplate tx;
    @Autowired ExcelService excel;
    RaceService races; TestClock clock;
    static class TestClock extends ServerClock {
        double time=1_000_000;
        String boot=UUID.randomUUID().toString();
        @Override public double now(){return time;}
        @Override public String bootId(){return boot;}
    }
    @BeforeEach void setup() {
        for(String table:List.of("processed_event","lane_result","heat","participant","discipline","competition_class","audit_log")) repo.db().update("DELETE FROM "+table);
        clock=new TestClock();races=new RaceService(repo,tx,clock,100,1800);
        for(String discipline:List.of("50 m Freistil","25 m Brust","25 m Rücken")) races.addDiscipline(discipline);
        races.addParticipants(List.of(person("1001"),person("1002"),person("1003")));
        for(String role:List.of("starter","lane1","lane2","lane3","admin")) {races.connect(role,role);races.heartbeat(role,role,20.0,true);}
    }
    Participant person(String id){return new Participant(id,"Test","Person "+id,2016,"offen","Testgruppe");}
    Heat heat(int count) {
        var list=new ArrayList<LaneAssignment>();for(int i=1;i<=count;i++)list.add(new LaneAssignment(i,"100"+i));
        Heat h=races.createHeat(new NewHeat("50 m Freistil","Test",list));races.arm(h.id());return h;
    }
    void ready(Heat h){races.ready("starter","starter",h.id(),true);for(Lane l:h.lanes())races.ready("lane"+l.lane(),"lane"+l.lane(),h.id(),true);}
    TimingEvent event(String type,Heat h,double time){return new TimingEvent(type,UUID.randomUUID().toString(),h.id(),clock.bootId(),time,10,false,"");}
    void start(Heat h){ready(h);races.timing("starter","starter",event("START",h,clock.time));}

    @Test void startNeedsEveryOccupiedLaneAndStarterReady(){
        Heat h=heat(3);races.ready("starter","starter",h.id(),true);
        assertThrows(IllegalStateException.class,()->races.timing("starter","starter",event("START",h,clock.time)));
        assertEquals("ARMED",repo.heat(h.id()).status());
        ready(h);races.timing("starter","starter",event("START",h,clock.time));assertEquals("RUNNING",repo.heat(h.id()).status());
    }
    @Test void delayedDeliveryDoesNotAddNetworkDelayAndDuplicateIsIdempotent(){
        Heat h=heat(1);start(h);double captured=clock.time+12_345;clock.time+=14_000;
        TimingEvent stop=event("STOP",h,captured);races.timing("lane1","lane1",stop);races.timing("lane1","lane1",stop);
        assertEquals(12_345L,repo.heat(h.id()).lanes().getFirst().elapsedMs());
        assertEquals(2,repo.db().queryForObject("SELECT COUNT(*) FROM processed_event",Integer.class));
        assertEquals("FINISHED",repo.heat(h.id()).status());
    }
    @Test void concurrentStopsAreStoredOnceAndFinishTheHeat() throws Exception {
        Heat h=heat(3);start(h);clock.time+=30_000;
        try(ExecutorService pool=Executors.newFixedThreadPool(3)){
            var jobs=new ArrayList<Future<?>>();for(int i=1;i<=3;i++){String role="lane"+i;TimingEvent e=event("STOP",h,clock.time-i*100);jobs.add(pool.submit(()->races.timing(role,role,e)));}
            for(var f:jobs)f.get();
        }
        assertEquals("FINISHED",repo.heat(h.id()).status());assertTrue(repo.heat(h.id()).lanes().stream().allMatch(l->l.elapsedMs()!=null));
    }
    @Test void aLaneCannotStartAndCannotStopAnUnassignedLane(){
        Heat h=heat(1);ready(h);assertThrows(IllegalArgumentException.class,()->races.timing("lane1","lane1",event("START",h,clock.time)));
        start(h);clock.time+=1000;assertThrows(IllegalArgumentException.class,()->races.timing("lane2","lane2",event("STOP",h,clock.time)));
    }
    @Test void secondDistinctStopCannotOverwriteFirst(){
        Heat h=heat(2);start(h);clock.time+=1000;races.timing("lane1","lane1",event("STOP",h,clock.time));clock.time+=100;
        assertThrows(IllegalStateException.class,()->races.timing("lane1","lane1",event("STOP",h,clock.time)));
        assertEquals(1000L,repo.heat(h.id()).lanes().getFirst().elapsedMs());
    }
    @Test void reconnectCanSubmitLocallyCapturedStop(){
        Heat h=heat(1);start(h);races.disconnect("lane1","lane1");clock.time+=8000;double captured=clock.time;clock.time+=3000;
        assertTrue(races.connect("lane1","replacement"));races.timing("lane1","replacement",event("STOP",h,captured));
        assertEquals(8000L,repo.heat(h.id()).lanes().getFirst().elapsedMs());
    }
    @Test void staleConnectionCannotTakeOverLiveRole(){
        assertFalse(races.connect("lane1","another"));
        clock.time+=9000;assertTrue(races.connect("lane1","replacement"));
        assertThrows(IllegalStateException.class,()->races.heartbeat("lane1","lane1",20.0,true));
        races.disconnect("lane1","lane1");races.heartbeat("lane1","replacement",20.0,true);
    }
    @Test void abortPreservesResultsAndRejectsLateStop(){
        Heat h=heat(2);start(h);clock.time+=2000;races.timing("lane1","lane1",event("STOP",h,clock.time));races.abort(h.id());
        assertThrows(IllegalStateException.class,()->races.timing("lane2","lane2",event("STOP",h,clock.time)));
        assertEquals(2000L,repo.heat(h.id()).lanes().getFirst().elapsedMs());
        Heat repeat=races.repeat(h.id());assertNotEquals(h.id(),repeat.id());assertEquals("PLANNED",repeat.status());assertNull(repeat.startMs());
    }
    @Test void serverRestartInterruptsHeatAndDoesNotResumeOldClock(){
        Heat h=heat(1);start(h);clock.time+=2000;TimingEvent old=event("STOP",h,clock.time);
        TestClock newClock=new TestClock();RaceService restarted=new RaceService(repo,tx,newClock,100,1800);restarted.recover();
        assertEquals("INTERRUPTED",repo.heat(h.id()).status());assertNull(restarted.state("admin").activeHeat());
        restarted.connect("lane1","new");assertThrows(IllegalStateException.class,()->restarted.timing("lane1","new",old));
    }
    @Test void importRejectsWholeBatchOnDuplicateId(){
        assertThrows(IllegalArgumentException.class,()->races.addParticipants(List.of(person("2000"),person("1001"))));
        assertTrue(repo.participant("2000").isEmpty());assertEquals(3,races.participants().size());
    }
    @Test void deletedParticipantDisappearsAndItsIdCannotBeReassigned(){
        races.deleteParticipant("1001");
        assertEquals(List.of("1002","1003"),races.participants().stream().map(Participant::id).toList());
        assertThrows(IllegalArgumentException.class,()->races.createHeat(new NewHeat("50 m Freistil","",List.of(new LaneAssignment(1,"1001")))));
        assertThrows(IllegalArgumentException.class,()->races.addParticipants(List.of(person("2000"),person("1001"))));
        assertTrue(repo.participant("2000").isEmpty());
        assertThrows(IllegalArgumentException.class,()->races.deleteParticipant("1001"));
        assertThrows(IllegalArgumentException.class,()->races.deleteParticipant("unbekannt"));
    }
    @Test void deletionWithdrawsFutureStartsAndCancelsOnlyEmptyPlannedHeats(){
        Heat shared=races.createHeat(new NewHeat("50 m Freistil","",List.of(new LaneAssignment(1,"1001"),new LaneAssignment(3,"1002"))));
        Heat solo=races.createHeat(new NewHeat("25 m Brust","",List.of(new LaneAssignment(2,"1001"))));
        Heat other=races.createHeat(new NewHeat("25 m Rücken","",List.of(new LaneAssignment(1,"1003"))));
        races.deleteParticipant("1001");
        assertEquals("PLANNED",repo.heat(shared.id()).status());
        assertEquals(List.of(3),repo.heat(shared.id()).lanes().stream().map(Lane::lane).toList());
        assertEquals("ABORTED",repo.heat(solo.id()).status());assertTrue(repo.heat(solo.id()).lanes().isEmpty());
        assertEquals(other,repo.heat(other.id()));
        assertTrue(repo.audit().stream().anyMatch(a->a.get("ACTION_TEXT").toString().contains("Teilnehmer gelöscht: Test Person 1001")));
    }
    @Test void deletingAnArmedOrRunningParticipantDoesNotChangeAnyAssignments(){
        Heat active=heat(1);
        Heat planned=races.createHeat(new NewHeat("25 m Brust","",List.of(new LaneAssignment(1,"1001"))));
        assertThrows(IllegalStateException.class,()->races.deleteParticipant("1001"));
        assertEquals(active.id(),races.state("admin").activeHeat().id());
        assertEquals(planned,repo.heat(planned.id()));assertTrue(repo.participant("1001").isPresent());
        start(active);
        assertThrows(IllegalStateException.class,()->races.deleteParticipant("1001"));
        assertEquals("RUNNING",repo.heat(active.id()).status());
        assertEquals(planned,repo.heat(planned.id()));assertTrue(repo.participant("1001").isPresent());
    }
    @Test void deletionKeepsHistoricalResultsAndCopiesOnlyRemainingParticipants(){
        Heat h=heat(3);start(h);clock.time+=2000;
        for(int i=1;i<=3;i++)races.timing("lane"+i,"lane"+i,event("STOP",h,clock.time));
        Heat finished=repo.heat(h.id());
        races.deleteParticipant("1001");
        assertEquals(finished,repo.heat(h.id()));
        Heat copy=races.repeat(h.id());assertEquals(List.of(2,3),copy.lanes().stream().map(Lane::lane).toList());
        races.correct(h.id(),1,new Correction("OK",2100L,"Ersatzzeit geprüft"));
        assertEquals(2100L,repo.heat(h.id()).lanes().getFirst().elapsedMs());
        races.deleteParticipant("1002");races.deleteParticipant("1003");
        assertThrows(IllegalStateException.class,()->races.repeat(h.id()));
        assertEquals(3,repo.heat(h.id()).lanes().size());
    }
    @Test void schemaUpgradePreservesExistingParticipantsAndCanRunAgain(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(ds);
        db.execute("""
            CREATE TABLE participant (id VARCHAR(40) PRIMARY KEY,first_name VARCHAR(100) NOT NULL,
            last_name VARCHAR(100) NOT NULL,birth_year INT NOT NULL,category VARCHAR(60) NOT NULL,
            training_group VARCHAR(100) NOT NULL)
            """);
        db.update("INSERT INTO participant VALUES ('alt-01','Anna','Bestand',2016,'offen','Hechte')");
        var schema=new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        schema.setSqlScriptEncoding(StandardCharsets.UTF_8.name());schema.execute(ds);
        RaceRepository migrated=new RaceRepository(db);
        assertEquals("Anna",migrated.participant("alt-01").orElseThrow().firstName());
        migrated.insert(person("neu-01"));
        db.update("UPDATE participant SET deleted=TRUE WHERE id='alt-01'");
        schema.execute(ds);
        assertEquals(List.of("neu-01"),migrated.participants().stream().map(Participant::id).toList());
        assertTrue(migrated.participantIdUsed("alt-01"));
    }
    @Test void correctionRequiresReasonAndKeepsAudit(){
        Heat h=heat(1);start(h);clock.time+=1000;races.timing("lane1","lane1",event("STOP",h,clock.time));
        assertThrows(IllegalArgumentException.class,()->races.correct(h.id(),1,new Correction("OK",1200L,"")));
        races.correct(h.id(),1,new Correction("OK",1200L,"Ersatzstoppuhr geprüft"));
        Lane l=repo.heat(h.id()).lanes().getFirst();assertEquals(1200L,l.elapsedMs());assertNotNull(l.stopMs());
        assertTrue(repo.audit().stream().anyMatch(a->a.get("ACTION_TEXT").toString().contains("Ersatzstoppuhr geprüft")));
    }
    @Test void backgroundFlagIsRetainedForReview(){
        Heat h=heat(1);start(h);clock.time+=1000;
        races.timing("lane1","lane1",new TimingEvent("STOP",UUID.randomUUID().toString(),h.id(),clock.bootId(),clock.time,10,true,"Seite pausiert"));
        assertTrue(repo.heat(h.id()).lanes().getFirst().needsReview());
    }
    @Test void excelTemplateCanBeImportedAndFormulasAreRejected() throws Exception {
        byte[] bytes=excel.template(true);List<Participant> list=excel.read(new ByteArrayInputStream(bytes));assertEquals(6,list.size());assertEquals("1001",list.getFirst().id());
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes));var out=new ByteArrayOutputStream()){
            book.getSheetAt(0).getRow(1).getCell(1).setCellFormula("1+1");book.write(out);
            assertThrows(IllegalArgumentException.class,()->excel.read(new ByteArrayInputStream(out.toByteArray())));
        }
    }
    @Test void laneSnapshotContainsOnlyItsOwnSwimmer(){
        Heat h=heat(3);State state=races.state("lane2");assertEquals(1,state.activeHeat().lanes().size());assertEquals(2,state.activeHeat().lanes().getFirst().lane());
    }
    @Test void disciplineCatalogueValidatesSelectionAndProtectsUsedNames(){
        assertThrows(IllegalArgumentException.class,()->races.addDiscipline(" 50 M FREISTIL "));
        assertThrows(IllegalArgumentException.class,()->races.addDiscipline(" "));
        assertThrows(IllegalArgumentException.class,()->races.createHeat(new NewHeat("Unbekannt","",List.of(new LaneAssignment(1,"1001")))));
        races.addDiscipline("100 m Flossen / Rettung");races.deleteDiscipline("100 m Flossen / Rettung");
        Heat h=heat(1);
        assertThrows(IllegalStateException.class,()->races.deleteDiscipline(h.discipline()));
        assertEquals(1,races.disciplines().stream().filter(d->d.name().equals(h.discipline())).findFirst().orElseThrow().heatCount());
    }
    @Test void recoveryImportsExistingFreeTextDisciplinesOnlyOnce(){
        repo.db().update("INSERT INTO heat(id,discipline,label,heat_status) VALUES (?,'Alt-Disziplin','Bestand','PLANNED')",UUID.randomUUID().toString());
        races.recover();races.recover();
        assertEquals(1,races.disciplines().stream().filter(d->d.name().equals("Alt-Disziplin")).count());
    }
    @Test void falseStartIsIdempotentAndImmediatelyArmsACleanRepeat(){
        Heat h=heat(3);start(h);clock.time+=800;
        races.timing("lane1","lane1",event("STOP",h,clock.time));
        TimingEvent late=event("STOP",h,clock.time),falseStart=event("FALSE_START",h,clock.time);
        races.timing("starter","starter",falseStart);
        Heat repeated=races.state("admin").activeHeat();
        assertNotEquals(h.id(),repeated.id());assertEquals(h.id(),repeated.falseStartOf());
        assertEquals("ARMED",repeated.status());assertNull(repeated.startMs());assertEquals(3,repeated.lanes().size());
        assertTrue(repeated.lanes().stream().allMatch(l->l.elapsedMs()==null&&l.status().equals("PENDING")));
        assertTrue(races.state("admin").devices().stream().noneMatch(Device::ready));
        assertEquals("FALSE_START",repo.heat(h.id()).status());assertEquals(800L,repo.heat(h.id()).lanes().getFirst().elapsedMs());
        assertThrows(IllegalStateException.class,()->races.timing("lane2","lane2",late));
        assertThrows(IllegalStateException.class,()->races.timing("starter","starter",event("START",repeated,clock.time)));
        races.timing("starter","starter",falseStart);assertEquals(2,races.heats().size());
        assertEquals(repeated.id(),races.state("admin").activeHeat().id());
        start(repeated);assertEquals("RUNNING",repo.heat(repeated.id()).status());
    }
    @Test void falseStartRequiresStarterCurrentHeatAndCurrentServer(){
        Heat h=heat(1);
        assertThrows(IllegalStateException.class,()->races.timing("starter","starter",event("FALSE_START",h,clock.time)));
        start(h);
        for(String role:List.of("lane1","admin"))assertThrows(IllegalArgumentException.class,()->races.timing(role,role,event("FALSE_START",h,clock.time)));
        TimingEvent wrongBoot=new TimingEvent("FALSE_START",UUID.randomUUID().toString(),h.id(),"old-server",clock.time,0,false,"");
        assertThrows(IllegalStateException.class,()->races.timing("starter","starter",wrongBoot));
        races.timing("starter","starter",event("FALSE_START",h,clock.time));
        assertThrows(IllegalStateException.class,()->races.timing("starter","starter",event("FALSE_START",h,clock.time)));
        assertEquals(2,races.heats().size());
    }
    @Test void falseStartAlsoInvalidatesAnAlreadyFinishedActiveHeat(){
        Heat h=heat(1);start(h);clock.time+=1000;races.timing("lane1","lane1",event("STOP",h,clock.time));
        assertEquals(1,races.rankings().getFirst().standings().stream().filter(Standing::complete).count());
        races.timing("starter","starter",event("FALSE_START",h,clock.time));
        assertTrue(races.rankings().getFirst().standings().stream().noneMatch(Standing::complete));
        assertThrows(IllegalStateException.class,()->races.correct(h.id(),1,new Correction("OK",1100L,"Nicht zulässig")));
        assertEquals(1000L,repo.heat(h.id()).lanes().getFirst().elapsedMs());
    }
}
```

## src/test/java/de/dlrg/vereinsmeisterschaft/StandingsCalculatorTest.java

```java
package de.dlrg.vereinsmeisterschaft;

import de.dlrg.vereinsmeisterschaft.model.Models.*;
import de.dlrg.vereinsmeisterschaft.service.StandingsCalculator;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StandingsCalculatorTest {
    Participant person(String id,String group) {return new Participant(id,"Vorname","Nachname "+id,2016,"offen",group);}
    Lane lane(Participant p,int number,Long ms,String status,boolean review) {
        return new Lane(number,p.id(),p.firstName(),p.lastName(),p.birthYear(),p.category(),p.trainingGroup(),status,
                ms==null?null:ms.doubleValue(),ms,10.0,review,"");
    }
    Lane ok(Participant p,int number,long ms) {return lane(p,number,ms,"OK",false);}
    Heat heat(long number,String discipline,String status,Lane... lanes) {
        return new Heat("heat-"+number,number,discipline,"",status,1000.0,"boot",5.0,null,List.of(lanes));
    }
    List<GroupRanking> ranks(List<Participant> people,Heat... heats) {
        return StandingsCalculator.calculate(people,List.of(heats),List.of(new Discipline("Freistil",0),new Discipline("Brust",0)));
    }
    @Test void sumsLastValidTimePerDisciplineRatherThanBestTimeOrAllAttempts(){
        Participant p=person("1","Hechte");
        var group=ranks(List.of(p),heat(1,"Freistil","FINISHED",ok(p,1,10000)),
                heat(3,"Brust","FINISHED",ok(p,1,20000)),heat(2,"Freistil","FINISHED",ok(p,1,15001))).getFirst();
        Standing row=group.standings().getFirst();assertEquals(35001,row.totalMs());assertEquals(1,row.place());
        assertEquals(2L,row.times().stream().filter(t->t.discipline().equals("Freistil")).findFirst().orElseThrow().heatNumber());
    }
    @Test void shorterIncompleteTotalCannotWinAndMissingDisciplineHasNoPlace(){
        Participant a=person("1","Hechte"),b=person("2","Hechte");
        var group=ranks(List.of(a,b),heat(1,"Freistil","FINISHED",ok(a,1,10000),ok(b,2,1000)),
                heat(2,"Brust","FINISHED",ok(a,1,20000))).getFirst();
        assertEquals("1",group.standings().getFirst().participant().id());assertEquals(1,group.standings().getFirst().place());
        Standing incomplete=group.standings().getLast();assertNull(incomplete.place());assertFalse(incomplete.complete());
        assertEquals(1000,incomplete.totalMs());assertTrue(incomplete.times().stream().anyMatch(t->t.status().equals("MISSING")));
    }
    @Test void lastValidMeansLastSwumEvenWhenHeatsWerePlannedInADifferentOrder(){
        Participant p=person("1","Hechte");
        Heat earlier=heat(9,"Freistil","FINISHED",ok(p,1,10000));
        Heat later=new Heat("heat-2",2,"Freistil","","FINISHED",2000.0,"boot",5.0,null,List.of(ok(p,1,15000)));
        Standing row=ranks(List.of(p),earlier,later).getFirst().standings().getFirst();
        assertEquals(15000,row.totalMs());assertEquals(2L,row.times().getFirst().heatNumber());
    }
    @Test void trainingGroupsHaveIndependentDisciplinesAndPlacings(){
        Participant a=person("1","Hechte"),b=person("2","Tümmler");
        var groups=ranks(List.of(a,b),heat(1,"Freistil","FINISHED",ok(a,1,30000)),heat(2,"Brust","FINISHED",ok(b,1,5000)));
        assertEquals(2,groups.size());
        for(GroupRanking group:groups){assertEquals(1,group.disciplines().size());assertEquals(1,group.standings().getFirst().place());}
    }
    @Test void equalTotalsSharePlaceAndFollowingPlaceSkipsATiedPosition(){
        Participant a=person("1","Hechte"),b=person("2","Hechte"),c=person("3","Hechte");
        var rows=ranks(List.of(a,b,c),heat(1,"Freistil","FINISHED",ok(a,1,10001),ok(b,2,10001),ok(c,3,10002))).getFirst().standings();
        assertEquals(List.of(1,1,3),rows.stream().map(Standing::place).toList());
    }
    @Test void falseStartsAndAbortedHeatsNeitherCountNorAddRequiredDisciplines(){
        Participant p=person("1","Hechte");
        var group=ranks(List.of(p),heat(1,"Freistil","FINISHED",ok(p,1,20000)),
                heat(2,"Freistil","FALSE_START",ok(p,1,1000)),heat(3,"Brust","ABORTED",ok(p,1,1000))).getFirst();
        assertEquals(List.of("Freistil"),group.disciplines());assertEquals(20000,group.standings().getFirst().totalMs());
    }
    @Test void plannedAndInterruptedDisciplinesPreventPrematureCompleteRanking(){
        Participant p=person("1","Hechte");
        for(String status:List.of("PLANNED","ARMED","RUNNING","INTERRUPTED")) {
            var row=ranks(List.of(p),heat(1,"Freistil","FINISHED",ok(p,1,20000)),
                    heat(2,"Brust",status,ok(p,1,1000))).getFirst().standings().getFirst();
            assertFalse(row.complete());assertNull(row.place());assertEquals(20000,row.totalMs());
        }
    }
    @Test void invalidAndUnreviewedAttemptsAreExcludedWhileAnOlderValidTimeRemains(){
        Participant a=person("1","Hechte"),b=person("2","Hechte");
        for(String bad:List.of("DNS","DNF","DSQ","OK")) {
            var group=ranks(List.of(a,b),heat(1,"Freistil","FINISHED",ok(a,1,20000)),
                    heat(2,"Freistil","FINISHED",lane(a,1,1000L,bad,bad.equals("OK")),lane(b,2,1000L,bad,bad.equals("OK")))).getFirst();
            var good=group.standings().getFirst();assertEquals("1",good.participant().id());assertEquals(20000,good.totalMs());assertEquals(1,good.place());
            assertFalse(group.standings().getLast().complete());
        }
    }
    @Test void peopleWithoutHeatsAreUnrankedAndUnregisteredPeopleDoNotAppear(){
        Participant a=person("1",""),deleted=person("2","");
        var empty=ranks(List.of(a)).getFirst();assertEquals("",empty.trainingGroup());assertFalse(empty.standings().getFirst().complete());
        assertNull(empty.standings().getFirst().place());
        var group=ranks(List.of(a),heat(1,"Freistil","FINISHED",ok(deleted,1,1000),ok(a,2,20000))).getFirst();
        assertEquals(1,group.standings().size());assertEquals("1",group.standings().getFirst().participant().id());
    }
    Participant classified(String id,String group,String category){return new Participant(id,"Vorname","Nachname "+id,2016,category,group);}
    @Test void classViewSeparatesMaleAndFemaleWithinTheSameTrainingGroup(){
        Participant male=classified("1","Grauwale","männlich"),female=classified("2","Grauwale","weiblich");
        List<Heat> heats=List.of(heat(1,"Freistil","FINISHED",ok(male,1,10000),ok(female,2,11000)));
        var classes=StandingsCalculator.calculate(List.of(male,female),heats,List.of(),"classes");
        assertEquals(2,classes.size());
        for(GroupRanking c:classes){assertEquals("Grauwale",c.trainingGroup());assertEquals(1,c.standings().size());assertEquals(1,c.standings().getFirst().place());}
        var all=StandingsCalculator.calculate(List.of(male,female),heats,List.of(),"groups");
        assertEquals(1,all.size());assertNull(all.getFirst().category());assertEquals(List.of(1,2),all.getFirst().standings().stream().map(Standing::place).toList());
    }
    @Test void differentClassProgrammesDoNotMakeTheOtherClassIncomplete(){
        Participant male=classified("1","Grauwale","männlich"),female=classified("2","Grauwale","weiblich");
        List<Heat> heats=List.of(heat(1,"Freistil","FINISHED",ok(male,1,10000)),heat(2,"Brust","FINISHED",ok(female,1,11000)));
        var classes=StandingsCalculator.calculate(List.of(male,female),heats,List.of(),"classes");
        assertTrue(classes.stream().flatMap(c->c.standings().stream()).allMatch(Standing::complete));
        var combined=StandingsCalculator.calculate(List.of(male,female),heats,List.of(),"groups");
        assertTrue(combined.getFirst().standings().stream().noneMatch(Standing::complete));
    }
    @Test void disciplineComparisonCombinesGroupsAndClassesButNeverDifferentDisciplines(){
        Participant a=classified("1","Grauwale","männlich"),b=classified("2","Hechte","weiblich"),c=classified("3","Beluga","offen");
        var comparisons=StandingsCalculator.byDiscipline(List.of(a,b,c),List.of(heat(1,"Freistil","FINISHED",ok(a,1,12000),ok(b,2,10000)),heat(2,"Brust","FINISHED",ok(c,1,5000))),List.of());
        assertEquals(2,comparisons.size());
        var freestyle=comparisons.stream().filter(d->d.discipline().equals("Freistil")).findFirst().orElseThrow();
        assertEquals(List.of("2","1"),freestyle.standings().stream().map(r->r.participant().id()).toList());
        assertEquals(List.of(1,2),freestyle.standings().stream().map(DisciplineStanding::place).toList());
    }
    @Test void disciplineComparisonUsesLastValidAttemptAndPreservesTies(){
        Participant a=person("1","Hechte"),b=person("2","Grauwale");
        var rows=StandingsCalculator.byDiscipline(List.of(a,b),List.of(heat(1,"Freistil","FINISHED",ok(a,1,1000),ok(b,2,1500)),heat(2,"Freistil","FINISHED",ok(a,1,1500)),heat(3,"Freistil","FALSE_START",ok(b,1,100))),List.of()).getFirst().standings();
        assertEquals(List.of(1,1),rows.stream().map(DisciplineStanding::place).toList());
        assertTrue(rows.stream().allMatch(r->r.time().elapsedMs()==1500));
    }
}
```

## src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker

```text
mock-maker-subclass
```

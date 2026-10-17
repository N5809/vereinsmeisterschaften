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

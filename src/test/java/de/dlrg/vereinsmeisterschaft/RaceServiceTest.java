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

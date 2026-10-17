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

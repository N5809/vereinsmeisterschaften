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

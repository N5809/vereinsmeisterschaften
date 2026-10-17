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

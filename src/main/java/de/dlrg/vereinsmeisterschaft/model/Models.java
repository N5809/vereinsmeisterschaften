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

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

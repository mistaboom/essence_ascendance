package com.mistaboom.essence_ascendance.attunement;

import java.util.List;

/** Server-observed action. Skills and integrations report actual outcomes through this same contract. */
public record AttunementEvent(String rootActionId, List<Outcome> outcomes) {
    public AttunementEvent {
        if (rootActionId == null || rootActionId.isBlank() || rootActionId.length() > 256)
            throw new IllegalArgumentException("Missing or oversized root gameplay identity");
        outcomes = List.copyOf(outcomes);
        if (outcomes.size() > 128) throw new IllegalArgumentException("Too many outcomes");
    }
    public record Outcome(String activityId, String sourceSignature, double units,
                          boolean eligible, String rejectionReason) {
        public Outcome {
            if (activityId == null || activityId.length() > 128 || sourceSignature == null || sourceSignature.isBlank()
                    || sourceSignature.length() > 256 || rejectionReason == null || rejectionReason.length() > 256)
                throw new IllegalArgumentException("Invalid outcome metadata");
        }
        public static Outcome eligible(String activityId, String source, double units) {
            return new Outcome(activityId, source, units, true, "");
        }
        public static Outcome rejected(String activityId, String source, String reason) {
            return new Outcome(activityId, source, 0, false, reason);
        }
    }
}

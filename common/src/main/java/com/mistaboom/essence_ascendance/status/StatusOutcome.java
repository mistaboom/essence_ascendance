package com.mistaboom.essence_ascendance.status;

import java.util.List;
import java.util.UUID;

/** One bounded immutable native application observation. No live game objects are retained. */
public record StatusOutcome(long eventId, long tick, UUID defender, int entityId, String dimension,
        String selectedSkill, String effect, String category, List<Instance> requested,
        List<Instance> priorDefender, UUID directEntity, UUID responsibleSource, String relationship,
        String nativeAcceptance, List<Instance> nativeDefender, List<Instance> resultingDefender,
        boolean prevented, boolean removed, List<Instance> copied, String targetAcceptance,
        List<Instance> targetResult, long cooldownUntil, int recursionDepth, UUID attribution,
        String rejectionReason, Instant instant) {
    public static final int MAX_CHAIN = 32;
    public StatusOutcome {
        requested = bounded(requested); priorDefender = bounded(priorDefender);
        nativeDefender = bounded(nativeDefender); resultingDefender = bounded(resultingDefender);
        copied = bounded(copied); targetResult = bounded(targetResult);
        if (eventId <= 0 || recursionDepth < 0 || recursionDepth > MAX_CHAIN) throw new IllegalArgumentException("Invalid status event");
    }
    private static List<Instance> bounded(List<Instance> instances) {
        if (instances.size() > MAX_CHAIN) throw new IllegalArgumentException("Unbounded status chain");
        return List.copyOf(instances);
    }
    public record Instance(int duration, int amplifier, boolean ambient, boolean particles, boolean icon) { }
    /** Instant APIs return void. Observe real resources without claiming their dispatch implies accepted damage. */
    public record Instant(String requestedPotency, double copiedPotency, float defenderHealthBefore,
            float defenderHealthAfter, float defenderAbsorptionBefore, float defenderAbsorptionAfter,
            float targetHealthBefore, float targetHealthAfter, float targetAbsorptionBefore, float targetAbsorptionAfter) { }
    public StatusOutcome withInstant(Instant observation) {
        return new StatusOutcome(eventId, tick, defender, entityId, dimension, selectedSkill, effect, category,
                requested, priorDefender, directEntity, responsibleSource, relationship, nativeAcceptance, nativeDefender,
                resultingDefender, prevented, removed, copied, targetAcceptance, targetResult, cooldownUntil,
                recursionDepth, attribution, rejectionReason, observation);
    }
}

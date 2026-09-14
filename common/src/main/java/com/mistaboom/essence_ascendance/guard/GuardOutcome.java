package com.mistaboom.essence_ascendance.guard;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** One bounded immutable report per native hit. Entity/stack/world objects are never retained. */
public record GuardOutcome(long eventId, long tick, UUID defender, GuardLifecycle.Snapshot guard,
                           String source, UUID direct, UUID responsible, String sourceDecision,
                           double incoming, double blocked, double mitigated, double healthLost, double absorptionLost,
                           boolean accepted, boolean successfulBlock, boolean perfect, int recursionDepth,
                           Vec3 attemptedKnockback, Vec3 acceptedKnockback, String knockbackDecision,
                           KnockbackEchoService.Result echo,
                           double ordinaryReflection, double nativeBlockReflection, double investedBlockReflection,
                           double wardExtension, double skillAmplifier, double requestedReflection,
                           double confirmedReflection, String reflectionDecision) {
    public GuardOutcome withKnockback(Vec3 attempted, Vec3 acceptedDelta, String decision, KnockbackEchoService.Result result) {
        return new GuardOutcome(eventId, tick, defender, guard, source, direct, responsible, sourceDecision,
                incoming, blocked, mitigated, healthLost, absorptionLost, accepted, successfulBlock, perfect,
                recursionDepth, attempted, acceptedDelta, decision, result, ordinaryReflection, nativeBlockReflection,
                investedBlockReflection, wardExtension, skillAmplifier, requestedReflection, confirmedReflection, reflectionDecision);
    }
}

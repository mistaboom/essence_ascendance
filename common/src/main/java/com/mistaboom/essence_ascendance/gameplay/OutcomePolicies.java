package com.mistaboom.essence_ascendance.gameplay;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stateless interpretations shared by future outcome consumers. These helpers neither track
 * sessions nor establish native facts: callers must supply accepted server observations and
 * end their own sessions at the lifecycle boundaries in docs/achievements/CONTRACT.md.
 */
public final class OutcomePolicies {
    public static final double NEARBY_CAT_RADIUS = 8.0;
    public static final long MINECRAFT_DAY_TICKS = 24_000;

    private OutcomePolicies() { }

    /** lastHostileTick must come from accepted hostile damage, never a swing or proximity. */
    public static boolean sameFight(long lastHostileTick, long now, int configuredTimeoutTicks,
                                    boolean sameDimension) {
        return sameDimension && lastHostileTick >= 0 && now >= lastHostileTick
                && configuredTimeoutTicks > 0 && now - lastHostileTick < configuredTimeoutTicks;
    }

    /** Immutable scalar candidate; distance is server-observed squared 3D feet-to-feet distance. */
    public record CatCandidate(UUID id, UUID owner, String dimension, boolean alive,
                               boolean loaded, boolean tamed, double distanceSquared) {
        public CatCandidate {
            Objects.requireNonNull(id);
            Objects.requireNonNull(dimension);
        }
    }

    public static Optional<UUID> nearestOwnedCat(UUID actor, String dimension,
                                                Collection<CatCandidate> candidates) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(dimension);
        return candidates.stream().filter(Objects::nonNull)
                .filter(cat -> cat.alive() && cat.loaded() && cat.tamed() && actor.equals(cat.owner())
                        && dimension.equals(cat.dimension()) && Double.isFinite(cat.distanceSquared())
                        && cat.distanceSquared() >= 0 && cat.distanceSquared() <= NEARBY_CAT_RADIUS * NEARBY_CAT_RADIUS)
                .min(Comparator.comparingDouble(CatCandidate::distanceSquared)
                        .thenComparing(cat -> cat.id().toString()))
                .map(CatCandidate::id);
    }

    /** Calendar day, using the overworld's dayTime for every dimension, including negative times. */
    public static long minecraftDay(long overworldDayTime) {
        return Math.floorDiv(overworldDayTime, MINECRAFT_DAY_TICKS);
    }

    /** A backward clock observation invalidates a session even if both samples share a day. */
    public static boolean sameMinecraftDay(long previousDayTime, long currentDayTime) {
        return currentDayTime >= previousDayTime && minecraftDay(previousDayTime) == minecraftDay(currentDayTime);
    }

    /** Pre-death zero health, prevented lethality, and alive reformation cannot end a life. */
    public static boolean endsLife(boolean nativeDeathCommitted, boolean deathPrevented, boolean reformedAlive) {
        return nativeDeathCommitted && !deathPrevented && !reformedAlive;
    }

    public enum MovementDecision { WAIT, CONTINUE, END }

    /**
     * Sprint distance is horizontal accepted path length. modeEligible includes the live sprint
     * effect, alive/non-mounted state and other rule-specific requirements. Absence of an observed
     * movement sample is not a stop; a sampled zero delta is. Invalid displacement is never clipped.
     */
    public static MovementDecision sprintContinuity(boolean sampleObserved, boolean modeEligible,
                                                     boolean sameDimension, boolean teleported,
                                                     double horizontalDistance, double configuredMaximumDistance) {
        if (!modeEligible || !sameDimension || teleported) return MovementDecision.END;
        if (!sampleObserved) return MovementDecision.WAIT;
        return Double.isFinite(horizontalDistance) && horizontalDistance > 0
                && Double.isFinite(configuredMaximumDistance) && configuredMaximumDistance > 0
                && horizontalDistance <= configuredMaximumDistance
                ? MovementDecision.CONTINUE : MovementDecision.END;
    }
}

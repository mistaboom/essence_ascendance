package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Shared saved-flight and owner-identity policy; runtime facts are supplied only by the server. */
public final class ProjectileLifecycle {
    private ProjectileLifecycle() { }

    public static boolean ownerMatches(ProjectileState state, UUID responsible, UUID currentLife,
                                       ResourceLocation dimension, boolean validOwner) {
        return state != null && validOwner && state.owner.equals(responsible)
                && state.ownerLife.equals(currentLife) && state.dimension.equals(dimension);
    }

    public static boolean accepts(ProjectileState state, ResourceLocation dimension, long now,
                                  ProjectileSource source, boolean validOwner) {
        if (state == null || state.ended || !validOwner || source != state.source || !state.dimension.equals(dimension)
                || now < state.launchedAt || state.remainingTicks <= 0
                || !Double.isFinite(state.remainingRange) || state.remainingRange <= 0.000001) return false;
        long age = now - state.launchedAt;
        return age >= 0 && age < state.profile.lifetimeTicks();
    }
}

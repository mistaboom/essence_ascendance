package com.mistaboom.essence_ascendance.visual.transientfx;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * One compact, server-authored visual cue. Geometry and seeded variation are
 * deliberately reconstructed by the client recipe registry.
 */
public record WorldVisualEvent(
        ResourceLocation recipeId,
        ResourceLocation dimension,
        Vec3 position,
        int sourceEntityId,
        int targetEntityId,
        Vec3 secondEndpoint,
        Vec3 direction,
        float scale,
        float intensity,
        VisualIntensity presentation,
        SemanticVisualColor color,
        int lifetimeTicks,
        long seed,
        float parameterA,
        float parameterB
) {
    public static final int NO_ENTITY = -1;
    public static final int MAX_LIFETIME_TICKS = 20 * 30;

    public WorldVisualEvent {
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(color, "color");
        if (!finite(position) || secondEndpoint != null && !finite(secondEndpoint) || !finite(direction))
            throw new IllegalArgumentException("Visual event vectors must be finite");
        if (!Float.isFinite(scale) || scale <= 0.0F || scale > 64.0F)
            throw new IllegalArgumentException("Visual event scale is out of bounds");
        if (!Float.isFinite(intensity) || intensity < 0.0F || intensity > 4.0F)
            throw new IllegalArgumentException("Visual event intensity is out of bounds");
        if (lifetimeTicks < 1 || lifetimeTicks > MAX_LIFETIME_TICKS)
            throw new IllegalArgumentException("Visual event lifetime is out of bounds");
        if (!Float.isFinite(parameterA) || !Float.isFinite(parameterB))
            throw new IllegalArgumentException("Visual event parameters must be finite");
        if (sourceEntityId < NO_ENTITY || targetEntityId < NO_ENTITY)
            throw new IllegalArgumentException("Visual event entity IDs are invalid");
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}

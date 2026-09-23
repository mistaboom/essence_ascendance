package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.VisualIntensity;
import com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Shared constructors for confirmed combat and projectile presentation events. */
public final class CombatVisualFeedback {
    private CombatVisualFeedback() { }

    public static void projectileDrag(ServerLevel level, Entity projectile, double factor) {
        Vec3 center = projectile.getBoundingBox().getCenter();
        link(level, com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds.WORLD_PROJECTILE_DRAG,
                center, null, projectile, center, projectile.getDeltaMovement(),
                1.0F, 1.0F, VisualIntensity.STANDARD, SemanticVisualColor.DEFENSE, 10,
                level.getGameTime() * 31L ^ projectile.getId(), (float) (1 - factor), 0);
    }

    /** Small shared defensive acknowledgment; replaces provisional native particle sprays. */
    public static void defenseImpact(ServerLevel level, Entity target) {
        if (target.level() != level) return;
        MicroVisualFeedback.world(level,
                com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds.WORLD_IMPACT_PULSE,
                target.getBoundingBox().getCenter(), SemanticVisualColor.DEFENSE, 0.55F, 0.75F,
                10, level.getGameTime() * 31L ^ target.getId());
    }

    public static void at(ServerLevel level, ResourceLocation recipe, Vec3 position, Vec3 direction,
                          float scale, float intensity, VisualIntensity presentation,
                          SemanticVisualColor color, int lifetimeTicks, long seed,
                          float parameterA, float parameterB) {
        emit(level, recipe, position, null, null, null, direction, scale, intensity,
                presentation, color, lifetimeTicks, seed, parameterA, parameterB);
    }

    public static void link(ServerLevel level, ResourceLocation recipe, Vec3 position,
                            Entity source, Entity target, Vec3 secondEndpoint, Vec3 direction,
                            float scale, float intensity, VisualIntensity presentation,
                            SemanticVisualColor color, int lifetimeTicks, long seed,
                            float parameterA, float parameterB) {
        emit(level, recipe, position, source, target, secondEndpoint, direction, scale, intensity,
                presentation, color, lifetimeTicks, seed, parameterA, parameterB);
    }

    private static void emit(ServerLevel level, ResourceLocation recipe, Vec3 position,
                             Entity source, Entity target, Vec3 secondEndpoint, Vec3 direction,
                             float scale, float intensity, VisualIntensity presentation,
                             SemanticVisualColor color, int lifetimeTicks, long seed,
                             float parameterA, float parameterB) {
        Vec3 safeDirection = direction.lengthSqr() > 1.0E-8 ? direction.normalize() : new Vec3(0, 1, 0);
        WorldVisualEvent event = new WorldVisualEvent(recipe, level.dimension().location(), position,
                source == null ? WorldVisualEvent.NO_ENTITY : source.getId(),
                target == null ? WorldVisualEvent.NO_ENTITY : target.getId(),
                secondEndpoint, safeDirection, scale, intensity, presentation, color,
                lifetimeTicks, seed, parameterA, parameterB);
        TransientVisualDispatch.nearby(level, event, presentation.worldRange());
    }
}

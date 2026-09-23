package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
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

    public static void shieldRam(ServerLevel level, Entity source, Entity target, Vec3 direction) {
        Vec3 center = target.getBoundingBox().getCenter();
        link(level, TransientVisualIds.WORLD_SHIELD_RAM, center, null, target, center, direction,
                1.20F, 1.0F, VisualIntensity.STANDARD, SemanticVisualColor.DEFENSE, 18,
                level.getGameTime() * 31L ^ source.getId() * 17L ^ target.getId(), 0, 0);
    }

    public static void guardResponse(ServerLevel level, Entity defender, int lifetimeTicks) {
        Vec3 center = defender.getBoundingBox().getCenter();
        link(level, TransientVisualIds.WORLD_GUARD_RESPONSE, center, defender, defender, null,
                defender.getLookAngle(), 1.0F, 1.0F, VisualIntensity.STANDARD,
                SemanticVisualColor.DEFENSE, Math.clamp(lifetimeTicks, 1, WorldVisualEvent.MAX_LIFETIME_TICKS),
                level.getGameTime() * 31L ^ defender.getId(), 0, 0);
    }

    public static void riposteEnd(ServerLevel level, Entity defender, boolean release) {
        Vec3 center = defender.getBoundingBox().getCenter();
        link(level, TransientVisualIds.WORLD_RIPOSTE_RELEASE, center, defender, defender, null,
                defender.getLookAngle(), 1.0F, 1.0F, VisualIntensity.STANDARD,
                SemanticVisualColor.DEFENSE, release ? 12 : 1,
                level.getGameTime() * 31L ^ defender.getId(), release ? 1 : 0, 0);
    }

    public static void reflectionReturn(ServerLevel level, Entity defender, Entity target, boolean ward) {
        Vec3 start = defender.getBoundingBox().getCenter();
        Vec3 end = target.getBoundingBox().getCenter();
        link(level, TransientVisualIds.WORLD_REFLECTION_RETURN, start, defender, target, end,
                end.subtract(start), 1.10F, 1.0F, VisualIntensity.MAJOR,
                SemanticVisualColor.DEFENSE, 16,
                level.getGameTime() * 31L ^ defender.getId() * 17L ^ target.getId(), ward ? 1 : 0, 0);
    }

    public static void crowdReprisal(ServerLevel level, Entity source, Entity target) {
        Vec3 start = source.getBoundingBox().getCenter();
        Vec3 end = target.getBoundingBox().getCenter();
        link(level, TransientVisualIds.WORLD_CROWD_REPRISAL, start, source, target, end,
                end.subtract(start), 0.90F, 0.85F, VisualIntensity.STANDARD,
                SemanticVisualColor.DEFENSE, 14,
                level.getGameTime() * 31L ^ source.getId() * 17L ^ target.getId(), 0, 0);
    }

    public static void counterstrike(ServerLevel level, Entity source, Entity target,
                                     boolean riposte, boolean storedForce) {
        Vec3 start = source.getBoundingBox().getCenter();
        Vec3 end = target.getBoundingBox().getCenter();
        if (storedForce)
            link(level, TransientVisualIds.WORLD_STORED_FORCE, start, source, target, end,
                    end.subtract(start), 1.15F, 1.0F, VisualIntensity.MAJOR,
                    SemanticVisualColor.DEFENSE, 12,
                    level.getGameTime() * 31L ^ source.getId() * 17L ^ target.getId(),
                    riposte ? 1 : 0, 1);
        if (riposte) riposteEnd(level, source, true);
    }

    public static void statusRejection(ServerLevel level, Entity defender, Entity responsible, boolean mirror) {
        Vec3 start = defender.getBoundingBox().getCenter();
        Vec3 end = responsible == null ? null : responsible.getBoundingBox().getCenter();
        Vec3 direction = end == null ? defender.getLookAngle() : end.subtract(start);
        link(level, TransientVisualIds.WORLD_STATUS_REJECTION, start, defender, responsible, end,
                direction, 1.05F, 1.0F, mirror ? VisualIntensity.MAJOR : VisualIntensity.STANDARD,
                SemanticVisualColor.DEFENSE, mirror ? 24 : 20,
                level.getGameTime() * 31L ^ defender.getId() * 17L ^ (responsible == null ? 0 : responsible.getId()),
                mirror ? 1 : 0, 0);
    }

    public static void shatteringWard(ServerLevel level, Entity owner, double radius) {
        if (!Double.isFinite(radius) || radius <= 0) return;
        Vec3 center = owner.getBoundingBox().getCenter();
        link(level, TransientVisualIds.WORLD_SHATTERING_WARD, center, owner, null, null, Vec3.ZERO,
                (float) Math.min(64, radius), 1.0F, VisualIntensity.MAJOR,
                SemanticVisualColor.VITALITY, 28, level.getGameTime() * 31L ^ owner.getId(), 0, 0);
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

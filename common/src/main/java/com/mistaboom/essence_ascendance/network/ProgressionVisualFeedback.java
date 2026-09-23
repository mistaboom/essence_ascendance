package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import com.mistaboom.essence_ascendance.visual.transientfx.VisualIntensity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Readable server-confirmed Mobility, Vitality and Utility event compositions. */
public final class ProgressionVisualFeedback {
    private ProgressionVisualFeedback() { }

    public static void mobilityLaunch(ServerPlayer player, Vec3 origin, Vec3 impulse,
                                      float power, int style) {
        CombatVisualFeedback.link(player.serverLevel(), TransientVisualIds.WORLD_MOBILITY_LAUNCH,
                origin, style >= 2 ? player : null, null, null, impulse,
                Math.clamp(0.95F + power * 0.42F, 0.95F, 1.55F),
                Math.clamp(0.85F + power * 0.20F, 0.85F, 1.35F), VisualIntensity.STANDARD,
                SemanticVisualColor.MOBILITY, 20,
                seed(player.serverLevel(), player, style), style, power);
    }

    public static void momentumSurge(ServerPlayer player) {
        Vec3 center = player.position().add(0, 0.045, 0);
        Vec3 forward = player.getLookAngle().multiply(1, 0, 1);
        if (forward.lengthSqr() < 1.0E-6) forward = new Vec3(
                -Math.sin(Math.toRadians(player.getYRot())), 0, Math.cos(Math.toRadians(player.getYRot())));
        CombatVisualFeedback.link(player.serverLevel(), TransientVisualIds.WORLD_MOMENTUM_SURGE,
                center, player, null, null, forward, 1.15F, 1.0F,
                VisualIntensity.STANDARD, SemanticVisualColor.MOBILITY, 30,
                seed(player.serverLevel(), player, 17), 0, 0);
    }

    public static void vitalityPurge(ServerPlayer player, double recovered) {
        CombatVisualFeedback.link(player.serverLevel(), TransientVisualIds.WORLD_VITALITY_PURGE,
                player.getBoundingBox().getCenter(), player, player, null, Vec3.ZERO,
                scaled(recovered, 1.0F, 1.55F), 1.0F, VisualIntensity.STANDARD,
                SemanticVisualColor.VITALITY, 24, seed(player.serverLevel(), player, 23),
                (float) Math.min(4, recovered), 0);
    }

    public static void vitalitySurge(ServerPlayer player, double healthFractionLost) {
        CombatVisualFeedback.link(player.serverLevel(), TransientVisualIds.WORLD_VITALITY_SURGE,
                player.getBoundingBox().getCenter(), player, player, null, player.getDeltaMovement(),
                Math.clamp(1.05F + (float) healthFractionLost, 1.05F, 1.55F), 1.0F,
                VisualIntensity.STANDARD, SemanticVisualColor.VITALITY, 24,
                seed(player.serverLevel(), player, 29), 0, 0);
    }

    public static void deathDefiance(ServerPlayer player, boolean spirit, boolean reform) {
        CombatVisualFeedback.link(player.serverLevel(), TransientVisualIds.WORLD_DEATH_DEFIANCE,
                player.getBoundingBox().getCenter(), player, player, null, new Vec3(0, 1, 0),
                reform ? 1.15F : 1.45F, 1.2F, VisualIntensity.MAJOR,
                SemanticVisualColor.VITALITY, reform ? 24 : 34,
                seed(player.serverLevel(), player, spirit ? 37 : 31), spirit ? 1 : 0, reform ? 1 : 0);
    }

    public static void recoveryTransfer(ServerPlayer player, double amount, boolean toFood) {
        CombatVisualFeedback.link(player.serverLevel(), TransientVisualIds.WORLD_RECOVERY_TRANSFER,
                player.getBoundingBox().getCenter(), player, player, null, new Vec3(0, 1, 0),
                scaled(amount, 0.90F, 1.30F), 0.9F, VisualIntensity.STANDARD,
                SemanticVisualColor.VITALITY, 20, seed(player.serverLevel(), player, toFood ? 43 : 41),
                toFood ? 1 : 0, (float) Math.min(4, amount));
    }

    public static void wardConvergence(ServerPlayer player, Entity victim, double amount) {
        CombatVisualFeedback.at(player.serverLevel(), TransientVisualIds.WORLD_WARD_CONVERGENCE,
                victim.position().add(0, 0.22, 0), new Vec3(0, 1, 0),
                scaled(amount, 1.0F, 1.45F), 0.95F, VisualIntensity.STANDARD,
                SemanticVisualColor.VITALITY, 44, seed(player.serverLevel(), victim, 47), 0, 0);
    }

    public static void utilityTransfer(ServerPlayer source, Entity target) {
        Vec3 start = source.getBoundingBox().getCenter();
        Vec3 end = target.getBoundingBox().getCenter();
        CombatVisualFeedback.link(source.serverLevel(), TransientVisualIds.WORLD_UTILITY_TRANSFER,
                start, source, target, end, end.subtract(start), 1.0F, 0.9F,
                VisualIntensity.STANDARD, SemanticVisualColor.UTILITY, 22,
                seed(source.serverLevel(), target, source.getId()), 0, 0);
    }

    public static void containmentSeal(ServerLevel level, Vec3 center) {
        CombatVisualFeedback.at(level, TransientVisualIds.WORLD_CONTAINMENT_SEAL, center,
                new Vec3(0, 1, 0), 2.20F, 1.1F, VisualIntensity.MAJOR,
                SemanticVisualColor.UTILITY, 28,
                level.getGameTime() * 31L ^ Double.doubleToLongBits(center.x + center.y * 31 + center.z * 961),
                0, 0);
    }

    public static void sanctuaryRelease(ServerPlayer owner, Entity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        CombatVisualFeedback.link(owner.serverLevel(), TransientVisualIds.WORLD_SANCTUARY_RELEASE,
                center, owner, target, center, new Vec3(0, 1, 0), 1.0F, 0.85F,
                VisualIntensity.STANDARD, SemanticVisualColor.UTILITY, 38,
                seed(owner.serverLevel(), target, owner.getId() * 3), target.getBbHeight() * 0.5F, 0);
    }

    public static void companionRecall(ServerPlayer owner, Entity companion, Vec3 formerPosition) {
        Vec3 end = companion.getBoundingBox().getCenter();
        Vec3 direction = end.subtract(formerPosition);
        CombatVisualFeedback.link(owner.serverLevel(), TransientVisualIds.WORLD_COMPANION_RECALL,
                end, owner, companion, formerPosition, direction, 1.05F, 0.95F,
                VisualIntensity.STANDARD, SemanticVisualColor.UTILITY, 24,
                seed(owner.serverLevel(), companion, owner.getId() * 5), 0, 0);
    }

    private static float scaled(double amount, float minimum, float maximum) {
        if (!Double.isFinite(amount) || amount <= 0) return minimum;
        return Math.clamp(minimum + (float) Math.log1p(amount) * 0.16F, minimum, maximum);
    }

    private static long seed(ServerLevel level, Entity entity, int salt) {
        return level.getGameTime() * 31L ^ entity.getId() * 17L ^ salt;
    }
}

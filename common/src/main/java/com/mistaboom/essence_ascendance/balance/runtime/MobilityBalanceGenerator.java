package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.config.MobilityBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics;
import net.minecraft.world.level.block.Blocks;

/** Uses the existing tier budget, semantic availability and global reference time, not a second tuning table. */
final class MobilityBalanceGenerator {
    private MobilityBalanceGenerator() { }
    static MobilityBalanceSettings generate(BalanceSettings settings) {
        var running = SkillBalanceSemantics.require(SkillIds.RUNNING_MOMENTUM);
        var vault = SkillBalanceSemantics.require(SkillIds.MOMENTUM_VAULT);
        var rush = SkillBalanceSemantics.require(SkillIds.RUSH);
        double window = settings.generation().survivalWindowSeconds();
        double speedWeight = running.weights().get(CapabilityAxis.GROUND_SPEED);
        double speed = SkillGenerationBudget.headroom(settings, running.skillId()) * speedWeight;
        double vaultPower = SkillGenerationBudget.headroom(settings, vault.skillId())
                * vault.weights().get(CapabilityAxis.VERTICAL_MOVEMENT);
        double rushPower = SkillGenerationBudget.headroom(settings, rush.skillId())
                * rush.weights().get(CapabilityAxis.CONVENIENCE);
        // A fence's real collision top defines the obstacle contract. It is not a free jump/flight budget.
        double fenceHeight = NativeBonusMechanics.collisionTop(Blocks.OAK_FENCE.defaultBlockState());
        double availability = running.expectedAvailability();
        var charged = SkillBalanceSemantics.require(SkillIds.CHARGED_JUMP);
        double impact = power(settings, SkillIds.IMPACT_CONTROL, CapabilityAxis.FALL_CONTROL);
        double chargedHeight = power(settings, SkillIds.CHARGED_JUMP, CapabilityAxis.JUMP);
        double chargedControl = power(settings, SkillIds.CHARGED_JUMP, CapabilityAxis.VERTICAL_MOVEMENT);
        double doubleHeight = power(settings, SkillIds.DOUBLE_JUMP, CapabilityAxis.JUMP);
        double doubleControl = power(settings, SkillIds.DOUBLE_JUMP, CapabilityAxis.FALL_CONTROL);
        double vector = power(settings, SkillIds.VECTOR_JUMP, CapabilityAxis.JUMP)
                + power(settings, SkillIds.VECTOR_JUMP, CapabilityAxis.VERTICAL_MOVEMENT);
        double brake = power(settings, SkillIds.VECTOR_JUMP, CapabilityAxis.FALL_CONTROL);
        return new MobilityBalanceSettings(
                new MobilityBalanceSettings.RunningMomentum(Math.clamp(speed, 0, 16),
                        ticks(window * availability), ticks(window * (1 - availability) * speedWeight),
                        Math.clamp(Math.toDegrees(Math.acos(availability)), 1, 180)),
                new MobilityBalanceSettings.MomentumVault(fenceHeight, 1 / (1 + vaultPower)),
                new MobilityBalanceSettings.Rush(ticks(window * rush.expectedAvailability() * (1 + rushPower))),
                new MobilityBalanceSettings.ImpactControl(impact / (1 + impact)),
                new MobilityBalanceSettings.ChargedJump(ticks(window * (1 - charged.expectedAvailability()) / (1 + chargedHeight)),
                        Math.min(64, chargedHeight), Math.min(64, chargedControl)),
                new MobilityBalanceSettings.AirJump(Math.min(64, doubleHeight), Math.min(64, doubleControl)),
                new MobilityBalanceSettings.VectorJump(Math.min(64, vector), brake / (1 + brake)));
    }
    private static double power(BalanceSettings settings, net.minecraft.resources.ResourceLocation skill, CapabilityAxis axis) {
        return SkillGenerationBudget.headroom(settings, skill) * SkillBalanceSemantics.require(skill).weights().get(axis);
    }
    private static int ticks(double seconds) { return Math.clamp((int) Math.ceil(seconds * 20), 1, 72_000); }
}

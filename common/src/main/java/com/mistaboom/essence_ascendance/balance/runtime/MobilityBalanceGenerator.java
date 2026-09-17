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
        return new MobilityBalanceSettings(
                new MobilityBalanceSettings.RunningMomentum(Math.clamp(speed, 0, 16),
                        ticks(window * availability), ticks(window * (1 - availability) * speedWeight),
                        Math.clamp(Math.toDegrees(Math.acos(availability)), 1, 180)),
                new MobilityBalanceSettings.MomentumVault(fenceHeight, 1 / (1 + vaultPower)),
                new MobilityBalanceSettings.Rush(ticks(window * rush.expectedAvailability() * (1 + rushPower))));
    }
    private static int ticks(double seconds) { return Math.clamp((int) Math.ceil(seconds * 20), 1, 72_000); }
}

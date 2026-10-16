package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Actual shield/rod consumers: player progression and innate equipment tiers are independent. */
public final class EquipmentBonusTierTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); EquipmentProfiles.init();
        var runtime = args.length == 0 ? RuntimeBalanceDefinition.bootstrap() : RuntimeBalanceDefinition.fromJson(
                com.mistaboom.essence_ascendance.balance.generated.BalanceProfileStore.read(java.nio.file.Path.of(args[0])).section("runtime"));
        EssenceConfigManager.install(runtime);
        try {
            var data = new PlayerEssenceData();
            for (boolean funded : new boolean[]{false, true}) {
                for (var stat : EssenceStatRegistry.values()) data.setInvested(stat, funded
                        ? runtime.config().balanceProfile().getInvestmentCap(AscendanceTiers.TRANSCENDENT, stat) : 0);
                for (var playerTier : AscendanceTierRegistry.values()) {
                    data.setTier(playerTier);
                    for (var itemTier : EquipmentTier.values()) for (boolean fractured : new boolean[]{false, true}) {
                        boolean eligible = !fractured && itemTier != EquipmentTier.LATENT;
                        var rod = EquipmentFishingRodService.resolve(data, itemTier, fractured);
                        check(rod.itemTier() == itemTier && rod.effectiveTier().ascendanceTier().equals(playerTier),
                                "Rod separates its innate tier from player progression");
                        check(rod.baseline().equals(runtime.config().fishingRodBalance().tier(itemTier)),
                                "Rod innate range, force and durability retain the physical tier");
                        for (var entry : EquipmentProfiles.FISHING_ROD.statApplicability(EquipmentActivationType.HELD).entrySet()) {
                            var stat = EssenceStatRegistry.get(entry.getKey()).orElseThrow();
                            equal(rod.bonus(stat), eligible ? StatScalingService.evaluate(data, stat).scaledBonus() * entry.getValue() : 0,
                                    "Rod " + stat.id() + ", player=" + playerTier.id() + ", item=" + itemTier);
                        }
                        var shield = EquipmentShieldService.resolve(data, itemTier, fractured);
                        check(shield.itemTier() == itemTier && shield.effectiveTier().ascendanceTier().equals(playerTier),
                                "Shield separates its innate tier from player progression");
                        var tuning = runtime.config().shieldBalance();
                        equal(shield.nativeDurability(), tuning.durability().get(itemTier), "Shield native durability follows item tier");
                        equal(shield.innateReflectionBonusPercent(), fractured ? 0 : tuning.innateReflectionBonus().get(itemTier),
                                "Shield innate reflection follows item tier");
                        equal(shield.amplification(), fractured ? 0 : tuning.blockAmplification().get(itemTier),
                                "Shield block amplification follows item tier");
                        equal(shield.investedReflectionPercent(), shieldBonus(data, eligible, EssenceStats.DAMAGE_REFLECTION, false), "Shield reflection");
                        equal(shield.durabilityEfficiencyPercent(), shieldBonus(data, eligible, EssenceStats.DURABILITY_EFFICIENCY, true), "Shield durability efficiency");
                        equal(shield.guardReadinessPercent(), shieldBonus(data, eligible, EssenceStats.GUARD_READINESS, true), "Shield readiness");
                        equal(shield.guardedMovementPercent(), shieldBonus(data, eligible, EssenceStats.GUARDED_MOVEMENT, true), "Shield movement");
                        equal(shield.knockbackResistancePercent(), shieldBonus(data, eligible, EssenceStats.KNOCKBACK_RESISTANCE, true), "Shield resistance");
                    }
                }
            }
            data.setTier(AscendanceTiers.TRANSCENDENT);
            check(EquipmentFishingRodService.resolve(data, EquipmentTier.DORMANT, false).fishingMultiplier() > 1,
                    "A Transcendent player channels funded Fishing Speed through a Dormant rod");
            check(EquipmentShieldService.resolve(data, EquipmentTier.DORMANT, false).guardedMovementPercent() > 0,
                    "A Transcendent player channels funded Guarded Movement through a Dormant shield");
            System.out.println("EquipmentBonusTierTest: " + checks + " player-only Bonus limits and independent innate tiers PASS");
        } finally { EssenceConfigManager.reset(); }
    }

    private static double shieldBonus(PlayerEssenceData data, boolean eligible, StatDefinition stat, boolean bounded) {
        var activation = stat == EssenceStats.DAMAGE_REFLECTION || stat == EssenceStats.DURABILITY_EFFICIENCY
                ? EquipmentActivationType.HELD : EquipmentActivationType.GUARDING;
        double value = eligible ? StatScalingService.evaluate(data, stat).scaledBonus()
                * EquipmentProfiles.SHIELD.statStrength(activation, stat) : 0;
        return bounded ? ShieldMath.percent(value) : value;
    }

    private static void equal(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-8, message + ": actual=" + actual + ", expected=" + expected);
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
}

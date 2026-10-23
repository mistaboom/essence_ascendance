package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.item.ItemStack;

/** Weapon-specific defaults. Tier physical damage/speed/durability remain in the equipment baselines. */
public final class GlaiveTuning {
    public static final String PERSONALIZED_RANGE = EquipmentAttackRange.PERSONALIZED_PERK;
    public static final double MAXIMUM_RANGE = EquipmentAttackRange.MAXIMUM_SUPPORTED;
    public static final double MAXIMUM_PIVOT_STEP = 16;
    public static final float STRONG_HIT_THRESHOLD = .9F;
    public static final double DAMAGE_MULTIPLIER = 1.0;
    public static final double ATTACK_SPEED_MULTIPLIER = 0.75;
    public static final double DURABILITY_MULTIPLIER = 1.0;
    public static final double SWIPE_ARC_DEGREES = 120.0;
    public static final ActionPhases STAB = new ActionPhases(0.5, 0, 4, 2, 0.5, 7);
    // One short physical extension reaches the full blade before the damaging arc.
    // The quicker arc spends the remaining cadence in recovery, retaining the same attack-rate gate.
    public static final ActionPhases SWIPE = new ActionPhases(1.0, 1, 5, 3, 1.0, 14);

    private GlaiveTuning() { }

    public static double stabTipDistance(double range,double elapsedTicks) {
        return WeaponActionGeometry.REST_TIP+(range-WeaponActionGeometry.REST_TIP)
                *Math.clamp(elapsedTicks/STAB.outwardTicks(),0,1);
    }

    public static double swipeTipDistance(double range,double elapsedTicks) {
        return WeaponActionGeometry.REST_TIP+(range-WeaponActionGeometry.REST_TIP)
                *Math.clamp(elapsedTicks/SWIPE.extensionTicks(),0,1);
    }

    public static double swipeAngle(double elapsedTicks) {
        double progress=Math.clamp((elapsedTicks-SWIPE.extensionTicks())/SWIPE.outwardTicks(),0,1);
        // A quick mid-stroke acceleration reads as a strike instead of an even, mechanical sweep.
        double stroke=progress*progress*(3-2*progress);
        return SWIPE_ARC_DEGREES*(stroke-.5);
    }

    public static double maximumRange(EquipmentTier tier) {
        return switch (tier) {
            case LATENT -> 4;
            case DORMANT -> 5;
            case AWAKENED -> 6;
            case RESONANT -> 8;
            case ASCENDANT -> 9;
            case TRANSCENDENT -> 10;
        };
    }

    /** Snapshot this at acceptance; never derive weapon geometry from interaction Reach. */
    public static double maximumRange(ItemStack stack) {
        return maximumRange(FracturedEquipmentData.isFractured(stack)
                ? EquipmentTier.LATENT : EquipmentTierData.tier(stack));
    }

    /** Shared range resolution uses the same holder eligibility as appearance and flavor. */
    public static double maximumRange(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        return EquipmentAttackRange.maximum(player, stack);
    }

    /** Outward and retraction are fixed; attack speed changes only the remaining recovery. */
    public record ActionPhases(double damageScalar, int extensionTicks, int outwardTicks, int retractionTicks,
                               double cadenceScale, int minimumCycleTicks) {
        public ActionPhases {
            if (!Double.isFinite(damageScalar) || damageScalar <= 0 || extensionTicks < 0 || outwardTicks <= 0
                    || retractionTicks <= 0 || !Double.isFinite(cadenceScale) || cadenceScale <= 0
                    || minimumCycleTicks < extensionTicks + outwardTicks + retractionTicks + 1)
                throw new IllegalArgumentException("Invalid weapon action tuning");
        }

        public int recoveryTicks(double resolvedActionsPerSecond) {
            int cycle=Math.max(minimumCycleTicks, (int) Math.ceil(EquipmentWeaponService.rateToTicks(resolvedActionsPerSecond)
                    * cadenceScale));
            return cycle - extensionTicks - outwardTicks - retractionTicks;
        }
    }
}

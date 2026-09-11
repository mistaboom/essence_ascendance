package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/**
 * Provisional server-side skill-effect balance, separate from purchase costs
 * and eligibility. Percent fields use 4.0 for 4%; health thresholds use 0.5
 * for half normal health. No value in this first batch is final balance.
 */
public record SkillEffectBalanceSettings(
        Frenzy frenzy,
        ArmorCrack armorCrack,
        Desperation desperation,
        DeathRush deathRush
) {
    public SkillEffectBalanceSettings {
        Objects.requireNonNull(frenzy, "Frenzy balance cannot be null");
        Objects.requireNonNull(armorCrack, "Armor Crack balance cannot be null");
        Objects.requireNonNull(desperation, "Desperation balance cannot be null");
        Objects.requireNonNull(deathRush, "Death Rush balance cannot be null");
    }

    public record Frenzy(
            int maxStacks,
            int chainTimeoutTicks,
            double damageBonusPercentPerStack,
            double attackSpeedBonusPercentPerStack
    ) {
        public Frenzy {
            integerRange("frenzy.max_stacks", maxStacks, 0, 100);
            integerRange("frenzy.chain_timeout_ticks", chainTimeoutTicks, 1, 72_000);
            finiteRange("frenzy.damage_bonus_percent_per_stack", damageBonusPercentPerStack, 0, 100);
            finiteRange("frenzy.attack_speed_bonus_percent_per_stack", attackSpeedBonusPercentPerStack, 0, 100);
        }
    }

    public record ArmorCrack(
            int maxStacks,
            int stackTimeoutTicks,
            double armorReductionPerStack,
            double toughnessReductionPerStack
    ) {
        public ArmorCrack {
            integerRange("armor_crack.max_stacks", maxStacks, 0, 100);
            integerRange("armor_crack.stack_timeout_ticks", stackTimeoutTicks, 1, 72_000);
            finiteRange("armor_crack.armor_reduction_per_stack", armorReductionPerStack, 0, 1_024);
            finiteRange("armor_crack.toughness_reduction_per_stack", toughnessReductionPerStack, 0, 1_024);
        }
    }

    public record Desperation(double maxDamageBonusPercent) {
        public Desperation {
            finiteRange("desperation.max_damage_bonus_percent", maxDamageBonusPercent, 0, 1_000);
        }
    }

    public record DeathRush(
            double killHealthThreshold,
            double nearDeathHealthThreshold,
            int maxStacks,
            int stackDurationTicks,
            double attackSpeedBonusPercentPerStack,
            double bowDrawSpeedBonusPercentPerStack,
            double castSpeedBonusPercentPerStack
    ) {
        public DeathRush {
            finiteRange("death_rush.kill_health_threshold", killHealthThreshold, 0, 1);
            finiteRange("death_rush.near_death_health_threshold", nearDeathHealthThreshold, 0, killHealthThreshold);
            integerRange("death_rush.max_stacks", maxStacks, 0, 100);
            integerRange("death_rush.stack_duration_ticks", stackDurationTicks, 1, 72_000);
            finiteRange("death_rush.attack_speed_bonus_percent_per_stack", attackSpeedBonusPercentPerStack, 0, 100);
            finiteRange("death_rush.bow_draw_speed_bonus_percent_per_stack", bowDrawSpeedBonusPercentPerStack, 0, 100);
            finiteRange("death_rush.cast_speed_bonus_percent_per_stack", castSpeedBonusPercentPerStack, 0, 100);
        }
    }

    /** Reuses constructor validation for the in-mod pure configuration diagnostic. */
    public void validate() {
        new Frenzy(frenzy.maxStacks(), frenzy.chainTimeoutTicks(),
                frenzy.damageBonusPercentPerStack(), frenzy.attackSpeedBonusPercentPerStack());
        new ArmorCrack(armorCrack.maxStacks(), armorCrack.stackTimeoutTicks(),
                armorCrack.armorReductionPerStack(), armorCrack.toughnessReductionPerStack());
        new Desperation(desperation.maxDamageBonusPercent());
        new DeathRush(deathRush.killHealthThreshold(), deathRush.nearDeathHealthThreshold(),
                deathRush.maxStacks(), deathRush.stackDurationTicks(),
                deathRush.attackSpeedBonusPercentPerStack(), deathRush.bowDrawSpeedBonusPercentPerStack(),
                deathRush.castSpeedBonusPercentPerStack());
    }

    public static SkillEffectBalanceSettings defaults() {
        return new SkillEffectBalanceSettings(
                new Frenzy(5, 60, 4.0, 4.0),
                new ArmorCrack(5, 60, 2.0, 0.5),
                new Desperation(30.0),
                new DeathRush(0.5, 0.2, 5, 160, 5.0, 5.0, 5.0)
        );
    }

    private static void integerRange(String field, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("skill_effects.offense.combat_stances." + field
                    + " must be between " + minimum + " and " + maximum);
        }
    }

    private static void finiteRange(String field, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("skill_effects.offense.combat_stances." + field
                    + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}

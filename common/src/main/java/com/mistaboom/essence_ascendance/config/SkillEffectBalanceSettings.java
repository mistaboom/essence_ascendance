package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/**
 * Provisional server-side skill-effect balance, separate from purchase costs
 * and eligibility. Percent fields use 4.0 for 4%; health thresholds use 0.5
 * for half normal health. No value in these implemented families is final balance.
 */
public record SkillEffectBalanceSettings(
        Frenzy frenzy,
        ArmorCrack armorCrack,
        Desperation desperation,
        DeathRush deathRush,
        Kindling kindling,
        Combustion combustion,
        Frostbite frostbite,
        Shatter shatter,
        StaticCharge staticCharge,
        ChainStrike chainStrike,
        ProjectileBalanceSettings projectiles,
        GuardBalanceSettings guard,
        PostureBalanceSettings posture,
        StatusBalanceSettings status,
        VitalityBalanceSettings vitality,
        MobilityBalanceSettings mobility,
        GatheringBalanceSettings gathering
) {
    public SkillEffectBalanceSettings {
        Objects.requireNonNull(frenzy, "Frenzy balance cannot be null");
        Objects.requireNonNull(armorCrack, "Armor Crack balance cannot be null");
        Objects.requireNonNull(desperation, "Desperation balance cannot be null");
        Objects.requireNonNull(deathRush, "Death Rush balance cannot be null");
        Objects.requireNonNull(kindling, "Kindling balance cannot be null");
        Objects.requireNonNull(combustion, "Combustion balance cannot be null");
        Objects.requireNonNull(frostbite, "Frostbite balance cannot be null");
        Objects.requireNonNull(shatter, "Shatter balance cannot be null");
        Objects.requireNonNull(staticCharge, "Static Charge balance cannot be null");
        Objects.requireNonNull(chainStrike, "Chain Strike balance cannot be null");
        Objects.requireNonNull(projectiles, "Projectile balance cannot be null");
        Objects.requireNonNull(guard, "Missing guard balance; rebuild generated balance");
        Objects.requireNonNull(posture, "Missing posture balance; rebuild generated balance");
        Objects.requireNonNull(status, "Missing status balance; rebuild generated balance");
        Objects.requireNonNull(vitality, "Missing Vitality balance; rebuild generated balance");
        Objects.requireNonNull(mobility, "Missing Mobility balance; rebuild generated balance");
        Objects.requireNonNull(gathering, "Missing Gathering balance; rebuild generated balance");
    }

    public record Frenzy(
            int maxStacks,
            int chainTimeoutTicks,
            double damageBonusPercentPerStack,
            double attackSpeedBonusPercentPerStack
    ) {
        public Frenzy {
            integerRange("combat_stances.frenzy.max_stacks", maxStacks, 0, 100);
            integerRange("combat_stances.frenzy.chain_timeout_ticks", chainTimeoutTicks, 1, 72_000);
            finiteRange("combat_stances.frenzy.damage_bonus_percent_per_stack", damageBonusPercentPerStack, 0, 100);
            finiteRange("combat_stances.frenzy.attack_speed_bonus_percent_per_stack", attackSpeedBonusPercentPerStack, 0, 100);
        }
    }

    public record ArmorCrack(
            int maxStacks,
            int stackTimeoutTicks,
            double armorReductionPerStack,
            double toughnessReductionPerStack
    ) {
        public ArmorCrack {
            integerRange("combat_stances.armor_crack.max_stacks", maxStacks, 0, 100);
            integerRange("combat_stances.armor_crack.stack_timeout_ticks", stackTimeoutTicks, 1, 72_000);
            finiteRange("combat_stances.armor_crack.armor_reduction_per_stack", armorReductionPerStack, 0, 1_024);
            finiteRange("combat_stances.armor_crack.toughness_reduction_per_stack", toughnessReductionPerStack, 0, 1_024);
        }
    }

    public record Desperation(double maxDamageBonusPercent) {
        public Desperation {
            finiteRange("combat_stances.desperation.max_damage_bonus_percent", maxDamageBonusPercent, 0, 1_000);
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
            finiteRange("combat_stances.death_rush.kill_health_threshold", killHealthThreshold, 0, 1);
            finiteRange("combat_stances.death_rush.near_death_health_threshold", nearDeathHealthThreshold, 0, killHealthThreshold);
            integerRange("combat_stances.death_rush.max_stacks", maxStacks, 0, 100);
            integerRange("combat_stances.death_rush.stack_duration_ticks", stackDurationTicks, 1, 72_000);
            finiteRange("combat_stances.death_rush.attack_speed_bonus_percent_per_stack", attackSpeedBonusPercentPerStack, 0, 100);
            finiteRange("combat_stances.death_rush.bow_draw_speed_bonus_percent_per_stack", bowDrawSpeedBonusPercentPerStack, 0, 100);
            finiteRange("combat_stances.death_rush.cast_speed_bonus_percent_per_stack", castSpeedBonusPercentPerStack, 0, 100);
        }
    }

    public record Kindling(int maxHeat, int heatPerHit, int heatExpiryTicks,
                           int ignitionDurationTicks, double burningDamagePercentPerSecond,
                           double burningDamageAmplificationPercent) {
        public Kindling {
            integerRange("elemental_imbuement.kindling.max_heat", maxHeat, 0, 100);
            integerRange("elemental_imbuement.kindling.heat_per_hit", heatPerHit, 0, 100);
            integerRange("elemental_imbuement.kindling.heat_expiry_ticks", heatExpiryTicks, 1, 72_000);
            integerRange("elemental_imbuement.kindling.ignition_duration_ticks", ignitionDurationTicks, 1, 72_000);
            finiteRange("elemental_imbuement.kindling.burning_damage_percent_per_second",
                    burningDamagePercentPerSecond, 0, 1_000);
            finiteRange("elemental_imbuement.kindling.burning_damage_amplification_percent",
                    burningDamageAmplificationPercent, 0, 1_000);
        }
    }

    public record Combustion(double damage, double radius, int targetsPerBurst,
                             int maximumGeneration, int rootTargetBudget,
                             double generationDamageFalloff, int seededIgnitionTicks) {
        public Combustion {
            finiteRange("elemental_imbuement.combustion.damage", damage, 0, 1_024);
            finiteRange("elemental_imbuement.combustion.radius", radius, 0, 64);
            integerRange("elemental_imbuement.combustion.targets_per_burst", targetsPerBurst, 0, 100);
            integerRange("elemental_imbuement.combustion.maximum_generation", maximumGeneration, 0, 16);
            integerRange("elemental_imbuement.combustion.root_target_budget", rootTargetBudget, 0, 1_000);
            finiteRange("elemental_imbuement.combustion.generation_damage_falloff", generationDamageFalloff, 0, 1);
            integerRange("elemental_imbuement.combustion.seeded_ignition_ticks", seededIgnitionTicks, 1, 72_000);
        }
    }

    public record Frostbite(int maxChill, int chillPerHit, int chillExpiryTicks,
                            double slowPerStack, double maximumProgressiveSlow,
                            int freezeDurationTicks, double frozenSlow) {
        public Frostbite {
            integerRange("elemental_imbuement.frostbite.max_chill", maxChill, 0, 100);
            integerRange("elemental_imbuement.frostbite.chill_per_hit", chillPerHit, 0, 100);
            integerRange("elemental_imbuement.frostbite.chill_expiry_ticks", chillExpiryTicks, 1, 72_000);
            finiteRange("elemental_imbuement.frostbite.slow_per_stack", slowPerStack, 0, 0.95);
            finiteRange("elemental_imbuement.frostbite.maximum_progressive_slow", maximumProgressiveSlow, 0, 0.95);
            finiteRange("elemental_imbuement.frostbite.frozen_slow", frozenSlow, 0, 0.99);
            integerRange("elemental_imbuement.frostbite.freeze_duration_ticks", freezeDurationTicks, 1, 72_000);
        }
    }

    public record Shatter(double shardDamage, double radius, int maximumTargets) {
        public Shatter {
            finiteRange("elemental_imbuement.shatter.shard_damage", shardDamage, 0, 1_024);
            finiteRange("elemental_imbuement.shatter.radius", radius, 0, 64);
            integerRange("elemental_imbuement.shatter.maximum_targets", maximumTargets, 0, 100);
        }
    }

    public record StaticCharge(double maximumCharge, double sprintPerTick, double jumpCharge,
                               double fallPerTick, double flightPerTick, double glidePerTick,
                               int idleGraceTicks, double decayPerTick, double lightningDamage) {
        public StaticCharge {
            finiteRange("elemental_imbuement.static_charge.maximum_charge", maximumCharge, 0.01, 1_000_000);
            finiteRange("elemental_imbuement.static_charge.sprint_per_tick", sprintPerTick, 0, 1_000);
            finiteRange("elemental_imbuement.static_charge.jump_charge", jumpCharge, 0, 1_000);
            finiteRange("elemental_imbuement.static_charge.fall_per_tick", fallPerTick, 0, 1_000);
            finiteRange("elemental_imbuement.static_charge.flight_per_tick", flightPerTick, 0, 1_000);
            finiteRange("elemental_imbuement.static_charge.glide_per_tick", glidePerTick, 0, 1_000);
            integerRange("elemental_imbuement.static_charge.idle_grace_ticks", idleGraceTicks, 0, 72_000);
            finiteRange("elemental_imbuement.static_charge.decay_per_tick", decayPerTick, 0, 1_000);
            finiteRange("elemental_imbuement.static_charge.lightning_damage", lightningDamage, 0, 1_024);
        }
    }

    public record ChainStrike(int maximumJumps, double radius, double damageFalloff) {
        public ChainStrike {
            integerRange("elemental_imbuement.chain_strike.maximum_jumps", maximumJumps, 0, 100);
            finiteRange("elemental_imbuement.chain_strike.radius", radius, 0, 64);
            finiteRange("elemental_imbuement.chain_strike.damage_falloff", damageFalloff, 0, 1);
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
        new Kindling(kindling.maxHeat(), kindling.heatPerHit(), kindling.heatExpiryTicks(),
                kindling.ignitionDurationTicks(), kindling.burningDamagePercentPerSecond(),
                kindling.burningDamageAmplificationPercent());
        new Combustion(combustion.damage(), combustion.radius(), combustion.targetsPerBurst(),
                combustion.maximumGeneration(), combustion.rootTargetBudget(),
                combustion.generationDamageFalloff(), combustion.seededIgnitionTicks());
        new Frostbite(frostbite.maxChill(), frostbite.chillPerHit(), frostbite.chillExpiryTicks(),
                frostbite.slowPerStack(), frostbite.maximumProgressiveSlow(),
                frostbite.freezeDurationTicks(), frostbite.frozenSlow());
        new Shatter(shatter.shardDamage(), shatter.radius(), shatter.maximumTargets());
        new StaticCharge(staticCharge.maximumCharge(), staticCharge.sprintPerTick(),
                staticCharge.jumpCharge(), staticCharge.fallPerTick(), staticCharge.flightPerTick(),
                staticCharge.glidePerTick(), staticCharge.idleGraceTicks(), staticCharge.decayPerTick(),
                staticCharge.lightningDamage());
        new ChainStrike(chainStrike.maximumJumps(), chainStrike.radius(), chainStrike.damageFalloff());
        projectiles.validate();
        guard.validate();
        posture.validate();
        status.validate();
        vitality.validate();
        mobility.validate();
        gathering.validate();
    }

    public static SkillEffectBalanceSettings defaults() {
        return new SkillEffectBalanceSettings(
                new Frenzy(5, 60, 4.0, 4.0),
                new ArmorCrack(5, 60, 2.0, 0.5),
                new Desperation(30.0),
                new DeathRush(0.5, 0.2, 5, 160, 5.0, 5.0, 5.0),
                new Kindling(5, 1, 100, 100, 20.0, 10.0),
                new Combustion(3.0, 4.0, 6, 2, 18, 0.75, 80),
                new Frostbite(5, 1, 100, 0.08, 0.40, 80, 0.95),
                new Shatter(3.0, 5.0, 6),
                new StaticCharge(100.0, 1.0, 4.0, 0.50, 0.35, 0.60, 40, 0.50, 4.0),
                new ChainStrike(3, 6.0, 0.75),
                ProjectileBalanceSettings.defaults(),
                GuardBalanceSettings.defaults(),
                PostureBalanceSettings.defaults(),
                StatusBalanceSettings.defaults(),
                VitalityBalanceSettings.defaults(),
                MobilityBalanceSettings.defaults(),
                GatheringBalanceSettings.defaults()
        );
    }

    private static void integerRange(String field, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("skill_effects.offense." + field
                    + " must be between " + minimum + " and " + maximum);
        }
    }

    private static void finiteRange(String field, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("skill_effects.offense." + field
                    + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}

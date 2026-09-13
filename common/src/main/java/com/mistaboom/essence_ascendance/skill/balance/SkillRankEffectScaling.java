package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Typed parameter consumers, evaluated only after profile or effective-rank changes. */
public final class SkillRankEffectScaling {
    private static final Map<ResourceLocation, BiConsumer<Parameters, Double>> RULES = new LinkedHashMap<>();
    static {
        register(SkillIds.FRENZY, (p, f) -> {
            var v = p.frenzy;
            p.frenzy = new SkillEffectBalanceSettings.Frenzy(v.maxStacks(), v.chainTimeoutTicks(),
                    scale(v.damageBonusPercentPerStack(), f, 100), scale(v.attackSpeedBonusPercentPerStack(), f, 100));
        });
        register(SkillIds.ARMOR_CRACK, (p, f) -> {
            var v = p.armorCrack;
            p.armorCrack = new SkillEffectBalanceSettings.ArmorCrack(v.maxStacks(), v.stackTimeoutTicks(),
                    scale(v.armorReductionPerStack(), f, 1024), scale(v.toughnessReductionPerStack(), f, 1024));
        });
        register(SkillIds.DESPERATION, (p, f) -> p.desperation = new SkillEffectBalanceSettings.Desperation(
                scale(p.desperation.maxDamageBonusPercent(), f, 1000)));
        register(SkillIds.DEATH_RUSH, (p, f) -> {
            var v = p.deathRush;
            p.deathRush = new SkillEffectBalanceSettings.DeathRush(v.killHealthThreshold(), v.nearDeathHealthThreshold(),
                    v.maxStacks(), v.stackDurationTicks(), scale(v.attackSpeedBonusPercentPerStack(), f, 100),
                    scale(v.bowDrawSpeedBonusPercentPerStack(), f, 100), scale(v.castSpeedBonusPercentPerStack(), f, 100));
        });
        register(SkillIds.KINDLING, (p, f) -> {
            var v = p.kindling;
            p.kindling = new SkillEffectBalanceSettings.Kindling(v.maxHeat(), v.heatPerHit(), v.heatExpiryTicks(),
                    v.ignitionDurationTicks(), scale(v.burningDamagePercentPerSecond(), f, 1000),
                    scale(v.burningDamageAmplificationPercent(), f, 1000));
        });
        register(SkillIds.COMBUSTION, (p, f) -> {
            var v = p.combustion;
            p.combustion = new SkillEffectBalanceSettings.Combustion(scale(v.damage(), f, 1024), v.radius(),
                    v.targetsPerBurst(), v.maximumGeneration(), v.rootTargetBudget(), v.generationDamageFalloff(), v.seededIgnitionTicks());
        });
        register(SkillIds.FROSTBITE, (p, f) -> {
            var v = p.frostbite;
            p.frostbite = new SkillEffectBalanceSettings.Frostbite(v.maxChill(), v.chillPerHit(), v.chillExpiryTicks(),
                    v.slowPerStack(), v.maximumProgressiveSlow(), (int) scale(v.freezeDurationTicks(), f, 72000), v.frozenSlow());
        });
        register(SkillIds.SHATTER, (p, f) -> {
            var v = p.shatter;
            p.shatter = new SkillEffectBalanceSettings.Shatter(scale(v.shardDamage(), f, 1024), v.radius(), v.maximumTargets());
        });
        register(SkillIds.STATIC_CHARGE, (p, f) -> {
            var v = p.staticCharge;
            p.staticCharge = new SkillEffectBalanceSettings.StaticCharge(v.maximumCharge(), v.sprintPerTick(), v.jumpCharge(),
                    v.fallPerTick(), v.flightPerTick(), v.glidePerTick(), v.idleGraceTicks(), v.decayPerTick(),
                    scale(v.lightningDamage(), f, 1024));
        });
        register(SkillIds.CHAIN_STRIKE, (p, f) -> {
            var v = p.chainStrike;
            p.chainStrike = new SkillEffectBalanceSettings.ChainStrike(v.maximumJumps(), v.radius(), retained(v.damageFalloff(), f));
        });
        register(SkillIds.HOMING_PROJECTILE, (p, f) -> {
            var v = p.projectiles;
            p.projectiles = projectile(v, homing(v.arrow(), f), homing(v.caster(), f),
                    v.ricochetDamageMultiplier(), v.piercingDamageMultiplier(), v.piercingShieldDamageMultiplier());
        });
        register(SkillIds.RICOCHET, (p, f) -> {
            var v = p.projectiles;
            p.projectiles = projectile(v, v.arrow(), v.caster(), retained(v.ricochetDamageMultiplier(), f),
                    v.piercingDamageMultiplier(), v.piercingShieldDamageMultiplier());
        });
        register(SkillIds.PIERCING_PROJECTILE, (p, f) -> {
            var v = p.projectiles;
            p.projectiles = projectile(v, v.arrow(), v.caster(), v.ricochetDamageMultiplier(),
                    retained(v.piercingDamageMultiplier(), f), retained(v.piercingShieldDamageMultiplier(), f));
        });
    }
    private SkillRankEffectScaling() { }

    /** A future effect registers its typed numeric parameter consumer alongside its handler. */
    public static void register(ResourceLocation id, BiConsumer<Parameters, Double> rule) {
        if (id == null || rule == null || RULES.putIfAbsent(id, rule) != null)
            throw new IllegalArgumentException("Duplicate or invalid rank effect consumer: " + id);
    }

    public static double factor(ResourceLocation id, int rank) {
        if (rank <= 1) return 1.0;
        return SkillBalanceRuntime.require(id.toString(), rank).powerMultiplier()
                / SkillBalanceRuntime.require(id.toString(), 1).powerMultiplier();
    }

    public static SkillEffectBalanceSettings apply(SkillEffectBalanceSettings base, Map<ResourceLocation, Integer> effectiveRanks) {
        if (effectiveRanks.values().stream().noneMatch(rank -> rank > 1)) return base;
        Parameters parameters = new Parameters(base);
        effectiveRanks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getValue() <= 1) return;
            var rule = RULES.get(entry.getKey());
            if (rule != null) rule.accept(parameters, factor(entry.getKey(), entry.getValue()));
        });
        return parameters.build();
    }

    private static double scale(double value, double factor, double maximum) { return Math.min(maximum, value * factor); }
    // The sum of a geometric continuation remains bounded by the rank factor.
    private static double retained(double value, double factor) { return value <= 0 ? 0 : 1.0 - (1.0 - value) / factor; }
    private static ProjectileBalanceSettings.Profile homing(ProjectileBalanceSettings.Profile v, double factor) {
        return new ProjectileBalanceSettings.Profile(v.range(), v.speed(), v.lifetimeTicks(), v.acquisitionRange(),
                v.acquisitionConeDegrees(), scale(v.turnDegreesPerTick(), factor, 45));
    }
    private static ProjectileBalanceSettings projectile(ProjectileBalanceSettings v, ProjectileBalanceSettings.Profile arrow,
            ProjectileBalanceSettings.Profile caster, double ricochet, double piercing, double shield) {
        return new ProjectileBalanceSettings(arrow, caster, v.ricochets(), v.ricochetRadius(), ricochet,
                v.penetrations(), piercing, shield, v.maximumImpacts(), v.maximumSpeed());
    }

    public static final class Parameters {
        public SkillEffectBalanceSettings.Frenzy frenzy;
        public SkillEffectBalanceSettings.ArmorCrack armorCrack;
        public SkillEffectBalanceSettings.Desperation desperation;
        public SkillEffectBalanceSettings.DeathRush deathRush;
        public SkillEffectBalanceSettings.Kindling kindling;
        public SkillEffectBalanceSettings.Combustion combustion;
        public SkillEffectBalanceSettings.Frostbite frostbite;
        public SkillEffectBalanceSettings.Shatter shatter;
        public SkillEffectBalanceSettings.StaticCharge staticCharge;
        public SkillEffectBalanceSettings.ChainStrike chainStrike;
        public ProjectileBalanceSettings projectiles;
        private Parameters(SkillEffectBalanceSettings v) {
            frenzy=v.frenzy(); armorCrack=v.armorCrack(); desperation=v.desperation(); deathRush=v.deathRush();
            kindling=v.kindling(); combustion=v.combustion(); frostbite=v.frostbite(); shatter=v.shatter();
            staticCharge=v.staticCharge(); chainStrike=v.chainStrike(); projectiles=v.projectiles();
        }
        private SkillEffectBalanceSettings build() {
            return new SkillEffectBalanceSettings(frenzy, armorCrack, desperation, deathRush, kindling,
                    combustion, frostbite, shatter, staticCharge, chainStrike, projectiles);
        }
    }
}

package com.mistaboom.essence_ascendance.skill.tooltip;

import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Typed, localized descriptions shared by every skill UI. Values are read from resolved gameplay settings.
 * Add a definition here alongside a new effect handler; never compute a second balance profile for UI. */
public final class SkillTooltipRegistry {
    public record Value(ToDoubleFunction<SkillEffectBalanceSettings> read, double displayScale) {
        public String format(SkillEffectBalanceSettings settings) { return SkillValueText.number(read.applyAsDouble(settings) * displayScale); }
    }
    public record Line(String key, List<Value> values) {
        public Line { values = List.copyOf(values); }
        public List<String> arguments(SkillEffectBalanceSettings settings) { return values.stream().map(v -> v.format(settings)).toList(); }
        public Component render(SkillEffectBalanceSettings settings) {
            Object[] args = arguments(settings).stream().map(value -> Component.literal(value).withStyle(ChatFormatting.WHITE)).toArray();
            return Component.translatable(key, args);
        }
    }
    private static final Map<ResourceLocation, List<Line>> DEFINITIONS = new LinkedHashMap<>();
    static {
        define(SkillIds.FRENZY,
                line("skill.essence_ascendance.frenzy.description.resolved" , n(s -> s.frenzy().damageBonusPercentPerStack()), n(s -> s.frenzy().attackSpeedBonusPercentPerStack()), n(s -> s.frenzy().maxStacks())),
                line("skill.essence_ascendance.frenzy.description.resolved.1" , sec(s -> s.frenzy().chainTimeoutTicks())));
        define(SkillIds.ARMOR_CRACK,
                line("skill.essence_ascendance.armor_crack.description.resolved" , n(s -> s.armorCrack().armorReductionPerStack()), n(s -> s.armorCrack().toughnessReductionPerStack()), n(s -> s.armorCrack().maxStacks())),
                line("skill.essence_ascendance.armor_crack.description.resolved.1" , sec(s -> s.armorCrack().stackTimeoutTicks())));
        define(SkillIds.DESPERATION,
                line("skill.essence_ascendance.desperation.description.resolved" , n(s -> s.desperation().maxDamageBonusPercent())));
        define(SkillIds.DEATH_RUSH,
                line("skill.essence_ascendance.death_rush.description.resolved" , pct(s -> s.deathRush().killHealthThreshold()), n(s -> s.deathRush().attackSpeedBonusPercentPerStack()), n(s -> s.deathRush().bowDrawSpeedBonusPercentPerStack()), n(s -> s.deathRush().castSpeedBonusPercentPerStack())),
                line("skill.essence_ascendance.death_rush.description.resolved.1" , n(s -> s.deathRush().maxStacks()), sec(s -> s.deathRush().stackDurationTicks()), pct(s -> s.deathRush().nearDeathHealthThreshold())));
        define(SkillIds.KINDLING,
                line("skill.essence_ascendance.kindling.description.resolved" , n(s -> s.kindling().heatPerHit()), n(s -> s.kindling().maxHeat()), sec(s -> s.kindling().ignitionDurationTicks()), n(s -> s.kindling().burningDamageAmplificationPercent())),
                line("skill.essence_ascendance.kindling.description.resolved.1" , n(s -> s.kindling().burningDamagePercentPerSecond()), sec(s -> s.kindling().heatExpiryTicks())));
        define(SkillIds.COMBUSTION,
                line("skill.essence_ascendance.combustion.description.resolved" , n(s -> s.combustion().damage()), n(s -> s.combustion().radius()), n(s -> s.combustion().targetsPerBurst())),
                line("skill.essence_ascendance.combustion.description.resolved.1" , pct(s -> s.combustion().generationDamageFalloff()), n(s -> s.combustion().maximumGeneration()), n(s -> s.combustion().rootTargetBudget()), sec(s -> s.combustion().seededIgnitionTicks())));
        define(SkillIds.FROSTBITE,
                line("skill.essence_ascendance.frostbite.description.resolved" , n(s -> s.frostbite().chillPerHit()), n(s -> s.frostbite().maxChill()), pct(s -> s.frostbite().slowPerStack()), pct(s -> s.frostbite().maximumProgressiveSlow())),
                line("skill.essence_ascendance.frostbite.description.resolved.1" , sec(s -> s.frostbite().freezeDurationTicks()), pct(s -> s.frostbite().frozenSlow()), sec(s -> s.frostbite().chillExpiryTicks())));
        define(SkillIds.SHATTER,
                line("skill.essence_ascendance.shatter.description.resolved" , n(s -> s.shatter().shardDamage()), n(s -> s.shatter().maximumTargets()), n(s -> s.shatter().radius())));
        define(SkillIds.STATIC_CHARGE,
                line("skill.essence_ascendance.static_charge.description.resolved" , n(s -> s.staticCharge().maximumCharge()), n(s -> s.staticCharge().lightningDamage())),
                line("skill.essence_ascendance.static_charge.description.resolved.1" , n(s -> s.staticCharge().sprintPerTick() * 20), n(s -> s.staticCharge().jumpCharge()), n(s -> s.staticCharge().fallPerTick() * 20), n(s -> s.staticCharge().flightPerTick() * 20), n(s -> s.staticCharge().glidePerTick() * 20), sec(s -> s.staticCharge().idleGraceTicks()), n(s -> s.staticCharge().decayPerTick() * 20)));
        define(SkillIds.CHAIN_STRIKE,
                line("skill.essence_ascendance.chain_strike.description.resolved" , n(s -> s.chainStrike().maximumJumps()), n(s -> s.chainStrike().radius()), pct(s -> s.chainStrike().damageFalloff())));
        define(SkillIds.HOMING_PROJECTILE,
                line("skill.essence_ascendance.homing_projectile.description.resolved" , n(s -> s.projectiles().arrow().acquisitionRange()), n(s -> s.projectiles().arrow().acquisitionConeDegrees()), n(s -> s.projectiles().arrow().turnDegreesPerTick())),
                line("skill.essence_ascendance.homing_projectile.description.resolved.1" , n(s -> s.projectiles().caster().acquisitionRange()), n(s -> s.projectiles().caster().acquisitionConeDegrees()), n(s -> s.projectiles().caster().turnDegreesPerTick())));
        define(SkillIds.RICOCHET,
                line("skill.essence_ascendance.ricochet.description.resolved" , n(s -> Math.min(s.projectiles().ricochets(), s.projectiles().maximumImpacts() - 1)), n(s -> s.projectiles().ricochetRadius()), pct(s -> s.projectiles().ricochetDamageMultiplier())));
        define(SkillIds.PIERCING_PROJECTILE,
                line("skill.essence_ascendance.piercing_projectile.description.resolved" , n(s -> Math.min(s.projectiles().penetrations(), s.projectiles().maximumImpacts() - 1)), pct(s -> s.projectiles().piercingDamageMultiplier())),
                line("skill.essence_ascendance.piercing_projectile.description.resolved.1" , pct(s -> s.projectiles().piercingShieldDamageMultiplier())));
        define(SkillIds.EXPLOSIVE_PAYLOAD,
                line("skill.essence_ascendance.explosive_payload.description.resolved" , n(s -> s.projectiles().explosiveRadius()), pct(s -> s.projectiles().explosiveDamageScale()), n(s -> s.combustion().targetsPerBurst()), n(s -> s.projectiles().payloadTriggerBudget())),
                line("skill.essence_ascendance.explosive_payload.description.resolved.1"));
        define(SkillIds.ROOTING_PAYLOAD,
                line("skill.essence_ascendance.rooting_payload.description.resolved" , sec(s -> s.projectiles().rootDurationTicks()), sec(s -> s.projectiles().rootMaxDurationTicks()), n(s -> s.projectiles().payloadTriggerBudget())),
                line("skill.essence_ascendance.rooting_payload.description.resolved.1" , sec(s -> s.projectiles().rootDurationTicks())));
        define(SkillIds.PROJECTILE_DRAG_FIELD,
                line("skill.essence_ascendance.projectile_drag_field.description.resolved" , n(s -> s.projectiles().control().outerRadius()), pct(s -> s.projectiles().control().minimumSpeedFactor()), n(s -> s.projectiles().control().innerRadius())),
                line("skill.essence_ascendance.projectile_drag_field.description.resolved.1"));
        define(SkillIds.INTERCEPTOR,
                line("skill.essence_ascendance.interceptor.description.resolved" , pct(s -> s.projectiles().control().readinessThreshold()), n(s -> s.projectiles().control().swingBudget()), n(s -> s.projectiles().control().swingRange())),
                line("skill.essence_ascendance.interceptor.description.resolved.1" , n(s -> s.projectiles().control().swingRadius()), n(s -> s.projectiles().control().swingHalfAngleDegrees())));
        define(SkillIds.TRAJECTORY_THEFT,
                line("skill.essence_ascendance.trajectory_theft.description.resolved" , n(s -> s.projectiles().control().theftSpeedMultiplier()), n(s -> s.projectiles().control().theftTargetRange()), n(s -> s.projectiles().control().theftAimConeDegrees())),
                line("skill.essence_ascendance.trajectory_theft.description.resolved.1" , n(s -> s.projectiles().control().theftTurnDegreesPerTick()), n(s -> s.projectiles().control().redirectBudget())));
        define(SkillIds.GUARDED_ADVANCE,
                line("skill.essence_ascendance.guarded_advance.description.resolved" , pct(s -> s.guard().mobility().slowdownRemoval()), n(s -> s.guard().mobility().stepHeight())));
        define(SkillIds.SHIELD_RAM,
                line("skill.essence_ascendance.shield_ram.description.resolved" , sec(s -> s.guard().ram().staggerTicks()), n(s -> s.guard().ram().knockback())),
                line("skill.essence_ascendance.shield_ram.description.resolved.1" , n(s -> s.guard().ram().minimumSpeed()), pct(s -> s.guard().ram().staggerMovementMultiplier()), n(s -> s.guard().ram().contactLimit()), sec(s -> s.guard().ram().repeatCooldownTicks())));
        define(SkillIds.REFLEXIVE_WARD,
                line("skill.essence_ascendance.reflexive_ward.description.resolved" , pct(s -> s.guard().ward().preventedReflectionScale())),
                line("skill.essence_ascendance.reflexive_ward.description.resolved.1" , n(s -> s.guard().ward().knockbackEchoScale()), n(s -> s.guard().ward().knockbackEchoCap())));
        define(SkillIds.STORED_FORCE,
                line("skill.essence_ascendance.stored_force.description.resolved" , pct(s -> s.guard().storedForce().conversion()), n(s -> s.guard().storedForce().capacity()), sec(s -> s.guard().storedForce().durationTicks())),
                line("skill.essence_ascendance.stored_force.description.resolved.1" , n(s -> s.guard().storedForce().damageScale()), n(s -> s.guard().storedForce().knockbackScale())));
        define(SkillIds.GUARD_AMPLIFIER,
                line("skill.essence_ascendance.guard_amplifier.description.resolved" , n(s -> s.guard().amplifier().perBlockGrowth()), n(s -> s.guard().amplifier().maximumMultiplier()), sec(s -> s.guard().amplifier().durationTicks())),
                line("skill.essence_ascendance.guard_amplifier.description.resolved.1" , sec(s -> s.guard().perfectGuard().windowTicks())));
        define(SkillIds.CROWD_REPRISAL,
                line("skill.essence_ascendance.crowd_reprisal.description.resolved" , pct(s -> s.guard().reprisal().damageScale()), n(s -> s.guard().reprisal().maximumTargets()), n(s -> s.guard().reprisal().radius())));
        define(SkillIds.RIPOSTE,
                line("skill.essence_ascendance.riposte.description.resolved" , sec(s -> s.guard().riposte().durationTicks()), pct(s -> s.guard().riposte().damageScale()), n(s -> s.guard().riposte().bonusReach())),
                line("skill.essence_ascendance.riposte.description.resolved.1" , sec(s -> s.guard().perfectGuard().windowTicks()), sec(s -> s.guard().riposte().protectionTicks())));
        define(SkillIds.EVASIVE_CURRENT,
                line("skill.essence_ascendance.evasive_current.description.resolved" , pct(s -> s.posture().evasive().maximumDodgeChance()), sec(s -> s.posture().evasive().buildTicks()), sec(s -> s.posture().evasive().drainTicks())),
                line("skill.essence_ascendance.evasive_current.description.resolved.1" , pct(s -> s.posture().evasive().successDrainFraction()), pct(s -> s.posture().evasive().hitDrainFraction())));
        define(SkillIds.BULWARK_STANCE,
                line("skill.essence_ascendance.bulwark_stance.description.resolved" , pct(s -> s.posture().bulwark().maximumResistance()), sec(s -> s.posture().bulwark().buildTicks()), sec(s -> s.posture().bulwark().drainTicks())),
                line("skill.essence_ascendance.bulwark_stance.description.resolved.1" , n(s -> s.posture().bulwark().threatRange()), n(s -> s.posture().bulwark().facingDegrees()), pct(s -> s.posture().bulwark().knockbackThreshold())));
        define(SkillIds.ADAPTIVE_GUARD,
                line("skill.essence_ascendance.adaptive_guard.description.resolved" , pct(s -> s.posture().adaptive().resistancePerStack()), n(s -> s.posture().adaptive().minimumHits()), n(s -> s.posture().adaptive().maximumStacks())),
                line("skill.essence_ascendance.adaptive_guard.description.resolved.1" , pct(s -> s.posture().adaptive().resistancePerStack() * Math.max(0, s.posture().adaptive().maximumStacks() - s.posture().adaptive().minimumHits() + 1)), sec(s -> s.posture().adaptive().windowTicks())));
        define(SkillIds.STATUS_MIRROR,
                line("skill.essence_ascendance.status_mirror.description.resolved" , sec(s -> s.status().mirrorCooldownTicks())),
                line("skill.essence_ascendance.status_mirror.description.resolved.1" , sec(s -> s.status().mirrorMaximumDurationTicks()), n(s -> s.status().mirrorMaximumAmplifier() + 1)));
        define(SkillIds.PURE_STATE,
                line("skill.essence_ascendance.pure_state.description.resolved"));
        define(SkillIds.RISING_RECOVERY,
                line("skill.essence_ascendance.rising_recovery.description.resolved" , n(s -> 1 + s.vitality().risingRecovery().maxSpeedBonus())),
                line("skill.essence_ascendance.rising_recovery.description.resolved.1" , n(s -> s.vitality().risingRecovery().maxSpeedBonus()), n(s -> s.vitality().risingRecovery().recoveryCurveExponent())));
        define(SkillIds.LIFE_STEAL,
                line("skill.essence_ascendance.life_steal.description.resolved" , pct(s -> s.vitality().lifeSteal().baseHealingFraction()), pct(s -> s.vitality().lifeSteal().perHitHealingFraction()), n(s -> s.vitality().lifeSteal().maxChainHits())),
                line("skill.essence_ascendance.life_steal.description.resolved.1" , pct(s -> s.vitality().lifeSteal().baseHealingFraction() + (s.vitality().lifeSteal().maxChainHits() - 1) * s.vitality().lifeSteal().perHitHealingFraction()), sec(s -> s.vitality().lifeSteal().chainTimeoutTicks())));
        define(SkillIds.FEAST_REFLEX,
                line("skill.essence_ascendance.feast_reflex.description.resolved" , pct(s -> s.vitality().feastReflex().useDurationMultiplier())),
                line("skill.essence_ascendance.feast_reflex.description.resolved.1"));
        define(SkillIds.INNER_SUSTENANCE,
                line("skill.essence_ascendance.inner_sustenance.description.resolved" , n(s -> s.vitality().innerSustenance().hungerPerRecovery()), n(s -> s.vitality().innerSustenance().saturationPerRecovery()), sec(s -> s.vitality().innerSustenance().hungerRecoveryIntervalTicks()), sec(s -> s.vitality().innerSustenance().combatTimeoutTicks())),
                line("skill.essence_ascendance.inner_sustenance.description.resolved.1"));
        define(SkillIds.HUNGER_WARD,
                line("skill.essence_ascendance.hunger_ward.description.resolved" , pct(s -> s.vitality().damage().hungerWard().damageShare())),
                line("skill.essence_ascendance.hunger_ward.description.resolved.1" , n(s -> s.vitality().damage().hungerWard().healthPerFoodPoint())));
        define(SkillIds.STAGGERED_PAIN,
                line("skill.essence_ascendance.staggered_pain.description.resolved" , sec(s -> s.vitality().damage().staggeredPain().paymentTicks())),
                line("skill.essence_ascendance.staggered_pain.description.resolved.1"));
        define(SkillIds.DAMAGE_CEILING,
                line("skill.essence_ascendance.damage_ceiling.description.resolved" , pct(s -> s.vitality().damage().damageCeiling().damageTakenFraction())),
                line("skill.essence_ascendance.damage_ceiling.description.resolved.1" , pct(s -> s.vitality().damage().damageCeiling().damageTakenFraction()), sec(s -> s.vitality().damage().damageCeiling().combatTimeoutTicks())));
        define(SkillIds.METABOLIC_CONVERSION,
                line("skill.essence_ascendance.metabolic_conversion.description.resolved" , n(s -> s.vitality().damage().metabolicConversion().foodPointsPerOverflowHealth()), n(s -> s.vitality().damage().metabolicConversion().healthPerNutrition())),
                line("skill.essence_ascendance.metabolic_conversion.description.resolved.1"));
        define(SkillIds.PAIN_PURGE,
                line("skill.essence_ascendance.pain_purge.description.resolved" , pct(s -> s.vitality().damage().painPurge().queuePerHealing())),
                line("skill.essence_ascendance.pain_purge.description.resolved.1"));
        define(SkillIds.SOUL_WARD,
                line("skill.essence_ascendance.soul_ward.description.resolved", pct(s -> s.vitality().wards().soulWard().victimHealthFraction()),
                        pct(s -> s.vitality().wards().soulWard().capacityHealthFraction())),
                line("skill.essence_ascendance.soul_ward.description.resolved.1", sec(s -> s.vitality().wards().soulWard().durationTicks())));
        define(SkillIds.DEEP_WARD,
                line("skill.essence_ascendance.deep_ward.description.resolved", pct(s -> s.vitality().wards().deepWard().capacityBonusFraction())),
                line("skill.essence_ascendance.deep_ward.description.resolved.1", sec(s -> s.vitality().wards().deepWard().combatTimeoutTicks()),
                        sec(s -> s.vitality().wards().deepWard().decayTicks())));
        define(SkillIds.SHATTERING_WARD,
                line("skill.essence_ascendance.shattering_ward.description.resolved", n(s -> s.vitality().wards().shatteringWard().maximumTargets()),
                        n(s -> s.vitality().wards().shatteringWard().radius()), n(s -> s.vitality().wards().shatteringWard().knockback())),
                line("skill.essence_ascendance.shattering_ward.description.resolved.1", pct(s -> s.vitality().wards().shatteringWard().healingFractionPerSecond()),
                        sec(s -> s.vitality().wards().shatteringWard().regenerationTicks())),
                line("skill.essence_ascendance.shattering_ward.description.resolved.2"));
        define(SkillIds.RUNNING_MOMENTUM,
                line("skill.essence_ascendance.running_momentum.description.resolved", pct(s -> s.mobility().runningMomentum().maximumSpeedBonus()),
                        sec(s -> s.mobility().runningMomentum().buildTicks())),
                line("skill.essence_ascendance.running_momentum.description.resolved.1", sec(s -> s.mobility().runningMomentum().drainTicks()),
                        n(s -> s.mobility().runningMomentum().sharpTurnDegrees())),
                line("skill.essence_ascendance.running_momentum.description.resolved.2"));
        define(SkillIds.MOMENTUM_VAULT,
                line("skill.essence_ascendance.momentum_vault.description.resolved", pct(s -> s.mobility().momentumVault().minimumMomentum()),
                        n(s -> s.mobility().momentumVault().stepHeight())),
                line("skill.essence_ascendance.momentum_vault.description.resolved.1"));
        define(SkillIds.RUSH,
                line("skill.essence_ascendance.rush.description.resolved", sec(s -> s.mobility().rush().durationTicks())),
                line("skill.essence_ascendance.rush.description.resolved.1"));
        define(SkillIds.TERRAIN_FREEDOM,
                line("skill.essence_ascendance.terrain_freedom.description.resolved"),
                line("skill.essence_ascendance.terrain_freedom.description.resolved.1"));
        define(SkillIds.AQUATIC_BODY,
                line("skill.essence_ascendance.aquatic_body.description.resolved"),
                line("skill.essence_ascendance.aquatic_body.description.resolved.1"));
        define(SkillIds.WATER_WALKING,
                line("skill.essence_ascendance.water_walking.description.resolved"),
                line("skill.essence_ascendance.water_walking.description.resolved.1"));
        define(SkillIds.LAVABORN,
                line("skill.essence_ascendance.lavaborn.description.resolved"),
                line("skill.essence_ascendance.lavaborn.description.resolved.1"));
        define(SkillIds.ADRENALINE,
                line("skill.essence_ascendance.adrenaline.description.resolved" , pct(s -> s.vitality().damage().adrenaline().triggerHealthLossFraction()), sec(s -> s.vitality().damage().adrenaline().durationTicks())),
                line("skill.essence_ascendance.adrenaline.description.resolved.1" , pct(s -> s.vitality().damage().adrenaline().movementSpeedBonus()), pct(s -> s.vitality().damage().adrenaline().attackSpeedBonus()), pct(s -> s.vitality().damage().adrenaline().knockbackResistance())));
    }
    private SkillTooltipRegistry() { }
    public static List<Line> lines(ResourceLocation skill) { return DEFINITIONS.getOrDefault(skill, List.of()); }
    public static Set<ResourceLocation> supportedIds() { return Collections.unmodifiableSet(DEFINITIONS.keySet()); }
    public static List<ResourceLocation> missingDefinitions(Collection<ResourceLocation> implemented) {
        return implemented.stream().filter(id -> !DEFINITIONS.containsKey(id)).sorted().toList();
    }
    private static void define(ResourceLocation skill, Line... lines) {
        if (lines.length == 0 || DEFINITIONS.putIfAbsent(skill, List.of(lines)) != null)
            throw new IllegalArgumentException("Missing or duplicate skill description: " + skill);
    }
    private static Line line(String key, Value... values) { return new Line(key, List.of(values)); }
    private static Value n(ToDoubleFunction<SkillEffectBalanceSettings> value) { return new Value(value, 1); }
    private static Value pct(ToDoubleFunction<SkillEffectBalanceSettings> value) { return new Value(value, 100); }
    private static Value sec(ToDoubleFunction<SkillEffectBalanceSettings> value) { return new Value(value, 1.0 / 20); }
}

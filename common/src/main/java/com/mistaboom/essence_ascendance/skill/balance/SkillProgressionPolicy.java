package com.mistaboom.essence_ascendance.skill.balance;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.skill.ProgressionRequirements;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import java.util.*;

/** Explicit, dimensionless design policy. Neither catalog placement nor generated currency is an input.
 * Native outcome measurements stay in their own units and are normalized only against their declared
 * meaningful first state. These are utility budgets, NOT measured DPS, acquisition times or play rates. */
public final class SkillProgressionPolicy {
    public static final String CONTRACT = "mechanical-utility-1";
    private SkillProgressionPolicy() { }

    public record Decision(ProgressionBand band, double utility, double exposure, double nativeMagnitude,
                           Map<String, Double> nativeOutcomes, Map<CapabilityAxis, Double> axisUtility,
                           List<String> assumptions) { }

    public static Decision evaluate(SkillDefinition skill, SkillBalanceSemantics.Descriptor semantics,
                                    JsonObject intrinsicEffects) {
        var measured = new TreeMap<String, Double>();
        double relative = 0; int count = 0;
        for (var outcome : skill.progressionRequirements().outcomes()) {
            double value = outcome.measure(intrinsicEffects);
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Unmeasurable skill outcome: " + skill.id());
            measured.put(outcome.path(), value);
            if (outcome.first() > 0) { relative += Math.max(1, value / outcome.first()); count++; }
        }
        // Log response avoids treating unrelated native units as additive power.
        double magnitude = count == 0 ? 1 : 1 + Math.log1p(Math.max(0, relative / count - 1)) / Math.log(4);
        double exposure = Math.sqrt(Math.max(.05, semantics.expectedAvailability()));
        Map<CapabilityAxis, Double> axes = new EnumMap<>(CapabilityAxis.class);
        for (var contribution : semantics.contributions()) {
            // Area impact counts declared simultaneous targets, never radius cubed or invented kills/second.
            double scope = 1 + Math.log(contribution.targets()) / Math.log(4);
            double pressure = contribution.weight() * authority(contribution.axis()) * scope;
            axes.merge(contribution.axis(), pressure, Double::sum);
        }
        double utility = axes.values().stream().mapToDouble(Double::doubleValue).sum() * magnitude * exposure;
        if (!Double.isFinite(utility) || utility <= 0) throw new IllegalArgumentException("Invalid mechanical utility: " + skill.id());
        return new Decision(band(utility), utility, exposure, magnitude, Collections.unmodifiableMap(measured),
                Collections.unmodifiableMap(axes), List.of(
                "Axis weights and utility thresholds are explicit balancing policy, not observed acquisition facts.",
                "Trigger reliability, uptime, setup risk and resource burden are representative design scenarios from the semantic descriptor; no measured gameplay rate is asserted.",
                "Native magnitudes use implemented parameter measurements divided by meaningful first-state floors; different physical units are never summed.",
                "No supported substitute uses this utility policy. Missing, unsupported, failed and verified-absent evidence remain separate diagnostic states.",
                "External access may lower functional availability independently of equivalent strength; prerequisites, milestones and live conditions still apply."));
    }

    public static ProgressionBand band(double utility) {
        if (!Double.isFinite(utility) || utility < 0) throw new IllegalArgumentException("Invalid utility");
        return ProgressionBand.at(utility < .65 ? 0 : utility < 1.1 ? 1 : utility < 1.8 ? 2 : utility < 2.8 ? 3 : 4);
    }

    /** Cost of persistent agency, relative to an ordinary conditional single-target improvement.
     * Exhaustive switch forces new axes to obtain an explicit policy instead of a catalog fallback. */
    public static double authority(CapabilityAxis axis) {
        return switch (axis) {
            case FLIGHT, DAMAGE_IMMUNITY, INDESTRUCTIBILITY, LOCAL_TIME_ACCELERATION -> 4;
            case TELEPORTATION, GLIDING, ITEM_LOSS_PREVENTION, AUTOMATED_EXTRACTION, AUTOMATED_FARMING,
                 AUTOMATED_FISHING, PASSIVE_GENERATION, SPAWN_SUPPRESSION, MOB_REPULSION,
                 MACHINE_ACCELERATION, BLOCK_ENTITY_ACCELERATION, ENTITY_ACCELERATION -> 2.5;
            case BURST_SURVIVAL, STATUS_RESISTANCE, AUTOMATION_INTERACTION, AREA_MINING, VEIN_MINING,
                 CROP_ACCELERATION, TREE_ACCELERATION, ANIMAL_ACCELERATION, HAZARD_SUPPRESSION -> 1.8;
            case AREA_DAMAGE, ARMOR_PENETRATION, SHIELD_INTERACTION, CROWD_CONTROL, AVOIDANCE,
                 REFLECTION, HEALING, REPAIR, CONVERSION, TOOL_VERSATILITY, INVENTORY,
                 VERTICAL_MOVEMENT, ABILITIES_FLYING_SPEED, MOB_FARMING -> 1.25;
            case SUSTAINED_DAMAGE, BURST_DAMAGE, ATTACK_RATE, RANGE, DELIVERY_RELIABILITY, DAMAGE_OVER_TIME,
                 EFFECTIVE_HEALTH, ARMOR, TOUGHNESS, DAMAGE_REDUCTION, BLOCKING, RECOVERY, REGENERATION,
                 GROUND_SPEED, JUMP, FALL_CONTROL, MINING_SPEED, HARVEST_LEVEL, CROP_YIELD, DROP_YIELD,
                 DURABILITY, REACH, ENCHANTING_EFFICIENCY, ANVIL_EFFICIENCY, THROUGHPUT, EXPERIENCE,
                 INFORMATION, CONVENIENCE, RESOURCE_CONSUMPTION, SUSTAINED_SURVIVAL, MELEE_DAMAGE,
                 RANGED_DAMAGE, MAGIC_DAMAGE, PROJECTILE_SPEED, CRITICAL_DAMAGE, SHIELD_CAPACITY,
                 MAX_HEALTH, KNOCKBACK_RESISTANCE, DURABILITY_REDUCTION, STEP_HEIGHT, HARVEST_SPEED,
                 FISHING_PRODUCTIVITY, FISHING_TIME_REDUCTION -> .75;
        };
    }

    /** Relative actual rank benefit; first-state floor excess does not feed any power allocation. */
    public static double rankUtility(SkillDefinition skill, JsonObject effects, JsonObject firstEffects) {
        double total = 0; int count = 0;
        for (var outcome : skill.progressionRequirements().outcomes()) {
            double base = Math.max(outcome.first(), outcome.measure(firstEffects));
            if (base <= 0) continue;
            total += Math.max(1, outcome.measure(effects) / base); count++;
        }
        return count == 0 ? 1 : total / count;
    }

    public static long price(long tierBudget, double utility, double categoryFactor,
                             double alternativeFactor, double rankBenefit, long previousPrice) {
        double fraction = Math.clamp(.045 * Math.sqrt(utility), .02, .25);
        double raw = Math.ceil(tierBudget * fraction * categoryFactor * alternativeFactor * rankBenefit);
        if (!Double.isFinite(raw) || raw >= Long.MAX_VALUE || raw <= 0) throw new ArithmeticException("Invalid procedural skill price");
        return Math.max(Math.max(1, previousPrice), (long) raw);
    }

    /** Explicit author controls apply to both provisional and final costs, never to access evidence. */
    public static double configuredPriceFactor(SkillDefinition skill, BalanceSettings settings, PackEvidence evidence) {
        var axes = SkillBalanceSemantics.require(skill.id()).weights(); double factor = 1;
        if (axes.containsKey(CapabilityAxis.FLIGHT)) {
            if (settings.flightPolicy() == BalanceSettings.FlightPolicy.RESTRICT) factor *= 2;
            else if (settings.flightPolicy() == BalanceSettings.FlightPolicy.MATCH_PACK)
                factor /= Math.sqrt(1 + Math.max(0, evidence.reference(ProgressionBand.EARLY, CapabilityAxis.FLIGHT, 0)));
        }
        if (axes.containsKey(CapabilityAxis.AREA_MINING) || axes.containsKey(CapabilityAxis.VEIN_MINING)) {
            if (settings.miningPolicy() == BalanceSettings.MiningPolicy.RESTRICT) factor *= 2;
            else if (settings.miningPolicy() == BalanceSettings.MiningPolicy.MATCH_PACK)
                factor /= Math.sqrt(Math.max(1, Math.max(evidence.reference(ProgressionBand.MID, CapabilityAxis.AREA_MINING, 1),
                        evidence.reference(ProgressionBand.MID, CapabilityAxis.VEIN_MINING, 1))));
        }
        return Math.max(.1, factor);
    }
}

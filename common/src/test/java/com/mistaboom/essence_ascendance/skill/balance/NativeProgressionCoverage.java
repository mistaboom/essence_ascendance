package com.mistaboom.essence_ascendance.skill.balance;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.runtime.*;
import com.mistaboom.essence_ascendance.equipment.ShieldMath;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;

/** Exhaustive saved-state inventory. Declared outcome measurements are not mislabeled as live gameplay tests. */
final class NativeProgressionCoverage {
    private static final Gson JSON = com.mistaboom.essence_ascendance.balance.generated.BalanceDocument.GSON;
    private NativeProgressionCoverage() { }

    static void append(RuntimeBalanceDefinition runtime, JsonObject root) {
        var skillRows = new JsonArray();
        var inactiveEffects = JSON.toJsonTree(SkillRankEffectScaling.applyResolved(runtime.config().skillEffects(),
                Map.of(), runtime.skillCurves())).getAsJsonObject();
        SkillBalanceRuntime.withCurves(runtime.skillCurves(), () -> {
            for (var skill : SkillRegistry.values()) {
                long cumulative = 0;
                JsonObject previous = null;
                var requirements = skill.progressionRequirements();
                for (var rank : runtime.skillCurves().get(skill.id().toString()).ranks()) {
                    cumulative = Math.addExact(cumulative, rank.cost());
                    var effects = JSON.toJsonTree(SkillRankEffectScaling.applyResolved(runtime.config().skillEffects(),
                            Map.of(skill.id(), rank.rank()), runtime.skillCurves())).getAsJsonObject();
                    var row = new JsonObject();
                    row.addProperty("skill", skill.id().toString());
                    row.addProperty("essence", skill.essenceId().toString());
                    row.addProperty("rank", rank.rank());
                    row.addProperty("requiredTier", skill.requiredTierId(rank.rank()).toString());
                    row.addProperty("cost", rank.cost());
                    row.addProperty("cumulativeCost", cumulative);
                    row.addProperty("maximumRank", skill.maximumRank());
                    row.addProperty("implementationRegistered", SkillEffectRegistry.isImplemented(skill.id()));
                    row.addProperty("activationPolicy", skill.activationPolicy().toString());
                    row.add("choiceGroup", JSON.toJsonTree(skill.choiceGroup()));
                    row.add("replacementTarget", JSON.toJsonTree(skill.replacementTarget()));
                    row.add("prerequisites", JSON.toJsonTree(skill.prerequisiteRanks(rank.rank())));
                    row.add("requirements", JSON.toJsonTree(skill.requirements(rank.rank())));
                    row.addProperty("firstStateContract", requirements.firstStateFloor());
                    row.addProperty("laterStateContract", requirements.additionalImprovementFloor());
                    row.addProperty("mechanicalConstraints", requirements.mechanicalConstraints());
                    row.addProperty("measurementEvidence", "Actual applyResolved parameter consumer; declared native outcome context. No world, trigger frequency, uptime, mitigation or equipment simulation.");
                    var outcomes = new JsonArray();
                    boolean any = false, all = true, regression = false, firstFloors = true;
                    for (var outcome : requirements.outcomes()) {
                        var measurement = new JsonObject();
                        double value = outcome.measure(effects);
                        measurement.addProperty("parameter", outcome.path());
                        measurement.addProperty("rawParameter", ProgressionRequirements.read(effects, outcome.path()));
                        measurement.addProperty("unpurchasedConfiguredParameter", ProgressionRequirements.read(inactiveEffects, outcome.path()));
                        measurement.addProperty("unpurchasedEffectActive", false);
                        measurement.addProperty("parameterChangeFromInactiveConfiguration", ProgressionRequirements.read(effects, outcome.path())
                                - ProgressionRequirements.read(inactiveEffects, outcome.path()));
                        measurement.addProperty("measurement", value);
                        measurement.addProperty("firstFloor", outcome.first());
                        measurement.addProperty("laterFloor", outcome.later());
                        measurement.addProperty("parameterMinimum", outcome.minimum());
                        measurement.addProperty("parameterMaximum", outcome.maximum());
                        measurement.addProperty("parameterQuantum", outcome.quantum());
                        measurement.addProperty("meetsFirstFloor", value + 1e-9 >= outcome.first());
                        firstFloors &= value + 1e-9 >= outcome.first();
                        if (previous == null) {
                            measurement.add("previousMeasurement", JsonNull.INSTANCE);
                            measurement.add("increment", JsonNull.INSTANCE);
                            measurement.addProperty("comparison", "Unpurchased skill has no active effect; first-state capability/parameter floor, not a fictitious zero-delay baseline");
                        } else {
                            double before = outcome.measure(previous), increment = value - before;
                            measurement.addProperty("previousMeasurement", before);
                            measurement.addProperty("increment", increment);
                            measurement.addProperty("comparison", "Previous published purchased rank");
                            regression |= increment < -1e-9;
                            if (outcome.later() > 0) {
                                boolean meets = increment + 1e-9 >= outcome.later();
                                measurement.addProperty("meetsLaterFloor", meets);
                                any |= meets; all &= meets;
                            }
                        }
                        outcomes.add(measurement);
                    }
                    row.add("outcomes", outcomes);
                    row.add("nativeOutcomeProbes", skillProbes(skill, previous, effects));
                    row.add("nativeOutcomeFloorsSatisfied", nativeFloors(row.getAsJsonArray("nativeOutcomeProbes"), false));
                    row.addProperty("declaredOutcomeFloorsSatisfied", firstFloors && !regression
                            && (previous == null || any && (requirements.alternativeImprovements() || all)));
                    row.addProperty("coverageKind", outcomes.isEmpty() ? "binary_capability_requires_native_hook_fixture" : "declared_native_outcome_parameters");
                    skillRows.add(row);
                    previous = effects;
                }
            }
            return null;
        });
        root.add("skillRankCoverage", skillRows);

        var bonusRows = new JsonArray();
        int active = 0, unavailable = 0;
        for (var stat : EssenceStatRegistry.values()) {
            var track = runtime.config().balanceProfile().bonusTrack(stat.id());
            if (track.applicability() == BonusTrackDefinition.Applicability.UNAVAILABLE) unavailable++;
            double previous = 0;
            boolean started = false;
            for (var point : track.checkpoints()) {
                var tier = AscendanceTierRegistry.get(point.tierId()).orElseThrow();
                double fraction = StatScalingService.realizedProgressionForInvestment(stat, point.cumulativeCap(), tier,
                        runtime.config().balanceProfile());
                double value = fraction * track.maximumEffect();
                var row = new JsonObject();
                row.addProperty("bonus", stat.id().toString());
                row.addProperty("essence", stat.essenceType().id().toString());
                row.addProperty("tier", point.tierId().toString());
                row.addProperty("applicability", track.applicability().toString());
                row.addProperty("available", point.available());
                row.addProperty("purchasable", point.purchasable());
                row.addProperty("startTier", track.startTier().toString());
                row.addProperty("completionTier", track.completionTier().toString());
                row.addProperty("segmentCost", point.segmentCost());
                row.addProperty("cumulativeCost", point.cumulativeCap());
                row.addProperty("unit", stat.unit().toString());
                row.addProperty("completedEffect", value);
                row.addProperty("previousCompletedEffect", previous);
                row.addProperty("increment", value - previous);
                row.addProperty("maximumEffect", track.maximumEffect());
                row.add("nativeMaximumEffect", JSON.toJsonTree(track.inputs().get("native_maximum_effect")));
                row.addProperty("requiredIncrement", started ? track.progressionRequirements().tierImprovementFloor()
                        : track.progressionRequirements().firstStateFloor());
                row.addProperty("declaredOutcomeFloorSatisfied", !point.purchasable()
                        || value - previous + 1e-9 >= row.get("requiredIncrement").getAsDouble());
                row.add("compatibility", JSON.toJsonTree(track.compatibilityRequirements()));
                row.addProperty("confidence", track.confidence());
                row.addProperty("source", track.source());
                row.addProperty("quantization", track.progressionRequirements().quantization());
                row.addProperty("mechanicalCap", track.progressionRequirements().mechanicalCap());
                row.add("nativeOutcomeProbes", probes(runtime, stat, point.tierId(), previous, value, track));
                row.add("nativeOutcomeFloorsSatisfied", point.purchasable()
                        ? nativeFloors(row.getAsJsonArray("nativeOutcomeProbes"), stat.id().getPath().equals("guard_readiness")) : JsonNull.INSTANCE);
                row.addProperty("measurementEvidence", "Actual shared investment-to-effect consumer. Native probes call shared pure mechanics or attribute sanitizers on the isolated audit classpath in named contexts; they do not execute the target pack's loader/world. Step uses its saved native baseline. Other effects remain parameter evidence; no live trigger rate is asserted.");
                bonusRows.add(row);
                if (point.purchasable()) { active++; started = true; previous = value; }
            }
        }
        root.add("bonusTierCoverage", bonusRows);
        var summary = new JsonObject();
        summary.addProperty("registeredSkills", SkillRegistry.size());
        summary.addProperty("purchasedSkillRanks", skillRows.size());
        summary.addProperty("registeredBonuses", EssenceStatRegistry.values().size());
        summary.addProperty("allBonusTierRows", bonusRows.size());
        summary.addProperty("activeBonusEndpoints", active);
        summary.addProperty("unavailableBonuses", unavailable);
        summary.addProperty("skillRankDeclaredFloorFailures", failures(skillRows, "declaredOutcomeFloorsSatisfied"));
        summary.addProperty("skillRankNativeProbeFailures", failures(skillRows, "nativeOutcomeFloorsSatisfied"));
        summary.addProperty("bonusEndpointDeclaredFloorFailures", failures(bonusRows, "declaredOutcomeFloorSatisfied"));
        summary.addProperty("bonusEndpointNativeProbeFailures", failures(bonusRows, "nativeOutcomeFloorsSatisfied"));
        summary.addProperty("skillRanksWithNativeProbes", measured(skillRows));
        summary.addProperty("bonusEndpointsWithNativeProbes", measured(bonusRows));
        summary.addProperty("savedNativeOutcomeContract", runtime.composition().getOrDefault("native_outcome_contract", 1.0));
        summary.addProperty("auditNativeOutcomeContract", ProgressionRequirements.NATIVE_OUTCOME_CONTRACT);
        summary.addProperty("coverageContract", "Registry exhaustive, every published rank and every tier row. Parameter minimum checks are separate from named native probes and separate from live-loader fixture coverage.");
        root.add("coverageSummary", summary);
    }

    private static JsonArray skillProbes(SkillDefinition skill, JsonObject previous, JsonObject effects) {
        var probes = new JsonArray();
        if (skill.id().equals(SkillIds.LIFE_STEAL)) for (int hits : new int[]{1, 2, 5, 20}) {
            probe(probes, "20 actual primary-damage HP across " + hits + " equal accepted chained hits; sufficient missing health; healing multiplier 1",
                    "health points", previous == null ? 0 : lifeStealHealing(previous, hits), lifeStealHealing(effects, hits));
            probes.get(probes.size() - 1).getAsJsonObject().addProperty("requiredIncrement", previous == null ? 1 : .5);
        }
        return probes;
    }

    private static double lifeStealHealing(JsonObject effects, int hits) {
        String path = "vitality/lifeSteal/";
        double base = ProgressionRequirements.read(effects, path + "baseHealingFraction");
        double perHit = ProgressionRequirements.read(effects, path + "perHitHealingFraction");
        int cap = (int) ProgressionRequirements.read(effects, path + "maxChainHits");
        double healing = 0;
        for (int hit = 1; hit <= hits; hit++) healing += com.mistaboom.essence_ascendance.vitality.RecoveryMath.healing(
                20.0 / hits, 20, base, perHit, hit, cap);
        return healing;
    }

    private static JsonArray probes(RuntimeBalanceDefinition runtime, StatDefinition stat, net.minecraft.resources.ResourceLocation tier, double previous,
                                    double value, BonusTrackDefinition track) {
        var probes = new JsonArray();
        if (NativeDamageBonusFloor.supported(stat)) {
            for (var context : NativeDamageBonusFloor.contexts(runtime, stat)) if (context.tier().equals(tier)) {
                probe(probes, "Same active-tier " + context.profile() + "; rounded physical damage=" + context.baseDamage()
                                + "; held applicability=" + context.applicability()
                                + "; fully charged unmitigated base damage; lower-tier equipment caps activation at its own tier",
                        "native base damage", context.damage(previous), context.damage(value));
                probes.get(probes.size() - 1).getAsJsonObject().addProperty("requiredIncrement", 1);
            }
        }
        if (NativeCooldownBonusFloor.supported(stat) && AscendanceTierRegistry.get(tier).orElseThrow().grantsPower()) {
            double rate = NativeCooldownBonusFloor.rate(runtime, stat, AscendanceTierRegistry.get(tier).orElseThrow());
            probe(probes, "Same active-tier supported weapon; rounded physical rate=" + rate
                            + "; no skill speed modifier; EquipmentWeaponService.rateToTicks; lower-tier gear caps activation at its own tier",
                    "whole cooldown ticks saved", NativeCooldownBonusFloor.ticks(rate, 0) - NativeCooldownBonusFloor.ticks(rate, previous),
                    NativeCooldownBonusFloor.ticks(rate, 0) - NativeCooldownBonusFloor.ticks(rate, value));
            probes.get(probes.size() - 1).getAsJsonObject().addProperty("requiredIncrement", 1);
        }
        if (stat.id().equals(EssenceStats.MELEE_ATTACK_SPEED.id()) && AscendanceTierRegistry.get(tier).orElseThrow().grantsPower()) {
            for (var context : NativeMeleeRateBonusFloor.contexts(runtime, AscendanceTierRegistry.get(tier).orElseThrow())) {
                probe(probes, "Same active-tier " + context.profile() + "; physical attack rate=" + context.baseRate()
                                + "; held applicability=" + context.applicability()
                                + "; native Player.attack float partial-charge formula at ticker0; no assumed frequency/DPS; no other speed modifier",
                        "partial-charge base-damage multiplier", context.outcome(previous), context.outcome(value));
                var row = probes.get(probes.size() - 1).getAsJsonObject();
                row.addProperty("nativeAttackRateBefore", context.rate(previous));
                row.addProperty("nativeAttackRateAfter", context.rate(value));
                row.addProperty("mechanicalMaximumPercentage", context.maximumPercent());
            }
        }
        switch (stat.id().getPath()) {
            case "attack_knockback" -> attributeProbe(probes, track, previous, value, Attributes.ATTACK_KNOCKBACK, false, .01);
            case "knockback_resistance" -> attributeProbe(probes, track, previous, value, Attributes.KNOCKBACK_RESISTANCE, false, .01);
            case "reach" -> {
                attributeProbe(probes, track, previous, value, Attributes.BLOCK_INTERACTION_RANGE, false, 1);
                attributeProbe(probes, track, previous, value, Attributes.ENTITY_INTERACTION_RANGE, false, 1);
            }
            case "luck" -> attributeProbe(probes, track, previous, value, Attributes.LUCK, false, 1);
            case "movement_speed" -> attributeProbe(probes, track, previous, value, Attributes.MOVEMENT_SPEED, true, .01);
            case "mining_speed" -> attributeProbe(probes, track, previous, value, Attributes.BLOCK_BREAK_SPEED, true, .01);
            case "jump_height" -> attributeProbe(probes, track, previous, value, Attributes.JUMP_STRENGTH, true, .01);
            case "guard_readiness" -> {
                int minimum = runtime.config().shieldBalance().minimumDisableTicks();
                probe(probes, "Vanilla shield disable; full compatible shield applicability", "ticks saved",
                        100 - ShieldMath.disableTicks(100, previous, minimum), 100 - ShieldMath.disableTicks(100, value, minimum));
                probe(probes, "Vanilla shield raise; full compatible shield applicability", "ticks saved",
                        5 - ShieldMath.raiseDelayTicks(5, previous, 1), 5 - ShieldMath.raiseDelayTicks(5, value, 1));
            }
            case "guarded_movement" -> probe(probes, "Native 0.2 blocking multiplier; no Guarded Advance", "movement multiplier",
                    ShieldMath.movementMultiplier(previous), ShieldMath.movementMultiplier(value));
            case "swim_speed" -> {
                double base = NativeBonusMechanics.baseline(Attributes.WATER_MOVEMENT_EFFICIENCY);
                probe(probes, "Native player water-movement attribute; full equipment applicability; no other modifier", "attribute value",
                        Attributes.WATER_MOVEMENT_EFFICIENCY.value().sanitizeValue(base + previous / 100),
                        Attributes.WATER_MOVEMENT_EFFICIENCY.value().sanitizeValue(base + value / 100));
            }
            case "sneak_speed" -> {
                double base = NativeBonusMechanics.baseline(Attributes.SNEAKING_SPEED);
                probe(probes, "Native player sneaking-speed attribute; full equipment applicability; no other modifier", "attribute value",
                        Attributes.SNEAKING_SPEED.value().sanitizeValue(base * (1 + previous / 100)),
                        Attributes.SNEAKING_SPEED.value().sanitizeValue(base * (1 + value / 100)));
            }
            case "max_health" -> {
                double base = NativeBonusMechanics.baseline(Attributes.MAX_HEALTH);
                probe(probes, "Native max-health attribute; full equipment applicability; one heart is two HP", "health points",
                        Attributes.MAX_HEALTH.value().sanitizeValue(base + previous * 2),
                        Attributes.MAX_HEALTH.value().sanitizeValue(base + value * 2));
                probes.get(probes.size() - 1).getAsJsonObject().addProperty("requiredIncrement", 2 * (previous == 0
                        ? track.progressionRequirements().firstStateFloor() : track.progressionRequirements().tierImprovementFloor()));
            }
            case "step_height" -> {
                double base = track.inputs().getOrDefault("native_base_step_height", NativeBonusMechanics.baseline(Attributes.STEP_HEIGHT));
                probe(probes, "Saved native step baseline; full equipment applicability; traversal still requires collision context", "blocks",
                        Attributes.STEP_HEIGHT.value().sanitizeValue(base + previous), Attributes.STEP_HEIGHT.value().sanitizeValue(base + value));
            }
            default -> { }
        }
        return probes;
    }

    private static void attributeProbe(JsonArray probes, BonusTrackDefinition track, double previous, double value,
                                       net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                       boolean multiplied, double scale) {
        double base = NativeBonusMechanics.baseline(attribute);
        double nativeScale = scale * (multiplied ? base : 1);
        probe(probes, attribute.unwrapKey().orElseThrow().location() + "; full supported equipment applicability; no external modifier; "
                        + (multiplied ? "ADD_MULTIPLIED_BASE" : "ADD_VALUE")
                        + "; native attribute measurement, not a universal world/loadout outcome",
                "native attribute units", attribute.value().sanitizeValue(base + previous * nativeScale),
                attribute.value().sanitizeValue(base + value * nativeScale));
        probes.get(probes.size() - 1).getAsJsonObject().addProperty("requiredIncrement", nativeScale * (previous == 0
                ? track.progressionRequirements().firstStateFloor() : track.progressionRequirements().tierImprovementFloor()));
    }

    private static long failures(JsonArray rows, String field) {
        return rows.asList().stream().map(JsonElement::getAsJsonObject).map(row -> row.get(field))
                .filter(value -> value != null && !value.isJsonNull() && !value.getAsBoolean()).count();
    }

    private static long measured(JsonArray rows) {
        return rows.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(row -> !row.get("nativeOutcomeFloorsSatisfied").isJsonNull()).count();
    }

    private static JsonElement nativeFloors(JsonArray probes, boolean alternative) {
        if (probes.isEmpty()) return JsonNull.INSTANCE;
        boolean all = true, any = false;
        for (var element : probes) {
            var row = element.getAsJsonObject();
            double increment = row.get("increment").getAsDouble();
            boolean passes = row.has("requiredIncrement") ? increment + 1e-9 >= row.get("requiredIncrement").getAsDouble() : increment > 1e-9;
            row.addProperty("floorSatisfied", passes);
            all &= passes; any |= passes;
        }
        return new JsonPrimitive(alternative ? any : all);
    }

    private static void probe(JsonArray probes, String context, String unit, double previous, double value) {
        var row = new JsonObject();
        row.addProperty("context", context); row.addProperty("unit", unit);
        row.addProperty("previous", previous); row.addProperty("completed", value);
        row.addProperty("increment", value - previous);
        probes.add(row);
    }
}

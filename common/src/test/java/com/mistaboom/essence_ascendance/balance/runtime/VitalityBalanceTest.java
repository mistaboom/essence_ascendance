package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.balance.engine.BuildComposition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.*;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import java.util.*;
import java.util.function.Consumer;

/** Strict generated schema, independent native-unit consumers, meaningful future ranks and no unrelated tuning drift. */
public final class VitalityBalanceTest {
    private static int checks;
    private static final List<ResourceLocation> SKILLS = List.of(SkillIds.RISING_RECOVERY, SkillIds.LIFE_STEAL,
            SkillIds.FEAST_REFLEX, SkillIds.INNER_SUSTENANCE);
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread,error) -> error.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init(); MilestoneProviders.init();
        Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        var runtime = RuntimeBalanceDefinition.bootstrap();
        check(runtime.toJson().equals(RuntimeBalanceDefinition.bootstrap().toJson()), "Deterministic generation");
        check(runtime.toJson().equals(RuntimeBalanceDefinition.fromJson(runtime.toJson()).toJson()), "Strict round trip");
        reject(runtime, j -> j.getAsJsonObject("effects").remove("vitality"));
        reject(runtime, j -> vitality(j).addProperty("inventedPower", 1));
        for (var section : vitality(runtime.toJson()).entrySet()) {
            reject(runtime, j -> vitality(j).remove(section.getKey()));
            for (String field : section.getValue().getAsJsonObject().keySet()) {
                reject(runtime, j -> vitality(j).getAsJsonObject(section.getKey()).remove(field));
                for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
                    reject(runtime, j -> vitality(j).getAsJsonObject(section.getKey()).addProperty(field, invalid));
            }
        }
        reject(runtime, j -> vitality(j).getAsJsonObject("lifeSteal").addProperty("perHitHealingFraction", 1));
        reject(runtime, j -> vitality(j).getAsJsonObject("risingRecovery").addProperty("recoveryCurveExponent", .9));
        reject(runtime, j -> vitality(j).getAsJsonObject("feastReflex").addProperty("useDurationMultiplier", 0));
        reject(runtime, j -> vitality(j).getAsJsonObject("innerSustenance").addProperty("hungerRecoveryIntervalTicks", 0));
        exactPointers(runtime);
        ranks(runtime);
        numeric(runtime);
        projections(runtime);
        metabolicBudgets();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "VitalityBalanceTest: " + checks + " schema, exact pointer, native-unit, projection, rank and preservation checks PASS");
    }
    private static void metabolicBudgets() {
        RuntimeReferencePolicy.withBootstrapReferences(() -> {
            double previousFood = -1, previousHealing = -1;
            for (double power : new double[]{.1, .2, .5, 1, 2, 4}) {
                var settings = BalanceSettings.parse("schema_version=1\n[power]\noverall=" + power, "metabolic-budget-test");
                var generated = VitalityDamageBalanceGenerator.generate(settings);
                var pair = generated.metabolicConversion();
                check(pair.foodPointsPerOverflowHealth() >= previousFood && pair.healthPerNutrition() >= previousHealing,
                        "Lower budgets must not amplify either conversion direction");
                previousFood = pair.foodPointsPerOverflowHealth(); previousHealing = pair.healthPerNutrition();
                var json = new com.google.gson.Gson().toJsonTree(Map.of("vitality", Map.of("damage", generated))).getAsJsonObject();
                var before = json.deepCopy();
                for (var outcome : ProgressionRequirements.skill(SkillIds.METABOLIC_CONVERSION).outcomes()) {
                    outcome.quantize(json); outcome.grantFirst(json);
                    check(outcome.measure(json) + 1e-9 >= outcome.first(), "Both conversion directions keep tangible first benefits");
                    check(ProgressionRequirements.read(json, outcome.path()) + 1e-9 >= ProgressionRequirements.read(before, outcome.path()) - outcome.quantum(),
                            "Floor publication cannot compensate by reducing the paired allocation");
                }
                var finalDamage = new com.google.gson.Gson().fromJson(json.getAsJsonObject("vitality").getAsJsonObject("damage"),
                        com.mistaboom.essence_ascendance.config.VitalityDamageBalanceSettings.class);
                finalDamage.validate();
                check(finalDamage.metabolicConversion().foodPointsPerOverflowHealth()
                        * finalDamage.metabolicConversion().healthPerNutrition() < 1, "Useful pair remains strictly lossy");
                json.getAsJsonObject("vitality").getAsJsonObject("damage").remove("metabolicConversion");
                before.getAsJsonObject("vitality").getAsJsonObject("damage").remove("metabolicConversion");
                check(json.equals(before), "Conversion floors cannot change Ward, Adrenaline or other damage parameters");
            }
            return null;
        });
    }
    private static void exactPointers(RuntimeBalanceDefinition baseline) {
        var overrides = BalanceOverrides.parse("[exact]\n"
                + "\"/runtime/effects/vitality/risingRecovery/recoveryCurveExponent\" = 3.0\n"
                + "\"/runtime/effects/vitality/lifeSteal/chainTimeoutTicks\" = 80\n"
                + "\"/runtime/effects/vitality/feastReflex/useDurationMultiplier\" = 0.2\n"
                + "\"/runtime/effects/vitality/innerSustenance/hungerRecoveryIntervalTicks\" = 180\n", "vitality-test.toml");
        var changed = RuntimeReferencePolicy.withBootstrapReferences(() -> RuntimeBalanceDefinition.generate(
                RuntimeReferencePolicy.bootstrapEvidence(), BalanceSettings.defaults(), overrides));
        var v = changed.config().skillEffects().vitality();
        near(v.risingRecovery().recoveryCurveExponent(), 3, "Recovery pointer");
        check(v.lifeSteal().chainTimeoutTicks() == 80, "Chain pointer");
        near(v.feastReflex().useDurationMultiplier(), .2, "Feast pointer");
        check(v.innerSustenance().hungerRecoveryIntervalTicks() == 180, "Floor-compatible hunger pointer");
        check(!baseline.config().balanceProfile().id().equals(changed.config().balanceProfile().id()), "Content identity includes new tuning");
        var oldJson = baseline.toJson(); var newJson = changed.toJson();
        for (String key : List.of("equipment", "statMaxBonuses", "infuser", "shield", "pylons", "crucible", "attunement", "composition"))
            check(oldJson.get(key).equals(newJson.get(key)), "Vitality override changed unrelated " + key);
        for (String key : oldJson.getAsJsonObject("effects").keySet()) if (!key.equals("vitality"))
            check(oldJson.getAsJsonObject("effects").get(key).equals(newJson.getAsJsonObject("effects").get(key)), "Vitality override changed prior skill " + key);
    }
    private static void ranks(RuntimeBalanceDefinition runtime) {
        SkillBalanceRuntime.install(runtime.skillCurves());
        var base = runtime.config().skillEffects();
        for (var id : SKILLS) {
            var curve = runtime.skillCurves().get(id.toString());
            check(curve.maximumRank() == curve.ranks().size(), "Only meaningful purchasable ranks are published");
            check(SkillRankEffectScaling.supports(id), "Missing real typed rank consumer " + id);
            check(SkillRankEffectScaling.apply(base, Map.of(id, 1)).equals(base), "Rank one must be exact generated value");
            var ranked = SkillRankEffectScaling.apply(base, Map.of(id, curve.maximumRank())); ranked.validate();
            check(curve.maximumRank() == 1 || !ranked.vitality().equals(base.vitality()), "Additional ranks need a real numeric effect " + id);
            check(ranked.frenzy().equals(base.frenzy()) && ranked.projectiles().equals(base.projectiles())
                    && ranked.guard().equals(base.guard()) && ranked.posture().equals(base.posture())
                    && ranked.status().equals(base.status()), "Vitality rank changed prior effects");
            var original = base.vitality(); var result = ranked.vitality();
            check(result.lifeSteal().maxChainHits() == original.lifeSteal().maxChainHits()
                    && result.lifeSteal().chainTimeoutTicks() == original.lifeSteal().chainTimeoutTicks(), "Ranks altered chain identity/reset policy");
            check(result.innerSustenance().combatTimeoutTicks() == original.innerSustenance().combatTimeoutTicks(), "Ranks altered combat authority");
        }
        SkillBalanceRuntime.clear();
    }
    private static void numeric(RuntimeBalanceDefinition runtime) {
        var effects = runtime.config().skillEffects();
        var v = effects.vitality();
        double recovery = RuntimeBuildScenarios.vitalityHealing(effects, Set.of(SkillIds.RISING_RECOVERY), 5, 2, "melee_shield", .5);
        near(recovery, 2 * v.risingRecovery().maxSpeedBonus() * Math.pow(.5, v.risingRecovery().recoveryCurveExponent()), "Native saturated capacity at missing-health curve");
        near(RuntimeBuildScenarios.vitalityHealing(effects, Set.of(SkillIds.RISING_RECOVERY), 5, 2, "melee_shield", 1), 0, "No missing-health recovery acceleration");
        double fraction = v.lifeSteal().baseHealingFraction() + (v.lifeSteal().maxChainHits() - 1) * v.lifeSteal().perHitHealingFraction();
        near(RuntimeBuildScenarios.vitalityHealing(effects, Set.of(SkillIds.LIFE_STEAL), 5, 2, "melee_shield", .5), 10 * fraction, "Accepted direct primary damage and native cadence");
        near(RuntimeBuildScenarios.vitalityHealing(effects, Set.of(SkillIds.LIFE_STEAL), 5, .1, "ranged", .5), .5 * v.lifeSteal().baseHealingFraction(), "Inactivity prevents assumed chain growth");
        double timeoutRate = 20.0 / v.lifeSteal().chainTimeoutTicks();
        near(RuntimeBuildScenarios.vitalityHealing(effects, Set.of(SkillIds.LIFE_STEAL), 5, timeoutRate, "ranged", .5),
                5 * timeoutRate * v.lifeSteal().baseHealingFraction(), "Exact timeout equality resets before the next accepted hit");
        double nearTimeoutFraction = v.lifeSteal().baseHealingFraction()
                + (v.lifeSteal().maxChainHits() - 1) * v.lifeSteal().perHitHealingFraction()
                * ((timeoutRate * 1.01 * v.lifeSteal().chainTimeoutTicks() / 20.0 - 1)
                / (v.lifeSteal().maxChainHits() - 1));
        near(RuntimeBuildScenarios.vitalityHealing(effects, Set.of(SkillIds.LIFE_STEAL), 5, timeoutRate * 1.01, "ranged", .5),
                5 * timeoutRate * 1.01 * nearTimeoutFraction, "Strictly earlier hit retains proportional chain growth");
        var item = new BuildComposition.Equipment(5, 2, 10, 2, 20, 0);
        var none = BuildComposition.Modifier.none();
        var ls = RuntimeBuildScenarios.combat(effects, Set.of(SkillIds.LIFE_STEAL), "ranged", item, none, 10, 10, 1);
        var proc = RuntimeBuildScenarios.combat(effects, Set.of(SkillIds.LIFE_STEAL,
                SkillIds.EXPLOSIVE_PAYLOAD, SkillIds.CHAIN_STRIKE), "ranged", item, none, 10, 10, 1);
        near(proc.healingPerSecond(), ls.healingPerSecond(), "Proc and secondary area damage cannot inflate Life Steal");
        for (var path : List.of(SkillIds.RICOCHET, SkillIds.PIERCING_PROJECTILE)) {
            var p = effects.projectiles();
            boolean ricochet = path.equals(SkillIds.RICOCHET);
            double extra = 0;
            int contacts = Math.min(p.maximumImpacts() - 1, ricochet ? p.ricochets() : p.penetrations());
            for (int contact = 1; contact <= contacts; contact++) extra += 5 * 2
                    * Math.pow(ricochet ? p.ricochetDamageMultiplier() : p.piercingDamageMultiplier(), contact) * v.lifeSteal().baseHealingFraction();
            var continued = RuntimeBuildScenarios.combat(effects, Set.of(SkillIds.LIFE_STEAL, path), "ranged", item, none, 10, 10, 1);
            near(continued.healingPerSecond(), ls.healingPerSecond() + extra, "Direct native continuation heals only base fraction after target change: " + path);
            var withPayload = RuntimeBuildScenarios.combat(effects, Set.of(SkillIds.LIFE_STEAL, path, SkillIds.EXPLOSIVE_PAYLOAD), "ranged", item, none, 10, 10, 1);
            near(withPayload.healingPerSecond(), continued.healingPerSecond(), "Payload area must not duplicate native continuation healing");
            near(RuntimeBuildScenarios.directContinuationHealing(effects, Set.of(path), 5, 2, "ranged"), 0, "No Life Steal means no continuation healing");
            near(RuntimeBuildScenarios.directContinuationHealing(effects, Set.of(SkillIds.LIFE_STEAL, path), 5, 2, "melee_shield"), 0, "Melee has no projectile continuation");
            var cappedJson = runtime.toJson(); cappedJson.getAsJsonObject("effects").getAsJsonObject("projectiles").addProperty("maximumImpacts", 1);
            var capped = RuntimeBalanceDefinition.fromJson(cappedJson).config().skillEffects();
            near(RuntimeBuildScenarios.directContinuationHealing(capped, Set.of(SkillIds.LIFE_STEAL, path), 5, 2, "ranged"), 0, "Native total-impact budget prevents extra healing");
        }
        var charged = RuntimeBuildScenarios.combat(effects, Set.of(SkillIds.LIFE_STEAL, SkillIds.STATIC_CHARGE), "ranged", item, none, 10, 10, 1);
        check(charged.healingPerSecond() > ls.healingPerSecond(), "Static Charge is part of accepted direct primary damage");
        near(RuntimeBuildScenarios.combat(effects, Set.of(SkillIds.LIFE_STEAL), "ranged", item, none, 10, 10, 1, 2).healingPerSecond(),
                ls.healingPerSecond() * 2, "Healing Effectiveness follows native Life Steal heal");
        near(RuntimeBuildScenarios.combat(effects, Set.of(SkillIds.RISING_RECOVERY), "ranged", item, none, 10, 10, .5, 2).healingPerSecond(),
                recovery, "Accelerated FoodData healing retains the existing Healing Effectiveness bypass");
        var passive = new BuildComposition.Modifier(0, 1, 1, 0, 0, 0, 0, 0, 0, 0, .5, 0);
        double risingFraction = v.risingRecovery().maxSpeedBonus() * Math.pow(.5, v.risingRecovery().recoveryCurveExponent());
        near(RuntimeBuildScenarios.combat(effects, Set.of(SkillIds.RISING_RECOVERY), "ranged", item, passive, 10, 10, .5, 2).healingPerSecond(),
                .5 * (1 + risingFraction) + recovery, "Rising Recovery multiplies Nexus passive and native food regeneration");
        for (var id : List.of(SkillIds.FEAST_REFLEX, SkillIds.INNER_SUSTENANCE))
            near(RuntimeBuildScenarios.vitalityHealing(effects, Set.of(id), 5, 2, "ranged", .5), 0, "Resource/capability cannot invent in-combat healing");
        try { RuntimeBuildScenarios.vitalityHealing(effects, Set.of(SkillIds.RISING_RECOVERY, SkillIds.LIFE_STEAL), 5, 2, "ranged", .5);
            throw new AssertionError("Exclusive recovery stacked"); } catch (IllegalArgumentException expected) { checks++; }
        try { RuntimeBuildScenarios.vitalityHealing(effects, Set.of(SkillIds.FEAST_REFLEX, SkillIds.INNER_SUSTENANCE), 5, 2, "ranged", .5);
            throw new AssertionError("Exclusive sustenance stacked"); } catch (IllegalArgumentException expected) { checks++; }
    }
    private static void projections(RuntimeBalanceDefinition runtime) {
        var ranks = new LinkedHashMap<ResourceLocation, Integer>(); SkillRegistry.values().forEach(skill -> ranks.put(skill.id(), runtime.skillCurves().get(skill.id().toString()).maximumRank()));
        var projection = SkillLoadoutProjection.project(SkillRegistry.values(), AscendanceTiers.TRANSCENDENT.id(), ranks,
                (id, rank) -> runtime.skillCurves().get(id.toString()).ranks().get(rank - 1).powerMultiplier(), Map.of(), false);
        var seen = new HashSet<ResourceLocation>();
        for (var scenario : projection.scenarios()) {
            var active = scenario.contributingRanks().keySet(); seen.addAll(active);
            check(SKILLS.subList(0, 2).stream().filter(active::contains).count() <= 1, "Recovery projection stacks exclusive choices");
            check(SKILLS.subList(2, 4).stream().filter(active::contains).count() <= 1, "Sustenance projection stacks exclusive choices");
        }
        check(seen.containsAll(SKILLS), "Current projection omitted implemented Vitality skills");
    }
    private static JsonObject vitality(JsonObject json) { return json.getAsJsonObject("effects").getAsJsonObject("vitality"); }
    private static void reject(RuntimeBalanceDefinition runtime, Consumer<JsonObject> mutation) {
        var json = runtime.toJson(); mutation.accept(json);
        try { RuntimeBalanceDefinition.fromJson(json); } catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError("Invalid Vitality schema accepted");
    }
    private static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1e-9, message); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}

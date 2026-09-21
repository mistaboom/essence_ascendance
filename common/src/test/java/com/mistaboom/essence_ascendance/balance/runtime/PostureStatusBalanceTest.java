package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.balance.engine.BuildComposition;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.config.*;
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

/** Generated posture/status schema, honest ranks, exclusive projections and actual mitigation order. */
public final class PostureStatusBalanceTest {
    private static int checks;
    private static final List<ResourceLocation> SKILLS = List.of(SkillIds.EVASIVE_CURRENT, SkillIds.BULWARK_STANCE,
            SkillIds.ADAPTIVE_GUARD, SkillIds.STATUS_MIRROR, SkillIds.PURE_STATE);

    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread,error) -> error.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init(); MilestoneProviders.init();
        Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        var runtime = RuntimeBalanceDefinition.bootstrap();
        check(runtime.toJson().equals(RuntimeBalanceDefinition.bootstrap().toJson()), "Generation must be deterministic");
        check(runtime.toJson().equals(RuntimeBalanceDefinition.fromJson(runtime.toJson()).toJson()), "Strict JSON round trip");
        near(runtime.config().skillEffects().posture().evasive().hitDrainFraction(),.125,"Generated damaging-hit debit keeps seven eighths of a full meter");
        near(runtime.config().skillEffects().posture().evasive().successDrainFraction(),.5,"Generated dodge debit is four times the damaging-hit debit");
        for (String section : List.of("posture", "status")) {
            reject(runtime, j -> j.getAsJsonObject("effects").remove(section));
            reject(runtime, j -> j.getAsJsonObject("effects").getAsJsonObject(section).addProperty("fakePower", 1));
            if (section.equals("status")) {
                for (String field : runtime.toJson().getAsJsonObject("effects").getAsJsonObject(section).keySet())
                    invalidFields(runtime, section, null, field);
            } else for (var entry : runtime.toJson().getAsJsonObject("effects").getAsJsonObject(section).entrySet()) {
                reject(runtime, j -> j.getAsJsonObject("effects").getAsJsonObject(section).remove(entry.getKey()));
                for (String field : entry.getValue().getAsJsonObject().keySet()) invalidFields(runtime, section, entry.getKey(), field);
            }
        }
        reject(runtime, j -> field(j, "posture", "evasive").addProperty("maximumDodgeChance", .751));
        reject(runtime, j -> field(j, "posture", "bulwark").addProperty("maximumResistance", 1));
        reject(runtime, j -> field(j, "posture", "adaptive").addProperty("minimumHits", 1));
        reject(runtime, j -> field(j, "posture", "adaptive").addProperty("resistancePerStack", .76));
        reject(runtime, j -> field(j, "status", null).addProperty("mirrorCooldownTicks", 19));
        var inputs = BalanceOverrides.parse("[exact]\n\"/runtime/effects/posture/evasive/buildTicks\" = 100\n"
                + "\"/runtime/effects/posture/evasive/hitDrainFraction\" = 0.25\n"
                + "\"/runtime/effects/posture/evasive/successDrainFraction\" = 1.0\n"
                + "\"/runtime/effects/status/mirrorCooldownTicks\" = 180\n", "posture-status.toml");
        var changed = RuntimeReferencePolicy.withBootstrapReferences(() -> RuntimeBalanceDefinition.generate(
                RuntimeReferencePolicy.bootstrapEvidence(), BalanceSettings.defaults(), inputs));
        check(changed.config().skillEffects().posture().evasive().buildTicks() == 100, "Posture exact TOML consumer");
        near(changed.config().skillEffects().posture().evasive().hitDrainFraction(),.25,"Explicit pack-maker hit debit overrides the new default");
        near(changed.config().skillEffects().posture().evasive().successDrainFraction(),1,"Explicit pack-maker dodge debit remains supported");
        check(changed.config().skillEffects().status().mirrorCooldownTicks() == 180, "Floor-compatible status exact TOML consumer");
        check(!changed.config().balanceProfile().id().equals(runtime.config().balanceProfile().id()), "Content identity must change");
        check(changed.toJson().get("attunement").equals(runtime.toJson().get("attunement")), "Tuning does not change Attunement");
        ranks(runtime);
        numeric(runtime.config().skillEffects());
        projections(runtime);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "PostureStatusBalanceTest: " + checks + " generated schema, bounds, rank, native-order model and projection checks PASS");
    }

    private static void invalidFields(RuntimeBalanceDefinition runtime, String section, String child, String key) {
        reject(runtime, j -> field(j, section, child).remove(key));
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
            reject(runtime, j -> field(j, section, child).addProperty(key, invalid));
    }

    private static void ranks(RuntimeBalanceDefinition runtime) {
        SkillBalanceRuntime.install(runtime.skillCurves());
        var base = runtime.config().skillEffects();
        for (var id : SKILLS) {
            var curve = runtime.skillCurves().get(id.toString());
            check(curve.maximumRank() == curve.ranks().size(), "Only meaningful purchasable states: " + id);
            check(SkillRankEffectScaling.apply(base, Map.of(id, 1)).equals(base), "First rank must use generated gameplay value");
            var ranked = SkillRankEffectScaling.apply(base, Map.of(id, curve.maximumRank()));
            ranked.validate();
            if (id.equals(SkillIds.PURE_STATE)) {
                check(!SkillRankEffectScaling.supports(id), "Pure State must not have an inert numeric consumer");
                check(ranked.equals(base), "Pure State cannot invent a numeric benefit");
            } else {
                check(SkillRankEffectScaling.supports(id), "Missing numeric consumer: " + id);
                check(curve.maximumRank() == 1 || !ranked.equals(base), "Honest numeric rank must change its parameter: " + id);
            }
            check(ranked.frenzy().equals(base.frenzy()) && ranked.projectiles().equals(base.projectiles())
                    && ranked.guard().equals(base.guard()), "Defense rank changed unrelated offensive or guard tuning");
            check(ranked.posture().movement().equals(base.posture().movement()), "Ranks changed movement validation");
            check(ranked.status().mirrorMaximumDurationTicks() == base.status().mirrorMaximumDurationTicks()
                    && ranked.status().mirrorMaximumAmplifier() == base.status().mirrorMaximumAmplifier(), "Ranks changed copy safety caps");
        }
        var strong = SkillRankEffectScaling.apply(base, Map.of(SkillIds.STATUS_MIRROR, runtime.skillCurves().get(SkillIds.STATUS_MIRROR.toString()).maximumRank()));
        near(RuntimeBuildScenarios.defensivePressure(strong, Set.of(SkillIds.STATUS_MIRROR)).maximumMirrorTransfersPerSecond(),
                20.0 / strong.status().mirrorCooldownTicks(), "Mirror capacity has applications/second units");
        SkillBalanceRuntime.clear();
    }

    private static void numeric(SkillEffectBalanceSettings settings) {
        var item = new BuildComposition.Equipment(10, 2, 15, 4, 20, 0);
        var nexus = new BuildComposition.Modifier(0, 1, 1, 0, 0, 0, 0, 0, .15, 0, 0, 0);
        var baseline = RuntimeBuildScenarios.combat(settings, Set.of(), "melee_shield", item, nexus, 15, 10, 1);
        for (var id : SKILLS) {
            var result = RuntimeBuildScenarios.combat(settings, Set.of(id), "melee_shield", item, nexus, 15, 10, 1);
            near(result.sustainedDamage(), baseline.sustainedDamage(), "Defense/status cannot consume offensive allowance");
            near(result.burstDamage(), baseline.burstDamage(), "Defense/status cannot fabricate burst");
            var pressure = RuntimeBuildScenarios.defensivePressure(settings, Set.of(id));
            if (id.equals(SkillIds.PURE_STATE) || id.equals(SkillIds.STATUS_MIRROR)) {
                near(result.effectiveHealth(), baseline.effectiveHealth(), "No status encounter evidence means no invented health");
                near(pressure.peakHarmfulStatusPrevention(), 1, "Ready status capability preserves full catalog promise");
            } else check(result.effectiveHealth() > baseline.effectiveHealth(), "Posture must contribute to survival");
        }
        double resistance = settings.posture().bulwark().maximumResistance();
        double taken = .85 * (1 - resistance);
        double expected = 20 / (taken * BuildComposition.armorDamageFraction(15 * taken, 15, 4));
        near(RuntimeBuildScenarios.combat(settings, Set.of(SkillIds.BULWARK_STANCE), "melee_shield", item,
                nexus, 15, 10, 1).effectiveHealth(), expected, "Native resistance precedes nonlinear armor/toughness");
        try {
            RuntimeBuildScenarios.defensivePressure(settings, Set.of(SkillIds.EVASIVE_CURRENT, SkillIds.BULWARK_STANCE));
            throw new AssertionError("Impossible posture composition accepted");
        } catch (IllegalArgumentException expectedFailure) { checks++; }
        try {
            RuntimeBuildScenarios.defensivePressure(settings, Set.of(SkillIds.STATUS_MIRROR, SkillIds.PURE_STATE));
            throw new AssertionError("Impossible status composition accepted");
        } catch (IllegalArgumentException expectedFailure) { checks++; }
    }

    private static void projections(RuntimeBalanceDefinition runtime) {
        var ranks = new LinkedHashMap<ResourceLocation, Integer>();
        SkillRegistry.values().forEach(skill -> ranks.put(skill.id(), runtime.skillCurves().get(skill.id().toString()).maximumRank()));
        for (boolean planned : List.of(false, true)) {
            var projection = SkillLoadoutProjection.project(SkillRegistry.values(), AscendanceTiers.TRANSCENDENT.id(), ranks,
                    (id, rank) -> runtime.skillCurves().get(id.toString()).ranks().get(rank - 1).powerMultiplier(), Map.of(), planned);
            var seen = new HashSet<ResourceLocation>();
            for (var scenario : projection.scenarios()) {
                var active = scenario.contributingRanks().keySet(); seen.addAll(active);
                check(SKILLS.subList(0, 3).stream().filter(active::contains).count() <= 1, "Projection stacked exclusive postures");
                check(SKILLS.subList(3, 5).stream().filter(active::contains).count() <= 1, "Projection stacked status choices");
                if (active.contains(SkillIds.PURE_STATE)) check(scenario.capabilityPressure().get(CapabilityAxis.STATUS_RESISTANCE) >= 1,
                        "Pure State's full binary pressure is present");
            }
            check(seen.containsAll(SKILLS), "Projection omitted an implemented posture/status choice");
        }
        for (int rank : List.of(1)) {
            var pure = SkillLoadoutProjection.project(SkillRegistry.values(), AscendanceTiers.TRANSCENDENT.id(),
                    Map.of(SkillIds.PURE_STATE, rank), (id, r) -> 2.5, Map.of(), false);
            near(pure.axisEnvelope().get(CapabilityAxis.STATUS_RESISTANCE), 1,
                    "Pure State immunity does not scale at diagnostic rank " + rank);
        }
    }

    private static JsonObject field(JsonObject json, String section, String child) {
        var effect = json.getAsJsonObject("effects").getAsJsonObject(section);
        return child == null ? effect : effect.getAsJsonObject(child);
    }
    private static void reject(RuntimeBalanceDefinition runtime, Consumer<JsonObject> mutation) {
        var json = runtime.toJson(); mutation.accept(json);
        try { RuntimeBalanceDefinition.fromJson(json); }
        catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError("Invalid posture/status schema accepted");
    }
    private static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1e-9, message); }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); checks++; }
}

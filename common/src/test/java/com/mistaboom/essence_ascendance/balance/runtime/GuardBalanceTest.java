package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.config.GuardBalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Executable guard tuning, rank, deterministic identity and existing-profile contract tests. */
public final class GuardBalanceTest {
    private static int checks;
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread,error) -> error.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        var first = RuntimeBalanceDefinition.bootstrap();
        check(first.toJson().equals(RuntimeBalanceDefinition.bootstrap().toJson()), "Guard generation differs across identical inputs");
        check(first.toJson().equals(RuntimeBalanceDefinition.fromJson(first.toJson()).toJson()), "Guard values fail strict JSON round trip");
        var defaults = GuardBalanceSettings.defaults();
        defaults.validate();
        check(defaults.mobility().slowdownRemoval() >= .8 && defaults.mobility().stepHeight() <= 1.1, "Guard movement exceeds short traversal contract");
        check(defaults.ram().minimumSpeed() > 0 && defaults.ram().staggerMovementMultiplier() > 0, "Ram became a passive aura/root");
        check(defaults.storedForce().conversion() == 1 - com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings.defaults().ricochetDamageMultiplier(), "Guard seed lost shared normalized retention reference");
        reject(first, j -> j.getAsJsonObject("effects").remove("guard"));
        reject(first, j -> guard(j).addProperty("freeShieldReflection", 1));
        for (var sectionEntry : guard(first.toJson()).entrySet()) {
            String section = sectionEntry.getKey();
            reject(first, j -> guard(j).remove(section));
            for (String field : sectionEntry.getValue().getAsJsonObject().keySet()) {
                reject(first, j -> field(j, section).remove(field));
                reject(first, j -> field(j, section).addProperty(field, Double.NaN));
                reject(first, j -> field(j, section).addProperty(field, Double.POSITIVE_INFINITY));
                reject(first, j -> field(j, section).addProperty(field, -1));
            }
        }
        for (String[] invalid : new String[][]{{"mobility","slowdownRemoval","1.01"}, {"mobility","stepHeight","1.2"},
                {"ram","minimumSpeed","0"}, {"ram","maximumSweep","4"}, {"ram","staggerMovementMultiplier","0"},
                {"ram","contactLimit","9"}, {"ram","repeatCooldownTicks","1"},
                {"ward","preventedReflectionScale","1.1"}, {"ward","knockbackEchoCap","2.1"},
                {"storedForce","conversion","1.1"}, {"storedForce","capacity","1025"}, {"storedForce","durationTicks","1201"},
                {"amplifier","maximumMultiplier","0.9"}, {"amplifier","perBlockGrowth","4"},
                {"perfectGuard","windowTicks","0"}, {"perfectGuard","windowTicks","11"},
                {"reprisal","damageScale","1.1"}, {"reprisal","maximumTargets","17"},
                {"riposte","bonusReach","2.1"}, {"riposte","protectionTicks","3"}})
            reject(first, j -> field(j, invalid[0]).addProperty(invalid[1], Double.parseDouble(invalid[2])));
        var parsed = BalanceOverrides.parse("[exact]\n\"/runtime/effects/guard/perfectGuard/windowTicks\" = 3\n"
                + "\"/runtime/effects/guard/mobility/slowdownRemoval\" = 0.9\n", "guard-test.toml");
        var overridden = RuntimeReferencePolicy.withBootstrapReferences(() -> RuntimeBalanceDefinition.generate(
                RuntimeReferencePolicy.bootstrapEvidence(), BalanceSettings.defaults(), parsed));
        check(overridden.config().skillEffects().guard().perfectGuard().windowTicks() == 3
                && overridden.config().skillEffects().guard().mobility().slowdownRemoval() == .9,
                "Guard overrides bypass existing TOML/resolved generation");
        JsonObject changedJson = first.toJson(); field(changedJson, "perfectGuard").addProperty("windowTicks", 3);
        var changed = RuntimeBalanceDefinition.fromJson(changedJson).withContentIdentity();
        check(!first.config().balanceProfile().id().equals(changed.config().balanceProfile().id()), "Changed guard timing reused content/cache identity");
        check(changed.toJson().get("attunement").equals(first.toJson().get("attunement")), "Guard tuning changed Attunement calibration");
        check(first.attunement().policy().repetitionFloor() > 0, "Nonzero repetition floor disappeared");
        check(!first.toJson().getAsJsonObject("attunement").toString().contains("eventCap"), "Guard added per-action Attunement caps");
        rankScaling(first);
        var effects = first.config().skillEffects();
        double expected = 10 * effects.guard().riposte().damageScale()
                + effects.guard().storedForce().capacity() * effects.guard().storedForce().damageScale();
        check(Math.abs(RuntimeBuildScenarios.guardCounterBurst(effects, Set.of(SkillIds.STORED_FORCE, SkillIds.RIPOSTE), 10) - expected) < 1e-12,
                "Combined counter burst omitted or multiplied flat Stored Force damage twice");
        check(RuntimeBuildScenarios.guardCounterBurst(effects, Set.of(SkillIds.GUARDED_ADVANCE, SkillIds.SHIELD_RAM,
                SkillIds.REFLEXIVE_WARD, SkillIds.GUARD_AMPLIFIER, SkillIds.CROWD_REPRISAL), 10) == 0,
                "Guard posture, control or requested reflection fabricated counterattack damage");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("GuardBalanceTest: " + checks
                + " deterministic guard schema, bounds, TOML, rank, content identity and Attunement checks PASS");
    }

    private static void rankScaling(RuntimeBalanceDefinition runtime) {
        SkillBalanceRuntime.install(runtime.skillCurves());
        var base = runtime.config().skillEffects();
        check(SkillRankEffectScaling.apply(base, Map.of()) == base, "Empty effective ranks changed runtime profile");
        Map<ResourceLocation, Integer> ranks = new LinkedHashMap<>();
        for (ResourceLocation id : List.of(SkillIds.GUARDED_ADVANCE, SkillIds.SHIELD_RAM, SkillIds.REFLEXIVE_WARD,
                SkillIds.STORED_FORCE, SkillIds.GUARD_AMPLIFIER, SkillIds.CROWD_REPRISAL, SkillIds.RIPOSTE)) {
            var curve = runtime.skillCurves().get(id.toString());
            check(curve.ranks().size() > 1, "Guard rank curve lost procedural projections");
            GuardBalanceSettings previous = base.guard();
            for (int rank = 1; rank <= curve.ranks().size(); rank++) {
                var next = SkillRankEffectScaling.apply(base, Map.of(id, rank)).guard();
                next.validate();
                check(power(next, id) >= power(previous, id), "Guard power decreases at projected rank " + rank);
                check(next.perfectGuard().equals(base.guard().perfectGuard()) && next.ram().contactLimit() == base.guard().ram().contactLimit()
                        && next.ram().minimumSpeed() == base.guard().ram().minimumSpeed()
                        && next.riposte().protectionTicks() == base.guard().riposte().protectionTicks(), "Rank increased timing, contact budget, or protection instead of power");
                previous = next;
            }
            ranks.put(id, curve.ranks().size());
            check(power(previous, id) > power(base.guard(), id), "Guard rank consumer is missing: " + id);
        }
        var all = SkillRankEffectScaling.apply(base, ranks);
        Map<ResourceLocation,Integer> reversed = new LinkedHashMap<>();
        ranks.entrySet().stream().sorted(Map.Entry.<ResourceLocation,Integer>comparingByKey().reversed()).forEach(e -> reversed.put(e.getKey(), e.getValue()));
        check(all.equals(SkillRankEffectScaling.apply(base, reversed)), "Guard rank resolution depends on map iteration order");
        check(all.projectiles().equals(base.projectiles()) && all.frenzy().equals(base.frenzy()), "Guard ranks changed unrelated effects");
        SkillBalanceRuntime.clear();
    }
    private static double power(GuardBalanceSettings v, ResourceLocation id) {
        if (id.equals(SkillIds.GUARDED_ADVANCE)) return v.mobility().slowdownRemoval();
        if (id.equals(SkillIds.SHIELD_RAM)) return v.ram().knockback();
        if (id.equals(SkillIds.REFLEXIVE_WARD)) return v.ward().knockbackEchoScale();
        if (id.equals(SkillIds.STORED_FORCE)) return v.storedForce().damageScale();
        if (id.equals(SkillIds.GUARD_AMPLIFIER)) return v.amplifier().maximumMultiplier();
        if (id.equals(SkillIds.CROWD_REPRISAL)) return v.reprisal().damageScale();
        return v.riposte().damageScale();
    }
    private static JsonObject guard(JsonObject json) { return json.getAsJsonObject("effects").getAsJsonObject("guard"); }
    private static JsonObject field(JsonObject json, String section) { return guard(json).getAsJsonObject(section); }
    private static void reject(RuntimeBalanceDefinition runtime, Consumer<JsonObject> mutation) {
        JsonObject json = runtime.toJson(); mutation.accept(json);
        try { RuntimeBalanceDefinition.fromJson(json); }
        catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError("Malformed guard profile accepted: " + guard(json));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}

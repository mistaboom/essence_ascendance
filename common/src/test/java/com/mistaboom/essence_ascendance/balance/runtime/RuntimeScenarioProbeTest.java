package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.BuildComposition.Channel;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.util.*;

/** The decision-only path must agree with complete numeric reports for accepted and rejected candidates. */
public final class RuntimeScenarioProbeTest {
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) ->
                failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init(); EquipmentProfiles.init();
        var baseline = RuntimeBalanceDefinition.bootstrap();
        var evidence = RuntimeReferencePolicy.bootstrapEvidence();
        int checks = 0, safe = 0, unsafe = 0;
        // Calibration must model the same flat damage as runtime, before skill amplification.
        var damageBonus = new com.mistaboom.essence_ascendance.balance.engine.BuildComposition.Modifier(
                5, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        for (String family : List.of("melee_shield", "ranged", "caster")) for (double baseDamage : List.of(2.0, 9.0)) {
            var equipment = new com.mistaboom.essence_ascendance.balance.engine.BuildComposition.Equipment(
                    baseDamage, 1, 0, 0, 20, 0);
            var measured = RuntimeBuildScenarios.combat(baseline.config().skillEffects(), Set.of(), family,
                    equipment, damageBonus, 4, 10, 1);
            if (Math.abs(measured.burstDamage() - baseDamage - 5) > 1e-9
                    || Math.abs(measured.sustainedDamage() - baseDamage - 5) > 1e-9)
                throw new AssertionError("Flat Bonus calibration diverges from native damage for " + family);
            var frenzy = baseline.config().skillEffects().frenzy();
            var amplified = RuntimeBuildScenarios.combat(baseline.config().skillEffects(),
                    Set.of(com.mistaboom.essence_ascendance.skill.SkillIds.FRENZY), family,
                    equipment, damageBonus, 4, 10, 1);
            double expected = (baseDamage + 5) * (1 + frenzy.maxStacks() * frenzy.damageBonusPercentPerStack() / 100);
            if (Math.abs(amplified.burstDamage() - expected) > 1e-9)
                throw new AssertionError("Skill amplification must include the flat Bonus once");
            checks += 2;
        }
        double maximumHealth = baseline.config().balanceProfile().bonusTrack(EssenceStats.MAX_HEALTH.id())
                .inputs().get("native_maximum_effect");
        var impossible = baseline.toJson();
        impossible.getAsJsonObject("statMaxBonuses").addProperty(EssenceStats.MAX_HEALTH.id().toString(), maximumHealth + 1);
        impossible.getAsJsonObject("balanceProfile").getAsJsonObject("bonusTracks")
                .getAsJsonObject(EssenceStats.MAX_HEALTH.id().toString()).addProperty("maximumEffect", maximumHealth + 1);
        try {
            RuntimeBalanceDefinition.fromJson(impossible);
            throw new AssertionError("Physically impossible health must be rejected before scenario analysis");
        } catch (RuntimeException expected) {
            Throwable cause = expected;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof IllegalArgumentException)
                    || !cause.getMessage().contains("Bonus exceeds generated native attribute headroom essence_ascendance:max_health"))
                throw new AssertionError("Impossible health failed for an unrelated reason", expected);
            checks++;
        }
        var variants = new ArrayList<RuntimeBalanceDefinition>(); variants.add(baseline);
        RuntimeBalanceDefinition overpoweredHealth = null;
        for (var stat : List.of(EssenceStats.MELEE_DAMAGE, EssenceStats.MAX_HEALTH, EssenceStats.HEALTH_REGENERATION)) {
            var json = baseline.toJson();
            // Scenario comparisons need valid candidates. Native health headroom
            // still permits vastly excessive composition power without impossible HP.
            double maximum = stat == EssenceStats.MAX_HEALTH ? maximumHealth : 1000;
            json.getAsJsonObject("statMaxBonuses").addProperty(stat.id().toString(), maximum);
            json.getAsJsonObject("balanceProfile").getAsJsonObject("bonusTracks")
                    .getAsJsonObject(stat.id().toString()).addProperty("maximumEffect", maximum);
            var candidate = RuntimeBalanceDefinition.fromJson(json);
            variants.add(candidate);
            if (stat == EssenceStats.MAX_HEALTH) overpoweredHealth = candidate;
        }
        var ordinary = BalanceSettings.defaults();
        var settingsJson = new com.google.gson.Gson().toJsonTree(ordinary).getAsJsonObject();
        for (String key : List.of("overallPower", "earlyPower", "midPower", "latePower", "apexPower")) settingsJson.addProperty(key, 4);
        var generous = new com.google.gson.Gson().fromJson(settingsJson, BalanceSettings.class);
        for (var settings : List.of(ordinary, generous))
        for (var runtime : variants) for (boolean developed : List.of(false, true)) {
            var plan = RuntimeBuildScenarios.plan(runtime, developed);
            var report = RuntimeBuildScenarios.analyze(runtime, evidence, settings, plan);
            if (runtime == overpoweredHealth && settings == ordinary) {
                if (report.safeFor(Channel.DEFENSE))
                    throw new AssertionError("Representable excessive health must fail Defense composition: developed=" + developed);
                checks++;
            }
            for (Channel channel : Arrays.asList(null, Channel.OFFENSE, Channel.DEFENSE, Channel.HEALING)) {
                boolean expected = report.safeFor(channel);
                if (RuntimeBuildScenarios.isSafe(runtime, evidence, settings, plan, channel) != expected)
                    throw new AssertionError("Decision-only probe disagrees with full report: " + channel + "/developed=" + developed);
                if (expected) safe++; else unsafe++;
                checks++;
            }
        }
        if (safe == 0 || unsafe == 0) throw new AssertionError("Both accepted and rejected candidates must be exercised: safe=" + safe + ", unsafe=" + unsafe);
        var empty = new RuntimeBuildScenarios.Plan(Map.of(), Map.of(), false, null);
        for (boolean detailed : List.of(false, true)) try {
            if (detailed) RuntimeBuildScenarios.analyze(baseline, evidence, BalanceSettings.defaults(), empty);
            else RuntimeBuildScenarios.isSafe(baseline, evidence, BalanceSettings.defaults(), empty, null);
            throw new AssertionError("Missing scenario tiers cannot silently succeed");
        } catch (NullPointerException | IllegalStateException expected) { checks++; }
        System.out.println("RuntimeScenarioProbeTest: " + checks + " differential checks PASS (safe=" + safe + ", unsafe=" + unsafe + ")");
    }
}

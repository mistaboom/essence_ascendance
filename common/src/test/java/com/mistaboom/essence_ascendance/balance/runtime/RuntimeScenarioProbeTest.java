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
        var variants = new ArrayList<RuntimeBalanceDefinition>(); variants.add(baseline);
        for (var stat : List.of(EssenceStats.MELEE_DAMAGE, EssenceStats.MAX_HEALTH, EssenceStats.HEALTH_REGENERATION)) {
            var json = baseline.toJson();
            json.getAsJsonObject("statMaxBonuses").addProperty(stat.id().toString(), 1000);
            json.getAsJsonObject("balanceProfile").getAsJsonObject("bonusTracks")
                    .getAsJsonObject(stat.id().toString()).addProperty("maximumEffect", 1000);
            variants.add(RuntimeBalanceDefinition.fromJson(json));
        }
        var settingsJson = new com.google.gson.Gson().toJsonTree(BalanceSettings.defaults()).getAsJsonObject();
        for (String key : List.of("overallPower", "earlyPower", "midPower", "latePower", "apexPower")) settingsJson.addProperty(key, 4);
        var generous = new com.google.gson.Gson().fromJson(settingsJson, BalanceSettings.class);
        int checks = 0, safe = 0, unsafe = 0;
        for (var settings : List.of(BalanceSettings.defaults(), generous))
        for (var runtime : variants) for (boolean developed : List.of(false, true)) {
            var plan = RuntimeBuildScenarios.plan(runtime, developed);
            var report = RuntimeBuildScenarios.analyze(runtime, evidence, settings, plan);
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

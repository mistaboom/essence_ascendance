package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.util.Comparator;
import java.util.function.Consumer;

/** Generated unlocks must be reachable before the next tier, with no prescribed build. */
public final class RuntimeAscensionPolicyTest {
    private static int checks;
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((t,e) -> e.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        var evidence = RuntimeReferencePolicy.bootstrapEvidence();
        var defaults = RuntimeBalanceDefinition.generate(evidence, BalanceSettings.defaults(), BalanceOverrides.empty());
        check(RuntimeAscensionPolicy.usesEssenceQualification(defaults), "Generated qualification policy missing");
        verify(defaults);
        for (double length : new double[]{0.1, 1, 10}) for (double pressure : new double[]{0.1, 1, 10}) {
            var settings = BalanceSettings.parse("[progression]\nlength=" + length + "\ncost_pressure=" + pressure, "policy-fixture");
            var generated = RuntimeBalanceDefinition.generate(evidence, settings, BalanceOverrides.empty());
            verify(generated);
            check(generated.toJson().equals(RuntimeBalanceDefinition.generate(evidence, settings, BalanceOverrides.empty()).toJson()),
                    "Progression generation changed with identical inputs");
        }
        String first = AscendanceAdvancements.DORMANT_TO_AWAKENED.id().toString();
        String second = AscendanceAdvancements.AWAKENED_TO_RESONANT.id().toString();
        reject(defaults, j -> j.getAsJsonObject("advancements").remove(first), "Missing transition accepted");
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(first)
                .addProperty("toTierId", AscendanceTiers.RESONANT.id().toString()), "Skipped tier accepted");
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(second)
                .addProperty("fromTierId", AscendanceTiers.DORMANT.id().toString()), "Duplicate origin accepted");
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(first)
                .addProperty("minimumDevelopedStats", Integer.MAX_VALUE), "Impossible stat breadth accepted");
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(first)
                .addProperty("minimumRepresentedCategories", Integer.MAX_VALUE), "Impossible category breadth accepted");
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(first)
                .addProperty("totalInvestmentMultiplier", Long.MAX_VALUE), "Overflowing threshold accepted");
        reject(defaults, j -> j.getAsJsonObject("composition").addProperty(RuntimeAscensionPolicy.COMPOSITION_KEY, 2), "Unknown accounting mode accepted");

        // A previously saved profile is compatible until explicit rebuild. The same
        // known mining cycle is rejected when a newly generated policy is installed.
        JsonObject cyclic = defaults.toJson();
        cyclic.getAsJsonObject("advancements").getAsJsonObject(first).add("worldRequirement",
                milestoneGate(Milestones.OBTAIN_DIAMONDS.id().toString()));
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(first)
                .add("worldRequirement", milestoneGate(Milestones.OBTAIN_DIAMONDS.id().toString())), "Future-tier mining gate accepted");
        cyclic.getAsJsonObject("composition").remove(RuntimeAscensionPolicy.COMPOSITION_KEY);
        var legacy = RuntimeBalanceDefinition.fromJson(cyclic);
        check(!RuntimeAscensionPolicy.usesEssenceQualification(legacy), "Cached profile silently opted into a new policy");
        check(!HarvestProgressionSafety.evaluate(legacy.config()).isEmpty(), "Legacy gate was silently removed");
        check(legacy.toJson().equals(cyclic), "Legacy decode rewrote saved configuration");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "RuntimeAscensionPolicyTest: " + checks + " generated chain, budget scaling, no mining/boss/breadth locks and legacy preservation checks PASS");
    }
    private static JsonObject milestoneGate(String id) {
        var gate = new JsonObject(); gate.addProperty("kind", "milestone"); gate.addProperty("id", id); return gate;
    }
    private static void verify(RuntimeBalanceDefinition runtime) {
        var tiers = AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(t -> t.order())).toList();
        var config = runtime.config(); var profile = config.balanceProfile();
        check(config.advancements().size() == tiers.size() - 1, "Transition count differs from tier chain");
        long previous = 0;
        for (int i = 0; i + 1 < tiers.size(); i++) {
            var from = tiers.get(i); var to = tiers.get(i + 1);
            var transition = config.advancements().values().stream().filter(a -> a.fromTierId().equals(from.id())).findFirst().orElseThrow();
            long cap = profile.getDefaultInvestmentCap(from), next = profile.getDefaultInvestmentCap(to);
            long required = transition.getRequiredInvestment(cap);
            check(transition.toTierId().equals(to.id()), "Transition does not advance exactly one tier");
            check(required >= next && required > previous, "Generated requirement does not reach next-tier budget");
            check(required - Math.max(next, previous + 1) < cap, "Threshold exceeds necessary current-cap rounding");
            check(transition.minimumDevelopedStats() == 0 && transition.minimumRepresentedCategories() == 0, "Generated unlock forces a Nexus build");
            check(transition.worldRequirement() instanceof MilestoneRequirement.Always, "Generated unlock requires a specific world/boss milestone");
            previous = required;
        }
        check(HarvestProgressionSafety.evaluate(config).isEmpty(), "Generated unlock has a harvest progression cycle");
        check(config.milestones().containsKey(Milestones.SKY_LIMIT.id()), "Skill-specific acquisition milestone removed");
    }
    private static void reject(RuntimeBalanceDefinition base, Consumer<JsonObject> mutation, String message) {
        JsonObject json = base.toJson(); mutation.accept(json);
        try { RuntimeBalanceDefinition.fromJson(json); } catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}

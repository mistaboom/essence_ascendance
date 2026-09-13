package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Real registry/codec checks for progression safety and physical-client/server profile isolation. */
public final class RuntimeLifecycleTest {
    private static int assertions;
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread,failure)->failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        AscendanceAdvancements.init();
        var server = RuntimeBalanceDefinition.bootstrap();
        String advancement = AscendanceAdvancements.DORMANT_TO_AWAKENED.id().toString();
        String milestone = Milestones.OBTAIN_DIAMONDS.id().toString();

        JsonObject overflow = server.toJson();
        advancement(overflow, advancement).addProperty("totalInvestmentMultiplier", Long.MAX_VALUE);
        rejected(()->RuntimeBalanceDefinition.fromJson(overflow), "Overflowing Ascendance investment accepted");
        JsonObject unknownTier = server.toJson();
        advancement(unknownTier, advancement).addProperty("toTierId", "test:missing_tier");
        rejected(()->RuntimeBalanceDefinition.fromJson(unknownTier), "Unknown target tier accepted");
        JsonObject backwards = server.toJson();
        advancement(backwards, advancement).addProperty("toTierId", AscendanceTiers.DORMANT.id().toString());
        rejected(()->RuntimeBalanceDefinition.fromJson(backwards), "Non-ascending transition accepted");
        JsonObject missingGate = server.toJson();
        JsonObject reference = new JsonObject(); reference.addProperty("kind", "milestone"); reference.addProperty("id", milestone);
        advancement(missingGate, advancement).add("worldRequirement", reference);
        missingGate.getAsJsonObject("milestones").remove(milestone);
        rejected(()->RuntimeBalanceDefinition.fromJson(missingGate), "Missing referenced milestone accepted");
        JsonObject unknownProvider = server.toJson();
        unknownProvider.getAsJsonObject("milestones").getAsJsonObject(milestone)
                .addProperty("providerId", "test:missing_provider");
        var serverOnlyProvider = RuntimeBalanceDefinition.fromJson(unknownProvider);
        EssenceConfigManager.installClient(serverOnlyProvider);
        check(EssenceConfigManager.clientRuntime() == serverOnlyProvider,
                "Client preview required a server-only milestone provider implementation");
        EssenceConfigManager.clearClient();
        rejected(serverOnlyProvider::validateServerReferences, "Unknown milestone provider accepted on server");
        check(server.toJson().equals(RuntimeBalanceDefinition.fromJson(server.toJson()).toJson()),
                "Generated milestone gates no longer roundtrip");

        // A valid explicit removal of a world gate remains supported.
        JsonObject ungated = server.toJson();
        JsonObject always = new JsonObject(); always.addProperty("kind", "always");
        advancement(ungated, advancement).add("worldRequirement", always);
        check(RuntimeBalanceDefinition.fromJson(ungated).config().getAdvancement(
                AscendanceAdvancements.DORMANT_TO_AWAKENED.id()).orElseThrow().worldRequirement()
                instanceof MilestoneRequirement.Always, "Explicit always gate rejected");

        String skill = server.skillCurves().keySet().iterator().next();
        JsonObject clientJson = server.toJson();
        clientJson.getAsJsonObject("balanceProfile").addProperty("displayName", "Other server preview");
        clientJson.getAsJsonObject("skillCurves").getAsJsonObject(skill).getAsJsonArray("ranks")
                .get(0).getAsJsonObject().addProperty("cost", 1L);
        var client = RuntimeBalanceDefinition.fromJson(clientJson).withContentIdentity();
        EssenceConfigManager.installClient(client);
        check(EssenceConfigManager.runtime() == client && !EssenceConfigManager.authoritativeReady(),
                "Remote client preview became server authority");
        EssenceConfigManager.install(server);
        check(EssenceConfigManager.runtime() == server && EssenceConfigManager.authoritativeReady(),
                "Integrated server did not replace client preview");
        EssenceConfigManager.installClient(client);
        check(EssenceConfigManager.runtime() == server, "Late client packet replaced server authority");
        check(SkillBalanceRuntime.snapshot().equals(server.skillCurves()), "Client packet replaced authoritative skill prices");
        EssenceConfigManager.clearClient();
        check(EssenceConfigManager.runtime() == server && SkillBalanceRuntime.snapshot().equals(server.skillCurves()),
                "Client disconnect cleared integrated server balance");
        rejected(()->EssenceConfigManager.install(null), "Null runtime install accepted");
        rejected(()->EssenceConfigManager.install(serverOnlyProvider), "Server installed unknown milestone provider");
        check(EssenceConfigManager.runtime() == server, "Failed installation replaced active profile");
        EssenceConfigManager.reset();
        check(!EssenceConfigManager.authoritativeReady() && !SkillBalanceRuntime.ready(),
                "Stopped server leaked runtime or skill prices");
        check(EssenceConfigManager.runtime() != server, "Stopped server retained authoritative object as bootstrap");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "RuntimeLifecycleTest: " + assertions + " progression/lifecycle assertions PASS");
    }
    private static JsonObject advancement(JsonObject json, String id) {
        return json.getAsJsonObject("advancements").getAsJsonObject(id);
    }
    private static void rejected(Runnable work, String message) {
        try { work.run(); } catch (IllegalArgumentException | NullPointerException expected) { assertions++; return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}

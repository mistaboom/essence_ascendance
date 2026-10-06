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
        var provisionalPlayer = new com.mistaboom.essence_ascendance.data.PlayerEssenceData();
        provisionalPlayer.attunement().chapter("preserved_earned_chapter");
        var beforeProvisional = provisionalPlayer.save();
        check(!EssenceConfigManager.authoritativeReady(), "A bootstrap became authoritative without validated installation");
        worldgenUnavailable("Initial worldgen used an unresolved bootstrap policy");
        EssenceConfigManager.runtime(); // Force the provisional object to exist, as it does during loader startup.
        check(com.mistaboom.essence_ascendance.attunement.AttunementService.snapshot(provisionalPlayer).categories().isEmpty(),
                "Provisional balance exposed live Attunement readiness");
        check(AscendanceEngine.evaluate(provisionalPlayer, EssenceConfigManager.serverRuntime()).status()
                        == AscendanceEvaluationResult.Status.CONFIGURATION_ERROR,
                "Provisional balance allowed player Ascension");
        check(provisionalPlayer.save().equals(beforeProvisional), "Unavailable profile reset or changed the earned chapter");
        var maximumWithoutProfile = new com.mistaboom.essence_ascendance.data.PlayerEssenceData();
        maximumWithoutProfile.setTier(AscendanceTiers.TRANSCENDENT);
        check(AscendanceEngine.evaluate(maximumWithoutProfile, null).status() == AscendanceEvaluationResult.Status.CONFIGURATION_ERROR
                        && !com.mistaboom.essence_ascendance.attunement.AttunementService.snapshot(maximumWithoutProfile).maximumTier(),
                "Missing authoritative profile was misreported as a completed constellation");
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
        check(com.mistaboom.essence_ascendance.attunement.AttunementService.snapshot(provisionalPlayer).categories().isEmpty(),
                "A client-only preview became server Attunement authority");
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
        worldgenUnavailable("Worldgen accepted a remote client's preview as server policy");
        check(EssenceConfigManager.markBalanceUnavailable() && EssenceConfigManager.balanceUnavailable(),
                "Rejected initial balance did not enter a nonfatal unavailable state");
        check(!EssenceConfigManager.authoritativeReady() && EssenceConfigManager.serverRuntime() == null,
                "Unavailable balance fabricated an authoritative runtime");
        for (String dimension : java.util.List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "test:custom")) {
            var id = net.minecraft.resources.ResourceLocation.parse(dimension);
            var policy = EssenceConfigManager.serverWorldgen();
            check(!policy.enabled(id) || !policy.distribution(id, -64, 319).enabled(),
                    "Unavailable balance authorized provisional ore in " + dimension);
        }
        check(AscendanceEngine.evaluate(provisionalPlayer, EssenceConfigManager.serverRuntime()).status()
                        == AscendanceEvaluationResult.Status.CONFIGURATION_ERROR
                        && com.mistaboom.essence_ascendance.attunement.AttunementService.snapshot(provisionalPlayer).categories().isEmpty(),
                "Unavailable balance allowed unvalidated player progression");
        check(provisionalPlayer.save().equals(beforeProvisional), "Unavailable balance changed earned player data");
        check(com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry.definitions().isEmpty()
                        && com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry.generation() == 0,
                "Unavailable balance invented dissolution mappings or published a mapping generation");
        EssenceConfigManager.install(server);
        check(!EssenceConfigManager.balanceUnavailable(), "Successful balance did not leave unavailable mode");
        check(EssenceConfigManager.serverWorldgen() == server.config().latentOreWorldgen(),
                "Worldgen did not use the single installed server profile");
        check(EssenceConfigManager.runtime() == server && EssenceConfigManager.authoritativeReady(),
                "Integrated server did not replace client preview");
        EssenceConfigManager.installClient(client);
        check(EssenceConfigManager.runtime() == server, "Late client packet replaced server authority");
        check(EssenceConfigManager.serverWorldgen() == server.config().latentOreWorldgen(),
                "Late client packet replaced worldgen policy");
        check(!EssenceConfigManager.markBalanceUnavailable() && !EssenceConfigManager.balanceUnavailable()
                        && EssenceConfigManager.serverWorldgen() == server.config().latentOreWorldgen(),
                "A failed replacement disabled the retained valid server profile");
        check(SkillBalanceRuntime.snapshot().equals(server.skillCurves()), "Client packet replaced authoritative skill prices");
        EssenceConfigManager.clearClient();
        check(EssenceConfigManager.runtime() == server && SkillBalanceRuntime.snapshot().equals(server.skillCurves()),
                "Client disconnect cleared integrated server balance");
        rejected(()->EssenceConfigManager.install(null), "Null runtime install accepted");
        rejected(()->EssenceConfigManager.install(serverOnlyProvider), "Server installed unknown milestone provider");
        check(EssenceConfigManager.runtime() == server, "Failed installation replaced active profile");
        var activePlayer = new com.mistaboom.essence_ascendance.data.PlayerEssenceData();
        check(AscendanceEngine.evaluate(activePlayer, EssenceConfigManager.serverRuntime()).status() == AscendanceEvaluationResult.Status.AVAILABLE
                        && !com.mistaboom.essence_ascendance.attunement.AttunementService.snapshot(activePlayer).categories().isEmpty(),
                "Failed replacement disabled the valid last-known-good Attunement profile");
        var activeChapter = server.attunement().chapter(activePlayer.getTierId().toString());
        for (var category : activeChapter.categories().keySet().stream().limit(activeChapter.requiredCategories()).toList()) {
            var activity = com.mistaboom.essence_ascendance.attunement.AttunementActivityRegistry.values().stream()
                    .filter(a -> a.categoryId().equals(category)).findFirst().orElseThrow();
            for (int i = 0; activePlayer.attunement().progress(category) < com.mistaboom.essence_ascendance.attunement.AttunementLedger.SCALE; i++)
                activePlayer.attunement().contribute("confirmed:" + category + ":" + i,
                        com.mistaboom.essence_ascendance.attunement.AttunementEvent.Outcome.eligible(activity.id(), "test:observed", Double.MAX_VALUE),
                        activeChapter, server.attunement().policy(), 0);
        }
        check(AscendanceEngine.evaluate(activePlayer, EssenceConfigManager.serverRuntime()).progress().readyToAscend(),
                "Installed authoritative profile could not accept earned seals");
        var savedEarned = activePlayer.save();

        EssenceConfigManager.reset();
        check(!EssenceConfigManager.balanceUnavailable(), "Stopped server leaked unavailable balance mode");
        worldgenUnavailable("Stopped server leaked a usable worldgen policy");
        check(!com.mistaboom.essence_ascendance.attunement.AttunementService.snapshot(activePlayer).ready()
                        && AscendanceEngine.evaluate(activePlayer, EssenceConfigManager.serverRuntime()).status() == AscendanceEvaluationResult.Status.CONFIGURATION_ERROR,
                "Cleared authority retained stale Ascension readiness");
        check(activePlayer.save().equals(savedEarned), "Losing profile authority erased earned seals");
        check(!EssenceConfigManager.authoritativeReady() && !SkillBalanceRuntime.ready(),
                "Stopped server leaked runtime or skill prices");
        check(EssenceConfigManager.runtime() != server, "Stopped server retained authoritative object as bootstrap");
        check(EssenceConfigManager.markBalanceUnavailable(), "Fresh unavailable mode could not be entered after stop");
        EssenceConfigManager.reset();
        worldgenUnavailable("Stopped unavailable server leaked a usable terrain policy");
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
    private static void worldgenUnavailable(String message) {
        try { EssenceConfigManager.serverWorldgen(); }
        catch (IllegalStateException expected) { assertions++; return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}

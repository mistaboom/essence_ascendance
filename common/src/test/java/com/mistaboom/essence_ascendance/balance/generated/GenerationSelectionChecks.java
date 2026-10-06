package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs against the integration suite's complete schema-2 profile and real typed validators/storage. */
final class GenerationSelectionChecks {
    private GenerationSelectionChecks() { }
    static int verify(BalanceDocument initial) throws Exception {
        int checks = 0;
        var folder = Files.createTempDirectory("generation-selection");
        var path = folder.resolve(BalanceProfileStore.PROFILE_FILE);
        AtomicInteger collectors = new AtomicInteger(), environment = new AtomicInteger(1), questScans = new AtomicInteger(), lootScans = new AtomicInteger();
        ProfileGenerationSelection.Source<GeneratedBalanceService.Active> generation = () -> {
            collectors.incrementAndGet();
            questScans.incrementAndGet(); BalancePerformance.increment("quest_definition_normalizations");
            lootScans.incrementAndGet(); BalancePerformance.increment("loot_settings_captures");
            BalancePerformance.increment("competitive_capability_runs");
            BalancePerformance.increment("adaptive_calibration_runs");
            var body = JsonParser.parseString(initial.text()).getAsJsonObject();
            var production = com.mistaboom.essence_ascendance.valuation.EffectiveProductionTest.currentEffectiveFixture(environment.get());
            body.getAsJsonObject("metadata").add("fixtureEffectiveProduction", BalanceDocument.GSON.toJsonTree(production));
            body.getAsJsonObject("metadata").addProperty("fixtureGenerationInput", environment.get());
            body.getAsJsonObject("metadata").addProperty("fixtureLootRefreshTicks", 24000 * environment.get());
            var quest = new com.mistaboom.essence_ascendance.balance.quest.QuestEvidence(
                    java.util.List.of(new com.mistaboom.essence_ascendance.balance.quest.QuestEvidence.Quest("fixture", java.util.List.of(), 0,
                            true, "all_completed", false, 0, false, java.util.List.of(),
                            java.util.List.of(new com.mistaboom.essence_ascendance.balance.quest.QuestEvidence.Reward("reward", "item", "minecraft:diamond",
                                    environment.get(), 1, "", "team", "{}", java.util.List.of())), java.util.List.of())), "selection fixture", java.util.List.of());
            body.getAsJsonObject("metadata").add("fixtureQuestDefinitions", quest.diagnostics());
            return GeneratedBalanceService.decode(BalanceDocument.seal(body));
        };
        ProfileGenerationSelection.Source<GeneratedBalanceService.Active> saved = () -> GeneratedBalanceService.decode(BalanceProfileStore.read(path));
        try {
            check(ProfileGenerationSelection.generationRequired(path, false), "Missing profile chooses automatic generation"); checks++;
            GeneratedBalanceService.Active active;
            try (var operation = BalancePerformance.begin("full_generation", "synthetic_selection")) {
                active = ProfileGenerationSelection.select(ProfileGenerationSelection.generationRequired(path, false), saved, generation);
                BalanceProfileStore.replaceAndRetain(path, active.document()); operation.complete("fixture_validated_and_saved");
            }
            check(collectors.get() == 1, "Missing profile invokes fixture collector once"); checks++;
            byte[] first = Files.readAllBytes(path);
            environment.set(2);
            try (var operation = BalancePerformance.begin("saved_profile_load", "synthetic_selection")) {
                active = ProfileGenerationSelection.select(ProfileGenerationSelection.generationRequired(path, false), saved, generation);
                operation.complete("loaded_validated");
            }
            check(collectors.get() == 1, "Saved schema 2 load never calls evidence collection"); checks++;
            check(questScans.get() == 1, "Valid saved profile performs no quest definition scan"); checks++;
            check(lootScans.get() == 1, "Valid saved profile performs no loot definition/config analysis"); checks++;
            check(active.document().section("metadata").get("fixtureGenerationInput").getAsInt() == 1,
                    "Changed external fixture does not alter saved authority"); checks++;
            check(Arrays.equals(first, Files.readAllBytes(path)), "Saved profile reused without replacement"); checks++;
            var telemetry = BalancePerformance.lastSnapshot();
            for (String name : new String[]{"generation_snapshot_captures", "evidence_collection_runs", "production_graph_collections", "production_recipes_inspected", "conservation_solve_runs", "runtime_generation_runs", "quest_definition_normalizations", "quest_progression_rule_evaluations", "loot_settings_captures", "loot_tables_inspected", "loot_unsupported_runtime_modifiers", "competitive_capability_runs", "adaptive_calibration_runs"}) {
                check(!telemetry.counts().containsKey(name), "Saved load has no generation workload: " + name); checks++;
            }
            check(telemetry.counts().get("profile_reads") == 1, "Saved data read once"); checks++;
            try (var operation = BalancePerformance.begin("explicit_rebuild", "synthetic_selection")) {
                var candidate = ProfileGenerationSelection.select(ProfileGenerationSelection.generationRequired(path, true), saved, generation);
                check(candidate.document().section("metadata").get("fixtureGenerationInput").getAsInt() == 2,
                        "Explicit rebuild observes current fixture data"); checks++;
                check(candidate.document().section("metadata").getAsJsonObject("fixtureEffectiveProduction").getAsJsonArray("processes")
                        .get(0).getAsJsonObject().getAsJsonArray("outputs").get(0).getAsJsonObject().get("itemId").getAsString().equals("minecraft:diamond"),
                        "Explicit rebuild normalizes current same-ID effective recipe replacement"); checks++;
                BalanceProfileStore.replaceAndRetain(path, candidate.document()); active = candidate;
                operation.complete("fixture_validated_and_saved");
            }
            check(collectors.get() == 2, "Explicit rebuild invokes fixture generation once"); checks++;
            check(lootScans.get() == 2 && active.document().section("metadata").get("fixtureLootRefreshTicks").getAsInt() == 48000,
                    "Explicit rebuild captures changed loot/config; saved reuse did not"); checks++;
            check(questScans.get() == 2 && active.document().section("metadata").getAsJsonObject("fixtureQuestDefinitions").getAsJsonArray("quests")
                    .get(0).getAsJsonObject().getAsJsonArray("rewards").get(0).getAsJsonObject().get("count").getAsInt() == 2,
                    "Explicit rebuild captures changed quest definitions"); checks++;
            check(!Arrays.equals(first, Files.readAllBytes(path)), "Validated replacement saved"); checks++;
            byte[] current = Files.readAllBytes(path); var previous = active;
            var excludedQuest = ProfileGenerationSelection.select(ProfileGenerationSelection.generationRequired(path, true), saved,
                    () -> excludedProviderCandidate(initial, "ftbquests",
                            com.mistaboom.essence_ascendance.balance.quest.FtbQuestProvider.definitionReadiness("2101.1.36", true, true, false, true)));
            check(excludedQuest.document().section("metadata").getAsJsonObject("fixtureExcludedProvider")
                    .get("status").getAsString().equals("NOT_READY"), "Unready optional quest provider allows validated candidate selection"); checks++;
            check(excludedQuest.document().sectionHash("evidence").equals(initial.sectionHash("evidence")),
                    "Excluded quest provider contributes no invented rewards, gates or evidence"); checks++;
            check(active == previous && Arrays.equals(current, Files.readAllBytes(path)), "Candidate selection does not automatically replace previous authority"); checks++;
            var excludedLootr = ProfileGenerationSelection.select(true, saved, () -> excludedProviderCandidate(initial, "lootr",
                    com.mistaboom.essence_ascendance.valuation.LootrProvider.readiness("1.21.1-1.11.38.126", false)));
            check(excludedLootr.document().section("metadata").getAsJsonObject("fixtureExcludedProvider")
                    .get("status").getAsString().equals("NOT_READY"), "Unready optional Lootr provider allows validated candidate selection"); checks++;
            check(excludedLootr.document().sectionHash("economy").equals(initial.sectionHash("economy")),
                    "Excluded Lootr provider does not invent payout or source conservation data"); checks++;
            check(active == previous && Arrays.equals(current, Files.readAllBytes(path)), "Excluded-provider candidate remains uncommitted until explicit publication"); checks++;
            try {
                ProfileGenerationSelection.select(true, saved, () -> {
                    var invalid = JsonParser.parseString(initial.text()).getAsJsonObject();
                    invalid.getAsJsonObject("runtime").remove("skillCurves");
                    return GeneratedBalanceService.decode(BalanceDocument.seal(invalid));
                });
                throw new AssertionError("Invalid generated runtime accepted");
            } catch (RuntimeException expected) { checks++; }
            check(active == previous && Arrays.equals(current, Files.readAllBytes(path)), "Validation failure retains previous authority"); checks++;
            var old = JsonParser.parseString(initial.text()).getAsJsonObject(); old.addProperty("schema", 1);
            try {
                ProfileGenerationSelection.select(false, () -> GeneratedBalanceService.decode(BalanceDocument.parse(old.toString())), generation);
                throw new AssertionError("Unsupported schema accepted");
            } catch (IllegalArgumentException expected) { checks++; }
            check(collectors.get() == 2 && Arrays.equals(current, Files.readAllBytes(path)), "Unsupported old schema does not migrate, fall back, delete or generate"); checks++;
        } finally { Files.deleteIfExists(path); Files.delete(folder); }
        System.out.println("GenerationSelectionChecks: " + checks + " checks PASS; timings are synthetic selection/typed validation/storage only");
        return checks;
    }
    private static GeneratedBalanceService.Active excludedProviderCandidate(BalanceDocument initial, String id,
            com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness readiness) {
        check(!readiness.collectable(), "Fixture provider should be unavailable for collection");
        var body = JsonParser.parseString(initial.text()).getAsJsonObject();
        var diagnostic = new com.google.gson.JsonObject();
        diagnostic.addProperty("id", id); diagnostic.addProperty("status", readiness.status().name());
        diagnostic.addProperty("excluded", true); diagnostic.addProperty("detail", readiness.detail());
        body.getAsJsonObject("metadata").add("fixtureExcludedProvider", diagnostic);
        return GeneratedBalanceService.decode(BalanceDocument.seal(body));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

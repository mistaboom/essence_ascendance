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
        AtomicInteger collectors = new AtomicInteger(), environment = new AtomicInteger(1);
        ProfileGenerationSelection.Source<GeneratedBalanceService.Active> generation = () -> {
            collectors.incrementAndGet();
            var body = JsonParser.parseString(initial.text()).getAsJsonObject();
            var production = JsonParser.parseString("{\"processes\":[{\"outputs\":[{\"itemId\":\""
                    + (environment.get() == 1 ? "minecraft:gold_ingot" : "minecraft:diamond") + "\"}]}]}");
            body.getAsJsonObject("metadata").add("fixtureEffectiveProduction", BalanceDocument.GSON.toJsonTree(production));
            body.getAsJsonObject("metadata").addProperty("fixtureGenerationInput", environment.get());
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
                        "Explicit rebuild captures the current generation fixture"); checks++;
                BalanceProfileStore.replaceAndRetain(path, candidate.document()); active = candidate;
                operation.complete("fixture_validated_and_saved");
            }
            check(collectors.get() == 2, "Explicit rebuild invokes fixture generation once"); checks++;
            check(!Arrays.equals(first, Files.readAllBytes(path)), "Validated replacement saved"); checks++;
            byte[] current = Files.readAllBytes(path); var previous = active;
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
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

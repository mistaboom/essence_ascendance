package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.economy.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBuildScenarios;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Cross-section codec checks with real Minecraft bootstrap and generated runtime curves; no server or loader stubs. */
public final class GeneratedBalanceIntegrationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> error.printStackTrace(output(FileDescriptor.err)));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();

        BalanceSettings settings = BalanceSettings.defaults();
        BalanceOverrides overrides = BalanceOverrides.empty();
        PackEvidence evidence = evidence();
        EconomyProfile economy = economy(evidence, settings);
        RuntimeBalanceDefinition runtime = RuntimeBalanceDefinition.generate(evidence, economy, settings, overrides);
        BalanceDocument document = document(evidence, economy, runtime, settings, overrides);
        GeneratedBalanceService.Active decoded = GeneratedBalanceService.decode(BalanceDocument.parse(document.text()));

        check(document.text().equals(decoded.document().text()), "Complete document bytes changed on parse/decode");
        verifyWholeAccounting(document);
        check(runtime.toJson().equals(decoded.runtime().toJson()), "Resolved runtime changed across the document codec");
        check(BalanceDocument.GSON.toJsonTree(economy).equals(BalanceDocument.GSON.toJsonTree(decoded.economy())),
                "Economic values, fractional yields or processing policy changed on decode");
        check(evidence.equals(decoded.evidence()), "Typed evidence or provenance changed on decode");
        check(BalanceDocument.hash(BalanceDocument.GSON.toJsonTree(evidence)).equals(
                        BalanceDocument.hash(BalanceDocument.GSON.toJsonTree(decoded.evidence()))),
                "Evidence digest changed after immutable-model deserialization");
        check(evidence.capabilities().equals(decoded.evidence().capabilities()), "Transformative capabilities were lost on decode");
        check(economy.processingPolicy().conversionEfficiencyBasisPoints()
                        == decoded.runtime().config().infuserBalance().conversionEfficiencyBasisPoints(),
                "Runtime conversion differs from canonical economy policy");
        check(economy.processingPolicy().carrierExtractionEfficiencyBasisPoints()
                        == decoded.runtime().config().infuserBalance().carrierExtractionEfficiencyBasisPoints(),
                "Runtime extraction differs from canonical economy policy");

        RuntimeBalanceDefinition regenerated = RuntimeBalanceDefinition.generate(evidence, economy, settings, overrides);
        BalanceDocument second = document(evidence, economy, regenerated, settings, overrides);
        check(document.text().equals(second.text()), "Identical inputs produced different complete generated profiles");
        check(document.integrity().equals(second.integrity()), "Identical inputs produced different integrity values");
        JsonObject reversed = new JsonObject();
        JsonObject original = JsonParser.parseString(document.text()).getAsJsonObject();
        original.keySet().stream().sorted(java.util.Comparator.reverseOrder()).forEach(key -> reversed.add(key, original.get(key)));
        check(document.text().equals(BalanceDocument.seal(reversed).text()), "Top-level map insertion order affected serialized bytes");

        reject(document, root -> root.remove("runtime"), "Missing runtime section was accepted");
        reject(document, root -> root.getAsJsonObject("runtime").remove("configVersion"), "Missing runtime schema field was accepted");
        reject(document, root -> root.getAsJsonObject("runtime").remove("attunement"), "Old runtime silently installed new Attunement policy without rebuild");
        reject(document, root -> root.getAsJsonObject("runtime").addProperty("unregisteredRuntimeField", 1), "Unknown runtime field was accepted");
        reject(document, root -> root.getAsJsonObject("runtime").getAsJsonObject("statMaxBonuses")
                .remove(EssenceStats.MELEE_DAMAGE.id().toString()), "Missing registered stat value was accepted");
        reject(document, root -> root.getAsJsonObject("runtime").getAsJsonObject("balanceProfile")
                .getAsJsonObject("defaultTierCaps").addProperty(AscendanceTiers.DORMANT.id().toString(), -1),
                "Negative generated investment cap was accepted");
        reject(document, root -> root.getAsJsonObject("runtime").getAsJsonObject("infuser")
                .addProperty("conversionEfficiencyBasisPoints", 1234), "Independently changed runtime conversion policy was accepted");
        reject(document, root -> root.getAsJsonObject("runtime").getAsJsonObject("infuser")
                .addProperty("carrierExtractionEfficiencyBasisPoints", 2345), "Independently changed runtime extraction policy was accepted");
        reject(document, root -> root.getAsJsonObject("economy").remove("processingPolicy"), "Missing economy processing policy was accepted");
        reject(document, root -> root.getAsJsonObject("economy").addProperty("solverPasses", 0), "Unvalidated economy was accepted");
        reject(document, root -> root.getAsJsonObject("metadata").addProperty("evidenceDigest", "incorrect"), "Incorrect evidence digest was accepted");
        reject(document, root -> root.getAsJsonObject("metadata").remove("evidenceDigest"), "Missing evidence digest was accepted");
        reject(document, root -> root.getAsJsonObject("evidence").getAsJsonObject("resources")
                .getAsJsonObject("minecraft:diamond").addProperty("economicValue", 999), "Resealed evidence mutation escaped digest validation");
        reject(document, root -> root.getAsJsonObject("evidence").remove("capabilities"), "Missing evidence collection was accepted");
        verifyNumericReportExport(decoded);

        output(FileDescriptor.out).println("GeneratedBalanceIntegrationTest: " + checks
                + " complete-profile codec, digest, generation determinism, strict-runtime and economy-policy checks PASS");
    }

    private static void verifyWholeAccounting(BalanceDocument legacy) {
        // A compatible saved fractional profile remains loadable until an explicit
        // rebuild. A new whole-accounting profile must not relabel those fractions.
        reject(legacy, root -> root.getAsJsonObject("metadata")
                .addProperty("dissolutionAccounting", "whole_essence_v1"),
                "Whole-accounting marker accepted legacy fractional yields");
        reject(legacy, root -> root.getAsJsonObject("metadata")
                .addProperty("dissolutionAccounting", "unknown_policy"),
                "Unknown dissolution accounting policy was silently accepted");

        JsonObject wholeJson = JsonParser.parseString(legacy.text()).getAsJsonObject();
        wholeJson.getAsJsonObject("metadata").addProperty("dissolutionAccounting", "whole_essence_v1");
        // This codec fixture has no processes; individual quantization is safe
        // here only. Real generation uses the whole-unit conservation solver.
        for (var entry : wholeJson.getAsJsonObject("economy").getAsJsonObject("resources").entrySet()) {
            JsonObject resource = entry.getValue().getAsJsonObject();
            JsonObject routes = resource.getAsJsonObject("routedYields");
            long total = 0;
            for (String essence : List.copyOf(routes.keySet())) {
                long amount = Math.round(routes.get(essence).getAsDouble());
                routes.addProperty(essence, amount);
                total = Math.addExact(total, amount);
            }
            resource.getAsJsonObject("dissolutionYield").addProperty("microUnits",
                    Math.multiplyExact(total, FractionalAmountService.SCALE));
        }
        var whole = GeneratedBalanceService.decode(BalanceDocument.seal(wholeJson));
        EconomyGenerator.validateWhole(whole.economy());
        check(whole.document().section("metadata").get("dissolutionAccounting").getAsString()
                .equals("whole_essence_v1"), "Whole accounting mode did not survive the codec");

        Map<net.minecraft.resources.ResourceLocation, Map<com.mistaboom.essence_ascendance.essence.EssenceDefinition, Long>> mappings
                = new java.util.LinkedHashMap<>();
        whole.economy().resources().forEach((item, resource) -> {
            Map<com.mistaboom.essence_ascendance.essence.EssenceDefinition, Long> routes = new java.util.LinkedHashMap<>();
            resource.routedYields().forEach((id, amount) -> routes.put(EssenceRegistry.get(
                    net.minecraft.resources.ResourceLocation.parse(id)).orElseThrow(), FractionalAmountService.units(amount)));
            mappings.put(net.minecraft.resources.ResourceLocation.parse(item), routes);
        });
        com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry.installResolved(mappings,
                new com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry.LoadSummary(0,0,0,0,0,List.of()), () -> {});
        mappings.forEach((id, expected) -> {
            var stack = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));
            var actual = com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry.resolveDissolution(stack);
            check(expected.equals(actual), "Published gameplay mapping differs from exact generated yields");
            actual.values().forEach(micros -> {
                for (long count : new long[]{1, 2, 9, 64}) for (long carry : new long[]{0, 999_999}) {
                    var credit = FractionalAmountService.accumulate(micros, count, carry);
                    check(credit.wholeAmount() == (micros / FractionalAmountService.SCALE) * count,
                            "Whole item payout depends on batch size or old fractional carry");
                    check(credit.nextCarry() == carry, "Whole item payout created or spent hidden fractional credit");
                }
            });
        });
    }

    private static void verifyNumericReportExport(GeneratedBalanceService.Active decoded) throws Exception {
        JsonObject skills = decoded.document().section("skills");
        for (JsonElement value : skills.getAsJsonArray("semantics")) {
            for (String property : List.of("deliveries", "equipment", "actions")) {
                List<String> encoded = new ArrayList<>();
                value.getAsJsonObject().getAsJsonArray(property).forEach(element -> encoded.add(element.toString()));
                check(encoded.equals(encoded.stream().sorted().toList()),
                        "Unordered skill semantics depend on JVM Set iteration: " + property);
            }
        }
        check(skills.has("combinedBuilds") && skills.get("combinedBuilds").isJsonObject(),
                "Generated service omitted numeric combined-build analysis from the saved document");
        RuntimeBuildScenarios.Analysis analysis = BalanceDocument.GSON.fromJson(skills.get("combinedBuilds"), RuntimeBuildScenarios.Analysis.class);
        check(analysis != null && !analysis.cases().isEmpty(), "Saved numeric analysis contains no evaluated cases");
        analysis.requireSafe();
        check(Double.isFinite(analysis.attenuation()) && analysis.attenuation() >= 0 && analysis.attenuation() <= 1,
                "Numeric analysis attenuation is invalid");
        assertFinite(skills.get("combinedBuilds"));
        Map<String, RuntimeBuildScenarios.Case> cases = new TreeMap<>();
        long expectedRows = 0;
        for (var one : analysis.cases()) {
            check(cases.put(one.evaluation().id(), one) == null, "Numeric case identifier is duplicated");
            expectedRows += (long) one.evaluation().scenarios().size() * BuildComposition.Metric.values().length;
        }

        Path folder = Files.createTempDirectory("generated-balance-report-test-");
        try {
            BalanceReports.export(decoded, null, folder, 0);
            Path reports = folder.resolve("reports");
            for (String name : List.of("balance_report.md", "valuation.csv", "equipment.csv", "curves.csv",
                    "builds.csv", "combat_builds.csv", "invariants.csv", "valuation_sources.csv", "valuation_source_dependencies.csv",
                    "warnings.csv", "equipment_capabilities.csv", "build_skill_ranks.csv", "build_selections.csv", "build_category_pressure.csv",
                    "combat_assumptions.csv", "evidence.csv", "evidence_dependencies.csv", "runtime_parameters.csv", "generated_equipment.csv",
                    "attunement_targets.csv", "attunement_breadth.csv", "attunement_methods.csv", "attunement_calibration.csv",
                    "attunement_investment.csv", "attunement_repetition.csv", "attunement_reachability.csv"))
                check(Files.isRegularFile(reports.resolve(name)) && Files.size(reports.resolve(name)) > 0,
                        "Complete profile report export omitted " + name);
            for (String name : List.of("pack_metadata.json", "report_text.json"))
                check(Files.isRegularFile(folder.resolve("diagnostics").resolve(name)), "Missing detailed diagnostic " + name);
            var attunement = decoded.runtime().attunement();
            List<List<String>> targets = csv(Files.readString(reports.resolve("attunement_targets.csv")));
            check(targets.size() - 1 == attunement.chapters().values().stream().mapToInt(chapter -> chapter.categories().size()).sum(), "Attunement category report lost rows");
            List<List<String>> breadth = csv(Files.readString(reports.resolve("attunement_breadth.csv")));
            check(breadth.size() - 1 == attunement.chapters().size(), "Attunement breadth report lost chapters");
            List<List<String>> methods = csv(Files.readString(reports.resolve("attunement_methods.csv")));
            check(methods.size() - 1 == attunement.methods().size(), "Attunement methods report lost registered methods");
            List<List<String>> calibration = csv(Files.readString(reports.resolve("attunement_calibration.csv")));
            check(calibration.size() - 1 == attunement.chapters().size() * attunement.methods().size(), "Attunement rates report lost chapter/method rows");
            Map<String, com.mistaboom.essence_ascendance.attunement.AttunementProfile.Chapter> chapters = new TreeMap<>();
            attunement.chapters().values().forEach(chapter -> chapters.put(chapter.id(), chapter));
            for (var line : calibration.subList(1, calibration.size())) {
                var chapter = chapters.get(cell(line, calibration.getFirst(), "chapter_id"));
                var method = attunement.methods().get(cell(line, calibration.getFirst(), "activity_id"));
                check(chapter != null && method != null, "Attunement report has a dangling chapter or method join");
                check(chapter.categories().containsKey(cell(line, calibration.getFirst(), "category_id")), "Attunement report has a dangling category join");
                close(csvNumber(line, calibration.getFirst(), "contribution_per_unit"), chapter.activities().get(method.activityId()).contributionPerUnit(), "CSV calibration differs from authoritative rate");
            }
            try (var paths = Files.list(reports)) {
                for (Path path : paths.filter(value -> value.toString().endsWith(".csv")).toList()) {
                    List<List<String>> table = csv(Files.readString(path));
                    int width = table.getFirst().size();
                    check(table.getFirst().stream().distinct().count() == width, "Duplicate CSV headings in " + path);
                    for (List<String> line : table) {
                        check(line.size() == width, "Nonrectangular spreadsheet table " + path);
                        for (String field : line) check(field.length() <= SpreadsheetReports.MAX_CELL_CHARACTERS, "Oversized spreadsheet field " + path);
                    }
                }
            }
            List<List<String>> valuation = csv(Files.readString(reports.resolve("valuation.csv")));
            List<String> valueHeader = valuation.getFirst();
            check(valuation.size() == decoded.evidence().resources().size() + 1, "Valuation rows missing");
            for (List<String> row : valuation.subList(1, valuation.size())) {
                var resource = decoded.economy().resources().get(cell(row, valueHeader, "item_id"));
                double sum = 0;
                for (String essence : List.of("offense", "defense", "vitality", "mobility", "gathering", "utility")) {
                    double amount = csvNumber(row, valueHeader, essence); sum += amount;
                    close(amount, resource.routedYields().getOrDefault("essence_ascendance:" + essence, 0.0), "Essence column differs from payout");
                }
                close(sum, csvNumber(row, valueHeader, "total_essence"), "Numeric Essence columns do not sum to payout");
            }
            check(csv(Files.readString(reports.resolve("valuation_sources.csv"))).size() - 1
                            == decoded.evidence().resources().values().stream().mapToInt(value -> value.sources().size()).sum(),
                    "Normalized sources lost acquisition evidence");
            List<List<String>> assumptions = csv(Files.readString(reports.resolve("combat_assumptions.csv")));
            check(assumptions.size() - 1 == analysis.assumptions().size()
                            + analysis.cases().stream().mapToInt(one -> one.evaluation().assumptions().size()).sum(),
                    "Combat assumptions were lost or repeated across metrics");
            List<List<String>> rows = csv(Files.readString(reports.resolve("combat_builds.csv")));
            check(rows.size() > 1 && rows.size() - 1 == expectedRows,
                    "Combat CSV omitted numeric metrics or substituted a summary formula for evaluated cases");
            List<String> header = rows.getFirst();
            for (String required : List.of("case_id", "participation", "metric", "predicted", "allowed", "budget_fraction", "passes", "analysis_attenuation"))
                check(header.contains(required), "Combat CSV omitted required column " + required);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (List<String> row : rows.subList(1, rows.size())) {
                check(row.size() == header.size(), "Combat CSV quoting changed the column count");
                String id = cell(row, header, "case_id");
                var one = cases.get(id);
                check(one != null, "Combat CSV introduced a case absent from saved numeric analysis");
                var participation = BuildComposition.Participation.valueOf(cell(row, header, "participation"));
                var metric = BuildComposition.Metric.valueOf(cell(row, header, "metric"));
                check(seen.add(id + "/" + participation + "/" + metric), "Combat CSV duplicated a case metric");
                double predicted = one.evaluation().scenarios().get(participation).value(metric);
                double allowed = one.limits().value(metric);
                double fraction = allowed > 0 ? predicted / allowed : 0;
                close(csvNumber(row, header, "predicted"), predicted, "CSV prediction differs from saved resolved gameplay calculation");
                close(csvNumber(row, header, "allowed"), allowed, "CSV allowed value differs from saved per-case limit");
                close(csvNumber(row, header, "budget_fraction"), fraction, "CSV budget fraction differs from saved numeric result");
                close(csvNumber(row, header, "analysis_attenuation"), analysis.attenuation(), "CSV attenuation differs from saved analysis");
                check(cell(row, header, "passes").equals("true") && predicted <= allowed + 1e-9 * Math.max(1, allowed),
                        "Combat CSV marked an unsafe case as passed");
            }
            String report = Files.readString(reports.resolve("balance_report.md"));
            check(report.contains("Resolved numeric combat checks") && report.contains("combat_builds.csv"),
                    "Human report omitted the resolved numeric checks");
            check(!report.contains("This saved profile has no combined-build numeric analysis"),
                    "Human report fell back to illustrative policy formulas despite saved numeric cases");
            check(!report.matches("(?s).*\\b(?:NaN|Infinity)\\b.*"), "Human report contains unexpected nonfinite values");
        } finally {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void assertFinite(JsonElement value) {
        if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(entry -> assertFinite(entry.getValue()));
        else if (value.isJsonArray()) value.getAsJsonArray().forEach(GeneratedBalanceIntegrationTest::assertFinite);
        else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
            check(Double.isFinite(value.getAsDouble()), "Saved numeric build analysis contains a nonfinite number");
    }
    private static String cell(List<String> row, List<String> header, String column) { return row.get(header.indexOf(column)); }
    private static double csvNumber(List<String> row, List<String> header, String column) {
        double value = Double.parseDouble(cell(row, header, column));
        check(Double.isFinite(value), "Combat CSV contains a nonfinite " + column); return value;
    }
    private static void close(double actual, double expected, String failure) {
        check(Math.abs(actual - expected) <= Math.ulp(expected) * 4, failure);
    }
    static List<List<String>> csv(String text) {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (!quoted && c == ',') { row.add(cell.toString()); cell.setLength(0); }
            else if (!quoted && c == '\n') { row.add(cell.toString()); rows.add(List.copyOf(row)); row.clear(); cell.setLength(0); }
            else if (quoted || c != '\r') cell.append(c);
        }
        if (quoted) throw new AssertionError("Combat CSV contains unterminated quoted field");
        if (!row.isEmpty() || !cell.isEmpty()) { row.add(cell.toString()); rows.add(List.copyOf(row)); }
        return rows;
    }

    private static PackEvidence evidence() {
        Map<String, ResourceEvidence> resources = new TreeMap<>();
        String[] items = {"minecraft:iron_sword", "minecraft:iron_chestplate", "minecraft:bread",
                "minecraft:ender_pearl", "minecraft:diamond", "minecraft:book"};
        List<EvidenceFact> facts = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            String item = items[i]; ProgressionBand stage = ProgressionBand.at(i / 2);
            resources.put(item, new ResourceEvidence(item, stage, Availability.FINITE, Automation.NONE,
                    true, true, 80 + i * 40, .85,
                    List.of(new AcquisitionSource("fixture:" + i, AcquisitionSource.Kind.LOOT, stage,
                            1, false, false, 0, .85, List.of(), "Synthetic source using a registered vanilla item")), List.of()));
            facts.add(new EvidenceFact(EvidenceFact.Subject.ITEM, item, "attainable", EvidenceFact.Value.flag(true),
                    "integration_fixture", EvidenceFact.Origin.OBSERVED, .85, 0, stage, List.of(), "Synthetic acquisition evidence"));
        }
        List<EquipmentReference> equipment = List.of(
                new EquipmentReference("minecraft:wooden_sword", "mainhand_melee", ProgressionBand.ENTRY,
                        Map.of(CapabilityAxis.BURST_DAMAGE, 4.0, CapabilityAxis.ATTACK_RATE, 1.6,
                                CapabilityAxis.SUSTAINED_DAMAGE, 6.4, CapabilityAxis.DURABILITY, 59.0),
                        List.of(), true, true, .9, "Explicit synthetic entry weapon reference"),
                new EquipmentReference("minecraft:wooden_pickaxe", "mainhand_tool", ProgressionBand.ENTRY,
                        Map.of(CapabilityAxis.MINING_SPEED, 2.0, CapabilityAxis.HARVEST_LEVEL, 0.0,
                                CapabilityAxis.DURABILITY, 59.0),
                        List.of(), true, true, .9, "Explicit synthetic entry mining reference"),
                new EquipmentReference("minecraft:iron_sword", "mainhand_melee", ProgressionBand.MID,
                        Map.of(CapabilityAxis.BURST_DAMAGE, 6.0, CapabilityAxis.ATTACK_RATE, 1.6,
                                CapabilityAxis.SUSTAINED_DAMAGE, 9.6, CapabilityAxis.DURABILITY, 250.0),
                        List.of("enchantable:14"), true, true, .9, "Synthetic weapon reference"));
        List<EnemyReference> enemies = List.of(new EnemyReference("minecraft:zombie", EnemyReference.Encounter.ROUTINE,
                ProgressionBand.ENTRY, Map.of(CapabilityAxis.EFFECTIVE_HEALTH, 20.0, CapabilityAxis.BURST_DAMAGE, 3.0),
                true, .8, List.of("attack_cadence_unknown"), "Synthetic encounter reference"));
        List<CapabilityEvidence> capabilities = List.of(new CapabilityEvidence("fixture:gliding", ProgressionBand.APEX,
                Map.of(CapabilityAxis.GLIDING, 1.0), true, .8, "Synthetic separate capability axis"));
        return new PackEvidence(resources, equipment, enemies, RobustFrontiers.build(equipment), facts,
                List.of("Synthetic fixture: no live server resources were scanned"), Map.of("recipes", 0L), capabilities);
    }

    private static EconomyProfile economy(PackEvidence evidence, BalanceSettings settings) {
        Map<String, EconomyProfile.ResourceValue> resources = new TreeMap<>();
        var essences = EssenceRegistry.values().stream().sorted(java.util.Comparator.comparing(value -> value.id().toString())).toList();
        int i = 0;
        for (ResourceEvidence resource : evidence.resources().values()) {
            double amount = 8.25 + i * 3;
            resources.put(resource.itemId(), new EconomyProfile.ResourceValue(new EconomicValue(resource.economicValue()),
                    DissolutionYield.of(amount), Map.of(essences.get(i++).id().toString(), amount), List.of()));
        }
        return new EconomyProfile(resources, List.of(), List.of(), List.of(), 1, EconomyProcessingPolicy.derive(settings));
    }

    private static BalanceDocument document(PackEvidence evidence, EconomyProfile economy, RuntimeBalanceDefinition runtime,
                                             BalanceSettings settings, BalanceOverrides overrides) throws Exception {
        JsonObject metadata = new JsonObject();
        PackFingerprint environment = new PackFingerprint(BalanceDocument.hash("integration-fixture"),
                Map.of("minecraft", "1.21.1", "essence_ascendance", "integration-fixture"), "1.21.1", "bootstrap_test",
                List.of("vanilla"), BalanceDocument.hash("registered-items"), BalanceDocument.hash("synthetic-recipes"), BalanceDocument.hash("synthetic-tags"));
        metadata.add("environment", BalanceDocument.GSON.toJsonTree(environment));
        metadata.addProperty("settingsFingerprint", BalanceDocument.hash(BalanceDocument.GSON.toJsonTree(settings)));
        metadata.addProperty("overridesFingerprint", BalanceDocument.hash(BalanceDocument.GSON.toJsonTree(overrides)));
        metadata.addProperty("evidenceDigest", BalanceDocument.hash(BalanceDocument.GSON.toJsonTree(evidence)));
        metadata.addProperty("freshnessPolicy", "Explicit rebuild required after changed inputs or resources");
        JsonObject validation = new JsonObject();
        validation.addProperty("runtime", "passed"); validation.addProperty("economy", "passed");
        validation.addProperty("serialization", "passed"); validation.addProperty("liveGameplay", "not performed by integration test");
        JsonObject document = new JsonObject();
        document.add("metadata", metadata);
        document.add("settings", BalanceDocument.GSON.toJsonTree(settings));
        document.add("overrides", BalanceDocument.GSON.toJsonTree(overrides));
        document.add("evidence", BalanceDocument.GSON.toJsonTree(evidence));
        document.add("runtime", runtime.toJson());
        document.add("economy", BalanceDocument.GSON.toJsonTree(economy));
        // Exercise the service's real semantic/rank-projection serializer as part of the full envelope.
        var diagnostics = GeneratedBalanceService.class.getDeclaredMethod("skillDiagnostics", RuntimeBalanceDefinition.class);
        diagnostics.setAccessible(true);
        document.add("skills", (JsonObject) diagnostics.invoke(null, runtime));
        document.add("validation", validation);
        return BalanceDocument.seal(document);
    }

    private static void reject(BalanceDocument original, Consumer<JsonObject> mutate, String failure) {
        JsonObject changed = JsonParser.parseString(original.text()).getAsJsonObject(); mutate.accept(changed);
        try { GeneratedBalanceService.decode(BalanceDocument.seal(changed)); }
        catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError(failure);
    }
    private static void check(boolean value, String failure) { checks++; if (!value) throw new AssertionError(failure); }
    private static PrintStream output(FileDescriptor descriptor) { return new PrintStream(new FileOutputStream(descriptor)); }
}

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
import com.mistaboom.essence_ascendance.balance.runtime.LatentOreBalanceGenerator;
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
        // Exercise exact ore policy precedence in the existing generation/replay fixture.
        BalanceOverrides overrides = new BalanceOverrides(List.of(), Map.of(
                "/runtime/worldgen/overworld/veinSize", 2L,
                "/runtime/worldgen/overworld/veinsPerChunk", 3L));
        PackEvidence evidence = evidence();
        EconomyProfile economy = economy(evidence, settings);
        RuntimeBalanceDefinition runtime = RuntimeBalanceDefinition.generate(evidence, economy, settings, overrides);
        check(runtime.config().latentOreWorldgen().overworld().veinSize() == 2
                        && runtime.config().latentOreWorldgen().overworld().veinsPerChunk() == 3,
                "Exact runtime worldgen overrides must remain authoritative below the automatic size/attempt floors");
        BalanceDocument document = document(evidence, economy, runtime, settings, overrides);
        checks += GenerationSelectionChecks.verify(document);
        check(!document.section("metadata").has("environment") && !document.section("metadata").has("settingsFingerprint")
                && !document.section("metadata").has("overridesFingerprint"), "Generated profiles contain no pack fingerprint metadata");
        GeneratedBalanceService.Active decoded = GeneratedBalanceService.decode(BalanceDocument.parse(document.text()));

        JsonObject generatedEnvelope = JsonParser.parseString(document.text()).getAsJsonObject();
        generatedEnvelope.remove("evidence"); generatedEnvelope.remove("economy");
        BalanceDocument streamed = BalanceDocument.sealGeneratedOwned(generatedEnvelope, evidence, economy);
        check(streamed.text().equals(document.text()), "Streamed generation changed the complete canonical profile");
        GeneratedBalanceService.Active streamedDecoded = GeneratedBalanceService.decode(streamed);
        check(streamedDecoded.evidence().equals(decoded.evidence()), "Streamed generation changed typed evidence");
        check(streamedDecoded.economy().equals(decoded.economy()), "Streamed generation changed typed economy");
        check(streamedDecoded.runtime().toJson().equals(decoded.runtime().toJson()), "Streamed generation changed runtime policy");

        check(document.text().equals(decoded.document().text()), "Complete document bytes changed on parse/decode");
        verifyWholeAccounting(document);
        check(runtime.toJson().equals(decoded.runtime().toJson()), "Resolved runtime changed across the document codec");
        verifyGeneratedBonusViews(decoded.runtime());
        check(BalanceDocument.GSON.toJsonTree(economy).equals(BalanceDocument.GSON.toJsonTree(decoded.economy())),
                "Economic values, whole yields or processing policy changed on decode");
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
        reject(document, root -> root.getAsJsonObject("runtime").getAsJsonObject("balanceProfile").remove("bonusTracks"), "Old runtime silently installed resolved Bonus tracks without rebuild");
        reject(document, root -> root.getAsJsonObject("metadata").addProperty("generatorRevision", "obsolete"), "Obsolete generator revision did not require an explicit rebuild");
        reject(document, root -> bonusTrack(root, EssenceStats.MOVEMENT_SPEED.id().toString())
                .getAsJsonArray("checkpoints").get(1).getAsJsonObject().addProperty("segmentCost", 0),
                "Inconsistent Bonus segment cost escaped full-document validation");
        reject(document, root -> bonusTrack(root, EssenceStats.STEP_HEIGHT.id().toString())
                .add("snapPoints", new com.google.gson.JsonArray()), "A complete-state track without snap endpoints was accepted");
        reject(document, root -> bonusTrack(root, EssenceStats.STEP_HEIGHT.id().toString())
                .addProperty("completionTier", AscendanceTiers.TRANSCENDENT.id().toString()),
                "Declared completion tier disagreed with resolved checkpoints");
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
        verifySavedEvidenceRegeneration(document, settings, overrides);
        verifyMachineProfile(evidence, economy, settings);

        output(FileDescriptor.out).println("GeneratedBalanceIntegrationTest: " + checks
                + " complete-profile codec, digest, generation determinism, strict-runtime and economy-policy checks PASS");
    }

    private static void verifyMachineProfile(PackEvidence base, EconomyProfile original, BalanceSettings settings) throws Exception {
        var machine = com.mistaboom.essence_ascendance.valuation.EffectiveProductionTest.machineCase();
        Map<String, ResourceEvidence> resources = new TreeMap<>(base.resources()); resources.putAll(machine.evidence().resources());
        PackEvidence evidence = new PackEvidence(resources, base.equipment(), base.enemies(), base.frontiers(), base.facts(), base.warnings(), base.graphSummary(), base.capabilities());
        Map<String, EconomyProfile.ResourceValue> values = new TreeMap<>(original.resources()); values.putAll(machine.economy().resources());
        var economy = new EconomyProfile(values, machine.economy().invariants(), machine.economy().processes(), machine.economy().warnings(), machine.economy().solverPasses(), original.processingPolicy());
        var runtime = RuntimeBalanceDefinition.generate(evidence, economy, settings, BalanceOverrides.empty());
        var candidate = document(evidence, economy, runtime, settings, BalanceOverrides.empty());
        var decoded = GeneratedBalanceService.decode(BalanceDocument.parse(candidate.text()));
        check(decoded.evidence().resources().get("minecraft:barrier").reachable(), "Machine-only acquisition reaches complete generated profile");
        check(decoded.economy().resources().get("minecraft:barrier").dissolutionYield().microUnits() > 0, "Machine-only payout retained in validated profile");
        check(decoded.economy().processes().equals(machine.economy().processes()), "Shared machine inputs, setup and outputs survive profile validation");
        check(decoded.economy().invariants().getFirst().passed(), "Machine-only conservation result retained");
    }

    private static void verifyWholeAccounting(BalanceDocument document) {
        reject(document, root -> root.getAsJsonObject("metadata").remove("dissolutionAccounting"),
                "Missing dissolution accounting policy was accepted");
        reject(document, root -> root.getAsJsonObject("metadata")
                .addProperty("dissolutionAccounting", "unknown_policy"),
                "Unknown dissolution accounting policy was silently accepted");
        reject(document, root -> {
            JsonObject resource = root.getAsJsonObject("economy").getAsJsonObject("resources")
                    .entrySet().iterator().next().getValue().getAsJsonObject();
            JsonObject routes = resource.getAsJsonObject("routedYields");
            String route = routes.keySet().iterator().next();
            routes.addProperty(route, routes.get(route).getAsDouble() + 0.25);
            double total = routes.entrySet().stream().mapToDouble(entry -> entry.getValue().getAsDouble()).sum();
            resource.getAsJsonObject("dissolutionYield").addProperty("microUnits",
                    Math.round(total * FractionalAmountService.SCALE));
        }, "Fractional yields with internally consistent totals were accepted");
        var whole = GeneratedBalanceService.decode(document);
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
                            "Whole item payout depends on batch size or unrelated fractional carry");
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
            for (String name : List.of("balance_report.md", "valuation.csv", "equipment.csv", "curves.csv", "skill_rank_parameters.csv",
                    "builds.csv", "combat_builds.csv", "invariants.csv", "valuation_sources.csv", "valuation_source_dependencies.csv",
                    "warnings.csv", "equipment_capabilities.csv", "build_skill_ranks.csv", "build_selections.csv", "build_category_pressure.csv",
                    "combat_assumptions.csv", "evidence.csv", "evidence_dependencies.csv", "runtime_parameters.csv", "generated_equipment.csv",
                    "attunement_targets.csv", "attunement_breadth.csv", "attunement_methods.csv", "attunement_calibration.csv",
                    "attunement_investment.csv", "attunement_repetition.csv", "attunement_reachability.csv", "projectile_policy.csv",
                    "latent_ore_supply.csv", "latent_ore_worldgen.csv", "latent_ore_policy.csv"))
                check(Files.isRegularFile(reports.resolve(name)) && Files.size(reports.resolve(name)) > 0,
                        "Complete profile report export omitted " + name);
            for (String name : List.of("pack_metadata.json", "report_text.json"))
                check(Files.isRegularFile(folder.resolve("diagnostics").resolve(name)), "Missing detailed diagnostic " + name);
            verifyLatentOreReports(decoded, reports);
            var attunement = decoded.runtime().attunement();
            var nativeRanks = csv(Files.readString(reports.resolve("skill_rank_parameters.csv")));
            check(nativeRanks.size() - 1 == decoded.runtime().skillCurves().values().stream()
                    .flatMap(curve -> curve.ranks().stream()).mapToInt(rank -> rank.parameters().size()).sum(),
                    "Native rank export lost generated parameters");
            for (var row : nativeRanks.subList(1, nativeRanks.size())) {
                var rank = decoded.runtime().skillCurves().get(row.get(0)).ranks().get(Integer.parseInt(row.get(1))-1);
                close(Double.parseDouble(row.get(4)), rank.parameters().get(row.get(3)), "Report native rank value diverged from gameplay authority");
            }
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
                double allowed = one.limitFor(participation).value(metric);
                double fraction = allowed > 0 ? predicted / allowed : 0;
                close(csvNumber(row, header, "predicted"), predicted, "CSV prediction differs from saved resolved gameplay calculation");
                close(csvNumber(row, header, "allowed"), allowed, "CSV allowed value differs from saved per-case limit");
                close(csvNumber(row, header, "budget_fraction"), fraction, "CSV budget fraction differs from saved numeric result");
                close(csvNumber(row, header, "analysis_attenuation"), analysis.attenuation(), "CSV attenuation differs from saved analysis");
                check(cell(row, header, "passes").equals("true") && predicted <= allowed + 1e-9 * Math.max(1, allowed),
                        "Combat CSV marked an unsafe case as passed");
            }
            JsonObject generationReport = JsonParser.parseString(Files.readString(BalanceReportLayout.diagnostics(folder).resolve("generation_evidence.json"))).getAsJsonObject();
            check(generationReport.equals(decoded.document().section("metadata").getAsJsonObject("generation")), "Export preserves saved generation provenance without recollection");
            String report = Files.readString(reports.resolve("balance_report.md"));
            check(report.contains("fixture:provider") && report.contains("PARTIALLY_SUPPORTED"), "Report exposes saved provider readiness and support");
            check(report.contains(decoded.document().integrity()) && report.contains("/essence admin balance rebuild"),
                    "Human report retains integrity and explicit author rebuild guidance");
            check(!report.contains("Pack fingerprint:") && !report.contains("Compatibility signatures"),
                    "Human report must not require or report pack fingerprints");
            check(report.contains("Resolved numeric combat checks") && report.contains("combat_builds.csv"),
                    "Human report omitted the resolved numeric checks");
            check(report.contains("Latent Ore supply") && report.contains("latent_ore_supply.csv")
                            && report.contains("latent_ore_worldgen.csv"),
                    "Human report omitted generated Latent Ore evidence and final distributions");
            check(!report.contains("This saved profile has no combined-build numeric analysis"),
                    "Human report fell back to illustrative policy formulas despite saved numeric cases");
            check(!report.matches("(?s).*\\b(?:NaN|Infinity)\\b.*"), "Human report contains unexpected nonfinite values");
            check(report.contains("Projectile payload and control policy"), "Human report omitted projectile interaction policy");
            var policyRows = csv(Files.readString(reports.resolve("projectile_policy.csv")));
            check(policyRows.size() == skills.getAsJsonObject("projectilePolicy").size() + 1, "Projectile policy export lost contracts");
            check(report.contains("Guard mobility and counterplay policy"), "Human report omitted guard interaction policy");
            var guardRows = csv(Files.readString(reports.resolve("guard_policy.csv")));
            check(guardRows.size() == skills.getAsJsonObject("guardPolicy").size() + 1, "Guard policy export lost contracts");
            check(report.contains("Defensive posture and harmful-status policy"), "Human report omitted posture/status policy");
            var postureRows = csv(Files.readString(reports.resolve("posture_status_policy.csv")));
            check(postureRows.size() == skills.getAsJsonObject("postureStatusPolicy").size() + 1, "Posture/status policy lost contracts");
            var defenseRows = csv(Files.readString(reports.resolve("combat_defense_pressure.csv")));
            check(defenseRows.size() == analysis.cases().size() * BuildComposition.Participation.values().length + 1,
                    "Conditional defense pressure lacks full case/participation coverage");
            check(skills.getAsJsonObject("postureStatusPolicy").get("rank_policy").getAsString().contains("binary"),
                    "Pure State rank limitation was hidden");
            var savedExports = new TreeMap<Path, String>();
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) savedExports.put(path, Files.readString(path));
            }
            BalanceReports.export(decoded, null, folder, 0);
            for (var entry : savedExports.entrySet()) check(Files.readString(entry.getKey()).equals(entry.getValue()), "Repeated saved profile report changed " + entry.getKey());
        } finally {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyLatentOreReports(GeneratedBalanceService.Active decoded, Path reports) throws Exception {
        JsonObject diagnostics = decoded.document().section("validation").getAsJsonObject("latentOre");
        check(diagnostics != null, "Saved profile omitted Latent Ore diagnostics");
        var settings = decoded.runtime().config().latentOreWorldgen();
        check(diagnostics.getAsJsonObject("resolved").equals(RuntimeBalanceDefinition.worldgenJson(settings)),
                "Latent Ore diagnostics must preserve final overridden runtime settings");
        Map<String, JsonObject> expectedCategories = new TreeMap<>();
        for (JsonElement element : diagnostics.getAsJsonArray("categories")) {
            JsonObject category = element.getAsJsonObject();
            check(expectedCategories.put(category.get("essenceId").getAsString(), category) == null,
                    "Saved Latent Ore diagnostics duplicated an Essence category");
        }
        List<List<String>> supply = csv(Files.readString(reports.resolve("latent_ore_supply.csv")));
        List<String> supplyHeader = supply.getFirst();
        check(supplyHeader.equals(List.of("essence_id", "source_families", "effective_sources", "coverage",
                        "target_effective_sources", "attempt_multiplier", "early_conversion_fuel_available")),
                "Latent Ore supply export changed its normalized category shape");
        check(supply.size() == 7 && expectedCategories.size() == 6, "Latent Ore supply export must include all six categories");
        java.util.Set<String> categoryIds = new java.util.HashSet<>();
        for (List<String> row : supply.subList(1, supply.size())) {
            String id = cell(row, supplyHeader, "essence_id");
            check(categoryIds.add(id) && expectedCategories.containsKey(id), "Latent Ore supply has duplicate or unknown categories");
            JsonObject category = expectedCategories.get(id);
            close(csvNumber(row, supplyHeader, "source_families"), category.get("sourceFamilies").getAsDouble(),
                    "Latent Ore source counts differ from saved diagnostics");
            close(csvNumber(row, supplyHeader, "effective_sources"), category.get("effectiveSources").getAsDouble(),
                    "Latent Ore effective supply differs from saved diagnostics");
            close(csvNumber(row, supplyHeader, "coverage"), category.get("coverage").getAsDouble(),
                    "Latent Ore coverage differs from saved diagnostics");
            double multiplier = csvNumber(row, supplyHeader, "attempt_multiplier");
            check(multiplier >= 0.75 && multiplier <= 1.5, "Latent Ore report multiplier exceeds the scarcity policy bounds");
            close(multiplier, diagnostics.get("multiplier").getAsDouble(), "Latent Ore report changed the applied multiplier");
            close(csvNumber(row, supplyHeader, "target_effective_sources"), 4, "Latent Ore report lost its category coverage target");
            check(cell(row, supplyHeader, "early_conversion_fuel_available")
                            .equals(diagnostics.get("earlyConversionFuelAvailable").getAsString()),
                    "Latent Ore fuel availability differs from saved evidence diagnostics");
        }
        check(categoryIds.equals(EssenceRegistry.values().stream().map(value -> value.id().toString())
                        .collect(java.util.stream.Collectors.toSet())),
                "Latent Ore supply rows do not cover the registered Essence identities");

        List<List<String>> worldgen = csv(Files.readString(reports.resolve("latent_ore_worldgen.csv")));
        List<String> worldgenHeader = worldgen.getFirst();
        check(worldgenHeader.equals(List.of("dimension", "enabled", "vein_size", "attempts_per_chunk", "min_y", "max_y",
                        "air_discard", "selection")), "Latent Ore worldgen export changed its dimension row shape");
        check(worldgen.size() == 5, "Fixture must export three vanilla distributions and one automatic-dimension policy");
        java.util.Set<String> dimensions = new java.util.HashSet<>();
        for (List<String> row : worldgen.subList(1, worldgen.size())) {
            String id = cell(row, worldgenHeader, "dimension");
            check(dimensions.add(id), "Latent Ore worldgen export duplicated a dimension");
            check(!cell(row, worldgenHeader, "selection").isBlank(), "Latent Ore worldgen row omitted selection policy");
            if (id.equals("other supported dimensions")) {
                check(cell(row, worldgenHeader, "enabled").equals(Boolean.toString(settings.automaticDimensions())),
                        "Automatic-dimension enable policy differs from runtime");
                close(csvNumber(row, worldgenHeader, "vein_size"), settings.overworld().veinSize(), "Custom dimensions lost inherited vein size");
                close(csvNumber(row, worldgenHeader, "attempts_per_chunk"), settings.overworld().veinsPerChunk(), "Custom dimensions lost inherited attempts");
                check(cell(row, worldgenHeader, "min_y").equals("usable generator/dimension minimum")
                                && cell(row, worldgenHeader, "max_y").equals("usable generator/dimension maximum"),
                        "Custom dimension report must describe generator-specific heights without inventing fixed bounds");
            } else {
                var distribution = settings.distribution(net.minecraft.resources.ResourceLocation.parse(id), -2048, 2048);
                check(cell(row, worldgenHeader, "enabled").equals(Boolean.toString(distribution.enabled())), "Ore enable state differs from runtime");
                close(csvNumber(row, worldgenHeader, "vein_size"), distribution.veinSize(), "Ore report lost exact final vein size");
                close(csvNumber(row, worldgenHeader, "attempts_per_chunk"), distribution.veinsPerChunk(), "Ore report lost exact final attempts");
                close(csvNumber(row, worldgenHeader, "min_y"), distribution.minY(), "Ore report changed lower generation bound");
                close(csvNumber(row, worldgenHeader, "max_y"), distribution.maxY(), "Ore report changed upper generation bound");
                close(csvNumber(row, worldgenHeader, "air_discard"), distribution.discardChanceOnAirExposure(), "Ore report changed exposure policy");
            }
        }
        check(dimensions.equals(java.util.Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "other supported dimensions")),
                "Latent Ore worldgen report omitted a baseline or automatic policy");

        List<List<String>> policy = csv(Files.readString(reports.resolve("latent_ore_policy.csv")));
        check(policy.getFirst().equals(List.of("assumption")), "Latent Ore policy export must keep one assumption per row");
        check(policy.size() == diagnostics.getAsJsonArray("assumptions").size() + 1, "Latent Ore policy export lost assumptions");
        for (int index = 1; index < policy.size(); index++)
            check(policy.get(index).equals(List.of(diagnostics.getAsJsonArray("assumptions").get(index - 1).getAsString())),
                    "Latent Ore policy export changed a saved assumption");
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
            double amount = 8 + i * 3;
            resources.put(resource.itemId(), new EconomyProfile.ResourceValue(new EconomicValue(resource.economicValue()),
                    DissolutionYield.of(amount), Map.of(essences.get(i++).id().toString(), amount), List.of()));
        }
        return new EconomyProfile(resources, List.of(), List.of(), List.of(), 1, EconomyProcessingPolicy.derive(settings));
    }

    private static BalanceDocument document(PackEvidence evidence, EconomyProfile economy, RuntimeBalanceDefinition runtime,
                                             BalanceSettings settings, BalanceOverrides overrides) throws Exception {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("generatorRevision", GeneratedBalanceService.GENERATION_REVISION);
        metadata.addProperty("dissolutionAccounting", "whole_essence_v1");
        metadata.add("generation", JsonParser.parseString("""
                {"workloads":{"synthetic_items":2},"provenance":"synthetic generation fixture",
                 "providers":[{"id":"fixture:provider","family":"evidence","status":"PARTIALLY_SUPPORTED","version":"fixture-1",
                   "ready":true,"requiredEvidenceComplete":true,"facts":1,"sources":0,"minimumConfidence":0.7,
                   "detail":"Supported fixture facts complete; player state unsupported","probeNanos":0,"collectionNanos":0}]}
                """).getAsJsonObject());
        metadata.addProperty("evidenceDigest", BalanceDocument.hash(BalanceDocument.GSON.toJsonTree(evidence)));
        JsonObject validation = new JsonObject();
        validation.addProperty("runtime", "passed"); validation.addProperty("economy", "passed");
        validation.addProperty("serialization", "passed"); validation.addProperty("liveGameplay", "not performed by integration test");
        validation.add("bonusTracks", com.mistaboom.essence_ascendance.balance.runtime.BonusTrackGenerator.diagnostics(runtime));
        validation.add("latentOre", LatentOreBalanceGenerator.diagnostics(evidence, economy,
                settings.latentOre(), runtime.config().latentOreWorldgen()));
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

    private static JsonObject bonusTrack(JsonObject root, String statId) {
        return root.getAsJsonObject("runtime").getAsJsonObject("balanceProfile")
                .getAsJsonObject("bonusTracks").getAsJsonObject(statId);
    }

    private static void verifyGeneratedBonusViews(RuntimeBalanceDefinition runtime) {
        var profile = runtime.config().balanceProfile();
        for (var track : profile.bonusTracks().values()) {
            var view = com.mistaboom.essence_ascendance.network.BonusTrackSnapshot.from(track, profile);
            check(view.checkpoints().equals(track.checkpoints()) && view.snapPoints().equals(track.snapPoints())
                    && view.startTier().equals(track.startTier()) && view.completionTier().equals(track.completionTier()),
                    "Generated Bonus facts diverged in synchronized presentation");
            if (track.applicability() != com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition.Applicability.UNAVAILABLE)
                check(track.purchaseStyle() == com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition.PurchaseStyle.FUNDED_STATES
                                && track.snapPoints().size() == track.activeStateCount() + 1,
                        "Generated Bonus must synchronize every complete funded state");
            var geometry = new com.mistaboom.essence_ascendance.client.nexus.NexusBonusTrackLayout(view, 30, 230);
            for (int index = 0; index < track.checkpoints().size(); index++) {
                var point = track.checkpoints().get(index);
                check(Math.abs(view.tierPositions().get(index) - index / (double)(track.checkpoints().size() - 1)) < 1e-12,
                        "Display tier guide spacing inherited nonlinear effect or investment growth");
                if (point.purchasable()) check(geometry.yForEffect(point.effectFraction())
                                == geometry.yForPosition(view.tierPositions().get(index)),
                        "Generated independent effect checkpoint missed its synchronized tier guide");
            }
            if (track.applicability() == com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition.Applicability.AVAILABLE
                    && track.completionTier().equals(AscendanceTiers.TRANSCENDENT.id())) {
                var previous = track.checkpoint(AscendanceTiers.ASCENDANT.id());
                var last = track.checkpoint(AscendanceTiers.TRANSCENDENT.id());
                check(last.cumulativeCap() > previous.cumulativeCap() && last.segmentCost() > 0,
                        "Generated long Bonus has no Transcendent investment segment");
                check(geometry.yForEffect(1) == 30 && geometry.yForEffect(previous.effectFraction()) > 30,
                        "Generated Transcendent effect segment cannot occupy the top of its rail");
            }
        }
    }

    private static void verifySavedEvidenceRegeneration(BalanceDocument original, BalanceSettings settings, BalanceOverrides overrides) throws Exception {
        var inputs = new com.mistaboom.essence_ascendance.balance.config.BalanceInputs(settings, overrides);
        String originalText = original.text();
        // Current-format failed generation can replay its validated evidence;
        // the diagnostic envelope itself remains noninstallable.
        var evidenceSource = SavedEvidenceRegenerator.failureSnapshot(inputs,
                original.decodeSection("evidence", PackEvidence.class), original.decodeSection("economy", EconomyProfile.class),
                new IllegalArgumentException("fixture calibration failure"));
        try { GeneratedBalanceService.decode(evidenceSource); throw new AssertionError("Failed-generation diagnostic was installed"); }
        catch (RuntimeException expected) { checks++; }
        JsonObject obsolete = JsonParser.parseString(evidenceSource.text()).getAsJsonObject();
        obsolete.getAsJsonObject("metadata").addProperty("generatorRevision", "obsolete-test-generator");
        try { SavedEvidenceRegenerator.regenerate(BalanceDocument.seal(obsolete), inputs); throw new AssertionError("Obsolete generator evidence was replayed"); }
        catch (IllegalArgumentException expected) { checks++; }
        var regenerated = SavedEvidenceRegenerator.regenerate(evidenceSource, inputs);
        var repeated = SavedEvidenceRegenerator.regenerate(evidenceSource, inputs);
        check(regenerated.document().text().equals(repeated.document().text()), "Saved evidence regeneration is nondeterministic");
        check(original.text().equals(originalText), "Saved-evidence operation mutated original document");
        for (String section : List.of("settings", "overrides", "evidence", "economy"))
            check(regenerated.document().section(section).equals(original.section(section)), "Offline rebuild altered " + section);
        check(regenerated.runtime().toJson().get("attunement").equals(original.section("runtime").get("attunement")), "Offline rebuild changed Attunement");
        check(regenerated.document().section("metadata").get("generatorRevision").getAsString().equals(GeneratedBalanceService.GENERATION_REVISION), "Replay omitted current generator revision");
        check(!regenerated.document().section("metadata").has("diagnosticOnly"), "Successful replay retained its diagnostic-only marker");
        check(regenerated.document().section("skills").getAsJsonObject("projectilePolicy").has("attunement"), "Replay omitted projectile participation policy");
        check(regenerated.document().section("skills").getAsJsonObject("guardPolicy").has("attunement"), "Replay omitted guard participation policy");
        check(regenerated.runtime().config().skillEffects().guard() != null, "Replay did not regenerate the current guard schema");
        var changedInputs = new com.mistaboom.essence_ascendance.balance.config.BalanceInputs(settings,
                new BalanceOverrides(List.of(), Map.of("/runtime/effects/projectiles/control/redirectBudget", 0L)));
        try { SavedEvidenceRegenerator.regenerate(evidenceSource, changedInputs); throw new AssertionError("Changed inputs reused stale saved evidence"); }
        catch (IllegalArgumentException expected) { checks++; }
        var changedSettings = new com.mistaboom.essence_ascendance.balance.config.BalanceInputs(
                BalanceSettings.parse("[progression]\ncost_pressure=1.25", "changed-settings.toml"), overrides);
        try { SavedEvidenceRegenerator.regenerate(evidenceSource, changedSettings); throw new AssertionError("Changed settings reused incompatible saved evidence"); }
        catch (IllegalArgumentException expected) { checks++; }
        JsonObject corrupt = JsonParser.parseString(evidenceSource.text()).getAsJsonObject();
        corrupt.getAsJsonObject("metadata").addProperty("evidenceDigest", "incorrect");
        try { SavedEvidenceRegenerator.regenerate(BalanceDocument.seal(corrupt), inputs); throw new AssertionError("Bad evidence digest accepted by replay"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void check(boolean value, String failure) { checks++; if (!value) throw new AssertionError(failure); }
    private static PrintStream output(FileDescriptor descriptor) { return new PrintStream(new FileOutputStream(descriptor)); }
}

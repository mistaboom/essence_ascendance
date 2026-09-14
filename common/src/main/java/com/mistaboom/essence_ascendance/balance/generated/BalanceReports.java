package com.mistaboom.essence_ascendance.balance.generated;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.balance.engine.BuildComposition;
import com.mistaboom.essence_ascendance.balance.engine.EvidenceFact;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidenceCollector;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBuildScenarios;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministic diagnostic views of the installed profile; export never runs analysis. */
public final class BalanceReports {
    private BalanceReports() { }
    public static void export(GeneratedBalanceService.Active current, GeneratedBalanceService.Active previous,
                              Path folder, long generationMillis) throws IOException {
        JsonObject skills = current.document().section("skills");
        SpreadsheetReports tables = new SpreadsheetReports();
        valuation(tables, current);
        equipment(tables, current);
        curves(tables, current, skills);
        builds(tables, current, skills);
        combatBuilds(tables, skills);
        invariants(tables, current);
        evidence(tables, current);
        runtimeTables(tables, current);
        ascension(tables, current);
        attunement(tables, current.runtime().attunement());
        var projectileRules = tables.table("projectile_policy.csv", "contract", "rule");
        if (skills.has("projectilePolicy")) skills.getAsJsonObject("projectilePolicy").entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> projectileRules.row(entry.getKey(), entry.getValue().getAsString()));
        var guardRules = tables.table("guard_policy.csv", "contract", "rule");
        if (skills.has("guardPolicy")) skills.getAsJsonObject("guardPolicy").entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> guardRules.row(entry.getKey(), entry.getValue().getAsString()));
        var postureRules = tables.table("posture_status_policy.csv", "contract", "rule");
        if (skills.has("postureStatusPolicy")) skills.getAsJsonObject("postureStatusPolicy").entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> postureRules.row(entry.getKey(), entry.getValue().getAsString()));
        Path reports = BalanceReportLayout.reports(folder), diagnostics = BalanceReportLayout.diagnostics(folder);
        tables.write(reports, diagnostics);
        BalanceProfileStore.writeAtomically(reports.resolve("balance_report.md"), report(current, previous, generationMillis, skills));
        BalanceProfileStore.writeAtomically(diagnostics.resolve("pack_metadata.json"), BalanceDocument.GSON.toJson(current.document().section("metadata")) + "\n");
        BalanceReportLayout.finishExport(folder);
    }
    private static String report(GeneratedBalanceService.Active current, GeneratedBalanceService.Active previous, long millis, JsonObject skills) {
        var doc = current.document();
        var evidence = current.evidence();
        JsonObject environment = doc.section("metadata").getAsJsonObject("environment");
        StringBuilder out = new StringBuilder("# Essence Ascendance pack balance\n\n");
        out.append("Generator: `").append(BalanceDocument.GENERATOR).append("`  \nProfile integrity: `").append(doc.integrity())
                .append("`  \nPack fingerprint: `").append(environment.get("digest").getAsString()).append("`\n\n")
                .append("This report explains the saved server profile. Edit the commented TOML inputs, then run `/essence admin balance rebuild`. The generated JSON is inspection-only.\n\n")
                .append("## Environment and confidence boundary\n\n")
                .append("Minecraft ").append(environment.get("minecraft").getAsString()).append("; loader ")
                .append(environment.get("loader").getAsString()).append(".\n\n")
                .append("Direct observations, engine inferences, user overrides and policy decisions retain separate evidence origins. Generic data cannot reveal arbitrary scripted quest gates, dynamic item effects, machine outputs or boss phases. Unknown systems require a factual override or a typed optional provider.\n\n")
                .append("Cheap startup fingerprints compare mod versions, datapack selection, registry identifiers and recipe identifiers. Unchanged identifiers do not prove unchanged recipe contents, loot, scripts or third-party configuration. Explicitly rebuild after those edits.\n\n")
                .append("### Installed mods\n\n| Mod | Version |\n|---|---|\n");
        environment.getAsJsonObject("mods").entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> row(out, entry.getKey(), entry.getValue().getAsString()));
        out.append("\nEnabled datapacks: ").append(environment.get("datapacks")).append("\n\n## Applied policy settings\n\n| Setting | Value |\n|---|---|\n");
        doc.section("settings").entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> row(out, entry.getKey(), entry.getValue().toString()));
        out.append("\n## Overrides\n\n```json\n").append(BalanceDocument.GSON.toJson(doc.section("overrides"))).append("\n```\n\n")
                .append("## External progression references\n\n")
                .append("Ascendance's own outputs are excluded from the external reference population. Frontiers use reachable equipment and compatible slots; independent weapon families are alternatives. Sparse axes use declared vanilla policy seeds and remain visible in the warnings.\n\n")
                .append("| Band | Axis | Reference |\n|---|---|---:|\n");
        for (ProgressionBand band : ProgressionBand.values())
            for (var entry : evidence.frontiers().getOrDefault(band, Map.of()).entrySet())
                row(out, band.name(), entry.getKey().name(), number(entry.getValue()));
        out.append("\n### Enemy reference population\n\n| Enemy | Encounter | Band | Included | Confidence | Reason |\n|---|---|---|---|---:|---|\n");
        evidence.enemies().forEach(enemy -> row(out, enemy.entityId(), enemy.encounter().name(), enemy.stage().name(),
                Boolean.toString(enemy.included()), number(enemy.confidence()), enemy.reason() + " " + enemy.unknownMechanics()));
        out.append("\n### Attainable external capability references\n\n")
                .append("These are resolved capability axes on reachable, included equipment. A value of one for flight, gliding or another binary axis means the capability is present; it is not one point of damage. Unexposed behavior requires a provider or factual override.\n\n")
                .append("| Item | Slot | Band | Capability | Value | Confidence | Evidence boundary |\n|---|---|---|---|---:|---:|---|\n");
        Set<CapabilityAxis> capabilityAxes = Set.of(CapabilityAxis.FLIGHT, CapabilityAxis.GLIDING,
                CapabilityAxis.TELEPORTATION, CapabilityAxis.AREA_MINING, CapabilityAxis.VEIN_MINING,
                CapabilityAxis.BLOCKING, CapabilityAxis.ARMOR_PENETRATION, CapabilityAxis.SHIELD_INTERACTION,
                CapabilityAxis.AUTOMATION_INTERACTION, CapabilityAxis.CONVERSION, CapabilityAxis.THROUGHPUT,
                CapabilityAxis.CROP_YIELD, CapabilityAxis.DROP_YIELD, CapabilityAxis.TOOL_VERSATILITY);
        evidence.equipment().stream().filter(item -> item.included() && item.reachable()).forEach(item ->
                item.axes().entrySet().stream().filter(axis -> capabilityAxes.contains(axis.getKey()) && axis.getValue() > 0)
                        .sorted(Map.Entry.comparingByKey()).forEach(axis -> row(out, item.itemId(), item.slot(), item.stage().name(),
                                axis.getKey().name(), number(axis.getValue()), number(item.confidence()), item.capabilities().toString())));
        out.append("\n### External system capability claims\n\n")
                .append("Provider and source claims below preserve their origin and confidence. Claims are evidence; their presence alone does not prove a complete model of the external system.\n\n")
                .append("| Subject | Kind | Property | Value | Origin | Confidence | Reason |\n|---|---|---|---|---|---:|---|\n");
        evidence.facts().stream().filter(fact -> fact.subject() == EvidenceFact.Subject.CAPABILITY
                        || fact.subject() == EvidenceFact.Subject.SOURCE)
                .sorted(Comparator.comparing(EvidenceFact::key).thenComparing(EvidenceFact::provider))
                .forEach(fact -> row(out, fact.subjectId(), fact.subject().name(), fact.property(), evidenceValue(fact.value()),
                        fact.origin().name(), number(fact.confidence()), fact.reason()));
        out.append("\n## Resource supply and automation\n\n| Classification | Items |\n|---|---:|\n");
        Map<String, Long> classes = new TreeMap<>();
        evidence.resources().values().forEach(resource -> classes.merge(resource.availability() + " / " + resource.automation(), 1L, Long::sum));
        classes.forEach((key, value) -> row(out, key, value.toString()));
        out.append("\nEconomic value measures opportunity cost. Dissolution yield is the separately constrained spendable output. Automation and source pressure affect yield; conservation runs after all yield overrides. `valuation.csv` contains every resource and its routing.\n\n")
                .append("### Production graph\n\n").append(evidence.graphSummary()).append("\n\n")
                .append("Final conservation passes: ").append(current.economy().solverPasses()).append(". Checked production paths: ")
                .append(current.economy().invariants().size()).append(". All accepted paths pass their final production budget. Ordinary crafting shares only consumed material value across outputs and byproducts. Verified finite-stock trading additionally has a capped renewable-source allowance derived from uninflated proposed yields; the invariant explanation separates material and source credit. Workstations, stock and restocking are productive constraints, so profitable trading is permitted without increasing direct item proposals.\n\n")
                .append("## Generated policy parameters\n\n")
                .append("Allocation shares and reference values below explain the selected policy. Entries labeled illustrative summarize policy pressure; they are not measured DPS/EHP or a substitute for the resolved numeric scenario checks that follow.\n\n")
                .append("| Parameter | Value |\n|---|---:|\n");
        current.runtime().composition().forEach((key, value) -> row(out,
                key + (Set.of("combined_damage_multiplier", "combined_defense_multiplier").contains(key) ? " (illustrative only)" : ""), number(value)));
        out.append("\n## Player tier unlocks\n\n");
        out.append("Player-tier Ascension completes Category Attunement seals through confirmed gameplay. It consumes no Essence and requires no wallet balance, investment, skill, equipment family, or world milestone. Earned tiers are permanent. Current category Bonus allocations and actual historical paid skill receipts only accelerate future eligible activity.\n\n")
                .append("`attunement_breadth.csv` resolves increasing optional-category breadth; `attunement_targets.csv` joins it by chapter_id. `attunement_pacing.csv` compares raw and sustained single-source amounts per seal, with no investment or variety. Repetition measures reference work, never callback count; diversification restores efficiency. Reachability distinguishes registered base methods from stage-accessible acquisition estimates. Estimates do not impose gates, and opaque quests, world generators or scripted recipes require pack evidence. Exploration discoveries reset each chapter and remain optional.\n\n")
                .append("### Attunement calibration assumptions\n\n");
        current.runtime().attunement().assumptions().forEach(assumption -> out.append("- ").append(assumption).append('\n'));
        combatSummary(out, skills);
        out.append("\nThe complete runtime curve table is in `curves.csv`. Bonus investment uses the same generated concave interpolation in the server and Nexus previews. Cost caps are generated from progression and resource supply; exact refunds use paid receipts, not a later profile's prices.\n\n");
        skillSummary(out, current, skills);
        if (skills.has("projectilePolicy")) {
            out.append("## Projectile payload and control policy\n\nResolved tuning is exported as scalar exact paths in `runtime_parameters.csv`; the contracts below are also in `projectile_policy.csv`.\n\n| Contract | Rule |\n|---|---|\n");
            skills.getAsJsonObject("projectilePolicy").entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> row(out, entry.getKey(), entry.getValue().getAsString()));
            out.append('\n');
        }
        if (skills.has("guardPolicy")) {
            out.append("## Guard mobility and counterplay policy\n\nResolved tuning is exported in `runtime_parameters.csv` under `/runtime/effects/guard`; `guard_policy.csv` records ordering, lifecycle and attribution rules.\n\n| Contract | Rule |\n|---|---|\n");
            skills.getAsJsonObject("guardPolicy").entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> row(out, entry.getKey(), entry.getValue().getAsString()));
            out.append('\n');
        }
        out.append("## Exclusions, capabilities and uncertainty\n\n| Subject | Classification | Reason |\n|---|---|---|\n");
        evidence.equipment().stream().filter(item -> !item.included() || !item.capabilities().isEmpty())
                .forEach(item -> row(out, item.itemId(), item.included() ? "capability" : "excluded", item.reason() + " " + item.capabilities()));
        evidence.resources().values().stream().filter(resource -> !resource.reachable())
                .forEach(resource -> row(out, resource.itemId(), "unreachable", resource.warnings().toString()));
        out.append("\n### Low-confidence resources\n\n| Resource | Confidence | Stage |\n|---|---:|---|\n");
        double threshold = doc.section("settings").get("warningConfidence").getAsDouble();
        evidence.resources().values().stream().filter(resource -> resource.confidence() < threshold)
                .forEach(resource -> row(out, resource.itemId(), number(resource.confidence()), resource.stage().name()));
        out.append("\n### Warnings requiring review\n\n");
        List<String> warnings = new ArrayList<>(evidence.warnings()); warnings.addAll(current.economy().warnings());
        warnings.stream().sorted().distinct().forEach(warning -> out.append("- ").append(warning.replace('\n', ' ')).append('\n'));
        if (warnings.isEmpty()) out.append("No generation warnings.\n");
        out.append("\n### High-impact evidence\n\n| Origin | Provider | Subject | Property | Value | Confidence | Reason |\n|---|---|---|---|---|---:|---|\n");
        int limit = doc.section("settings").get("expandedDiagnostics").getAsBoolean() ? Integer.MAX_VALUE : 200;
        evidence.facts().stream().sorted(Comparator.comparingInt((com.mistaboom.essence_ascendance.balance.engine.EvidenceFact fact) -> fact.priority()).reversed()
                        .thenComparing(com.mistaboom.essence_ascendance.balance.engine.EvidenceFact::key)).limit(limit)
                .forEach(fact -> row(out, fact.origin().name(), fact.provider(), fact.subjectId(), fact.property(),
                        evidenceValue(fact.value()), number(fact.confidence()), fact.reason()));
        out.append("\nAll evidence is retained in generated JSON. This table is ")
                .append(limit == Integer.MAX_VALUE ? "expanded without a claim-count limit" : "limited to 200 claims")
                .append(" by diagnostics.expanded.\n\n")
                .append("## Validation and performance\n\n").append(doc.section("validation")).append("\n\n");
        Map<String, Long> phases = GeneratedBalanceService.phaseTimingsMillis();
        if (phases.isEmpty()) {
            out.append("No generation timings are available for this process. This export reads the saved profile; it does not rerun analysis. Preserve the original generation log when comparing performance.\n\n");
        } else {
            out.append("Last successful generation measured in this process: ").append(millis).append(" ms.\n\n")
                    .append("| Generation phase | Milliseconds |\n|---|---:|\n");
            phases.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(phase -> row(out, phase.getKey(), phase.getValue().toString()));
            out.append("\n| Evidence provider | Milliseconds |\n|---|---:|\n");
            PackEvidenceCollector.lastProviderTimingsMillis().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(provider -> row(out, provider.getKey(), provider.getValue().toString()));
        }
        out.append("\nTimings are rounded down to whole milliseconds; zero is a valid measured duration. They do not participate in deterministic balance values or profile integrity. Graph traversal is bounded; normal loads skip providers and solvers. Gameplay, loader transformation and multiplayer acceptance require the supplied local tests.\n\n")
                .append("## Comparison with previous active profile\n\n");
        if (previous == null) out.append("No previous active profile was supplied for this export.\n");
        else {
            long changed = current.economy().resources().entrySet().stream()
                    .filter(entry -> !entry.getValue().equals(previous.economy().resources().get(entry.getKey()))).count();
            long removed = previous.economy().resources().keySet().stream().filter(id -> !current.economy().resources().containsKey(id)).count();
            out.append("Previous integrity: `").append(previous.document().integrity()).append("`. Resource rows added/changed: ")
                    .append(changed).append("; removed: ").append(removed).append("; runtime changed: ")
                    .append(!current.runtime().toJson().equals(previous.runtime().toJson())).append(".\n");
        }
        out.append("\n## Export layout and calibration handoff\n\nCSV tables in reports/ contain one observation per row, with numeric essence categories in separate columns. Join resource details by item_id, and skill details by projection_id plus scenario_id. Equipment.csv describes external references; generated_equipment.csv describes Ascendance's saved tier baselines. Runtime_parameters.csv contains the complete saved runtime as scalar rows with exact JSON pointers. Combat assumptions are stored once in combat_assumptions.csv and join to combat_builds.csv by case_id; a blank case_id is an assumption shared by the entire analysis.\n\n")
                .append("A rare text field longer than a spreadsheet cell is replaced by a reference into diagnostics/report_text.json; no text is truncated. Potential formula text is escaped with a leading apostrophe. All original evidence and analysis remain in generated_balance.json. CSV values are inspection exports, never runtime inputs.\n\n")
                .append("Preserve generated_balance.json, the complete reports/ and diagnostics/ folders, both TOML inputs, latest.log, loader/version and the actual pack version. Correct factual analysis with providers/overrides and balance preferences with policy; do not alter generated JSON.\n");
        return out.toString();
    }
    private static void valuation(SpreadsheetReports tables, GeneratedBalanceService.Active current) {
        List<String> essenceIds = new ArrayList<>(List.of("essence_ascendance:offense", "essence_ascendance:defense",
                "essence_ascendance:vitality", "essence_ascendance:mobility", "essence_ascendance:gathering", "essence_ascendance:utility"));
        Set<String> extraEssences = new TreeSet<>();
        current.economy().resources().values().forEach(value -> extraEssences.addAll(value.routedYields().keySet()));
        extraEssences.removeAll(essenceIds); essenceIds.addAll(extraEssences);
        List<String> columns = new ArrayList<>(List.of("item_id", "mod", "stage", "availability", "automation", "reachable", "external",
                "confidence", "economic_value", "total_essence"));
        for (int i = 0; i < essenceIds.size(); i++) columns.add(i < 6 ? essenceIds.get(i).split(":", 2)[1] : "essence_" + essenceIds.get(i));
        columns.addAll(List.of("source_count", "override_count", "warning_count"));
        var out = tables.table("valuation.csv", columns.toArray(String[]::new));
        var sources = tables.table("valuation_sources.csv", "item_id", "source_index", "source_id", "kind", "stage", "output_per_event",
                "renewable", "rate_known", "units_per_second", "confidence", "dependency_count", "reason");
        var dependencies = tables.table("valuation_source_dependencies.csv", "item_id", "source_index", "source_id", "dependency_id");
        var warnings = tables.table("warnings.csv", "scope", "subject_id", "warning_index", "warning");
        // A pack can have tens of thousands of resources and many claims per item.
        // Index provenance once instead of rescanning the full evidence database per CSV row.
        Map<String, Long> overrideOrigins = new TreeMap<>();
        current.evidence().facts().stream().filter(fact -> fact.origin() == EvidenceFact.Origin.OVERRIDE
                        && (fact.subject() == EvidenceFact.Subject.ITEM || fact.subject() == EvidenceFact.Subject.EQUIPMENT))
                .forEach(fact -> overrideOrigins.merge(fact.subjectId(), 1L, Long::sum));
        current.evidence().resources().forEach((id, resource) -> {
            var value = current.economy().resources().get(id);
            List<String> cells = new ArrayList<>(List.of(id, id.split(":", 2)[0], resource.stage().name(), resource.availability().name(),
                    resource.automation().name(), Boolean.toString(resource.reachable()), Boolean.toString(resource.external()), number(resource.confidence()),
                    number(value == null ? resource.economicValue() : value.economicValue().amount()), number(value == null ? 0 : value.dissolutionYield().amount())));
            for (String essence : essenceIds) cells.add(number(value == null ? 0 : value.routedYields().getOrDefault(essence, 0.0)));
            cells.add(Integer.toString(resource.sources().size())); cells.add(Long.toString(overrideOrigins.getOrDefault(id, 0L)));
            cells.add(Integer.toString(resource.warnings().size() + (value == null ? 0 : value.warnings().size())));
            out.row(cells.toArray(String[]::new));
            for (int i = 0; i < resource.sources().size(); i++) {
                var source = resource.sources().get(i);
                sources.row(id, Integer.toString(i), source.id(), source.kind().name(), source.stage().name(), number(source.expectedOutput()),
                        Boolean.toString(source.renewable()), Boolean.toString(source.rateKnown()), source.rateKnown() ? number(source.unitsPerSecond()) : "",
                        number(source.confidence()), Integer.toString(source.dependencies().size()), source.reason());
                for (String dependency : source.dependencies()) dependencies.row(id, Integer.toString(i), source.id(), dependency);
            }
            warningRows(warnings, "resource_evidence", id, resource.warnings());
            if (value != null) warningRows(warnings, "resource_economy", id, value.warnings());
        });
        warningRows(warnings, "pack_evidence", "", current.evidence().warnings());
        warningRows(warnings, "pack_economy", "", current.economy().warnings());
    }
    private static void warningRows(SpreadsheetReports.Table out, String scope, String id, List<String> warnings) {
        for (int i = 0; i < warnings.size(); i++) out.row(scope, id, Integer.toString(i), warnings.get(i));
    }
    private static void equipment(SpreadsheetReports tables, GeneratedBalanceService.Active current) {
        List<String> columns = new ArrayList<>(List.of("item_id","slot","stage","reachable","included","confidence","reason","capability_count"));
        for (CapabilityAxis axis : CapabilityAxis.values()) columns.add(axis.name().toLowerCase(Locale.ROOT));
        var out = tables.table("equipment.csv", columns.toArray(String[]::new));
        var capabilities = tables.table("equipment_capabilities.csv", "item_id", "slot", "stage", "capability");
        current.evidence().equipment().forEach(item -> {
            List<String> values = new ArrayList<>(List.of(item.itemId(),item.slot(),item.stage().name(),Boolean.toString(item.reachable()),Boolean.toString(item.included()),number(item.confidence()),item.reason(),Integer.toString(item.capabilities().size())));
            for (CapabilityAxis axis : CapabilityAxis.values()) values.add(number(item.axes().getOrDefault(axis,0.0)));
            out.row(values.toArray(String[]::new));
            item.capabilities().forEach(capability -> capabilities.row(item.itemId(), item.slot(), item.stage().name(), capability));
        });
    }
    private static void curves(SpreadsheetReports tables, GeneratedBalanceService.Active current, JsonObject skills) {
        var out = tables.table("curves.csv", "system", "category", "registered_id", "tier", "rank", "investment", "cost", "resulting_power",
                "projection_id", "scenario_id", "axis", "catalog", "equipment_context", "sample_fraction", "status", "cost_scope");
        var config = current.runtime().config(); var profile = config.balanceProfile();
        for (var stat : EssenceStatRegistry.values()) for (var tier : AscendanceTierRegistry.values()) {
            long cap = profile.getInvestmentCap(tier,stat);
            for (double fraction : new double[]{0,0.1,0.25,0.5,1}) {
                long investment = (long)Math.floor(cap * fraction);
                double power = config.statMaxBonus(stat) * StatScalingService.progressionForInvestment(stat,investment,tier,profile);
                out.row("nexus",stat.category().name(),stat.id().toString(),tier.id().toString(),"",Long.toString(investment),Long.toString(investment),number(power),"","","","","",number(fraction),"passed","stat_investment");
            }
        }
        current.runtime().skillCurves().forEach((id, skill) -> skill.ranks().forEach(rank -> out.row("skill","",id,"",Integer.toString(rank.rank()),"",Long.toString(rank.cost()),number(rank.powerMultiplier()),"","","","","","",rank.rank() <= skill.maximumRank() ? "purchasable" : "future_projection","individual_rank")));
        for (var entry : projections(skills)) {
            JsonObject projection = entry.getValue().getAsJsonObject();
            for (JsonElement value : projection.getAsJsonArray("scenarios")) {
                JsonObject scenario = value.getAsJsonObject();
                String cost = activeRankCost(current, scenario).toString();
                String ranks = Integer.toString(rankCount(scenario));
                scenario.getAsJsonObject("axisPressure").entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(axis ->
                        out.row("skill_scenario", "", "", projectionTier(entry.getKey()), ranks, "", cost, number(axis.getValue().getAsDouble()),
                                entry.getKey(), scenario.get("id").getAsString(), axis.getKey(), projectionMode(entry.getKey()),
                                scenario.get("equipmentContext").getAsString(), "", "reachable_projection", "active_ranks_excluding_inactive_prerequisites"));
            }
        }
    }

    private static void skillSummary(StringBuilder out, GeneratedBalanceService.Active current, JsonObject skills) {
        out.append("### Skill ranks and reachable builds\n\n")
                .append("Current projections include implemented effects at purchasable ranks. Future-catalog projections also include planned effects and repeated-rank estimates; they do not enable those effects. All projections assume the declared skill milestones and required investments have been completed.\n\n")
                .append("Skills combine within actual equipment contexts and legal prerequisite, replacement and exclusive-choice relationships. Axis pressures are dimensionless modeling estimates, not measured damage. An envelope takes each axis's maximum across different scenarios; its columns are not one simultaneously achievable build.\n\n")
                .append("| Tier | Catalog | Scenarios | Evaluated component states | Largest axis-envelope values |\n|---|---|---:|---:|---|\n");
        for (var entry : projections(skills)) {
            JsonObject projection = entry.getValue().getAsJsonObject();
            row(out, projectionTier(entry.getKey()), projectionMode(entry.getKey()),
                    Integer.toString(projection.getAsJsonArray("scenarios").size()), projection.get("evaluatedComponentStates").getAsString(),
                    axisSummary(projection.getAsJsonObject("axisEnvelope"), 3));
        }
        out.append("\nRepresentative generalist scenarios are shown below. The full stable scenario/axis export is `builds.csv`; `curves.csv` also includes scenario-axis rows. Full semantics, selected choices, rank maps, envelopes, conservative bounds and solver explanations are retained under `skills` in generated_balance.json.\n\n")
                .append("| Tier | Catalog | Scenario | Contributing skills | Active ranks | Active-rank curve cost | Largest axis pressures | Confidence |\n|---|---|---|---:|---:|---:|---|---:|\n");
        for (var entry : projections(skills)) {
            JsonObject projection = entry.getValue().getAsJsonObject();
            List<JsonObject> scenarios = new ArrayList<>();
            projection.getAsJsonArray("scenarios").forEach(value -> scenarios.add(value.getAsJsonObject()));
            scenarios.stream().filter(scenario -> scenario.get("objective").getAsString().equals("broad_generalist"))
                    .sorted(Comparator.comparingInt((JsonObject scenario) -> representativeOrder(scenario.get("equipmentContext").getAsString()))
                            .thenComparing(scenario -> scenario.get("id").getAsString())).limit(3)
                    .forEach(scenario -> row(out, projectionTier(entry.getKey()), projectionMode(entry.getKey()), scenario.get("id").getAsString(),
                            Integer.toString(scenario.getAsJsonObject("contributingRanks").size()), Integer.toString(rankCount(scenario)),
                            activeRankCost(current, scenario).toString(), axisSummary(scenario.getAsJsonObject("axisPressure"), 3),
                            number(scenario.get("confidence").getAsDouble())));
        }
        out.append("\nActive-rank curve cost sums the saved prices of active skills through their projected ranks. It excludes inactive or replaced prerequisite purchases and all equipment/Nexus spending, so it is not a complete build-acquisition price. The exports keep this cost label explicit. The saved skill curves remain the price authority.\n\n");
    }

    private static void builds(SpreadsheetReports tables, GeneratedBalanceService.Active current, JsonObject skills) {
        List<String> columns = new ArrayList<>(List.of("projection_id", "tier", "catalog", "scenario_id", "equipment_context", "objective", "active_skill_count",
                "contributing_skill_count", "total_active_ranks", "active_rank_curve_cost_excluding_inactive_prerequisites",
                "confidence", "selected_loadout_count"));
        for (CapabilityAxis axis : CapabilityAxis.values()) {
            String name = axis.name().toLowerCase(Locale.ROOT);
            columns.add(name + "_pressure"); columns.add(name + "_capability_pressure");
            columns.add(name + "_envelope"); columns.add(name + "_conservative_upper_bound");
        }
        var out = tables.table("builds.csv", columns.toArray(String[]::new));
        var ranks = tables.table("build_skill_ranks.csv", "projection_id", "scenario_id", "skill_id", "active_rank", "contributing_rank");
        var selections = tables.table("build_selections.csv", "projection_id", "scenario_id", "selection_group", "selected_skill_id");
        var categories = tables.table("build_category_pressure.csv", "projection_id", "scenario_id", "essence_id", "axis", "pressure");
        for (var entry : projections(skills)) {
            JsonObject projection = entry.getValue().getAsJsonObject();
            for (JsonElement value : projection.getAsJsonArray("scenarios")) {
                JsonObject scenario = value.getAsJsonObject();
                JsonObject pressure = scenario.getAsJsonObject("axisPressure");
                JsonObject capability = scenario.getAsJsonObject("capabilityPressure");
                String cost = activeRankCost(current, scenario).toString();
                List<String> values = new ArrayList<>(List.of(entry.getKey(), projectionTier(entry.getKey()), projectionMode(entry.getKey()),
                        scenario.get("id").getAsString(), scenario.get("equipmentContext").getAsString(), scenario.get("objective").getAsString(),
                        Integer.toString(scenario.getAsJsonObject("activeRanks").size()), Integer.toString(scenario.getAsJsonObject("contributingRanks").size()),
                        Integer.toString(rankCount(scenario)), cost, number(scenario.get("confidence").getAsDouble()),
                        Integer.toString(scenario.getAsJsonObject("selections").size())));
                for (CapabilityAxis axis : CapabilityAxis.values()) {
                    values.add(jsonNumber(pressure, axis.name())); values.add(jsonNumber(capability, axis.name()));
                    values.add(jsonNumber(projection.getAsJsonObject("axisEnvelope"), axis.name()));
                    values.add(jsonNumber(projection.getAsJsonObject("conservativeUpperBounds"), axis.name()));
                }
                out.row(values.toArray(String[]::new));
                String scenarioId = scenario.get("id").getAsString();
                JsonObject active = scenario.getAsJsonObject("activeRanks"), contributing = scenario.getAsJsonObject("contributingRanks");
                Set<String> ids = new TreeSet<>(active.keySet()); ids.addAll(contributing.keySet());
                for (String id : ids) ranks.row(entry.getKey(), scenarioId, id, jsonNumber(active, id), jsonNumber(contributing, id));
                scenario.getAsJsonObject("selections").entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(selection ->
                        selections.row(entry.getKey(), scenarioId, selection.getKey(), selection.getValue().getAsString()));
                if (scenario.has("categoryPressure")) scenario.getAsJsonObject("categoryPressure").entrySet().stream()
                        .sorted(Map.Entry.comparingByKey()).forEach(category -> category.getValue().getAsJsonObject().entrySet().stream()
                                .sorted(Map.Entry.comparingByKey()).forEach(axis -> categories.row(entry.getKey(), scenarioId, category.getKey(),
                                        axis.getKey(), number(axis.getValue().getAsDouble()))));
            }
        }
    }

    private static RuntimeBuildScenarios.Analysis numericAnalysis(JsonObject skills) {
        if (!skills.has("combinedBuilds") || !skills.get("combinedBuilds").isJsonObject()) return null;
        return BalanceDocument.GSON.fromJson(skills.getAsJsonObject("combinedBuilds"), RuntimeBuildScenarios.Analysis.class);
    }

    private static void combatSummary(StringBuilder out, JsonObject skills) {
        if (skills.has("postureStatusPolicy")) {
            out.append("\n### Defensive posture and harmful-status policy\n\nResolved runtime values are in `runtime_parameters.csv`; `posture_status_policy.csv` records conditions, event order, binary rank limitations and missing status evidence. `combat_defense_pressure.csv` reports conditional avoidance, resistance, knockback and harmful-effect bounds separately from damage.\n\n| Contract | Rule |\n|---|---|\n");
            skills.getAsJsonObject("postureStatusPolicy").entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> row(out, entry.getKey(), entry.getValue().getAsString()));
        }
        out.append("\n### Resolved numeric combat checks\n\n");
        var analysis = numericAnalysis(skills);
        if (analysis == null) {
            out.append("This saved profile has no combined-build numeric analysis. Explicitly rebuild to produce it; the illustrative policy factors above do not establish numeric safety.\n\n");
            return;
        }
        out.append("These cases compose the actual saved equipment, Nexus and implemented-skill values under legal skill selections. They cover equipment-focused, bonus-focused, skill-focused, mixed, fully combined, offense-specialized and broad generalist participation. Each prediction is compared with its own generated limit. They are model checks under the assumptions below, not measured combat results.\n\n")
                .append("Minimum initial calibration: ").append(number(analysis.attenuation())).append("; independent Nexus/posture/rank recovery is recorded in runtime composition parameters. Named skill/equipment cases: ")
                .append(analysis.cases().size()).append(". Full per-case, per-participation, per-metric values are in `combat_builds.csv`; saved analysis is `skills.combinedBuilds`.\n\n")
                .append("The next table identifies the tightest budget in each participation/tier group. A ratio below or equal to one is within that case's allowed limit; all metrics and cases in the group contribute to its pass result.\n\n")
                .append("| Tier | Participation | Cases | Tightest metric | Predicted | Allowed | Budget used | All pass |\n|---|---|---:|---|---:|---:|---:|---|\n");
        for (var tier : AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(value -> value.order())).toList()) {
            List<RuntimeBuildScenarios.Case> cases = analysis.cases().stream().filter(row -> row.tier().equals(tier.id().toString())).toList();
            if (cases.isEmpty()) continue;
            for (BuildComposition.Participation participation : BuildComposition.Participation.values()) {
                NumericCheck worst = null;
                boolean allPass = true;
                int caseCount = 0;
                for (var one : cases) {
                    var metrics = one.evaluation().scenarios().get(participation);
                    if (metrics == null) continue;
                    caseCount++;
                    for (var metric : BuildComposition.Metric.values()) {
                        NumericCheck check = new NumericCheck(one, participation, metric, metrics.value(metric), one.limitFor(participation).value(metric));
                        allPass &= check.passed();
                        if (worst == null || check.ratio() > worst.ratio()) worst = check;
                    }
                }
                if (worst != null) row(out, tier.id().getPath(), participation.name(), Integer.toString(caseCount), worst.metric().name(),
                        number(worst.predicted()), number(worst.allowed()), ratioText(worst), Boolean.toString(allPass));
            }
        }
        out.append("\nFor each tier, the following six rows show one actual fully combined case: the case nearest any of its limits. These six metrics belong to that same named selection. Sustained/area damage and healing use HP/second; burst uses HP/attack; EHP and sustained health use incoming-damage-equivalent HP.\n\n")
                .append("| Tier | Skill selection | Metric | Predicted | Allowed | Pass |\n|---|---|---|---:|---:|---|\n");
        for (var tier : AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(value -> value.order())).toList()) {
            RuntimeBuildScenarios.Case representative = null;
            double largest = -1;
            for (var one : analysis.cases()) {
                if (!one.tier().equals(tier.id().toString())) continue;
                var metrics = one.evaluation().scenarios().get(BuildComposition.Participation.FULLY_COMBINED);
                if (metrics == null) continue;
                double usage = 0;
                for (var metric : BuildComposition.Metric.values())
                    usage = Math.max(usage, new NumericCheck(one, BuildComposition.Participation.FULLY_COMBINED,
                            metric, metrics.value(metric), one.limitFor(BuildComposition.Participation.FULLY_COMBINED).value(metric)).ratio());
                if (usage > largest) { largest = usage; representative = one; }
            }
            if (representative == null) continue;
            var metrics = representative.evaluation().scenarios().get(BuildComposition.Participation.FULLY_COMBINED);
            for (var metric : BuildComposition.Metric.values()) {
                NumericCheck check = new NumericCheck(representative, BuildComposition.Participation.FULLY_COMBINED,
                        metric, metrics.value(metric), representative.limitFor(BuildComposition.Participation.FULLY_COMBINED).value(metric));
                row(out, tier.id().getPath(), representative.skillSelection(), metric.name(), number(check.predicted()), number(check.allowed()), Boolean.toString(check.passed()));
            }
        }
        out.append("\nModel assumptions:\n\n");
        analysis.assumptions().forEach(assumption -> out.append("- ").append(assumption.replace('\n', ' ')).append('\n'));
        out.append("\nCase-specific incoming-hit and healing-window assumptions are preserved in `combat_assumptions.csv`, joined by case_id. The sustained-health calculation assumes healing is fully useful during its evaluation window; it is an upper bound, not guaranteed survival.\n\n");
    }

    private static void combatBuilds(SpreadsheetReports tables, JsonObject skills) {
        var out = tables.table("combat_builds.csv", "tier", "skill_selection", "case_id", "participation", "metric", "unit", "predicted", "allowed", "budget_fraction",
                "passes", "analysis_attenuation");
        var assumptions = tables.table("combat_assumptions.csv", "scope", "case_id", "assumption_index", "assumption");
        var pressure = tables.table("combat_defense_pressure.csv", "case_id", "participation", "peak_avoidance_fraction",
                "peak_damage_reduction_fraction", "frontal_knockback_immunity", "peak_harmful_status_prevention_fraction",
                "maximum_mirror_transfers_per_second", "mirror_maximum_duration_ticks", "mirror_maximum_amplifier");
        var analysis = numericAnalysis(skills);
        if (analysis == null) return;
        for (int i = 0; i < analysis.assumptions().size(); i++) assumptions.row("analysis", "", Integer.toString(i), analysis.assumptions().get(i));
        for (var one : analysis.cases().stream().sorted(Comparator.comparing(RuntimeBuildScenarios.Case::tier)
                .thenComparing(RuntimeBuildScenarios.Case::skillSelection)).toList()) {
            for (int i = 0; i < one.evaluation().assumptions().size(); i++)
                assumptions.row("case", one.evaluation().id(), Integer.toString(i), one.evaluation().assumptions().get(i));
            for (var participation : BuildComposition.Participation.values()) {
                var metrics = one.evaluation().scenarios().get(participation);
                if (metrics == null) continue;
                {
                    var p = one.defensivePressure().get(participation);
                    pressure.row(one.evaluation().id(), participation.name(), number(p.peakAvoidance()), number(p.peakDamageReduction()),
                            Boolean.toString(p.frontalKnockbackImmunity()), number(p.peakHarmfulStatusPrevention()),
                            number(p.maximumMirrorTransfersPerSecond()), Integer.toString(p.mirrorMaximumDurationTicks()),
                            Integer.toString(p.mirrorMaximumAmplifier()));
                }
                for (var metric : BuildComposition.Metric.values()) {
                    NumericCheck check = new NumericCheck(one, participation, metric, metrics.value(metric), one.limitFor(participation).value(metric));
                    out.row(one.tier(), one.skillSelection(), one.evaluation().id(), participation.name(), metric.name(), metricUnit(metric),
                            number(check.predicted()), number(check.allowed()), ratioText(check), Boolean.toString(check.passed()),
                            number(analysis.attenuation()));
                }
            }
        }
    }
    private static String metricUnit(BuildComposition.Metric metric) {
        return switch (metric) {
            case SUSTAINED_DAMAGE, AREA_DAMAGE, HEALING_PER_SECOND -> "hp_per_second";
            case BURST_DAMAGE -> "hp_per_attack";
            default -> "incoming_damage_equivalent_hp";
        };
    }

    private record NumericCheck(RuntimeBuildScenarios.Case source, BuildComposition.Participation participation,
                                BuildComposition.Metric metric, double predicted, double allowed) {
        double ratio() { return allowed > 0 ? predicted / allowed : predicted == 0 ? 0 : Double.POSITIVE_INFINITY; }
        boolean passed() { return predicted <= allowed + 1e-9 * Math.max(1, allowed); }
    }
    private static String ratioText(NumericCheck check) { return Double.isFinite(check.ratio()) ? number(check.ratio()) : "over_zero_limit"; }

    private static List<Map.Entry<String, JsonElement>> projections(JsonObject skills) {
        Map<String, Integer> order = new TreeMap<>();
        AscendanceTierRegistry.values().forEach(tier -> order.put(tier.id().getPath(), tier.order()));
        return skills.getAsJsonObject("reachableProjections").entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, JsonElement> entry) -> order.getOrDefault(projectionTier(entry.getKey()), Integer.MAX_VALUE))
                        .thenComparing(Map.Entry::getKey)).toList();
    }

    private static String projectionTier(String key) { return key.replaceFirst("_(current_effects|future_catalog)$", ""); }
    private static String projectionMode(String key) { return key.endsWith("_future_catalog") ? "future_catalog" : "current_effects"; }
    private static int representativeOrder(String context) {
        return switch (context) { case "melee_shield" -> 0; case "ranged" -> 1; case "gathering_tool" -> 2; default -> 3; };
    }
    private static int rankCount(JsonObject scenario) {
        return scenario.getAsJsonObject("activeRanks").entrySet().stream().mapToInt(entry -> entry.getValue().getAsInt()).sum();
    }
    private static java.math.BigInteger activeRankCost(GeneratedBalanceService.Active current, JsonObject scenario) {
        java.math.BigInteger total = java.math.BigInteger.ZERO;
        for (var entry : scenario.getAsJsonObject("activeRanks").entrySet()) {
            var curve = current.runtime().skillCurves().get(entry.getKey());
            if (curve == null) throw new IllegalArgumentException("Scenario references missing generated skill " + entry.getKey());
            int rank = entry.getValue().getAsInt();
            if (rank < 0 || rank > curve.ranks().size()) throw new IllegalArgumentException("Scenario rank exceeds generated curve " + entry.getKey());
            for (int i = 0; i < rank; i++) total = total.add(java.math.BigInteger.valueOf(curve.ranks().get(i).cost()));
        }
        return total;
    }
    private static String axisSummary(JsonObject axes, int count) {
        return axes.entrySet().stream().sorted(Comparator.comparingDouble((Map.Entry<String, JsonElement> entry) -> entry.getValue().getAsDouble())
                        .reversed().thenComparing(Map.Entry::getKey)).limit(count)
                .map(entry -> entry.getKey() + "=" + number(entry.getValue().getAsDouble())).reduce((a, b) -> a + "; " + b).orElse("No modeled pressure");
    }
    private static String jsonNumber(JsonObject object, String key) { return number(object.has(key) ? object.get(key).getAsDouble() : 0); }
    private static void evidence(SpreadsheetReports tables, GeneratedBalanceService.Active current) {
        var facts = tables.table("evidence.csv", "fact_id", "subject_kind", "subject_id", "property", "value_type", "value_number", "value_text", "value_flag",
                "origin", "provider", "priority", "stage", "confidence", "dependency_count", "reason");
        var dependencies = tables.table("evidence_dependencies.csv", "fact_id", "dependency_id");
        List<EvidenceFact> ordered = current.evidence().facts().stream().sorted(Comparator.comparing(EvidenceFact::key)
                .thenComparing(EvidenceFact::provider).thenComparing(fact -> fact.origin().name()).thenComparingInt(EvidenceFact::priority)).toList();
        for (int i = 0; i < ordered.size(); i++) {
            var fact = ordered.get(i); var value = fact.value(); String id = Integer.toString(i);
            facts.row(id, fact.subject().name(), fact.subjectId(), fact.property(), value.type().name(),
                    value.type() == EvidenceFact.ValueType.NUMBER ? number(value.number()) : "",
                    value.type() == EvidenceFact.ValueType.TEXT ? value.text() : "",
                    value.type() == EvidenceFact.ValueType.FLAG ? Boolean.toString(value.flag()) : "",
                    fact.origin().name(), fact.provider(), Integer.toString(fact.priority()), fact.stage() == null ? "" : fact.stage().name(),
                    number(fact.confidence()), Integer.toString(fact.dependencies().size()), fact.reason());
            fact.dependencies().forEach(dependency -> dependencies.row(id, dependency));
        }
    }
    private static void runtimeTables(SpreadsheetReports tables, GeneratedBalanceService.Active current) {
        JsonObject runtime = current.runtime().toJson();
        var parameters = tables.table("runtime_parameters.csv", "json_pointer", "section", "entry_key", "property", "value_type", "value_number", "value_text", "value_flag");
        flattenRuntime(parameters, new ArrayList<>(List.of("runtime")), runtime);
        JsonObject equipment = runtime.getAsJsonObject("equipment");
        Set<String> properties = new TreeSet<>();
        equipment.entrySet().forEach(entry -> properties.addAll(entry.getValue().getAsJsonObject().keySet()));
        List<String> columns = new ArrayList<>(List.of("tier_id", "tier_order"));
        properties.forEach(property -> columns.add(property.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT)));
        var generated = tables.table("generated_equipment.csv", columns.toArray(String[]::new));
        Map<String, Integer> orders = new TreeMap<>();
        AscendanceTierRegistry.values().forEach(tier -> orders.put(tier.id().toString(), tier.order()));
        equipment.entrySet().stream().sorted(Comparator.comparingInt(entry -> orders.getOrDefault(entry.getKey(), Integer.MAX_VALUE))).forEach(entry -> {
            JsonObject baseline = entry.getValue().getAsJsonObject();
            List<String> values = new ArrayList<>(List.of(entry.getKey(), Integer.toString(orders.getOrDefault(entry.getKey(), Integer.MAX_VALUE))));
            properties.forEach(property -> values.add(baseline.has(property) ? scalarNumber(baseline.get(property)) : ""));
            generated.row(values.toArray(String[]::new));
        });
    }
    private static String scalarNumber(JsonElement value) { return value.getAsBigDecimal().stripTrailingZeros().toPlainString(); }
    private static void ascension(SpreadsheetReports tables, GeneratedBalanceService.Active current) {
        var out = tables.table("ascension.csv", "transition_id", "from_tier", "to_tier", "qualification",
                "required_essence", "current_tier_budget", "next_tier_budget", "current_harvest_level", "next_harvest_level",
                "required_developed_stats", "required_categories", "world_gate", "runtime_json_pointer");
        var config = current.runtime().config();
        String mode = "category_attunement";
        config.advancements().values().stream().sorted(Comparator.comparingInt(a -> AscendanceTierRegistry.get(a.fromTierId()).orElseThrow().order()))
                .forEach(a -> {
                    var from = AscendanceTierRegistry.get(a.fromTierId()).orElseThrow();
                    var to = AscendanceTierRegistry.get(a.toTierId()).orElseThrow();
                    long cap = config.balanceProfile().getDefaultInvestmentCap(from);
                    out.row(a.id().toString(), from.id().toString(), to.id().toString(), mode,
                            "0", Long.toString(cap),
                            Long.toString(config.balanceProfile().getDefaultInvestmentCap(to)),
                            Integer.toString(config.equipmentBaselineConfig().baselineFor(from).harvestLevel()),
                            Integer.toString(config.equipmentBaselineConfig().baselineFor(to).harvestLevel()),
                            "0", Integer.toString(current.runtime().attunement().chapter(from.id().toString()).requiredCategories()),
                            a.worldRequirement() instanceof com.mistaboom.essence_ascendance.progression.MilestoneRequirement.Always ? "none" : "configured_milestones",
                            "/runtime/advancements/" + a.id().toString().replace("~", "~0").replace("/", "~1"));
                });
    }
    static void attunement(SpreadsheetReports tables, com.mistaboom.essence_ascendance.attunement.AttunementProfile profile) {
        var targets = tables.table("attunement_targets.csv", "chapter_id", "from_tier", "to_tier", "category_id", "target", "investment_reference");
        var breadth = tables.table("attunement_breadth.csv", "chapter_id", "from_tier", "to_tier", "registered_categories", "required_categories", "optional_categories");
        var methods = tables.table("attunement_methods.csv", "activity_id", "category_id", "calibration_family", "units", "label_key", "description_key", "base_game_accessible");
        var calibration = tables.table("attunement_calibration.csv", "chapter_id", "category_id", "activity_id", "units", "reference_units", "contribution_per_unit", "stage_accessible", "explanation");
        var pacing = tables.table("attunement_pacing.csv", "chapter_id", "category_id", "activity_id", "units", "stage_accessible", "raw_units_per_seal", "single_source_units_per_seal", "single_source_efficiency", "fresh_reference_percent", "floor_reference_percent");
        var investment = tables.table("attunement_investment.csv", "chapter_id", "category_id", "investment_reference", "curve_exponent", "fraction_cap", "maximum_added_multiplier", "base_multiplier", "maximum_multiplier", "bonus_allocations", "historical_owned_skill_receipts", "wallet_counts", "equipment_counts");
        var repetition = tables.table("attunement_repetition.csv", "policy_id", "repetition_floor", "maximum_variety_bonus", "history_window", "history_units", "variety_measure", "per_action_cap");
        var reachable = tables.table("attunement_reachability.csv", "chapter_id", "category_id", "base_methods", "positive_rate_methods", "stage_accessible_methods", "effective_accessible_methods", "scarcity_multiplier", "zero_investment_reachable", "repetition_floor_positive", "required_skill", "required_world_gate", "validation_scope");
        for (var method : profile.methods().values()) methods.row(method.activityId(), method.categoryId(), method.calibrationFamily(), method.units(),
                method.labelKey(), method.descriptionKey(), Boolean.toString(method.baseGameAccessible()));
        var policy = profile.policy();
        repetition.row("shared_recent_history", number(policy.repetitionFloor()), number(policy.varietyStrength()), Integer.toString(policy.historyWindow()), "generated_reference_outcomes", "weighted_source_diversity", "none");
        for (var chapter : profile.chapters().values()) {
            breadth.row(chapter.id(), chapter.fromTierId(), chapter.toTierId(), Integer.toString(chapter.categories().size()), Integer.toString(chapter.requiredCategories()), Integer.toString(chapter.categories().size() - chapter.requiredCategories()));
            for (var category : chapter.categories().values()) {
                targets.row(chapter.id(), chapter.fromTierId(), chapter.toTierId(), category.categoryId(), Long.toString(category.target()), Long.toString(category.investmentReference()));
                investment.row(chapter.id(), category.categoryId(), Long.toString(category.investmentReference()), "0.5", "1", number(policy.maximumAcceleration()), "1", number(1 + policy.maximumAcceleration()), "true", "true", "false", "false");
                long base = profile.methods().values().stream().filter(method -> method.categoryId().equals(category.categoryId()) && method.baseGameAccessible()).count();
                long positive = chapter.activities().values().stream().filter(rate -> rate.categoryId().equals(category.categoryId()) && rate.contributionPerUnit() > 0).count();
                long accessible = chapter.activities().values().stream().filter(rate -> rate.categoryId().equals(category.categoryId()) && rate.stageAccessible()).count();
                String referenceSuffix = ResourceLocation.parse(category.categoryId()).getPath() + "_" + ResourceLocation.parse(chapter.fromTierId()).getPath();
                reachable.row(chapter.id(), category.categoryId(), Long.toString(base), Long.toString(positive), Long.toString(accessible),
                        number(profile.references().getOrDefault("effective_methods_" + referenceSuffix, (double) accessible)),
                        number(profile.references().getOrDefault("accessibility_gain_" + referenceSuffix, 1.0)),
                        Boolean.toString(accessible > 0), Boolean.toString(policy.repetitionFloor() > 0), "none", "none", "acquisition_stage_and_opportunity_estimate; live_adapter_acceptance_separate");
            }
            for (var rate : chapter.activities().values()) {
                calibration.row(chapter.id(), rate.categoryId(), rate.activityId(), rate.units(), number(rate.referenceUnits()), number(rate.contributionPerUnit()), Boolean.toString(rate.stageAccessible()), rate.evidence());
                var category = chapter.categories().get(rate.categoryId());
                double raw = com.mistaboom.essence_ascendance.attunement.AttunementPacing.rawUnits(rate, category);
                double repeated = com.mistaboom.essence_ascendance.attunement.AttunementPacing.repeatedUnits(rate, category, policy);
                double referencePercent = rate.referenceUnits() / raw * 100;
                pacing.row(chapter.id(), rate.categoryId(), rate.activityId(), rate.units(), Boolean.toString(rate.stageAccessible()), number(raw), number(repeated), number(raw / repeated), number(referencePercent), number(referencePercent * policy.repetitionFloor()));
            }
        }
    }
    private static void flattenRuntime(SpreadsheetReports.Table out, List<String> path, JsonElement value) {
        if (value.isJsonObject()) value.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<String> child = new ArrayList<>(path); child.add(entry.getKey()); flattenRuntime(out, child, entry.getValue());
        });
        else if (value.isJsonArray()) {
            int index = 0;
            for (JsonElement element : value.getAsJsonArray()) {
                List<String> child = new ArrayList<>(path); child.add(Integer.toString(index++)); flattenRuntime(out, child, element);
            }
        } else {
            String pointer = "/" + path.stream().map(part -> part.replace("~", "~0").replace("/", "~1")).collect(java.util.stream.Collectors.joining("/"));
            String type = value.isJsonNull() ? "NULL" : value.getAsJsonPrimitive().isNumber() ? "NUMBER" : value.getAsJsonPrimitive().isBoolean() ? "FLAG" : "TEXT";
            out.row(pointer, path.size() > 1 ? path.get(1) : "", path.size() > 3 ? path.get(2) : "", path.getLast(), type,
                    type.equals("NUMBER") ? scalarNumber(value) : "", type.equals("TEXT") ? value.getAsString() : "", type.equals("FLAG") ? value.getAsString() : "");
        }
    }
    private static void invariants(SpreadsheetReports tables, GeneratedBalanceService.Active current) {
        var out = tables.table("invariants.csv", "tested_path","input_budget_material_and_source","expected_output","maximum_output","net_gain_over_budget","pass","explanation");
        current.economy().invariants().forEach(check -> out.row(check.path(),number(check.inputValue()),number(check.expectedOutputValue()),number(check.maximumOutputValue()),number(check.netGain()),Boolean.toString(check.passed()),check.explanation()));
    }
    private static String number(double value) { return SpreadsheetReports.number(value); }
    private static String evidenceValue(EvidenceFact.Value value) {
        return switch (value.type()) {
            case NUMBER -> number(value.number());
            case TEXT -> value.text();
            case FLAG -> Boolean.toString(value.flag());
        };
    }
    private static void row(StringBuilder out,String... values) {
        out.append('|'); for(String value:values)out.append(' ').append(value.replace("|","\\|").replace('\n',' ')).append(" |"); out.append('\n');
    }
}

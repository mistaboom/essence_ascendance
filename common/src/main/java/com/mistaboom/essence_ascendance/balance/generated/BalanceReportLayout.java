package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** Locations for derived exports; authoritative JSON and editable TOML stay in place. */
public final class BalanceReportLayout {
    private static final List<String> LEGACY_EXPORTS = List.of("balance_report.md", "valuation.csv",
            "equipment.csv", "curves.csv", "builds.csv", "combat_builds.csv", "invariants.csv", "pack_metadata.json");

    private BalanceReportLayout() { }

    public static Path reports(Path folder) { return folder.resolve("reports"); }
    public static Path diagnostics(Path folder) { return folder.resolve("diagnostics"); }

    /** Invoke only after every new export has been written successfully. */
    public static void finishExport(Path folder, String currentIntegrity) throws IOException {
        if (currentIntegrity == null || currentIntegrity.isBlank())
            throw new IllegalArgumentException("Current installed profile integrity is required for report export");
        BalanceProfileStore.writeAtomically(folder.resolve("README_REPORTS.txt"), """
                ESSENCE ASCENDANCE FILE GUIDE

                EDITABLE INPUTS
                  ../essence_ascendance.toml  Commented balance policy settings.
                  balance_overrides.toml     Commented factual and exact-value overrides.
                Edit the TOML inputs, then run /essence admin balance rebuild.
                Existing input files are never replaced with bundled defaults.

                SAVED RUNTIME CONFIGURATION
                  generated_balance.json     Authoritative validated server profile.
                Generated on first run or explicit rebuild, then loaded on later starts.
                It also retains complete evidence, acquisition sources, production paths,
                resolved values, skill selections, assumptions and validation results.
                Use TOML for changes; directly editing this JSON fails its integrity checks.

                PLAYER AND PACK-MAKER REPORTS
                  reports/                   Spreadsheet CSV tables and balance report.
                Open CSV files using UTF-8 and comma separation. The first row contains
                column names. Enable your spreadsheet application's filters on that row.
                Each Essence has a numeric column; related detail tables use named IDs.
                These are exports, not inputs. Editing a CSV does not change gameplay.

                REPORT TABLES AND LINKS
                  valuation.csv                   One item; total and per-Essence yields.
                  valuation_sources.csv           One acquisition source per item.
                  valuation_source_dependencies.csv  One required item/source per row.
                Sources and dependencies join by item_id plus source_index. Source rates
                are blank when unknown. output_per_event is an expected quantity; it is
                not a measured farm throughput. economic_value is opportunity value,
                while total_essence is the actual ordinary per-item dissolution yield.

                  equipment.csv                   External equipment reference axes.
                  equipment_capabilities.csv      One capability per reference item.
                  generated_equipment.csv         Ascendance baseline stats per tier.
                  ascension.csv                   Player chapters and harvest access.
                External equipment and its capabilities join by item_id, slot and stage.
                Generated armor/toughness columns represent the whole baseline set.
                Archetype tradeoffs and purchased effects are applied separately.
                Player unlocks complete Category Attunement seals through gameplay.
                Normalized Bonus development and actual paid owned-skill receipts accelerate
                future credit only. Wallet and ordinary equipment value do not count.
                No currency is consumed and completed player tiers remain permanent.

                  attunement_targets.csv          One category target per chapter.
                  attunement_breadth.csv           Required/optional seals per chapter.
                  attunement_methods.csv          Registered method metadata and units.
                  attunement_calibration.csv      Activity rates/references per chapter.
                  attunement_pacing.csv           Raw and repeated-source amounts per seal.
                  attunement_investment.csv       Category acceleration references/bounds.
                  attunement_repetition.csv       Shared repetition, variety, history, no-cap policy.
                  attunement_reachability.csv     Zero-investment registration/rate checks.
                Join chapter tables by chapter_id, category rows additionally by category_id,
                and method definitions/calibration by activity_id. Targets mix methods;
                no method is individually mandatory. Runtime parameters retain every
                assumption and calibration reference; live adapter validation is separate.

                  curves.csv                      Nexus investment and skill rank samples.
                  bonus_tracks.csv                Per-Bonus tiers, effects, exact segment costs and evidence.
                  runtime_parameters.csv          All scalar runtime values and exact paths.
                  projectile_policy.csv           Payload, ownership and defensive control contracts.
                  guard_policy.csv                Guard, perfect-block, reflection and counterattack contracts.
                  posture_status_policy.csv       Posture, harmful-status and binary rank contracts.
                  combat_defense_pressure.csv     Conditional avoidance, resistance, knockback and status bounds.
                Curve cost_scope distinguishes individual rank prices from active-rank
                build totals, which exclude inactive prerequisites and other investments.
                Runtime json_pointer values can be used in commented TOML exact overrides.

                  builds.csv                      One legal projected skill scenario.
                  build_skill_ranks.csv           Active/contributing ranks per skill.
                  build_selections.csv            Selected skills per choice group.
                  build_category_pressure.csv     Modeled axis pressure per Essence.
                These tables join by projection_id plus scenario_id. current_effects and
                future_catalog are separate projections; planned skills are not enabled.
                Pressure is dimensionless. Envelope columns combine maxima from different
                scenarios, not the stats of one simultaneously achievable build.

                  combat_builds.csv               Numeric combat results and allowed limits.
                  combat_assumptions.csv          Analysis-wide and per-case assumptions.
                Case assumptions join by case_id. Rows with scope=analysis apply to all
                cases. Metric units are explicit; these are model checks, not combat logs.

                  invariants.csv                  Material and bounded-source budget checks.
                  warnings.csv                    One warning per subject and scope.
                  evidence.csv                    Every fact, its typed value and origin.
                  evidence_dependencies.csv       One dependency per fact_id.
                Filter evidence by origin=OVERRIDE and subject_id to inspect corrections.
                Source indices and fact IDs identify rows within the current export.

                TROUBLESHOOTING
                  diagnostics/               Machine-readable export metadata and details.
                  diagnostics/bonus_tracks.json  Structured Bonus resolution, provenance and uncertainty.
                  diagnostics/generation_comparison.json  Replay comparison for this profile, if available.
                  diagnostics/legacy_reports/ Previous exports and stale comparisons, archived safely.
                Long text is retained in diagnostics instead of oversized spreadsheet cells.
                Legacy reports are historical; use reports/ for the current export.
                Comparisons with a different currentIntegrity are preserved only in the archive.
                To send diagnostics, include this entire folder, ../essence_ascendance.toml
                and logs/latest.log. The saved profile contains the full analysis evidence.

                REFRESH
                  /essence admin balance export   Refresh reports from the saved profile.
                  /essence admin balance rebuild  Recalculate and install a new profile.
                  /essence debug balance validate Check the installed profile.
                Exporting does not rerun analysis or change gameplay values.
                """);
        archiveLegacyExports(folder, currentIntegrity);
    }

    private static void archiveLegacyExports(Path folder, String currentIntegrity) throws IOException {
        List<Path> oldFiles = new ArrayList<>(LEGACY_EXPORTS.stream().map(folder::resolve)
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList());
        Path comparison = diagnostics(folder).resolve("generation_comparison.json");
        if (hasStaleComparison(comparison, currentIntegrity)) oldFiles.add(comparison);
        if (oldFiles.isEmpty()) return;
        Path parent = diagnostics(folder).resolve("legacy_reports");
        Files.createDirectories(parent);
        Path archive;
        for (int sequence = 1; ; sequence = Math.incrementExact(sequence)) {
            archive = parent.resolve("export-" + sequence);
            try { Files.createDirectory(archive); break; }
            catch (FileAlreadyExistsException occupied) { /* Preserve every existing archive. */ }
        }
        // No replacement or recursive traversal: only these exact, generated files move.
        for (Path source : oldFiles) Files.move(source, archive.resolve(source.getFileName()));
    }

    private static boolean hasStaleComparison(Path comparison, String currentIntegrity) throws IOException {
        BasicFileAttributes attributes;
        try { attributes = Files.readAttributes(comparison, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException absent) { return false; }
        if (!attributes.isRegularFile())
            throw new IOException("Generation comparison is not a regular file: " + comparison);
        // Parse the whole document strictly before trusting its identity. Unreadable or malformed
        // comparisons are historical too; moving the original preserves every byte for inspection.
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(comparison))) {
            reader.setLenient(false);
            reader.beginObject();
            String identity = null;
            boolean seenIdentity = false, duplicateIdentity = false;
            while (reader.hasNext()) {
                if (reader.nextName().equals("currentIntegrity")) {
                    duplicateIdentity |= seenIdentity;
                    seenIdentity = true;
                    if (reader.peek() == JsonToken.STRING) identity = reader.nextString();
                    else reader.skipValue();
                } else reader.skipValue();
            }
            reader.endObject();
            return reader.peek() != JsonToken.END_DOCUMENT || duplicateIdentity || !currentIntegrity.equals(identity);
        } catch (IOException | IllegalStateException unreadableOrMalformed) {
            return true;
        }
    }
}

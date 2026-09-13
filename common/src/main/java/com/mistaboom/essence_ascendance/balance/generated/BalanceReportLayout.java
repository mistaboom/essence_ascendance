package com.mistaboom.essence_ascendance.balance.generated;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/** Locations for derived exports; authoritative JSON and editable TOML stay in place. */
public final class BalanceReportLayout {
    private static final List<String> LEGACY_EXPORTS = List.of("balance_report.md", "valuation.csv",
            "equipment.csv", "curves.csv", "builds.csv", "combat_builds.csv", "invariants.csv", "pack_metadata.json");

    private BalanceReportLayout() { }

    public static Path reports(Path folder) { return folder.resolve("reports"); }
    public static Path diagnostics(Path folder) { return folder.resolve("diagnostics"); }

    /** Invoke only after every new export has been written successfully. */
    public static void finishExport(Path folder) throws IOException {
        BalanceProfileStore.writeAtomically(folder.resolve("README_REPORTS.txt"), """
                ESSENCE ASCENDANCE FILE GUIDE

                EDITABLE INPUTS
                  ../essence_ascendance.toml  Commented balance policy settings.
                  balance_overrides.toml     Commented factual and exact-value overrides.
                Edit the TOML inputs, then run /essence debug balance rebuild.
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
                  ascension.csv                   Player tier thresholds and harvest access.
                External equipment and its capabilities join by item_id, slot and stage.
                Generated armor/toughness columns represent the whole baseline set.
                Archetype tradeoffs and purchased effects are applied separately.
                New generated player unlocks count available Essence, allocated Bonuses
                and actual paid skill receipts. Any category can qualify. Moving Essence
                among these pools does not create progress; ascension adds no extra fee.
                Unchanneled Crucible reservoirs and consumed gear/crafting costs do not
                count. Completed player tier unlocks remain permanent after spending.

                  curves.csv                      Nexus investment and skill rank samples.
                  runtime_parameters.csv          All scalar runtime values and exact paths.
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
                  diagnostics/legacy_reports/ Previous root exports, if any, archived safely.
                Long text is retained in diagnostics instead of oversized spreadsheet cells.
                Legacy reports are historical; use reports/ for the current export.
                To send diagnostics, include this entire folder, ../essence_ascendance.toml
                and logs/latest.log. The saved profile contains the full analysis evidence.

                REFRESH
                  /essence debug balance export   Refresh reports from the saved profile.
                  /essence debug balance rebuild  Recalculate and install a new profile.
                  /essence debug balance validate Check the installed profile.
                Exporting does not rerun analysis or change gameplay values.
                """);
        archiveLegacyExports(folder);
    }

    private static void archiveLegacyExports(Path folder) throws IOException {
        List<Path> oldFiles = LEGACY_EXPORTS.stream().map(folder::resolve)
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList();
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
}

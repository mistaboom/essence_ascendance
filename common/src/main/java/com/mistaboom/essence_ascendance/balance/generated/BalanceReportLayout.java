package com.mistaboom.essence_ascendance.balance.generated;

import java.io.IOException;
import java.nio.file.Path;

/** Current derived export locations; authoritative JSON and editable TOML stay in place. */
public final class BalanceReportLayout {
    private BalanceReportLayout() { }
    public static Path reports(Path folder) { return folder.resolve("reports"); }
    public static Path diagnostics(Path folder) { return folder.resolve("diagnostics"); }

    /** Invoke only after every current export has been written successfully. */
    public static void finishExport(Path folder, String currentIntegrity) throws IOException {
        if (currentIntegrity == null || currentIntegrity.isBlank())
            throw new IllegalArgumentException("Current installed profile integrity is required for report export");
        BalanceProfileStore.writeAtomically(folder.resolve("README_REPORTS.txt"), """
                ESSENCE ASCENDANCE FILE GUIDE

                EDITABLE INPUTS
                  ../essence_ascendance.toml  Global power, effort, activity and ore policy.
                  skill_settings.toml       Skill unlock tiers, ranks and native stat endpoints.
                  bonus_settings.toml       Bonus active tiers and effect bounds.
                  balance_overrides.toml     Generic facts and exact value overrides.
                Edit the TOML inputs, then run /essence admin balance rebuild.
                Missing Skill and Bonus settings are generated only after a validated
                profile is durably published. Existing inputs are never overwritten.
                Omitted entries use automatic values and authored Skill unlock tiers.
                Native limits and Skill prerequisites still apply to configured values.

                SAVED RUNTIME CONFIGURATION
                  generated_balance.json.gz  Authoritative validated server profile.
                Generated on first run or explicit rebuild, then loaded on later starts.
                It retains item and recipe estimates, equipment measurements, resolved
                values, configured Skill ranks and effects, assumptions and validation results.
                Editing this JSON directly fails its integrity checks.

                CURRENT REPORTS
                  reports/                   CSV tables and balance_report.md.
                  valuation.csv              Estimated values and final per-Essence payouts.
                  equipment.csv              Measured equipment axes and catalog strength bands.
                  generated_equipment.csv    Ascendance equipment baselines per tier.
                  bonus_tracks.csv           Published Bonus states, effects and prices.
                  skill_rank_parameters.csv  Published skill ranks and native stat values.
                  runtime_parameters.csv     Runtime values and exact override paths.
                  latent_ore_supply.csv      Configured placement policy and minimums.
                  latent_ore_worldgen.csv    Final distributions and exact dimension overrides.
                  latent_ore_policy.csv      Placement assumptions.
                  warnings.csv               Complete warnings by scope and subject.
                Open CSV files as UTF-8 with comma separation and a header row.
                Item values and catalog strength do not prove survival access, source
                availability, progression gates, recurring supply or mining rates.
                These are exports: editing a CSV does not change gameplay.

                DETAIL AND PROVENANCE
                  diagnostics/report_manifest.json  Current profile identity and export detail.
                  diagnostics/report_text.json      Full text for oversized spreadsheet cells.
                Explicit export includes valuation_sources.csv and
                valuation_source_dependencies.csv with advisory loaded recipe structure.
                Routine rebuilds omit those detail tables and remove stale copies.
                Unknown source rates remain unknown. Recipe quantities do not prove
                attainable inventory or farm throughput. Check profile identity before
                comparing separately saved diagnostics.

                REFRESH
                  /essence admin balance export   Refresh reports from the saved profile.
                  /essence admin balance rebuild  Recalculate and install a new profile.
                  /essence debug balance validate Check the installed profile.
                Exporting does not rerun analysis or change gameplay values.
                Normal restart reuses saved balance; it does not apply edited inputs.
                """);
    }
}

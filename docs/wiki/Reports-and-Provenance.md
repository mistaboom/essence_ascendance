# Reports, validation and decision provenance

[Diagnostics](https://github.com/mistaboom/essence_ascendance/wiki/Diagnostics-and-Testing) · [Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration)

Reports explain the installed saved balance: estimated material values, actual Essence payouts, measured equipment references, resolved Skill parameters and Bonus progression. They do not establish survival acquisition, renewable rates or hidden mechanics.

Editing a CSV or Markdown report does not change gameplay. Edit the human configuration and explicitly rebuild instead.

## Start with currentness

All paths below are beneath **`config/essence_ascendance/`**.

1. Run `/essence debug balance summary` for installed identity/counts.
2. Run `/essence debug balance validate` to compare validated disk data with active authority.
3. Check `diagnostics/report_manifest.json` for matching integrity and complete state.
4. Read `README_REPORTS.txt` and `reports/balance_report.md`.

These commands require permission 2 and support console. Validation reads saved data; it does not install or generate balance. `/essence admin balance export` recreates the derived reports from the active snapshot without recalculation.

A report-write failure after installation can leave valid gameplay with incomplete reports. Fix the write problem and export again. Edited TOML files and an old CSV do not describe newly active gameplay until a rebuild succeeds.

## Choose a report

Every CSV below lives in `reports/`. Open as UTF-8, comma-separated data with headers. The exporter writes CSV, not XLSX.

| Files | Contents |
| --- | --- |
| `valuation.csv`, `warnings.csv` | Intrinsic estimate, total spendable payout, routed Essence amounts and reasons |
| `valuation_sources.csv`, `valuation_source_dependencies.csv` | Advisory loaded recipe structure; explicit export only; join by item_id and source_index |
| `equipment.csv`, `equipment_capabilities.csv` | Measured external axes and catalog strength bands |
| `generated_equipment.csv`, `fishing_rod.csv` | Installed Ascendance equipment baselines and rod values |
| `evidence.csv`, `evidence_dependencies.csv` | Retained facts, provenance and dependencies |
| `curves.csv`, `bonus_tracks.csv` | Prices, partial-funding samples, active spans and Bonus endpoints in their native units |
| `skill_rank_parameters.csv` | Every published Skill rank's exact native parameters and cost, including accepted settings |
| `builds.csv`, `build_skill_ranks.csv`, `build_selections.csv`, `build_category_pressure.csv` | Modeled legal selections and contributing ranks; join by projection_id and scenario_id |
| `combat_builds.csv`, `combat_assumptions.csv`, `combat_defense_pressure.csv` | Conditional numeric composition checks and their assumptions |
| `attunement_targets.csv`, `attunement_breadth.csv` | Category targets and required/optional seals per chapter |
| `attunement_methods.csv`, `attunement_calibration.csv`, `attunement_pacing.csv` | Registered activity units, calibration and modeled quantities |
| `attunement_investment.csv`, `attunement_repetition.csv`, `attunement_reachability.csv` | Development, repetition and native activity opportunities; no acquisition proof |
| `latent_ore_supply.csv`, `latent_ore_policy.csv`, `latent_ore_worldgen.csv` | Configured placement policy/minimums and final distributions |
| `runtime_parameters.csv`, `ascension.csv`, `invariants.csv` | Resolved scalar pointers, chapter summary and complete-recipe conservation |
| `projectile_policy.csv`, `guard_policy.csv`, `posture_status_policy.csv`, `vitality_policy.csv` | Saved system behavior explanations |

Routine rebuilds remove the two optional exhaustive recipe tables; explicit export recreates them. Markdown evidence lists are bounded during routine rebuilds. Complete decision tables and warnings remain available.

Older quest, adaptive-balance and competitive-provider tables belong to the retired generation model. A file left in an old directory is not a current export merely because it is readable. Check the manifest and this file list; fresh configs are required for the new model.

## Interpret values correctly

`economic_value` is an advisory estimate. `total_essence` and routed amounts describe base-item dissolution after conservation. Native enchantment components can change a stack's actual result: an enchanted-book catalog row is an upper bound, while the Archive/tooltips and loaded enchantment diagnostics show component-specific values.

Resource access, supply and automation are unexamined. Saved `reachable=false` or unknown classifications mean no acquisition proof was made, not that the item is impossible to obtain. Equipment `stage` is relative catalog strength, not the player's expected acquisition tier.

Complete static recipe constraints concern spendable payouts. An advisory estimate can exceed a recipe bound without being a conservation failure. Opaque recipes are excluded from hard conservation and counted explicitly. An invariant PASS covers the modeled material bill, not every hidden machine operation or mod interaction.

Bonus values remain continuous between completed player-tier endpoints. A track may finish before Transcendent; higher player tiers retain the earned benefit. Eligible equipment tier does not cap the player's Bonus.

For Skills, compare the emitted `skill_settings.toml` with `skill_rank_parameters.csv` after a successful rebuild. Native paths and units matter; a smaller cooldown can be an improvement. Configuration cannot remove prerequisites or fabricate numeric ranks for binary mechanics.

Do not combine unrelated axis maxima into one simultaneous build. An active-rank cost can exclude inactive/replaced prerequisite purchases, equipment and Bonus spending. It is not a complete acquisition price or a measured playtime.

## Diagnostic files

| Path | Purpose |
| --- | --- |
| `diagnostics/report_manifest.json` | Profile identity and export status/detail |
| `diagnostics/report_text.json` | Lossless sidecar for cells exceeding 32,767 characters |
| `diagnostics/pack_metadata.json`, `diagnostics/generation_evidence.json` | Saved environment and generation context |
| `diagnostics/bonus_tracks.json`, `diagnostics/routing_classification.json` | Track and routing explanations |
| `diagnostics/failed-generation-*.json.gz` | Diagnostic candidate that did not install |
| `diagnostics/uncommitted-profile-*.json.gz` | Candidate whose durable commit failed |

Diagnostic artifacts do not prove installation. Saved profile limits are 256 MiB compressed and 4 GiB inflated; these are safety limits, not heap recommendations. Preserve logs for measured generation timings.

Share mod/loader versions, integrity, relevant warnings and a small reproduction. Remove private paths, coordinates, player identifiers and credentials. Keep sanitized reasons and units.

## Developer verification receipts

The isolated `tools/verification/balance_feedback_run.py` helper requires all four human inputs after successful generation, saves their bytes under its receipt's `human-inputs/` directory, and records before/published hashes. Existing inputs must remain unchanged through generation and each rebuild; a requested saved restart must preserve the saved balance files too. Its `--fresh` option replaces only generated profile authority, preserving human settings. New automatic defaults require deliberately setting up the isolated fixture's inputs first.

The read-only `ExportSavedBalanceTool` and `BalancePerformanceReplay` protect the profile and all four human-input paths. Missing Skill or Bonus settings are recorded as absent and must remain absent; these tools do not generate defaults. Such offline receipts do not replace native generation/restart or gameplay tests.

Current source: `balance/generated/BalanceReports.java`, `BalanceReportLayout.java` and `SpreadsheetReports.java` under `common/src/main/java/com/mistaboom/essence_ascendance/`.

# Reports, validation and decision provenance

[Diagnostics](https://github.com/mistaboom/essence_ascendance/wiki/Diagnostics-and-Testing) · [Worked scenarios](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios)

Reports turn generated decisions into questions a pack author can answer: Which source established access? Why was an item discounted? Which capability moved a candidate tier? What prerequisite prevented an earlier Skill?

They are derived views of saved authority. Editing a CSV or Markdown report does not change gameplay.

## Start with currentness

All paths here are beneath **`config/essence_ascendance/`** unless stated otherwise.

1. Run `/essence debug balance summary` for installed identity/counts.
2. Run `/essence debug balance validate` to compare validated disk data with active authority.
3. Open `diagnostics/report_manifest.json`; check integrity, state and detail level.
4. Read `README_REPORTS.txt` and `reports/balance_report.md`.

All three commands used on this page require permission 2 and support console. Validation reads/decodes files and can be expensive; it installs nothing. `/essence admin balance export` writes derived files and produces exhaustive source/dependency tables without recapturing or recalibrating.

Report failure after install can leave valid gameplay with incomplete/stale reports. Use export to recreate views of the installed snapshot after addressing disk/write problems. Do not interpret an old CSV as a different active economy.

## Choose a report by question

Every CSV listed below is in `reports/`. Open as UTF-8, comma-separated data, keep headers, and use spreadsheet filters. There is no native XLSX output.

### Resources and equipment

| Files | Read them for |
| --- | --- |
| `valuation.csv`, `warnings.csv` | Opportunity value, final per-Essence yield, supply/access state and diagnostic reasons |
| `valuation_sources.csv`, `valuation_source_dependencies.csv` | Exhaustive acquisition detail, **explicit export only**; join `item_id` + `source_index` |
| `equipment.csv`, `equipment_capabilities.csv` | External native axes/capability evidence; join item/slot/stage |
| `generated_equipment.csv` | Generated tier baselines; armor/toughness are baseline full-set values, not each piece |
| `evidence.csv`, `evidence_dependencies.csv`, `quest_evidence.csv` | Resolved facts, origins/dependencies and quest unknowns |

Routine rebuilds archive older exhaustive acquisition CSVs instead of leaving them looking current. Full sources remain in saved authority. Markdown samples are bounded; decision CSVs and complete warnings are the broader reference.

### Bonuses, Skills and builds

| Files | Read them for |
| --- | --- |
| `curves.csv`, `bonus_tracks.csv`, `skill_rank_parameters.csv` | Prices/checkpoints, track effects and meaningful native rank parameters |
| `adaptive_balance.csv`, `skill_availability.csv`, `skill_ranks.csv`; `adaptive_balance.md` | Catalog/candidate/final tiers, clamps, witnesses, coverage and pricing/rank reasoning |
| `builds.csv`, `build_skill_ranks.csv`, `build_selections.csv`, `build_category_pressure.csv` | Projected scenarios, active/contributing ranks, choices and pressure; join projection/scenario |
| `combat_builds.csv`, `combat_assumptions.csv`, `combat_defense_pressure.csv` | Numeric model and its assumptions; not observed fights |
| `competitive_capabilities.csv`, `competitive_frontiers.csv`, `competitive_candidates.csv`, `competitive_unsupported_counts.csv`; `competitive_capabilities.md` | Admitted representatives, unresolved candidates and unsupported counts |

Some tables are conditional on retained evidence. A missing optional table is different from missing mandatory profile authority.

### Activity, ore and policy

| Files | Read them for |
| --- | --- |
| `attunement_targets.csv`, `attunement_breadth.csv` | Category targets and required/optional seals by chapter |
| `attunement_methods.csv`, `attunement_calibration.csv`, `attunement_pacing.csv` | Registered units, calibration and modeled pacing |
| `attunement_investment.csv`, `attunement_repetition.csv`, `attunement_reachability.csv` | Development acceleration, repeated-source policy and estimated access |
| `latent_ore_supply.csv`, `latent_ore_policy.csv`, `latent_ore_worldgen.csv` | Early coverage, supply assumptions and selected hosts/distributions |
| `runtime_parameters.csv`, `ascension.csv`, `invariants.csv` | Scalar pointers, chapter/harvest summary and modeled production/conservation |
| `projectile_policy.csv`, `guard_policy.csv`, `posture_status_policy.csv`, `vitality_policy.csv` | Saved system contract explanations |

`ascension.csv` is not a wallet-payment prerequisite table. Current player Ascension uses seals.

## Read provenance as a chain

Follow the source identifier, origin/provider/version, supported access/dependencies, measurement units/scope, confidence, matched overrides and final reason.

To follow a multi-table decision, filter the first table to the relevant item or projection ID, then find rows with the same IDs in its detail table. For acquisition, both `item_id` and `source_index` must match; an item's different source can have different dependencies. [Report terminology](https://github.com/mistaboom/essence_ascendance/wiki/Procedural-Balance#terms-used-in-reports).

For a Skill, compare **catalog → candidate → final tier**, then prerequisite/setup clamps. For an item, compare source opportunity value to final yield and policy/conservation changes. For a capability, distinguish admitted measurements from unresolved candidate labels.

Do not combine unrelated per-axis maxima into one imagined simultaneous build. A source may be strongest for one axis but incompatible with another choice. Active-rank curve cost can omit inactive/replaced prerequisite purchases; it is not automatically a complete build-acquisition price.

Confidence measures certainty, not a simple power multiplier. PARTIAL/UNKNOWN means coverage is incomplete. A recorded invariant PASS verifies the modeled contract, not every undocumented loop.

## Detailed diagnostics

Paths below live in `diagnostics/`.

| File or directory | Purpose |
| --- | --- |
| `report_manifest.json` | Installed identity and export status/detail |
| `report_text.json` | Lossless sidecar for cells longer than 32,767 characters; retain with CSVs that reference it |
| `pack_metadata.json`, `generation_evidence.json` | Environment/provider facts and retained generation evidence |
| `bonus_tracks.json`, `routing_classification.json` | Track/routing explanations |
| `competitive_capabilities.json`, `adaptive_balance.json` | Full optional competition/adaptation projections |
| `generation_comparison.json` | Comparison artifact when present, not a second authority |
| `legacy_reports/` | Preserved old exports/comparisons, including their needed sidecars |
| `failed-providers-*.json` | Provider/collection failure evidence |
| `failed-generation-*.json.gz` | Diagnostic-only failed runtime-generation candidate; cannot install |
| `uncommitted-profile-*.json.gz` | Validated candidate whose durable commit failed; does not prove installation |

Wildcard names identify generated random-suffix families, not files to manually create.

The saved profile has compressed/inflated safety bounds of 256 MiB / 4 GiB. These limits are not recommended heap sizes. Timing/count/memory telemetry appears in normal logs and bounded operation snapshots; no separate `performance.json` authority is promised.

## Share useful evidence

Include mod/MC/loader versions, profile integrity, manifest state, relevant warning/provider rows and a small reproduction. Remove personal paths, player/owner identifiers, coordinates, credentials and raw personal logs.

Keep source labels and reasons after sanitizing; they are more useful than a screenshot of an unexplained number. [Issue tracker](https://github.com/mistaboom/essence_ascendance/issues).

Source: [report writer](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/BalanceReports.java), [layout/currentness](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/BalanceReportLayout.java), [spreadsheet exports](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/SpreadsheetReports.java).

# Wiki publication verification

Latest publication: beta.5 on 2026-10-13, recorded below. Earlier sections are historical receipts.

Live wiki: https://github.com/mistaboom/essence_ascendance/wiki

## Scope and publication

- Reviewed implementation: `59e27446afbb6c9e73be38d0d407915fe15354fa`.
- Published mod scope: `v1.0.0-beta.1`, release source `9caef8477c8aa95a5f61b7fc008d1659e2445e79`, Minecraft 1.21.1, Java 21, Fabric and NeoForge.
- Release-to-implementation comparison found no changes to command/gameplay/config/resource/integration/test implementations. Later changes concern descriptions, documentation, branding and build tooling; released jars were not independently reproduced.
- Canonical documentation was committed to main in `3ab8e32`; the live rendering repair is in `73ab3ac`.
- The wiki was uninitialized. GitHub's authorized first-page flow created the complete reviewed Home page in `296b456`; that initial history was preserved.
- The separate wiki checkout's inspected default branch is `master`. Full publication is `a1520ed`, followed by diagram readability repair `6c06ee5`.
- Both repositories were pushed normally. No existing wiki pages were deleted. The maintained set is 19 content pages plus `_Sidebar.md` and `_Footer.md`.

## Independent source and documentation review

- Rechecked the production command-registration entry point, all six command classes and shared permission/target/identifier handling against the inventory and command pages. Reviewed handler effects and supporting services, including purchases, reset/grants, milestone overrides, held-item mutation, Attunement, profile operations and diagnostic reconciliation.
- Compared policy keys/defaults with bundled TOML and typed ranges, enum choices, budget-sum tolerance, generation assumptions, Attunement bounds and ore distribution validation.
- Compared all 47 factual override keys with parser types, enums, bounds and contradiction checks; checked priority resolution, exact-value restrictions and processing-time units.
- Rechecked saved-profile selection, revision/integrity validation, transport preflight, durable replacement/install order, post-install report/sync failure behavior and recipe-reload boundaries.
- Rechecked Archive gift/replacement/opening/navigation/search and the active server presentation boundary; reviewed integration loader/version/readiness limits and released scope.
- Corrected the maximum-tier Attunement query description: queries show completed seals at Transcendent without requiring a next chapter. Updated the audit as well as player/diagnostic pages.

## Focused checks

Static checks found no missing internal destinations or heading anchors across **127 cross-page links**, no missing files among **50 unique implementation/maintenance references**, and complete sidebar coverage for all 19 content pages. Source references pinned to a commit were checked against Git objects.

The static key-coverage comparison covered **36 bundled policy/root keys**, **47 fact keys** and **9 ore-parser keys**. All **five TOML examples** parsed with Python's standard TOML reader; their keys/types/ranges and budget constraints were compared with the actual bounded parser and typed validators. This was not an execution of the Java parser or balance generator.

The audit-to-page syntax comparison found no missing ungrouped command forms. Consolidated alternatives, help endpoints, optional targets, numeric ranges and examples were also reviewed against registrations. Privacy/placeholder scans found no matches. Staged whitespace checks passed.

## Live verification

Opened the actual published GitHub wiki and verified Home, sidebar/footer, Administration Commands, Configuration, Procedural Balance, Balance Generation and Players and the Archive. Checked rendered command tables and warnings, configuration tables and highlighted TOML, heading-anchor navigation, active-server guidance and both Mermaid diagrams.

The first overview diagram rendered too small in the wiki column. Changed its direction from horizontal to vertical in the canonical source, committed/pushed both repositories, and rechecked the live diagram's readable labels. The lifecycle diagram rendered without a syntax error.

Compared all 21 maintained Markdown files between canonical sources and the wiki checkout, and checked remote branch commits and clean working-tree state after publication. The manual republishing procedure is in [PUBLISHING.md](PUBLISHING.md), with a reader-facing summary on the live Version and Maintenance page.

## Verification limits

These are source, documentation, Git synchronization and live rendering checks. No Minecraft instance was launched, no gameplay/admin/test command was executed, no balance profile was rebuilt, and no installed instance, world, gameplay code, release jar, platform token or unrelated project setting was changed. This publication establishes no new gameplay benchmark, fresh Java test result, universal mod compatibility or perfect automatic balance.

## Local multiplayer continuation — 2026-10-13, not published

Canonical wiki sources were refreshed for the current dirty multiplayer implementation after the separate Archive continuation completed. This does not change the October 8 publication history or extend its source-equality claim. Latest public release evidence is beta.4 at `4caee9ad53c9ad2faec0eec14ec525262277512c`; uncommitted multiplayer/Archive work is absent from that source.

Current page inventory, source verification, final Archive destinations, documentation checks and the user's manual publication checklist are in [PASSDOWN.md](PASSDOWN.md), with the substantive source continuation appended to [AUDIT.md](AUDIT.md). No staging, commits, pushes, remote changes, wiki checkout creation or publication occurred in this continuation. GitHub's separate wiki remains unchanged by local edits.

## Implementation-handoff documentation reconciliation — 2026-10-13

Canonical local pages now reflect exact shared FTB gates, named approval enforcement, membership help/countdown guidance, strengthened implementation receipts and the user's narrow real activation/new-Crucible ownership confirmation. The intentional beta save break is documented; existing-machine provider migration and remaining live checks stay explicit. This follow-up did not stage, commit, push or publish. Current source remains development work. See `PASSDOWN.md` and `CHECKS-2026-10-13-FOLLOWUP.json`; earlier publication records remain historical.

## Beta.5 release and wiki publication — 2026-10-13

The user authorized committing and pushing the completed implementation, Archive and wiki and publishing the next beta. Release source is `12f64fffdb8ae2ff2e3e9ddea4aed72e99defbf3`, tagged `v1.0.0-beta.5`. The [GitHub prerelease](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.5) was published at 18:22:59 UTC with Fabric and NeoForge Minecraft 1.21.1 jars and `SHA256SUMS` attached by Actions.

- [Main CI](https://github.com/mistaboom/essence_ascendance/actions/runs/38445225759) passed the common checks and both loader builds.
- [Release workflow](https://github.com/mistaboom/essence_ascendance/actions/runs/38446281299) passed its build, GitHub attachment and all four mod-site upload jobs. Serialized uploads succeeded on their first attempts, including NeoForge on Modrinth; no rerun or duplicate upload was needed. This verifies this release, not a guarantee against future platform failures.
- [Description synchronization](https://github.com/mistaboom/essence_ascendance/actions/runs/38445225657) passed for both mod sites.
- The wiki was published through `tools/wiki/publish.ps1` from the release source to wiki commit `a73d36b07e5d420255db887af09f63497e2cf491` on `master`, changing 19 of 22 maintained pages. The publisher verified the remote commit and removed its temporary checkout. Live Home, Administration Commands, Updating and Recovery, sidebar/footer and release links were checked.
- The release notes, README and wiki prominently require a new world when upgrading from beta.4 or earlier because the multiplayer save format changes player balances, membership and machine ownership. Older-save migration and cross-provider migration of existing machines remain unsupported. Existing worlds were not changed or deleted.

Downloaded GitHub jars passed SHA-256 verification, ZIP integrity and loader metadata/license checks:

| Loader | SHA-256 |
| --- | --- |
| Fabric | `73b763d898158a53b13a2d03a8ffd94128f8b723e67d3eca08ccecacd1180f73` |
| NeoForge | `65f5d7957511524bd5f050d4094f2e7f6894da4889b81211b57e4c9c381a2301` |

Authenticated Modrinth verification found exactly one beta.5 version per loader: [Fabric `6mVigN9Z`](https://modrinth.com/mod/essence-ascendance/version/6mVigN9Z) and [NeoForge `1JgXfo5w`](https://modrinth.com/mod/essence-ascendance/version/1JgXfo5w). Both had the correct Minecraft version, loader, beta type, matching reviewed changelog and SHA-512 matching the GitHub jars. Both version records were listed, but the project still reported `processing` with requested status `approved`; public availability remains subject to project moderation.

CurseForge accepted both uploads according to the successful publication jobs. Its public file list still showed beta.4 during the immediate follow-up check, so public beta.5 availability, file IDs and independently downloaded CurseForge bytes were not confirmed. Do not re-upload merely because publication/moderation has not yet appeared in that listing.

Local evidence is retained under ignored `build/beta5-release/`, including full checks/build logs, wiki validation and publication logs, release workflow logs, downloaded GitHub artifacts and the Modrinth verification receipt. Full local common checks, both production builds and asset invariants passed before publication; the tagged source also passed GitHub CI and the release build. These checks do not complete the broader real two-client, offline/unloaded-machine, graphical or restart acceptance checklist, and do not establish crash-atomic persistence.

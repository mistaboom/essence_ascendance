> **Local source refresh, 2026-10-17.** Current configuration, generation, economy and report pages now describe the simple generator and editable `skill_settings.toml` / `bonus_settings.toml`. This is a local documentation update, not a wiki publication or release-validation receipt. The beta.5 scope and evidence below remain historical; other gameplay pages still need a separate full wiki review. [Current model](https://github.com/mistaboom/essence_ascendance/blob/main/docs/simple-balance.md).

# Version and wiki maintenance

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [Source](https://github.com/mistaboom/essence_ascendance) · [Issues](https://github.com/mistaboom/essence_ascendance/issues)

## Documented scope

| Reference | Value |
| --- | --- |
| Documentation refresh | **2026-10-13**, beta.5 multiplayer and Archive update |
| Documented mod version | **1.0.0-beta.5** |
| Minecraft / Java | **1.21.1 / 21** |
| Loader projects | Fabric and NeoForge |
| Release | [v1.0.0-beta.5](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.5), prerelease |
| Previous beta.4 source | [4caee9ad53c9ad2faec0eec14ec525262277512c](https://github.com/mistaboom/essence_ascendance/commit/4caee9ad53c9ad2faec0eec14ec525262277512c) |
| Pre-multiplayer baseline | `438404996988d730c8246f5aa5620897a795a13f`; multiplayer was added afterward |
| Multiplayer implementation reference | [Tagged beta.5 source](https://github.com/mistaboom/essence_ascendance/tree/v1.0.0-beta.5/common/src/main/java/com/mistaboom/essence_ascendance), reviewed October 13. |
| Historical balance/wiki baseline | [59e27446afbb6c9e73be38d0d407915fe15354fa](https://github.com/mistaboom/essence_ascendance/commit/59e27446afbb6c9e73be38d0d407915fe15354fa), original beta.1 review |
| Research baseline | `docs/wiki-maintenance/AUDIT.md` in the repository documentation tree |

The original October 8 source-equality and publication statements apply only to their dated beta.1 baseline. Beta.5 adds the separately reviewed multiplayer implementation and Archive refresh. This documentation update is not a new full balance audit or proof of every multiplayer scenario.

The generator revision is `rod-hook-set-steering-50`, with generated schema 2, generator `pack-balance-1`, ordinary yield policy `whole_essence_v1` and input TOML schema 1. Use the active profile/diagnostics for the installed environment's actual identity. **Beta.5 requires a new world when upgrading from beta.4 or earlier:** player schema 10 replaces beta.4's schema 6, with no older-world migration. See [upgrade guidance](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery#multiplayer-persistence-and-upgrades).

## Documentation source of truth

Command registrations, arguments, permissions and handlers decide syntax and effects. Parsers and typed validation decide supported input keys/ranges. Lifecycle/install/sync code decides when values become active. The Archive's presentation boundary decides which values players see.

Engineering notes can describe superseded designs. Current Ascension is seal-based with no Essence payment; older investment-threshold comments are not its eligibility rule. The fact field `processing_time` uses server ticks in current production code.

Do not turn bootstrap constants into universal pack tables. Runtime/rank/yield values belong in active Archive data and generated reports.

## Maintainer refresh procedure

1. Identify current release/tag/source and working-tree state. Preserve unrelated work.
2. Compare release source to implementation, documenting any unreleased behavior separately.
3. Reinspect every registered executable branch, help endpoint, alias, optional target and argument limit; update the three command pages and index together.
4. Compare parser key sets, defaults, range/enum validation and bundled input examples against configuration/overrides pages.
5. Trace profile selection, saved validation, explicit rebuild, commit failures, exports and synchronization; check lifecycle claims.
6. Recheck Archive delivery/recipe/opening/navigation/search and active-context behavior.
7. Recheck provider version/loader/readiness gates. Separate candidates/partial contracts from comprehensive support.
8. Check cross-page URLs, heading anchors, examples, report filenames and implementation links.
9. Perform representative staged acceptance separately if the release requires it; record environment and actual scope rather than “all mods supported.”

Each local `docs/wiki/*.md` filename corresponds to its wiki page slug; `Home.md`, `_Sidebar.md` and `_Footer.md` supply navigation. Keep cross-page links absolute under `https://github.com/mistaboom/essence_ascendance/wiki/`.

## Republish reviewed pages

Edit **`docs/wiki/` in the main repository**. Keep the audit in `docs/wiki-maintenance/AUDIT.md` current when implementation changes. Commit and push the reviewed documentation to the main repository under its contribution rules.

Keep `docs/wiki/` as the only permanent local editing location. The manual publisher creates a temporary checkout of [the wiki Git repository](https://github.com/mistaboom/essence_ascendance.wiki.git), discovers its default branch, and preserves existing history and other pages. Run these commands from the main repository root in PowerShell after committing and pushing the reviewed source pages:

```powershell
./tools/wiki/publish.ps1
./tools/wiki/publish.ps1 -Publish -Message 'Update reviewed wiki documentation'
```

The first command previews the diff without pushing. Reconcile any useful direct wiki edits in the canonical files before publishing. Use `-Pages Version-and-Maintenance.md` on both commands to publish only that page, or omit `-Pages` to select all maintained Markdown pages, including navigation. Successful runs remove their temporary checkout; failed runs retain it for recovery and report its location.

The publisher commits and pushes normally, without force-pushing or deleting pages. It uses existing GitHub Git access and verifies the remote commit. The main repository and wiki have separate histories: pushing `docs/wiki/` alone does not publish the wiki. After publication, check the changed pages on the actual wiki. For a full publication, check Home, sidebar/footer, a command page, Configuration and Procedural Balance, including tables, anchors and diagrams. Correct rendering problems in the canonical files first, then republish.

[Detailed manual publishing procedure](https://github.com/mistaboom/essence_ascendance/blob/main/docs/wiki-maintenance/PUBLISHING.md). This workflow uses existing GitHub access; it adds no credentials, automatic rewrites or scheduled publishing.

## Evidence and test claims

Examples illustrate confirmed syntax/settings; they are not claimed to have been executed. Source suites exercise real parsing, codecs and targeted synthetic/native contracts at their documented scope. Their existence is not a fresh PASS or a multiplayer/survival compatibility result.

Keep validation claims specific: profile decode/integrity, modeled conservation, meaningful-rank policy, command parsing, presentation readiness or a concrete live scenario. Include environment/version information with any future performance or gameplay acceptance result.

The October 13 Stage 9 receipts were inspected as inherited evidence: full common checks/both loader builds/asset checks passed; initialized native/both-provider runs exercised memory-backed machines and synthetic actual-interface rosters. Their artifact hashes describe those builds, not later Archive or implementation rebuilds.

The subsequent implementation receipts under `build/ftb-shared-compat-audit/` record co-installed FTB initialization/API checks on both loaders for the exact shared Library 2101.1.36 and 2101.1.37 combinations. These fixtures exit before opening a world/provider manager. They establish the checked API/definition paths, not every live membership event or arbitrary newer-version support.

The later `build/membership-command-followup/verified-checks-and-packaging.log` records **678 membership assertions, 1,155 command checks, 13,452 shared presentation checks, 70,083 headless Archive integration checks**, both production jars and **349 asset checks per loader**. The runtime activation cases use a fake provider without a world. Headless presentation/Archive checks do not establish visual usability. Its separate `final-jar-audit.json` describes local development artifacts; none of these receipts establishes a published new beta.

The user confirmed **real FTB authority activation and team ownership of a newly placed Crucible**. This is a narrow user-reported live success, separate from the automated fixtures. Two-client fairness, offline-member events, unloaded-machine behavior, world/process restarts, graphical review and interrupted-save recovery remain pending. Existing machines are not migrated across providers. Player/chunk cross-file crash atomicity remains unjournaled and unproven; runtime rollback coverage does not establish it. This wiki follow-up only inspects source/receipts and checks Markdown; it executes no gameplay command or build.

Archive destination verification, historical receipts and publication records are maintained under `docs/wiki-maintenance/`. The beta.5 release links identify the release source; earlier local receipt hashes retain their original scope. GitHub release automation builds and distributes the two loader jars, while the wiki uses its separate reviewed publishing procedure.

## High-value implementation entry points

The GitHub links below are **historical balance/Archive baseline references**. For current multiplayer, inspect the portable paths in [Teams and shared infrastructure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#source-evidence); the new handlers/providers are not present at the historical commit.

| Subject | Source |
| --- | --- |
| Commands | [command package](https://github.com/mistaboom/essence_ascendance/tree/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command) |
| Input grammar/policy | [balance/config](https://github.com/mistaboom/essence_ascendance/tree/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/config) |
| Generation/storage/reports | [balance/generated](https://github.com/mistaboom/essence_ascendance/tree/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated) |
| Item valuation | [valuation](https://github.com/mistaboom/essence_ascendance/tree/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/valuation) |
| Archive/runtime presentation | [Archive screen](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/client/AscendanceArchiveScreen.java), [presentation context](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/client/presentation/PresentationContext.java) |

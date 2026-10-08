# Version and wiki maintenance

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [Source](https://github.com/mistaboom/essence_ascendance) · [Issues](https://github.com/mistaboom/essence_ascendance/issues)

## Documented scope

| Reference | Value |
| --- | --- |
| Documentation date | **2026-10-08** |
| Mod | **1.0.0-beta.1**, prerelease |
| Minecraft / Java | **1.21.1 / 21** |
| Loader projects | Fabric and NeoForge |
| Release | [v1.0.0-beta.1](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.1), published October 8, 2026 |
| Release source | [9caef8477c8aa95a5f61b7fc008d1659e2445e79](https://github.com/mistaboom/essence_ascendance/commit/9caef8477c8aa95a5f61b7fc008d1659e2445e79) |
| Wiki implementation reference | [59e27446afbb6c9e73be38d0d407915fe15354fa](https://github.com/mistaboom/essence_ascendance/commit/59e27446afbb6c9e73be38d0d407915fe15354fa) |
| Research baseline | `docs/wiki-maintenance/AUDIT.md` in the repository documentation tree |

The implementation reference has the same commands, gameplay, balance, config/resources and tests as the published beta's source. Post-release differences concern descriptions/listing synchronization, documentation and branding/build tooling. This source comparison does not independently reproduce the released jars.

Current generated authority uses schema 2, generator `pack-balance-1`, revision `native-equipment-headroom-46` and ordinary yield policy `whole_essence_v1`. Input TOML schema is 1.

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

Clone or reuse [the wiki Git repository](https://github.com/mistaboom/essence_ascendance.wiki.git) in a separate directory. Inspect its default branch, status and history, then pull normally. Copy the reviewed Markdown pages into that checkout, including `_Sidebar.md` and `_Footer.md`. Compare existing content before replacing overlapping pages; preserve pages outside this maintained set unless their removal has been explicitly reviewed.

Review the wiki diff, commit and push normally, without force-pushing. The main repository and wiki have separate histories: pushing `docs/wiki/` alone does not publish the wiki. After publication, open the actual wiki and check Home, sidebar/footer, a command page, Configuration and Procedural Balance, including tables, anchors and diagrams. Correct rendering problems in the canonical files first, then republish.

[Detailed manual publishing procedure](https://github.com/mistaboom/essence_ascendance/blob/main/docs/wiki-maintenance/PUBLISHING.md). This workflow uses existing GitHub access; it adds no credentials, automatic rewrites or scheduled publishing.

## Evidence and test claims

Examples illustrate confirmed syntax/settings; they are not claimed to have been executed. Source suites exercise real parsing, codecs and targeted synthetic/native contracts at their documented scope. Their existence is not a fresh PASS or a multiplayer/survival compatibility result.

Keep validation claims specific: profile decode/integrity, modeled conservation, meaningful-rank policy, command parsing, presentation readiness or a concrete live scenario. Include environment/version information with any future performance or gameplay acceptance result.

## High-value implementation entry points

| Subject | Source |
| --- | --- |
| Commands | [command package](https://github.com/mistaboom/essence_ascendance/tree/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command) |
| Input grammar/policy | [balance/config](https://github.com/mistaboom/essence_ascendance/tree/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/config) |
| Generation/storage/reports | [balance/generated](https://github.com/mistaboom/essence_ascendance/tree/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated) |
| Item valuation | [valuation](https://github.com/mistaboom/essence_ascendance/tree/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/valuation) |
| Archive/runtime presentation | [Archive screen](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/client/AscendanceArchiveScreen.java), [presentation context](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/client/presentation/PresentationContext.java) |

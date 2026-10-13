# Wiki multiplayer continuation handoff — 2026-10-13

**Status: implemented in canonical local Markdown; not staged, committed, pushed or published.** The user handles final Git/publication. Gameplay, Archive code/content/tests, frameworks, installed instances, worlds, inputs/caches/profiles and release jars were not modified by this wiki task. The separate Archive chat owns its source changes and validation.

## Authority and version scope

- Checkout base: `438404996988d730c8246f5aa5620897a795a13f`. It lacks the uncommitted multiplayer implementation. Local `mod_version` is `1.0.0-beta.4`, MC 1.21.1, Java 21, Fabric/NeoForge.
- Latest public non-draft release observed October 13: [v1.0.0-beta.4](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.4), published October 12 at `4caee9ad53c9ad2faec0eec14ec525262277512c`; Fabric/NeoForge jars and SHA256SUMS. This is release evidence, not proof of current dirty-source inclusion.
- Current multiplayer facts are independently verified local source, explicitly marked **development**. Immutable current source links and future release inclusion remain pending the user's real implementation commit/release. Older links are labeled historical; no current behavior was pinned to a commit that lacks it.
- The October 13 simplification/follow-up/Stage 9 sections outrank historical engineering notes. Original `AUDIT.md` and `PUBLICATION.md` records remain intact, with a dated continuation.
- Archive Final completed its multiplayer continuation before final wiki destinations were inserted. Its recorded final source-verification/destination/validation handoff is `docs/ascendance-archive.md`, section **Shared-essence documentation continuation — 2026-10-13**.

## Pages changed

| Canonical page | Purpose |
| --- | --- |
| `Teams-and-Shared-Infrastructure.md` (new) | One administrative reference for communal player-backed Stored, personal progression, trust, providers/selection, authority changes, machinery, source budgets, preferences, work/automation and recovery limits |
| `Home.md`, `_Sidebar.md`, `_Footer.md` | Add the team route and distinguish public beta.4 from current development |
| `Players-and-the-Archive.md` | Minimum cooperation explanation and final Archive routes; exact fractions versus tooltip display; correct Item Yields localized-name filtering |
| `Installation-and-Server-Setup.md` | Latest release link, matching development endpoints, optional membership dependencies and schema/authority transition cautions |
| `Integrations-and-Compatibility.md` | Separate membership from generic/evidence/display contracts and document exact FTB combinations; the subsequent shared-tuple fix is recorded below |
| `Configuration.md` | Actual server JVM property/default/enum/lifecycle; no invented teams TOML file or key |
| `Commands.md` | Index new branches, correct UUID/coordinate conventions and explicit development scope |
| `Player-Commands.md` | All native team forms and ordinary/own-item machine forms; prerequisites/capabilities, exact examples, effects and UUID/position rules |
| `Administration-Commands.md` | Five membership operations, narrow operator machine recovery, effective-pool destructive clear and teamed-reset semantics |
| `Diagnostics-and-Testing.md` | Membership/machine observability and legacy output caveats; existing enchantment diagnostic; inherited-test limits |
| `Troubleshooting.md` | Targeted membership/readiness/link/bandwidth/selection/capacity/recipient/recovery cases, without generic resets/rebuilds |
| `Updating-and-Recovery.md` | Actual persistence ownership, coordinated backups, incompatible development schema and non-remapping authority changes |
| `Worked-Scenarios.md` | Prospective cooperative development-pack workflow, not a claim of live tests |
| `Progression-and-Power.md` | Personal recipient versus communal funding; explicit Start and paid-work pointer |
| `Balance-Generation.md` | Correct inspected generator revision and separate membership/state from balance rebuilding |
| `Version-and-Maintenance.md` | Current source/release boundary and specific inherited versus new verification |

The maintained wiki has **20 content pages plus sidebar/footer**, 22 Markdown files. Existing unrelated balance/player reference material was preserved rather than replaced with a second player manual.

Maintenance: dated source audit appended to `AUDIT.md`; non-publication status appended to `PUBLICATION.md`; current manual-review gate added to `PUBLISHING.md`. This handoff is the current wiki passdown. A narrowly scoped note is appended to the multiplayer passdown only after the Archive's completed note; its receipts/pending checks remain intact.

## Final Archive destinations

Namespace is `essence_ascendance:` for every ID below. Titles were compared to localized text and IDs to actual catalog registrations, including the existing dynamic Mechanics topic list. These are in-game paths, not guessed GitHub pages or invented Archive commands.

| Title | ID path | Route |
| --- | --- | --- |
| Teams and Shared Essence | `reference/mechanics/teams` | Reference → Mechanics |
| Collecting Essence | `guide/essence/collecting` | Guide → Essence |
| Your First Machine Network | `guide/machines/first_network` | Guide → Machines |
| Beginning Infusion | `guide/infusion/first_focus` | Guide → Infusion |
| Essence Crucible | `reference/machines/crucible` | Reference → Machines |
| Essence Pylon | `reference/machines/pylon` | Reference → Machines |
| Essence Infuser | `reference/machines/infuser` | Reference → Machines |
| Essence Focus | `reference/machines/focus` | Reference → Machines |
| Equipment Infusion | `reference/equipment/infusion` | Reference → Equipment |
| Durability and Lifecycle | `reference/equipment/lifecycle` | Reference → Equipment |
| Allocation and Storage | `reference/mechanics/allocation_storage` | Reference → Mechanics |
| Over-Cap Investment | `reference/mechanics/overcap` | Reference → Mechanics |

Detailed gameplay stays there. The new team article is static read-only guidance, not a live roster or management screen. Item Yields remains Reference → Essences → Item Yields; current name filtering does not search registry IDs. First-powered-tier delivery, the Raw Latent Ore + two sticks replacement recipe and operator delivery were reinspected.

## Current source findings

The current source-seam table in `AUDIT.md` is canonical evidence for registrations **and handlers**, membership selection/reconciliation, exact accounting/persistence, binding/linking/capacity, scheduling, preferences/authorization, jobs/recipient/automation and Archive catalog. Paths are portable repository-relative references; no current immutable source commit is fabricated.

Important verified limits/discrepancies:

- Public beta.4 source's EA player save is **schema 6**; current development requires **10** and rejects other versions. No older-world migration was found. A balance rebuild does not repair this. Preserve matching complete world/build backups; do not edit schemas or clear player data as a shortcut.
- The wiki audit initially found incompatible FTB Library gates. Subsequent implementation fixes accept Teams **2101.1.11** with Library **2101.1.30, 2101.1.36 or 2101.1.37**. Teams **2101.1.11 / Library 2101.1.36 or 2101.1.37 / Quests 2101.1.36 / Architectury 13.0.11** satisfies both gates; see the follow-up evidence below. The older Quests evidence tuple with Teams **2101.1.10** does not enable membership.
- OpenPAC **0.32.7** is accepted on Fabric/NeoForge. Matching artifact metadata requires Fabric config port >=21.0.5, fixture 21.1.6. Provider APIs are compile-only; compilation/initialized API fixtures do not prove live provider managers/events.
- Explicit authority activation retains personal Available/progression, equalizes target Stored and changes solo identities; old team bindings are not remapped or declared dissolved. Narrow recovery does not authorize takeover of any otherwise bound old-provider machine.
- An explicit `machine ... link automatic` clears its current target and discovers eligible nearest source; ordinary Automatic refresh remains stable. Locked/Disconnected command modes and old-setting migration readers remain valid.
- Operator ray-debug text retains labels such as `cap/family` / `player-owned`; current aggregate member-scaled storage/authorization code controls behavior. These labels were recorded, not changed in gameplay code.
- Retained internal recipient/port/binding fields are not executable controls. No removed adoption/privatization, recipient-selector, configurable-port or ownership-break workflows were restored in prose.
- `/essence machine ... start` on a Crucible arms dissolution, not player incoming channeling. Team unbound equipment needs the GUI confirmation; the chat branch cannot supply it.
- The wiki audit initially found a default-`Team` command bypass. The implementation follow-up corrected it: `/essence team approve <request> [name]` requires an explicit valid name only when creating a new team. The shared service rejects a missing/invalid creation name without membership/fund/request changes; existing-team approvals preserve their name. The player/team pages now describe this behavior.
- `test enchantments` has no explicit active-profile rejection. It writes a loaded-registry receipt; meaningful yields still depend on valid mappings. Do not treat its mere execution as proof of ready balance.

## Documentation validation performed

Only read-only source inspection and local Markdown checks were performed; no Java/gameplay tests, build, Minecraft launch, real-world command, provider migration or balance generation occurred here.

- Compared **27 executable multiplayer forms** against actual registrations/handlers: 13 native team forms, 9 machine forms and 5 membership-admin forms. Rechecked alias versus non-executable/incomplete paths, UUID/string/word/BlockPos types, suggestions, console/player restrictions, actual permissions, effect/output and example prerequisites. Examples are syntax illustrations, not executed commands.
- Checked the JVM property read/default and enum, provider precedence/retention, 1,200-tick preparation, 100-tick capture schedule, exact adapter tuples and matching runtime artifact metadata.
- Read current balance settings/Attunement/ore parser/defaults and bundled TOML; parsed **five** existing wiki TOML examples with Python `tomllib`. Checked coverage of **35** root/non-ore policy keys plus the ore setting fields. Input key/default/range and selected example constraints were checked separately; this is not execution of the Java bounded parser or generator. Machine-readable check scope and inspected source SHA-256s are in [CHECKS-2026-10-13.json](CHECKS-2026-10-13.json).
- Validated **168 internal wiki links/anchors**, complete sidebar coverage for **20** content pages, balanced fences, nonempty sections and **49** historical source file/directory paths through Git objects. Current portable source paths were inspected independently.
- Verified **12** final Archive titles/IDs/navigation routes against the completed handoff, live catalog/section/localization and article composition. No pending/guessed Archive destination remains.
- Reviewed stale ownership/capacity/exclusivity/adoption/port/protection/automation wording in context. Preserved legitimate one-active-player-source and one-specific-Infuser-source restrictions.
- Privacy/personal-content/unfinished-marker scans passed for canonical pages; generic examples contain no actual player identity, personal path, credential or raw log. No hidden user-specific content was added.
- Scoped `git diff --check -- docs/wiki docs/wiki-maintenance` passed. HEAD and staging stayed unchanged; only approved documentation and the append-only auxiliary note were written by this chat. The Archive chat's edits remain separate prior/concurrent work.

## Inherited evidence is not a new pass

Inspected the four requested Stage 9 log/packaging receipts. Full common checks and both builds/asset checks passed in their recorded scope; native and both-provider initialization passed. Actual-interface synthetic rosters and memory-backed machines are not live-world multiplayer proof. Their Stage 9 artifact hashes predate the separately rebuilt Archive jars.

The completed Archive handoff records its own focused five-suite pass and both loader builds/resource checks. This wiki did not rerun them or recalculate current jar hashes. Keep those receipt scopes separate from the local Markdown checks above.

Remaining: actual provider managers and live offline roster lifecycle, process/world restarts, authority activation on real test state, two-client fairness/identity, graphics/input/navigation, arbitrary pipes and cross-file crash atomicity. Runtime rollback is covered by existing fixtures; player/chunk crash atomicity is unjournaled/unproven. The original wiki-only audit made no gameplay change; the subsequent compatibility and approval-naming fixes are recorded below.

## Manual publication checklist — user actions only

1. Review the canonical Markdown and this continuation's release/development labels. Keep pending gameplay/Archive work out of any documentation-only staging. Current multiplayer source URLs must await a real implementation commit; public release inclusion needs separate evidence. Development prose can retain portable references if published before a release, but must retain its explicit uncommitted/development scope.
2. Recheck `git status`, the staged file list, source version and the final Archive handoff after any subsequent source changes. Commit only intended documentation; do not use an indiscriminate `git add .`. The current team/machine/Archive refresh does not authorize reverting unrelated code.
3. Follow `PUBLISHING.md` to commit/push reviewed canonical pages to the main GitHub repository. The inspected main origin is `https://github.com/mistaboom/essence_ascendance.git`. No permanent outside wiki checkout is needed or assumed.
4. From the main repository root, run `./tools/wiki/publish.ps1` to preview. The wiki remote is `https://github.com/mistaboom/essence_ascendance.wiki.git`; the established default branch was `master`, but the publisher discovers the current default. It preserves history/other pages and never force-pushes. Review all selected pages, including older changes, before a full publication. Use `-Pages` with exact filenames for a narrower reviewed set.
5. Only after review, run `./tools/wiki/publish.ps1 -Publish -Message 'Update reviewed multiplayer documentation'` with the same optional page selection. The script requires selected pages already committed, uses existing Git authentication, and removes its temporary checkout after success. Main pushes alone do not publish the wiki.
6. Verify the actual live Home, sidebar/footer, team page, player/admin command tables, Configuration property section, Archive pointers and source/release scope. This local task did not publish or render-check a changed live page. Preserve current development labels until a real released artifact is verified to contain the implementation.

No automation, extra publishing credentials, new documentation framework or gameplay policy was introduced.

## FTB shared-tuple implementation follow-up — October 13, 2026

The implementation chat added the exact Teams 2101.1.11 / Library 2101.1.36 membership tuple, preserving Teams 2101.1.11 / Library 2101.1.30. Quests gates are unchanged. Canonical Teams, Integrations and Worked Scenarios pages now describe the common accepted tuple. The initial audit's incompatible-gate finding was correct for its source snapshot and is superseded by this fix; `CHECKS-2026-10-13.json` remains the original wiki audit receipt, not a fingerprint of the subsequent code.

New receipts under `build/ftb-shared-compat-audit`: `artifact-checksums.json` (official Maven checksums verified); `common-checks.log` (638 membership assertions, 68 quest assertions); `fabric-shared-runtime.log` and `neoforge-shared-runtime.log` (both PASS with Teams 2101.1.11 / Library 2101.1.36 / Quests 2101.1.36 / Architectury 13.0.11). Both real loaders initialized the co-installed mods, loaded the membership adapter, ran its 20 real-interface fixtures and shared accounting contract, and exercised production early quest-definition capture using real Library SNBT plus malformed-source rejection. These are synthetic/no-world checks, not actual manager, multiplayer-event, graphical, quest-completion or restart acceptance. No Git staging/push/publication occurred in this follow-up.

## FTB Library 2101.1.37 and approval naming follow-up — 2026-10-13

The user's Fabric IntelliJ test installed Teams 2101.1.11 / Library 2101.1.37. Read-only latest.log inspection confirmed the Library-37 gate rejected the adapter and the test world's established authority was native. M hiding reflected provider installation, not active authority. Creating an FTB party and re-placing a Crucible before activating FTB therefore did not switch the world or its machine bindings. This is a diagnosed user-reported test, not a completed FTB migration pass.

The code now accepts exact membership tuples Teams 2101.1.11 / Library 2101.1.30, 2101.1.36 or 2101.1.37. The shared quest tuple is Quests 2101.1.36 / Library 2101.1.36 or 2101.1.37 / Teams 2101.1.11 / Architectury 13.0.11; the older Quests 2101.1.30 / Library 2101.1.35 / Teams 2101.1.10 evidence-only tuple remains. This extends the earlier Library-36 follow-up; it does not accept arbitrary higher versions. All 24 published common SNBT Java sources are identical between Library-36 and Library-37. Both loader Library-37 binaries matched official Maven checksums.

Native approval now supports /essence team approve <request> [name]. The shared service requires a valid explicit name only for creation, rejecting missing/blank/too-long names with request, membership and funds unchanged. Existing-team approvals preserve the team name. The three-argument service overload passes no name instead of supplying Team. The existing native machine fixture that relied on the old default now supplies Shared workshop. No save schema, authority auto-switch, accounting, ownership policy or popup layout changed.

Receipts under build/ftb-shared-compat-audit: followup-common-checks.log (655 membership, 69 quest assertions); fabric-library-37-verified-runtime.log (1,097 command assertions plus actual initialized loader, membership API/accounting and early quest-definition checks); neoforge-library-37-verified-runtime.log (same initialized membership/quest checks); library-2101.1.37-checksums.json; library-snbt-source-comparison.json; followup-production-packaging.log (both production jars and 349 asset checks each); final-jar-audit.json (updated local development hashes, no test fixtures/bundled FTB, Library-37 gate present). The earlier Library-37 logs failed on the intentionally tightened naming requirement in the old fixture and remain distinct from these passes. Stage 9 and Archive hashes predate these jars; none is a published new beta.

Live follow-up requires restarting the IntelliJ process to load new discovery code, then explicitly prepare ftb_teams / inspect status / activate its receipt if changing the existing native world is intended. Existing bindings from another authority are not remapped; test a new physical placement after activation. A fresh world with supported FTB and AUTO selects FTB immediately. Do not reset/delete an existing world to solve this. The user continues live managers/events, two-client/offline/unloaded machinery, GUI and normal restart checks in the existing manual guide. Cross-file crash atomicity remains unjournaled/unproven. No installed mods, EULA, world data, profiles, staging, commits, pushes or publication were changed by this follow-up.

## Activation timeout and command readability follow-up — 2026-10-13

The next user-run Fabric log confirmed that Teams 2101.1.11 / Library 2101.1.37 was accepted and the real target roster was captured. Preparation at 10:28:15 produced a valid one-party/one-member receipt shown at 10:28:33; activation at 10:29:35 was after the old 1,200-tick deadline. This explains the generic rejection without claiming a successful authority switch or machine migration. The original log is retained at `build/membership-command-followup/user-activation-expiry.log`.

Preparation now allows 6,000 server ticks (about five minutes), with a visible countdown and an explicit expiry/retry notice. Status reads do not extend it. Token, financial/roster revision, readiness and current-roster validation remain required. Readable membership, team and machine commands reuse the established title/section/label styling; balances retain exact decimal shares, names replace record dumps, and command suggestions fill chat without executing mutations. Membership bare/help now displays guidance. Provider rows use last observed readiness, and failures/unobserved-adapter details remain visible to console users. Existing machine bindings from another authority are still not remapped; use a new physical placement after successful activation when testing.

`build/membership-command-followup/verified-checks-and-packaging.log` passed: 678 membership assertions (including real runtime prepare/tick/status/activate with a fake provider and no world, 80-second review, exact expiry, unchanged saved state, expired-token rejection and new preparation), 1,155 command checks, 13,452 shared presentation checks, 70,083 headless Archive integration checks, both production jars, and 349 asset checks per loader. The initial `checks-and-packaging.log` failed on a test-only trailing space in the new bare-help case; it is retained separately. `final-jar-audit.json` records the new development jar hashes, current command resources and absence of fixture/FTB class leaks. Earlier jar hashes predate this change. Live activation, real multiplayer events, graphical review and restarts remain pending; no client/world, installed mods, Git staging/commit/push or publication was changed.

## Wiki reconciliation of implementation update — 2026-10-13

The wiki chat independently rechecked the new provider gates, named approval registration/shared validation, level-2 bare/help handlers, 6,000-tick preparation and status/expiry behavior. Exact FTB tuple and naming/timeout edits already made by the implementation chat were retained. Added explicit help entries to Administration Commands; refreshed evidence scope in Version and Maintenance, Diagnostics and Testing, Teams and Shared Infrastructure, Integrations and Compatibility, and Worked Scenarios. Updating and Recovery now records the user's intentional beta save break and forthcoming patch-note disclosure rather than requiring an unplanned migration.

Current user-confirmed live evidence: real FTB authority activation and team ownership of a newly placed Crucible. Earlier pending-activation statements above are historical. Existing machines are still not migrated between providers. Two-client, offline-member, unloaded-machine, restart, graphical and interrupted-save checks remain open. The successful 678/1,155/presentation/Archive/build/asset receipts were inspected, not rerun. They do not establish a published beta or turn headless checks into visual acceptance.

Final Archive destinations remain the twelve verified entries above. No gameplay/Archive file, installed instance, world, profile, Git index, commit, remote or publication was changed. Audit and documentation check receipt were updated separately; the original dated evidence stays intact. The existing manual publication checklist still applies: review source/development scope, selectively commit/push intended docs yourself, preview the existing publisher, publish only reviewed committed pages, and inspect the live wiki. Replace source links/release labels only after actual source/release evidence exists.

Follow-up documentation checks passed: 173 internal wiki links/anchors, sidebar coverage for 20 content pages, balanced fences across 22 Markdown files, five TOML examples parsed, exact membership help/approval syntax coverage, source constants/gates checked, and no unfinished/private/user-specific content found. Scoped `git diff --check -- docs/wiki docs/wiki-maintenance` passed. HEAD remains `438404996988d730c8246f5aa5620897a795a13f`; staging remains empty. The original `CHECKS-2026-10-13.json` was preserved. The separate follow-up receipt fingerprints ten inspected implementation/test files without claiming new gameplay passes.

## Beta.5 release preparation — 2026-10-13

The user authorized committing/pushing the completed multiplayer implementation, Archive and wiki and publishing the next beta through GitHub Actions. Canonical pages now target v1.0.0-beta.5, with release-tag source links replacing uncommitted-worktree labels. Prominent new-world notices explain the player-data schema 6 to 10 incompatibility and lack of older-save migration. Existing-machine migration between providers remains unsupported; choose the provider before creating a new world. The narrow user-confirmed FTB activation/new-Crucible success and remaining live-test limits are preserved.

Release-scope documentation checks passed for all 22 canonical Markdown pages: 173 internal links/anchors, complete sidebar coverage, balanced fences and five TOML examples. The existing wiki publisher's disposable-repository tests and all four Actions workflow syntax checks passed. Historical audit receipts remain unchanged. Release notes are maintained at docs/releases/1.0.0-beta.5.md; actual publication evidence belongs in PUBLICATION.md after remote verification.

## Beta.5 publication completed — 2026-10-13

Implementation, Archive and canonical wiki were committed and pushed as `12f64fffdb8ae2ff2e3e9ddea4aed72e99defbf3`, tagged `v1.0.0-beta.5`. The live wiki is at `a73d36b07e5d420255db887af09f63497e2cf491`. CI, description synchronization and every release job passed, including NeoForge Modrinth on its first attempt. GitHub downloads and Modrinth file hashes/changelogs were verified. Modrinth project moderation remains pending, and CurseForge's public list had not yet shown the accepted beta.5 uploads at the immediate check. See [PUBLICATION.md](PUBLICATION.md) for exact links, checksums and evidence limits. Earlier unpublished-development statements in this passdown describe their historical stage.

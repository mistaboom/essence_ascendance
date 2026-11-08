# Wiki source map and review boundaries

Maintained player and pack-maker pages live in `docs/wiki/`. They are the sole
permanent local wiki originals. [PUBLISHING.md](PUBLISHING.md) owns preview,
publication and recovery; [../MAINTENANCE.md](../MAINTENANCE.md) is the project handoff.
The earlier dated audit/passdown/publication chronology remains in Git history.

## Recheck these owners when changing documentation

| Topic | Authoritative repository owners |
| --- | --- |
| Supported versions, loaders and dependency gates | `gradle/targets.json`, loader metadata and optional integration adapters |
| Public/admin command syntax | `common/src/main/java/com/mistaboom/essence_ascendance/command/`, both registration and handlers |
| Configuration keys/defaults and live refresh | `common/src/main/java/com/mistaboom/essence_ascendance/config/` |
| Saved state and upgrade boundaries | `EssenceSavedData`, `PlayerEssenceData`, version checks and maintained release notes |
| Shared ownership and machine transactions | membership services, `machine/`, `crucible/`, `infuser/`, `pylon/`, `nexus/` |
| Recipe valuation and pack-specific results | balance-generation owners, saved reports, `docs/pack-compatibility.md` |
| Preset downloads and tested pack versions | `presets/catalog.json`, immutable revision inputs under `presets/`, `tools/presets/build.py` |
| Archive navigation and displayed values | current Archive UI, guide registrations and language resources |
| Audio commands and Minecraft settings | `docs/audio/CONTRACT.md`, `docs/audio/VALIDATION.md`, audio command handlers |
| Achievement rules and native progress | `docs/achievements/CONTRACT.md`, `STATUS.md`, `PLAYER-GUIDE.md` |

A source inventory is not an executed gameplay test. Verify exact parser defaults,
ranges, lifecycle and permission checks, not just registered command names. Do not
hardcode pack-specific values into generic prose. Keep supported-version and
release references honest: unreleased source behavior is not proof of an older tag.

## Publication and verification

### Beta.17 release guide: published

The membership guide now describes runtime API detection without FTB version
allowlists, fallback to built-in teams on actual API failure or provider removal,
preserved confirmed ownership/balances and server-authoritative team controls.
The review checked `MembershipProviders`, `MembershipRuntime`, `PoolMembershipState`,
`ReservoirService`, `MachineBinding` and `TeamInterfaceNetworkService`, plus their
regressions and the deployment evidence summarized in `docs/VALIDATION.md`.

The release-page selection was `Home.md`, `_Footer.md`,
`Version-and-Maintenance.md`, `Administration-Commands.md`, `Configuration.md`,
`Integrations-and-Compatibility.md`, `Player-Commands.md`,
`Teams-and-Shared-Infrastructure.md` and `Troubleshooting.md`. These pages were
published with the ATM11 catalog selection below after exact-source CI passed.
The reported world-reopen failure has now passed local native acceptance in
ATM11: beta.17 loaded the saved profile and joined the same world after save/quit,
without the policy-cache exception or resulting datapack failure. The controlled
regression and version-specific fixture scope are recorded in
[../VALIDATION.md](../VALIDATION.md). Exact-source CI and the
[beta.17 release workflow](https://github.com/mistaboom/essence_ascendance/actions/runs/42434154081)
both passed all 324 tasks and 25 release tests. All ten release jobs succeeded;
the official artifact verification receipt is maintained in
[../MAINTENANCE.md](../MAINTENANCE.md).
Existing versioned preset downloads retain their original compatibility records;
these guide edits do not relabel their tested configuration or pack version.

### Preset catalog publication

The preset catalog adds versioned downloads with per-revision compatibility and
installation notes. Its first entry preserves the verified FTB Evolution 2
**1.5.1** inputs; the user's subsequent 1.7.0 pack update is not a new full balance
audit. The ZIP contains four TOMLs and a standalone additive datapack, plus
instructions, the project license and checksums. It excludes generated profiles,
worlds, player records and internal audit material. A completed numeric check is
reported separately from an unfinished progression/boss playthrough.

Catalog pages are generated from their maintained inputs and committed under
`docs/wiki/` for the existing publisher. New packs and revisions do not require
changes to the publisher or a new copy of the website. The original publication
scope was `Config-Presets.md`, `Preset-FTB-Evolution-2-1-5-1-r1.md`, `Home.md`,
`_Sidebar.md` and `_Footer.md`; integration documentation was outside that selection.

That selection was published on 2026-11-07 as wiki commit
`67e8ac1d7f76556f3ab27ee40dfc678057d8eb79`, from main-repository source commit
`3dd97afd1836698fdab46c1f140ff152f94b432d`. The live catalog, detail page, Home,
sidebar and footer were checked. An anonymous download of the 54,420-byte ZIP
matched its recorded SHA-256, and all individual file checksums passed. The preset
packaging suite, generated-output check and disposable wiki publisher checks
passed locally; the Configuration presets GitHub Actions run also passed on Linux.
These are publication and packaging checks, not a new gameplay balance audit.

### ATM11 preset: published with native acceptance

The published `atm11/0.14.0-beta-r1` revision targets ATM11 0.14.0-beta on
NeoForge 26.1.2 with Essence Ascendance beta.17. Its audit covered the original
298 mod jars and supplied/generated configurations. The preset contains all 90 Skills,
41 Bonuses, 66 equipment controls, 6,935 category payout ceilings across 6,934
resources, 124 reference corrections and 12 additive bulk-material tag entries.
The ZIP supplies four TOMLs and the required
`kubegroovy/data/essence_ascendance/tags/item/low_value_bulk_materials.json`, plus
instructions, license and checksums. Installation requires both `config` and
`kubegroovy` in the game directory; no generated profile or private audit files
are distributed.

The beta.17 native rebuild, reload, saved-profile validation and save/quit/reopen
passed locally on 2026-11-07. The generated result matched all 658 authored
controls, and reopening retained integrity
`26cf8a7da6e19ecb511df99fb14bdb0d8daee9834226690f1e5e35559829cd22`.
Post-reopen command validation passed at 16:00:50 with the same profile file checksum.
Exact-source CI passed all 324 tasks and 25 release tests.
The release workflow subsequently passed all ten build and publication jobs.
Progression effort and competitive endgame behavior remain design targets;
a complete progression and boss playthrough has not been completed.

The generated `Config-Presets.md` and `Preset-ATM11-0-14-0-beta-r1.md` pages,
`_Sidebar.md` and the nine release-guide pages above were published on
2026-11-07 (2026-11-08 UTC) as wiki commit
`c5d130d08c5606422311a16fe312cef67ddff963`, from main-repository source
`72d4c68478ddd405fdac21434799eea33f638d65`. The reviewed selection contained
12 pages; 11 changed and the sidebar already matched. Fresh anonymous HTTP
reads verified that all 12 raw pages exactly matched their canonical source.
The rendered catalog, ATM11 detail, Home and Version pages show the current
content, beta.17 footer, Downloads navigation and correct public ZIP link.

The public ATM11 ZIP was independently downloaded without authentication. Its
SHA-256 is `4d33c6eb5b2b7f4d3d70baa6ad5eb0a1863d332182a6cd83abb5937f877a78bd`;
all eight archive members, payload checksums and paths passed verification.
[Configuration presets run 42434315065](https://github.com/mistaboom/essence_ascendance/actions/runs/42434315065)
passed all 22 tests and the generated-output check on Linux at preset source
`c152b6b2273dde919b22b6584430d2bfad225532`. The original FTB Evolution 2 ZIP
remains byte-for-byte unchanged at SHA-256
`a5f421bbdb39e4442794789bac8229003b4769cf8018f6783a9ba550c5228879`;
its historical wiki publication receipt above is preserved.

The last recorded audio wiki publication is commit
`5762bdfbfc46c0c0fd7658a1bc32e359a9fff01a` on the separate wiki repository, from the
beta.14 acceptance record. This is historical evidence, not a fresh remote check.
Inspect the current remote through the publisher before the next update.

For each changed page, check cross-page links/anchors, command examples, TOML
syntax, version scope and source links. Update `_Sidebar.md`, `_Footer.md` and Home
when navigation changes. Preview against the actual remote before publishing so
useful wiki-side edits are retained. The publisher never force-pushes and removes
its successful temporary checkout.

The publisher's disposable Git tests (`tools/wiki/test-publish.ps1`) verify selected
page copying, history preservation, rejected inputs and cleanup. They do not prove
Minecraft gameplay, native rendering, save migration, universal mod compatibility
or human audio/visual acceptance. A main-repository push does not publish the wiki;
the three-site release workflow publishes artifacts and descriptions separately.

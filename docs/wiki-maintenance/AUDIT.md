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

### Beta.16 membership guide: prepared, publication pending

The membership guide now describes runtime API detection without FTB version
allowlists, fallback to built-in teams on actual API failure or provider removal,
preserved confirmed ownership/balances and server-authoritative team controls.
The review checked `MembershipProviders`, `MembershipRuntime`, `PoolMembershipState`,
`ReservoirService`, `MachineBinding` and `TeamInterfaceNetworkService`, plus their
regressions and the deployment evidence summarized in `docs/VALIDATION.md`.

The prepared release-page selection is `Home.md`, `_Footer.md`,
`Version-and-Maintenance.md`, `Administration-Commands.md`, `Configuration.md`,
`Integrations-and-Compatibility.md`, `Player-Commands.md`,
`Teams-and-Shared-Infrastructure.md` and `Troubleshooting.md`. Publish this set
after the beta.16 release has been verified, then record the actual wiki commit.
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
changes to the publisher or a new copy of the website. The selected publication
scope is `Config-Presets.md`, `Preset-FTB-Evolution-2-1-5-1-r1.md`, `Home.md`,
`_Sidebar.md` and `_Footer.md`; pending integration documentation is separate.

That selection was published on 2026-11-07 as wiki commit
`67e8ac1d7f76556f3ab27ee40dfc678057d8eb79`, from main-repository source commit
`3dd97afd1836698fdab46c1f140ff152f94b432d`. The live catalog, detail page, Home,
sidebar and footer were checked. An anonymous download of the 54,420-byte ZIP
matched its recorded SHA-256, and all individual file checksums passed. The preset
packaging suite, generated-output check and disposable wiki publisher checks
passed locally; the Configuration presets GitHub Actions run also passed on Linux.
These are publication and packaging checks, not a new gameplay balance audit.

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

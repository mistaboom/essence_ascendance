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
| Archive navigation and displayed values | current Archive UI, guide registrations and language resources |
| Audio commands and Minecraft settings | `docs/audio/CONTRACT.md`, `docs/audio/VALIDATION.md`, audio command handlers |
| Achievement rules and native progress | `docs/achievements/CONTRACT.md`, `STATUS.md`, `PLAYER-GUIDE.md` |

A source inventory is not an executed gameplay test. Verify exact parser defaults,
ranges, lifecycle and permission checks, not just registered command names. Do not
hardcode pack-specific values into generic prose. Keep supported-version and
release references honest: unreleased source behavior is not proof of an older tag.

## Publication and verification

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

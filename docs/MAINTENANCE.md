# Current maintainer handoff

Use the repository opened in IntelliJ as the single maintained development checkout.
This file is the starting point; old chat-by-chat handoffs are consolidated
into the feature contracts and acceptance summaries linked below.

## Working and testing

- Keep the editor-active Minecraft version at **1.21.1**. Run Gradle with Java 25;
  the 1.21.1 targets select Java 21 through their toolchain.
- `.run/` contains the eight supported client/server launch configurations and
  **Essence Ascendance - Pack Tester**. `.idea/` and the four normal loader/version
  `run/` directories belong to the retained IntelliJ setup.
- `gradle/targets.json` owns the four target/dependency definitions. Common source,
  resources and tests belong under `common/src`; generated projections are not
  authored code. Keep Gradle invocations sequential.
- Run `gradlew.bat :checkSupportedTargets --console=plain`. Exact local archive
  providers and hashes are written to `build/production-artifacts-all.json`.
- Disposable fixture launchers remain in `tools/verification`, `tools/audio` and
  the native init scripts under `gradle/`. Their generated worlds, captures,
  import kits and historical run directories are reproducible temporary output.
  They are not permanent alternative development instances.

## Maintained source of truth

| Area | Maintained entry point |
| --- | --- |
| Build, multiversion APIs, runtime boundaries | [DEVELOPMENT.md](DEVELOPMENT.md) |
| Current verification map and remaining limits | [VALIDATION.md](VALIDATION.md) |
| Release preparation and three-site distribution | [RELEASING.md](RELEASING.md) |
| Achievement IDs, rules, tests and acceptance | [achievements/STATUS.md](achievements/STATUS.md) |
| Achievement semantics and persistence | [achievements/CONTRACT.md](achievements/CONTRACT.md) |
| Achievement presentation and existing-asset provenance | [achievements/PRESENTATION.md](achievements/PRESENTATION.md) |
| Audio architecture, current evidence and authoring | [audio/PASSDOWN.md](audio/PASSDOWN.md) |
| Models, procedural assets and promoted audio | [../art/README.md](../art/README.md) |
| Canonical player/pack-maker wiki | [wiki/Home.md](wiki/Home.md) |
| Premade configuration presets and adding new packs | [../presets/README.md](../presets/README.md) |
| Wiki source review and publishing | [wiki-maintenance/PUBLISHING.md](wiki-maintenance/PUBLISHING.md) |

Keep irreproducible authoring/design references in the consolidated auxiliary
folder and source-controlled runtime assets in `art/`. Normal builds must not
depend on auxiliary files. The auxiliary index records retained originals and
the cleanup disposition. Do not copy production code or maintain a second
permanent wiki checkout there.

## Cleanup policy

Keep reusable fixtures and concise checked results; regenerate disposable worlds
when needed. Preserve user Prism instances and the normal IntelliJ run worlds.
Remove only positively identified agent-created imports/test outputs. Validate
absolute paths before recursive operations and never traverse directory junctions.
Check available Recycle Bin retention before relying on reversible disposal; do
not empty it automatically. Permanent removal needs an explicit, verified scope.

The 2026-11-07 consolidation retained the nine IntelliJ launch configurations
and four normal run roots. All six user Prism instances matched their baseline
immediately after the agent-created import was removed. The user subsequently
confirmed deleting five packs independently; FTB Evolution 2 remains user-managed.
The agent-created Prism review import, two obsolete worktrees and their merged
local branches are gone.
The auxiliary folder now contains `Audio`, `Sources`, `Reference`, `Tools` and
one index (2,069 files / 1,893,772,384 logical bytes after consolidation).
Retained authoring assets were verified by SHA-256, including the four
originals rescued from old build folders. The user subsequently confirmed
emptying the Recycle Bin; retired generated auxiliary history is not recoverable
there. Retained authoring sources remain in the organized auxiliary folder.

Workspace cleanup completed on 2026-11-07: the user manually ran the verified
PowerShell 7 cleanup script after automatic approval review blocked agent-side
removal. A read-only follow-up confirmed all 2,426 planned targets absent
(89,634 files / 50,002,353,408 logical bytes in the frozen plan). The nine IntelliJ
launch configurations, four normal run roots and their existing worlds,
`.idea/`, current production manifest and FTB Evolution 2 instance remain.
All four rescued authoring originals passed SHA-256 verification after cleanup.

The frozen plan and manual script are retained under auxiliary
`Tools/Maintenance`; plan SHA-256 is
`043fd8727dfc86ad83ecc51cf6a8f30f8a1c650dd569b461b10545d7ecfdba21`.
This cleanup is complete; do not treat that historical plan as pending work or
extend it to newly generated output.

## Current release

**The beta.18 release workflow is complete; GitHub downloads are verified.** It fixes Minecraft 26
skill outlines, continuous Essence Wings/vanilla rocket flight and Vector Boost,
switches Nexus to the ordinary fullscreen screen foundation, and fixes translated
command arguments and admin toggle activation. The user accepted the clean
Minecraft 26 playthrough on 2026-11-08. [Release notes](releases/1.0.0-beta.18.md).

The temporary skill-lab generators, launch scripts and dedicated native fixture
were removed before release. The focused line-rendering, flight, text and Nexus
regressions remain; native admin-command coverage lives in the existing server
fixture. [VALIDATION.md](VALIDATION.md) records the checks and their boundaries.

### Published beta.18 receipt

The release source is `e37a3fec9d65d67404cc2c316242284841bc5125`, tagged
`v1.0.0-beta.18`. [CI](https://github.com/mistaboom/essence_ascendance/actions/runs/42547668140)
passed all 328 Gradle tasks in 17m 10s and all 25 release-tool tests. The
[release workflow](https://github.com/mistaboom/essence_ascendance/actions/runs/42549342823)
then passed all 328 tasks in 28m 24s and all 25 release tests. All ten jobs
succeeded: build, GitHub downloads, four CurseForge uploads and four Modrinth
uploads. No upload retries or manual jar attachments were needed.

The [GitHub beta.18 prerelease](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.18)
contains four jars and their checksums. Workflow artifact `13645234922` has ZIP
SHA-256 `d887b59f63343205fe01b8d22b1e637595c490a0d26777b29482ba35d9989919`.
All four downloaded GitHub jars match that artifact's sizes and SHA-256 hashes;
the release notes and checksum asset match too. The published jars contain no
temporary lab files, lab hooks or authored regression-test classes.

Modrinth has exactly four beta.18 records with the expected game/loader versions,
dependencies and changelog. All four actual Modrinth CDN downloads match the
workflow's SHA-256, SHA-512 and sizes.

| Target | Published SHA-256 | Modrinth version |
| --- | --- | --- |
| 1.21.1 Fabric | `19ea4da217c04b8f49b6d43fd63d364eb7d888fd0d60cd2e2422377a3c28dbf5` | `dcArok3j` |
| 1.21.1 NeoForge | `a1fbe44c8ec3d48d47fb614e6d8dbfd5be13fdc88cf3ad21c813dbf7cdd00bc0` | `Qswe7kwS` |
| 26.1.2 Fabric | `8522e84c7fdd06146caec14248c2ec9919785437a13a4f4aade79f5372317f0f` | `FJiq1yXv` |
| 26.1.2 NeoForge | `89ee7110be28778037b1e2d2363ae9fe88b94ae067e73fdbacc8155e9def168b` | `E16Vu8YK` |

Verification on 2026-11-09 at 02:42 UTC found Modrinth's project still
`processing`, with `requested_status=approved`, no approval date, and anonymous
project/version APIs returning 404. Successful uploads and CDN downloads do not
establish public project approval.

All four CurseForge upload logs explicitly report success. By 02:46 UTC, its
anonymous file API exposed all four beta.18 records, and individual public
metadata matched their filenames, byte lengths, Minecraft versions, loaders and
beta release type. The file IDs are `9520621` (1.21.1 Fabric), `9520622`
(1.21.1 NeoForge), `9520624` (26.1.2 Fabric) and `9520628` (26.1.2 NeoForge).
CurseForge CDN bytes were not independently downloaded. The earlier upload/list
delay required no retry; the later public-metadata evidence is retained in
`build/beta18-cf-publication-watch.json`.

The beta.18 Home, Version and footer wiki pages were published as commit
`2dabbf698119ebf4a23a7ee71cb437909adc9195`. Fresh raw-page and rendered-HTML checks
passed; [the wiki receipt](wiki-maintenance/AUDIT.md) records their exact scope.
Local publication evidence is retained in `build/beta18-ci-verification.json`,
`build/beta18-release-summary.json`, `build/beta18-github-verification.json`,
`build/beta18-modsite-verification.json` and `build/beta18-wiki-verification.json`.

### Previous beta.17 release

**The beta.17 release workflow is complete; GitHub downloads are verified.**
Source commit `13e4bccb5115e5f751346a71c286df1785b25a48` fixes the equipment-rule
refresh race found while reopening the ATM11 audit world. It preserves eager
refresh for non-ticking automation players and defers off-thread inventory work
to the owning server thread. [Release notes](releases/1.0.0-beta.17.md).

All four local production archives passed their 68-task build/asset check, and
all 25 release-tool tests passed. The Fabric 1.21.1 native fixture passed,
including 413 two-handed checks and 132 fishing checks after a test-only heightmap
fixture repair. ATM11 0.14.0-beta on NeoForge 26.1.2 passed rebuild, reload,
saved-profile validation, save/quit/reopen in the same client session and
post-reopen validation with unchanged profile bytes. [VALIDATION.md](VALIDATION.md)
records the scope and remaining playthrough limits.

[CI run 42431826634](https://github.com/mistaboom/essence_ascendance/actions/runs/42431826634)
passed the exact source commit: all 324 Gradle tasks and 25 release tests, with
the build completing in 28m 4s. The ATM11 preset and current guides are published;
[the wiki receipt](wiki-maintenance/AUDIT.md) records source, rendered-page and
public-download verification.

### Published beta.17 receipt

The release source is `13e4bccb5115e5f751346a71c286df1785b25a48`, tagged
`v1.0.0-beta.17`. The
[release workflow](https://github.com/mistaboom/essence_ascendance/actions/runs/42434154081)
passed all 324 Gradle tasks in 27m 45s and all 25 release tests. All ten jobs
succeeded: build, GitHub downloads, four CurseForge uploads and four Modrinth
uploads. The [GitHub beta.17 release](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.17)
contains four jars and their checksums. Workflow artifact `13599801053` has ZIP
SHA-256 `d4627bfdd38fb2521095ffbbfd5354b18277c6b95bba43e1cf7372a503294258`.

Independent downloads of all four GitHub jars match the workflow's sizes and
SHA-256 hashes; release notes and the checksum asset match too. Modrinth has
exactly four matching beta.17 records, with the expected game/loader versions,
dependencies and changelog. All four actual Modrinth CDN downloads match the
workflow's SHA-256, SHA-512 and size.

| Target | Published SHA-256 | Modrinth version |
| --- | --- | --- |
| 1.21.1 Fabric | `2c6cbd666a1a8dddc5494e97d51c8211904fa42453e65f1f60083c01dceb5493` | `qOkQJoNM` |
| 1.21.1 NeoForge | `6a6ae7d787d45d1622a4131d33e648c8a5c8a25bfebe55f34183c5f73e663878` | `IW5eIwc0` |
| 26.1.2 Fabric | `52544e4d037b2d5e489217dbd4be6e247b563f208c589f31e182f43f894a6271` | `maYQVIxu` |
| 26.1.2 NeoForge | `49950dc20c003612c20cd9c0920ff2730b01b5f41e0caaa3fced56581535600d` | `GDwnbD1o` |

Verification on 2026-11-08 at 01:58 UTC found Modrinth's project `processing`,
with `requested_status=approved`, no approval date and anonymous project/version
APIs returning 404. Upload acceptance and accessible CDN bytes do not establish
public approval. All four CurseForge upload logs explicitly report success, but
the author file API returned 403; beta.17 file IDs, public visibility and
independent CDN comparisons remain unverified. At 01:59 UTC, the retrieved
[public file list](https://www.curseforge.com/minecraft/mc-mods/essence-ascendance/files/all?page=1&pageSize=20&showAlphaFiles=hide&sortBy=dateCreated&sortOrder=desc)
showed 42 results, including all four beta.16 and beta.15 files, with beta.16
newest and no beta.17 files observed. Its cache freshness is unknown; absence
is not proof of rejection. Do not re-upload because a public list has not updated.

Detailed local receipts are `build/beta17-github-verification.json`,
`build/beta17-modsite-verification.json` and
`build/beta17-workflow-verification.json`; the build log is
`build/beta17-release-workflow-build.log`. These published hashes are independent
of the local jars used for native acceptance. Preserve the published beta.16
artifacts and their historical receipt below.

### Published beta.16 receipt

Beta.16 removes FTB version allowlists, tries the installed membership API, and
recovers to built-in teams on an actual failure while preserving confirmed
ownership and balances. Server-authoritative team controls remain available
after fallback. [Release notes](releases/1.0.0-beta.16.md) describe the changes
and the beta.15 upgrade path, which needs no reset or rebuild.

The membership changes passed the full local four-target gate (324 tasks), with
482 provider, 169 binding and 281 interface assertions per Minecraft version.
Native NeoForge 26.1.2 checks passed with FTB absent and the pack's Teams 26.1.2.5 /
Library 26.1.2.11 binaries. Deployment to the FTB Evolution 2 server restored
available FTB membership; saved-balance validation passed without regeneration,
and the user confirmed the fix worked. [VALIDATION.md](VALIDATION.md) records
the scope. That local candidate still carried beta.15 metadata; it is distinct
from the published beta.15 and beta.16 artifacts.

The release source is `7d41a7a7ddc171ebbcdedf09335e25f00a3107a8`, tagged
`v1.0.0-beta.16`. The
[release workflow](https://github.com/mistaboom/essence_ascendance/actions/runs/42428827966)
passed all 324 Gradle tasks in 21m 26s and all 25 release tests. All ten jobs
succeeded: build, GitHub downloads, four CurseForge uploads and four Modrinth
uploads. The [GitHub beta.16 release](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.16)
contains four jars and their checksums. Workflow artifact `13596993618` has ZIP
SHA-256 `f136512053198229feecc149842f62147be1414a5d9bde0898c189da0ba2e8eb`.

Independent downloads of all four GitHub jars match the workflow's sizes and
SHA-256 hashes; release notes and the checksum asset match too. Modrinth has
exactly four matching beta.16 records, with the expected game/loader versions,
dependencies and changelog. All four actual Modrinth CDN downloads match the
workflow's SHA-256, SHA-512 and size.

| Target | Published SHA-256 | Modrinth version |
| --- | --- | --- |
| 1.21.1 Fabric | `970c2b66fe38686f69c8dd29a7c83f22e074e789394c466dec7037c648cb8147` | `oifLLlRZ` |
| 1.21.1 NeoForge | `56bfcce77a5e593396dfd09f2d938321cb3cdd4e471353cb1b105bf05d9babe0` | `TvXVWE4y` |
| 26.1.2 Fabric | `c4013d6a45531cedda3c0c6a0cc79ef5a76a6266853eee48e3cf95e0cb3ed5d7` | `jC7vw8Y5` |
| 26.1.2 NeoForge | `231db8a0098d8f6794cfbd7c7c87b89d6b56c6807469cd1859eda4020a32de90` | `vLCUj4WX` |

Verification on 2026-11-08 UTC still found Modrinth's project `processing`, with
`requested_status=approved`, no approval date and anonymous project/version
APIs returning 404. Upload acceptance and accessible CDN bytes do not establish
public approval. All four CurseForge upload logs explicitly report successful
publication, but the author file API returned 403; exact file IDs, public
visibility and independent CDN comparisons remain unverified. The retrieved
public list showed beta.14 and may be cached; a fresh-query fetch was unavailable.
Do not re-upload because a public list has not updated. Detailed local receipts
are `build/beta16-github-verification.json`,
`build/beta16-modsite-verification.json` and
`build/beta16-workflow-verification.json`; the build log is
`build/beta16-release-workflow-build.log`.

### Published beta.15 receipt

Beta.15 adds the 69-achievement implementation and the **Essence Ascendance** tab
label. The user accepted the visual result on 2026-11-07 and requested cleanup,
commit, push and publication. [Release notes](releases/1.0.0-beta.15.md) describe
player-visible changes. The release source is commit
`f11508b4e2dc888b8067d6b7830c2f8fe1abb5fa`, tagged `v1.0.0-beta.15`.
[CI](https://github.com/mistaboom/essence_ascendance/actions/runs/42390995767)
passed all 324 Gradle tasks and 25 release tests. The
[release workflow](https://github.com/mistaboom/essence_ascendance/actions/runs/42392955863)
passed its own 324-task gate in 16m 26s and all 25 release tests. All ten jobs
succeeded: build, GitHub downloads, four CurseForge uploads and four Modrinth
uploads. The [GitHub beta.15 release](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.15)
contains the four jars and their SHA-256 checksums. Workflow artifact
`13582702242` is the release artifact set; its ZIP SHA-256 is
`f9e350307ac23c31cd16bcbec6d90c946fbaf5108d5f93f91951bfd02ea880f9`.
All four actual GitHub downloads match the workflow's file sizes and SHA-256
checksums; the published notes and checksum file match too. Modrinth has exactly
four beta.15 records, with matching metadata, dependencies and changelog. All four
Modrinth CDN downloads match the workflow's SHA-256, SHA-512 and size.

| Target | Published SHA-256 | Modrinth version |
| --- | --- | --- |
| 1.21.1 Fabric | `acf8dccd9e777611536387cf9915704b3ff00a00728b810e5dc77db6ed7c8685` | `QUP88XXW` |
| 1.21.1 NeoForge | `e819af42cb02940a9b4c2c777358da404e1681c05108ff4d021f393f8cfb4b72` | `heYRx495` |
| 26.1.2 Fabric | `0e38e38e0355a6d91f46dc78558f8b6a3553c948dfad900212b6af4d5da1f9d3` | `fka2XdYH` |
| 26.1.2 NeoForge | `0e17c592d89a4b4269ff46327f91a045dec22e332dce4ccc55f3a983ccc52908` | `1dzvWBHD` |

Modrinth's project remains `processing`, with `requested_status=approved` and no
approval date; its anonymous project/version APIs return 404. Successful uploads
and accessible CDN bytes do not mean the project is publicly approved. CurseForge
accepted all four uploads, and the user confirmed its author view says
**processing** on 2026-11-07. The observed public file list still showed beta.14
and the author file-list API returned 403. Exact CurseForge file IDs, public
visibility and independent CDN comparisons remain unverified; do not re-upload
merely because the public list has not updated. Local detailed receipts are
`build/beta15-github-verification.json` and `build/beta15-modsite-verification.json`.

Native fixtures and rendered captures remain distinct from a human playthrough
of every achievement. The published hashes above come from the release workflow;
local build hashes are separate and must not be substituted for them.

Local beta.15 validation passed: the complete four-target gate ran 324 tasks
(319 executed) in 20m 52s; all 25 release selection tests and six achievement
launcher tests passed. The four provider-selected jars staged successfully.
Staged-file secret scanning and whitespace checks passed. The source snapshots
in `art/` and the audio machine-readable receipts are unchanged.

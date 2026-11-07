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
originals rescued from old build folders. Historical generated auxiliary folders
were sent to the Recycle Bin, but a later audit found only 11 of 14 retired roots
still present; full recovery of that generated history is not guaranteed.

The remaining workspace cleanup covers 2,426 explicit generated targets,
89,634 files and 50,002,353,408 logical bytes. Automatic approval review rejected
execution twice, including after the user's explicit permanent-removal approval;
none of those targets was removed. The frozen plan is
`build/beta15-workspace-cleanup.json` (SHA-256
`043fd8727dfc86ad83ecc51cf6a8f30f8a1c650dd569b461b10545d7ecfdba21`).
Do not describe this remaining cleanup as completed or bypass the execution block.
The auxiliary `Tools/Maintenance/Invoke-WorkspaceBuildCleanup.ps1` and adjacent
README provide a manual cleanup package. Its default preview passed for all
2,426 targets and four protected authoring backups. Only the user's explicit
manual `-Apply` execution performs permanent removal; Apply was not run here.

## Current release

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

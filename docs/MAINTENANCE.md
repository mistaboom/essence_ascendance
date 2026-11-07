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
Use the Windows Recycle Bin for reversible disposal; do not empty it automatically.

## Current release

Beta.15 adds the 69-achievement implementation and the **Essence Ascendance** tab
label. The user accepted the visual result on 2026-11-07 and requested cleanup,
commit, push and publication. [Release notes](releases/1.0.0-beta.15.md) describe
player-visible changes. Final commit, CI and publication receipts are recorded
here when the release workflow finishes. Native fixtures and rendered captures
remain distinct from a human playthrough of every achievement.

Local beta.15 validation passed: the complete four-target gate ran 324 tasks
(319 executed) in 20m 52s; all 25 release selection tests and six achievement
launcher tests passed. The four provider-selected jars staged successfully.
Staged-file secret scanning and whitespace checks passed. The source snapshots
in `art/` and the audio machine-readable receipts are unchanged.

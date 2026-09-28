# Pack Tester

## First use in IntelliJ

1. Open this live Gradle project. In the **Gradle** tool window, click **Sync All Projects** (called **Reload All Gradle Projects** in some versions).
2. The project and Gradle JVM must use JDK 21 or newer. If needed, select the installed JDK under **File → Project Structure → Project SDK**, and **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM**. Do not change Prism's game settings as part of this setup.
3. In the top Run widget, select **Essence Ascendance — Pack Tester** and press Play (or **Shift+F10**).
4. Review the selected instance, loader and complete destination. Close that instance's Minecraft, check the explicit closed-game confirmation, then choose **Build & Deploy** or **Build, Deploy & Launch**.

The project supplies `.run/Essence Ascendance - Pack Tester.run.xml`; do not recreate it. It is a **JAR Application** configuration with `:pack-tester:installDist` as a finite before-launch step. The desktop JVM starts after Gradle completes. This avoids recursive Gradle locking even when the IDE delegates ordinary Java Application configurations to Gradle. No `workspace.xml`, global IDE settings or existing Minecraft run configurations are supplied or overwritten.

If the configuration is absent, wait for Gradle import, then open **Run → Edit Configurations** and locate the supplied configuration. Its JAR is `$PROJECT_DIR$/pack-tester/build/install/pack-tester/lib/pack-tester.jar`, working directory is `$PROJECT_DIR$`, and program arguments are `gui`. Its before-launch Gradle task is `:pack-tester:installDist`. Do not convert it to a Gradle `run` task. See JetBrains' [JAR Application documentation](https://www.jetbrains.com/help/idea/run-debug-configuration-jar.html).

### Optional separate toolbar button or shortcut

These are personal IDE settings. Add them yourself if wanted; no plugin is needed.

1. **Settings → Tools → External Tools → +**. Name: `Essence Ascendance — Pack Tester`. Program: `powershell.exe`. Arguments: `-NoProfile -File "$ProjectFileDir$\tools\pack-tester.ps1" gui`. Working directory: `$ProjectFileDir$`. Enable the console if you want bootstrap diagnostics. Do not add an execution-policy bypass or change global policy.
2. **Settings → Keymap → External Tools**, find that tool, right-click → **Add Keyboard Shortcut**, and choose an unused shortcut.
3. For a separate button, right-click the main toolbar → **Customize Main Toolbar** → choose a toolbar group → **Add Action** → **External Tools** → select the tool. Apply.

These are supported [external tool](https://www.jetbrains.com/help/idea/configuring-third-party-tools.html) and [toolbar customization](https://www.jetbrains.com/help/idea/customize-actions-menus-and-toolbars.html) features. The regular Run widget works without this optional setup.

## Windows fallback and noninteractive use

From the project root in PowerShell:

```powershell
.\tools\pack-tester.ps1 gui
.\tools\pack-tester.ps1 list
.\tools\pack-tester.ps1 inspect --instance "$env:APPDATA\PrismLauncher\instances\YOUR_INSTANCE_ID"
.\tools\pack-tester.ps1 deploy --instance "$env:APPDATA\PrismLauncher\instances\YOUR_INSTANCE_ID" --confirm-closed
.\tools\pack-tester.ps1 deploy --instance 'D:\Portable Prism\instances\YOUR_INSTANCE_ID' --root 'D:\Portable Prism' --prism 'D:\Portable Prism\prismlauncher.exe' --confirm-closed --launch
.\tools\pack-tester.ps1 recover --instance "$env:APPDATA\PrismLauncher\instances\YOUR_INSTANCE_ID" --confirm-closed
```

The fallback compiles/installs only the helper, waits for Gradle to finish, then starts a separate Java process. It uses `JAVA_HOME`, otherwise `java` on PATH. If local policy disallows PowerShell scripts, run the supplied files directly without changing policy:

```powershell
.\gradlew.bat :pack-tester:installDist
java -jar .\pack-tester\build\install\pack-tester\lib\pack-tester.jar gui
java -jar .\pack-tester\build\install\pack-tester\lib\pack-tester.jar help
```

Replace `gui` with the same CLI arguments for noninteractive operation. `--project` accepts another working directory. CLI deployment always requires an explicit `--instance`; it never deploys the GUI's remembered target. `--confirm-closed` is an assertion that this target's game is closed and will stay closed during the operation. It cannot override positive running evidence. No arbitrary-JAR deployment or compatibility override is exposed.

## Discovery, settings and compatibility

The picker reads `%APPDATA%/PrismLauncher`, each root's `prismlauncher.cfg` (`InstanceDir` and `AdditionalInstanceDirs`), and manually browsed roots/instance directories. **Browse** also accepts an individual instance or its game/mods directory. Use Browse on a portable application's root first to associate custom instance directories for optional launch. **Prism executable…** saves its executable path. Refresh rereads metadata and dependencies off the UI thread.

Identity comes from `instance.cfg` plus `mmc-pack.json` format 1, `InstanceType=OneSix`, and component UIDs. Display names are not IDs. Following [Prism's gameRoot implementation](https://github.com/PrismLauncher/PrismLauncher/blob/develop/launcher/minecraft/MinecraftInstance.cpp), `minecraft` takes precedence; `.minecraft` is used only if it exists and `minecraft` does not. The actual resolved mods path is displayed before execution. Duplicate display names remain separate rows. Missing remembered targets leave no selected replacement.

Machine-specific values live only in ignored `.pack-tester/settings.json`: `roots`, `browsed`, `selected`, and `executable`. The initial local selection is seeded during setup, never hardcoded into shared source. UUID build receipts live in ignored `.pack-tester/results`. There is no pack-name branching.

`SUPPORTED` means the instance Minecraft target, supported loader and resolved required mod metadata match. The exact Minecraft version comes from `gradle.properties`, even when a mod manifest declares a broader range. Required dependency constraints come from the existing loader manifests before the build and from the built JAR before deployment. Fabric and NeoForge remain distinct; Forge, Quilt and vanilla are blocked. The current NeoForge language-loader contract is `javafml [4,)`. Unknown range syntax, custom component patches, malformed/ambiguous metadata, or inaccessible paths produce an explanation and block deployment. Top-level dependency metadata is resolved; a required dependency available only in nested libraries must be reviewed rather than assumed present. Optional dependencies remain optional. The helper does not inspect or modify account tokens.

The game still needs Java 21 or newer. Prism can choose Java dynamically; the helper's JDK and cached launcher values do not prove the game JVM version. The GUI states this limitation and never modifies Prism Java, memory, accounts, or pack settings. Symbolic links/junctions in deployment paths are conservatively rejected rather than risking a redirected write.

## Build and deployment contract

`pack-tester` is an ordinary Java application module. Root Loom/Architectury configuration is restricted to `common`, `fabric`, and `neoforge`; the mod projects do not depend on the helper. Gson/TOML parsers are helper-only dependencies. No runtime mod initialization or gameplay code is changed.

`PackService` is the one GUI/CLI workflow. Its extension points are `PrismDiscovery`, `Compatibility`/`ModMetadata`, `ProductionBuild`, `Deployment`, `RunningCheck`, `PrismLaunch`, and `LocalSettings`. `PackTesterWindow` only presents/requests operations.

For the chosen loader, `ProductionBuild` invokes this repository's `GradleWrapperMain` with an argument vector (no command shell). `:packTesterNeoforgeArtifact` or `:packTesterFabricArtifact` depends only on its actual `:neoforge:remapJar` or `:fabric:remapJar`. The Gradle receipt contains that task's `archiveFile`, loader, Minecraft version, request UUID and SHA-256. No newest-file search is used. Compilation, resource generation, and the existing required asset-ownership check stay in the dependency graph. Routine deployment never invokes `clean`, full `build`, full `check`, or expensive balance invariants. Existing verification tasks remain enabled.

An immutable operation target and cross-process target lock span build, deployment and optional launch. The target metadata/layout is reread after the build and must match the frozen target. Build failures/cancellation cannot enter installation. A GUI Cancel request suppresses installation and launch immediately, but waits for this Gradle invocation to finish: no shared daemon, Prism, Minecraft or unrelated Java process is terminated. Close the GUI again once idle. Force-killing the JVM or powering off can leave a journal; use recovery before starting the game.

Before replacing anything, the tool stages and validates the archive outside active mods, hashes it against the build receipt, and inventories existing JARs by authoritative loader metadata. Unrelated files, including filename lookalikes, are preserved. Ambiguous multi-mod bundles or bundled Essence Ascendance copies block deployment. Duplicate standalone own-mod copies are all backed up, including a previous copy for the other loader. Only the selected loader's replacement is installed.

Each target stores a lock and transaction state in `<instance>/.essence-ascendance-pack-tester/`. Each UUID subdirectory retains prior copies and a deployment receipt. `pending.json` is written before moving old copies; it binds the operation to the exact game directory and records names/hashes. Same-filesystem atomic moves and rollback restore previous copies on handled failures. The new artifact is rehashed before committing. Interrupted prepared transactions roll back through **Recover interrupted deployment** or `recover`; committed transactions are verified and finalized. Unexpected files/hashes stop recovery rather than overwrite them. Keep the game closed until recovery succeeds; preserve the journal and backups if manual review is needed. Backups are never automatically pruned.

Running checks look for Java process arguments referencing this exact instance and file-lock evidence from its `latest.log`; command lines/tokens are never displayed or persisted. Prism can send launch data privately and Windows can hide arguments, so absence of a match/lock is always **unknown**, never proof the game stopped. Each GUI operation requires a closed-game checkbox and destination confirmation; CLI requires `--confirm-closed`. Keep other installers and the game away from that mods directory during the operation. No tool can prevent a separately launched program from racing an installation without coordinating with it.

Launch runs only after explicit **Build, Deploy & Launch**, successful verified deployment and a final cancellation gate. The executable must be a selected `prismlauncher.exe` which identifies itself through `--version`. The target must map uniquely to its actual folder ID within the root's configured instance directories. Launch uses [Prism's supported CLI](https://prismlauncher.org/wiki/getting-started/command-line-interface/): `--dir ROOT --launch ID`. A launch request is not evidence that Minecraft started successfully; inspect the instance's `logs/latest.log` afterward. Directly browsed instances without a verified root can deploy but cannot launch.

**Open reports** opens `minecraft/config/essence_ascendance/reports` (or the resolved `.minecraft` equivalent), the existing `BalanceReportLayout` location. No config, balance profile, world, script, dependency JAR or generated report is copied, regenerated, deleted or rewritten by deployment.

## Focused verification and evidence

```powershell
.\gradlew.bat :pack-tester:regression :pack-tester:installDist
# Run directly, OUTSIDE Gradle: this test invokes the real wrapper for each loader.
java -cp 'pack-tester/build/classes/java/test;pack-tester/build/install/pack-tester/lib/*' dev.essence.packtester.PackTesterTest "$PWD" production-smoke
```

The regression suite uses temporary fixture instances under `pack-tester/build`: discovery, duplicate names, custom roots, both directory layouts/loaders, incompatible/missing metadata, dependency constraints, argument boundaries, failed builds, cancellation, exclusive Windows file locks, duplicate own JARs, unrelated preservation, rollback/recovery and launch gates. The separate production smoke uses each real build task and receipt, deploys only to temporary fixtures, and checks that helper classes are absent from production JARs. It never launches Prism. Do not run the production smoke from a blocking Gradle JavaExec task.

Keep actual code/fixture test results separate from Windows GUI, IntelliJ, Prism-launch, and Minecraft/gameplay results. Per-pass status, machine diagnostics and timestamped validation logs belong in the auxiliary `pack-compatibility/HANDOFF.md`, `chats/chat-NN.md` and `validation/chat-NN/`, not in competing rolling documents here. Normal builds never depend on the auxiliary folder. Generated fixture/build directories are disposable; real instance backups, profiles and human configuration are not.

## Runtime compatibility and balance baseline

Dynamic creative-tab catalogs use shared lazy variant suppliers with native loader append callbacks. NeoForge's append path avoids Architectury's empty-anchor `acceptAfter` behavior, which is incompatible with newer NeoForge insertion validation. Keep supplier-time catalog resolution and the same item components/order on both loaders.

The optional JEI tooltip-search hook acknowledges JEI's completed initial index and resource-reload rebuilds. Only a newer Essence tooltip revision requests another full index rebuild; its elapsed time is logged. This avoids repeating JEI's own indexing when the inventory opens. It does not make the pack's initial indexing or the resource reload for newly selected Latent Ore host textures free. Measure those phases separately from balance generation and cached-profile loading.

Start with `inspect` and the deployment receipt. Record the real instance/game directory, `instance.cfg` managed pack version, `mmc-pack.json` Minecraft/loader versions, installed mod manifest versions, and installed artifact SHA-256. Read the game JVM version from its fresh log; record the Gradle JVM separately. A successful build, `SUPPORTED` result, or accepted Prism launch request does not establish successful world loading.

Preserve an existing unmodified-pack startup log when available. Check that it contains no own-mod discovery/initialization, reaches the title screen and exits normally, and compare its discovered mod names with the deployed run. Name equality establishes the observed mod list, not historical byte equality. Record the baseline's actual scope: a title-screen run does not verify world creation or gameplay. Never remove pack mods to make our mod work.

For a fresh baseline, create a clearly named Survival world with commands enabled for diagnostics. The current operator commands (permission level 2) are:

```text
/essence debug balance summary
/essence debug balance fingerprint
/essence debug balance validate
/essence admin balance export
```

`summary` reads the active snapshot. `fingerprint` checks identity/staleness; it does not hash every recipe's behavior or all script contents. `validate` validates the saved document and its agreement with the active snapshot. `export` writes derived reports without rebuilding or replacing the profile. Preserve the command responses from `logs/latest.log`, the report guide and relevant reports, profile/input hashes, world name, capture time, and deployed artifact hash. Copy only relevant diagnostics; omit account data and whole worlds.

The profile is `<game>/config/essence_ascendance/generated_balance.json.gz`; reports are beside it under `reports/`. A missing profile before the first world is expected. Balance must be installed on the Overworld level-load event **before initial spawn chunk generation**, because adaptive Latent Ore consumes the generated policy. Do not move this initialization to `SERVER_STARTED` to avoid a failure. Do not copy a development profile into a pack, delete a profile to inspect it, or run `balance rebuild` as a read-only check.

An existing profile can also be inspected through the supplied export task:

```powershell
.\gradlew.bat :common:exportSavedBalance "-PbalanceConfigDir=C:\path\to\instance\minecraft\config" --console=plain
```

This task decodes the saved profile and writes reports; it checks that the profile and both human TOML inputs remain byte-identical. It does not open a world or regenerate balance. The optional `:common:balanceAscensionPolicyInvariants -PattunementReplayProfile=...` performs a separate read-only replay with output in the project's `build/`; it is not part of ordinary deployment.

Add `-PbalanceMemoryProbe` to `:common:exportSavedBalance` for an isolated retained-heap measurement instead of report export. It measures the parsed document, validated active profile, and typed data after releasing the document, while preserving the same protected inputs. Explicit collection is confined to this diagnostic JVM; the game is never attached or opened. Run it with Minecraft closed, and distinguish its profile footprint from the full pack's live heap or any claim of a leak.

After the first capture, save/quit and reopen the same world, then repeat summary/fingerprint. Confirm the log loaded the saved profile instead of generating again and compare its hash. For a large profile, use the read-only offline decode/export while Minecraft is closed instead of allocating another full profile with live validate. Exercise a machine transaction and the Archive using synchronized values. Record command-granted test items separately from an actual survival path (natural Latent Ore acquisition, smelting, recipe discovery and first machine crafting). Report suspicious values and unsupported evidence without tuning constants during initial measurement. A command-assisted machine/UI smoke check does not establish survival accessibility.

If runtime calibration fails before a profile can be committed, the service saves a failure-only evidence envelope at `<game>/config/essence_ascendance/diagnostics/failed-generation-<uuid>.json.gz`. This is explicitly non-installable diagnostic data, not a live profile. It preserves the expensive collected evidence so the existing `regenerateSavedBalance` development task can reproduce runtime generation offline with `-PsavedBalanceConfig=<game config>`, `-PsavedBalanceOutput=<isolated output outside config>` and `-PsavedBalanceSource=<exact failed diagnostic>`. Source, any installed profile and human inputs stay unchanged. Never copy the isolated candidate into the pack to bypass native validation, and never describe offline replay as gameplay evidence.

The shared store writes compact JSON through gzip without changing its canonical integrity. The authoritative file is `generated_balance.json.gz`, bounded at 256 MiB stored / 4 GiB inflated, as are compressed failure captures. Reads pool repeated strings within a bounded per-read cache; no global string interning is used. An existing plain development profile is retained and requires an explicit rebuild when no current compressed profile exists; it is never silently migrated or deleted. A failed disk commit also attempts to retain `diagnostics/uncommitted-profile-<uuid>.json.gz`: that candidate passed validation but was never installed. Neither diagnostic file is selected automatically as live authority. Actual pack acceptance still requires native execution.

After typed validation, the active document retains its duplicate evidence/economy JSON as privately owned compressed sections instead of full trees. Typed gameplay data and all generated values remain unchanged. Complete profile writes stream those sections through the existing canonical writer; section hashes and diagnostic APIs retain their contracts. The snapshot does not depend on a backing file remaining unchanged or present. Explicit requests for a large JSON section still materialize that section, so use the offline tools for large inspections. Compaction preserves the saved format and does not require a rebuild.

Report tables spill beyond 64 KiB to temporary files owned by the export, then publish through the same atomic writer and clean up those temporary files. This keeps large acquisition CSVs out of heap memory while retaining all rows; individual derived reports are bounded at 4 GiB. Spreadsheet applications may have lower row limits, so use a CSV reader for complete large tables. Profile storage/reopen and large CSV export have constrained-heap regression coverage; these tests do not establish gameplay success.

## Balance performance evidence

Existing-profile startup and full generation are separate performance budgets. Later integration work must preserve both. Chat 2B — Balance Load & Generation Performance follows Chat 02, before the existing Chat 3 through Chat 8 integration passes.

`BalancePerformance` records structured `Balance performance` log events with one process-scoped operation ID, trigger, begin/end/failure, outcome, workload counters, reuse/rescan/report/publication flags and nested phase timings. Progress is rate-limited, with a bounded ten-second heartbeat reporting the current phase; no invented percentage or per-item log is emitted. Final records give inclusive and exclusive nanoseconds: sum exclusive values plus unattributed time, never nested inclusive totals. An operation ending `loaded_marked_stale` passed validation but has reported identity/input staleness under the existing explicit-rebuild policy; it must not be described as an identical-environment valid-profile baseline.

Saved-profile instrumentation separates file locate/open/parse, integrity, typed decode, runtime decode/constructor validation, explicit validations, compressed retention, identity checks, mapping preparation, publication and synchronization. Full generation separately measures evidence collection, data indexes/providers, acquisition, routing, production/conservation, runtime generation, serialization and reports. Resource-manager replacement, explicit fingerprint checks, explicit rebuild and actual player snapshot sends have distinct operation reasons. The early Overworld callback and every validation/failure-retention rule remain intact.

Add `-PbalancePhaseProbe` to the existing `:common:exportSavedBalance -PbalanceConfigDir=<existing game config>` task for read-only parse/decode phase measurements without report writes. Run with Minecraft closed. It verifies the profile and both human TOMLs remain byte-identical, does not force collection, and cannot check the live pack fingerprint or reproduce client/terrain/loading costs. It is mutually exclusive with `-PbalanceMemoryProbe`. Preserve the `BALANCE_PERFORMANCE_OFFLINE` record with artifact/profile hashes, Java version and a clear offline label. No profile removal, regeneration or installation is needed to inspect it.

JEI revision logs identify changed snapshot type and entry count, native indexed revision, and the cause/duration of explicit reindexing. Identical snapshots and already-empty clears do not invalidate search; genuinely new late server data still must. A memory reduction, successful compilation, or faster first opening does not establish same-JVM reopening performance.

## Shared rules for later passes

- Edit the live project directly; no ZIP delivery or duplicate source authority. Live source defines implementation state; the user's latest instructions define desired behavior.
- Inspect, extend and migrate existing shared systems. Do not create parallel balancing frameworks, superficial wrappers, ATM10-specific tables, pack-name switches, legacy aliases or obsolete-schema migrations unless explicitly requested.
- Preserve Fabric and NeoForge, six Essences, existing tiers/accounting, established gameplay/UI and balance/worldgen lifecycle requirements unless a demonstrated defect requires a targeted fix. Optional integrations remain optional and version-aware.
- Keep expensive environment analysis out of ticks, tooltips and transactions. Keep developer diagnostics out of player-facing Archive content and tooltips.
- Preserve both saved-profile startup and full-generation performance budgets, with separate evidence for stale checks, explicit rebuild, client indexing, terrain and launcher costs. Do not bypass validation or weaken fingerprints to improve a timer.
- Update affected regression tests in the same pass; run focused checks separately. Keep ordinary Build & Deploy fast and never globally disable tests.
- Preserve and extend this Pack Tester; deploy only through its shared backend. Never delete worlds or human-authored configuration. Name generated files requiring an explicit rebuild rather than silently wiping them.
- Fix demonstrated launch/runtime failures when encountered instead of deferring solely because another numbered pass owns the area.
- No staging/commit by default, Git push/publication, destructive reset, unrelated revert, branch or worktree change without explicit instruction. If a commit is requested, reference issue #28 without closing it.
- The user performs all mouse clicking, including Minecraft, Prism, Pack Tester and IntelliJ. Do not take IntelliJ screenshots or control its UI; project-file edits and command-line verification are allowed. Supply the smallest concrete game procedure and read its resulting logs/reports directly.

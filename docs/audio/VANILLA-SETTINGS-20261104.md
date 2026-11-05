# Vanilla audio settings follow-up — 2026-11-04

**Four-target build, exact packages and two native-world checks PASS; distinct-client gameplay and publication PENDING.**
This beta.14 follow-up replaces the separate EA audio preferences with Minecraft's
normal sound and subtitle controls. The user's “everything works now” records
functional feedback on [candidate G](HUD-COMMANDS-20261104.md); its passing
[receipt](HUD-COMMANDS-20261104-VALIDATION.json) remains historical evidence for
those exact inputs. It does not verify this later simplification or establish
unreported long-loop/mix and rights approval.

## Change and evidence boundary

- `AudioPreferences` and `AudioSettingsScreen` are removed. Minecraft's Master and
  category sliders are the sole volume controls; authored gains, including the
  approved flight gain of 1.0, and approved sound designs remain unchanged.
- EA captions follow Minecraft's **Show Subtitles** setting. Neutral relative
  captions, native/supplemental duplicate suppression, native bottom-right
  placement and the existing smooth HUD-card lift remain shared behavior.
- The obsolete EA preference file is ignored and left on disk. No deletion or
  migration is needed.
- Local commands are `/essence audio` / `help`, `audition <semantic>`,
  `start <profile>` and `stop`. `/essence audio settings` is removed. Operator
  diagnostics remain `/essence debug audio [help]` and
  `/essence debug audio play <semantic>`; normal permissions and routing apply.

The [frozen input snapshot](../../build/audio-vanilla-settings-release-20261104/inputs.json)
contains **2,047 inputs**, fingerprint
`411b786632e666e8b62e71fd40695ab11f13ea2d1f01fb87deb919a2e641cea6`.
The [input delta](../../build/audio-vanilla-settings-release-20261104/input-delta.json)
records **two removals and 17 changes**; all **345 art inputs** match candidate G.
The frozen fingerprint remains unchanged after the passing build. The
[maintained receipt](VANILLA-SETTINGS-20261104-VALIDATION.json) records
`BUILD_AND_WORLD_PASS_GAMEPLAY_AND_PUBLICATION_PENDING`.

`:checkSupportedTargets` passes in **17m 59s**, **282 tasks** (260 executed,
22 up-to-date), actual exit **0**. The [guard receipt](../../build/audio-vanilla-settings-release-20261104/supported-targets.receipt.json)
records **1,080.3402 seconds**, peak committed memory **4,203,433,984 bytes**, no
remaining owned process and no forced termination. These are build measurements,
not native gameplay performance results.

The [exact package review](../../build/audio-vanilla-settings-release-20261104/package-validation.json)
passes all four providers/pins: each contains **49 approved samples, 62 registered
events and 45 custom definitions**, with matching processed resources and no
retired `AudioPreferences`/`AudioSettingsScreen` classes or menu labels. The
[release checks](../../build/audio-vanilla-settings-release-20261104/release-tests.log)
pass **25 tests**. The [launcher checks](../../build/audio-vanilla-settings-release-20261104/launcher-tests.log)
run **18 tests: 17 pass, one skips** because the Windows symlink privilege is
unavailable.

Exact packaged native-world checks pass on **1.21.1 Fabric** and **26.1.2
NeoForge**, each with **36 matrix cases and 171 assertions**: **72 cases / 342
assertions** total, using hidden clients and native OpenAL playback. The guarded
runs take **74.0347 seconds / 1,870,843,904 peak committed bytes** for Fabric and
**68.0280 seconds / 3,003,781,120 bytes** for NeoForge, both actual exit **0** with
no owned processes remaining. These controlled world snapshots do not substitute
for paid-transfer gameplay or distinct-client multiplayer. The 1.21.1 Fabric
distinct-client session is active with its first real interface/team paths
passing; 26.1.2 NeoForge remains pending. Pack Tester `installDist` also passes in
**10.0126 seconds**, four tasks up-to-date.

A release reproducibility defect was confirmed: Git's CRLF normalization would
invalidate all **49 approved recipe hashes**. The exact-byte rules in
`.gitattributes` now cover `art/source/audio/**`, `art/audio-*.json` and
`docs/audio/*.json`; restaging preserved the working bytes. An actual
[checkout-index verification](../../build/audio-vanilla-settings-release-20261104/git-byte-preservation.json)
passes **313 files each** with `core.autocrlf=true` and `false`. This Git-storage
repair is separate from the 2,047-input runtime freeze; no production code changed
after the gate.

## Completion ledger

| Item | Current result |
| --- | --- |
| Shared settings simplification and unchanged approved art | Implemented; frozen inputs recorded |
| Sequential guarded full supported-target gate | PASS; 17m 59s, 282 tasks, actual exit 0 |
| Exact packaged native-world matrix | PASS on 1.21.1 Fabric and 26.1.2 NeoForge; 72 cases / 342 assertions total |
| Distinct-client gameplay/performance reruns | 1.21.1 Fabric ACTIVE; 26.1.2 NeoForge PENDING; no overall gameplay PASS yet |
| Four exact package identities and structural review | PASS; 49 samples / 62 events / 45 custom definitions each |
| Maintained validation receipt | Recorded build/world PASS; gameplay and publication PENDING |
| Release / launcher regression checks | PASS; 25 release tests; launcher 17 pass / one Windows symlink skip |
| Git preservation of hash-bound recipe/provenance bytes | PASS; 313 actual checked-out files under each of two line-ending modes |
| Pack Tester installation build | PASS; 10.0126 seconds, four tasks up-to-date |
| Commit/push and remote CI | Authorized; completion PENDING |
| GitHub release, all four targets | Authorized; publication PENDING |
| CurseForge and Modrinth, all four targets on each | Authorized; publication PENDING |
| Pack-maker wiki command reference | Five-page update prepared; preview/publication PENDING |
| Human vanilla-settings follow-up | PENDING |
| Prior thruster, Infuser and Pylon Focus-change sound feedback | Accepted; no renewed sound-design approval requested |
| Unreported 30–60-second loop/mix review and recorded provenance/rights confirmation | PENDING; not inferred from publication authorization |

The earlier G and six-session native/FTB records retain their exact archive scope.
They do not substitute for the affected native reruns above. Broader
device/pack/OS/FTB combinations and isolated native CPU/allocation profiling are
additional coverage. GUI-scale-4 machine clipping remains deferred Low / P3;
unselected historical-source limitations and the separate generic visual-reload
investigation remain outside this pass.

The wiki follow-up is limited to current pack-testing commands and version/footer
scope: Commands, Diagnostics-and-Testing, Player-Commands,
Version-and-Maintenance and _Footer. It adds no broad audio/player guide. The
in-game Archive remains the player gameplay guide.

## Preferred human launch: Minecraft 1.21.1 Fabric

Use these steps after the current package checks are accepted. The existing
[IntelliJ run configuration](../../.run/Essence%20Ascendance%20-%20Pack%20Tester.run.xml)
uses the open project as its working directory; the
[development guide](../DEVELOPMENT.md#current-run-folders-and-intellij-entries)
documents the target layout.

1. Open `C:\Users\rcboo\Dev\Minecraft\essence_ascendance` in IntelliJ and use the
   live `main` checkout containing beta.14. Close the selected Minecraft instance.
2. Run **Essence Ascendance — Pack Tester**. Select the intended Prism instance
   whose **Minecraft** column is **1.21.1**, **Loader** is **Fabric** and **Status**
   is **SUPPORTED**. Review its instance ID and exact **Mods directory**.
3. Tick **I confirm the selected game's Minecraft is closed and will stay closed
   until completion.** Click **Build, Deploy & Launch** and confirm that same
   destination. If Prism is not configured, choose it using **Prism executable…**;
   alternatively use **Build & Deploy**, wait for completion, then launch that
   same instance through Prism.
4. Open your existing test world with the machinery and flight-capable character.
   This path uses your selected instance; it does not install an earlier prepared
   acceptance world. Save and quit normally when finished.

## Short human checklist

Use **Options → Music & Sounds** for volume and **Show Subtitles**. Keep the
existing approved sounds; report the action, setting and any unexpected result.

| Action | Expected behavior and feedback needed |
| --- | --- |
| Run a paid Infuser operation and briefly use authorized flight. Set **Master Volume** to 50%, then 0%, then restore your previous value. | Both become quieter, both mute at zero, and both recover without overlapping or lingering playback. Report any sound that ignores Master or fails to recover. |
| With Master restored, repeat 50% → 0% → restore for **Players** during flight, then **Blocks** during machinery. | Players controls flight; Blocks controls machinery. Report cross-category changes, unexpected silence or duplicate restart. |
| While machinery and flight are active, toggle **Show Subtitles** Off, then On; allow up to five seconds for an ongoing loop's next caption refresh and trigger a fresh action. | Off clears/disables EA and native subtitle presentation. On restores eligible captions at their normal cadence without duplicate native/relative lines or misleading direction arrows; old one-shots do not replay. Report a surviving, missing or duplicated caption after that refresh window. |
| With subtitles On, use flight long enough to show its right-side HUD card, and generate nearby machine/action captions. | Native captions stay at vanilla bottom-right; the right-hand card stack rises and settles smoothly without covering captions. Report overlap, jumps or altered card animation. |
| Enter `/essence audio`, inspect help and Tab completions, and try `/essence audio settings`. | Help/completions offer help, audition, start and stop; settings is absent and the removed command fails normally. There is no separate EA audio settings screen. |
| Keep the Infuser and your personal channel active for 30–60 seconds, occasionally use the Caster and brief flight, then stop the machine and channel. | Only the previously unreported long-loop/mix review is requested: report seams, distracting repetition, sounds masking each other or an unwanted lingering tail. |

Provenance/redistribution-rights confirmation is a separate recorded review. No
headless result or successful publication fills that field automatically.

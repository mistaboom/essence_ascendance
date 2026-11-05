# Vanilla audio settings follow-up — 2026-11-04

**Headless technical verification, post-merge CI, release uploads and wiki publication PASS. Human review and external store status remain outstanding.**
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
`PUBLISHED_HEADLESS_TECHNICAL_PASS_HUMAN_REVIEW_PENDING`; its final 27,501 bytes have
SHA-256 `fb6ca0f2f96446daacebe68e8fb7e2c203d8d8649d94caf858e551815aa2633a`.

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

[Remote CI 41971359962](https://github.com/mistaboom/essence_ascendance/actions/runs/41971359962)
passes at implementation commit `cf884d0975b54ae9d6f3fc554de08bc87d0fe237`.
The [CI artifact comparison](../../build/audio-vanilla-settings-release-20261104/ci-artifact-comparison.json)
passes all four targets. Remote archive SHA-256 values differ from the locally
pinned JARs used for native acceptance. Complete entry comparison identifies only
the generated Architectury adapter package difference and JSON/TOML line endings;
all other entry bytes, including approved audio samples and other classes, match.
This is static artifact comparison, not an additional native run of the remote
archives. [Release workflow 41974285162](https://github.com/mistaboom/essence_ascendance/actions/runs/41974285162)
completed successfully at **2026-11-05 05:58:06 UTC**. Its fresh build takes
**26m 49s**, executes all **282 tasks**, and passes **25 release tests**.

[Beta.14 on GitHub](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.14)
contains all four JARs and `SHA256SUMS`. Actual downloaded files match the release
artifact bytes in the [download verification](../../build/audio-vanilla-settings-release-20261104/github-download-verification.json).
The [release artifact comparison](../../build/audio-vanilla-settings-release-20261104/release-artifact-comparison.json)
passes with the same scoped generated-adapter-package and JSON/TOML line-ending
differences from the local native-tested pins; approved OGG and all other runtime
entry bytes match. All **eight site-upload jobs succeed on their first attempt**,
without retries, as recorded in the [upload proof](../../build/audio-vanilla-settings-release-20261104/site-upload-proof.json).
Public destinations are [CurseForge](https://www.curseforge.com/minecraft/mc-mods/essence-ascendance)
and [Modrinth](https://modrinth.com/mod/essence-ascendance). The
[site verification](../../build/audio-vanilla-settings-release-20261104/modsite-publication-verification.json)
passes all four Modrinth CDN downloads against the release artifact bytes,
including metadata, dependencies and changelog. All four versions are listed:
[1.21.1 Fabric](https://modrinth.com/mod/essence-ascendance/version/Y12pWvIA),
[1.21.1 NeoForge](https://modrinth.com/mod/essence-ascendance/version/9mqqBBO6),
[26.1.2 Fabric](https://modrinth.com/mod/essence-ascendance/version/nKbIzRic) and
[26.1.2 NeoForge](https://modrinth.com/mod/essence-ascendance/version/2PneqtUx).
The project remains `processing` with requested status `approved`: **public project
approval is an external blocker**, not an inferred success. CurseForge accepted
all four uploads, but its public file list returned HTTP 403; **public availability
is unverified**, not rejected. No additional uploads were attempted.

[PR #3](https://github.com/mistaboom/essence_ascendance/pull/3) passes review and is
merged at `160af0ab558ddcbd1444d9576a98bbce1e0d889c`; its change is limited to three
workflow action-pin SHA lines. The fresh [post-merge CI 41976334977](https://github.com/mistaboom/essence_ascendance/actions/runs/41976334977)
passes at **2026-11-05 06:08:54 UTC**, with a **19m 27s** full gate, all **282
tasks executed**, and **25 release tests**. Main includes the reviewed merge
`160af0a`; the release remains bound to tested implementation source `cf884d0`.
No production audio change is attributed to that workflow-only merge.

Exact packaged native-world checks pass on **1.21.1 Fabric** and **26.1.2
NeoForge**, each with **36 matrix cases and 171 assertions**: **72 cases / 342
assertions** total, using hidden clients and native OpenAL playback. The guarded
runs take **74.0347 seconds / 1,870,843,904 peak committed bytes** for Fabric and
**68.0280 seconds / 3,003,781,120 bytes** for NeoForge, both actual exit **0** with
no owned processes remaining. These controlled world snapshots are separate from
the real gameplay sessions below. Pack Tester `installDist` also passes in
**10.0126 seconds**, four tasks up-to-date.

Both exact packaged **distinct-client gameplay sessions PASS**, on 1.21.1 Fabric
and 26.1.2 NeoForge: **57 phase paths each** (56 generic PASS markers plus the
special disconnect path), **18 native measurement windows each / 36 total**, and
**four zero process exits each / eight total**. Actual server logs report
**423 Fabric / 391 NeoForge assertions**. Each actor client executes **five actual
ChatScreen Enter command paths**; the observer does not run that preflight.
Real interface requests, late connections/reconnections, native membership, paid
Infuser/Pylon work, authorized flight and simultaneous activity pass. The native
guard receipts record **430.1341023 seconds / 3,980,046,336 peak committed bytes**
for Fabric and **428.1495226 seconds / 5,757,038,592 bytes** for NeoForge, with no
owned processes remaining.

The [shared native summary](../../build/audio-gameplay-acceptance-20261102/vanilla-settings-native-summary-20261104.json)
and [compact measurements](../../build/audio-gameplay-acceptance-20261102/vanilla-settings-native-compact-20261104.md)
record actual paid sources, native decoding/playback and traffic. Both targets
discover **41 loaded objects**; dense windows remain **32 sources / 12 owned
voices / 12 playing**, and idle/stopped windows are **0 / 0 / 0** with zero block
entity updates. Dense client audio tick observations are:

| Target | Per-window p95 range | Java allocation per tick |
| --- | --- | --- |
| 1.21.1 Fabric | 43.2–79.6 µs | 2,928.0–3,713.2 bytes |
| 26.1.2 NeoForge | 48.2–123.2 µs | 3,034.7–3,455.5 bytes |

These are separate window percentiles under the same OS resource guard, not a
pooled percentile or unconstrained benchmark. Steady windows perform no decoding;
actual cumulative cold/setup decoding produces 22 actor and 20 observer native
buffers per target with zero failures. Whole-process CPU includes gameplay,
rendering, networking and native audio; Java allocation excludes native buffers.
The measurements do not isolate OpenAL mixer CPU or establish human audibility,
loop seamlessness or HUD approval.

A release reproducibility defect was confirmed: Git's CRLF normalization would
invalidate all **49 approved recipe hashes**. The exact-byte rules in
`.gitattributes` now cover `art/source/audio/**`, `art/audio-*.json`,
`docs/audio/*.json` and `tools/audio/fixtures/**`; restaging preserved the working
bytes, including the hash-bound test recipe. An actual
[checkout-index verification](../../build/audio-vanilla-settings-release-20261104/git-byte-preservation.json)
passes **319 files each** with `core.autocrlf=true` and `false` at the `cf884d0`
checkpoint. All hash-bound audio, fixtures and historical receipts retain their
bytes. The current `VANILLA-SETTINGS-20261104-VALIDATION.json` is an evolving
publication ledger and has changed since that checkpoint; the 319-file proof
does not claim its later contents stayed frozen. Its separate
[final Git-blob byte check](../../build/audio-vanilla-settings-release-20261104/final-ledger-git-bytes.json)
passes: final working bytes equal staged blob
`43743226b149642bab51b2d48d6831db4c46dada`. This Git-storage repair is separate from
the unchanged 2,047-input runtime freeze; no production code changed after the gate.

## Completion ledger

| Item | Current result |
| --- | --- |
| Shared settings simplification and unchanged approved art | Implemented; frozen inputs recorded |
| Sequential guarded full supported-target gate | PASS; 17m 59s, 282 tasks, actual exit 0 |
| Exact packaged native-world matrix | PASS on 1.21.1 Fabric and 26.1.2 NeoForge; 72 cases / 342 assertions total |
| Distinct-client gameplay/performance reruns | PASS on 1.21.1 Fabric and 26.1.2 NeoForge; 57 phase paths each / 36 windows / eight zero exits |
| Four exact package identities and structural review | PASS; 49 samples / 62 events / 45 custom definitions each |
| Maintained validation receipt | PUBLISHED_HEADLESS_TECHNICAL_PASS_HUMAN_REVIEW_PENDING; final staged Git bytes PASS |
| Release / launcher regression checks | PASS; 25 release tests; launcher 17 pass / one Windows symlink skip |
| Git preservation of hash-bound recipe/provenance bytes | PASS at cf884d0; 319 files under each of two line-ending modes; separate final publication-ledger Git-blob check PASS |
| Pack Tester installation build | PASS; 10.0126 seconds, four tasks up-to-date |
| Commit/push | PASS; implementation cf884d0 and reviewed workflow merge [160af0a](https://github.com/mistaboom/essence_ascendance/commit/160af0ab558ddcbd1444d9576a98bbce1e0d889c) on main |
| Remote CI and artifact comparison | PASS; [run 41971359962](https://github.com/mistaboom/essence_ascendance/actions/runs/41971359962); archive differences explicitly scoped above |
| GitHub release, all four targets | PASS; [beta.14](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.14), four JAR downloads and SHA256SUMS match release artifact bytes |
| Modrinth, all four targets | Upload/CDN bytes/metadata PASS; versions listed; project processing, public approval PENDING |
| CurseForge, all four targets | Uploads accepted first attempt; public listing HTTP 403, availability UNVERIFIED |
| Pack-maker wiki command reference | Five pages published at 5762bdf; source and live HTTP checks PASS |
| PR #3 workflow pin update | Reviewed and merged at 160af0a; [post-merge CI 41976334977](https://github.com/mistaboom/essence_ascendance/actions/runs/41976334977) PASS, 19m 27s / 282 executed / 25 release tests |
| Human vanilla-settings follow-up | PENDING |
| Prior thruster, Infuser and Pylon Focus-change sound feedback | Accepted; no renewed sound-design approval requested |
| Unreported 30–60-second loop/mix review and recorded provenance/rights confirmation | PENDING; not inferred from publication authorization |

No technical release or wiki-publishing blocker remains. The external store
limits and human review above remain explicitly unresolved.

The earlier G and six-session native/FTB records retain their exact archive scope.
The current affected reruns cover the two representatives above. Broader
device/pack/OS/FTB combinations and isolated native CPU/allocation profiling are
additional coverage. GUI-scale-4 machine clipping remains deferred Low / P3;
unselected historical-source limitations and the separate generic visual-reload
investigation remain outside this pass.

The wiki follow-up is limited to current pack-testing commands and version/footer
scope: Commands, Diagnostics-and-Testing, Player-Commands,
Version-and-Maintenance and _Footer. After the [five-page preview](../../build/audio-vanilla-settings-release-20261104/wiki-preview.log),
normal publication advances the wiki from `8fbdd3f` to
`5762bdfbfc46c0c0fd7658a1bc32e359a9fff01a`, using canonical source `160af0a`.
[Source verification](../../build/audio-vanilla-settings-release-20261104/wiki-source-verification.json)
passes 22 pages, 160 links/anchors and five TOML examples.
[Fresh live HTTP verification](../../build/audio-vanilla-settings-release-20261104/wiki-live-verification.json)
passes exact LF-normalized raw bytes for all five published files and tables,
anchors and footer in the four content-page HTML responses. Start at the
[published command reference](https://github.com/mistaboom/essence_ascendance/wiki/Commands#audio-commands)
or [server audio diagnostics](https://github.com/mistaboom/essence_ascendance/wiki/Diagnostics-and-Testing#audio-playback).
No broad audio/player guide was added; the in-game Archive remains the player
gameplay guide.

## Preferred human launch: Minecraft 1.21.1 Fabric

The current package checks are accepted. The existing
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

# Audio human-feedback follow-up — 2026-11-04

## Current HUD/command follow-up — 2026-11-04

**Candidate-G headless technical verification PASS.** The
[final HUD/command record and exact launch/checklist](HUD-COMMANDS-20261104.md) and
[maintained receipt](HUD-COMMANDS-20261104-VALIDATION.json) bind current acceptance.
The shared deferred-open repair closes the confirmed 1.21.1 chat-submit settings
bug; the equivalent ordinary-key bug was not established on modern. Both final
exact native-c sessions execute **six actual ChatScreen Enter command paths**, with
settings persisting after chat closes, and complete all **57 phase paths each,
36 windows / eight zero exits** (391 Fabric / 390 NeoForge server assertions).
The final four-target gate passes in **17m 59s**, 282 tasks (246 executed, 36
up-to-date); all **2,049 frozen inputs and four exact provider/pin hashes** verify.

Use the new fixture-free **`human-1211fabric-hud-commands-v2`** preferred 1.21.1
Fabric package at `127.0.0.1:64831`; exact launch and short checklist are in the
final record. Its 121 critical inputs and saved server-only smoke PASS. No agent
launched its visible human client; older prepared worlds remain retained. Finish
with `/stop`, then close Minecraft. F's passing gate/native results remain
historical **direct-connection dispatch** evidence and its human copy is superseded.

The user explicitly accepts **thruster gain-1.0 sound, the Infuser fix and Pylon
Focus-change sound**. **Required remaining acceptance:** human smooth-HUD/caption
approval, unreported 30–60-second mixed-loop listening and separate recorded rights
confirmation. GUI-scale-4 machine clipping stays deferred Low / P3. Current native
coverage is two affected representatives; prior native/FTB receipts keep their
archive scope. Broader device/pack/OS/FTB cross-products and isolated native
profiling are additional coverage; unselected-source history and generic visual
reload investigation remain outside this pass.

## Retained prior human-feedback checkpoint

Time zone: America/Anchorage. **Headless technical verification for this follow-up
PASS:** candidate D's four-target full gate, both affected exact packaged native
sessions and fixture-free preferred human-package preparation are complete. The
two native reruns have **eight zero process exits and 36 measured windows**. Human
audibility/mix/loops, full HUD approval and recorded rights review remain pending.
The [follow-up validation receipt](HUMAN-FEEDBACK-20261104-VALIDATION.json) binds this
scope. This follows the user's listening/HUD check of the earlier 1.21.1 Fabric package. The prior [gameplay receipt](GAMEPLAY-20261102-VALIDATION.json),
six native sessions and 18m 6s full gate retain their exact earlier archive scope.
They do not certify the subsequent interface/caption changes or the newly approved
flight gain. The user explicitly authorized **“Raise thruster gain to 1.0”** after
hearing the earlier test package. Only the shared flight playback gain changes from
0.16 to 1.0; approved sound files/designs/sources and all other gains are preserved.
No new audibility, global listening/HUD or rights approval is inferred.

## Findings and disposition

| Finding / reproduction | Verified evidence and current disposition |
| --- | --- |
| Ordinary Infuser Start/Stop adds a custom acknowledgement to the native button click. | The accepted toggle branch in [EssenceInfuserMenu](../../common/src/main/java/com/mistaboom/essence_ascendance/infuser/EssenceInfuserMenu.java) emitted `interface.acknowledge` while the actual native button already owned its click. The implementation now leaves the native click and retains `interface.denied` for rejected requests. The cue itself and the separate confirmed Soulbind acknowledgement remain available. Native server-routing regressions pass 149 assertions on each version, and both current exact packaged 1.21.1 Fabric / 26.1.2 NeoForge sessions pass phase 0 with zero custom acknowledgement. |
| Medium / P2: more than four native subtitle rows can cover the right-hand HUD cards. | Supplemental captions already consumed actual occupied HUD bounds; Minecraft's native bottom-right subtitle group did not. At a 480×270 scaled viewport, a five-row native group at `(327,190,152,50)` intersects the card at `(302,155,172,56)` by 21 vertical units. Shared layout/native-hook repair is implemented; human acceptance of the changed placement remains pending. |
| Flight is inaudible or possibly very quiet in the running test world, despite the “Thrusting” caption. | The user first reported sustained stamina-consuming flight after jump/release/hold, then confirmed the caption while still hearing no clear sound. The report does not establish a playback/decoder defect. Existing THRUST → `mobility.thrust` → `mobility.thrust.body` → approved `flight_wind_v2` routing is unchanged. The user explicitly approved the shared flight gain increase 0.16→1.0; it is implemented, the fresh full gate passes and both affected native sessions verify actual playback. New human listening results remain pending. The native fixture now exercises ordinary key-input polling instead of authoring movement packets. Both 1.21.1 Fabric and 26.1.2 NeoForge pass actual sustained native-body and stop checks through normal key polling; this does not establish human audibility. |
| Low / P3: machine panels clip at the user's GUI scale 4. | User reports machine fit at scales 1/2/3 **only**; this is not general HUD approval. Fixed panel sizes are Infuser 230×338, Crucible 230×319 and Pylon 230×258 GUI units. Clipping occurs when the actual scaled viewport is smaller than the panel; scale number alone is insufficient. Deferred to shared responsive-machine UI work, with no per-screen scaling patch. |

The machine-size evidence is
`build/audio-human-feedback-20261104/machine-scale-review.json`. The user's physical
resolution was not supplied. For the explicitly hypothetical 1920×1080/scale-4
case, the viewport is 480×270: Infuser clips 34 units at top and bottom; Crucible
clips 24/25; Pylon fits. These are geometry calculations, not screenshots or a
claim about the user's resolution. A correct responsive repair must handle native
slots, dragging, widgets, tooltips and recipe-viewer bounds through shared owners.
For now use the reported working scales 1–3, or a viewport at least 338 GUI units
high for the Infuser.

## Explicitly approved flight gain

The user's instruction **“Raise thruster gain to 1.0”** authorizes this specific
exception to the prior preservation constraint. The shared owner is
[art/audio-catalog.json](../../art/audio-catalog.json), field
`policies.playback.flight.gain`: **0.16 → 1.0**, a 6.25× amplitude factor
(approximately **+15.92 dB** before other native/user/lifecycle gains). No audio file,
waveform, creative design, source selection, pitch, other gain or production flight
routing is revised by this step. No claim about final device loudness or listening
acceptance follows from that arithmetic.

[authorized-gain-change.json](../../build/audio-human-feedback-20261104/authorized-gain-change.json)
records the exact authorization, single-field change and retained
`audio-catalog-before-gain.json`. Candidate D's 2,048-input fingerprint is
`464d66660246562917353c3a5c50471d2408755406205e8c088ef6648dfd3ab9`
(`candidate-inputs-d.json`). Every other source/asset input matches candidate B;
the earlier interface/caption repairs remain part of this candidate.

The in-progress supported-C build was deliberately cancelled after this approval
because its inputs were superseded. Only the identified owned build worker/tree
was signalled; the guard terminated eight build descendants. Human game processes
were untouched. C is **superseded, not accepted**, with actual build exit unknown.
Fresh `final-clean-d` and `final-supported-d` now **PASS**, sequentially under the
existing 25% CPU/8 GiB guard, actual exits 0. The full gate completes in **18m 12s**,
282 tasks (277 executed, five up-to-date). Both subsequent affected packaged native
sessions pass, and the new preferred manual package contains those verified bytes
and gain 1.0. No new human listening approval is claimed.

## Shared caption repair and coverage

[CaptionLayout](../../common/src/main/java/com/mistaboom/essence_ascendance/client/audio/CaptionLayout.java)
and [CaptionRouter](../../common/src/main/java/com/mistaboom/essence_ascendance/client/audio/CaptionRouter.java)
place one coherent native subtitle group using the actual native backdrop and
already-filtered audible-row count. The group moves left of occupied cards, then
above remaining obstacles if needed; its placed bounds also reserve space for
supplemental captions. Native text, range, expiry and directional arrows retain
their native owners. No second subtitle list or copied measurement path is added.

[AudioSubtitleMixin](../../common/src/main/java/com/mistaboom/essence_ascendance/mixin/AudioSubtitleMixin.java)
observes native placement. On 1.21.1, the shared skill/status cards render once before
native subtitles so their actual bounds are available; the former later draw in
[SkillEffectHudMixin](../../common/src/main/java/com/mistaboom/essence_ascendance/mixin/SkillEffectHudMixin.java)
is removed. On 26.1.2, existing ordering already precedes deferred subtitle drawing.
Both legacy loader bytecode checks show the subtitle callback runs independently
of the subtitle preference; the existing card hide-GUI condition remains intact.

The existing [AudioRuntimeTest](../../common/src/test/java/com/mistaboom/essence_ascendance/audio/AudioRuntimeTest.java)
adds 161 assertions covering 1–8 native lines at GUI scales 1–4, complete on-screen
bounds, no native/HUD/supplement collision and unchanged placement when unobstructed.
[AudioNativeTest](../../common/src/test/java/com/mistaboom/essence_ascendance/audio/AudioNativeTest.java)
checks native hooks. Its modern annotation reader initially assumed scalar `at`
metadata, although that target uses an array; one shared helper now accepts either
representation while requiring exactly one anchor. This was a test-reader failure,
not a demonstrated production rendering failure. The corrected modern native suite
now passes **747 assertions**, actual exit 0, in 15 seconds/25 tasks
(`focused-modern-b.log` and its receipt). The later candidate-D full gate also
passes. These suite results remain distinct from the two subsequent packaged native
passes and pending human layout approval.

## Executed checks and pending work

| Check | Current result / limitation |
| --- | --- |
| `focused-a` shared behavior | Both versions PASS: 240 runtime, 385 integration, 1,618 catalog and 24 extension assertions. These are headless state/layout checks, not human hearing or pixel approval. |
| `focused-a` native suite | Legacy PASS, 745 assertions. Modern stops on the test-only scalar/array annotation reader. Overall `focused-a` is **FAIL**, actual exit 1; partial passes do not convert the invocation to success. Parser correction is implemented; separate `focused-modern-b` passes 747 modern native assertions, actual exit 0. Failed `focused-a` remains failed. |
| Infuser live-button/native gameplay regression | Both actual native server-routing suites PASS **149 assertions each**, on 1.21.1 and 26.1.2 (`native-routing-1211fabric.*`, `native-routing-2612fabric.*`). Existing `projectile/NativeAudioRoutingTest` exercises live buttons; both current packaged sessions also pass phase 0 with zero custom acknowledgement. Rejection remains private and Soulbind keeps its separate confirmation. |
| Ordinary flight input | Both current packaged sessions PASS using normal key polling/request handling, actual native THRUST body/caption and subsequent stop checks. Prior direct movement-packet evidence is not used as proof of this input path. Native playback is verified; human audibility remains pending. |
| Preferred packaged native gameplay/performance | `human-feedback-1211fabric-native-20261104-a` PASS on candidate D: four zero exits, full paid Infuser and both pylon sweeps, native membership/true late connect/reconnect, mixed ordinary-key flight and zero ordinary Infuser acknowledgement, **18 actual windows**. Guard PASS, 424.133734 seconds, peak job commit 4,126,191,616 bytes, zero remaining owned processes. No human sound/HUD result is inferred. |
| Modern packaged native gameplay/performance | `human-feedback-2612neoforge-native-20261104-a` PASS on candidate D: four zero exits and **18 actual windows**, normal-key flight, zero ordinary Infuser acknowledgement, paid/pylon/native membership/true-connection lifecycle checks. Guard PASS, 424.161549 seconds, peak job commit 5,990,141,952 bytes, zero remaining owned processes. Together the two affected reruns produce eight zero exits and 36 actual windows. |
| Production packaging and external fixtures | `package-validation.json` independently verifies all four provider hashes, processed asset/data byte equality, captions and authoring exclusion: 49 unchanged approved Oggs, 62 registered cues, 45 custom definitions, shared packaged flight gain 1.0 on every target. Preferred/modern fixtures compile and are pinned in `fixture-bindings.json`; external fixture code remains separate from production. |
| Four-target supported gate | **PASS** for candidate D, including the expressly approved flight gain: standalone `final-clean-d`, then `final-supported-d`, both actual exit 0. Full gate **18m 12s**, 282 tasks (277 executed, five up-to-date). Guard wall time 1,094.347 seconds; peak committed bytes 4,447,555,584; minimum available physical/commit bytes 19,370,287,104 / 19,204,227,072; active/terminated owned processes both zero, no resource breach. Evidence: `final-supported-d.log`, `.receipt.json` and actual-exit sidecar. Both affected native sessions subsequently pass on their exact current provider bytes. |
| Retained prior build attempts | `final-clean-b` PASS, exit 0, 10 seconds; `final-supported-b` exits 1 after approximately 50 seconds without a build-failure diagnostic, cause unestablished, no resource breach/remaining child. Supported C was owner-cancelled when gain approval superseded its inputs; actual build exit unknown, not accepted. Those records retain their exact scope and are not rewritten as passes. |
| Updated human package and server-only smoke | `human-1211fabric-feedback` is fixture-free, gain 1.0, exact current 1.21.1 Fabric production hash `b91f5fd1…d62e1aa`, endpoint `127.0.0.1:52882`. `human-package-review.json` PASS verifies 121 critical inputs. Server-only smoke PASS, actual exit 0, saved world and unchanged critical inputs; guard 12.012490 seconds, peak commit 893,927,424 bytes. No human client was launched by the agent. |
| Human replay and rights review | **PENDING.** Reported problems prevent blanket listening/HUD approval. Recorded selected-material provenance, licence/attribution and redistribution confirmation remain separate user decisions. |

Focused/build/package evidence is under `build/audio-human-feedback-20261104/`.
Only the test annotation-reader change separates the focused A/B snapshots;
candidate D adds the expressly approved gain. Its 2,048-input fingerprint remains
stable. Both native receipts are under `build/audio-packaged-acceptance/` in
`human-feedback-1211fabric-native-20261104-a` and
`human-feedback-2612neoforge-native-20261104-a`.

This follow-up reruns the **two affected representative native targets**, spanning
both Minecraft versions and both loaders. The earlier six-run native/FTB matrix
and its four-target archives remain historical evidence with their original hashes;
they are not silently reclassified as current candidate-D native passes. No full
four-target current native matrix or repeated current FTB matrix is claimed.

| Candidate D target | Pinned production SHA-256 |
| --- | --- |
| 1.21.1 Fabric | `b91f5fd191f3fbd5a5a8d9a6f32ce165f2b7dee3183c3835e52c91635d62e1aa` |
| 1.21.1 NeoForge | `e5c294bceac25a6508aa37cc260e59af5614bdc2e1d62a5b4627dbc1d6583e55` |
| 26.1.2 Fabric | `880039ec689e813a9c9abccd6490d6b19387c562e79f1f15b72c53e4e4fb3491` |
| 26.1.2 NeoForge | `2046c1ffcda144376726bfb4a3ace09b0d8c14c192f2d20df8498ca81dc9fe3e` |

The preferred native archive exactly matches its pinned provider above; its fixture
SHA-256 is `d28ef1d2e228882ac734ceb9f608b14e69b4ab3a59a173fc753ca7c4f9916898`.
Modern NeoForge uses fixture
`f38402dd44599edd41467b816afef764f6358d507b0c76356ee9064e22c932c5`.
Package structural checks are distinct from per-target native gameplay results;
no all-four-current-package native matrix is inferred from these archive checks.

## Current native performance evidence

The shared summary and compact tables report `VERIFIED_NATIVE_MEASUREMENTS` in
`build/audio-gameplay-acceptance-20261102/human-feedback-final-native-summary-20261104.json`
and `human-feedback-final-native-compact-20261104.{json,md}`. There are **36 actual
windows**, two repeats of idle/dense/stopped across server/A/reconnected B per run.
Each dense client discovers **41 EA block entities** and retains **32 desired
sources / 12 owned / 12 actually playing native bodies**, with stable identities.
Idle/stopped windows have zero block-entity updates and no owned bodies. Actual
cold/setup decoding produces actor **21** and observer **20** native buffers per
session, no failures; settled windows perform zero decode workers.

| Target | Dense server CPU cores | Server Java allocation GB / ~21s | Client audio p95 microseconds | Dense encoded packets / encoded MB / socket MB |
| --- | ---: | ---: | ---: | --- |
| 1.21.1 Fabric | 0.141–0.156 | 1.759–1.761 | 37.8–71.2 | 13,186–13,212 / 4.522 / 2.799–2.800 |
| 26.1.2 NeoForge | 0.138–0.147 | 1.727–1.728 | 50.1–101.9 | 13,260–13,318 / 4.519–4.520 / 2.802–2.803 |

Ranges are separate window/client observations, not pooled percentiles. CPU cores
means process CPU seconds/elapsed seconds; allocation is Java allocated bytes, not
retained heap. Actual OpenAL mixing is included only in whole-process CPU; isolated
native mixer CPU and native/direct allocations are not measured. Sound-engine Java
thread and decoder-worker observations remain in the full report. These are hidden,
OS-constrained loopback runs under the established guard, not human audibility,
foreground device/OS performance or HUD-pixel approval.

## Retained launcher recovery history

The manual-launch collision is a separate recovery task. Read-only inspection
identified the earlier disposable human server (PID 9032, parent gone) retaining
loopback port 49330 with matching critical inputs. Wrapper recovery is now **READY**:
14 regressions pass, 121 critical inputs are verified, original helper/manifest
copies are preserved and only the reviewed helper binding changes. The same launch
command can reconnect only to the verified owned prepared server; it cannot adopt
an unrelated port owner. Evidence:
[human-launcher-recovery-receipt.json](../../build/audio-human-feedback-20261104/human-launcher-recovery-receipt.json).
That recovery receipt alone does not establish a successful user retry. A later
user-initiated launch is now confirmed by
`human-logs/20261104-152301-432693600-session.json` in the earlier `human-1211fabric-final-v2` package:
server PID 15728 and client PID 9504. These were not agent-launched. The earlier
server 9032 and Gradle attempt ended around the same period; the recovery helper
has no server-termination path, and their ending remains unclassified. No common
cause or production defect is inferred from that timing. The user subsequently
reported the “Thrusting” caption but no clear sound, possibly very quiet, and
expressly approved the gain change above. Audibility after that change is pending.

A recovered server has no inherited input/child-exit handle: issue `/stop` in game
for normal save/shutdown. Closing only its client leaves that verified server
available for another reconnect. No screen control, server signal or visible client
launch was performed by the recovery agent. The package still contains the earlier
accepted production bytes, including the former 0.16 flight gain. It does not yet
contain the new gain or interface/caption candidate and cannot establish their
acceptance.

## Current human package and short acceptance check

Use **Minecraft 1.21.1 Fabric**, `human-1211fabric-feedback`, which replaces
`human-1211fabric-final-v2` for this follow-up. It contains the interface/caption
repairs and the expressly approved **1.0 flight gain**, without the external test
fixture. Its exact current production hash is the 1.21.1 Fabric pin above.
[human-package-review.json](../../build/audio-human-feedback-20261104/human-package-review.json)
verifies 121 critical inputs and the loopback endpoint `127.0.0.1:52882`. The
[server-only smoke](../../build/audio-gameplay-acceptance-20261102/human-feedback-server-smoke-20261104.json)
passes with world save and unchanged critical inputs; no human client was launched
by the agent. Preparation and native state do not approve audible or visual quality.

Exact new launch command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rcboo\Dev\Minecraft\essence_ascendance\build\audio-packaged-acceptance\human-1211fabric-feedback\launch-human.ps1"
```

The saved player is Survival **EA_A**, at **0.5,99,-1.5**, with Caster and authorized
Fatigue Flight. Infuser is **0,98,1**, Crucible **0,98,3**, with eight focused Pylons
around the Crucible at Y=98. The Infuser contains an installed Focus and a separate
workpiece. Use comfortable native Master/Players/Blocks levels; in `/ea_audio`,
leave family gains at 100%, Continuous On and supplemental captions Always, with
native subtitles enabled. Use your normal listening device and the reported working
GUI scales 1–3.

| Exact action | Expected behavior and feedback needed |
| --- | --- |
| Start ordinary Infuser infusion, close its screen and listen for **20 seconds**; reopen, Stop and repeat. | One native button click on each Start/Stop, no extra custom success chime. One spatial body crosses its loop seam and stops cleanly. Report extra clicks/chimes, gaps, clicks at the seam, stacked loops or abrupt fades. |
| Run `/ea_audio start mobility.thrust`, listen at the same comfortable levels, then `/ea_audio stop`. Close menus, jump, release then hold normal jump for sustained stamina-consuming flight; release and land. | The approved flight sound now uses gain 1.0 and should track sustained THRUST/stop. Report whether direct audition and actual flight are audible, too quiet/loud, or masked; include duration, stamina behavior and caption presence. No audibility fix is assumed before your reply. |
| With the right-hand skill/channel cards visible, run machinery, cast and briefly fly until **five or more native subtitle lines** appear. Repeat at GUI scales 1/2/3. | Native and supplemental groups avoid each other and the cards while preserving native arrows. Report line count, scale/resolution, overlap or clipping. The separate scale-4 machine-panel limitation remains deferred. |
| With paid work active, open each Pylon and remove/reinsert its Focus to reach active counts **0, 4 and 8**; keep withdrawn Foci in your inventory. Listen at each count, including another **20-second** loop with normal casting nearby. | No old body under the replacement; comfortable transitions/repetition and usable machine/weapon/flight balance. Report family/count, masking and whether a fault repeats at the seam. |

For longer loops a Focus workpiece may finish after roughly 142 seconds at full
rate. Stop, collect it, hold it and run `/essence admin item tier set dormant` in
this disposable world; reinsert it in the input slot, leaving the installed Focus
in place, and restart. If reusing a recovered server without its original wrapper,
issue `/stop` in game for normal save/shutdown; closing only that client leaves the
verified recovered server available.

Please report device/headphones, volume settings, GUI scale/resolution, exact
action/family/pylon count and what you heard or saw. Machine-panel fit at scales
1–3 is the only human acceptance recorded in this follow-up. Listening/mix/loops,
complete HUD coexistence and recorded selected-material rights approval remain
**PENDING**; technical verification does not supply those judgments.

## Completion and remaining scope

| Item | Status / remaining work |
| --- | --- |
| Shared interface/caption repairs, explicit flight gain, four-target gate and package validation | **PASS**; approved audio bytes/designs/sources and all other gains preserved |
| Affected real native gameplay/input/performance reruns | **PASS**, two targets, eight zero exits, 36 windows; earlier six-session native/FTB history unchanged |
| Updated fixture-free preferred package and server smoke | **PASS / prepared**, exact new launcher above; no agent-launched human client |
| Human audibility/mix/loops and full HUD acceptance | **PENDING**, four checks above; no blanket approval inferred |
| Recorded provenance/licence/attribution and redistribution confirmation | **PENDING**, separate user/reviewer decision |
| GUI-scale-4 machine fit | **Low / P3 deferred**, shared responsive-machine UI follow-up; use known-working scales 1–3 |
| Broader compatibility and performance | **Additional coverage**: other device/pack/OS combinations, more providers/players/WAN/soak, other current native/FTB cross-products and isolated native mixer/native-allocation profiling. Unselected historical-source limitations and generic visual-reload investigation stay outside this pass. |

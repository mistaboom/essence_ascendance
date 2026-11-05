# HUD placement and audio-command follow-up — 2026-11-04

Time zone: America/Anchorage. **Candidate-G headless technical verification PASS.**
Human HUD, unreported long-loop/mix acceptance and rights confirmation remain
PENDING. This follows the user's test of the prepared 1.21.1
Fabric audio package. The user accepts the thruster sound, the Infuser fix and the
sound of Pylon Focus changes, but rejects moving native subtitles toward the
middle. Native subtitles must retain their vanilla position; the right-hand HUD
card stack must lift smoothly using its existing motion. Audio commands must also
follow the established `/essence` structure. G closes the settings entry-path gap:
both affected exact native sessions execute **six commands through actual
ChatScreen Enter**, including submission, chat close and persistent settings.
Both complete all **57 phase paths each, 36 measured windows and eight zero
process exits**. The final four-target gate passes in **17m 59s**, 282 tasks
(246 executed, 36 up-to-date), with all 2,049 frozen inputs and four exact package
hashes verified. The fixture-free preferred G package and server-only save/shutdown
check pass; its exact launch is below. No agent launched its visible human client.
F's passing results retain their direct-connection dispatch scope and its prepared
copy is superseded. The [maintained receipt](HUD-COMMANDS-20261104-VALIDATION.json)
binds current technical acceptance, separate from human visual/listening or rights
approval.

The [request and baseline](../../build/audio-hud-command-feedback-20261104/request-and-baseline.json)
records the exact scoped feedback and preservation requirements. The prior
[human-feedback record](HUMAN-FEEDBACK-20261104.md) and
[validation receipt](HUMAN-FEEDBACK-20261104-VALIDATION.json) remain historical
evidence for candidate D, fingerprint
`464d66660246562917353c3a5c50471d2408755406205e8c088ef6648dfd3ab9`.
That receipt's SHA-256 is
`6750ba74dc417fe70c3036f299fe366933e5d87850fa5bb9bf7aa49405c0dd93`.
It is not rewritten to certify this subsequent HUD/command candidate.

The verified 2,049-input candidate-G snapshot is
[candidate-inputs-g.json](../../build/audio-hud-command-feedback-20261104/candidate-inputs-g.json),
fingerprint `af6ba24bc23130b241f5b65e570577c0c9ce0274db4ec4bbe51c741c1eb8a49e`.
F (`82e356b9a3b81103347c2e05fdd04d77f2bac171457d24a8cf67e28f86a2bb77`),
E (`67b0a318d20e143455d827c23468ea42d9e56b85ce0041ab1d8851ebcf384bc2`),
C (`50555a44cbaf7206b7db8dcab4130f181278e51badc6d85838bd93980e07b3f2`)
and B (`3efbdfeb1eb606d053084e05e54cf36f5ac1bcd8bf5b89b8878d5a68533663c1`)
remain superseded diagnostic snapshots. F's native results are retained below with
their direct-dispatch limitation; final G verification includes the actual chat
submission and close path.

## Human feedback and scope

| Item | Current evidence and disposition |
| --- | --- |
| Thruster sound at approved gain 1.0 | **Human accepted:** user says the thruster sound is now good. Preserve gain 1.0, the approved sound bytes and existing flight behavior. This is not blanket mix/loop approval. |
| Infuser Start/Stop | **Human accepted:** user says the Infuser is fixed. Preserve removal of the extra custom acknowledgement and the native button click. |
| Pylon Focus changes | **Human accepted:** user says these sound good. Preserve the current sound behavior. Other unreported loop/mix cases remain unapproved. |
| Native subtitle position | **Human rejected:** captions moving toward the middle are not the desired behavior. Restore the vanilla native position; lift the right-hand HUD card stack smoothly through its existing motion. New visual acceptance is pending. |
| Audio-command convention | Canonical `/essence` syntax, focused checks and both final G native sessions pass. Shared deferred opening repairs the confirmed 1.21.1 chat-submit settings defect; both versions now verify all six preflight commands through actual ChatScreen Enter. F's direct-connection preflight remains scoped history. |
| Machine GUI scale 4 | Earlier Low / P3 clipping remains deferred to shared responsive-machine UI work. The earlier scale-1/2/3 machine-fit acceptance remains scoped to those screens. |
| Rights and remaining subjective acceptance | Recorded selected-material provenance/licence/attribution/redistribution confirmation and any unreported listening/mix/loop or HUD cases remain **PENDING**. None is inferred from the three explicit sound approvals. |

Approved sound files/designs/sources, the authorized flight gain 1.0, all other
gains, the accepted Infuser fix and unrelated changes are preserved. This follow-up
changes HUD placement and command structure through shared owners. It does not
request new sound design or a separate HUD animation system.

## Implemented command structure

The project [Commands reference](../wiki/Commands.md) states that all mod chat
commands start with `/essence`, with discoverable branches/identifiers through Tab
completion and operator menus at permission level 2. Its syntax uses `<argument>`
for a required argument and `[argument]` for an optional one. The
[Player commands reference](../wiki/Player-Commands.md) distinguishes player/console
context and explicit help handlers. The audio branch must follow that structure
while retaining the existing distinction between device-local settings/audition
and operator-triggered server routing.

These are the implemented paths, verified in focused checks and both final G
native runs through actual ChatScreen Enter. Older packages do not acquire the new
syntax retroactively.
The canonical references are [Commands](../wiki/Commands.md),
[Player commands](../wiki/Player-Commands.md) and
[Diagnostics and testing](../wiki/Diagnostics-and-Testing.md).

| Implemented path | Owner/context |
| --- | --- |
| `/essence audio` or `/essence audio help` | Client-local clickable, localized help; no operator requirement |
| `/essence audio settings` | Existing device-local settings screen; no operator requirement |
| `/essence audio audition <semantic>` | Existing local semantic-cue preview; no operator requirement |
| `/essence audio start <profile>` | Existing owned continuous preview and lease behavior; no operator requirement |
| `/essence audio stop` | Existing local preview cleanup; no operator requirement |
| `/essence debug audio` or `/essence debug audio help` | Level-2 server help, also available from console, with the client-only alternative identified |
| `/essence debug audio play <semantic>` | Existing WORLD/PLAYER server routing; level 2 and an in-game player required |

The shared `AudioCommands` owner supplies presentation, identifier handling and
catalog-derived completion. Short cue/profile IDs default to `essence_ascendance:`;
quoted full IDs are supported. Suggestions filter by prefix and quote qualified
IDs. Only directly playable cues are offered; server completion also excludes
UI-only cues. Known contextual names such as `riposte_release` and `stored_force`
produce context-required errors, while unknown names fail before playback or a
success response. UI-only server requests direct the user to local audition.
The client owns only the `audio` actions. A shared greedy fallback for other
`/essence` branches throws the exact `UnknownCommand` exception recognized by both
loaders, preserving server ownership of help, team, debug and administration. The
prior subtree-only registration was insufficient on Fabric: matching the common
root could produce `UnknownArgument`, which that loader consumes instead of
forwarding to the server. The repaired fallback adds no server action executor.

The old `/ea_audio` examples and earlier debug syntax in dated audio records remain
historical package instructions. `/ea_audio` and `/essence debug audio <semantic>`
are removed rather than retained as aliases. The final 1.21.1 Fabric and 26.1.2
NeoForge native runs verify merged-tree completion and actual command dispatch.

## Shared HUD repair and diagnostic history

The native subtitle hook now observes the actual native backdrop rectangle union
without moving native subtitles. Two bounded nine-slot buffers retain the prior
frame's footprints for shared placement. The existing right-hand card stack lifts
through its established motion; supplemental captions remain bottom-right above
the actual native subtitle group. This reuses the current card animation and
caption reservation owners. Smoothness and final visual coexistence still require
the user's approval.

Focused attempt A is **FAIL**, actual exit 1: its new server-command test incorrectly
classified `interface.acknowledge` as UI-only, although its actual scope is private
PLAYER. Legacy runtime 286 and native 753 assertions passed before that test
failure; those partial results do not make the invocation pass. The test input was
corrected to the actual UI-only `ui.press` cue. Candidate B then passes legacy
checks but **FAILS** modern test compilation: global native-source adaptation
rewrites `Field.getDouble`, and `GuiMessage` has moved packages. Candidate C uses
boxed `Field.get` and the narrow version-specific chat-class name. These are test
repairs; no production routing or rendering defect is inferred from those failures.

[focused-c.receipt.json](../../build/audio-hud-command-feedback-20261104/focused-c.receipt.json)
now **PASS**, actual exit 0, both versions: runtime **286**, integration **385**,
catalog **1,618**, extension **24** and command **1,292** assertions each; native
**753** on 1.21.1 and **751** on 26.1.2. Gradle reports **44 seconds**; the guarded
invocation takes 46.043546 seconds with peak committed job memory 2,169,217,024 bytes.
That C full gate was deliberately owner-cancelled when the Fabric dispatch collision
above was confirmed. Its partial tasks are not a passing supported-target result.

[focused-e.receipt.json](../../build/audio-hud-command-feedback-20261104/focused-e.receipt.json)
**PASS**, actual exit 0, with **1,299 command/presentation/operator assertions on
each version**, Gradle 25 seconds. Negative help validation now rejects false
validity from the greedy catch-all, in addition to checking the exact forwarding
exception. The subsequent
[final-supported-e receipt](../../build/audio-hud-command-feedback-20261104/final-supported-e.receipt.json)
**PASS**, 20m 49s/282 tasks. The exact four-package
[structural/preservation review](../../build/audio-hud-command-feedback-20261104/package-validation.json)
also passes for E. These results retain E's exact input/archive scope.

The first actual Fabric run,
`build/audio-packaged-acceptance/hud-commands-1211fabric-native-20261104-a`, is
**FAIL before executing any command** because the merged native command tree has
no `audio` branch. Inspection confirms both compiled Fabric versions copy an empty
client root into the existing server `essence` root, then populate a detached copy.
This is a confirmed native integration defect, separate from the earlier mistaken
tests and the already-repaired forwarding exception. The shared repair extracts
`AudioDevelopment.audio()`, installs the completed branch with `installCompletions`,
and uses one common client `handleCommands` RETURN hook in
[AudioCommandCompletionMixin](../../common/src/main/java/com/mistaboom/essence_ascendance/mixin/AudioCommandCompletionMixin.java).
Candidate F implements and tests that repair. No client-only parser result
substitutes for successful actual native command completion and dispatch.

## Retained candidate-F verification and newly identified entry-path gap

[focused-production-f.receipt.json](../../build/audio-hud-command-feedback-20261104/focused-production-f.receipt.json)
**PASS**, actual exit 0: **1,350 command assertions per version**, native **753**
on 1.21.1 and **751** on 26.1.2. The invocation builds all four current provider
archives and reports 1m 14s. This precedes the first current native run.

The exact [Fabric native-b receipt](../../build/audio-packaged-acceptance/hud-commands-1211fabric-native-20261104-b/receipt.json)
**PASS**, SHA-256
`5cd32ab4ca428a92384063db155dfbbae20ab32fd95c9d63f5c8918bb600e805`.
Production SHA-256 is
`f2c75a615c2a55aae8b377614362a165d8cb8b58a9c0a2dba9a239332c02c03b`;
fixture SHA-256 is
`725a3e6056c811f3774c2e2e166d0a78ddb7796d42bf5f133dde63b87d056d66`.
All 57 phase paths and 18 actual performance windows pass with four zero process exits.
Before acknowledging each client as joined, the native command preflight verifies
completion, local help/settings/audition/start/stop, native preview cleanup and
server help through `connection.sendCommand`. It does **not** exercise the enclosing
ChatScreen Enter/close path. The complete paid gameplay, membership,
late/reconnecting-client and simultaneous-activity scenario then passes. This is
actual packaged-native evidence, with that specific UI-entry limitation and no
human HUD approval.

The exact [26.1.2 NeoForge native-b receipt](../../build/audio-packaged-acceptance/hud-commands-2612neoforge-native-20261104-b/receipt.json)
also **PASS**, four zero exits and 18 windows, SHA-256
`076754ba56b25938d399c6eec28082f57c20552bd0962154a1e43f41a8dce83d`;
fixture SHA-256 is
`8ce3df736d43c7731184ab38da06839bcb47b60a995a768f98097e8f8652f19d`.
Server assertions are **450 Fabric / 430 NeoForge**. Both runs complete all 57
phase paths and the actual native command preflight. These two affected
representatives span both Minecraft versions and loaders; they do not turn older
six-session native/FTB evidence into a current four-target/FTB replay.

[final-supported-f.receipt.json](../../build/audio-hud-command-feedback-20261104/final-supported-f.receipt.json)
**PASS**, actual exit 0, **17m 37s**, 282 tasks (222 executed, 60 up-to-date), no
remaining or terminated owned process. It runs after the accepted Fabric-native
result, with Gradle and native execution sequential under the shared guard. There
is no new standalone F cleanup: E's cleanup is historical, and F's provider
archives were built by the focused production invocation. The
[final package/identity review](../../build/audio-hud-command-feedback-20261104/package-validation-final-f.json)
verifies all 2,049 frozen inputs and all four provider hashes unchanged after the
gate. All packages retain **49 approved Oggs, 62 registered cues and 45 custom
definitions**, matching processed assets/data and excluding authoring dependencies.

| Final candidate-F target | Exact production SHA-256 |
| --- | --- |
| 1.21.1 Fabric | `f2c75a615c2a55aae8b377614362a165d8cb8b58a9c0a2dba9a239332c02c03b` |
| 1.21.1 NeoForge | `98e85b5d1c6e21db0f4de981e48d3b55ec84c11a1b2d254ac5c80c1be01caf4f` |
| 26.1.2 Fabric | `526791e3dc046b04836799822151eaecbafc0d8057539135a8ef84f6d839d056` |
| 26.1.2 NeoForge | `663be3ad0a5748fe3ba4babaac6107f6877a93dcb2021414378fa251cbe714a8` |

The later [API proof](../../build/audio-hud-command-feedback-20261104/settings-chat-submit-defect-review.json)
confirms a **Medium / P2 settings defect on the ordinary 1.21.1 keyboard path**:
ChatScreen sends the command before closing; nested `client.execute` runs inline,
so the final close discards the newly opened settings screen. Both versions close
after submission, but modern reentrant execution normally queues the nested call;
the equivalent ordinary-key bug is **not established for 26.1.2**. F's preflight
could not detect the legacy bug because it entered at `connection.sendCommand`.
G keeps the repair in the shared settings-open owner, forcing the existing queue
with legacy `tell` / modern `schedule`, and extends the same native fixture to
press Enter through ChatScreen. The G results below verify the complete path on
both targets. F's earlier receipts remain passing evidence for what they executed.

## Retained candidate-F actual native performance evidence

The shared [combined summary](../../build/audio-gameplay-acceptance-20261102/hud-commands-final-native-summary-20261104.json)
and [compact tables](../../build/audio-gameplay-acceptance-20261102/hud-commands-final-native-compact-20261104.md)
verify **36 actual idle/dense/stopped windows**. Each client discovers all **41
loaded EA block entities**. Dense paid work stabilizes at **32 desired sources /
12 owned / 12 actually playing native bodies**, preserving continuity. Idle/stopped
windows have zero block-entity updates and zero owned/playing bodies. Cold/setup
decoding produces **22 native buffers for actor A / 20 for reconnected B**, with
zero failures; steady windows perform zero decode workers.

| Target | Dense server CPU cores | Java allocation GB / ~21s | Client audio p95 µs | Encoded packets / MB / socket-out MB |
| --- | ---: | ---: | ---: | --- |
| 1.21.1 Fabric | 0.139–0.202 | 1.739–1.742 | 41.0–76.4 | 13,317–13,383 / 4.522–4.523 / 2.800–2.801 |
| 26.1.2 NeoForge | 0.176–0.205 | 1.682–1.684 | 43.9–102.4 | 12,832–12,845 / 4.517 / 2.798–2.799 |

Each dense window includes 10,752 actual block-entity snapshots; traffic covers
both clients and ordinary paid progress synchronization. CPU cores means process
CPU seconds/elapsed seconds. Java allocation is allocated heap, not retained memory
or native/direct allocation. Per-client/window p95 ranges are not pooled. Whole
process CPU includes actual native OpenAL mixing but does not isolate its cost;
decoder observations include resource read/Vorbis/SoundBuffer work, excluding later
OpenAL upload. The full report retains sound-engine Java-thread and idle/stopped
measurements. These are hidden, OS-constrained loopback runs under the shared
25% CPU/8 GiB guard, not foreground/device/OS benchmarks or human HUD/audibility
approval. Broader compatibility and isolated native profiling remain additional
coverage.

## Final candidate-G verification

[final-supported-g.receipt.json](../../build/audio-hud-command-feedback-20261104/final-supported-g.receipt.json)
**PASS**, actual exit 0, **17m 59s**, 282 tasks (246 executed, 36 up-to-date).
The gate includes 1,350 command assertions per version and native invariants
753/751. The [G package review](../../build/audio-hud-command-feedback-20261104/package-validation-g.json)
verifies all four providers against their exact pins and all **2,049 frozen inputs**.
Approved sound bytes, gain 1.0, all other gains and the accepted Infuser behavior
are preserved. Each archive retains 49 approved Oggs, 62 registered cues and 45
custom definitions; production packaging excludes authoring tools and personal
audio paths.

| Final G target | Exact production SHA-256 |
| --- | --- |
| 1.21.1 Fabric | `251fa158b498bf41fbc56d09a4e486c1e119f85adcb35f3704d819f6b2945eea` |
| 1.21.1 NeoForge | `fb6caa2c4557c00e1d4f126a41977973dc41c3f3987f357d4bb016805dfccbe1` |
| 26.1.2 Fabric | `f60b710063f734ec77ff0258a297e5930e8fc83f5cd47189d8afd3bfdbaaa4a8` |
| 26.1.2 NeoForge | `7b9ce36e561c2512583320eb6698a42399fd667f90341df3f7fade662357dac0` |

The current exact [Fabric native-c receipt](../../build/audio-packaged-acceptance/hud-commands-1211fabric-native-20261104-c/receipt.json)
and [NeoForge native-c receipt](../../build/audio-packaged-acceptance/hud-commands-2612neoforge-native-20261104-c/receipt.json)
both **PASS**, four zero exits and 18 windows each. Receipt SHA-256 values are
`2e37784ed8a70d8a4202b4dee1e6d6e18c4c1076bc28873bc9b212ef37e2d854`
and `ea27544e786906af303d8f51fb6e3b7f7ed9574b89479c549de97e74c8d2e8c2`.
Their fixtures are respectively
`32fa804f79262c60ca2e390ff3efbdf94e4f456549c1c90e4bcf66e06195de32`
and `075492d06ff2945b521ad3165e9d6e9ff434b5d6329ae7c882661948cb705b0d`.
Both execute all **57 phase paths** (56 generic PASS markers plus the special
disconnect path), with **391 Fabric / 390 NeoForge server assertions**. Real late
connections/reconnects, native membership, paid work, Pylon states, authorized
normal-key flight and mixed activity retain the shared native scenario coverage.

The preflight now enters `/essence audio help`, settings, audition, start, stop and
`/essence help` through the actual **ChatScreen Enter** path. It verifies command
submission and subsequent chat close; deferred settings remains open afterward,
preview cleanup completes, and server-owned help still dispatches correctly.
This closes F's direct-connection coverage gap without adding another test manager.
Native clients remain headless; this is not human visual or listening acceptance.
These two affected representatives span both versions/loaders, not a new current
all-four-native or FTB cross-product matrix.

The [G combined native summary](../../build/audio-gameplay-acceptance-20261102/hud-commands-g-native-summary-20261104.json)
and [G compact tables](../../build/audio-gameplay-acceptance-20261102/hud-commands-g-native-compact-20261104.md)
report `VERIFIED_NATIVE_MEASUREMENTS`, **36 actual windows**, 41/41 loaded/discovered
EA block entities and stable dense **32 desired / 12 owned / 12 playing** bodies.
Idle/stopped windows have zero block-entity updates and no owned/playing bodies;
all steady windows have zero decode workers. Actual cold/setup buffer counts are
**23/21 Fabric actor/observer** and **22/20 NeoForge actor/observer**, zero failures.

| Final G target | Dense server CPU cores | Java allocation GB / ~21s | Client audio p95 µs | Encoded packets / MB / socket-out MB |
| --- | ---: | ---: | ---: | --- |
| 1.21.1 Fabric | 0.138–0.157 | 1.772–1.775 | 35.1–68.7 | 13,243–13,244 / 4.522 / 2.800 |
| 26.1.2 NeoForge | 0.101–0.137 | 1.741 | 43.0–80.6 | 12,871–12,880 / 4.517 / 2.799 |

Each dense window has 10,752 block-entity snapshots from actual paid progression.
The preceding measurement limits apply unchanged: p95 ranges are not pooled,
allocation is Java heap only, whole-process CPU includes but does not isolate
OpenAL mixing, and hidden OS-constrained loopback results are not foreground/device
benchmarks or human approval. Broader compatibility and isolated native allocation/
mixer profiling are additional coverage.

## Verification and handoff status

| Check | Status / completion condition |
| --- | --- |
| Shared HUD placement/motion repair | **G technical PASS**; restored native position and existing card animation; human visual approval pending |
| Shared audio-command projection and settings opening | **G PASS**, six actual ChatScreen Enter paths on both targets; legacy inline-open bug repaired |
| Both-version focused regressions | **G gate PASS**, 1,350 command assertions per version and native 753/751; earlier receipts retain scope |
| Full supported-target gate / package identity | **G PASS**, 17m 59s/282 tasks, 2,049 frozen inputs and four exact provider/pin hashes |
| Affected actual native checks | **G PASS**, 57 phase paths each/36 windows/eight zero exits; server assertions 391/390; F retains direct-dispatch scope |
| Preferred 1.21.1 Fabric human package | **G PASS / prepared**, new v2 copy, fixture-free/121 critical inputs verified, saved server-only smoke PASS; no agent-launched visible client |
| Human acceptance of smooth card lift and restored vanilla captions | **PENDING**; no visual approval is inferred from headless geometry or prior sound approval |
| Thruster sound, Infuser fix, Pylon Focus-change sound | **ACCEPTED by the user**, scoped to the actions reported above |
| Unreported longer-loop/mix acceptance | **Required human check PENDING**, 30–60-second mixed-activity row below; three accepted sounds stay accepted |
| Selected-material provenance/licence/attribution/redistribution confirmation | **Required reviewer confirmation PENDING**, separate from sound/HUD acceptance |
| GUI-scale-4 machine-panel clipping | **Low / P3 deferred**, shared responsive-machine UI work; use reported working scales 1–3 |
| Broader device/pack/OS, other native/FTB cross-products and isolated native mixer/allocation profiling | **Additional coverage**; generic visual-reload work and unselected historical-source limitations remain outside this pass |

## Provisional F human-package history

The provisional **Minecraft 1.21.1 Fabric** F package is
`human-1211fabric-hud-commands`, using the recorded F Fabric archive above and
endpoint `127.0.0.1:53190`. Its
[prepared-package review](../../build/audio-hud-command-feedback-20261104/human-package-review-f.json)
verifies 121 critical inputs, fixture exclusion and manifest
`c23ed01076200e9f24da6a725487adaa18b684321cf6050fdaf200c23f6b0814`.
The later [server-only smoke](../../build/audio-gameplay-acceptance-20261102/human-hud-commands-server-smoke-20261104.json)
passes with actual exit 0, a saved world and unchanged critical inputs. Its earlier
preparation review's pending smoke/modern-run notes are superseded by these later
results. Older prepared worlds remain retained; no agent launched this visible
human client. **This is provisional history, not the recommended final replacement.**
It is superseded by the verified G package below; do not use the F copy to assess
the corrected chat-Enter behavior.

## Exact preferred G launch and short human check

Use **Minecraft 1.21.1 Fabric**, new package
`human-1211fabric-hud-commands-v2`, endpoint `127.0.0.1:64831`. The
[G human-package review](../../build/audio-hud-command-feedback-20261104/human-package-review-g.json)
verifies the exact final G archive, fixture exclusion and **121 critical inputs**.
Manifest SHA-256:
`5c0993fbe8a9d734338267e330202c40f1c89f778f545c17ee6f4501dab7c391`.
The [G server-only smoke](../../build/audio-gameplay-acceptance-20261102/human-hud-commands-g-server-smoke-20261104.json)
**PASS**, actual exit 0, saved world and unchanged critical inputs. No human client
was launched by the agent; older prepared worlds are retained.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rcboo\Dev\Minecraft\essence_ascendance\build\audio-packaged-acceptance\human-1211fabric-hud-commands-v2\launch-human.ps1"
```

When finished, run `/stop` in game, then close Minecraft to save normally and
release the server port.

The copied accepted saved scene contains Survival EA_A at **0.5,99,-1.5**, a Caster and authorized
Fatigue Flight; Infuser **0,98,1**, Crucible **0,98,3** and eight focused Pylons.
Use GUI scales 1–3. Thruster sound, the Infuser fix and Pylon Focus-change sound
are already accepted; this check asks for command/HUD behavior, not renewed sound
approval.

| Exact action | Expected result / feedback |
| --- | --- |
| Run `/essence help`, `/essence audio` and `/essence debug audio help`; try Tab after `/essence audio audition wea`. Type `/essence audio settings` in normal chat and press Enter. | Normal server help still works, local help is readable/clickable, completion offers matching cues and settings stays open after Enter. Report missing branches, wrong dispatch or a screen that immediately closes. |
| Enable Minecraft's native subtitles. Start the existing Infuser; in the Crucible select All and start personal channeling. Close menus, cast at the stone platform, then jump, release Jump and hold it briefly before landing. | Native captions stay bottom-right. The **right-hand Fatigue Flight stamina card** smoothly lifts to clear native/supplemental text and settles as captions expire. Its ordinary opening/closing animation remains intact. The channel/source card is on the left and is not the target of this check. Report overlap, sideways native text movement, snapping or jitter. |
| In `/essence audio settings`, set supplemental captions **Off** while Minecraft subtitles remain **On**, then run `/essence debug audio play weapon.cast.release`. Set **Follow Subtitles**, disable Minecraft subtitles, let old lines expire and repeat that command; re-enable native subtitles and repeat. | Off does **not** suppress vanilla native subtitles. Follow with native Off shows no caption; native On restores one native line without a duplicate. Report the two settings separately. |
| Keep Infuser infusion and personal channeling active for **30–60 seconds**, occasionally use the Caster and brief Flight, then Stop both infusion and personal channeling in their screens. | Report audible seams/repetition, mix masking or an unwanted lingering tail. This checks the unreported longer-loop/mixed case; it does not reopen the three sound approvals already given. |

If natural activity does not leave **five native rows** visible at once, use this
optional stress case. Temporarily increase Minecraft's Accessibility **Notification
Display Time**, enter the five commands below, then close chat and briefly fly to
show the right stamina card. These are distinct, directly playable WORLD cues;
they do not perform the named attacks. Watch the card rise/settle as lines appear
and expire; repeat a brief flight if it fully recovers first. Restore your original
notification duration afterward.

```mcfunction
/essence debug audio play weapon.cast.release
/essence debug audio play weapon.bolt.impact
/essence debug audio play combat.explosion
/essence debug audio play ward.break
/essence debug audio play projectile.intercept
```

Short IDs above resolve to `essence_ascendance:`; quoted qualified forms also work.
For an additional mixed native/supplemental check, select **Always** with native
subtitles **On**, then `/essence audio audition interface.denied`: the neutral
supplement belongs above the native group, with the right cards lifting clear.
Please report GUI scale/resolution, line count and whether native text stayed in
place while the cards moved smoothly. GUI-scale-4 panel clipping remains deferred;
recorded rights confirmation and unreported mix/loop cases remain pending.

All agent execution remains headless. No screen control is requested or used. The
root agent owns sequential guarded build/native execution. Current final automated
acceptance is **PASS** for candidate G, including the actual chat-Enter repair;
human visual/unreported listening and rights judgments remain separate. Earlier
receipts retain their original hashes and scope.

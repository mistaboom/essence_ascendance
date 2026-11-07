# Audio and sound-accessibility contract

This is the current shared runtime contract. [INVENTORY.md](INVENTORY.md) maps selected material and feature coverage; [AUTHORING.md](AUTHORING.md) owns source/review operations; [VALIDATION.md](VALIDATION.md) separates exact technical evidence from human acceptance. Start with [PASSDOWN.md](PASSDOWN.md) for maintenance.

## Owners and extension points

Features report confirmed facts or current source state. The common presentation path resolves recipients, cues, native playback and accessible text independently of visual admission. Audio never authorizes a transaction, changes gameplay timing, or infers success from a click. Minecraft owns spatialization, sound categories, resource-pack resolution and sound/subtitle settings.

Java owners below are relative to `common/src/main/java/com/mistaboom/essence_ascendance/`.

| Concern | Maintained owner | Boundary |
| --- | --- | --- |
| Definitions | `art/audio-catalog.json`, `audio/AudioCatalog.java` | One immutable definition source for assets, cues, policies, recipes and source profiles; no copied Java cue list or feature policy tables |
| Review and source provenance | `art/audio-manifest.json` and immutable `art/source/audio/receipts/` | Exact asset/revision review; catalog selection does not imply final listening or rights approval |
| Build resources | `tools/audio/AudioAssets.java`, existing `AssetPipeline` | Validate/copy selected bytes; generate `sounds.json`, Ogg resources and runtime descriptor under target `build`; no DAW, encoder or Auxiliary access in normal builds |
| Registration | `audio/AudioContent.java`, common initialization | One Architectury SoundEvent registration path; no client sound class in dedicated-server initialization |
| Authoritative delivery | `network/FeedbackDispatch.java` | Shared capability-negotiated semantic envelope; public and private recipient rules; no parallel native audio packet per occurrence |
| Client fan-out | `client/FeedbackPresentation.java`, `client/ClientPacketDispatch.java` | Queue once; reject stale connection work; visual budgets cannot silence otherwise eligible audio or captions |
| Client composition | `client/audio/AudioRuntime.java` | Existing loaded snapshots/tracked flags, bounded discovery and world/player lifecycle; no rendering dependency |
| Policy and lifecycle | `audio/AudioEngine.java`, `AudioSources.java`, `AudioSourceBridge.java` | One admission, identity, fade, mute, reload, retry and cleanup implementation for all sources |
| Native playback | `client/audio/NativeAudio.java` | Play/stop/native handle state and categories; no per-feature playback code |
| Captions and layout | `client/audio/{CaptionRouter,CaptionLayout}.java`, `mixin/AudioCaptionFrameMixin.java` | Native text actually drawn determines duplicate suppression; neutral relative captions and shared HUD bounds |
| Resource reload | `mixin/AudioSoundReloadMixin.java` | Invalidate after native `SoundManager.apply` commits resources, then reconcile valid bodies |
| Input and result correlation | `client/audio/AudioUi.java`, `audio/FeedbackReceipts.java` | Native widget or custom click, never both; server result remains distinct from input acknowledgement |
| Text | Existing `assets/essence_ascendance/lang/` | Shared localized subtitle/caption keys; no English text in packets or recipes |

The actual path is confirmed feature fact → shared semantic envelope or correlated receipt → catalog recipe → `AudioEngine` → native instance/event → generated sound definition → approved Ogg or explicit native event → native/semantic caption → shared cleanup. Source adapters provide identity, activity, position and bounded semantic parameters, never arbitrary files, gain, caption strings or handles.

The catalog validates identities, references, translations, finite bounds and allowed scope. Recipes use bounded predicates (`minimumA`, `maximumA`, `minimumB`) and non-overlapping contextual alternatives. No runtime scripting, recursive policy inheritance or multi-phase scheduler is needed. Resource packs can replace native samples; they cannot change server recipients or gameplay authority.

## Delivery and occurrence lifetime

World events use same-dimension policy-radius delivery and independent per-modality range checks. Private result data goes only to its authorized recipient, never into a public recipient union. UI feedback is local and non-spatial. Native commands such as `/playsound` retain native behavior; the removed audio packet interceptor must not return.

Identity includes source generation and occurrence sequence, with connection/world acceptance boundaries. A visual seed is not an occurrence ID. Replayed results reuse identity and cannot repeat audio or captions. Glaive uses accepted action/phase identity; late pose snapshots do not replay swings. Interception emits one aggregate accent per action, not per projectile.

One-shot freshness is 100 ticks. Deduplication retains fresh identities until their deadline; it drops new cosmetic work when full instead of evicting fresh identities and permitting replay. Player-scoped committed notices can outlive their menu within that freshness window. Nexus replies instead bind to screen/menu/request incarnation through `FeedbackReceipts`; closing their owner invalidates them. Durable visible denial/result state remains independent of whether a cosmetic cue is admitted.

## Continuous source identity and lifecycle

A body belongs to a scoped source/channel, never to the selected sample alone. Block sources include dimension, position and observed instance identity; entity/player sources include UUID and incarnation/activity identity. Beam keys include both endpoints. Replacing a block/entity at the same coordinates cannot inherit an old body, and an old stop cannot remove the replacement.

`reconcile` is idempotent for unchanged body **and lifecycle**. Active sources fade in; invalid/inactive sources release and disappear. Stop/destroy work bypasses spawn limits. A parameter/profile change replaces the existing voice without overlap. Muted, out-of-range or budget-denied candidates remain cheap only while valid and within bounded interest. Returning sources resume **body only**: no old start, stop, completion or one-shot is replayed.

| Source | Authoritative activity | Required behavior |
| --- | --- | --- |
| Player beam | Positive actual Crucible player transfer snapshots | One destination body; loaded linked Crucible supplies active-pylon count 0–8 |
| Infuser beam | Positive recent grant throughput and transferred types | One midpoint body per valid endpoint pair; processing/demand alone is insufficient |
| Thrust | Existing native tracked thrust flags | Same controller and validity/position suppliers as machines |
| Private channel projection | Existing confirmed session/HUD state | No second audio body over the public transfer beam; preserve HUD freshness without adding an audio heartbeat |

Loaded persistent machine snapshots do not expire merely because no packet arrived. Optional simulation-time leases remain a shared capability for genuine network-only sources. No audio-specific periodic machine packet, server inventory scan or chunk load is permitted.

Tracking loss, chunk unload, source replacement, player death/respawn, dimension/world change and disconnect remove affected sources. Screen close invalidates only screen-bound work. Pause follows native gameplay time; multiplayer menus do not pause world sound. Clock rewind invalidates temporal state. Resource/device recovery stops stale handles, clears unavailable-resource caches, re-resolves definitions and reconciles still-valid bodies with bounded backoff. Missing assets fail silent with bounded diagnostics, not unrelated fallback or per-tick retries.

## Work bounds

These are implemented bounds in shared code/catalog, not promises of native frame cost:

| Resource | Limit |
| --- | --- |
| Retained source candidates / loop voices / one-shot voices | 256 / 24 / 32 |
| Machine body group / thrust body group | 12 / 4 |
| Occurrence attempts per tick | 56 public, 64 total |
| History entries / private reserve / freshness | 512 / 64 / 100 ticks |
| Discovery per tick | At most 32 tracked players, 32 loaded block entities, 25 nearby chunk lookups |
| Saturated machine discovery retry | 20 ticks |
| Active body caption refresh | At most once per 100 ticks |
| Sustain lifecycle | 4-tick attack, 6-tick release, 20-tick restart backoff |

Admission prefers higher priority and nearer sources with stable tie behavior; a denied cue does not queue for a later burst. Source/group repetition and concurrency stay catalog-owned. The runtime retains cursors instead of world-wide lists and never scans all machines every tick. Definitions are parsed once. Source and Java-allocation measurements must remain distinct from isolated native mixer cost.

## Native settings and accessible feedback

Minecraft's Master/category sliders are the only user volume controls. Machine/beam cues use `BLOCKS`, player/weapon/activity cues use `PLAYERS`, and relative interface/transaction cues use `MASTER`. Authored gain remains catalog data, including explicitly approved flight gain 1.0. There is no EA sound-settings screen, preference gain or server-stored sound override. The obsolete EA preference file is ignored and left alone.

EA captions follow Minecraft's **Show Subtitles** option. Volume mute does not disable eligible semantic captions. Native world cues keep native direction and placement; relative/private cues use neutral text without invented arrows. Observe the native overlay's actual drawn text before suppressing the supplement: a sound callback alone does not establish that a subtitle appeared. Never play an inaudible sample to manufacture a caption.

Caption validity and privacy share event/source checks, while caption repetition/admission remain independent of sound-voice and visual budgets. The native frame hook handles version-specific draw order. `CaptionLayout` uses actual occupied HUD/actionbar bounds, including smooth card movement and UI scale. Show Subtitles Off disables captions; switching On can refresh an ongoing body at its next cadence, up to five seconds, without replaying old one-shots. Walking away or unloading is not a gameplay completion/terminal caption.

Native widgets retain their click/narration. Custom accepted mouse/keyboard activations use `AudioUi`; hover, typing, rendering and every drag sample do not. Server acceptance/denial is a separate fact and preserves visible localized reasons. Sound-off operation must remain usable, and captions must not expose hidden entities, balances or unauthorized outcomes.

## Extending and verifying

| Addition | Necessary change | Shared behavior retained |
| --- | --- | --- |
| New take for an existing cue | Reviewed immutable source/export/receipt and catalog selection | Existing transport, native engine and lifecycle |
| New cue for an existing fact | Catalog recipe, localization and coverage expectation | Existing receiver and admission |
| New confirmed fact | One authoritative semantic declaration with identity/context | Existing dispatch and client fan-out |
| New continuous source | Thin adapter/profile plus validity/transition tests | Existing fade, mute, reload, caption and budget implementation |
| New lifecycle capability | Common capability plus two representative-source tests | No feature-local loop manager |
| Native API change | Exact dependency signature evidence and narrow native boundary | Shared catalogs and policy |

[gradle/targets.json](../../gradle/targets.json) owns the four targets: 1.21.1 Fabric/NeoForge use Java 21 and `remapJar`; 26.1.2 Fabric/NeoForge use Java 25 and `shadowJar`. Gradle uses Java 25, runs sequentially, and keeps editor-active source at 1.21.1. Use exact production providers/manifests, never a cached/glob-selected jar. Shared logic stays in authored common sources; selected native roots compile verbatim.

[AUTHORING.md](AUTHORING.md) governs immutable source material and review. [VALIDATION.md](VALIDATION.md) owns evidence scope: compilation, source checks and hidden native fixtures cannot establish perceived mix, native seam quality, human layout or redistribution approval. Preserve approved sound bytes/design and gameplay semantics when changing infrastructure.

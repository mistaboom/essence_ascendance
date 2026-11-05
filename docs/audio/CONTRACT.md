# Audio and sound-accessibility contract

Design baseline: 2026-10-28; integration update: **2026-11-02**. The shared foundation now serves the complete implemented-feature inventory with explicit implemented/native/silent/deferred/blocked classifications. [INVENTORY.md](INVENTORY.md) and its checked [COVERAGE.json](COVERAGE.json) ledger own that coverage. [PASSDOWN.md](PASSDOWN.md) records reusable additions and remaining production work; [VALIDATION.md](VALIDATION.md) distinguishes executed headless checks from listening/world acceptance. [AUTHORING.md](AUTHORING.md) retains the asset review workflow.

Settings-policy update: **2026-11-04**. The [vanilla-settings follow-up](VANILLA-SETTINGS-20261104.md) supersedes the earlier EA preference/menu design with Minecraft-owned volume and subtitle controls. Historical dated receipts retain their original scope; the follow-up records current verification status.

The current implementation supports immediate one-shots, bounded semantic recipe predicates (`minimumA`, `maximumA`, `minimumB`) and non-overlapping contextual alternatives, loaded/tracked continuous sources, optional simulation-time leases, tracked position providers and correlated one-shot receipts. It does not need a multi-phase scheduler or per-feature managers. The design examples below remain illustrative where their names exceed those implemented capabilities. Existing foundation joined-client receipts do not establish human approval of the expanded mix. The 49 creatively approved runtime samples are now selected via explicit integration promotion; unapproved candidates and demonstrations remain excluded. Native mix/listening/rights acceptance is still pending. [INTEGRATION-20261102.md](INTEGRATION-20261102.md) records current selection, provenance and behavior. Existing cue identities remain stable, including dormant definitions for deliberately silent feature routes.

## Scope and governing decisions

Feature code declares a confirmed occurrence or publishes current source state. Shared presentation infrastructure decides recipients, resolves a cue, applies settings and repetition policy, plays it, and supplies accessible feedback. A button and a machine use the same cue catalog, resolution, routing, localization and admission path. A continuous machine adds the same lifecycle capability that channeling or flight can use.

This is an extension of the existing common presentation architecture, not a second gameplay bus or a replacement Minecraft sound engine. Keep native spatialization, sound categories, resource-pack sound resolution and subtitles. Keep visual recipes and their renderer budgets. Extract shared dispatch responsibilities where both modalities need them; do not make sound depend on a visible particle, render frame, mesh load or visual admission decision.

No change to gameplay timing, source-wide throughput, paid Infuser work, progression, damage, ownership, saves, membership, channel limits or public target support is authorized by this design. Preserve one active Crucible source per player and multiple consumers per physical source. Feedback cannot authorize a transaction or infer success from a click.

## Existing foundations and precise extension points

The table below preserves the design-baseline extraction plan. References to
proposed owners or capabilities that "do not exist yet" describe that baseline,
not the integrated implementation. See the [independent audit](AUDIT-20261102.md)
for current concrete ownership, transport, source bounds and verified extensions.

Java paths below are relative to `common/src/main/java/com/mistaboom/essence_ascendance/` unless qualified. These owners exist today; new names later in this document are proposed.

| Existing owner | Reuse / extension | Boundary to preserve |
| --- | --- | --- |
| `network/TransientVisualDispatch` | Move its same-dimension, radius and `canPlayerReceive` recipient selection into shared `network/FeedbackDispatch`; keep old methods as thin visual compatibility callers during migration | Current nearby and acting-player GUI dispatch is real; there is no generic audio transport today |
| `network/{MicroVisualFeedback,CombatVisualFeedback,ProgressionVisualFeedback}` and `visual/transientfx/TransientVisualIds` | Preserve semantic call sites and events; map their occurrences to feedback recipes once | Color, intensity, seed and visual lifetime are not sound category, occurrence identity or audio lifetime |
| `network/{WorldVisualEventPayload,GuiVisualEventPayload}` | One receiver per payload enters a common `client/FeedbackPresentation` fan-out before visual filtering | Existing codecs have no universal occurrence ID; GUI container ID alone is insufficient for reopened screens |
| `client/ClientPacketDispatch` | All network work uses `queue`; add audio clear to session clearing, resource invalidation to reload coordination | Connection epochs already reject old queued work; respawn intentionally keeps the connection, so world/entity audio also checks its own scope |
| `client/EssenceAscendanceClient`, `mixin/TransientVisualClientMixin` | Initialize client audio once; extend existing tick/cleanup entry point and reload registration | No per-feature render or tick mixins; no client audio classes in common server initialization |
| `visual/MachineVisualState` and four machine block entities | Consume existing observer update tags, snapshot application and initial chunk state | These are public presentation facts, not authoritative finance; snapshot equality already avoids redundant sends |
| `visual/FlightVisualState`, `network/WeaponActionStatePayload`, `equipment/WeaponActionRuntime` | Reuse tracked flight flags and accepted action IDs/phases; do not reconstruct actions from animation | Entity tracking/attachments remain at their existing native boundaries |
| `network/TeamInterfaceNetworkService`, `network/ChannelSessionsPayload`, `client/ChannelSessionsClientState` | Reuse existing confirmed channel projection; expose freshness/acceptance outside its renderer | Current service evaluates every 4 ticks and refreshes unchanged HUD state every 16; current client freshness clearing occurs in `render` after 20 ticks. Audio must not depend on that render call |
| `lifecycle/PlayerRuntimeLifecycleService`, existing entity/guard removal hooks | Extend ordered cleanup only where new transient server feedback state exists | Never persist audio handles or move gameplay cleanup into the sound engine |
| `client/MachineContainerScreen`, `client/ui/fullscreen/FullscreenControls`, existing custom input owners | Shared acknowledgement helper/wrapper for EA controls; preserve native widget narration/focus | Do not globally intercept every Minecraft click or add an extra sound after native `Button` playback |
| Architectury `DeferredRegister` in content classes, `EssenceAscendance.init()` | Register EA SoundEvents from the generated immutable catalog once in common initialization | `ContentRegistrationCompat` currently adapts item/block construction, not sounds; add an adapter only for an observed incompatible sound signature |
| `assets/essence_ascendance/lang/en_us.json`, `SharedPresentationTest` | Add subtitle and settings translations through the existing language resource and component checks | No English text in recipes, packets or sound implementation |
| `gradle/assets.gradle`, `tools/AssetPipeline.java`, `tools/AssetPipelineTest.java` | Extend the shared build-owned asset pipeline with an audio import/validation category | Existing pipeline imports Blockbench art; audio import, encoding receipts and sound checks do not exist yet |
| `gradle/test-inputs.gradle`, common JavaExec checks, production archive invariants | Add reusable contract/lifecycle suites and exact native probes to normal checks | Existing successful builds/visual fixtures do not establish sound or subtitle behavior |

## Authoritative data ownership

Canonical catalog: `art/audio-catalog.json`, now referenced once by an audio category entry in `art/asset-manifest.json`. It has versioned sections `assets`, `policies`, `cues`, `recipes`, and `sourceProfiles`. `art/audio-manifest.json` owns authoring recipes/take history/review by the same asset IDs. References are explicit IDs. The implemented authoring slice supports direct named policies without inheritance; any later inheritance capability must be one level with no recursive overrides. Resolve and validate once at build/startup, not per event.

| Concern | Single authoritative owner | Derived consumers |
| --- | --- | --- |
| Cue identity | `cues.<namespace:path>`; EA cue ID is also its native SoundEvent ID | Registration, recipe references and generated `sounds.json`; no separately maintained Java list |
| Asset identity, paths, approved bytes, provenance and export settings | `assets.<assetId>` | Cue asset references; build copies approved OGG to declared resource path |
| Subtitle key | Cue's explicit `subtitleKey`, or explicit `none` with reason | Generated SoundEvent subtitle and semantic caption use the same key |
| Translated text | Existing `common/src/main/resources/assets/essence_ascendance/lang/<locale>.json` | Native subtitles, localized captions, controls and narration |
| Category, gain, pitch, attenuation, priority | Named playback policy referenced by cue; exceptional values explicitly on the cue and validated | Shared router and native sound instance construction |
| Variant selection | Cue's variant rule and weighted asset references | Native weighted sample selection, seeded by occurrence; recipes may select semantic cue variants such as launch versus impact |
| Recipients and radius | Cue's named recipient policy | Shared server dispatch plus client validation; caller supplies actor/position/context, never a new radius |
| Repetition, concurrency and aggregation | Cue's named admission policy | Shared audio admission; no feature-specific cooldown maps |
| Caption eligibility, repetition and priority | Cue's caption policy within named admission policy | CaptionRouter; translated text still belongs only to language JSON |
| Continuous behavior | Named lifecycle policy, referenced by source profile | Shared continuous controller; profile also identifies start/body/end cue references |
| Occurrence-to-cue mapping / contextual composition | `recipes`, keyed by semantic event and scope | Shared fan-out, including explicit silence; bounded typed predicates, no scripts |
| Source facts | Existing gameplay authority / synchronized snapshot | Source adapter supplies identity, active condition and normalized parameters only |
| User volume and subtitle controls | Minecraft's client options: Master/category sliders and Show Subtitles | Native mixing and shared CaptionRouter; no separate EA preferences, menu or server-stored override |
| Runtime session state | Proposed `AudioRuntime` and its continuous controller | Handles, deduplication, admission and cleanup; never disk or saved NBT |

Every field above must have one resolved value. Reject duplicate IDs/paths, unknown references, cycles, ambiguous mappings, missing translations, non-finite numbers and illegal source/scope combinations. A cue cannot accept arbitrary filenames, subtitle strings or gain from a packet. Packs may replace native samples/subtitle resources using normal resource precedence; they cannot widen server recipients or rewrite gameplay through this catalog. Initial policy/recipe definitions are shipped immutable program data; general user scripting and datapack behavior injection are outside this foundation.

### Concrete catalog example (illustrative approved subset)

```json
{
  "schema": 1,
  "assets": {
    "resonance_body_a": {
      "status": "approved",
      "revision": "r0001",
      "master": "source/audio/masters/resonance_body_a/r0001.wav",
      "approved": "source/audio/approved/resonance_body_a/r0001.ogg",
      "resource": "essence_ascendance:machine/resonance_body_a",
      "recipe": "source/audio/recipes/history/resonance_body_a/r0001.json",
      "receipt": "source/audio/receipts/resonance_body_a/r0001.json"
    }
  },
  "policies": {
    "playback": {"machine_body": {"category": "BLOCKS", "gain": 0.25,
      "pitch": 1.0, "attenuation": "linear", "priority": 10}},
    "recipients": {"near_machine": {"scope": "world", "radius": 16}},
    "admission": {"machine_body": {"group": "machine", "perSource": 1,
      "perGroup": 12, "repeatTicks": 0, "overflow": "prefer_near"}},
    "lifecycle": {"sustain": {"attackTicks": 4, "releaseTicks": 6,
      "resume": "body_only", "restartBackoffTicks": 20}}
  },
  "cues": {
    "essence_ascendance:machine.resonance.body": {
      "playback": "machine_body", "recipients": "near_machine",
      "admission": "machine_body",
      "subtitleKey": "subtitles.essence_ascendance.machine.resonance",
      "variants": {"selection": "native_weighted", "samples": [
        {"asset": "resonance_body_a", "weight": 1}]}
    }
  },
  "recipes": {
    "machine.resonance": {"body": "essence_ascendance:machine.resonance.body"}
  },
  "sourceProfiles": {
    "machine.resonance": {"recipe": "machine.resonance", "lifecycle": "sustain"}
  }
}
```

This historical schema example uses one body cue; a source need not add a start/end sample. The current integration selects 49 approved runtime exports; unselected candidates remain `authoring_only`. A receipt contains master/approved/frozen-recipe/project hashes, license/attribution state, encoder/version/options, sample rate/channels, duration, measured peak/loudness and review state; it must agree with the selected take in the authoring manifest. An integration receipt preserves pending final review. Lifecycle policy does not duplicate playback gain or recipient range. The example asset's `resource` expands to `assets/essence_ascendance/sounds/machine/resonance_body_a.ogg`; the cue expands to one `sounds.json` event with its subtitle and approved asset entry. Paths are resolved from `art/`, validated to stay there, and cannot point into the auxiliary workspace at build time.

Build produces `sounds.json`, runtime catalog descriptors and OGG copies under the existing target-local `common/versions/<minecraft>/build/generated-resources/assets/assets/essence_ascendance/`. Runtime descriptors omit authoring paths. Registration reads the generated immutable descriptor before registry freeze on both sides. Resource reload re-resolves native assets, not registry IDs. A sound-only recipe cannot require a visual recipe registration to be accepted.

## Minimal shared runtime

Use a small composition of concrete services, not an extension framework for hypothetical loaders:

1. `AudioCatalog` resolves immutable data and registers SoundEvents through common Architectury registration. `FeedbackRecipes` maps semantic context to cue references and existing visual recipes.
2. `FeedbackDispatch` owns public world and private player delivery, including existing visual dispatch. `FeedbackPresentation` receives once on the client thread and fans out independently to visual recipes and `AudioRuntime`.
3. `AudioRuntime` reads native settings and resolves source position, admission, native playback and caption decisions. `ContinuousFeedback` is an owned component of this runtime, using the same resolution/admission path. It has no separate cue database or per-feature engine.
4. `NativeAudio` contains only native play/stop/handle-status/tickable-instance and subtitle-boundary operations whose signatures actually differ. Minecraft owns local volume and subtitle settings. Caption presentation uses existing screen/HUD composition, with one bounded semantic queue for eligible feedback not already drawn by native subtitles.

Proposed operations (contract signatures, not required exact Java syntax):

```text
Feedback.world(semanticEvent, worldContext)       // confirmed server fact
Feedback.player(semanticEvent, recipientContext)  // private confirmed fact
Feedback.ui(semanticEvent, screenContext)         // client-local input/navigation
ContinuousFeedback.reconcile(sourceKey, profileId, desiredState)
ContinuousFeedback.remove(sourceKey, reason)
AudioRuntime.clear(scope, reason)
```

`worldContext`: level/dimension, actor identity if any, source identity, position, occurrence ID, originating server tick and bounded typed semantic parameters. `recipientContext` adds the exact recipient UUID and optional request/menu generation. `screenContext`: screen instance generation, control/action ID and local input sequence. `desiredState`: active, position or tracked entity reference, revision and only profile-declared normalized parameters (e.g. work rate). Sound filenames, volumes, categories, cooldowns, caption strings and loop handles never belong at these call sites.

World cues are spatial and audible only in the policy radius; player-targeted cues are private and relative by default, with position allowed only by an explicit policy. UI cues are non-spatial. Continuous cues choose world/player scope through their profile and use the same native category/settings path. One semantic event may choose different world and private cues, but one listener gets a given cue occurrence only once.

### Network authority and deduplication

Current lifetime policy: `PLAYER`-scoped committed notices (team mutations,
Infuser activation and exact soulbind confirmation) belong to the recipient and
may arrive after their menu closes, within the shared 100-tick freshness window.
They report already confirmed outcomes, not a result for a newly opened screen.
Nexus replies are explicitly screen/menu/request-bound by `FeedbackReceipts` and
are discarded after that owner closes. Neither route is inferred from a click.

Do not add an audio packet alongside every existing visual packet. For an existing event with sufficient facts, its single receive path invokes a recipe for both modalities. Move receiver registration out of the visual managers into `FeedbackPresentation`, then call their existing acceptance functions. Keep old semantic IDs and visual composition intact. Add occurrence identity and any missing typed context to the shared envelope in one coordinated protocol revision; do not silently append fields while accepting incompatible clients. Preserve supported-client capability checks. Same mod protocol is required; explicit versioned payload IDs/handshake handle any supported compatibility window.

Extract audience calculation from `TransientVisualDispatch` once. Where public visual and sound ranges differ, send one envelope to the union of their approved public recipients and apply independent range checks per modality. Private result data must use separate private delivery, never be added to a public union. Existing server facts sent only to an actor do not become audible to observers merely because a recipe has a world sound. Add public facts deliberately at the authority boundary if necessary.

Occurrence identity is `(serverSession, sourceGeneration, monotonicSequence)`; native action/transaction IDs can supply the sequence where their uniqueness is established. Client session epoch is an additional acceptance boundary. Visual seed alone is not unique. A local input acknowledgement and server result are distinct occurrences; retrying a result carries the original ID. Existing gameplay operations without identity need a feedback-only sequence allocated at the confirmed emission, not a new transaction system. Duplicate/replayed packets must neither restart a loop nor repeat a caption.

One-shot freshness is bounded to 100 server ticks using the existing synchronized game-time basis, with pause/world-reset handling. Expired cosmetic feedback is dropped, while durable transaction reason text remains in its existing result state. Dedup entries live until the occurrence's freshness deadline; never evict a still-fresh identity just to admit another cue, since that permits replay. At the 512-entry limit drop new cosmetic occurrences; reserve 64 entries for private critical results. Source revisions/action IDs additionally reject old state. A new cue cannot extend retention beyond the shared bound. Missing reliable actor identity cannot be guessed from visual color/target; supply it once at the confirmed event if a private recipe needs it.

For Glaive state, use `(owner UUID, actionId, semantic phase)` and source generation. Process the accepted phase once, not every pose sample. Late observer snapshots establish current state without replaying a past swing. A batch interception event emits one aggregate cue for the swing, not one cue per intercepted projectile; retain per-projectile visuals if desired.

## Source identity and continuous lifecycle

`FeedbackSourceKey` is a tagged value with session/world scope plus:

| Source kind | Identity | Position / validity |
| --- | --- | --- |
| Block | Dimension + block position + client-observed instance generation + channel | Existing loaded block entity and observer snapshot; replacement at same position gets a new generation |
| Entity | Dimension + entity UUID + observed incarnation + channel | Network entity ID is only a lookup hint; verify UUID and entity object/incarnation |
| Player activity | Player UUID + activity/session ID + channel | Existing confirmed channel/action state; respawn invalidates old instance even on the same connection |
| Interface | Client session + screen generation + control/activity channel | Screen/container generation, not just numeric container ID |
| Fixed transient point | Dimension + occurrence ID | Immutable location and bounded lifetime |

Channel identifies simultaneous roles such as `work` or `thrust`, not an asset. Deduplication key for a loop is source key + channel, so a variant/parameter change cannot create parallel bodies. `reconcile` is idempotent: unchanged state performs no allocation, packet, resolution or restart. State sequence rejects old updates for the same generation. An old stop cannot stop a new block/entity instance.

State machine: absent -> desired/eligible -> fading in -> running -> fading out -> absent. Inactive, removed or invalid source clears desired state. Muted/out-of-range/budget-denied sources remain cheap desired candidates only while valid and within the bounded interest registry. They resume **body only**, with no historical start, stop or completion cue. Rapid inactive/active changes reuse a valid fading handle where possible; otherwise stop old handle before creating one replacement. One-shot start/end accents are recipe choices, not mandatory lifecycle sounds.

Hooks and failure rules:

- Machine adapters run on loaded observer snapshots and through the bounded nearby recovery cursor. Player beams require positive actual Crucible player streams; Infuser beams require positive recent grant throughput and transferred types. The loaded linked Crucible supplies authoritative active-pylon count, capped at eight. Each key contains both endpoint incarnations and uses the beam midpoint. Generic dissolution/processing, idle links and independent Pylon/Focus hums are silent. Do not read menu-only state or scan server inventories for audio.
- Tracking begin supplies current entity state; tracking end, chunk unload and block/entity replacement remove its source. Initial active snapshots start only the body. Repeated snapshot loads must not imply a new generation unless object identity actually changed.
- Private `ChannelSessionsPayload` projection retains HUD/session authority but creates no audio body; the public loaded transfer snapshots own the one beam. Existing lease capability stays shared and tested for genuine network-only sources, without another heartbeat or duplicated private/public voice.
- Unchanged persistent machine snapshots do **not** expire merely because no packet arrived. Their validity comes from the still-loaded object. Network-only activity sources use an explicit lease and low-frequency renewal only if no existing tracked lifecycle/state can supply validity. Default lease 100 ticks, renewal at 40 ticks; stop on expiry and never force a chunk load. No heartbeat is added to ordinary machine snapshots.
- Player death, respawn, dimension change and disconnect remove affected sources. World object change clears all world-bound handles, including returning to the same dimension later. Old queued work is rejected by `ClientPacketDispatch` plus world/source checks.
- Screen close invalidates screen-bound cues/results; private long-lived player results may remain HUD feedback if their recipe explicitly says so. UI dismissal never means a transaction was canceled on the server.
- Reload stops handles using old resolved assets, waits for native resource reload completion, re-resolves once and reconciles still-valid desired bodies. Missing assets fail silent with a deduplicated diagnostic; never retry every tick or fall back to an unrelated sound. Device loss/restart follows the same body-only reconcile with 20-tick backoff.
- Single-player pause follows the native pause boundary; do not advance fades/leases using wall time while gameplay is paused. Multiplayer menus do not pause world sound. Local UI acknowledgements use a monotonic UI clock; game-time rewind invalidates temporal audio state. Verify native pause/resume on all four targets.

### Bounded work and admission defaults

These are proposed initial hard limits, owned by common policy and tunable only after measurement: 256 interested continuous sources/client, 24 playing loop voices total, 12 machine bodies, 32 transient EA voices, 64 accepted one-shot attempts/tick, 512 dedup/repetition entries with at most 100 ticks retention, 8 pending semantic captions. These limits affect EA feedback only; do not manipulate vanilla sound-engine global limits. A source can contribute at most one body per declared channel, and a recipe at most three one-shot layers. Stop/destroy processing always bypasses spawn budgets.

Prefer private critical feedback, then nearby confirmed actions, then ambient bodies. On ties use distance and stable source order; hysteresis avoids alternating equally distant machines. If all voices are higher priority, drop the new cue without a deferred burst. Dedup/aggregation keys include source or actor and semantic group; unrelated machines must not suppress one another globally. High-rate work completion aggregates per machine over 10 ticks; micro procs use a 10-tick per-source/family interval. Critical private transitions have no cosmetic repeat interval, but duplicate IDs still suppress replay. Default UI input interval is 2 ticks per control and no held-pointer repeat unless the control itself implements repeat.

No per-render work, world-wide block scans, per-sample packets or repeated resource parsing. The idle runtime returns immediately. Each tick visits only admitted handles for fades/position/validity; static positions are cached. Rerank interested sources when snapshots, settings or listener interest change, at most every 10 ticks for movement. Prefer squared distance and reuse resolved immutable records.

Interest registration uses native loaded/tracking callbacks and existing snapshot delivery. To re-admit a previously evicted/out-of-range machine without a new state packet, use a bounded cursor over already-loaded block entities in intersecting nearby chunks when the listener changes interest cells, and on a saturated retry at most every 20 ticks. Examine at most 32 candidates/tick; retain a cursor, not a list of the world. Never load a chunk or enumerate every machine each tick. Entity adapters reuse native tracking and a bounded nearby query for missed candidates. Stale references are discarded promptly. Dense worlds may omit distant ambience under budget; they may not leak handles or grow an unbounded waiting list. Profile measured counters for candidates visited, eligible sources, voices, drops, retries and packets; do not log every tick.

## Settings, subtitles and accessible feedback

EA sound uses native Master and category controls as its sole user-facing mixer. Category policy: machine/process/channel-source = `BLOCKS`; weapon/skill/player activity = `PLAYERS`; local interface/transaction result = `MASTER` (non-spatial). There is no invented native UI category. Authored catalog gain remains part of each cue; there are no additional EA master/family gains or continuous-sound toggle. Native mute changes stop muted voices and allow still-valid bodies to reconcile on unmute; never replay missed one-shots.

Minecraft persists its own sound and subtitle options. EA adds no audio preference file or settings screen and does not store these options in `EssenceServerConfig`, balance profiles, `PlayerEssenceData`, or the server-backed skill-HUD preference payload. The obsolete EA audio configuration is ignored and left on disk; it requires no migration or deletion.

Generate native `sounds.json` subtitle fields from cue `subtitleKey`. Use concise semantic text such as “Infuser works”, “Channeling stops”, “Cast released” or “Action unavailable”; do not use “whoosh 03”, tier colors, raw amounts or untranslated IDs. Shared samples may belong to different semantic SoundEvents with distinct captions. Native retained sounds retain their native subtitle ownership; do not clone vanilla translations. Private notices stay private. Captions cannot reveal hidden entities, distant machinery, team balances or denied result details.

**Current verification belongs to the [validation ledger](VALIDATION.md), not this policy.** Inspect exact native `SoundEngine`, subtitle listener/overlay and instance paths for both Minecraft profiles; test Master=0, category=0, restoration, missing sample and Show Subtitles Off/On. Do not assume native subtitles are emitted for an inaudible or rejected sound, or that a looping sound refreshes native subtitle text throughout its lifetime.

EA captions are enabled exactly when Minecraft's Show Subtitles option is enabled; there is no separate off/follow/always mode. This shared gate still allows useful semantic feedback while volume is muted. One `CaptionRouter` owns the bounded supplement for eligible, validated occurrences. Observe text actually drawn by the native overlay to suppress duplicate supplemental lines; native callbacks alone do not prove visible delivery. Shared occurrence admission still rejects replays. Never play a hidden/epsilon-volume sound to manufacture a subtitle.

World cues retain native directional subtitles and vanilla placement. Relative/private cues use neutral supplemental text without invented directional arrows. Preserve readable contrast, existing UI scaling and shared smooth HUD-card movement around actual caption bounds. Captions remain separate from visual effect intensity and audio-voice budgets, while sharing validity/recipient checks and their own bounded repetition policy. Ambient bodies caption once on entering eligible interest and at most once every 100 ticks while still active; no repeated announcements for every waveform loop. Show Subtitles Off disables caption presentation. After switching On, an ongoing body's caption may return at its next refresh cadence (up to five seconds); old one-shots are not replayed. Muting volume alone does not disable eligible semantic captions. Terminal captions occur only on a confirmed stop/failure, never from walking away or unloading. Source absence is not gameplay completion.

Button press is a local acknowledgement, not server success. Standard widgets keep native click and narration; an EA wrapper selects either native click or the shared custom click, never both. Custom-drawn controls invoke the same helper on an accepted activation (mouse and keyboard), not hover, rendering or every drag sample. Server acceptance/rejection uses a separate result recipe and preserves existing visible reason text. Routine clicks need no extra caption; result/denial or meaningful toggles have localized text. Existing screens/HUD remain usable with all sound off. No requirement relies on pitch, stereo, timbre or color alone.

## Authoring, review and promotion

| Location / owner | Content and authority |
| --- | --- |
| Auxiliary `Audio/{projects,masters,previews,exports,references,backups}/` | Editable `.aup3` sessions, revisioned lossless masters/stems, listening batches, validated candidates, references and safely closed backups. Family/asset/revision paths are defined in AUTHORING; never a build dependency or runtime lookup |
| Repository `art/source/audio/masters/` | Immutable promoted lossless reference masters for provenance and build verification; editable projects and source stems remain in Auxiliary |
| Repository `art/source/audio/approved/` and `receipts/` | Exact selected OGG exports and hash/encoder/provenance receipts. Integration receipts preserve pending final mix/loop/rights review; final-review receipts record completed acceptance. Exports are derivative, not an independent creative source |
| `art/audio-catalog.json` | Canonical cue, asset and behavior references; status `authoring_only` excludes candidate bytes from runtime export. Stable cue registration may remain available through native fallbacks |
| `art/audio-manifest.json` | Stable asset IDs link synthesis recipes and immutable auxiliary take history/review to the catalog. Catalog selects an approved revision; frozen receipts must agree with that revision's hashes and review |
| Repository `art/source/audio/recipes/` and `tools/audio/` | Versioned synthesis recipes/parameters/seeds and shared DSP, project, storage, export, validation and promotion owners. No per-cue generation/export script |
| Existing language JSON | Subtitle/settings text and translation review |
| Target-local `build/` | Generated native resources, previews, measurements, check receipts; never hand-edited or committed |

For final release promotion, select the exact reviewed take, verify redistribution rights/attribution and complete actual native mix/loop/listening review. Shared promotion copies the already validated bytes, master, frozen recipe and receipt without re-encoding. An explicit user-authorized integration selection may instead promote creatively accepted takes with an immutable `.integration.json` receipt while preserving pending final-review/rights flags. It cannot promote demonstrations or unaccepted/unselected revisions. Later final review writes a separate receipt; never rewrite source history or fabricate review flags. Catalog `approved` means selected byte ownership, and the selected receipt distinguishes integration from completed final review. Runtime world sounds use mono OGG Vorbis; UI may use mono or stereo when reviewed. Start with 48 kHz exports, short buffered samples and seamless body loops; avoid streaming for short effects. Longer music/ambience streaming is deferred. Export must remove unintended leading silence/clicks, preserve headroom and record loudness/true peak. Initial ceiling is -1 dBTP; family loudness targets are set by reference audition rather than blindly normalizing every short transient to the same LUFS. Loop seams and coexistence with native sounds require listening, not just metadata checks.

Normal builds validate and copy approved exports; they do not require a workstation DAW/encoder or re-encode unchanged audio. The pipeline tracks catalog/master/export/receipt/importer inputs, verifies hash correspondence and emits deterministic resources. Changing a master invalidates its approval receipt until re-exported. Failed import cannot leave partial promoted resources. Runtime JARs contain only the used OGG, generated catalog/sounds configuration and language resources, not WAV/DAW/receipts/tools. Resource-pack replacement, missing/corrupt files and pack reload are explicit tests. An approved audio batch is reviewable with cue IDs, before/after clips and exact export hashes.

## Four-target contract

`gradle/targets.json` remains the authority: `1211Fabric` and `1211NeoForge` use Java 21 and `remapJar`; `2612Fabric` and `2612NeoForge` use Java 25 and `shadowJar`. Gradle runs with Java 25. Shared catalog, recipes, runtime policy, lifecycle and tests live in `common/src/main` / `common/src/test` and common generated resources. Use small Stonecutter conditions for isolated native signature changes; select disjoint native source implementations only when genuinely incompatible. Selected `src/1.21.1` and `src/26.1.2` roots compile verbatim, so their comments cannot select code.

Likely inspection boundaries, **not yet verified differences**: SoundEvent/holder constructors, tickable SoundInstance API, sound reload/pause callbacks and subtitle listener behavior. Existing loader entity tracking/attachment hooks supply entity state; do not create audio-specific copies. Loader entrypoints only register native callbacks where common Architectury hooks are insufficient. No reflection-based runtime version detection, copied per-target catalogs, loader-specific sound policies or speculative future targets.

Use exact production provider aliases and receipts; never pick JARs from a glob/cache. Keep editor-active source at 1.21.1 and Gradle operations sequential. Audio does not alter target metadata, recipe resource formats, release selection or existing visual source ownership.

## Historical paper proofs and extension cost (current routing supersedes example cues)

### 1. Infuser starts working and later stops

1. Existing server processing commits work; its existing observer snapshot changes to `processing()`. No audio decision is added to the processing loop and no money/work is recomputed.
2. Client initial/update application reaches the common machine source adapter. It supplies block instance key, `machine.resonance` profile, active state, position and normalized rate. Repeated equal snapshots are no-ops.
3. `ContinuousFeedback.reconcile` resolves profile/cue once, uses shared recipient-interest/settings/admission and creates one native body handle. Its subtitle/caption comes from the same catalog/localization path as a button result.
4. Rate changes update only declared pitch/gain parameters within policy bounds; they do not restart the body. A new nearby observer receives current snapshot and joins the body without a historical start accent.
5. A later non-processing snapshot fades/stops the handle. Only a distinct confirmed completed-work event may emit `process.complete`; idle/stalled/unloaded is not completion. Block removal/unload stops immediately if required, independently of snapshot arrival. Replacing the block cannot inherit the old handle.

Required additions: one source adapter for existing machine snapshots/lifecycle, one shared profile and reusable body cue/assets; optional semantic completion hook only where absent. Reused: state synchronization, registration, localization, routing, settings, admission, native playback and lifecycle controller. No machine-specific SoundInstance class or looping packet stream.

### 2. Button acknowledgement

1. Existing control accepts one mouse/keyboard activation. Standard `Button` retains native click; an EA custom control calls `Feedback.ui(ui.press, screen/control/input context)` through the shared helper.
2. The local recipe selects the common click cue, if it owns playback; the same catalog/settings/admission/native path runs without a network packet. No gameplay success is implied and no duplicate native click is added.
3. For a server operation, the existing request proceeds unchanged. A confirmed result carries request/occurrence identity and target screen generation to `Feedback.player`. Accepted/rejected results select reusable result cues; obsolete screen-bound replies are discarded and existing reason text remains.

Required additions: a wrapper/helper in common controls, one input mapping and result mapping where needed. Reused: existing request/result service, client queue, screen identity and the entire cue runtime. Routine local navigation requires no server result or new protocol.

### 3. Weapon / skill action

Use explosive payload as the first migration proof. `OffenseProjectilePayloads.impact` already emits `WORLD_EXPLOSIVE_PAYLOAD` after the confirmed effect and separately calls `GENERIC_EXPLODE`. Extend the shared occurrence envelope, map the event to `combat.explosion`, and remove that direct sound call in the same implementation change. Shared dispatch sends once to approved nearby recipients; shared client fan-out independently admits visual and sound, selects the approved sample and native subtitle, and deduplicates replay. Initial cue can preserve the vanilla explosion asset and current 0.35 gain / 1.5 pitch until a custom replacement is approved. An impact burst has one sound, not one sound per damaged target.

For Glaive launch, reuse accepted `actionId`/phase from `WeaponActionStatePayload` before render filtering; never infer sound from interpolated blade position. Interrupted actions invalidate scheduled accents. Ordinary sword/bow native attack/launch audio remains native. Feature damage, projectile authority and cooldowns do not move into feedback.

Required additions: recipe plus cue definition, semantic identity in the shared transport, migration/removal of the old call; custom sample optional. Reused: existing impact/action authority, dispatch, all runtime services. No weapon-local audio timers or duplicate packets.

### 4. Another cue and another continuous source

Another discrete cue using an existing behavior requires **only** an asset/receipt if the sound content is new, one catalog cue (or reuse an existing cue), recipe mapping, translation if semantically new, and an inventory/fixture expectation. If the occurrence is already emitted, there is no gameplay call-site change, new packet, listener, settings file or lifecycle code. If absent, add one declaration at the confirmed outcome, with source/context. Do not add a new behavior class just for a different waveform, tier or pitch.

Another continuous source, such as flight thrust, requires a source adapter over existing `FlightVisualState` tracked flags, a profile selecting body/start/end cues and parameter mapping, and source-specific validity/transition fixture cases. It reuses registration, resolution, native playback, settings, captions, source keys, deduplication, bounded admission, fades, reload/device recovery and cleanup. No new heartbeat if tracking already supplies validity. If a new source genuinely requires a new capability (e.g. a network-only lease), implement it once in the continuous controller with at least two representative source tests; feature adapters only declare that capability. The thrust profile itself remains deferred until the machine and private channel proofs pass.

## Architecture acceptance and stage boundaries

The foundation is acceptable only when the four proofs above run through one resolver/admission/playback/caption path; each policy has the owner in the table; old migrated calls are removed; visuals may be disabled without suppressing audio; audio may be muted without losing required text; continuous handles and queues remain bounded through removal/reload/reconnect; native calls compile and behave on all four targets; and assets have reproducible ownership/provenance.

Stage 0 historically ended with documentation. The subsequent authoring and runtime foundation stages, and the 2026-10-29 feature integration, are implemented as recorded in the newest PASSDOWN/VALIDATION sections. The current integration uses one continuous controller for destination/pylon transfer beams and flight sources, and the common one-shot path for controls and accepted outcomes. Older proposed-stage descriptions are historical. Human audition, expanded in-world caption/coexistence review and the remaining production acceptance matrix are separate gates; headless behavior/packaging checks do not approve a mix.

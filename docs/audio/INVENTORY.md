# Audio inventory

Current source map, consolidated 2026-11-07. The catalog contains **62 registered cues, 68 semantic recipes, 19 continuous profiles and 49 selected custom samples**. The other 34 catalog assets remain authoring-only. [COVERAGE.json](COVERAGE.json) is the machine-checked classification owner; this document summarizes it without duplicating all IDs.

| Coverage | Added EA feedback | Native feedback | Intentional silence | Deferred |
| --- | ---: | ---: | ---: | ---: |
| 90 implemented skills | 29 | 22 | 34 | 5 |
| 43 visual semantics | 21 | 4 | 14 | 4 |
| 24 feature surfaces | 12 | 7 | 1 | 4 |

The frozen [selection](../../art/source/audio/handoffs/prompt7-20261101.json) identifies **51 creatively accepted takes: 49 runtime samples (19 loops, 30 one-shots) and two demonstrations**. The [source audit](PROMPT7-AUDIO-AUDIT.json) binds exact projects, closed backups, masters, exports, recipes/settings/seeds/tools and **233 useful stems**. No selected source material was missing at that audit. [The manifest](../../art/audio-manifest.json) owns review; [the catalog](../../art/audio-catalog.json) owns runtime selection. Integration promotion preserves pending final review. Creative acceptance is not native mix/loop or rights acceptance; see [VALIDATION.md](VALIDATION.md).

## Selected runtime material

Cue IDs below use the `essence_ascendance:` namespace. Every listed take is **r0001 unless explicitly marked otherwise**. A/B effects are controlled variations; beam counts and ceremony lengths are semantic choices, not weighted alternatives.

| Stable cue / role | Approved asset(s) | Integration meaning |
| --- | --- | --- |
| `channel.resonance.body` | `beam_player_p0_v2` through `beam_player_p8_v2` | Nine 12-second player-destination loops, selected by active pylon count 0–8. |
| `machine.resonance.body` | `beam_infuser_p0_v2` through `beam_infuser_p8_v2` | Nine 12-second Infuser-destination loops, selected by active pylon count 0–8. |
| `mobility.thrust.body` | `flight_wind_v2` | Eight-second loop while authoritative thrust is active. |
| `ui.press` | `ui_key_clack_v3`, `ui_key_clack_b_v3` | Custom-control clack variations; native widgets retain their own click. |
| `interface.acknowledge` | `ui_accept_v2` | Meaningful committed acceptance. |
| `interface.denied` | `ui_deny_v2` | Denial response. |
| `progression.purchase` | `nexus_transaction_v2` | Committed Nexus transaction. |
| `machine.focus.install` | `focus_install_v2` | Confirmed changed hand/menu installation; no independent Focus hum. |
| `progression.ascend` | `ascendance_chimes_v4`, `awakening_chimes_v4` | Full 8.0-second ceremony or 3.8-second Awakening, selected from event context. |
| `weapon.cast.release` | **`cast_wineglass` r0002** | Approved 0.8-second falling wineglass; successful projectile spawn only. |
| `weapon.bolt.impact` | `magic_impact_soft_v5`, `magic_impact_soft_b_v5` | One impact cue with two variants for accepted terrain/entity collision. |
| `element.fire.apply` | `fire_catch_v2`, `fire_catch_b_v2` | Ignition variations. |
| `element.fire.burst` | `combustion_v2` | Combustion whoosh. |
| `element.ice.freeze` | `freeze_gust_v2` | Freeze transition. |
| `element.ice.shatter` | `ice_shatter_v2` | Ice fracture. |
| `element.arc` | `static_snap_v2` | Primary discharge; existing chain-hop suppression remains. |
| `element.root` | `root_bind_v2` | Rooting contact. |
| `projectile.redirect` | `redirect_clang_v2` | Confirmed deflection. |
| `projectile.pierce` | `pierce_thuck_v2` | Confirmed piercing. |
| `projectile.intercept` | `intercept_tok_v2`, **`intercept_tok_b_v2` r0002** | One aggregate action accent, not one sound per projectile. |
| `combat.guard.ready` | `riposte_ready_v2` | Actionable guard/Riposte readiness. |
| `combat.guard.release` | `counter_release_v2` | Actual counterattack release; reset/expiry remains silent. |
| `ward.break` | `ward_break_v2` | Shattering Ward break. |
| `vitality.surge` | `adrenaline_heartbeat_v4` | One lub-dub on activation. |
| `vitality.rescue` | `death_defiance_v4` | Initial rescue only; reform remains silent. |
| `mobility.launch` | `mobility_jump_v2` | Enhanced launch/jump. |
| `mobility.boost` | `mobility_rush_v2` | Rush/forward movement burst. |
| `utility.recall` | `companion_recall_v2` | Confirmed companion recall. |
| Player beam **demonstration only** | `beam_player_build_v2` | Approved 27.5-second listening arrangement; never select as a runtime body. |
| Infuser beam **demonstration only** | `beam_infuser_build_v2` | Approved 27.5-second listening arrangement; never select as a runtime body. |

The Focus take's frozen empty `cueIds` remains accurate historical provenance; the new runtime cue is registered without rewriting that source history. The original 27 cue associations remain stable. Beam p1–p8 and Awakening identities extend the catalog; p0 keeps each existing beam body identity.

## Source detail worth preserving

| Selected family | Source and design contract |
| --- | --- |
| Beam bodies | Original strike-free chime modes, staggered breathing notes and compatible destination voicings. Zero pylons is one foundational note; each additional pylon adds one through nine notes at eight pylons. Added notes enrich the chord without proportionate loudness growth. See [beam presets](../../art/source/audio/presets/beam-chimes-v2.json). The two zero-pylon projects intentionally have one original note layer; they are not flattened exports. |
| Ceremonies | Full 160-tick/8.0-second ascendance and 76-tick/3.8-second Awakening are separately arranged, with nine editable chime tracks each. Release begins at normalized 0.58 (4.64/2.204 seconds). [Ceremony presets](../../art/source/audio/presets/ceremony-chimes-v4.json) reuse beam modal ratios/strengths; actual admitted/deferred ceremony start owns timing, not camera ease-out. |
| Wineglass cast | [Frozen r0002 recipe](../../art/source/audio/recipes/history/cast_wineglass/r0002.json): four original layers, one fork-tine contact and descending glass ring. Master is exactly 38,400 frames/0.800 seconds at 48 kHz PCM24; decoded Ogg is 0.797333 seconds. Contact stems and first 32 ms match r0001 bit-for-bit. Only two ring pitch paths change: main bowl holds 1092 Hz for 32 ms, then falls to 190 Hz by 0.58 seconds. |
| Soft impacts | [Soft impact presets](../../art/source/audio/presets/impact-soft-v5.json) retain v4 layer identities/seeds/timing with gentler onset, crack/grit and dispersing tail; B is tighter/darker. Five editable layers each. One terrain/entity contact semantic with controlled variants, not a separate hit-confirmation layer. |
| Heartbeat and rescue | One full-range lub-dub on Adrenaline activation; the original death-defiance accent plays on rescue only. Neither becomes a continuing heartbeat or spirit-reform cue. No tailoring to a particular laptop/streaming playback device. |
| Demonstrations and auditions | Two 27.5-second beam build arrangements show the nine states and never ship as bodies. Outer preview fades and offline repeated/overlapping taps are audition aids, not runtime cadence or loop-seam proof. Raw decoded loop fixtures preserve joins. |

Creative references described character; original recipes do not sample referenced commercial games. This provenance statement is distinct from the still-pending recorded redistribution/attribution decision. Exact old feedback, unselected alternatives and rejected warm-arcane takes remain in immutable manifest history, source recipes, batch JSON and Auxiliary projects. Do not select an asset's latest revision automatically or rewrite frozen authoring-time descriptions to make them appear approved.

## Runtime coverage and deliberate silence

Positive actual player/Infuser transfer snapshots select one destination/pylon body through the common lifecycle. Idle links, dissolution, processing and independent Focus hums are silent. Private channel HUD projection never duplicates the public beam. Focus installation compares committed before/after stacks through the same hand/menu helper; identical installs, removal, load and observer refresh are silent.

Custom controls, correlated Nexus transactions, meaningful team/soulbind outcomes, successful Caster spawn, accepted Glaive action/contact and confirmed bolt/skill transitions use the shared semantic path. Ordinary Infuser menu slot clicks do not emit a custom acceptance accent. A/B sample weights and all role gain, range, repetition and lifecycle policy are catalog-owned. Selected custom sounds use unity pitch; pylon counts and ceremony duration are semantic alternatives rather than random variants.

Native melee, explosions, shield impact, bow/rod actions, tool/place/shear sounds, durability/equipment, anvil/brewing results, widgets and toasts retain native ownership without a custom duplicate. Glaive contact uses ordinary strong melee contact. Passive stats, appearance and synchronization stay silent.

Generic completion, channel start/stop, projectile drag-entry, extra guard-return/retaliation, routine protection, vitality purge/reform and utility transfer/seal/release have no automatic audio recipes; gameplay and visual feedback remain. Dormant registered cue identities remain stable for commands/resource packs. Optional discovery/unlock/gathering/recovery notices, arbitrary tagged processors without authoritative output adapters, music/ambience/DSP and voice are deferred. Adding them requires an explicit useful confirmed fact, not a sound on every scan, snapshot, fuel mutation or proc.

For exact per-ID reasons use [COVERAGE.json](COVERAGE.json). For adding material use [AUTHORING.md](AUTHORING.md); for shared runtime boundaries use [CONTRACT.md](CONTRACT.md).

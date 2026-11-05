# Fresh cast concepts and softer impacts — 2026-11-01

## Latest listening decisions

The user's feedback on [the v4 set](CHIME-REVISIONS-20261101.md) was:

> the magic cast sound is just getting worse. try something new from scratch. the magic impact is better. make it softer all around. the others are great.

The two chime ceremonies, heartbeat, and death-defiance rescue are creatively accepted: `ascendance_chimes_v4`, `awakening_chimes_v4`, `adrenaline_heartbeat_v4`, and `death_defiance_v4`. Together with the 42 accepted v2 takes and two accepted v3 menu clacks, this brings the accepted set to 48 takes. Their audio and editable projects remain unchanged.

Both v4 casts and both v4 impacts require revision: `cast_punch_v4`, `cast_punch_b_v4`, `magic_impact_v4`, and `magic_impact_b_v4`. The shared creative-review CLI records the exact feedback separately from final review. Creative acceptance does not establish native mix, loop, or rights approval, select a catalog variant, or permit promotion. Previous final reviews remain pending. All five new candidates also remain pending creative and final review.

This decision supersedes the previous batch's then-pending creative status while preserving its production receipt and historical files. The user's earlier instruction against tailoring sounds to laptop speakers or Moonlight remains in force. Accepted heartbeat and ceremony designs are not revised for the reported playback setup.

## New batch and comparison intent

The data-only [cast-exploration-20261101 batch](../../art/source/audio/batches/cast-exploration-20261101.json) is authored and finalized: five r0001 candidates, 25 useful stems, five genuine editable Audacity projects with matching normally closed backups, PCM24 masters, and validated Ogg exports. The [production receipt](../../build/audio-cast-exploration-20261101.json) records exact hashes, measurements, validation, and the resolved maintenance incident below. Every prior project/export hash across 66 candidates was verified unchanged. The descriptions below explain the frozen recipes' intent; the new sounds still await user listening review.

| Order | Candidate | Scope of this pass | Stable cue |
| --- | --- | --- | --- |
| 1 | `cast_crystal_v5` | **A — Crystal flick:** a short crystal-facet contact inside a compact air flick, with brief fixed inharmonic resonance. | `weapon.cast.release` |
| 2 | `cast_air_v5` | **B — Hollow air launch:** a small opening contact, rounded pressure swell, and low-mid shell resonance. | `weapon.cast.release` |
| 3 | `cast_release_v5` | **C — Elastic pressure release:** a dry, soft material snap followed by a muted air pocket. | `weapon.cast.release` |
| 4–5 | `magic_impact_soft_v5`, `magic_impact_soft_b_v5` | Retain the v4 atonal contact, with rounder onset, gentler crack/grit, a soft dispersing tail, and lower overall level. B is slightly tighter and darker. | `weapon.bolt.impact` |

Cue IDs use the `essence_ascendance:` namespace. The three casts are distinct concepts created from scratch, not a further revision of the rejected woop and not a proposed random-variation pool. Present them as alternatives so the user can choose a direction before that direction receives its own controlled variations. The two impacts are matched variants within one retained family.

The listening page is Auxiliary `Audio/previews/cast-exploration-20261101/review.html`. It presents casts A/B/C first, followed by the softer impacts, with repeated comparison controls for both groups. Its 27 local file links, 12 audio players, and 20 unique element IDs were checked. The v4 page was regenerated with current creative-review badges; its 43 local links, 19 players, and 31 unique IDs were checked. All targets resolve. Browser playback and visual review were not performed. Neither markup checks nor numerical screening establish that the user likes the new sounds.

## Production, review, and preservation

Every candidate uses the existing recipe/preset → shared synthesis → Audacity → export/validation → review workflow. The [fresh cast presets](../../art/source/audio/presets/cast-exploration-v5.json) express three new material/air concepts; they inherit no timbre from the rejected v2–v4 cast recipes. The [soft impact presets](../../art/source/audio/presets/impact-soft-v5.json) preserve the v4 seeds, layer identities, and timings while changing contact envelopes, spectral balance, and levels. Each candidate has five named, independently editable layers. Common synthesis, project organization, lossless stems, export, validation, and revision handling are unchanged; no production-tool code was changed.

All five `.aup3` files passed SQLite integrity, sample-block, named-track, and actual Audacity-render verification. Each has a matching normally closed backup. The workflow preserved original and resolved recipes, seeds, tool versions/hashes, provenance, stable cue associations, and export settings. Masters/stems are 48 kHz mono PCM24; candidates are 48 kHz mono Vorbis at quality 5. The five files are one-shots; no new decoded-loop tests apply. Earlier accepted loops remain unchanged.

All 66 prior candidate project/export pairs were verified byte-for-byte by their recorded hashes. Historical reviews, rejected directions, and earlier production records remain available. No new candidate is promoted in this pass.

## Technical measurements and listening limits

| Candidate | Decoded duration | True peak | Measured padded LUFS |
| --- | --- | --- | --- |
| Cast A — crystal flick | 0.257 s | −9.85 dBTP | −26.95 |
| Cast B — hollow air launch | 0.317 s | −11.58 dBTP | −26.95 |
| Cast C — elastic pressure release | 0.271 s | −6.44 dBTP | −26.95 |
| Soft impact A | 0.297 s | −12.34 dBTP | −30.65 |
| Soft impact B | 0.297 s | −12.42 dBTP | −30.55 |

The casts were calibrated to the same 400 ms padded loudness measurement for comparison, retaining their different transient shapes and crest factors. Their measured RMS spread is 0.75 dB. This does not make them automatic weighted playback variations, nor does equal measured loudness guarantee equal perceived level.

Relative to their corresponding v4 versions, impact A is 2.75 dB lower in RMS and B is 3.60 dB lower. Their 2–5 kHz energy fractions fall from 21.85%/17.96% to 8.07%/5.21%. The softer pair differs by 0.13 dB RMS and 0.10 measured LU. These comparisons support the intended reduction in level and brightness; perceived softness, comfort, repetition, cast identity, and in-game balance remain human listening judgments.

Numerical validation passed with true peaks from −12.42 to −6.44 dBTP and no clipping. All [34 Python tests](../../build/audio-cast-exploration-20261101-python.log) passed, as did [52 audio assertions per supported version and both ownership checks](../../build/audio-cast-exploration-20261101-assets.log). Passing file and workflow checks does not change the pending creative or final review state.

## Maintenance incident: audit-created SQLite sidecars

The independent v4 audit used SQLite `mode=ro` without `immutable=1`, creating empty WAL files and 32,768-byte SHM sidecars on all eight v4 projects after finalization. The project bytes still matched their closed backups. A subsequent creative-review attempt correctly stopped on the sidecars rather than assuming the projects were safe to review.

The original project and sidecar evidence was archived under Auxiliary `Audio/references/audit-sidecars-20261101/`, with the pre-maintenance record in `before.json`. All eight projects were opened through Audacity with `New`/`OpenProject2`/`GetInfo` only, then closed by normal GUI quit. No save or edit was performed. All sidecars cleared through that normal lifecycle; `ensure_closed` passed and every project retained its exact recorded hash. `after.json` in the same archive records those results. All eight subsequent creative-review decisions succeeded: four accepted and four revision requests, bringing the creative-acceptance total to 48. No sidecar was manually deleted, no validation was bypassed, no project database was edited, and no historical backup was replaced. The [authoring guide](AUTHORING.md) now requires `mode=ro&immutable=1` plus an explicitly closed connection for inspection of already closed, sidecar-free projects.

## Runtime handoff remains unchanged

- The selected cast concept will still serve `weapon.cast.release`. Review the existing 1.6 fallback pitch before selecting a unity-pitch authored candidate; choice and pitch belong in shared routing.
- Preserve one `weapon.bolt.impact` cue for accepted terrain and entity contact. Softer A/B impacts are controlled variations, not new contact semantics or a separate hit-confirmation layer.
- Keep the accepted 8.0/3.8-second ceremonies, single heartbeat, rescue-only event, menu clacks, and other accepted sounds unchanged. Existing ceremony timing/selection, native reuse, deliberate silence, beam-only behavior, and Focus-install handoffs remain in effect.

No gameplay/runtime changes, catalog selection, or final promotion are part of this completed authoring pass. User listening review and final release approval remain pending.

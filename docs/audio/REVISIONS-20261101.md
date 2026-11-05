# Listening revisions — 2026-11-01

## Decisions and review state

The user listened to [the previous 48-candidate set](BATCH-20261101.md), requested six revisions, and said "the rest are great." The exact previous takes now record 42 `creativeReview.status: accepted` decisions and six `revise_requested` decisions. The six are `ascendance_v2`, `ui_press_v2`, `ui_press_b_v2`, `caster_woop_v2`, `caster_woop_b_v2`, and `adrenaline_v2`. Their projects, files, and decision history are preserved. The other 42 sounds are unchanged.

Creative acceptance records the user's judgment of the sound. It does not claim that an in-game mix was heard, establish native-loop or rights approval, change the existing final `review.status: pending`, select a catalog variant, or permit promotion. The shared `creative-review` CLI records reviewer, date, exact notes, and decision history without changing those final review gates.

The user also withdrew the proposed native arrow-impact and full-totem choices. `minecraft:entity.arrow.hit` and the optional `minecraft:entity.arrow.hit_player` reference are superseded for magic-bolt contact by original candidates. `minecraft:item.totem.use` is superseded for death-defiance rescue by a brief original chime/clang. These are authoring and future routing decisions; the current runtime fallbacks have not been changed.

The data-only batch [revisions-20261101.json](../../art/source/audio/batches/revisions-20261101.json) is authored and finalized: ten original r0001 candidates, ten genuine editable Audacity projects with matching normally closed backups, PCM24 masters, validated Ogg exports, and 60 useful stems. Eight short effects contribute 36 stems; the two ceremony arrangements contribute 12 instrument stems each. The [production receipt](../../build/audio-revisions-20261101.json) records exact project/export hashes and validation evidence. Every new candidate remains pending creative and final review. No new asset is promoted. All 48 previous project/export hashes remain unchanged.

## Listening order and creative intent

Descriptions below explain the intended sound, not a claim that the user has heard or approved these revisions. Durations come from the current recipes; final export measurements belong in the generated technical report.

| Order | Candidate | Duration | Intended sound and cue association |
| --- | --- | --- | --- |
| 1 | `ascendance_ceremony_v3` | 8.00 s | A full original triumph: low foundation, rising horn phrase and strings, accelerating timpani, a broad major-chord crown, and settling chimes. `progression.ascend`, full ceremony. |
| 2 | `awakening_ceremony_v3` | 3.80 s | A separately composed shorter arrangement of the same ceremonial family, aligned to natural Awakening. `progression.ascend`, Awakening. |
| 3–4 | `ui_key_clack_v3`, `ui_key_clack_b_v3` | 0.10 s each | Mechanical keypress: plastic downstroke, switch click, and light return. Matched keycap/timing variation. `ui.press`. |
| 5–6 | `cast_tonal_v3`, `cast_tonal_b_v3` | 0.34 s each | Clearly pitched low-mid woop that rises and settles onto G, with a quiet octave body. Matched contour/tuning variation. `weapon.cast.release`. |
| 7 | `adrenaline_heartbeat_v3` | 0.40 s | One muffled heartbeat: fuller lub, then a shorter lighter dub 215 ms later. `vitality.surge`; activation only. |
| 8 | `death_defiance_v3` | 0.76 s | One original bright bell-clang with an open fifth and short shimmer. No extended totem sequence. `vitality.rescue`. |
| 9–10 | `magic_impact_v3`, `magic_impact_b_v3` | 0.32 s each | Compact magical contact, a short G/D energy ring, and fading particle spray. Matched contact/glide variation, sharing the cast's pitch family. `weapon.bolt.impact`. |

Cue IDs in this table use the `essence_ascendance:` namespace. The shared listening page provides individual controls, repeated menu/cast/impact A/B comparisons, and cast-followed-by-impact context. There are no continuous sounds in this revision batch; the accepted beam and flight loops remain unchanged in the previous batch.

## Ceremony timing comes from gameplay

[`AscendanceTierFeedback.play`](../../common/src/main/java/com/mistaboom/essence_ascendance/nexus/AscendanceTierFeedback.java) supplies 160 ticks for full ascendance and 76 ticks for natural Awakening: 8.0 and 3.8 seconds at 20 ticks per second. [`AscendanceCeremonyRecipe`](../../common/src/main/java/com/mistaboom/essence_ascendance/client/transientfx/AscendanceCeremonyRecipe.java) places the visual release at normalized progress 0.58. The compositions therefore reach their main release at 4.64 and 2.204 seconds respectively, then resolve by the visual end. The camera's additional 800 ms ease-out is not additional ceremony animation time.

| Phase | Full ascendance | Awakening |
| --- | --- | --- |
| Gather ends | 1.760 s | 0.836 s |
| Build begins | 1.440 s | 0.684 s |
| Main release / crown | 4.640 s | 2.204 s |
| Settle begins | 6.080 s | 2.888 s |
| Visual and authored audio end | 8.000 s | 3.800 s |

The shorter piece is its own arrangement, not accelerated playback of the longer file. Both use original composition and oscillator/noise synthesis; the game references describe character rather than borrowed samples or melodies.

## Shared production and evidence

The whole batch uses the established recipe/preset → shared synthesis → Audacity project → export/validation → review workflow. The short effects share [revision-v3.json](../../art/source/audio/presets/revision-v3.json); both ceremonies share [ceremony-v3.json](../../art/source/audio/presets/ceremony-v3.json). The common score-event primitive places timed notes on named instrument layers, retaining one editable Audacity track and lossless stem per instrument. Event parameters override that instrument's preset; the shared renderer handles every event and normalizes only the final mix. No per-sound generation script, separate project builder, or exporter is added.

All ten `.aup3` files passed SQLite integrity and sample-block checks, project-render verification, and matching closed-backup verification. The workflow preserved PCM24 masters and stems, frozen source/resolved recipes, seeds, tool hashes/versions, and export settings. Candidates are 48 kHz mono Ogg Vorbis at quality 5. All ten files are one-shots; decoded-loop join tests are not applicable to them. The previously accepted continuous candidates remain unchanged. Historical failed or incomplete reservations remain preserved.

The revision listening page is Auxiliary `Audio/previews/revisions-20261101/review.html`, with 24 audio players and 54 verified local links. The previous page was regenerated with the separate creative-review badges: 108 players and 268 verified local links. All target files and element IDs were validated. Browser playback and visual review were not performed; these checks establish report/file integrity, not listening acceptance.

### Numerical measurements and their limits

| Candidate family | Decoded duration | True peak | Measured LUFS |
| --- | --- | --- | --- |
| Full ascendance | 8.000 s | −5.40 dBTP | −18.94 |
| Awakening | 3.800 s | −5.52 dBTP | −18.82 |
| Menu clack A/B | 0.092 s each | −11.19 to −9.40 dBTP | −34.65 to −34.55 |
| Tonal cast A/B | 0.337 s each | −14.89 to −14.86 dBTP | −24.15 to −23.95 |
| Heartbeat | 0.397 s | −8.44 dBTP | −27.95 |
| Rescue clang | 0.757 s | −7.12 dBTP | −23.49 |
| Magic impact A/B | 0.317 s each | −10.22 to −9.88 dBTP | −25.85 to −25.65 |

Numerical screening found no clipping or gap warnings. Leading audio below −60 dB lasts at most 0.34 ms; matched A/B pairs differ by at most 0.20 measured LUFS. These are file measurements, and short-event LUFS values are not a substitute for perceived balance. Spectral, silence, and repetition reports support review but cannot establish pleasant timbre, absence of perceived harshness, fatigue, or the in-game mix. Human listening to all revised takes remains pending.

All [34 Python tests](../../build/audio-revisions-20261101-python.log) passed. The [asset and ownership checks](../../build/audio-revisions-20261101-assets.log) passed for both supported versions, with 52 audio assertions per version. An independent agent audit also passed. No numerical or automated result changes the creative/final review state.

The receipt preserves two recovered execution issues: an initial finalization attempt used system Python without NumPy and failed before mutation, then succeeded with the documented virtual environment; an initial report checker misclassified local file URLs as external, then was corrected and all targets validated. Neither issue required altering a candidate, an old project/export, or its review history.

## Runtime handoff, not gameplay changes

- **Ceremony:** Select the 8.0-second or 3.8-second arrangement through the shared catalog/presentation route using the existing event's Awakening `parameterA` and 160/76-tick lifetime. Start with the actual admitted visual ceremony, including any existing deferral, rather than an earlier Nexus receipt. Preserve unity playback speed/pitch and a single occurrence. No new gameplay packet or feature-owned audio player is needed.
- **Cast:** These tonal files are authored at unity pitch. The existing 1.6 fallback pitch was tuned for a different sample and must be reviewed in shared routing before selecting these candidates.
- **Magic impact:** Keep one semantic `weapon.bolt.impact` cue for accepted terrain and entity contact. A/B are controlled variations, not a new surface taxonomy or duplicate hit-confirmation cue.
- **Rescue:** The new short clang belongs to `vitality.rescue` only. It does not introduce a later `vitality.reform` replay or revive the previously approved-silent reform notice.
- **Unchanged decisions:** Preserve the 42 creatively accepted sounds and the earlier beam-only, deliberate-silence, native-reuse, and Focus-install handoffs. Acceptance here does not install those changes.

No runtime/gameplay source, catalog selection, or production resource is changed by this authoring revision. In-game mix, actual native playback, and final promotion approval remain separate work.

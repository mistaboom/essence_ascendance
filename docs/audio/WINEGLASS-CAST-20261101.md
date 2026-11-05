# Wineglass cast — 2026-11-01

## Direction and review state

The user changed direction after [the hollow-air refinement](CAST-AIR-REFINEMENT-20261101.md):

> okay new direction for the sound. try the tink of a wineglass. like just one tine of a fork hitting the bell of a wineglass.

`cast_air_v5` r0002 records a creative revision request for this direction change. No other acceptance or rejection is inferred. The 50 previously creatively accepted takes remain unchanged; earlier cast alternatives and their review histories are preserved. Creative acceptance remains separate from native mix, rights, and final release approval.

The [wineglass-cast-20261101 batch](../../art/source/audio/batches/wineglass-cast-20261101.json) is authored and finalized with one candidate: **`cast_wineglass` r0001**. Four original synthesized layers combine tiny fork contact, 9 ms contact grain, paired 1092/1094.4 Hz bowl resonances, and upper glass modes. Its intended character is one fork tine touching a wineglass bowl. The take remains pending creative and final review. The [production receipt](../../build/audio-wineglass-cast-20261101.json) records all files and checks; all 72 prior project/export pairs are verified unchanged.

## Production and handoff

The shared workflow produced a genuine editable `.aup3`, four useful stems, a lossless master, frozen source/resolved recipes, seed/tool provenance, validated Ogg, and matching normally closed backup. Actual Audacity-render agreement, immutable SQLite integrity, and archived source/stem/tool hashes passed. Master/stems are 48 kHz mono PCM24; export is mono Vorbis at quality 5. The master lasts 0.800 s and decoded Ogg 0.797333 s, measuring −10.87 dBTP, −26.69 LUFS, and −25.751 dBFS RMS.

The listening page is Auxiliary `Audio/previews/wineglass-cast-20261101/review.html`: six checked local links, three audio players, and ten unique IDs. It includes eight isolated taps spaced 1.05 s apart and eight overlapping decoded taps at 0.4 s onsets. The latter preserves every full tail, including the last: 172,672 frames/3.597333 s, −10.54 dBTP, with no normalization. `overlapping-taps.json` in that preview directory records the fixture. This is offline audition, not a claim about runtime cadence, voice stealing, or in-game mix. The prior refinement page was regenerated with its revision-request badge; seven links/four players/13 IDs were checked. Browser playback and visual review were not performed.

The reusable `quality.overlapping_preview` helper adds full-tail summing at exact onsets, rejecting invalid inputs and sample clipping before writing. It does not trim or normalize; existing isolated repetition remains unchanged. Its archived hash is recorded in the receipt. Five new tests cover the helper; the full current [39-test Python suite](../../build/audio-wineglass-cast-20261101-python.log) passed, along with [52 audio assertions per supported version and both ownership checks](../../build/audio-wineglass-cast-20261101-assets.log). Numerical results do not establish glass character, pleasant attack/brightness, repetition comfort, or native mix. No new loop is required; user listening remains pending.

The cue remains `essence_ascendance:weapon.cast.release`, once after successful projectile spawn. Failed spawns and cooldown-rejected presses remain silent. Preserve the unity-pitch handoff: the existing 1.6 fallback pitch requires shared-routing review before selecting authored audio. Projectile impact stays separate. Only the shared QC helper changed; synthesis, gameplay/runtime, catalog selection, and promotion did not. The user's no-device-tailoring instruction and immutable SQLite inspection guidance remain unchanged.

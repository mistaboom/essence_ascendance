# Wineglass pitch descent — 2026-11-01

Approval update: the user subsequently accepted `cast_wineglass r0002` with “perfect” and declared sound creation/revisions complete. The manifest now records creative acceptance. [The prompt 7 handoff](PROMPT7-HANDOFF.md) and exact selection supersede the creative-pending state in this historical production receipt; native mix/final release review remains pending. The audio and source files are unchanged.

## Direction and review state

The user called [the wineglass candidate](WINEGLASS-CAST-20261101.md) a good starting point and requested the same sound with a pronounced pitch drop after the initial hit. They then clarified that its length should stay the same and the descent should remain audible, without falling too quickly or too far to hear.

`cast_wineglass` r0001 records a creative revision request containing both notes. Positive feedback on the base sound does not approve the requested revision in advance. The 50 previously creatively accepted takes and all other review states remain unchanged; r0001 and its files are preserved.

The [wineglass-drop-20261101 batch](../../art/source/audio/batches/wineglass-drop-20261101.json) is authored and finalized with one new immutable **`cast_wineglass` r0002** take. Only the two continuous ring layers descend, with the main bowl frequency held at 1092 Hz for 32 ms, then falling to 190 Hz by 0.58 s. Contact layers, seeds, envelopes, preset, and duration are unchanged. The [production receipt](../../build/audio-wineglass-drop-20261101.json) verifies that both contact stems and the first 32 ms of the PCM24 master are bit-identical to r0001. The new take remains pending creative and final review.

## Shared production and handoff

The shared workflow produced a genuine four-track editable Audacity project, four stems, a matching normally closed backup, PCM24 master, frozen recipe/seed/tool provenance, and validated mono Vorbis candidate. Actual project-render agreement and immutable SQLite integrity passed. All 73 earlier project/export/backup pairs remain unchanged. The 48 kHz master is exactly 38,400 frames/0.800 s; decoded Ogg is 0.797333 s, matching r0001's decoded duration. The export measures −10.87 dBTP and −27.66 LUFS.

The listening page is Auxiliary `Audio/previews/wineglass-drop-20261101/review.html`, with four audio players, seven validated local links, and 12 unique IDs. It includes three original-then-falling pairs and eight full-tail overlapping hits at 0.4 s onsets. The overlap fixture measures −10.08 dBTP without clipping and retains original levels. These are offline comparisons, not runtime cadence or voice-budget tests. Browser playback and visual review were not performed.

Targeted synthesis, deterministic output, preserved opening/contact stems, project agreement, encoding, hashes, closure, and backup checks passed. All nine production-tool hashes match r0001, so the existing [39-test Python pass](../../build/audio-wineglass-cast-20261101-python.log) is retained and was **not rerun**. Both targets' [audio and ownership build gates](../../build/audio-wineglass-drop-20261101-assets.log) passed for this revision. Numerical checks do not establish that the user can hear or likes the descent. This remains a one-shot; no new loop test is required.

The stable cue is `essence_ascendance:weapon.cast.release`, once after successful projectile spawn; failed spawns and cooldown-rejected presses remain silent. Unity pitch is still a runtime handoff because the existing 1.6 fallback pitch requires review before selecting authored audio. No production-tool or gameplay/runtime change, catalog selection, or promotion occurred. No-device-tailoring and immutable SQLite inspection guidance are unchanged. Creative and final listening approval remain pending.

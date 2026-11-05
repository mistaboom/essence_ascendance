# Hollow-air cast refinement — 2026-11-01

## Latest listening decisions

After reviewing [the three fresh cast concepts and softer impacts](CAST-EXPLORATION-20261101.md), the user accepted both `magic_impact_soft_v5` and `magic_impact_soft_b_v5`: "both are great." These two unchanged takes bring the creative-acceptance total to 50.

The user selected **cast B, the hollow air launch**, with a small refinement: slightly less hissing air rush and a slightly higher tonal component. `cast_air_v5` r0001 therefore records `creativeReview.status: revise_requested`, with the selected direction explained in its feedback. The user did not explicitly reject casts A or C; those unselected alternatives retain their existing pending review state. They are not silently rejected or added to a random-variation pool.

Creative acceptance remains separate from final production review. No decision here establishes native mix, loop, or rights approval, selects a runtime catalog variant, or promotes a take. The refined take remains pending creative and final review; it does not inherit approval from the selected direction. All earlier audio, projects, and review history remain preserved.

## One targeted revision

The data-only [cast-air-refinement-20261101 batch](../../art/source/audio/batches/cast-air-refinement-20261101.json) is authored and finalized. It contains one new immutable take, **`cast_air_v5` r0002**, under the same stable asset identity. The [production receipt](../../build/audio-cast-air-refinement-20261101.json) records its five stems, genuine editable Audacity project, matching closed backup, PCM24 master, validated Ogg, hashes, and validation evidence. All 71 prior project/export pairs were verified unchanged.

The five layers, preset library, seeds, envelope shapes, timing, and duration are retained. Only four synthesis parameters change: the frequencies of layers 02 (hollow resonance) and 05 (dry rim) rise two semitones; layer 03 (pressure air) gains fall 1 dB; layer 04 (release breath) gains fall 3 dB. An independent audit confirmed those four intended changes and deterministic output. The existing r0001 recipe, master, stems, project, and preview remain historical evidence of what the user heard.

The intended cue remains `essence_ascendance:weapon.cast.release`: one accepted projectile launch, separate from impact. Both approved soft impacts and the other 48 creatively accepted takes remain unchanged. The user's earlier instruction against tailoring sounds to laptop speakers or Moonlight still applies; this refinement responds to their creative feedback, not their playback setup.

The listening page is Auxiliary `Audio/previews/cast-air-refinement-20261101/review.html`, with seven validated local links, four audio players, and 13 unique element IDs. Its `original-then-refined.wav` comparison plays r0001 then r0002 three times, retaining the source levels. The earlier exploration page was regenerated with the current review statuses; its 27 links, 12 players, and 20 unique IDs were checked. All targets resolve. Browser playback and visual review were not performed. Describing the changes is not a subjective listening verdict.

## Shared production and review

The take uses the existing synthesis, Audacity organization, export, validation, and revision workflow. Common infrastructure remains unchanged; only the sound's recipe parameters vary. The source/resolved recipe, seed and tool provenance, useful named tracks/stems, export settings, cue association, measurements, and review state are preserved.

The genuine `.aup3` project passed actual Audacity-render agreement and immutable SQLite integrity checks, with a matching normally closed backup. Its master and five stems are 48 kHz mono PCM24; the candidate is 48 kHz mono Vorbis at quality 5. Actual synthesis, encode/decode, recipe/stem/tool hashes, and decoded repetition/comparison fixtures were checked. This is a one-shot with no new loop requirement.

| Measurement | r0002 result |
| --- | --- |
| Decoded duration | 0.317333 s |
| True peak | −11.75 dBTP |
| Measured padded loudness | −27.95 LUFS |
| RMS | −26.303 dBFS, 0.856 dB lower than r0001 |

The independent layer audit found the tonal share increased from approximately 26.3% to 33.2%, while release-breath share fell from 19.5% to 12.3%. These measurements describe the intended shift in balance; perceived hiss, pitch, comfort, repetition, and in-game balance remain human listening judgments. The refined take has not received creative approval.

The [current asset checks](../../build/audio-cast-air-refinement-20261101-assets.log) passed 52 audio assertions per supported version and both ownership checks. The shared 34-test Python suite was **not rerun** for this parameter-only change: all production-tool hashes match r0001, whose [34-test run](../../build/audio-cast-exploration-20261101-python.log) passed. The actual candidate pipeline and targeted checks above were run for r0002.

The [SQLite inspection guidance](AUTHORING.md) remains unchanged: verify closure first, inspect only closed sidecar-free projects with `mode=ro&immutable=1` and `contextlib.closing`, then recheck closure and hashes. Never delete sidecars or alter historical databases/backups to satisfy review gates.

No production-tool code, gameplay/runtime routing, catalog selection, or promotion changed in this completed pass. The existing unity-pitch cast handoff and one impact cue for accepted terrain/entity contact remain in effect; user listening, final native mix, and release approval remain pending.

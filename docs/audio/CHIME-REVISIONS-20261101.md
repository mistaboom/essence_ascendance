# Chime ceremony and impact revisions — 2026-11-01

## Latest listening decisions

This update follows the user's listening review of [the ten v3 candidates](REVISIONS-20261101.md). The two mechanical menu clacks are accepted and remain unchanged. The user wants the ceremonies rebuilt around the previously accepted channeling wind-chime sound with more variation; the orchestral ceremony direction did not work for them. Casts need more punch and less warble, the heartbeat needs to be audible, impacts should be less pitched and more atonal, and death-defiance needs a larger, more substantial event than a UI accent.

The resulting creative-review disposition is 44 accepted prior takes: the 42 already accepted v2 takes plus `ui_key_clack_v3` and `ui_key_clack_b_v3`. The other eight v3 takes require revision: `ascendance_ceremony_v3`, `awakening_ceremony_v3`, `cast_tonal_v3`, `cast_tonal_b_v3`, `adrenaline_heartbeat_v3`, `magic_impact_v3`, `magic_impact_b_v3`, and `death_defiance_v3`. These decisions belong to the existing manifest's separate `creativeReview` fields, recorded through the shared CLI with the exact user feedback. Prior audio/project bytes and decision history are preserved.

Creative acceptance is not final production approval. Previous final reviews remain pending; it does not establish in-game mix, native-loop, or rights approval, and it does not select or promote a take. All eight new v4 candidates remain pending creative and final review. The previous v3 production receipt remains valid evidence of those files' creation and checks; its then-pending creative status is superseded by this later listening decision.

The user clarified that they were listening on laptop speakers while streaming their desktop through Moonlight, and explicitly requested that the sound not be tailored to that setup. The heartbeat therefore retains a natural full-range balance: low chest body, restrained valve texture, and a modest presence increase, without laptop- or streaming-compensation EQ. A later comparison on headphones or direct playback can help review; no such comparison or audibility result is claimed here.

## Revision batch and listening order

The data-only [chime-revisions-20261101 batch](../../art/source/audio/batches/chime-revisions-20261101.json) is authored and finalized: eight r0001 candidates, eight genuine editable Audacity projects with matching normally closed backups, 47 useful stems, PCM24 masters, and validated Ogg exports. The two ceremonies contribute nine stems each; the six other effects contribute 29. The [production receipt](../../build/audio-chime-revisions-20261101.json) records exact files, hashes, measurements, and validation. All 58 prior project/export hashes were verified unchanged. Descriptions below remain design intent, not a claim that the user has heard or approved the new takes.

| Order | Candidate | Intended change | Stable cue |
| --- | --- | --- | --- |
| 1 | `ascendance_chimes_v4` | An eight-second ceremony built from the accepted wind-chime/channeling character, with evolving notes and greater variation supporting the accomplishment. | `progression.ascend`, full ceremony |
| 2 | `awakening_chimes_v4` | A separately arranged 3.8-second ceremony in the same chime family for natural Awakening. | `progression.ascend`, Awakening |
| 3–4 | `cast_punch_v4`, `cast_punch_b_v4` | A more forceful tonal cast with less wavering pitch; matched variations should retain a clear, quick attack. | `weapon.cast.release` |
| 5 | `adrenaline_heartbeat_v4` | One natural full-range lub-dub heartbeat with stronger presence, preserving the two-part physical gesture and low body. | `vitality.surge` |
| 6–7 | `magic_impact_v4`, `magic_impact_b_v4` | More atonal magical contacts, with less identifiable note/ring and controlled A/B variation. | `weapon.bolt.impact` |
| 8 | `death_defiance_v4` | A 1.4-second original rescue: bright clang, weighty bronze body, brief protective rush, and warm settling tail. | `vitality.rescue` |

Cue IDs use the `essence_ascendance:` namespace. Neither accepted menu-clack take is reauthored. The accepted beam and flight loops are also unchanged. These revisions are one-shots; their relationship to the channeling palette does not create a new runtime loop requirement.

The listening page is Auxiliary `Audio/previews/chime-revisions-20261101/review.html`. It presents the two ceremonies first, then cast A/B, heartbeat, impact A/B, and rescue, with matched comparisons and cast-to-impact context. The page has 19 audio players, 43 checked local links, and 31 unique element IDs. The v3 page was regenerated with updated creative-review badges: 24 players, 54 checked local links, and 37 unique IDs. All referenced targets and IDs passed validation. Browser playback and visual review were not performed. Earlier audio remains preserved.

## Timing and reusable production

The ceremony timing remains grounded in [`AscendanceTierFeedback.play`](../../common/src/main/java/com/mistaboom/essence_ascendance/nexus/AscendanceTierFeedback.java): 160 ticks for full ascendance and 76 ticks for natural Awakening, or 8.0 and 3.8 seconds. [`AscendanceCeremonyRecipe`](../../common/src/main/java/com/mistaboom/essence_ascendance/client/transientfx/AscendanceCeremonyRecipe.java) begins its visual release at normalized progress 0.58, corresponding to 4.64 and 2.204 seconds. The chime arrangements follow those phases and end with the actual animation. The extra camera ease-out does not extend the visual ceremony's duration.

The [ceremony presets](../../art/source/audio/presets/ceremony-chimes-v4.json) reuse the accepted [beam palette's](../../art/source/audio/presets/beam-chimes-v2.json) `suspended_chime` modal partial ratios and strengths. Irregular overlapping note entries, changing voicings, and amplitude curves supply variation on nine editable chime tracks per arrangement. The shorter arrangement is scheduled separately. The other effects share [revision-v4 presets](../../art/source/audio/presets/revision-v4.json). All variation is recipe/preset data using existing shared synthesis and score events; no production-tool code was changed. Common project organization, export, validation, and revision handling remain shared.

All eight `.aup3` files passed SQLite integrity, sample-block, and actual Audacity-render verification, with matching normally closed backups. The workflow preserved source and resolved recipes, seeds, tool versions/hashes, export settings, and stable cue associations. Masters and stems are 48 kHz mono PCM24; candidates use 48 kHz mono Vorbis at quality 5. There are no new loops requiring decoded join tests in this batch. The accepted continuous files remain unchanged.

## Technical measurements and listening limits

| Candidate family | Decoded duration | True peak | Measured LUFS |
| --- | --- | --- | --- |
| Full ascendance chimes | 8.000 s | −11.01 dBTP | −26.58 |
| Awakening chimes | 3.800 s | −11.02 dBTP | −25.72 |
| Cast A/B | 0.297 s each | −8.41 to −8.40 dBTP | −20.75 to −20.35 |
| Heartbeat | 0.402 s | −7.29 dBTP | −22.85 |
| Magic impact A/B | 0.297 s each | −9.35 to −9.13 dBTP | −27.15 to −26.25 |
| Death-defiance rescue | 1.400 s | −6.35 dBTP | −22.07 |

The technical audit passed without clipping. Both ceremonies have no interior silence gaps; their short quiet onset/tail regions belong to their shaped envelopes. Cast variations differ by 0.40 measured LU and 0.31 dB RMS; impact variations differ by 0.90 LU and 0.98 dB RMS. Short-event loudness measurements support comparison but do not establish perceived balance.

The revised heartbeat is 3.26 dB higher in RMS level than v3, with approximately 66.2% of its energy below 200 Hz. This preserves a natural low-bodied sound rather than compensating for the reported playback setup. The impacts have approximately 18–22% of their energy in the 2–5 kHz band. These are numerical descriptions, not judgments about clarity or comfort. Perceived heartbeat audibility, punch, harshness, repetition fatigue, ceremony character, and native mix remain for human listening; no new take has creative approval.

All [34 Python tests](../../build/audio-chime-revisions-20261101-python.log) passed, along with [52 audio assertions per supported version and both ownership checks](../../build/audio-chime-revisions-20261101-assets.log). Technical evidence does not alter pending review or promotion status.

## Runtime handoff remains unchanged

- Select the 8.0-second or 3.8-second ceremony through the shared catalog/presentation route using the existing event's Awakening `parameterA` and lifetime. Start at the actual admitted visual ceremony, including any existing deferral, and preserve unity playback speed/pitch.
- Review the cast's existing 1.6 fallback pitch before selecting these unity-pitch authored files; keep the change in shared routing.
- Keep one `weapon.bolt.impact` cue for accepted terrain and entity contact. A/B are sound variations, not separate hit semantics or duplicate hit confirmations.
- Play the death-defiance event for `vitality.rescue` only; the later `vitality.reform` notice remains deliberately silent.
- Preserve the accepted menu clacks, the other 42 accepted sounds, native reuse, deliberate silence, beam-only behavior, and Focus-install handoffs.

No runtime/gameplay changes, catalog selection, or promotion are part of this authoring pass. Final release review and actual native mix acceptance remain separate from the creative decisions above.

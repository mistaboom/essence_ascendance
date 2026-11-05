# Reusable audio authoring

Current approved set: [integration handoff](INTEGRATION-20261102.md) and [exact selection](../../art/source/audio/handoffs/prompt7-20261101.json). Creation and revisions are complete. The user's 2026-11-02 integration request authorizes the 49 runtime takes through the explicit integration mode below; the two demonstrations remain outside runtime. Creative approval and integration permission do not complete native mix, listening, loop or redistribution review. Use shared `tools/audio/audit.py` to recheck existing source files without synthesizing, re-encoding or changing reviews. Historical authoring commands below remain available for future separately requested work.

## Production batches and reusable palette (2026-11-01)

[WINEGLASS-DROP-20261101.md](WINEGLASS-DROP-20261101.md) records the completed pitch-descent revision. [WINEGLASS-CAST-20261101.md](WINEGLASS-CAST-20261101.md) preserves its original receipt, [CAST-AIR-REFINEMENT-20261101.md](CAST-AIR-REFINEMENT-20261101.md) the hollow-air refinement receipt, [CAST-EXPLORATION-20261101.md](CAST-EXPLORATION-20261101.md) the v5 comparison receipt, [CHIME-REVISIONS-20261101.md](CHIME-REVISIONS-20261101.md) the v4 receipt, [REVISIONS-20261101.md](REVISIONS-20261101.md) the v3 receipt, and [BATCH-20261101.md](BATCH-20261101.md) the previous production receipt and reduced sound plan. The warm-arcane first batch is rejected and preserved as history. The production path uses one-level layer presets via `recipes.py`; timed/modal layers, instrument score events, pitch paths, noise tilt/morph and periodic loops in `synthesis.py`; source/seed/stem/tool provenance and project-render verification in `audio.py`; shared QC/decoded repetition in `quality.py`; data-driven `batch.py` orchestration; and the offline listening buttons in `player.py`. No per-cue authoring script is needed.

**Author in small groups, then close and finalize before continuing.** `batch.py author` now defaults to at most eight new projects per invocation. This is a limit on work created by that call, not a check that Audacity has no older frames open. Inspect the current windows first and close the just-authored owned projects normally before rerunning. Do not increase the limit to accumulate an entire large batch in Audacity.

```powershell
$audioPython = 'tools/audio/.venv/Scripts/python.exe'
$audioAux = 'C:/Users/rcboo/Documents/Essence Ascendance Auxiliary Files'
$audioBatch = 'art/source/audio/batches/wineglass-drop-20261101.json'
& $audioPython tools/audio/batch.py prepare $audioBatch --auxiliary $audioAux
& $audioPython tools/audio/batch.py author $audioBatch --auxiliary $audioAux --limit 8
# Save/close this group's owned projects normally in Audacity's GUI.
& $audioPython tools/audio/batch.py finalize $audioBatch --auxiliary $audioAux --available
# Repeat author -> normal GUI save/close -> finalize --available until complete.
# Final complete-batch verification and derived listening page:
& $audioPython tools/audio/batch.py finalize $audioBatch --auxiliary $audioAux
& $audioPython tools/audio/batch.py report $audioBatch --auxiliary $audioAux
& $audioPython -m unittest discover -s tools/audio -p 'test_*.py' -v
```

`author` preserves existing manifest takes associated with this batch and resumes missing ones by default. `finalize --available` skips items that have no completed manifest take yet; it does not skip unsafe/open projects. Reports require the entire batch. Incomplete reservations remain on disk for diagnosis. A manifest take means candidate creation completed; it does not mean closure, backup, or approval completed.

For a new full revision pass, create a new data-only batch ID with the desired asset list and use the normal bounded resume cycle. To revise one sound, use `audio.py author <asset>`. `--new-revisions` forces new takes even for already completed items, but each call starts at the beginning of its supplied item list; repeatedly calling it with the same large definition and limit will revise the first group again. It is not a resumable full-batch revision cursor. Never use a huge limit as a workaround.

### Recover saved unfinished projects in bounded groups

After an interruption, preserve the saved projects, masters, logs, incomplete reservations, and existing recovery data. Inspect Audacity's state and distinguish this batch's projects from unrelated recovery entries. Do not reopen a project that is already open, discard unrelated entries, edit databases, or remove journal sidecars. Start with an empty owned project and no competing workflow.

`recovery.py` reopens saved manifest candidates through Audacity itself. It checks track names against the recorded organization, renders the actual recovered mix, compares frame count and maximum/RMS error with the preserved master, saves, and records a receipt. It creates an empty frame before `OpenProject2`, because this Audacity installation can open a project yet report `Failed` when the command starts from a populated frame. The helper never repairs SQLite, closes windows, or approves a take.

```powershell
& $audioPython tools/audio/recovery.py $audioBatch --auxiliary $audioAux --start 0 --limit 8
# Save/close only the recovered owned projects normally through the GUI.
& $audioPython tools/audio/batch.py finalize $audioBatch --auxiliary $audioAux --available
# Advance by batch item offset, not by the number of files actually reopened:
& $audioPython tools/audio/recovery.py $audioBatch --auxiliary $audioAux --start 8 --limit 8
# Save/close and finalize again before advancing to the next slice.
```

`--start` is a zero-based position in the batch's `items` array; recovery accepts limits 1–12 and defaults to eight. Missing manifest takes and already closed/backed-up takes are skipped. Inspect any error before continuing: a command may have opened a frame before it reported failure. A reservation with stems but no saved `.aup3` cannot be recovered through this helper; preserve it and author a new immutable revision. Finalization remains the only owner of verified closed-project backups.

Atomic JSON writes retain a temporary file plus atomic replacement. Bounded retries handle transient Windows `PermissionError` without truncating the manifest. An exhausted retry is still a real failure; inspect and resume through the shared owner rather than inventing or overwriting a receipt.

Each source recipe may name a repository-contained `presetLibrary`. Layers name a one-level preset and override explicit parameters. The workflow saves both original recipe/library content and the resolved recipe, and archives exact authoring code under Auxiliary `references/tools/<sha256>/<filename>`. Frozen take QC remains historical; regenerated review reports label their current spectral method. Tool hashes distinguish these analyses.

For one-shots whose tails overlap at the desired audition spacing, use shared `quality.overlapping_preview`. It sums complete decoded sources at exact onsets, preserves the final tail and original levels, and rejects invalid input or sample clipping before writing. It never trims or normalizes; existing isolated repetition is unchanged. Preserve the onset/frame/source-hash metadata with the fixture and validate its rendered levels. This is an offline listening comparison, not verification of actual runtime cadence, voice admission, or in-game mix.

### Instrument scores remain recipe data

A layer may contain an `events` array to schedule 1–256 notes on one named instrument stem. Each event declares `startSeconds` and `lengthSeconds` within the recipe duration and may override the permitted synthesis parameters, such as frequency, gain, envelope, or seed. The layer supplies the shared instrument/preset. Events use the existing synthesis path, sum into that instrument's stem, and receive normalization only with the completed mix. Nested events, out-of-bounds events, and score events in periodic-loop recipes are rejected. Both ceremony arrangements reuse this primitive and the same instrument preset library, yielding 12 useful tracks each instead of a separate track per note. Preserve source/resolved scores and tool hashes with the usual recipe provenance; project organization, export, and validation are unchanged.

### Record creative feedback separately from final approval

Use `creative-review` when the user accepts a sound's character or requests a revision, without claiming they have approved the in-game mix, native looping, or rights. Select the exact asset/revision heard and preserve their actual notes and date:

```powershell
& $audioPython tools/audio/audio.py creative-review <asset> <revision> --auxiliary $audioAux --status accepted --reviewer '<reviewer>' --at '<decision date and timezone>' --notes '<exact feedback>'
# For a requested change, use --status revise_requested with the relevant feedback.
& $audioPython tools/audio/batch.py report $audioBatch --auxiliary $audioAux
```

This writes `creativeReview` on an existing final-pending take; a changed decision retains the earlier entry in `creativeReviewHistory`, and repeating an identical decision is idempotent. The player labels these states "Sound approved" or "Revision requested" while explicitly retaining final release/in-game mix review as pending. New takes have no inherited creative approval. The command does not set listening/mix/loop/license flags, alter final `review.status`, or bypass `review`/`promote`. Already-final reviews remain immutable. Revise through a new immutable take or a new data-only batch; preserve the old candidate and its feedback.

Audacity projects use named tracks at 48 kHz; a rendered project mix is compared with the master before recording a candidate. PCM preferences may add shaped dither to that diagnostic WAV. Both maximum and RMS error are bounded, while preserved masters/stems remain 24-bit. Music import preferences are temporarily set to `No` / `DoNothing` and restored in `finally`; these exact enum values were checked against [Audacity 3.7.9 source](https://github.com/audacity/audacity/blob/Audacity-3.7.9/src/prefs/ImportExportPrefs.cpp). The installed adapter also rejects `GetInfo: Type=Preferences` after a reproduced crash; individual `GetPreference` queries remain supported.

Authorized desktop automation may perform ordinary GUI save/close and recovery for owned projects. Script-pipe Close/Exit remains blocked. The historical user-only GUI instruction below described the earlier workflow, not an additional approval requirement. Never discard unrelated recovery entries, edit historical take databases, submit a crash report or remove sidecars to make finalization pass.

### Keep SQLite inspection from creating sidecars

Inspect a finalized Audacity database only after confirming that its project is normally closed, its recorded hash matches, and no `-wal`, `-shm`, or `-journal` sidecar exists. Use a SQLite URI with **both** `mode=ro&immutable=1`, and explicitly close the connection with `contextlib.closing`:

```python
from contextlib import closing
import sqlite3

with closing(sqlite3.connect(project_path.resolve().as_uri() + '?mode=ro&immutable=1', uri=True)) as connection:
    integrity = connection.execute('PRAGMA integrity_check').fetchone()
```

After inspection, verify closure and absence of sidecars again and confirm that the project hash is unchanged.

The immutable flag is for a verified closed file, never a live project or a database with pending journals. A connection's transaction context alone does not close it. `mode=ro` alone was insufficient in a real 2026-11-01 audit: it created empty WAL files and SHM sidecars on eight already finalized v4 projects, and the next creative-review attempt correctly refused them. Preserve project and sidecar evidence first. Resolve through Audacity's normal open/close lifecycle without editing or saving an unchanged reviewed project, then recheck closure and hashes against its existing backup. Do not manually remove sidecars or replace a historical backup to make a gate pass. [The cast exploration record](CAST-EXPLORATION-20261101.md) tracks that maintenance incident and its resolution evidence.

Runtime fallback extension (2026-10-28): a cue may declare an explicit `fallback: "minecraft:<event>"` while all its authored samples are unavailable. Native-only variants use `{ "event": "minecraft:<event>", "weight": 1 }` instead of `asset`; the two fields are mutually exclusive. These pass through the same generator and descriptor. An explicit fallback never approves, copies or changes a pending custom take. Once approved samples are available, they replace the fallback through the existing resource pipeline. Actual native event existence is checked against each supported version's content-addressed vanilla sound manifest.


The runtime resource route is the contract's existing `AssetPipeline`, not checked-in copies under `common/src/main/resources`. Editable sessions live outside the repository. Selected masters, frozen recipes, exports and receipts enter repository art ownership through final-review promotion or explicitly authorized integration promotion. Nothing in a normal build opens Audacity, invokes Python/FFmpeg, re-encodes a sample, or reads Auxiliary Audio.

## Owners and identities

| Owner | Responsibility |
| --- | --- |
| `tools/audio/synthesis.py` | Recipe validation, envelopes, layered harmonic tones, filtered seeded noise, AM/FM, bounded variation, loop overlap, lossless stems |
| `tools/audio/storage.py` | Contained paths, exclusive workflow leases, immutable revision allocation, atomic JSON writes |
| `tools/audio/audacity.py` | Installed executable/module evidence, bounded named-pipe commands, new-project import/save, closed-project copying and backup |
| `tools/audio/encoding.py` | One pinned Vorbis export path, PCM master conversion, actual codec/decode/channel/rate/duration/level/seam validation |
| `tools/audio/audio.py` | Reusable orchestration, candidate records, review and promotion; CLI does not contain cue-specific generation |
| `tools/audio/batch.py` | Data-defined preparation, bounded/resumable authoring, staged finalization and complete review reports |
| `tools/audio/recovery.py` | Bounded Audacity reopen/render/save verification for saved unfinished manifest takes; no database editing or window closure |
| `tools/audio/player.py` | Offline grouped playback buttons, search, stop-all, exact review state and separate technical/native-reference information |
| `art/audio-manifest.json` | Stable asset ID → current synthesis recipe, family/scope, immutable take history and take review; all auxiliary paths are relative to `Audio/` |
| `art/audio-catalog.json` | Stable asset ID → runtime resource and selected approved revision/paths; cue identity, subtitle key and named policy/recipe/profile references |
| `tools/audio/AudioAssets.java` | Build relationship/receipt/hash/Ogg validation and deterministic generated native resources; called once by `AssetPipeline` |
| Existing language JSON | Subtitle text; no English strings in recipes or generated cue definitions |

An asset ID is shared by its recipe, authoring manifest, catalog, export receipt and take history. Recipe IDs in the runtime catalog describe semantic feedback; synthesis recipes are separate authoring data. Cue availability is derived from approved sample references. Pending variants are filtered; a cue with no approved sample is omitted. No second maintained `sounds.json` or Java cue list exists.

The manifest owns each take's creative/final review and any explicit `integrationPromotion` permission. The catalog's `status` and `revision` select what a build may use; `approved` means selected bytes, not that every listening or release gate passed. A promotion receipt freezes the selected take's review, hashes, encoder and measurements; builds reject disagreement with that take. `candidate.json` in an auxiliary export directory is only an asset/revision pointer back to the manifest, not another review database.

## Storage

Auxiliary root on this workstation: `C:/Users/rcboo/Documents/Essence Ascendance Auxiliary Files`. The CLI appends **`Audio/`**:

| Directory | Contents |
| --- | --- |
| `projects/<family>/<assetId>/rNNNN/` | Editable `.aup3`, command transcript; edited-session raw PCM render when applicable |
| `masters/<family>/<assetId>/rNNNN/` | 24-bit 48 kHz mono `master.wav`, independently editable layer stems, frozen `recipe.json` |
| `previews/<batch>/` | Listening OGGs named with asset and revision |
| `exports/<family>/<assetId>/rNNNN/` | Validated OGG awaiting review/promotion, stable manifest pointer |
| `references/` | Reference material; never an automatic build input |
| `backups/<family>/<assetId>/rNNNN/` | Byte-identical copies of safely closed projects |

After promotion, repository `art/source/audio/{masters,approved,receipts}/<assetId>/rNNNN.*` and `recipes/history/<assetId>/rNNNN.json` preserve prior approvals. Current recipes remain under `art/source/audio/recipes/`. Large editable sessions/stems remain auxiliary; a promoted master plus frozen recipe is repository-contained creative provenance. Generated resources belong only under each target's `build/generated-resources/assets/`, including `assets/essence_ascendance/{sounds/,sounds.json,audio/catalog.json}`.

## Setup and GUI preparation

Run from the repository root in PowerShell:

```powershell
python -m venv tools/audio/.venv
tools/audio/.venv/Scripts/python.exe -m pip install -r tools/audio/requirements.txt
$audioPython = 'tools/audio/.venv/Scripts/python.exe'
$audioAux = 'C:/Users/rcboo/Documents/Essence Ascendance Auxiliary Files'
```

Audacity 3.7.9 is installed at `C:/Program Files/Audacity/Audacity.exe`. In Audacity press **Ctrl+P → Modules**, scroll to **mod-script-pipe**, choose **Enabled**, click **OK**, and restart only after saving existing work. Open an empty project. Then:

```powershell
& $audioPython tools/audio/audio.py doctor
```

This reports executable/module hashes and installed version, queries actual `SaveProject2` support and current tracks, and reports the bundled encoder. It never launches/restarts the DAW or sends mouse/keyboard input. Do not change the active Audacity project during an authoring operation: the scripting interface targets its current project globally. Separate workflow leases prevent competing workflow invocations, not human window switching.

API reference: Audacity's [scripting setup](https://manual.audacityteam.org/man/scripting.html) and [command reference](https://manual.audacityteam.org/man/scripting_reference.html). Installed-command Help and local execution, rather than assumptions about another version, supply the tested compatibility evidence below.

**Installed-version limitation:** scripting `Close` crashed Audacity 3.7.9 during earlier testing after successful saves. The adapter rejects `Close`, `CloseProject` and `Exit` before writing to the pipe. It creates/imports/saves a new project and leaves it open. Close owned projects through normal GUI actions; authorized desktop automation can perform those actions. `finalize` verifies closure before backup. Recovery files are preserved and sidecars are never deleted to force acceptance. The 2026-11-01 large-window interruption additionally motivates the eight-project authoring limit; see [the batch incident record](BATCH-20261101.md).

## Author and revise

```powershell
& $audioPython tools/audio/audio.py author process_complete_a --auxiliary $audioAux --batch my-review
& $audioPython tools/audio/audio.py author resonance_body_a --auxiliary $audioAux --batch my-review
```

Both commands use the same generation/project/export path. The first recipe mixes three transient layers with envelopes and controlled pitch variation. The second mixes resonance and modulated filtered noise with a loop overlap. Each prints its newly allocated revision and preview path. Existing revisions are never overwritten. An interrupted reservation remains on disk; the next authoring operation allocates a new number.

Save-only authoring leaves the new projects open. In each named project press **Ctrl+W**, saving if prompted. If journal files remain, exit Audacity normally after saving other work. Then use the revision numbers printed by `author`:

```powershell
& $audioPython tools/audio/audio.py finalize process_complete_a r0002 --auxiliary $audioAux
& $audioPython tools/audio/audio.py finalize resonance_body_a r0003 --auxiliary $audioAux
```

`finalize` refuses `.aup3-wal`, `-shm` and `-journal` sidecars, requires exclusive file access, copies under an exclusive read handle, checks SQLite integrity and compares hashes. It does not repair databases or infer that a process disappearing means a safe close. Repeating finalization validates the existing association without replacing its backup. A project changed after finalization cannot be promoted.

For an experiment without Audacity, add `--without-project` to `author`. It follows the same synthesis/export/validation path but cannot be promoted without an editable project.

To revise manually, make a working copy of a **closed** project, edit that copy in Audacity, save and close it normally. Capture it through the common workflow, with its path relative to Auxiliary Audio:

```powershell
& $audioPython tools/audio/audio.py author process_complete_a --auxiliary $audioAux --batch edited-review --from-project 'projects/process/process_complete_a/working/edited.aup3'
```

The adapter first checks closure and copies the working project into a new immutable revision. It opens that copy, exports all tracks as PCM and saves. The export owner retains the raw Audacity WAV and makes a standard 24-bit 48 kHz mono master without changing global Audacity export preferences. Close and finalize the new revision normally. Manual edits retain their parent-project association and `audacity_edit` source method; their frozen synthesis recipe describes ancestry, not a claim that DSP alone reproduces manual edits. This capture branch still needs an end-to-end manual-edit session test; see PASSDOWN.

After an interrupted save where no manifest take was completed, first recover/save/close the specific project through Audacity. `finalize <assetId> <revision> --batch <batch> --auxiliary $audioAux` can resume its saved master/project without regenerating DSP. It deliberately stops if an unrecorded export already exists; inspect that failed revision before choosing a fresh revision. Never remove database sidecars to force acceptance.

## Listen, review, promote

For development integration explicitly requested before final native review, use
the same `promote` command with the frozen **art-relative** selection and the actual
user instruction. For the current selection, the shared operation is:

```powershell
$audioSelection = 'source/audio/handoffs/prompt7-20261101.json'
$audioSet = Get-Content -Raw "art/$audioSelection" | ConvertFrom-Json
foreach ($audioItem in $audioSet.items | Where-Object purpose -eq 'runtime') {
    & $audioPython tools/audio/audio.py promote $audioItem.asset $audioItem.revision --auxiliary $audioAux --integration-selection $audioSelection --integration-authority 'Integrate approved audio assets into Essence Ascendance. Use the shared promotion workflow for approved candidates. Preserve source revisions and unapproved candidates.'
    if ($LASTEXITCODE -ne 0) { throw "Promotion failed: $($audioItem.asset)" }
}
```

This mode requires creative acceptance, pending final review, a closed editable
project/backup, and an exact runtime selection/export hash. It rejects demonstrations
and preserves the original bytes. The manifest records `integrationPromotion`; the
immutable `<revision>.integration.json` receipt copies that authorization and links
the project, backup, source master, export, frozen recipe, inputs and stems by
relative auxiliary paths and content hashes. Final review stays pending. Do not
invent listening, mix, loop, license or attribution findings to permit integration.

After actual final review, ordinary `promote` selects a separate `<revision>.json`
receipt and leaves the integration receipt and sound bytes intact. Repeat either
promotion mode is idempotent while its recorded review/authorization remains current.

Audition previews, including repeated loop seams, onset/tail clicks, useful level, and coexistence with native Minecraft sounds. Numeric checks cannot approve creative quality, loop perception or mix. Confirm redistribution rights and record attribution. Example **only after actually doing the review**:

```powershell
& $audioPython tools/audio/audio.py review process_complete_a r0002 --auxiliary $audioAux --accept --reviewer 'Your name' --notes 'Describe the listening and native mix review' --license 'Confirmed redistribution license' --attribution 'Actual attribution' --listened --mix-reviewed
# Looping assets additionally require --loop-reviewed.
& $audioPython tools/audio/audio.py promote process_complete_a r0002 --auxiliary $audioAux
```

Use `--reject` instead of `--accept` to record a rejected take. Reviews are immutable: create another revision to revise an accepted/rejected take. Promotion rechecks bytes, measurements, closed project/backup and review; copies to immutable repository paths; writes a matching receipt; and changes the catalog last. Partial copy failures cannot make an incomplete take available to builds. Prior approved revisions remain recoverable. Repeat promotion is idempotent if all bytes agree.

The encoder uses `libvorbis`, quality 5, 48 kHz mono, stripped metadata and bit-exact Ogg serial handling. Its complete options, executable SHA-256 and version are recorded. Dependency versions are pinned in `requirements.txt`; use the recorded encoder binary/version when reproducing historical exports. Other operating systems' bundled FFmpeg binaries can differ and require fresh validation/review.

Validation fully decodes with FFmpeg, checks actual Ogg/Vorbis identity, mono for world samples, 48 kHz, 0.02–30 seconds, expected decoded duration, finite non-silent samples, sample peak/RMS, oversampled true peak at or below -1 dBTP, integrated LUFS, and a numerical loop-seam bound when applicable. Short transient loudness is measured rather than normalized to a universal target; clips shorter than 400 ms are zero-padded only for loudness measurement and labeled `zero_padded_to_400ms` in the receipt. Their exported audio is unchanged. Standalone `validate <path>` applies world defaults. Authoring currently emits mono for every cue; stereo UI authoring is not implemented.

## Build and verification

Run Gradle sequentially with Java 25; keep editor-active source at 1.21.1:

```powershell
$env:JAVA_HOME = 'C:/Users/rcboo/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2'
.\gradlew.bat :common:1.21.1:audioAssetInvariants :common:26.1.2:audioAssetInvariants
.\gradlew.bat :common:1.21.1:assetPipelineInvariants :common:26.1.2:assetPipelineInvariants
.\gradlew.bat :assetJarInvariants1211Fabric :assetJarInvariants1211NeoForge :assetJarInvariants2612Fabric :assetJarInvariants2612NeoForge
& $audioPython -m unittest discover -s tools/audio -p test_audio.py -v
```

The Java audio suite runs in normal `check`, with repository-contained synthetic fixtures whose simulated acceptance is expressly test-only. It exercises the same production importer and publication boundary. Build validation checks catalog/manifest/recipe identities, policy/cue/profile/translation references, review/receipt agreement, SHA-256 associations, true-peak/channel/rate/duration evidence and actual Vorbis headers/Ogg page framing/sequence/CRC/EOS. Full decode and loudness analysis are authoring-time checks bound to exact approved bytes; builds do not rerun the decoder. Failed validation precedes publication. Runtime archives exclude authoring formats and contain only generated runtime descriptors plus available approved OGGs.

The generated descriptor now feeds the shared runtime registration, semantic routing,
continuous playback and caption implementation described in
[the integration handoff](INTEGRATION-20261102.md). Authoring/importer checks establish
source and resource integrity; they do not establish packaged-client behavior,
multiplayer, native mute/reload acceptance or human listening. Keep those separate
in [the validation ledger](VALIDATION.md).

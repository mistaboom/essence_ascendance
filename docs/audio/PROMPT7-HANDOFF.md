# Prompt 7 — approved audio integration handoff

**Historical preparation record.** The exact source selection below remains pinned.
For the completed development integration and current review boundary, use
[INTEGRATION-20261102.md](INTEGRATION-20261102.md) and [VALIDATION.md](VALIDATION.md).
The later explicit integration authorization preserves pending final review rather
than filling in unperformed listening/mix flags.

Prepared 2026-11-01, America/Anchorage. Sound creation and revisions are complete. The user approved the final falling-wineglass cast with “perfect” and requested preparation for development integration. Do not regenerate, re-encode, retune, normalize, time-stretch, or replace these approved files.

## Start here

- Repository: `C:/Users/rcboo/Dev/Minecraft/essence_ascendance`.
- Auxiliary root: `C:/Users/rcboo/Documents/Essence Ascendance Auxiliary Files`; authoring commands append `Audio/`.
- [Exact selection](../../art/source/audio/handoffs/prompt7-20261101.json): 51 approved asset/revision pairs and expected export hashes. This freezes the selected takes; never substitute an asset's latest revision automatically.
- [File audit](PROMPT7-AUDIO-AUDIT.json): exact auxiliary export/project/backup/master/stem/input paths, hashes, measured levels/durations, loop checks, cue associations and approval records for every selected take. Paths in this report identify their root explicitly.
- [Inventory](INVENTORY.md): current grouped cue mapping, native reuse, deliberate silence, and integration gaps. [Contract](CONTRACT.md) and [authoring workflow](AUTHORING.md) remain governing references.

The selection contains **49 runtime-intended samples** (30 one-shots, 19 loops) and **two approved beam-build demonstrations**. All 51 have genuine editable Audacity projects, matching normally closed backups, PCM24 masters, frozen generation inputs/settings/seeds/tool versions and validated Vorbis exports, with **233 useful source stems**. No approved authoring material is missing and no recovery or relocation was needed. The two zero-pylon beam projects intentionally contain one original note layer each; these are not flattened-export substitutes.

Every selection is `r0001` except **`intercept_tok_b_v2 r0002`** and **`cast_wineglass r0002`**. Do not select an earlier cast, ceremony, heartbeat, menu press, impact or rescue merely because its asset remains in the catalog. Unselected and rejected history is preserved.

`beam_player_build_v2 r0001` and `beam_infuser_build_v2 r0001` are 27.5-second listening demonstrations. Exclude them from runtime promotion/selection. The 18 actual beam bodies are the 12-second `beam_{player,infuser}_p{0..8}_v2` files; powered flight is the separate eight-second loop.

## Storage and approval boundary

The manifest is the sole review/source owner: [art/audio-manifest.json](../../art/audio-manifest.json). The selection and audit are derived integration records, not a second editable approval database. All 51 selected takes have `creativeReview.status=accepted`. `cast_wineglass r0002` now records the final approval. Existing frozen recipe/provenance text describes its authoring-time state and must not be rewritten to change historical approval wording.

Approved sound files currently belong in Auxiliary `Audio/exports/<family>/<asset>/<revision>/`; projects, masters, stems, presets, inputs and archived tools remain in their manifest-owned auxiliary locations. These are the correct pre-promotion locations, not misplaced files. The repository already owns current recipes, reusable presets, take history/approval metadata and the exact integration selection. No approved bytes were moved or changed during this audit.

Final `review.status` remains `pending`, and catalog assets remain `authoring_only`, because native mix/loop acceptance has not been performed. This is integration-stage validation, not an outstanding sound-design revision or missing source. The existing `review` command requires truthful native mix and loop flags; do not fill them in merely to satisfy promotion. Original deterministic synthesis provenance is preserved; record the project's actual redistribution/attribution decision with final review.

After actual final review, shared `promote` copies the existing bytes to these immutable paths, relative to `art/`:

| Material | Repository destination |
| --- | --- |
| Ogg export | `source/audio/approved/<asset>/<revision>.ogg` |
| Lossless master | `source/audio/masters/<asset>/<revision>.wav` |
| Frozen resolved recipe | `source/audio/recipes/history/<asset>/<revision>.json` |
| Review/hash/export receipt | `source/audio/receipts/<asset>/<revision>.json` |

Audacity projects and useful source layers stay in Auxiliary. Runtime/CI must consume the promoted repository files through `AssetPipeline`; never make builds read Auxiliary, invoke authoring tools, or add hand-maintained `sounds.json`/duplicate resource copies.

## Required development integration

1. **Beam behavior:** replace generic processing/dissolution and independent Focus hums with actual Crucible-to-player and Crucible-to-Infuser transfers. Use positive transfer state, not requested demand. Select destination and authoritative active-pylon count, capped at eight. Zero pylons means one note. The files already contain note breathing and twinkles. Do not randomize the pylon states or play build demonstrations as loops. Use the shared continuous lifecycle for transitions and a consistent Crucible/destination source identity to prevent duplicate machine/private-channel playback. Preserve range, bounded voices, pause, mute, reload, stop and cleanup behavior; no added start/stop accents.
2. **Focus installation:** `focus_install_v2` intentionally has no existing `cueIds`; its proposed cue is `essence_ascendance:machine.focus.install`. Add a confirmed-change occurrence for actual authorized hand/menu installation or replacement. `CONSUME` can also mean the Focus was already identical, so it alone is not success evidence. Never emit on load/observer refresh or reuse the continuous `machine.focus.body` identity.
3. **Ceremonies:** `ascendance_chimes_v4` is 8.0 seconds; `awakening_chimes_v4` is 3.8 seconds. Both associate with the current `progression.ascend` semantic route but require deterministic contextual selection. Existing feedback identifies Awakening with `parameterA=1`/76 ticks, full ascendance with `0`/160 ticks. Align to actual ceremony start, including deferred starts. Do not randomize or stretch them; the camera's extra ease-out is not sound duration.
4. **Cast, impacts and rescue:** `cast_wineglass r0002` is the sole approved cast, with the preserved 0.8-second master and falling ring. Retain successful-projectile-spawn-only release. Review unity authored pitch rather than inheriting fallback pitch 1.6. The two soft impacts are variations of one accepted terrain/entity collision cue. `death_defiance_v4` belongs only to `vitality.rescue`; spirit reform is silent. The older native arrow-impact and full-totem references are superseded.
5. **Variations and mix:** the paired menu clacks, ignition cracks, interception toks and soft impacts are controlled variants. Follow existing shared variant/admission infrastructure. Review gain and pitch by role; especially do not inherit denial fallback pitch 0.8 automatically. Keep everyday feedback restrained and critical rescue/readiness feedback legible. Do not tailor the assets to one playback device.
6. **Approved silence/native reuse:** silence generic processing completion, channel start/stop, projectile drag-entry, extra guard-return/defensive retaliation, routine status protection, vitality purge/reform and utility transfer/seal/release. Preserve their gameplay and visuals. Retain suitable vanilla melee, generic explosion, shield impact and ordinary Minecraft interaction sounds without double playback. Current Glaive contact's trident-ground fallback needs review against ordinary melee intent. Do not package cached vanilla references or repurpose a strongly associated sound such as the creeper cue.
7. Update `COVERAGE.json`, catalog recipes/profiles, localized captions and tests with the actual runtime changes. Existing broad integration and historical native tests are not evidence that these newly approved samples are integrated. Use the contract/validation matrix for in-game mix, repeat fatigue, dense sources, pylon/destination transitions, captions, resource reload, pause/mute, lifecycle and supported targets. Keep gameplay timing, economics, transactions and authority unchanged.

## Commands

Run from the repository root. Recheck the exact selection before integration; this command only reads audio/source files and writes its derived report:

```powershell
$audioPython = 'tools/audio/.venv/Scripts/python.exe'
$audioAux = 'C:/Users/rcboo/Documents/Essence Ascendance Auxiliary Files'
$audioSelection = 'art/source/audio/handoffs/prompt7-20261101.json'
& $audioPython tools/audio/audit.py --selection $audioSelection --auxiliary $audioAux --output docs/audio/PROMPT7-AUDIO-AUDIT.json
if ($LASTEXITCODE -ne 0) { throw 'Approved audio audit failed' }
```

Use the audit's existing files for integration listening. Source generation/export commands are unnecessary. After actually completing native mix/listening and any applicable loop review, record each runtime take's findings with the existing command; supply the actual reviewer, findings and rights decision:

```powershell
& $audioPython tools/audio/audio.py review <asset> <revision> --auxiliary $audioAux --accept --reviewer '<actual reviewer>' --notes '<actual listening and native mix findings>' --license '<confirmed redistribution rights>' --attribution '<actual attribution>' --listened --mix-reviewed
# Add --loop-reviewed for a loop only after native loop review.
& $audioPython tools/audio/audio.py promote <asset> <revision> --auxiliary $audioAux
```

Do not run placeholder commands unchanged. For a completed final-review set, this exact-selection loop promotes runtime items only, refuses unfinished reviews, and uses the existing byte-preserving workflow:

```powershell
$audioSet = Get-Content -Raw $audioSelection | ConvertFrom-Json
$audioManifest = Get-Content -Raw art/audio-manifest.json | ConvertFrom-Json
foreach ($audioItem in $audioSet.items | Where-Object purpose -eq 'runtime') {
    $audioEntry = $audioManifest.assets.PSObject.Properties[$audioItem.asset].Value
    $audioTake = @($audioEntry.revisions | Where-Object id -eq $audioItem.revision)
    if ($audioTake.Count -ne 1 -or $audioTake[0].review.status -ne 'accepted') {
        throw "Final review incomplete: $($audioItem.asset) $($audioItem.revision)"
    }
    & $audioPython tools/audio/audio.py promote $audioItem.asset $audioItem.revision --auxiliary $audioAux
    if ($LASTEXITCODE -ne 0) { throw "Promotion failed: $($audioItem.asset)" }
}
```

Promotion does not wire variants, semantic ceremony selection, beam source routing or silence decisions automatically. Complete those integration changes through the existing shared runtime. Run the targeted data/resource gates after catalog/promotion changes, followed by the contract's runtime and release checks appropriate to the integration:

```powershell
$env:JAVA_HOME = 'C:/Users/rcboo/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2'
.\gradlew.bat :common:1.21.1:audioAssetInvariants :common:26.1.2:audioAssetInvariants :common:1.21.1:assetOwnershipInvariants :common:26.1.2:assetOwnershipInvariants --console=plain
& $audioPython -m unittest discover -s tools/audio -p 'test_*.py' -v
```

Never open closed `.aup3` files for SQLite inspection using only `mode=ro`. The shared audit uses exclusive closure checks, `mode=ro&immutable=1`, explicit connection closure and before/after hashes. Preserve old projects, failed reservations and recovery entries; never delete sidecars to force a check to pass.

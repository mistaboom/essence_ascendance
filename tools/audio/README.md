# Audio tools

For the integrated approved sound set, start with [the current integration handoff](../../docs/audio/INTEGRATION-20261102.md). `audit.py --selection <selection.json> --auxiliary <root> --output <report.json>` pins exact revisions and checks existing audio/source material without generating, changing or promoting it. The derived report does not own approval. Demonstration takes cannot be relabeled as runtime selections.

Use [the authoring guide](../../docs/audio/AUTHORING.md) for setup, commands and ownership. Run `audio.py --help` for the shared CLI. New sounds supply JSON recipes; do not add per-sound generation or export scripts.

Python/FFmpeg are authoring-only dependencies. The Java importer and offline tests are compiled through the existing Gradle asset pipeline. Files in `fixtures/` have simulated test review only and must never be promoted or registered as production content.

`promote` normally requires accepted final listening/mix/loop review. When the user
explicitly requests development integration of creatively approved takes, use the
same byte-preserving command with `--integration-selection` (a frozen path relative
to `art/`) and `--integration-authority` (the actual user instruction). This mode
requires an exact runtime selection/export hash, creative acceptance, and pending
final review; it rejects demonstration-only takes. It records `integrationPromotion`
in the manifest and an immutable `<revision>.integration.json` receipt. Final review
stays pending: this permission is not listening, native-mix, loop or redistribution
approval. Receipts link the export, master and frozen recipe to the auxiliary editable
project, backup and generation inputs by relative paths and content hashes.

After real final review, run ordinary `promote` again. It preserves the integration
receipt, writes the separate `<revision>.json` final receipt, and selects that receipt
without altering the approved audio bytes. Never manufacture review flags to satisfy
a build. Production archives contain only selected Oggs and runtime descriptors;
authoring receipts, masters, projects and tools remain outside those archives.

Production batches are JSON under `art/source/audio/batches/`; `batch.py` offers `prepare`, `author`, `finalize` and `report`. `recipes.py` freezes reusable layer presets, `quality.py` creates decoded-loop/repetition checks, and `player.py` creates grouped offline playback buttons. See [the current playful/chime batch](../../docs/audio/BATCH-20261101.md) for the approved direction and runtime handoff. The warm-arcane batch is rejected history. Human review remains owned by the manifest; neither a batch report nor a passing test approves audio.

Keep the Audacity window count bounded. Run from the repository root:

```powershell
$audioPython = 'tools/audio/.venv/Scripts/python.exe'
$audioAux = 'C:/Users/rcboo/Documents/Essence Ascendance Auxiliary Files'
$audioBatch = 'art/source/audio/batches/playful-chimes-20261101.json'
& $audioPython tools/audio/batch.py prepare $audioBatch --auxiliary $audioAux
& $audioPython tools/audio/batch.py author $audioBatch --auxiliary $audioAux --limit 8
# Save/close this group's owned projects normally in the GUI before continuing.
& $audioPython tools/audio/batch.py finalize $audioBatch --auxiliary $audioAux --available
# Repeat that author/close/finalize cycle until the batch is complete, then:
& $audioPython tools/audio/batch.py finalize $audioBatch --auxiliary $audioAux
& $audioPython tools/audio/batch.py report $audioBatch --auxiliary $audioAux
```

`author` defaults to eight new projects and resumes missing manifest takes. It does not close existing windows. `--available` skips not-yet-created takes during staged finalization; reports require the complete batch. Use a new batch ID for a resumable full revision pass. Repeating `--new-revisions` against the same limited batch starts from its first items again.

For saved unfinished candidates after interruption, use `recovery.py <batch.json> --auxiliary <root> --start 0 --limit 8`, then close the reopened owned projects normally and run `finalize --available` before advancing the zero-based item offset. The helper checks layer names and the actual Audacity-rendered mix before saving; it never edits databases or deletes recovery data. Inspect existing windows first, do not reopen already open projects, and preserve unrelated recovery entries. Detailed recovery examples and limits are in [AUTHORING.md](../../docs/audio/AUTHORING.md).

## Exact packaged acceptance

`packaged_acceptance.py` is one shared Windows launcher for all four targets. It
reads the exact production-provider manifest, installed runtime caches and the
existing disposable packaged-server fixtures. It copies only the catalog-pinned
Architectury/Fabric API dependencies, the unchanged production JAR and an external
test-only fixture. It never reads account tokens or modifies Prism instances.
Every writable game, library installation and copied world stays under
`build/audio-packaged-acceptance/<fresh-label>/`. Missing local prerequisites fail
explicitly. This is optional acceptance tooling, never a production dependency.

Build the external fixture for one exact target per sequential Gradle invocation:

```powershell
.\gradlew.bat -I gradle/audio-native-hooks.init.gradle :fabric:26.1.2:audioPackagedFixtureJar --console=plain
python tools/audio/packaged_acceptance.py --target 2612Fabric --label new-socket-run --fixture fabric/versions/26.1.2/build/native-fixtures/audio-native-fixture.jar --multiplayer
python tools/audio/packaged_acceptance.py --target 2612Fabric --label new-world-run --fixture fabric/versions/26.1.2/build/native-fixtures/audio-native-fixture.jar --world --run
python tools/audio/packaged_acceptance.py --target 2612Fabric --label new-resource-run --fixture fabric/versions/26.1.2/build/native-fixtures/audio-native-fixture.jar --world --resource-recovery --run
```

Substitute the target's loader/version and ID for each supported pair. Only Fabric
1.21.1 uses `audio-packaged-fixture.jar` (intermediary names); both NeoForge versions
and Fabric 26.1.2 use `audio-native-fixture.jar`. Production mod classes are excluded
from each fixture. `--archive-copy <jar>` accepts a previously staged exact archive
only when its SHA-256 still matches the selected provider manifest, retaining the
original provider path in the receipt. Stage exact bytes before cleaning outputs.

The socket suite launches a dedicated loopback server and two rendered clients
with distinct offline UUIDs. It checks public/private recipients, distance,
dimension, simultaneous actors, silence, native `/playsound`, and disconnect.
It then places and binds a real Crucible, funds its actual reservoir, and claims
an authorized player channel through the production scheduler. Per-type reservoir
debits must exactly equal personal grants. A distant second client approaches the
already-active source, followed by stop, restart and physical block removal. Both
clients must own exactly one active native body when appropriate, with no repeated
start on unchanged transfer updates. This scene does not inject client snapshots.
An external test mixin also observes the actual normal HUD frame order without
calling either draw method: native text must be drawn before supplemental-caption
eligibility is evaluated. It records both draw orders, waits for a screenless real
frame, and disables focus pausing in the disposable fixture. This is distinct from
the controlled world matrix.
Listener callback counts and native device initialization are separate evidence
from perceived audio. The world suite runs the existing controlled matrix against
the packaged mod: settings, native caption draws, source replacement, reload and
device restart. Controlled observer/flight state does not establish paid transfer
or flight gameplay. Device initialization is required; the same native beam handle
must remain active for at least 13.5 seconds, beyond one twelve-second sample
period. This checks native continuity without approving perceived seams.
Both suites leave human listening, HUD layout, true socket late joins/reconnects,
paid Infuser transfers, pylon transitions and simultaneous machine transactions
explicitly separate. The paid Crucible case covers a previously connected distant
listener approaching an active source. It does not assert chunk absence or create
a new socket connection.

Each successful run preserves `receipt.json`, before/after input hashes, exact
launch arguments, an archival copy of the launcher source and separate process
logs. The opt-in resource run also requires its separate recovery PASS marker;
it uses disposable resource-pack overrides and leaves production assets untouched.
No successful exit or stale completion
file alone establishes acceptance. Synthetic performance output measures the
shared controller inside a client process and excludes native mixing, actual
machine discovery and packet traffic. Run launcher regressions with
`python -m unittest discover -s tools/audio -p test_packaged_acceptance.py`.

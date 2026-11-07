# Modpack configuration presets

This directory owns the editable preset sources, versioned downloads and catalog
metadata used by the [wiki downloads page](https://github.com/mistaboom/essence_ascendance/wiki/Config-Presets).
Each pack has an independent ID and each preset has an immutable revision. Adding
ATM10, ATM11 or another pack uses the same layout and builder; it does not need a
new publishing script or a change to the existing downloads.

## Layout

```text
presets/
  catalog.json
  <pack-id>/
    <pack-version>-r1/
      README.txt
      LICENSE.txt
      files/
        config/...
        datapacks/<standalone-pack>/pack.mcmeta
        datapacks/<standalone-pack>/data/...
  downloads/
    <pack-id>-<pack-version>-r1.zip
    SHA256SUMS.txt
```

Only the inputs below `files/`, the preset's README and its license enter the ZIP.
An additional `SHA256SUMS.txt` inside each ZIP covers those files. Downloads contain
editable TOML settings and additive datapack JSON, not mod jars, generated balance
profiles, reports, worlds or player records. Keep each companion datapack in its
own directory so updating a modpack's managed datapack does not replace it.

## Add a pack or revision

1. Choose a stable lowercase pack ID, such as `atm10` or `atm11`. Create a source
   directory with a distinct revision, such as `atm10/2.0.0-r1`. These are examples,
   not published presets or promises of support for those versions.
2. Copy only the reviewed editable inputs into its `files/` tree. A `config/`
   payload may contain TOML files; a `datapacks/` payload needs its own `pack.mcmeta`
   and JSON resources below `data/`. Review the files for credentials, private
   network addresses and local paths. The builder catches some accidental local
   provenance; it is not a complete secret scanner or a Minecraft schema validator.
3. Write `README.txt` using Markdown-compatible headings and prose. It is bundled
   unchanged and also appears on the detail page. Explain the intended difficulty,
   play style, prerequisites, exact installation paths, required companion packs,
   rebuild/validation commands, testing evidence, remaining playtest limits and
   revision history. Add the applicable redistribution notice as `LICENSE.txt`.
   The current project-owned presets use the repository's MIT license.
4. Add an entry to the `entries` array in `catalog.json`:

   ```json
   {
     "id": "example-pack",
     "revision": "1.0.0-r1",
     "pack_name": "Example Pack",
     "pack_version": "1.0.0",
     "minecraft": "26.1.2",
     "loader": "NeoForge",
     "config_format": "1.0.0-beta.15",
     "status": "Playtest",
     "tested_on": "2026-11-07",
     "summary": "The intended progression and power target for this preset.",
     "compatibility_notes": [
       "State the actual tested modpack/build and any additional prerequisites."
     ],
     "source": "example-pack/1.0.0-r1",
     "page": "Preset-Example-Pack-1-0-0-r1"
   }
   ```

   Keep the root `schema` at `1`. `source` must equal `<id>/<revision>`; each
   ID/revision pair, archive filename and wiki page must be unique. Reuse a pack
   ID for later pack versions and tuning revisions. `config_format` records the
   **Essence Ascendance mod version the configuration was tested with**, not the
   internal numeric TOML schema. The wiki labels it “Configuration tested with.”
   Metadata strings are plain text; put formatted instructions in `README.txt`.
   `tested_on` is the date of the recorded testing, not a generated build timestamp.
5. With Python 3.11 or newer, run from the repository root:

   ```powershell
   python tools/presets/build.py --write
   python -m unittest discover -s tools/presets -p 'test_*.py'
   python tools/presets/build.py --check
   ```

The builder creates the ZIP, aggregate checksums, `docs/wiki/Config-Presets.md`
and the entry's detail page. The catalog shows the most recently tested revision
for each pack first, with older revisions in a separate table. A pack without a
published preset does not get a placeholder download.

## Preserve previous downloads

Do not edit an already published revision's inputs, README or license. Use a new
revision such as `1.0.0-r2`, retain the older catalog entry and add a short change
history. A new modpack version also gets a new entry and source directory.
Existing downloads are reused byte-for-byte after checking their contents, fixed
metadata and file checksums; they are not recompressed on another computer merely
because its compression library differs.

If an archive is missing but tracked, restore its recorded bytes from Git instead
of rebuilding that published filename. If a new, **untracked** first draft needs
correction before publication, use:

```powershell
python tools/presets/build.py --write --replace-unpublished
```

That flag cannot replace a tracked archive. The normal `--write` command refuses
to change any existing archive's inputs. `--check` never creates or changes files.

## Review and publish

Inspect the archive contents, generated page, compatibility wording and checksums.
Validate the inputs with the actual target modpack and the appropriate Essence
Ascendance build, then distinguish that evidence from a full progression
playthrough. A preset cannot supply a runtime fix that only exists in a newer mod
jar. Never label a preset for a newly updated pack merely because an older cached
profile still loads in it.

Commit the reviewed source, catalog, ZIP, checksum file and generated wiki pages
together. Push the main repository first so the wiki's direct ZIP links exist.
Then follow [the wiki publishing guide](../docs/wiki-maintenance/PUBLISHING.md)
to preview and publish the selected catalog/detail/navigation pages. Publishing
the main repository alone does not update GitHub's separate wiki.

For example, the first preset's selected generated pages are:

```powershell
./tools/wiki/publish.ps1 -Pages Config-Presets.md,Preset-FTB-Evolution-2-1-5-1-r1.md
./tools/wiki/publish.ps1 -Pages Config-Presets.md,Preset-FTB-Evolution-2-1-5-1-r1.md -Publish -Message 'Publish reviewed configuration presets'
```

Include `_Sidebar.md`, `_Footer.md` and `Home.md` when their navigation changed.
Verify the live pages and download the public ZIP to compare its SHA-256. The
maintained originals remain in this repository; there is no permanent second
wiki checkout or automatic wiki rewrite.

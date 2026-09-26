# Art assets

This folder is the version-controlled home for authoring assets. It is backed up with the project in Git/Gitea and is **not** a Minecraft resource directory. Open and save the project copies directly; the auxiliary folder is no longer an art source.

## Layout

| Location | Contents | Included in production jars? |
| --- | --- | --- |
| `source/models/armor/` | Current Ascendance armor Blockbench project | No |
| `source/models/block/` | Current block and machine Blockbench projects | No |
| `source/models/item/` | Current item Blockbench projects | No |
| `source/textures/armor/ascendance/` | 17 editable grayscale masks: eight base/accent pairs and base-only belt | No |
| `previews/armor/` | Whole-set visual previews | No |
| `history/armor/` | Previous armor source, material references, repaint scripts, notes and verification | No |
| `../common/src/main/resources/assets/essence_ascendance/` | Runtime PNGs, JSON models, EAM1 meshes, and other game resources | Yes |

All paths in the following table are relative to this folder, except runtime paths, which are relative to `common/src/main/resources/assets/essence_ascendance/`.

## Model index

| Editable model | Runtime assets |
| --- | --- |
| [Ascendance armor](source/models/armor/ascendance_armor.bbmodel) | `meshes/armor/ascendance/*.eamesh`; `textures/armor/ascendance/generated/*.png` |
| [Channelstone](source/models/block/channelstone.bbmodel) | `models/block/channelstone*.json`; `textures/block/channelstone.png` |
| [Crucible](source/models/block/crucible.bbmodel) | `meshes/essence_crucible.eamesh`; `textures/block/essence_crucible.png` |
| [Essence block](source/models/block/essence_block.bbmodel) | `models/item/essence_block_layers.json`; `textures/item/essence_block/`; baked block tier variants |
| [Essence ore](source/models/block/essence_ore.bbmodel) | `textures/block/latent_ore/`; adaptive ore models and sprites |
| [Infuser](source/models/block/infuser.bbmodel) | `meshes/essence_infuser.eamesh`; `textures/block/essence_infuser.png` |
| [Nexus](source/models/block/nexus.bbmodel) | `meshes/ascendance_nexus.eamesh`; `textures/block/ascendance_nexus.png` |
| [Pylon](source/models/block/pylon.bbmodel) | `meshes/essence_pylon.eamesh`; `textures/block/essence_pylon.png` |
| [Focus](source/models/item/focus.bbmodel) | `meshes/focus.eamesh`; `textures/item/focus.png` |
| [Essence ingot](source/models/item/essence_ingot.bbmodel) | `models/item/essence_ingot.json`; `textures/item/essence_ingot/` |
| [Essence nugget](source/models/item/essence_nugget.bbmodel) | `models/item/essence_nugget.json`; `textures/item/essence_nugget/` |

The initial migration preserved Blockbench projects and embedded textures byte-for-byte. Current sources may receive subsequent intentional art edits; previous armor paint passes are retained in `history/armor/`. Only armor and Focus currently have automated model importers; storing another model here does not automatically replace its runtime exports.

The current armor contains nine meshes and 17 source masks. The base-only belt equips with leggings and follows the torso at pivot `(0,0,0)`. Its 128×128 image occupies x=256 in the 384×128 leggings atlas, after the left and right leg images. The latest prepaint remodel is [preserved here](history/armor/belt-remodel-shading/ascendance_armor.before-shading.bbmodel).

## Editing and building

Run commands from the repository root:

```powershell
# Rebuild armor meshes, 24 slot/tier atlases and four exact accent-only emission atlases.
.\gradlew.bat :common:generateAscendanceArmor

# Explicitly import all embedded armor textures after editing them in Blockbench, then rebake.
# This replaces all 17 editable source PNGs. Normal builds never do this import.
.\gradlew.bat :common:importAscendanceArmorTextures :common:generateAscendanceArmor

# Re-export the Focus mesh and its runtime texture.
python tools/export_focus_mesh.py

# Build the production jars for both loaders.
.\gradlew.bat :fabric:remapJar :neoforge:remapJar
```

For armor shading changes, edit `source/textures/armor/ascendance/` directly. Preserve each mask's coverage and image dimensions. The generator stitches base/accent pairs into a single tinted texture per armor slot and tier; `belt_base.png` receives only the canonical base grey. It never fills holes or adds painted pixels. Geometry and UV changes belong in `source/models/armor/ascendance_armor.bbmodel`. See [the armor documentation](../docs/ascendance-armor.md) for more detail.

The same generator also writes `{helmet,chestplate,leggings,boots}_emission.png` in the runtime generated texture folder. These atlases copy the source accent pixels exactly, including alpha and hidden RGB, at their existing atlas coordinates. The belt region and unused space stay transparent. Do not paint these outputs: edit the original accent masks and regenerate. Runtime code supplies the Focus-style luminous tint and tier alpha `0, 22, 45, 67, 90, 112`; it preserves the combined normally lit atlas beneath the emission. Procedural crown, collar, waist, thigh and ankle forms need no additional authored textures. Their bounded design lives in `ArmorVisualStyle`, with animation/geometry in `ArmorOrnaments`.

Run `:common:armorVisualInvariants` with the existing armor/texture checks for procedural bounds, complexity and flight clearance. Run `armorJarInvariants` to build and inspect both production jars for the 37 generated armor resources and excluded authoring assets. The existing texture-only previews below do not show emission or procedural ornaments; use the [in-game review matrix](../docs/ascendance-armor.md#visual-verification) to evaluate those effects.

The current repaint uses consistent upper front-left lighting `(-0.35, +0.75, -0.56)` in display axes X/right, Y/up, Z/back. Smooth panel gradients, highlights along actual bevel edges and soft shadows within each part are painted into grayscale. Coplanar triangle diagonals remain unaccented. Observed base shades span 152–255 and accents 170–255; every source alpha byte and hidden RGB value is preserved.

Two optional tools support explicit art work and never run during a normal build:

- `tools/ShadeAscendanceArmor.java`: `<input.bbmodel> <material-reference.png> <output-directory>`; requires Gson when compiling/running and writes a separate repaint for review.
- `tools/PreviewAscendanceArmor.java`: `<asset-namespace-directory> <preview.png> [before-asset-namespace-directory]`; uses standard Java libraries and renders the exported meshes without added lighting. The asset namespace directory is `common/src/main/resources/assets/essence_ascendance` for current resources.

The other base/accent textures already under the runtime resources are used by Minecraft's layered item rendering, particle textures, or ore sprite generation. They intentionally remain packaged. The block texture generator also produces required block models/textures under `common/build/generated-resources/essence-block-textures`; builds recreate those outputs from version-controlled inputs.

Commit source artwork, exporter/generator changes, and changed runtime resources together. Keep the `art/` tree out of resource source sets. New authoring files should use lowercase names with underscores, with current work in `source`, viewable exports in `previews`, and superseded work in `history`. Build outputs and dependency caches do not belong here.

## Migration record

[The migration manifest](history/asset-migration-2026-09-25.json) records the original location, destination, size, and SHA-256 of every moved file. The 43 auxiliary art files and 18 duplicate project copies were verified before their old copies were removed. Identical files share one canonical destination. The workbook, unrelated validation material, and dependencies were outside this migration's scope.

Historical repaint notes and scripts are retained byte-for-byte for provenance. Their old auxiliary paths and older mesh formats describe their original run; use the paths and commands above for current work. [The latest six-tier preview](previews/armor/armor-shaded-belt-preview.png) shows the actual exported meshes with their baked textures and no added lighting. [The before/after comparison](previews/armor/armor-shading-comparison.png) shows input authoring colors beside the final repaint. Both are software renders, not in-game screenshots.

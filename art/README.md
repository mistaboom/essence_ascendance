# Art assets

The procedural project logo lives in [branding/](branding/README.md). Run `./gradlew.bat generateLogo` to regenerate its SVG and square PNG exports from `tools/LogoGenerator.java` and the canonical palettes. Branding exports are kept here for external use; the runtime asset rules below apply to the Blockbench pipeline.

Edit the Blockbench projects in `source/models/`, save their embedded textures, and build. The same asset pipeline imports every registered model automatically. Changes to an existing named texture, including the sword's accent, are picked up without a separate extraction command.

Unfinished projects can be listed in the manifest with category `authoring_only`. They remain editable source art and produce no runtime assets until an export category is assigned.

## Source and output locations

| Location | Purpose |
| --- | --- |
| `source/models/block/` | Block textures and machine meshes |
| `source/models/item/` | Inventory sprites, layered equipment sprites, and held item meshes such as Focus and shield |
| `source/models/armor/` | Equipped armor geometry and its embedded material masks |
| `asset-manifest.json` | Source files, named texture mappings, asset categories, and import settings |
| `../common/versions/<minecraft>/build/generated-resources/assets/assets/essence_ascendance/` | Generated runtime textures, models, and meshes for the selected target |
| `../common/versions/<minecraft>/build/asset-work/armor-masks/` | Extracted armor masks used during target generation and verification |
| `../common/src/main/resources/assets/essence_ascendance/` | Hand-authored runtime configuration, including blockstates, renderer models, and language text |

The BBModels and manifest are the authoring sources. Generated files belong under `build/`, are ignored by Git, and are recreated after `clean`. Do not edit generated PNGs or copy them into `src/main/resources`. The `art/` tree and intermediate masks are never packaged in production jars; Minecraft receives the generated runtime resources alongside hand-authored configuration.

## Editing and building

Run these commands from the repository root:

```powershell
# Import all registered BBModels and regenerate their runtime resources.
.\gradlew.bat :common:generateAssets

# Build both loaders; resource processing runs generateAssets automatically.
.\gradlew.bat build

# Build and verify that both production jars contain the generated assets.
.\gradlew.bat assetJarInvariants
```

An ordinary build is enough after saving a BBModel. Gradle tracks the manifest, source models, and generator code, so unchanged inputs can reuse existing outputs. Older asset generation task names remain aliases for the unified pipeline.

To add an asset, place its BBModel in the appropriate source folder and register its named textures and category in [asset-manifest.json](asset-manifest.json). Use lowercase filenames with underscores. Reuse the existing category and importer for comparable assets. Texture names are part of the mapping: rename a source texture only when updating its manifest mapping too. Commit source artwork, manifest changes, and generator changes; generated outputs do not need committing.

## Asset categories

| Category | Build behavior | Runtime behavior |
| --- | --- | --- |
| Untinted sprites and block textures | Extract the selected embedded PNG unchanged | Raw Latent is a flat sprite; Raw Latent Block uses the placed cube model for its corner-view item |
| Layered inventory sprites | Extract named layers and generate the configured item models | `base` uses primary tint, `accent` uses accent tint, and `nochange` stays untinted |
| Stitched block textures | Extract base/accent masks and compose the required variants using the shared palette and pixel compositor | Placed Essentium blocks use one composite texture per variant |
| Static meshes | Export the configured geometry transform and texture through the shared mesh importer | Machines and Focus use their existing mesh renderers |
| Stitched item meshes (`stitched_mesh`) | Bake named base/accent masks into six tier textures and an exact accent emission mask; export one mesh in its declared coordinate frame | The shield shares armor's palette, normal composite pass, and current emission settings |
| Equipped armor | Extract the 17 masks, export nine meshes, and bake 24 tier atlases plus four emission atlases | Armor animates with the wearer and uses the established tier palette and emission progression |
| Adaptive ore masks | Extract the ore and transition masks unchanged | Compose against the actual host block texture during resource loading |

Adaptive ore composition belongs at runtime because resource packs and installed mods determine the host textures. It uses the shared pixel compositor. Essentium inventory blocks retain their layered, tinted item models, while placed blocks use the stitched variants.

The Focus import applies its declared opaque-white texture transform to preserve its current tint-neutral appearance and avoid transparency at its narrow UV strip. Other extracted PNGs retain their embedded bytes. Unused Blockbench reference images are not exported.

## Model index

Paths below are relative to this folder. Runtime identifiers are relative to `assets/essence_ascendance/` inside the generated resource directory.

| Editable model | Runtime assets |
| --- | --- |
| [Ascendance armor](source/models/armor/ascendance_armor.bbmodel) | `meshes/armor/ascendance/*.eamesh`; `textures/armor/ascendance/generated/*.png` |
| [Ascendance shield](source/models/item/ascendance_shield.bbmodel) | `meshes/item/ascendance_shield.eamesh`; `textures/item/ascendance_shield/{tier,emission}.png` |
| [Channelstone](source/models/block/channelstone.bbmodel) | `textures/block/channelstone.png` |
| [Crucible](source/models/block/crucible.bbmodel) | `meshes/essence_crucible.eamesh`; `textures/block/essence_crucible.png` |
| [Essence block](source/models/block/essence_block.bbmodel) | `textures/item/essence_block/`; stitched block variants |
| [Essence ore](source/models/block/essence_ore.bbmodel) | `textures/block/latent_ore/` |
| [Infuser](source/models/block/infuser.bbmodel) | `meshes/essence_infuser.eamesh`; `textures/block/essence_infuser.png` |
| [Nexus](source/models/block/nexus.bbmodel) | `meshes/ascendance_nexus.eamesh`; `textures/block/ascendance_nexus.png` |
| [Pylon](source/models/block/pylon.bbmodel) | `meshes/essence_pylon.eamesh`; `textures/block/essence_pylon.png` |
| [Raw Latent block](source/models/block/raw_latent_block.bbmodel) | `textures/block/raw_latent_ore_block.png`; cube and inherited item models |
| [Focus](source/models/item/focus.bbmodel) | `meshes/focus.eamesh`; `textures/item/focus.png` |
| [Essence ingot](source/models/item/essence_ingot.bbmodel) | `textures/item/essence_ingot/`; layered item model |
| [Essence nugget](source/models/item/essence_nugget.bbmodel) | `textures/item/essence_nugget/`; layered item model |
| [Raw Latent](source/models/item/raw_latent.bbmodel) | `textures/item/raw_latent_ore.png`; untinted item model |
| [Ascendance Archive](source/models/item/ascendance_archive.bbmodel) | `textures/item/ascendance_archive.png`; untinted generated item model |
| [Ascendance Matrix](source/models/item/ascendance_matrix.bbmodel) | `textures/item/ascendance_matrix.png`; untinted generated item model |
| [Ascendance sword](source/models/item/ascendance_sword.bbmodel) | `textures/item/ascendance_sword/`; six-tier item models |
| [Ascendance glaive](source/models/item/ascendance_glaive.bbmodel) | `textures/item/ascendance_glaive/{blade_base,blade_accent,nochange,butt_accent}.png`; six-tier item models |
| [Ascendance bow](source/models/item/ascendance_bow.bbmodel) | `textures/item/ascendance_ranged_weapon/`; four bow states at all six tiers |
| [Ascendance caster](source/models/item/ascendance_caster.bbmodel) | `textures/item/ascendance_caster/`; six-tier handheld item models |
| [Ascendance pickaxe](source/models/item/ascendance_pickaxe.bbmodel) | `textures/item/ascendance_pickaxe/`; six-tier item models |
| [Ascendance axe](source/models/item/ascendance_axe.bbmodel) | `textures/item/ascendance_axe/`; six-tier item models |
| [Ascendance shovel](source/models/item/ascendance_shovel.bbmodel) | `textures/item/ascendance_shovel/`; six-tier item models |
| [Ascendance hoe](source/models/item/ascendance_hoe.bbmodel) | `textures/item/ascendance_hoe/`; six-tier item models |
| [Ascendance fishing rod and hook](source/models/item/ascendance_rod.bbmodel) | `textures/item/ascendance_fishing_rod/`; idle and cast models at all six tiers; `textures/entity/ascendance_fishing_hook/{tier}.png` |
| [Helmet](source/models/item/ascendance_helmet.bbmodel), [chestplate](source/models/item/ascendance_chestplate.bbmodel), [leggings](source/models/item/ascendance_leggings.bbmodel), [boots](source/models/item/ascendance_boots.bbmodel) | `textures/item/ascendance_{helmet,chestplate,leggings,boots}/`; six-tier armor inventory models |

The equipment catalog supplies all six item tiers to JEI and the creative menu. Texture import does not change the existing equipment binding rules. Inventory armor uses base/accent layers; the sword, caster, tools, and all four bow states also include `nochange`. Tint index `1` is the shared equipment accent contract: marked generated equipment models receive the same accent-only `0..180` emission curve as armor and shield, in every item display context. The caster grip remains authored at pixel `(12,12)`.

The glaive exports its corrected four 32 x 32 masks unchanged. Layers 0/1/2 are blade base metal, blade accent, and untinted wooden shaft; layer 3 is the separate butt accent and maps to the same tint/emission material as layer 1 through `EmissiveAccentItem.materialTintIndex`. There is no butt base-metal layer. Its `ignore` reference image is never exported.

The same `layered_item` manifest entry now declares `spriteMeshes`: build-time pixel extrusion into the existing EAM1 format, consumed by `BlockbenchStaticMesh`. The declared origin is `(20,12)` for both rigid blade masks and `(2,30)` for butt/wood. The diagonal sprite frame is lateral `(x+y-originX-originY)/unitsPerBlock`, axial `(x-y-originX+originY)/unitsPerBlock`, with `unitsPerBlock=32*sqrt(2)` and thickness `1/32` block. Original texel colors/alpha are sampled without a second runtime image importer or atlas.

The wood exports its complete mesh plus grip rows 23..31, front rows 0..22, and repeat row 22. That row contains precisely `(8,22)`, `(9,22)`, `(10,22)` with RGB `#443922/#6C5618/#271D0A`. Repeating it shifts source coordinates by `(+1,-1)`, or `sqrt(2)/32` blocks along the shaft, with no lateral shift. The blade and complete upper shaft share one continuous translation. Whole repeat rows feed backwards from the moving upper shaft into the fixed grip; the last row overlaps by less than one repeat inside the palm. This keeps the shaft texture moving with the blade and hides row-count transitions at the hand. Grip and upper shaft retain their original end details; only the interior strip repeats. The blade socket keeps its authored one-texel wood overlap. Seven small generated meshes stay under `common/versions/<minecraft>/build`, and both accents share `EquipmentAppearance` tint/emission resolution in compact and extended views. The new blade projects to axial `[0,24]` and lateral `[-5,5]` diagonal texels from its socket; collision bounds use the same dimensions. The world renderer follows server-solved attachment distances: ordinary tier ranges remain 4/5/6/8/9/10 blocks. Diagnostic rendering tests retain 16/64-block stress inputs; repeat count never defines gameplay range. The native visual fixture records mesh motion across repeat boundaries, verifies palm overlap and camera clipping, and captures a fractional extension sequence. The tiny diagonal silhouette can alias at distant/oblique views even when the original square pixels and repeat spacing are correct.

Glaive animation uses the recorded action clock, independently of attack recharge. The two-tick presentation buffer supplies a short rest-to-attack pivot, and the existing non-damaging retraction blends back to native rest. Full-weight attacks preserve the server-solved tip and fixed 45-degree wrist angle. Only the boundary keyframes vary the wrist angle. Fast consecutive actions finish the buffered return before preparing again; the preparation uses the available gap without delaying damage playback. First-person boundary transforms compensate for the hand/world field-of-view difference, and native equip lowering does not follow recharge for this weapon. The native fixture captures both attack modes through these first-person keyframes.

See [Ascendance armor art](../docs/ascendance-armor.md) for equipped-armor atlas layouts, emission settings, and visual verification. Optional `tools/ShadeAscendanceArmor.java` and `tools/PreviewAscendanceArmor.java` support separate authoring and preview work; they do not run during ordinary builds. The preview tool reads the generated asset namespace directory listed above.

See [Ascendance shield art](../docs/ascendance-shield.md) for its embedded mask names, hand-space transform, and visual checks. The shield belongs under `source/models/item/` because it is a held item, even though its material and emission workflow matches armor. Its base/accent masks stay embedded in the BBModel; no duplicate source PNGs are required.

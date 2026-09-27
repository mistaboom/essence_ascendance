# Ascendance armor art

Edit geometry, UVs, and embedded textures in `art/source/models/armor/ascendance_armor.bbmodel`, then build. Armor participates in the same manifest-driven asset pipeline as blocks, machines, and inventory sprites. The BBModel is the source of truth for all nine armor meshes and 17 material masks. See [the art guide](../art/README.md) for the complete workflow.

`Head` supplies the helmet; `Chest`, `Left_Arm`, and `Right_Arm` supply the chestplate; `Left_Leg`, `Right_Leg`, and `Belt` supply leggings; `Left_Foot` and `Right_Foot` supply boots. The belt follows the torso while equipped as part of the leggings. The separate armor inventory sprites are authored in `art/source/models/item/ascendance_{helmet,chestplate,leggings,boots}.bbmodel` and use the shared layered-item importer.

| Purpose | Path |
| --- | --- |
| Geometry, UVs, and embedded masks | `art/source/models/armor/ascendance_armor.bbmodel` |
| Asset registration | `art/asset-manifest.json` |
| Extracted intermediate masks | `common/build/asset-work/armor-masks/` |
| Baked tier and emission PNGs | `common/build/generated-resources/assets/assets/essence_ascendance/textures/armor/ascendance/generated/` |
| Runtime EAM1 meshes | `common/build/generated-resources/assets/assets/essence_ascendance/meshes/armor/ascendance/` |

The embedded texture names are `{Head,Chest,Left_Arm,Right_Arm,Left_Leg,Right_Leg,Left_Foot,Right_Foot}_{Base,Accent}`, plus `Belt_Base`. The extracted intermediate filenames are lowercase. There are eight base/accent pairs and one base-only belt, with no belt accent. Intermediate PNGs and runtime outputs are generated files; edit the embedded textures in Blockbench instead. They are recreated after `clean` and never copied into the source resource tree.

## Geometry and atlases

`:common:generateAssets` runs automatically during resource processing for both loaders. It preserves the triangular faces, winding, and local UV coordinates. The source meshes use origin `(0,0,0)` and rotation `(-90,0,0)`; the forward-facing import maps each source vertex to `(x, 24-z, y)`, subtracts the animated humanoid part pivot, and divides by 16. Head, chest, and belt use `(0,0,0)`, arms use `(±5,2,0)`, and legs/feet use `(±1.9,12,0)`, with positive X for the authored left side.

The leggings model attaches the belt to its torso bone, independently of either leg's walking rotation. Meshes attach to the corresponding humanoid parts so animation, visibility, and enchantment glint follow the armor pipeline. The source UV layout was not authored for vanilla armor trims; trim placement is not validated.

Armor and Essentium blocks share `MaskedTextureWriter` and `TexturePixels` to bake complementary base/accent masks. Armor produces one composite PNG per slot and tier. The belt receives the canonical base tint without an accent layer.

| Tier | Base mask | Accent mask |
| --- | --- | --- |
| Latent | Canonical light grey `#B9BDC4` | Canonical Latent copper |
| Dormant–Transcendent | Canonical tier grey (`*_PRIMARY`) | Canonical tier accent (`*_METAL`) |

Grayscale highlights and panel shading are multiplied by the canonical palette. The compositor preserves source coverage and dimensions; it does not resample, dilate, fill holes, add painted padding, or alter geometry. Composited images occupy these atlas positions:

| Slot | Atlas dimensions | Source placement (pixels) |
| --- | --- | --- |
| Helmet | 256×256 | Head at (0,0), 256×256 |
| Chestplate | 512×256 | Chest at (0,0), 256×256; Left_Arm at (256,0), 128×128; Right_Arm at (384,0), 128×128 |
| Leggings | 384×128 | Left_Leg at (0,0), 128×128; Right_Leg at (128,0), 128×128; Belt at (256,0), 128×128 |
| Boots | 256×128 | Left_Foot at (0,0), 128×128; Right_Foot at (128,0), 128×128 |

Unused atlas space stays transparent. UVs use each Blockbench mask's `uv_width` and `uv_height` metadata (64 for head/chest, 32 for limbs/belt) before conversion to normalized atlas coordinates. Runtime identifiers remain `meshes/armor/ascendance/{part}.eamesh` and `textures/armor/ascendance/generated/{slot}_{tier}.png` beneath the mod asset namespace.

Each EAM1 mesh contains big-endian magic `0x45414D31`, a triangle count, and three `(x,y,z,u,v,nx,ny,nz)` float vertices per triangle. Every face has a normalized surface normal. The importer validates named meshes, pivots, rotations, triangulation, finite positions and UVs, image dimensions, and matching positive mask UV dimensions.

## Editing and generation

Save the armor BBModel after editing its geometry or textures, then run `./gradlew :common:generateAssets` or an ordinary build. No separate texture import is required. Gradle tracks the BBModels, manifest, and generator/compositor/palette code. Older asset generation task names remain aliases for the unified pipeline.

Only generated runtime resources and hand-authored runtime configuration enter the jars. BBModels, the manifest, and extracted intermediate armor masks stay outside the resource source sets. Masks needed during rendering, such as layered inventory sprites and adaptive ore overlays, are exported as runtime textures by their corresponding categories.

The optional `tools/ShadeAscendanceArmor.java` authoring tool accepts `<input.bbmodel> <material-reference.png> <output-directory>`. It writes a separate repaint for review and requires Gson when compiling and running. It does not run during a normal build; incorporate an accepted repaint into the canonical BBModel.

The optional `tools/PreviewAscendanceArmor.java` accepts `<asset-namespace-directory> <preview.png> [before-asset-namespace-directory]` and uses standard Java libraries. Point it to `common/build/generated-resources/assets/assets/essence_ascendance` after generation. It renders the exported meshes and baked textures without added lighting; it does not show runtime emission.

## Accent emission

The normal armor pass uses the combined base/accent atlas and ordinary lighting. Four generated resources, `{helmet,chestplate,leggings,boots}_emission.png`, copy the exact grayscale accent ARGB pixels into the same atlas positions, including hidden RGB. The belt and unused atlas space remain transparent, and the renderer skips the belt mesh in the emission pass. Edit the embedded accent masks and rebuild to update these derived textures.

| Tier | Emission alpha (0–255) |
| --- | --- |
| Latent | 0 |
| Dormant | 36 |
| Awakened | 72 |
| Resonant | 108 |
| Ascendant | 144 |
| Transcendent | 180 |

`ArmorEmission.MAX_ALPHA` defines the endpoint of 180; lower tiers scale evenly, with Latent staying at zero. Tint uses the Focus's 3:1 accent/white mix, starting from `EquipmentTierVisuals.armorAccentRgb(tier)`. Base metal and the belt retain normal lighting at every tier.

`ArmorRenderTypes` uses Minecraft's unlit eyes shader with ordinary alpha blending. The shader does not call `minecraft_mix_light`, so directional normals cannot darken side-facing sleeve accents. Emission uses identical mesh vertices and UVs, `LEQUAL` depth testing, color-only writes, nearest texture sampling, and the same `VIEW_OFFSET_Z_LAYERING` as vanilla armor. The combined normally lit atlas remains beneath the accent overlay. The existing enchantment foil pass is preserved, and Minecraft owns shader/texture resource reloads.

`ArmorPresentationMixin` provides the common post-armor hook for Fabric and NeoForge. Its emission model copies the wearer's animated transforms and baby scaling, then enables parts according to the equipped slot. Chestplate emission includes both arms even when the wearer's skin or an armor stand's wooden arms are hidden. Leggings emit on their legs only, never on the torso-attached belt. Invisible entities and spectators receive no added pass.

An equipped set adds at most four accent mesh passes; Latent skips emission. Armor has no floating ornaments or particles. Existing Essence Wings, Fatigue Flight, and Vector Boost effects remain separate from armor rendering.

## Visual verification

Run these commands from the repository root:

```powershell
.\gradlew.bat :common:ascendanceArmorInvariants :common:armorVisualInvariants :common:texturePixelInvariants :common:ascendancePaletteInvariants
.\gradlew.bat assetJarInvariants
```

The armor suite checks normal/emission atlas pixels, transparent belt and unused space, imported geometry, UVs, bone attachment, slot isolation, and animation. It compares emitted mesh vertices/UVs against the ordinary armor pose with hidden wearer limbs, independently rotated arms/legs, crouched torso, and baby scaling. Both sleeves must emit when the parent arms are hidden.

`armorVisualInvariants` checks tier scaling, luminous tint, the unlit shader state, normal alpha blending, armor depth, and color-only writes. `assetJarInvariants` builds both production jars and verifies their generated resources against the current build outputs, excluding authoring files. The older `armorJarInvariants` entry point remains available as an alias.

For in-game review, select each armor tier from the creative menu and equip it. Alternatively, hold an armor item and use `/essence admin item tier set <tier>` before equipping; that command requires cheats/operator permission and follows the existing equipment tier/binding behavior.

1. Compare daylight and an enclosed dark room. Latent must have no added emission; subsequent tiers must increase steadily. Base metal and the belt must stay normally lit.
2. Inspect both sleeves from front, side, and rear on a player and an ordinary armor stand with hidden wooden arms. Turn, walk, crouch, swim, and fall-fly to check alignment.
3. Enchant a piece, re-equip it, and reload resources with F3+T. Check glint, mask edges, and the absence of seams, flicker, halos, or light showing through solid surfaces.
4. Check all six tiers separately and as a set, including with flight effects active. Only the existing flight effects should appear outside the armor.

Automated checks verify resource and rendering contracts; in-game inspection establishes visual fit, perceived brightness, and glint strength.

# Ascendance armor art

All eight meshes are imported from `art/source/models/armor/ascendance_armor.bbmodel`. `Head` supplies the helmet; `Chest`, `Left_Arm` and `Right_Arm` supply the chestplate; `Left_Leg` and `Right_Leg` supply leggings; `Left_Foot` and `Right_Foot` supply boots. Inventory icons are unchanged. The user's previous source is preserved as `art/history/armor/Armor.before-directional-shading.bbmodel`. See [the art index](../art/README.md) for the complete source layout.

The files to edit and inspect are directly in the repository:

| Purpose | Canonical path |
| --- | --- |
| Geometry and UV source | `art/source/models/armor/ascendance_armor.bbmodel` |
| Editable white base/accent PNGs | `art/source/textures/armor/ascendance/` |
| Baked tier PNGs | `common/src/main/resources/assets/essence_ascendance/textures/armor/ascendance/generated/` |
| Runtime EAM1 meshes | `common/src/main/resources/assets/essence_ascendance/meshes/armor/ascendance/` |
| Material previews | `art/previews/armor/` |
| Previous paint passes and references | `art/history/armor/` |

Source PNG names are lowercase: `{head,chest,left_arm,right_arm,left_leg,right_leg,left_foot,right_foot}_{base,accent}.png`. Edit these 16 PNGs directly to change shading. Normal builds use these external PNGs and never overwrite them from the embedded Blockbench textures. The corrected `left_leg_accent.png` mask retains the user's removed pixels; the generator cannot restore them from an older embedded image.

`generateAscendanceArmor` runs automatically during resource processing for both loaders. It preserves the triangular faces, winding and local UV coordinates. The source meshes all use origin `(0,0,0)` and rotation `(-90,0,0)`; the established forward-facing import maps each source vertex to `(x, 24-z, y)`, then subtracts the animated humanoid part pivot and divides by 16. Head and chest use `(0,0,0)`, arms use `(±5,2,0)`, and legs/feet use `(±1.9,12,0)`, with positive X for the authored left side. Meshes attach to the corresponding humanoid model parts so pose animation, visibility and enchantment glint follow the armor pipeline. The source UV layout was not authored for vanilla armor trims; trim placement is not validated for this preview.

Both armor and Essentium blocks use `MaskedTextureWriter` and `TexturePixels` to bake complementary base/accent masks into one PNG per slot and tier. There are no separate coplanar base/accent rendering passes. All 16 source masks retain their authored resolution and coverage:

| Tier | Base mask | Accent mask |
| --- | --- | --- |
| Latent | Canonical light grey `#B9BDC4` | Canonical Latent copper |
| Dormant–Transcendent | Canonical tier grey (`*_PRIMARY`) | Canonical tier accent (`*_METAL`) |

Latent's base now receives the same tint treatment as every other tier. Its grey continues the series one step lighter than Dormant, calculated channel by channel as `2 * Dormant - Awakened`: `#B9BDC4`. Existing tier accent colors remain canonical. The repaint uses bright neutral grayscale and smooth restrained shading so multiplying by the tier palette preserves the accents' color strength. Source alpha bytes, hidden RGB pixels, image dimensions, geometry and UVs are preserved exactly.

The current paint pass uses one upper front-left model-space light, normalized from `(-0.35, +0.75, -0.56)` in X/right, Y/up, Z/back coordinates. Source face normals and a broad position-based material field share this direction, so highlights and shadows stay coherent across the set. Paint remains near-white (observed base 228-254 and accent 240-254), with no grain or streaks. The latest left-leg accent correction is retained: 982 painted pixels, including the 32-pixel knee cutout from the author.

The generator only composites existing pixels. It does not resample, dilate, fill holes, add painted padding, or alter the model. Source base/accent images are copied into these atlases after compositing:

| Slot | Atlas dimensions | Source placement (pixels) |
| --- | --- | --- |
| Helmet | 256×256 | Head at (0,0), 256×256 |
| Chestplate | 512×256 | Chest at (0,0), 256×256; Left_Arm at (256,0), 128×128; Right_Arm at (384,0), 128×128 |
| Leggings | 256×128 | Left_Leg at (0,0), 128×128; Right_Leg at (128,0), 128×128 |
| Boots | 256×128 | Left_Foot at (0,0), 128×128; Right_Foot at (128,0), 128×128 |

Unused atlas space stays transparent. UVs use each Blockbench mask's `uv_width` and `uv_height` metadata (64 for head/chest, 32 for limbs) before conversion to normalized atlas coordinates. Resources are generated as `meshes/armor/ascendance/{head,chest,left_arm,right_arm,left_leg,right_leg,left_foot,right_foot}.eamesh` and `textures/armor/ascendance/generated/{helmet,chestplate,leggings,boots}_{tier}.png` beneath the mod asset namespace. Each EAM1 mesh contains big-endian magic `0x45414D31`, a triangle count, then three `(x,y,z,u,v,nx,ny,nz)` float vertices per triangle. Every face has its own normalized surface normal, matching the other authored mod meshes.

To update geometry or UVs, edit `art/source/models/armor/ascendance_armor.bbmodel` in Blockbench and build. To intentionally replace all 16 editable PNGs with that model's embedded textures, run `./gradlew :common:importAscendanceArmorTextures`. This explicit import preserves each embedded PNG's exact bytes; it is never a dependency of a normal build. Running `./gradlew :common:importAscendanceArmorTextures :common:generateAscendanceArmor` imports first, then rebakes. For shading-only edits, edit the PNGs under `art/source/textures/armor/ascendance/` and run `./gradlew :common:generateAscendanceArmor` or a normal build; no import is needed. Generated tier PNGs and meshes are derived outputs and will be replaced on regeneration.

The importer checks all named meshes, pivots, rotations, triangulation, finite positions/UVs, mask image dimensions and matching positive mask UV dimensions instead of silently accepting an incompatible export. A clean checkout never depends on the auxiliary folder. Task inputs include the Blockbench source, all 16 external source PNGs, and generator/compositor/palette code. Outputs are limited to the armor mesh and generated tier texture folders, keeping editable PNGs outside the normal generator's output ownership.

Only the runtime resource tree is packaged; `art/` is versioned with the project but is not a resource source directory. Authoring models, the 16 armor source masks, previews and repaint history stay out of the mod jars. Other masks that Minecraft reads at runtime, including Essentium layered item textures and latent ore overlays, remain runtime resources. The resource configuration also excludes `.bbmodel` files and the former armor source-mask path.

Run `:common:clean` once after migrating an existing checkout, before building, to discard cached armor source masks, `models/armor/*.mesh` and `textures/models/armor/generated/*.png` copies. These obsolete paths are neither generator outputs nor configured resource inputs. The old `generateAscendanceHelmet`, `compileAscendanceHelmetGenerator` and `ascendanceHelmetInvariants` task names remain aliases for the full-armor tasks.

To compare in game, hold each Ascendance armor item in your main hand and use `/essence admin item tier set latent` (or `dormant`, `awakened`, `resonant`, `ascendant`, `transcendent`), then equip it and use third-person view. The admin command requires cheats/operator permission and applies the existing equipment tier/soulbinding behavior.

Verification: `:common:ascendanceArmorInvariants` covers the full armor import, canonical palette and all generated slot/tier atlases against the source masks. `:common:texturePixelInvariants` covers shared compositing math; `:common:ascendancePaletteInvariants` covers the shared canonical palette contract. Build both platform jars with `:fabric:remapJar :neoforge:remapJar`. These automated checks do not replace an in-game visual fit and animation check.

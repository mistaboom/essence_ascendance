# Ascendance armor art

All nine meshes are imported from `art/source/models/armor/ascendance_armor.bbmodel`. `Head` supplies the helmet; `Chest`, `Left_Arm` and `Right_Arm` supply the chestplate; `Left_Leg`, `Right_Leg` and `Belt` supply leggings; `Left_Foot` and `Right_Foot` supply boots. The belt follows the torso while equipped as part of the leggings. Inventory icons are unchanged. The latest remodel before repainting is preserved as `art/history/armor/belt-remodel-shading/ascendance_armor.before-shading.bbmodel`. See [the art index](../art/README.md) for the complete source layout.

The files to edit and inspect are directly in the repository:

| Purpose | Canonical path |
| --- | --- |
| Geometry and UV source | `art/source/models/armor/ascendance_armor.bbmodel` |
| Editable white base/accent PNGs | `art/source/textures/armor/ascendance/` |
| Baked tier PNGs | `common/src/main/resources/assets/essence_ascendance/textures/armor/ascendance/generated/` |
| Runtime EAM1 meshes | `common/src/main/resources/assets/essence_ascendance/meshes/armor/ascendance/` |
| Material previews | `art/previews/armor/` |
| Previous paint passes and references | `art/history/armor/` |

Source PNG names are lowercase: `{head,chest,left_arm,right_arm,left_leg,right_leg,left_foot,right_foot}_{base,accent}.png`, plus `belt_base.png`. These are 17 editable masks: eight base/accent pairs and one base-only belt. There is no belt accent file. Normal builds use these external PNGs and never overwrite them from the embedded Blockbench textures. The remodeled chest, legs and belt retain the author's current geometry, UVs and mask coverage.

`generateAscendanceArmor` runs automatically during resource processing for both loaders. It preserves the triangular faces, winding and local UV coordinates. The source meshes all use origin `(0,0,0)` and rotation `(-90,0,0)`; the established forward-facing import maps each source vertex to `(x, 24-z, y)`, then subtracts the animated humanoid part pivot and divides by 16. Head, chest and belt use `(0,0,0)`, arms use `(±5,2,0)`, and legs/feet use `(±1.9,12,0)`, with positive X for the authored left side. The leggings model attaches the belt to its torso bone, independent of either leg's walking rotation. Meshes attach to the corresponding humanoid model parts so pose animation, visibility and enchantment glint follow the armor pipeline. The source UV layout was not authored for vanilla armor trims; trim placement is not validated for this preview.

Both armor and Essentium blocks use `MaskedTextureWriter` and `TexturePixels` to bake complementary base/accent masks into one PNG per slot and tier. The belt uses the same canonical base tint with no accent layer. There are no separate coplanar base/accent rendering passes. All 17 source masks retain their authored resolution and coverage:

| Tier | Base mask | Accent mask |
| --- | --- | --- |
| Latent | Canonical light grey `#B9BDC4` | Canonical Latent copper |
| Dormant–Transcendent | Canonical tier grey (`*_PRIMARY`) | Canonical tier accent (`*_METAL`) |

Latent's base now receives the same tint treatment as every other tier. Its grey continues the series one step lighter than Dormant, calculated channel by channel as `2 * Dormant - Awakened`: `#B9BDC4`. Existing tier accent colors remain canonical. The repaint uses neutral grayscale with bright highlights and visible panel shading so multiplying by the tier palette preserves the accents' color strength. Source alpha bytes, hidden RGB pixels, image dimensions, geometry and UVs are preserved exactly.

The current paint pass uses one upper front-left model-space light, normalized from `(-0.35, +0.75, -0.56)` in display axes X/right, Y/up, Z/back. Broad panel gradients, bevel highlights along real panel edges and soft shadows cast within each armor part share this direction. Coplanar triangles are treated as one panel, so their triangulation diagonals do not become painted edges. The observed grayscale ranges are 152–255 for bases and 170–255 for accents. This pass preserves every source alpha byte and hidden RGB value, as well as all image dimensions, geometry and UVs.

[The six-tier preview](../art/previews/armor/armor-shaded-belt-preview.png) renders the actual exported meshes with only the baked textures and no additional lighting. [The before/after comparison](../art/previews/armor/armor-shading-comparison.png) compares the input authoring colors with the final paint. These are software previews, not in-game screenshots.

The generator only composites existing pixels. It does not resample, dilate, fill holes, add painted padding, or alter the model. Source base/accent images are copied into these atlases after compositing:

| Slot | Atlas dimensions | Source placement (pixels) |
| --- | --- | --- |
| Helmet | 256×256 | Head at (0,0), 256×256 |
| Chestplate | 512×256 | Chest at (0,0), 256×256; Left_Arm at (256,0), 128×128; Right_Arm at (384,0), 128×128 |
| Leggings | 384×128 | Left_Leg at (0,0), 128×128; Right_Leg at (128,0), 128×128; Belt at (256,0), 128×128 |
| Boots | 256×128 | Left_Foot at (0,0), 128×128; Right_Foot at (128,0), 128×128 |

Unused atlas space stays transparent. UVs use each Blockbench mask's `uv_width` and `uv_height` metadata (64 for head/chest, 32 for limbs/belt) before conversion to normalized atlas coordinates. Resources are generated as `meshes/armor/ascendance/{head,chest,left_arm,right_arm,left_leg,right_leg,belt,left_foot,right_foot}.eamesh` and `textures/armor/ascendance/generated/{helmet,chestplate,leggings,boots}_{tier}.png` beneath the mod asset namespace. Each EAM1 mesh contains big-endian magic `0x45414D31`, a triangle count, then three `(x,y,z,u,v,nx,ny,nz)` float vertices per triangle. Every face has its own normalized surface normal, matching the other authored mod meshes.

To update geometry or UVs, edit `art/source/models/armor/ascendance_armor.bbmodel` in Blockbench and build. To intentionally replace all 17 editable PNGs with that model's embedded textures, run `./gradlew :common:importAscendanceArmorTextures`. This explicit import preserves each embedded PNG's exact bytes; it is never a dependency of a normal build. Running `./gradlew :common:importAscendanceArmorTextures :common:generateAscendanceArmor` imports first, then rebakes. For shading-only edits, edit the PNGs under `art/source/textures/armor/ascendance/` and run `./gradlew :common:generateAscendanceArmor` or a normal build; no import is needed. Generated tier PNGs and meshes are derived outputs and will be replaced on regeneration.

The optional offline authoring tool `tools/ShadeAscendanceArmor.java` accepts `<input.bbmodel> <material-reference.png> <output-directory>`. It repaints existing RGB values and writes its result separately for review; it never runs automatically during a resource build. Compile it with Gson on the classpath. The independent preview tool `tools/PreviewAscendanceArmor.java` accepts `<asset-namespace-directory> <preview.png> [before-asset-namespace-directory]`. Its asset directory is the `assets/essence_ascendance` folder containing exported meshes and tier atlases; providing the optional third argument creates the before/after comparison. The preview tool uses standard Java libraries.

The importer checks all named meshes, pivots, rotations, triangulation, finite positions/UVs, mask image dimensions and matching positive mask UV dimensions instead of silently accepting an incompatible export. A clean checkout never depends on the auxiliary folder. Task inputs include the Blockbench source, all 17 external source PNGs, and generator/compositor/palette code. Outputs are limited to the armor mesh and generated tier texture folders, keeping editable PNGs outside the normal generator's output ownership.

Only the runtime resource tree is packaged; `art/` is versioned with the project but is not a resource source directory. Authoring models, the 17 armor source masks, previews and repaint history stay out of the mod jars. Other masks that Minecraft reads at runtime, including Essentium layered item textures and latent ore overlays, remain runtime resources. The resource configuration also excludes `.bbmodel` files and the former armor source-mask path.

Run `:common:clean` once after migrating an existing checkout, before building, to discard cached armor source masks, `models/armor/*.mesh` and `textures/models/armor/generated/*.png` copies. These obsolete paths are neither generator outputs nor configured resource inputs. The old `generateAscendanceHelmet`, `compileAscendanceHelmetGenerator` and `ascendanceHelmetInvariants` task names remain aliases for the full-armor tasks.

To compare in game, hold each Ascendance armor item in your main hand and use `/essence admin item tier set latent` (or `dormant`, `awakened`, `resonant`, `ascendant`, `transcendent`), then equip it and use third-person view. The admin command requires cheats/operator permission and applies the existing equipment tier/soulbinding behavior.

Verification: `:common:ascendanceArmorInvariants` covers the full armor import, canonical palette and all generated slot/tier atlases against the source masks. `:common:texturePixelInvariants` covers shared compositing math; `:common:ascendancePaletteInvariants` covers the shared canonical palette contract. Build both platform jars with `:fabric:remapJar :neoforge:remapJar`. These automated checks do not replace an in-game visual fit and animation check.

## Accent emission

Armor has no floating ornaments, planes, rings, ticks or satellites. The normal pass still uses the combined base/accent atlas and ordinary lighting. Four generated resources, `{helmet,chestplate,leggings,boots}_emission.png`, copy the exact grayscale accent ARGB pixels into the same atlas positions, without resampling, expansion, padding or new paint. The belt and unused atlas space remain transparent, and the renderer skips the belt mesh in the emission pass. Edit the existing 17 source masks and regenerate normally; emission PNGs remain derived runtime resources.

| Tier | Emission alpha (0�255) |
| --- | --- |
| Latent | 0 |
| Dormant | 41 |
| Awakened | 82 |
| Resonant | 122 |
| Ascendant | 163 |
| Transcendent | 204 |

`ArmorEmission` preserves an evenly scaled progression from no emission to a substantially stronger Transcendent endpoint. The endpoint is 204, a 15% reduction from the unlit value of 240. To tune it, change `MAX_ALPHA` in `common/src/main/java/com/mistaboom/essence_ascendance/visual/ArmorEmission.java`; lower tiers scale automatically, with Latent staying at zero. The unlit rendering method is unchanged. Tint still uses the Focus's 3:1 accent/white mix, starting from `EquipmentTierVisuals.armorAccentRgb(tier)`. Base metal and the belt retain normal lighting at every tier.

`ArmorRenderTypes` uses Minecraft's unlit eyes shader with **ordinary alpha blending**, not its additive eye blend. Unlike `entityTranslucentEmissive`, this shader does not call `minecraft_mix_light`, so directional normals cannot darken side-facing sleeve accents. The former armor effect also lacked the Focus's raised base lighting, making equal overlay alpha look much weaker in darkness. Armor now supplies its stronger light contribution entirely through the authored accent mask; it does not raise lighting on the base metal.

Emission uses identical mesh vertices and UVs, `LEQUAL` depth testing, color-only writes, nearest texture sampling and the same `VIEW_OFFSET_Z_LAYERING` as vanilla armor. No inflated shell or extra polygon offset is introduced. Transparent pixels cannot occlude other surfaces, and the combined normally lit atlas remains beneath accent boundaries. The existing vanilla/loader enchantment foil pass is preserved. Minecraft owns shader/texture resource reloads.

`ArmorPresentationMixin` retains one common post-armor hook for Fabric and NeoForge. Its emission model copies the wearer's animated transforms and baby scaling, then enables parts according to the equipped armor slot. Chestplate emission explicitly includes both arms even when the wearer's skin or an armor stand's wooden arms are hidden; copying that hidden-limb visibility was the cause of missing sleeve emission on ordinary armor stands. Leggings emit on their legs only, never on the torso-attached belt. Invisible entities and spectators receive no added pass.

The procedural renderer, motif definitions and attachment visitor have been removed. There are no armor particles, animation queues, flight-space ornaments, extra network messages or gameplay changes. An equipped set adds at most four accent mesh passes, and Latent skips emission entirely. Existing Essence Wings, Fatigue Flight and Vector Boost effects are unchanged.

## Visual verification

Run these from the repository root:

```powershell
.\gradlew.bat :common:ascendanceArmorInvariants :common:armorVisualInvariants :common:texturePixelInvariants :common:ascendancePaletteInvariants
.\gradlew.bat :fabric:remapJar :neoforge:remapJar armorJarInvariants
```

The armor suite checks normal/emission atlas pixels, including hidden RGB, the transparent belt and unused space. It compares emitted mesh vertices/UVs against the ordinary armor pose with hidden wearer limbs, independently rotated arms/legs, crouched torso and baby scaling. Both sleeve meshes must still emit when the parent arms are hidden. `armorVisualInvariants` now runs `ArmorEmissionTest`: exact tier scaling, luminous tint, the actual unlit shader state, normal alpha blending, matching armor depth and color-only writes. `armorJarInvariants` compares all 37 generated armor assets in both production jars byte-for-byte and rejects authoring assets and obsolete ornament classes.

Manual inspection is left to the user. Hold each armor item and run `/essence admin item tier set <tier>` before equipping it. Review all six tiers, first separately and then as a set:

1. Compare daylight and an enclosed dark room. Latent must have no added emission; subsequent tiers must increase steadily, with Transcendent much brighter. Base metal and the belt must stay normally lit.
2. Inspect both sleeves from front, side and rear on a player and an ordinary armor stand with hidden wooden arms. Turn, walk, crouch, swim and fall-fly to check the accent overlay stays aligned with the armor.
3. Enchant a piece, re-equip it, then reload resources with F3+T. Check glint, unchanged mask edges, and the absence of seams, flicker, halos or light showing through solid surfaces.
4. Confirm there are no floating armor effects at any tier. Compare the Transcendent set with no flight, harness only, active thrust, Essence Wings and Vector Boost; only the existing flight effects should float outside the armor.

The automated checks cannot decide the final perceived brightness or glint strength. Those remain visual tuning judgments; no new in-game review is claimed for this revision.

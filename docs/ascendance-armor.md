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

## Emission and magical ornamentation

The normal pass still uses the combined base/accent atlas and ordinary armor lighting. Four additional generated resources, `{helmet,chestplate,leggings,boots}_emission.png`, copy the exact grayscale accent ARGB pixels into the same atlas positions. There is no resampling, expansion, padding, or new paint. Belt and unused atlas space are transparent; the renderer also skips the belt mesh entirely in the emission pass. Edit only the existing 17 source masks, then regenerate normally. The four emission PNGs are derived runtime resources, not new authoring inputs.

| Tier | Accent emission alpha (0–255) | Ornament refinement |
| --- | --- | --- |
| Latent | 0 | One small static fragment at each primary attachment; no arm/thigh ornaments |
| Dormant | 22 | Two opposed floating fragments |
| Awakened | 45 | Four sections establish the primary form; shoulder/thigh forms and two close-view ticks appear |
| Resonant | 67 | A second broken band; slow movement of the gaps |
| Ascendant | 90 | A broader outer band and four sparse ticks |
| Transcendent | 112 | Nearly complete separated bands, two small counter-moving inset planes and luminous fine cores |

Emission uses the same shader and blend/depth-write semantics as `entityTranslucentEmissive`, full-bright packed light `0xF000F0`, and 3:1 accent/white luminous tint as Focus. Its endpoint is the **passive** Transcendent Focus alpha of 112, excluding the active-machine +12. All magic derives its color from `EquipmentTierVisuals.armorAccentRgb(tier)`; the neutral `primaryRgb` is not used. Coplanar emission uses exactly the normal mesh vertices and UVs with the standard depth-tested, color-only emissive blend. `ArmorRenderTypes` adds the same `VIEW_OFFSET_Z_LAYERING` as vanilla armor, so the two passes share their view-depth transform; a generic unadjusted entity overlay would sit behind the armor. There is no inflated shell or polygon offset to create edge halos; the combined normal texture remains under all accent edges. Enchantment foil remains in the original loader armor pipeline.

| Piece | Primary form | Animated attachments and later refinement |
| --- | --- | --- |
| Helmet | Flattened broken crown ellipse above the brow | Head bone; concentric refinement and two inset planes stay above the eyes |
| Chestplate | Frontal lozenge collar | Body bone; paired shoulder lozenges begin at Awakened on their respective arm bones |
| Leggings | Wide, shallow frontal waist lozenge | Belt/body bone; paired long thigh lozenges begin at Awakened on their respective leg bones |
| Boots | Small stabilizer lozenges ahead of each ankle | Corresponding leg bones; broken bands become layered fins with sparse close-view marks |

Each slot supplies a primary form when worn alone. The full set repeats the same gaps, luminous cores and tier color at restrained sizes. Lower-tier arm/thigh omissions keep the initial set quiet. The belt ornament is procedural magic in front of the waist; the belt texture itself never emits.

`ArmorPresentationMixin` adds one common post-armor hook for Fabric and NeoForge. `AscendanceArmorModel.visitAttachments` traverses the actual model cube transforms, so ornaments inherit exactly the mesh's head/torso/limb animation, visibility and baby scaling. The visitor captures immutable frame-local poses before another entity can mutate the shared model. The incoming entity transform supplies crouching, swimming, fall-flying and body rotation. Invisible entities and spectators get no added presentation. Texture resources use Minecraft's normal reload path; no additional pixel or resource cache is introduced.

Broad planes and sparse lines use `ProceduralGeometry`, `ProceduralMotion`, `ProceduralRenderTypes` and the world-tail queue. Physical surfaces establish depth first; planes test depth without writing it and fine lines test/write depth. Lines/cores fade between 12 and 24 blocks, then broad forms fade between 32 and 48 blocks. Animation uses entity age, partial tick, stable entity identity, equipment slot and tier, with no tick particles, gameplay mutations or network messages. The maximum close-view full set is **2,160 ornament vertices in two shared batches**, plus at most four accent mesh passes. It queues one entry per wearer and stores at most nine small pose snapshots; there are no unbounded per-frame geometry collections.

All authored ornament bounds lie in front of the torso. `ArmorVisualStyle` records a conservative reserved rear half-space beginning at torso-local `z=0.12`, ahead of the harness, outward wing fan and thruster columns (including their renderer translation). While any flight visual is active, each animated motif's eight bound corners are transformed back into torso coordinates. A motif that reaches that half-space is omitted for that frame, preserving flight space even when a limb swings backward. This deliberately favors flight readability over a rear-swinging armor detail. Physical armor/accent emission stays unchanged.

## Visual verification

Run these from the repository root:

```powershell
.\gradlew.bat :common:ascendanceArmorInvariants :common:armorVisualInvariants :common:texturePixelInvariants :common:ascendancePaletteInvariants
.\gradlew.bat :fabric:remapJar :neoforge:remapJar armorJarInvariants
```

The armor suite checks all normal and emission pixels (including hidden RGB, the transparent belt and unused atlas space), mesh/motif bone correspondence, visibility, animated placement and baby scaling. The visual suite verifies actual render-state shards against vanilla armor depth layering and Focus emission semantics, then executes the real procedural emitter across all tiers, motifs and sampled motion phases; checks nondecreasing actual vertex counts, deterministic/static behavior, bounds, distance reduction and posed flight clearance; and caps the close-view set at 2,600 vertices. `armorJarInvariants` builds both production jars, compares all 37 generated armor resources byte-for-byte with the runtime sources, and rejects packaged authoring assets.

For manual review, use a disposable creative world with cheats on each loader. Give yourself the four `essence_ascendance:ascendance_*` armor items. Hold each in your main hand and use `/essence admin item tier set <tier>` before equipping it. Test `latent`, `dormant`, `awakened`, `resonant`, `ascendant`, and `transcendent` in that order.

1. For every tier, wear each of the four slots alone, then the complete set. Inspect front, rear and both sides in third person at roughly 3, 12, 24 and 48 blocks (a second player or equipped armor stands help with distance checks). Confirm the crown clears the eyes and the same motif remains recognizable as its bands complete.
2. Compare `/time set noon` with an enclosed unlit room or night. Latent accents must have no added glow; the belt and base metal must remain normally lit at all tiers. Inspect accent boundaries up close against bright sky and dark blocks for seams, flicker or halos.
3. Hold and enchant a piece with `/enchant @s minecraft:unbreaking 1`, then re-equip it. Confirm the normal foil remains visible through the accent blend. Reload resources with F3+T and repeat the close-view check.
4. Walk, sprint, crouch, turn the head/body, swim and fall-fly. Check independent leg gait and torso-anchored waist ornaments. Equip a baby zombie in a protected test enclosure to verify small humanoid scaling. Test invisibility and spectator mode for absent added effects.
5. On the complete Transcendent set, use the existing skill loadout/toggles to compare **no flight**, **Fatigue Flight harness only**, **active Fatigue Flight thrust**, **Essence Wings**, and **Vector Boost**. Inspect from front, side and rear while moving, including backward limb swings. The harness/wing fan/thruster silhouette must stay clear; conflicting limb ornaments may disappear while the flight visual is active.
6. Repeat with several visible equipped entities and mixed-tier sets. Check that fine detail disappears before the broad forms and that no magic shows through the wearer or a nearby solid wall.

Automated geometry and resource checks do not establish perceived brightness, glint strength, motion comfort or the final in-game silhouette. Those remain visual review criteria; client startup alone is not an in-game review of this matrix.

Implementation verification (2026-09-25): the automated suites and both production jar inspections pass. Fabric client startup reached the title screen and loaded a separate review world without an armor mixin error. Visual review was stopped at the user's request before the tier/flight matrix was inspected; the user will perform those inspections. No claim of completed in-game visual validation is made.

# Ascendance shield art

The editable shield lives at `art/source/models/item/ascendance_shield.bbmodel`. It is a held item mesh, so it belongs beside other item sources rather than the equipped armor model. The unified asset pipeline imports it through the `stitched_mesh` entry in `art/asset-manifest.json` during ordinary builds.

## Source and generated resources

The BBModel contains one `Shield` mesh and two complementary grayscale masks: `ascendance_shield_base` and `ascendance_shield_accent`. Both images are 256×256, with a 64×64 UV frame. Geometry, UVs, material assignments, image alpha, and existing painted coverage remain authored data. Retexturing changes the RGB of already painted pixels only. The smooth face and panel shading uses the same directional material treatment as armor, with restrained highlights that preserve the tier accent's saturation.

| Purpose | Location |
| --- | --- |
| Editable geometry and embedded masks | `art/source/models/item/ascendance_shield.bbmodel` |
| Source registration and coordinate mapping | `art/asset-manifest.json` |
| Runtime mesh | `common/build/generated-resources/assets/assets/essence_ascendance/meshes/item/ascendance_shield.eamesh` |
| Six tier textures and emission mask | `common/build/generated-resources/assets/assets/essence_ascendance/textures/item/ascendance_shield/` |
| Hand-authored item display configuration | `common/src/main/resources/assets/essence_ascendance/models/item/ascendance_shield{,_blocking}.json` |

The six normal textures are named `latent.png`, `dormant.png`, `awakened.png`, `resonant.png`, `ascendant.png`, and `transcendent.png`. `MaskedTextureWriter` composes the embedded masks with the same canonical base and accent colors as armor. Latent receives the existing light gray base tint. This produces one complete normally lit surface; complementary masks are never drawn as separate ordinary surfaces, avoiding seams between their pixels.

`emission.png` preserves the exact embedded accent PNG bytes, including all alpha and hidden RGB. No source mask padding, dilation, resampling, or hole filling occurs. The base mask stays only in the source BBModel; it does not need a separate runtime PNG. Generated files remain under `build/`, are ignored by Git, and are regenerated after `clean`. Source BBModels and the manifest stay outside production jars.

## Geometry and rendering

The authored mesh uses origin `(0,0,0)` and rotation `(90,0,0)`. Its manifest maps positions to `(x,-z,y)/16`, keeping the grip at the origin and the plate's front toward negative Z in vanilla shield model coordinates. The runtime then applies vanilla shield rendering's `(1,-1,-1)` scale. Existing vanilla shield and blocking display transforms position the item in either hand, inventories, item frames, and on the ground.

The exporter preserves every triangle and its winding, computes normalized face normals, and normalizes UVs against the masks' 64×64 UV metadata. It validates both masks' image and UV dimensions and accepts only faces assigned to those materials. A private export view binds both masks to the stitched runtime texture; it never rewrites source material assignments.

`AscendanceShieldRenderer` reads the existing bound equipment tier and uses a cached mesh with its generated tier texture. Enchantment glint uses the normal foil buffer. Both loaders invoke the same renderer through their shield item rendering hook, and client resource reloads invalidate the cached mesh.

Accent lighting directly reuses `ArmorEmission` and `ArmorRenderTypes`. The current armor tuning therefore also controls the shield: Latent has no added emission, followed by alpha values 36, 72, 108, 144, and 180. The luminous accent tint, unlit shader, ordinary alpha blending, depth treatment, and color-only writes remain the existing armor settings. The shield does not modify those shared values or add gameplay effects.

## Editing and verification

Save changes in Blockbench, then run:

```powershell
.\gradlew.bat :common:generateAssets
.\gradlew.bat :common:assetPipelineInvariants :common:ascendanceShieldInvariants
.\gradlew.bat assetJarInvariants
```

The pipeline tests check every real shield output pixel against the canonical tint and compositor, exact emission bytes, every authored mesh vertex and UV, normalized normals, unchanged source art, deterministic generation, and failed-import isolation. The packaging task checks all generated resource bytes in both production jars and excludes authoring files.

The optional authoring tool `tools/ShadeAscendanceArmor.java` also accepts a named mesh and masks after its existing three arguments: `<source.bbmodel> <material-reference.png> <output-directory> Shield ascendance_shield_base ascendance_shield_accent`. It writes a separate repaint for review and is not run by ordinary builds. The optional `tools/PreviewAscendanceShield.java` accepts `<asset-namespace-directory> <preview.png>` and renders the generated mesh with all six tier textures. Offline previews show baked shading without runtime emission.

For in-game review, compare all six tiers in daylight and darkness. Inspect both hands while idle and blocking, third-person stance, enchantment glint, inventory appearance, dropped items, and item frames. Check that the grip sits in the hand, the detailed plate faces outward, and texture boundaries show no flicker or gaps. Reload resources with F3+T and confirm the mesh and textures refresh. Existing guard behavior, cooldowns, equipment binding, and tier selection should behave as before.

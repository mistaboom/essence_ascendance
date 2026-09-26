# Armor remodel and visible shading

The author remodeled the chest and both legs and added a base-only Belt. The untouched input is `ascendance_armor.before-shading.bbmodel`. It contains nine meshes and seventeen embedded texture masks. The final source is `../../../source/models/armor/ascendance_armor.bbmodel`, and the editable masks are in `../../../source/textures/armor/ascendance/`.

The Belt equips with the leggings and attaches to the torso/body pivot `(0,0,0)`, independently of both leg bones. It uses the canonical base-metal grey for each tier and has no accent texture. The leggings atlas is 384 by 128 pixels: left leg at x=0, right leg at x=128, belt at x=256.

## Material reference and prompt

Mode: built-in ImageGen edit. Input: `../directional-armor-repaint/directional-white-material-reference.png`. Selected output: `material-reference.png`.

Exact prompt:

> Use case: lighting-weather. Edit this shared grayscale satin-metal material reference for tintable game armor. The previous image is much too pale and flat. Make the shading OBVIOUSLY VISIBLE: bright white upper-left highlight, a broad soft sculpted transition through pearl-gray midtones, and a smooth medium-gray lower-right shadow, roughly grayscale 150 through 255. One single light from upper front-left. This is a full-bleed material swatch, with no object outline, text, grid, scratches, grain, streaks, mottling, or tiny sparkles. Keep it perfectly neutral grayscale. Broad smooth volume, strong enough to remain clearly visible after color tinting and on small 3D surfaces. Avoid diagonal shiny stripes; use a broad rounded highlight and calm continuous falloff. We will transfer this shared material onto the authored surfaces using model normals and exact existing pixel masks.

## Painting method

`tools/ShadeAscendanceArmor.java` applies the shared material to the model's actual surfaces. Its one light is normalized from `(-0.35, -0.56, +0.75)` in Blockbench source coordinates, equivalent to `(-0.35, +0.75, -0.56)` in X/right, Y/up, Z/back display coordinates.

The former paint pass compressed the shading into base values 228-254 and accent values 240-254. This pass uses broader panel gradients, directional highlights along geometric panel boundaries, and soft cast shadows from nearby raised details. Adjacent coplanar triangles are grouped before finding boundaries, so internal triangulation diagonals do not receive painted bevel lines. The material reference is averaged to remove grain before transfer. The final observed range is base 152-255 and accent 170-255, with grayscale standard deviations around 23, so the texture shading remains visible without additional render lighting.

Each original alpha byte and RGB value beneath fully transparent pixels is retained. No painted pixels are added, removed, dilated or resampled. Geometry, UVs, texture metadata, and all other Blockbench values are preserved. Only RGB of already-painted pixels and the embedded PNG sources change. Both embedded and external masks contain the same final pixels. The new source masks were taken from the remodeled model, not from the older external exports.

This is an explicit offline art operation. Normal Gradle builds still only tint and stitch the already-painted masks. To reproduce the repaint with Java 21 and Gson on the classpath, run from the repository root:

```text
java -cp <gson.jar> tools/ShadeAscendanceArmor.java art/history/armor/belt-remodel-shading/ascendance_armor.before-shading.bbmodel art/history/armor/belt-remodel-shading/material-reference.png <scratch-output-directory>
```

The tool writes seventeen PNGs, a Blockbench model, and `shading-verification.json` to that scratch directory. Review them before replacing the canonical source files.

## Preview and checks

`../../../previews/armor/armor-shaded-belt-preview.png` shows all six tiers using the actual runtime meshes and baked PNGs, with no extra lighting applied by the renderer. `../../../previews/armor/armor-shading-comparison.png` compares the input author's colors with the final shaded textures on the same remodeled geometry. These are software previews, not in-game screenshots.

`shading-verification.json` records painted-pixel counts, grayscale ranges and variation, and original alpha hashes for all seventeen masks. The armor invariant checks cover nine EAM1 meshes, UVs, normals, slot isolation, all 24 baked tier atlases, the base-only belt tint, and independent torso/leg animation. Actual in-game appearance has not been visually checked in this pass.

Final verification passed: armor invariants, 131 canonical palette checks, 10,147 texture compositing checks, and both Fabric and NeoForge production builds. Each jar contains nine armor meshes and 24 generated armor textures, with no authoring models or source masks. An independent comparison confirmed all non-image Blockbench fields, every alpha byte, and transparent RGB match the untouched input; every final embedded PNG also matches its external source file byte-for-byte.

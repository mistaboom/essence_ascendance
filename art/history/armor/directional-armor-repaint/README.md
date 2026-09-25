# Directional armor shading

The final 16 masks are saved in `../../Armor Textures/`, embedded in `../../Armor.bbmodel`, and mirrored in the repository's `common/src/main/resources/assets/essence_ascendance/textures/armor/ascendance/source/`. The unchanged input is backed up in `../../Armor.before-directional-shading.bbmodel`. That input includes the author's corrected left-leg accent: 982 painted pixels, with the accidental 32-pixel knee fill removed.

## Material reference

Mode: built-in ImageGen edit. Input: `../smooth-armor-repaint/smooth-white-material-reference.png`. Selected output: `directional-white-material-reference.png`.

Exact prompt:

> Use case: precise-object-edit. Edit this shared neutral grayscale satin-white metal material swatch. Keep the clean smooth full-bleed square texture, no object silhouette, no text, no marks. Add a modest amount of smooth shading and broad volume compared with this very plain reference. Establish ONE soft light from the upper left, with a broad white highlight toward upper-left and a gentle light-gray shadow toward lower-right. No reverse highlights, no conflicting secondary light, no diagonal brush strokes, no scratches, no streaks, no mottling, no sharp bands, no black or dark gray. Smooth neutral grayscale only; retain bright white highlights and light gray midtones for multiplicative color tinting. This is the shared material reference used to recolor existing armor UV masks; preserve a calm, smooth appearance.

## Exact-mask transfer

`DirectionalArmorRepaint.java` transfers only the low-frequency material shading onto the existing painted pixels. All parts use the same upper front-left light, normalized from `(-0.35, +0.75, -0.56)` in X/right, Y/up, Z/back coordinates. Face normals, broad position shading, and shallow soft shadows from nearby details use this same light. The reference is spatially averaged to remove fine grain and streaks. Accents stay closer to white than the base to retain the canonical tier colors' strength after tinting.

Observed final grayscale range: base 228-254, accent 240-254. `pixel-verification.json` records per-mask coverage, unchanged alpha hashes, and grayscale statistics. Verification compared all 16 masks with the corrected input: image dimensions, every alpha byte, and RGB beneath transparent pixels are unchanged. All non-image Blockbench data, including geometry and UVs, are unchanged.

The saved Java utility accepts the input Blockbench file, reference PNG, and output directory in that order, and requires Gson on the Java classpath. It is an offline art operation; normal builds do not repaint textures. Normal builds read the editable external source PNGs, then composite base and accent into one atlas per slot and tier.

## Verification

The armor invariants, canonical palette invariants, shared texture compositing invariants, and Fabric/NeoForge production builds passed. `../../Armor Textures/armor-directional-preview.png` was rendered from the final EAM1 meshes and baked tier PNGs by the saved `DirectionalArmorPreview.java`. It uses the same light direction and is a software preview, not an in-game screenshot. Actual in-game fit and animation have not been visually checked in this pass.

Both production jars contain the expected 8 EAM1 meshes, 24 generated PNGs and 16 source PNGs, byte-identical to the canonical assets, with no obsolete armor paths. A scratch export matched all embedded PNG bytes. A scratch edit to one opaque source pixel changed exactly the corresponding pixel in all six helmet variants with the expected tint, while preserving the edited source PNG, the other 18 atlases, all meshes and the canonical files.

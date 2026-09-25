# Smooth full-armor repaint

Mode: built-in ImageGen for one shared smooth white material reference, followed by deterministic material projection in Java onto the existing painted pixels only.

All 16 source base/accent masks retain their dimensions, every alpha byte and all unpainted RGB values. All non-texture Blockbench data is unchanged, including geometry and UV coordinates. The reference is reduced to a smooth 24x24 shading field and projected through each triangle using 3D position, so shading does not follow or emphasize UV island edges. Texture colors are neutral grayscale with shallow ranges: base 236–255, accent 244–255. This leaves most of the canonical tint intensity intact and avoids dark streaks, grain and baked outlines. Existing Minecraft rendering supplies directional surface lighting.

Edited model: ../../Armor.bbmodel. Repository source: tools/assets/Armor.bbmodel. Original backup: ../../Armor.before-smooth-armor-repaint.bbmodel. Separate masks: ../../Armor Textures/. Pixel metrics and alpha hashes: pixel-verification.json.

Latent now uses a base tint too: #B9BDC4, computed channel-by-channel as 2 × Dormant (#9FA6AF) − Awakened (#858F9A). All six accent colors and the other five base colors are unchanged.

## ImageGen prompt

Use case: stylized-concept. Asset type: shared grayscale material reference for repainting a Minecraft armor texture set. Create a single square, front-on, smoothly shaded satin-white metal material swatch that fills the entire image edge to edge. It is a material shading reference, NOT an object or a UV atlas. Color neutral grayscale only, very high-key white: almost all pixels between RGB 235 and 255. A single extremely broad, soft diffuse highlight centered toward the top; a very shallow smoothly blended shadow toward the bottom. Plain and uniform surface, absolutely no streaks, no directional scratch lines, no grain, no mottling, no speckle, no brushed-metal bands, no diagonal specular streaks, no hard bevel borders, no seams or outlines, no visible objects, no 3D scene, no text. Smooth continuous gradation suitable for multiplicative tinting: keep it bright so copper and gold tints remain colorful and do not become muddy. Flat square crop, opaque background, full-bleed. This one clean white material will be projected onto the original armor masks while their pixel coverage is preserved exactly.

## Validation result

The full armor invariants pass for all eight meshes: every source vertex, triangle and atlas UV, slot isolation, animated pivots, finite surface normals, and all24 tier atlases compared pixel-for-pixel with canonical-tinted source masks. Canonical palette: 131 checks pass. Shared texture compositing: 10,147 checks pass. Clean Fabric and NeoForge production jars both contain exactly eight mesh resources and24 tier textures, with no obsolete head-only texture/class resources. No in-game visual fit or animation session was performed.

Preview: ../../Armor Textures/armor-tier-preview.png, rendered from the actual generated mesh resources and tier atlases. It is a software material preview with illustrative lighting, not a game screenshot.

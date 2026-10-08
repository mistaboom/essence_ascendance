# Essence Ascendance logo

The logo is an ascending faceted gold core inside a broken orbit with six Essence gems. It uses the existing tier metals, tier greys, and dark UI surface. The gems follow the Nexus category order clockwise from the top: Offense, Defense, Mobility, Utility, Vitality, Gathering.

`tools/LogoGenerator.java` is the artwork source. It imports `CanonicalPaletteValues` and `AscendanceUiPalette` directly; geometry is shared between vector and raster exports. There are no random inputs, external images, fonts in the emblem, or third-party dependencies. Shaded gem facets and the background halo are derived from canonical colors.

Run from the repository root with Java 21:

```powershell
.\gradlew.bat generateLogo
```

| Export | Use |
| --- | --- |
| `essence_ascendance_logo.svg` | Scalable vector master |
| `essence_ascendance_logo_1024.png` | High resolution square master |
| `essence_ascendance_logo_512.png` | Recommended project icon for both hosting sites |
| `essence_ascendance_logo_400.png` | Exact 400 × 400 export |
| `essence_ascendance_logo_{32,64,128,256}.png` | Smaller icons and readability checks |
| `../../build/branding/logo-preview.png` | Review sheet with 512, 128, 64, and 32 px artwork at actual size |

All PNGs have an opaque dark background and use four-times supersampling. The generator checks PNG dimensions and keeps the 512 px export below 256 KiB, the limit documented by [Modrinth's project icon API](https://docs.modrinth.com/api/operations/changeprojecticon/). The 400, 512, and 1024 px files satisfy [CurseForge's 400 × 400 minimum](https://support.curseforge.com/support/solutions/articles/9000199552).

Branding is an exception to the runtime art output convention: these portable SVG/PNG exports are kept in Git alongside their generator for use outside Minecraft. Regenerate them after changing the generator or palette; do not edit the exports directly. Compilation and previews stay under `build/`. This task is optional, does not run during ordinary builds, and does not publish the logo or package it into mod jars.

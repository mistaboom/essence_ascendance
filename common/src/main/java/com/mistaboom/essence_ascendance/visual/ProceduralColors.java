package com.mistaboom.essence_ascendance.visual;

import net.minecraft.resources.ResourceLocation;

/** Resolve semantic colors at the composition site; geometry accepts plain RGB values. */
public final class ProceduralColors {
    private ProceduralColors() { }

    public static int essence(ResourceLocation id) { return AscendancePalette.categoryRgb(id); }
    public static int focusMetal(ResourceLocation tier) { return AscendancePalette.tierMetalRgb(tier); }
    public static int focusPrimary(ResourceLocation tier) { return AscendancePalette.tierPrimaryRgb(tier); }

    /** Machine or effect-specific accents are supplied by the caller without changing geometry. */
    public static int accent(int rgb) { return rgb & 0xFFFFFF; }

    /** Preserve the canonical Utility purple and transcendent core until a composition opts in to another hue. */
    public static Colors canonicalWisp() {
        return new Colors(AscendancePalette.UTILITY, AscendancePalette.TRANSCENDENT.metalRgb());
    }

    public record Colors(int shell, int core) { }
}

package com.mistaboom.essence_ascendance.visual.transientfx;

import com.mistaboom.essence_ascendance.visual.AscendancePalette;

/** Stable semantic identities used by compact visual events. */
public enum SemanticVisualColor {
    OFFENSE(AscendancePalette.OFFENSE),
    DEFENSE(AscendancePalette.DEFENSE),
    MOBILITY(AscendancePalette.MOBILITY),
    UTILITY(AscendancePalette.UTILITY),
    VITALITY(AscendancePalette.VITALITY),
    GATHERING(AscendancePalette.GATHERING),
    LATENT(AscendancePalette.LATENT.metalRgb()),
    DORMANT(AscendancePalette.DORMANT.metalRgb()),
    AWAKENED(AscendancePalette.AWAKENED.metalRgb()),
    RESONANT(AscendancePalette.RESONANT.metalRgb()),
    ASCENDANT(AscendancePalette.ASCENDANT.metalRgb()),
    TRANSCENDENT(AscendancePalette.TRANSCENDENT.metalRgb());

    private final int rgb;

    SemanticVisualColor(int rgb) { this.rgb = rgb; }

    public int rgb() { return rgb; }
}

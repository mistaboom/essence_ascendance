package com.mistaboom.essence_ascendance.visual;

/** Dependency-free identity values shared by the game and build-time texture tools. */
public final class CanonicalPaletteValues {
    public static final int OFFENSE = 0xD65368;
    public static final int DEFENSE = 0x5B8FD9;
    public static final int MOBILITY = 0x55C7D6;
    public static final int UTILITY = 0xAC7ADD;
    public static final int VITALITY = 0xE889B5;
    public static final int GATHERING = 0x58B88B;

    // Continue the light-to-dark grey series one step before Dormant: 2 * Dormant - Awakened.
    public static final int LATENT_PRIMARY = 0xB9BDC4;
    public static final int LATENT_METAL = 0xA86E4B;
    public static final int DORMANT_PRIMARY = 0x9FA6AF;
    public static final int DORMANT_METAL = 0xB78650;
    public static final int AWAKENED_PRIMARY = 0x858F9A;
    public static final int AWAKENED_METAL = 0xC99F54;
    public static final int RESONANT_PRIMARY = 0x6B7785;
    public static final int RESONANT_METAL = 0xDFB95F;
    public static final int ASCENDANT_PRIMARY = 0x515E6D;
    public static final int ASCENDANT_METAL = 0xF2CF78;
    public static final int TRANSCENDENT_PRIMARY = 0x394655;
    public static final int TRANSCENDENT_METAL = 0xFFE7AA;

    private CanonicalPaletteValues() { }
}

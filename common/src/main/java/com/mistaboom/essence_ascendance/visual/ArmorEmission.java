package com.mistaboom.essence_ascendance.visual;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;

/** Accent-only emission, independent of environmental and directional lighting. */
public final class ArmorEmission {
    private static final int[] ALPHA = {0, 48, 96, 144, 192, 240};

    private ArmorEmission() { }

    public static int alpha(EquipmentTier tier) { return ALPHA[tier.ordinal()]; }

    /** Keep the Focus's 3:1 accent/white luminous tint. */
    public static int luminousColor(int rgb) {
        return (((rgb >> 16 & 255) * 3 + 255) / 4) << 16
                | (((rgb >> 8 & 255) * 3 + 255) / 4) << 8
                | ((rgb & 255) * 3 + 255) / 4;
    }
}

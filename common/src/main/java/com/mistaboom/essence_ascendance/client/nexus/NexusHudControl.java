package com.mistaboom.essence_ascendance.client.nexus;

/** Shared symbol, eligibility and exclusive hit region for the presentation-only node control. */
public final class NexusHudControl {
    public static final int SIZE = 12;
    private NexusHudControl() { }
    public static boolean visible(boolean progression, boolean purchased, boolean effective) {
        return progression && purchased && effective;
    }
    public static boolean progressionAvailable(net.minecraft.resources.ResourceLocation tier) {
        return com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry.get(tier)
                .map(value -> value.grantsPower()).orElse(false);
    }
    public static String glyph(boolean enabled) { return enabled ? "●" : "○"; }
    public static boolean hit(double x, double y, int right, int bottom) {
        return x >= right - SIZE - 1 && x < right - 1 && y >= bottom - SIZE - 1 && y < bottom - 1;
    }
}

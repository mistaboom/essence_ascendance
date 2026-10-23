package com.mistaboom.essence_ascendance.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/** Packed block/sky light values used by the custom mesh and field emitters. */
public final class ClientLightCompat {
    public static final int FULL_BRIGHT = 0x00F000F0;
    private ClientLightCompat() {}

    public static int packed(Level level, BlockPos pos) {
        //? if >=26.1.2
        /*return (level.getBrightness(LightLayer.BLOCK, pos) << 4) | (level.getBrightness(LightLayer.SKY, pos) << 20);*/
        //? if <26.1.2
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos);
    }
}

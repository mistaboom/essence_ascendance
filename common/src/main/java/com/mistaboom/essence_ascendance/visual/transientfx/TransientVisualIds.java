package com.mistaboom.essence_ascendance.visual.transientfx;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

/** Loader- and side-neutral recipe identities used by dispatchers and client registries. */
public final class TransientVisualIds {
    public static final ResourceLocation WORLD_IMPACT_PULSE = id("impact_pulse");
    public static final ResourceLocation WORLD_GATHERING_SPRITZ = id("gathering_spritz");
    public static final ResourceLocation WORLD_UTILITY_ACKNOWLEDGE = id("utility_acknowledge");
    public static final ResourceLocation WORLD_PROCESSOR_COMPLETE = id("processor_complete");

    public static final ResourceLocation GUI_ANVIL_COMPRESSION = id("anvil_compression");
    public static final ResourceLocation GUI_GRINDSTONE_SWEEP = id("grindstone_sweep");
    public static final ResourceLocation GUI_ENCHANTING_GLYPH = id("enchanting_glyph");
    public static final ResourceLocation GUI_TRADE_ACKNOWLEDGE = id("trade_acknowledge");
    public static final ResourceLocation GUI_PROCESSOR_COMPLETE = id("processor_complete");

    private TransientVisualIds() { }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }
}

package com.mistaboom.essence_ascendance.essence;

import net.minecraft.resources.ResourceLocation;

public record EssenceDefinition(
        ResourceLocation id,
        String displayName,
        EssenceFamily family
) {
}
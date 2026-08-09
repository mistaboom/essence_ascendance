package com.mistaboom.essence_ascendance.tier;

import net.minecraft.resources.ResourceLocation;

public record AscendanceTierDefinition(
        ResourceLocation id,
        String displayName,
        int order
) {
}
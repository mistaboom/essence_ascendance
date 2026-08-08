package com.mistaboom.essence_ascendance.stat;

import net.minecraft.resources.ResourceLocation;

public record StatDefinition(
        ResourceLocation id,
        String displayName,
        StatCategory category,
        StatUnit unit
) {
}
package com.mistaboom.essence_ascendance.stat;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.resources.ResourceLocation;

public record StatDefinition(
        ResourceLocation id,
        String displayName,
        StatCategory category,
        StatUnit unit,
        EssenceDefinition essenceType
) {
}
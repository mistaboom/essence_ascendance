package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Provisional permanent Attunement IDs used for framework/UI testing. Their
 * eventual Infuser sacrifice recipes remain deliberately undefined.
 */
public final class SkillAttunements {

    public static final ResourceLocation DEMOLITION = id("demolition");
    public static final ResourceLocation UNDYING = id("undying");
    public static final ResourceLocation OCEAN_HEART = id("ocean_heart");
    public static final ResourceLocation MAGMATIC = id("magmatic");

    private static final List<ResourceLocation> VALUES = List.of(
            DEMOLITION,
            UNDYING,
            OCEAN_HEART,
            MAGMATIC
    );

    private SkillAttunements() {
    }

    public static List<ResourceLocation> values() {
        return VALUES;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                path
        );
    }
}

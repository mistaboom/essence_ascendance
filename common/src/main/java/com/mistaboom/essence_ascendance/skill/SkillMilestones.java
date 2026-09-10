package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

/** Permanent milestone flags referenced by the curated skill catalog. */
public final class SkillMilestones {

    public static final ResourceLocation SKY_LIMIT = id("sky_limit");
    public static final ResourceLocation HERO_OF_THE_VILLAGE = id("hero_of_the_village");
    public static final ResourceLocation BEST_FRIENDS_FOREVER = id("best_friends_forever");
    public static final ResourceLocation LOCAL_BREWERY = id("local_brewery");
    public static final ResourceLocation ENCHANTER = id("enchanter");
    public static final ResourceLocation BEACON_ACTIVATION = id("beacon_activation");

    private SkillMilestones() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                path
        );
    }
}

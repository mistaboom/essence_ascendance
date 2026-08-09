package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class MilestoneProviders {

    public static final ResourceLocation ADVANCEMENT =
            id(
                    "advancement"
            );

    public static final ResourceLocation INTERNAL =
            id(
                    "internal"
            );


    static {

        MilestoneProviderRegistry.register(
                ADVANCEMENT,
                MilestoneProviders::checkAdvancement
        );

        MilestoneProviderRegistry.register(
                INTERNAL,
                MilestoneProviders::checkInternal
        );
    }


    private MilestoneProviders() {
    }


    private static boolean checkAdvancement(
            ServerPlayer player,
            MilestoneDefinition milestone
    ) {
        ResourceLocation advancementId =
                ResourceLocation.tryParse(
                        milestone.target()
                );

        if (advancementId == null) {
            return false;
        }

        AdvancementHolder advancement =
                player.server
                        .getAdvancements()
                        .get(
                                advancementId
                        );

        if (advancement == null) {
            return false;
        }

        return player
                .getAdvancements()
                .getOrStartProgress(
                        advancement
                )
                .isDone();
    }


    private static boolean checkInternal(
            ServerPlayer player,
            MilestoneDefinition milestone
    ) {
        ResourceLocation milestoneId =
                ResourceLocation.tryParse(
                        milestone.target()
                );

        if (milestoneId == null) {
            return false;
        }

        return EssenceSavedData
                .get(
                        player.server
                )
                .hasCompletedInternalMilestone(
                        player.getUUID(),
                        milestoneId
                );
    }


    private static ResourceLocation id(
            String path
    ) {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                path
        );
    }


    public static void init() {
        // Forces static initialization.
    }
}
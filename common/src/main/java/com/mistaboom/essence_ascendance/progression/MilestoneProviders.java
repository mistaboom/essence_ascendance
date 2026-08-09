package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class MilestoneProviders {

    public static final ResourceLocation ADVANCEMENT =
            id("advancement");

    public static final ResourceLocation INTERNAL =
            id("internal");


    private MilestoneProviders() {
    }


    public static void init() {

        MilestoneProviderRegistry.register(
                ADVANCEMENT,
                MilestoneProviders::evaluateAdvancement
        );


        MilestoneProviderRegistry.register(
                INTERNAL,
                MilestoneProviders::evaluateInternal
        );
    }


    /*
     * ============================================================
     * VANILLA ADVANCEMENT PROVIDER
     * ============================================================
     */

    private static MilestoneCheckResult evaluateAdvancement(
            ServerPlayer player,
            MilestoneDefinition milestone
    ) {

        ResourceLocation advancementId =
                ResourceLocation.tryParse(
                        milestone.target()
                );


        if (advancementId == null) {

            EssenceAscendance.LOGGER.warn(
                    "Milestone '{}' has invalid advancement target '{}'",
                    milestone.id(),
                    milestone.target()
            );

            return MilestoneCheckResult.unresolved();
        }


        var advancement =
                player.server
                        .getAdvancements()
                        .get(
                                advancementId
                        );


        if (advancement == null) {

            EssenceAscendance.LOGGER.warn(
                    "Milestone '{}' references unknown advancement '{}'",
                    milestone.id(),
                    advancementId
            );

            return MilestoneCheckResult.unresolved();
        }


        boolean complete =
                player
                        .getAdvancements()
                        .getOrStartProgress(
                                advancement
                        )
                        .isDone();


        return complete
                ? MilestoneCheckResult.completed()
                : MilestoneCheckResult.incomplete();
    }


    /*
     * ============================================================
     * INTERNAL PROVIDER
     * ============================================================
     */

    private static MilestoneCheckResult evaluateInternal(
            ServerPlayer player,
            MilestoneDefinition milestone
    ) {

        ResourceLocation milestoneId =
                ResourceLocation.tryParse(
                        milestone.target()
                );


        if (milestoneId == null) {

            EssenceAscendance.LOGGER.warn(
                    "Milestone '{}' has invalid internal milestone target '{}'",
                    milestone.id(),
                    milestone.target()
            );

            return MilestoneCheckResult.unresolved();
        }


        boolean complete =
                EssenceSavedData
                        .get(player.server)
                        .hasCompletedInternalMilestone(
                                player.getUUID(),
                                milestoneId
                        );


        return complete
                ? MilestoneCheckResult.completed()
                : MilestoneCheckResult.incomplete();
    }


    private static ResourceLocation id(
            String path
    ) {

        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                path
        );
    }
}
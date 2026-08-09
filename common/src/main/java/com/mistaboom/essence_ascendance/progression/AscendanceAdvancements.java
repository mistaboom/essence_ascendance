package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

public final class AscendanceAdvancements {

    private static final double DEFAULT_DEVELOPED_THRESHOLD =
            0.25D;


    public static final AscendanceAdvancementDefinition DORMANT_TO_AWAKENED =
            register(
                    "dormant_to_awakened",
                    AscendanceTiers.DORMANT.id(),
                    AscendanceTiers.AWAKENED.id(),
                    2L,
                    3,
                    2,
                    MilestoneRequirement.milestone(
                            Milestones.OBTAIN_DIAMONDS
                    )
            );


    public static final AscendanceAdvancementDefinition AWAKENED_TO_RESONANT =
            register(
                    "awakened_to_resonant",
                    AscendanceTiers.AWAKENED.id(),
                    AscendanceTiers.RESONANT.id(),
                    3L,
                    5,
                    3,
                    MilestoneRequirement.milestone(
                            Milestones.OBTAIN_ANCIENT_DEBRIS
                    )
            );


    public static final AscendanceAdvancementDefinition RESONANT_TO_ASCENDANT =
            register(
                    "resonant_to_ascendant",
                    AscendanceTiers.RESONANT.id(),
                    AscendanceTiers.ASCENDANT.id(),
                    5L,
                    8,
                    4,
                    MilestoneRequirement.anyOf(
                            MilestoneRequirement.milestone(
                                    Milestones.DEFEAT_ENDER_DRAGON
                            ),
                            MilestoneRequirement.milestone(
                                    Milestones.DEFEAT_WITHER
                            )
                    )
            );


    public static final AscendanceAdvancementDefinition ASCENDANT_TO_TRANSCENDENT =
            register(
                    "ascendant_to_transcendent",
                    AscendanceTiers.ASCENDANT.id(),
                    AscendanceTiers.TRANSCENDENT.id(),
                    8L,
                    12,
                    6,
                    MilestoneRequirement.allOf(
                            MilestoneRequirement.milestone(
                                    Milestones.DEFEAT_ENDER_DRAGON
                            ),
                            MilestoneRequirement.milestone(
                                    Milestones.DEFEAT_WITHER
                            ),
                            MilestoneRequirement.milestone(
                                    Milestones.DEFEAT_WARDEN
                            )
                    )
            );


    private AscendanceAdvancements() {
    }


    private static AscendanceAdvancementDefinition register(
            String path,
            ResourceLocation fromTierId,
            ResourceLocation toTierId,
            long totalInvestmentMultiplier,
            int minimumDevelopedStats,
            int minimumRepresentedCategories,
            MilestoneRequirement worldRequirement
    ) {
        AscendanceAdvancementDefinition definition =
                new AscendanceAdvancementDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                EssenceAscendance.MOD_ID,
                                path
                        ),
                        fromTierId,
                        toTierId,
                        totalInvestmentMultiplier,
                        minimumDevelopedStats,
                        minimumRepresentedCategories,
                        DEFAULT_DEVELOPED_THRESHOLD,
                        worldRequirement
                );

        return AscendanceAdvancementRegistry.register(
                definition
        );
    }


    public static void init() {
        // Forces static initialization.
    }
}
package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

public final class Milestones {

    public static final MilestoneDefinition OBTAIN_DIAMONDS =
            register(
                    "obtain_diamonds",
                    "Obtain Diamonds",
                    MilestoneProviders.ADVANCEMENT,
                    "minecraft:story/mine_diamond"
            );


    public static final MilestoneDefinition OBTAIN_ANCIENT_DEBRIS =
            register(
                    "obtain_ancient_debris",
                    "Obtain Ancient Debris",
                    MilestoneProviders.ADVANCEMENT,
                    "minecraft:nether/obtain_ancient_debris"
            );


    public static final MilestoneDefinition DEFEAT_ENDER_DRAGON =
            register(
                    "defeat_ender_dragon",
                    "Defeat the Ender Dragon",
                    MilestoneProviders.ADVANCEMENT,
                    "minecraft:end/kill_dragon"
            );


    /*
     * Vanilla does not give us exactly the persistent semantics we
     * want for every boss milestone.
     *
     * These are therefore internal milestones. Future gameplay
     * events will mark them complete.
     */
    public static final MilestoneDefinition DEFEAT_WITHER =
            register(
                    "defeat_wither",
                    "Defeat the Wither",
                    MilestoneProviders.INTERNAL,
                    "essence_ascendance:defeat_wither"
            );


    public static final MilestoneDefinition DEFEAT_WARDEN =
            register(
                    "defeat_warden",
                    "Defeat the Warden",
                    MilestoneProviders.INTERNAL,
                    "essence_ascendance:defeat_warden"
            );


    private Milestones() {
    }


    private static MilestoneDefinition register(
            String path,
            String displayName,
            ResourceLocation providerId,
            String target
    ) {
        MilestoneDefinition milestone =
                new MilestoneDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                EssenceAscendance.MOD_ID,
                                path
                        ),
                        displayName,
                        providerId,
                        target
                );

        return MilestoneRegistry.register(
                milestone
        );
    }


    public static void init() {
        // Forces static initialization.
    }
}
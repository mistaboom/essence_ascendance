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


    /*
     * Skill purchase gates use the same configurable milestone registry and
     * providers as Ascendance tier transitions. These stable definition IDs
     * are what skills reference; servers may replace their display name,
     * provider, or target through milestone_overrides without changing the
     * skill catalog.
     */
    public static final MilestoneDefinition SKY_LIMIT =
            register(
                    "sky_limit",
                    "Sky's the Limit",
                    MilestoneProviders.ADVANCEMENT,
                    "minecraft:end/elytra"
            );


    public static final MilestoneDefinition HERO_OF_THE_VILLAGE =
            register(
                    "hero_of_the_village",
                    "Hero of the Village",
                    MilestoneProviders.ADVANCEMENT,
                    "minecraft:adventure/hero_of_the_village"
            );


    public static final MilestoneDefinition BEST_FRIENDS_FOREVER =
            register(
                    "best_friends_forever",
                    "Best Friends Forever",
                    MilestoneProviders.ADVANCEMENT,
                    "minecraft:husbandry/tame_an_animal"
            );


    public static final MilestoneDefinition LOCAL_BREWERY =
            register(
                    "local_brewery",
                    "Local Brewery",
                    MilestoneProviders.ADVANCEMENT,
                    "minecraft:nether/brew_potion"
            );


    public static final MilestoneDefinition ENCHANTER =
            register(
                    "enchanter",
                    "Enchanter",
                    MilestoneProviders.ADVANCEMENT,
                    "minecraft:story/enchant_item"
            );


    public static final MilestoneDefinition BEACON_ACTIVATION =
            register(
                    "beacon_activation",
                    "Beacon construction or activation",
                    MilestoneProviders.ADVANCEMENT,
                    "minecraft:nether/create_beacon"
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

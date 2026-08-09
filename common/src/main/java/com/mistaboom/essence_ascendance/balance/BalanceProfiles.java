package com.mistaboom.essence_ascendance.balance;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public final class BalanceProfiles {

    /*
     * ============================================================
     * VANILLA
     * ============================================================
     *
     * Designed around ordinary Minecraft progression.
     *
     * Rough economy landmarks:
     *
     * 10,000       ≈ five iron armor sets
     * 100,000      ≈ five diamond armor sets
     * 1,000,000    ≈ five netherite armor sets/upgrades
     *
     * Later tiers become increasingly expensive so that powerful
     * improvements can use diminishing returns without trivializing
     * the vanilla game.
     */

    public static final BalanceProfileDefinition VANILLA =
            register(
                    "vanilla",
                    "Vanilla",
                    10_000L,
                    100_000L,
                    1_000_000L,
                    10_000_000L,
                    100_000_000L
            );


    /*
     * ============================================================
     * VANILLA+
     * ============================================================
     *
     * Intended for packs that expand Minecraft substantially but
     * still retain roughly recognizable vanilla power scaling.
     */

    public static final BalanceProfileDefinition VANILLA_PLUS =
            register(
                    "vanilla_plus",
                    "Vanilla+",
                    25_000L,
                    250_000L,
                    2_500_000L,
                    25_000_000L,
                    250_000_000L
            );


    /*
     * ============================================================
     * MODDED
     * ============================================================
     *
     * Intended for large modpacks with extensive automation,
     * powerful equipment, large resource economies, and much
     * higher endgame power ceilings.
     */

    public static final BalanceProfileDefinition MODDED =
            register(
                    "modded",
                    "Modded",
                    100_000L,
                    1_000_000L,
                    10_000_000L,
                    100_000_000L,
                    1_000_000_000L
            );


    private BalanceProfiles() {
    }


    private static BalanceProfileDefinition register(
            String path,
            String displayName,
            long dormantCap,
            long awakenedCap,
            long resonantCap,
            long ascendantCap,
            long transcendentCap
    ) {
        Map<ResourceLocation, Long> tierCaps =
                Map.of(
                        AscendanceTiers.DORMANT.id(),
                        dormantCap,

                        AscendanceTiers.AWAKENED.id(),
                        awakenedCap,

                        AscendanceTiers.RESONANT.id(),
                        resonantCap,

                        AscendanceTiers.ASCENDANT.id(),
                        ascendantCap,

                        AscendanceTiers.TRANSCENDENT.id(),
                        transcendentCap
                );

        /*
         * No per-stat overrides yet.
         *
         * Issue 6's configuration system will populate overrides
         * where individual stats require different economics.
         */
        Map<
                ResourceLocation,
                Map<ResourceLocation, Long>
                > statOverrides =
                Map.of();

        BalanceProfileDefinition profile =
                new BalanceProfileDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                EssenceAscendance.MOD_ID,
                                path
                        ),
                        displayName,
                        tierCaps,
                        statOverrides
                );

        return BalanceProfileRegistry.register(
                profile
        );
    }


    public static void init() {
        /*
         * Forces static initialization and registration of all
         * built-in balance profiles.
         */
    }
}
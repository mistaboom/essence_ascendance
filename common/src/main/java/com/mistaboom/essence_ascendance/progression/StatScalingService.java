package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.stat.StatScalingMode;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class StatScalingService {

    private StatScalingService() {
    }


    /*
     * ============================================================
     * STAT SCALING
     * ============================================================
     */

    public static StatScalingResult evaluate(
            ServerPlayer player,
            StatDefinition stat
    ) {

        Objects.requireNonNull(
                player,
                "Player cannot be null"
        );


        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(
                                player.getUUID()
                        );


        return evaluate(
                playerData,
                stat
        );
    }


    public static StatScalingResult evaluate(
            PlayerEssenceData playerData,
            StatDefinition stat
    ) {

        Objects.requireNonNull(
                playerData,
                "Player Essence data cannot be null"
        );

        Objects.requireNonNull(
                stat,
                "Stat cannot be null"
        );


        StatInvestmentLimit investmentLimit =
                TierInvestmentPolicy.evaluate(
                        playerData,
                        stat
                );


        AscendanceTierDefinition currentTier =
                playerData.getTier();


        /*
         * CHASSIS stats use current-tier development as their normalized
         * progression value.
         *
         * This mirrors the current-tier saturation model used by armor
         * chassis development and gives future chassis systems one
         * authoritative normalized value to consume.
         *
         * CHASSIS stats intentionally produce no additive bonus.
         */

        if (stat.scalingMode()
                == StatScalingMode.CHASSIS) {

            double progression =
                    currentTierDevelopment(
                            investmentLimit
                    );


            return new StatScalingResult(
                    stat,
                    currentTier,
                    investmentLimit.storedInvestment(),
                    investmentLimit.effectiveInvestment(),
                    investmentLimit.investmentCap(),
                    progression,
                    0.0,
                    0.0,
                    0.0
            );
        }


        List<AscendanceTierDefinition> tiers =
                orderedTiers();


        int currentTierIndex =
                findTierIndex(
                        tiers,
                        currentTier
                );


        if (currentTierIndex < 0) {

            throw new IllegalStateException(
                    "Current tier is not registered: "
                            + currentTier.id()
            );
        }


        /*
         * BONUS stats retain the continuous cross-tier progression curve
         * introduced by Issue 8.
         *
         * Their earned bonus therefore does not drop simply because the
         * player Ascends into a tier with a larger investment cap.
         */

        double progression =
                calculateProgression(
                        stat,
                        investmentLimit.effectiveInvestment(),
                        currentTierIndex,
                        tiers
                );


        double transcendentMaximumBonus =
                EssenceConfigManager
                        .get()
                        .statMaxBonus(
                                stat
                        );


        double currentTierMaximumBonus =
                transcendentMaximumBonus
                        * tierFraction(
                        currentTierIndex,
                        tiers.size()
                );


        double scaledBonus =
                transcendentMaximumBonus
                        * progression;


        return new StatScalingResult(
                stat,
                currentTier,
                investmentLimit.storedInvestment(),
                investmentLimit.effectiveInvestment(),
                investmentLimit.investmentCap(),
                progression,
                currentTierMaximumBonus,
                transcendentMaximumBonus,
                scaledBonus
        );
    }


    /*
     * ============================================================
     * CURRENT-TIER CHASSIS DEVELOPMENT
     * ============================================================
     *
     * CHASSIS progression is intentionally current-tier saturation:
     *
     *     effective investment / current tier cap
     *
     * This is the same general development concept used by the armor
     * chassis, while BONUS stats continue to use the cross-tier curve.
     */

    private static double currentTierDevelopment(
            StatInvestmentLimit limit
    ) {

        if (limit.investmentCap()
                <= 0L) {

            return 0.0;
        }


        return clamp01(
                limit.effectiveInvestment()
                        / (double) limit.investmentCap()
        );
    }


    /*
     * ============================================================
     * CONTINUOUS PROGRESSION CURVE
     * ============================================================
     *
     * Example with five tiers:
     *
     * 0 Essence       -> 0%
     * Dormant cap     -> 20%
     * Awakened cap    -> 40%
     * Resonant cap    -> 60%
     * Ascendant cap   -> 80%
     * Transcendent    -> 100%
     *
     * Interpolation occurs between those points.
     *
     * This intentionally uses all previous tier cap points instead
     * of simply dividing by the player's current tier cap.
     *
     * Therefore Ascending NEVER reduces an already-earned bonus.
     */

    private static double calculateProgression(
            StatDefinition stat,
            long effectiveInvestment,
            int currentTierIndex,
            List<AscendanceTierDefinition> tiers
    ) {

        long previousCap =
                0L;


        double previousFraction =
                0.0;


        for (int i = 0;
             i <= currentTierIndex;
             i++) {

            AscendanceTierDefinition tier =
                    tiers.get(i);


            long currentCap =
                    EssenceConfigManager
                            .get()
                            .balanceProfile()
                            .getInvestmentCap(
                                    tier,
                                    stat
                            );


            if (currentCap < 0L) {

                throw new IllegalStateException(
                        "Negative investment cap for stat "
                                + stat.id()
                                + " at tier "
                                + tier.id()
                );
            }


            if (currentCap < previousCap) {

                throw new IllegalStateException(
                        "Investment caps must not decrease across tiers for stat "
                                + stat.id()
                                + ". "
                                + tier.id()
                                + " has cap "
                                + currentCap
                                + " after previous cap "
                                + previousCap
                );
            }


            double currentFraction =
                    tierFraction(
                            i,
                            tiers.size()
                    );


            /*
             * Zero-cap tiers contribute no progression point.
             *
             * This also leaves room for a future disabled/locked
             * stat implementation.
             */

            if (currentCap == 0L) {
                continue;
            }


            /*
             * Equal positive caps mean the tier can unlock a higher
             * ceiling without requiring additional investment.
             *
             * This is unusual but deterministic and prevents broken
             * interpolation if a pack author deliberately configures
             * equal adjacent caps.
             */

            if (currentCap == previousCap) {

                if (effectiveInvestment
                        >= currentCap) {

                    previousFraction =
                            currentFraction;
                }

                continue;
            }


            if (effectiveInvestment
                    <= currentCap) {

                double segmentProgress =
                        (
                                effectiveInvestment
                                        - previousCap
                        )
                                / (double) (
                                currentCap
                                        - previousCap
                        );


                segmentProgress =
                        clamp01(
                                segmentProgress
                        );


                return lerp(
                        previousFraction,
                        currentFraction,
                        segmentProgress
                );
            }


            previousCap =
                    currentCap;


            previousFraction =
                    currentFraction;
        }


        /*
         * Effective investment is tier-capped, so normally this is
         * only reached when the player exactly fills a configured
         * point or unusual equal caps are present.
         */

        return clamp01(
                previousFraction
        );
    }


    /*
     * ============================================================
     * CATEGORY DEVELOPMENT
     * ============================================================
     *
     * This is deliberately a CURRENT-TIER saturation metric.
     *
     * Example:
     *
     * Defense effective investment: 40,000
     * Defense current capacity:     80,000
     *
     * development = 0.50
     *
     * It may decrease when a player Ascends because their available
     * development space just increased. Stored progression is not
     * lost.
     */

    public static CategoryDevelopment evaluateCategory(
            ServerPlayer player,
            StatCategory category
    ) {

        Objects.requireNonNull(
                player,
                "Player cannot be null"
        );


        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(
                                player.getUUID()
                        );


        return evaluateCategory(
                playerData,
                category
        );
    }


    public static CategoryDevelopment evaluateCategory(
            PlayerEssenceData playerData,
            StatCategory category
    ) {

        Objects.requireNonNull(
                playerData,
                "Player Essence data cannot be null"
        );

        Objects.requireNonNull(
                category,
                "Category cannot be null"
        );


        long storedInvestment =
                0L;


        long effectiveInvestment =
                0L;


        long currentCapacity =
                0L;


        for (StatDefinition stat :
                EssenceStatRegistry.values()) {

            if (stat.category()
                    != category) {

                continue;
            }


            StatInvestmentLimit limit =
                    TierInvestmentPolicy.evaluate(
                            playerData,
                            stat
                    );


            storedInvestment =
                    Math.addExact(
                            storedInvestment,
                            limit.storedInvestment()
                    );


            effectiveInvestment =
                    Math.addExact(
                            effectiveInvestment,
                            limit.effectiveInvestment()
                    );


            currentCapacity =
                    Math.addExact(
                            currentCapacity,
                            limit.investmentCap()
                    );
        }


        double development;

        if (currentCapacity <= 0L) {

            development =
                    0.0;

        } else {

            development =
                    effectiveInvestment
                            / (double) currentCapacity;


            development =
                    clamp01(
                            development
                    );
        }


        return new CategoryDevelopment(
                category,
                storedInvestment,
                effectiveInvestment,
                currentCapacity,
                development
        );
    }


    /*
     * ============================================================
     * TIER CURVE HELPERS
     * ============================================================
     */

    private static List<AscendanceTierDefinition> orderedTiers() {

        List<AscendanceTierDefinition> tiers =
                new ArrayList<>(
                        AscendanceTierRegistry.values()
                );


        tiers.sort(
                Comparator.comparingInt(
                        AscendanceTierDefinition::order
                )
        );


        if (tiers.isEmpty()) {

            throw new IllegalStateException(
                    "No Ascendance tiers are registered"
            );
        }


        return List.copyOf(
                tiers
        );
    }


    private static int findTierIndex(
            List<AscendanceTierDefinition> tiers,
            AscendanceTierDefinition target
    ) {

        for (int i = 0;
             i < tiers.size();
             i++) {

            if (tiers
                    .get(i)
                    .id()
                    .equals(
                            target.id()
                    )) {

                return i;
            }
        }


        return -1;
    }


    private static double tierFraction(
            int tierIndex,
            int tierCount
    ) {

        return (
                tierIndex
                        + 1
        )
                / (double) tierCount;
    }


    private static double lerp(
            double start,
            double end,
            double progress
    ) {

        return start
                + (
                end
                        - start
        )
                * progress;
    }


    private static double clamp01(
            double value
    ) {

        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        value
                )
        );
    }
}
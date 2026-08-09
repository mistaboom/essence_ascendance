package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

public final class AscendanceEngine {

    private AscendanceEngine() {
    }


    /*
     * ============================================================
     * PROGRESS EVALUATION
     * ============================================================
     */

    public static AscendanceEvaluationResult evaluate(
            ServerPlayer player
    ) {

        Objects.requireNonNull(
                player,
                "Player cannot be null"
        );


        EssenceSavedData savedData =
                EssenceSavedData.get(
                        player.server
                );


        PlayerEssenceData playerData =
                savedData.getPlayerData(
                        player.getUUID()
                );


        AscendanceTierDefinition currentTier =
                playerData.getTier();


        /*
         * Ascendance may only move exactly one registered tier
         * forward.
         */

        AscendanceTierDefinition nextTier =
                findImmediateNextTier(
                        currentTier
                );


        if (nextTier == null) {

            return new AscendanceEvaluationResult(
                    AscendanceEvaluationResult.Status.MAX_TIER,
                    currentTier,
                    null,
                    null
            );
        }


        Optional<AscendanceAdvancementDefinition> advancementOptional =
                EssenceConfigManager
                        .get()
                        .getAdvancementForTier(
                                currentTier.id()
                        );


        if (advancementOptional.isEmpty()) {

            EssenceAscendance.LOGGER.error(
                    "No Ascendance advancement configuration exists for tier {}",
                    currentTier.id()
            );


            return configurationError(
                    currentTier,
                    nextTier
            );
        }


        AscendanceAdvancementDefinition advancement =
                advancementOptional.get();


        /*
         * Configuration may not skip tiers.
         */

        if (!advancement
                .fromTierId()
                .equals(
                        currentTier.id()
                )) {

            EssenceAscendance.LOGGER.error(
                    "Ascendance advancement {} has from-tier {}, but was resolved for current tier {}",
                    advancement.id(),
                    advancement.fromTierId(),
                    currentTier.id()
            );


            return configurationError(
                    currentTier,
                    nextTier
            );
        }


        if (!advancement
                .toTierId()
                .equals(
                        nextTier.id()
                )) {

            EssenceAscendance.LOGGER.error(
                    "Ascendance advancement {} attempts transition {} -> {}, but the immediate next registered tier is {}",
                    advancement.id(),
                    currentTier.id(),
                    advancement.toTierId(),
                    nextTier.id()
            );


            return configurationError(
                    currentTier,
                    nextTier
            );
        }


        try {

            AscendanceProgressSnapshot progress =
                    calculateProgress(
                            player,
                            playerData,
                            currentTier,
                            nextTier,
                            advancement
                    );


            return new AscendanceEvaluationResult(
                    AscendanceEvaluationResult.Status.AVAILABLE,
                    currentTier,
                    nextTier,
                    progress
            );


        } catch (RuntimeException exception) {

            EssenceAscendance.LOGGER.error(
                    "Could not evaluate Ascendance progress for player {} at tier {}",
                    player.getUUID(),
                    currentTier.id(),
                    exception
            );


            return configurationError(
                    currentTier,
                    nextTier
            );
        }
    }


    /*
     * ============================================================
     * MANUAL ASCENSION
     * ============================================================
     *
     * Eligibility never automatically changes the player's tier.
     *
     * The player must explicitly request Ascension.
     */

    public static AscendanceAttemptResult ascend(
            ServerPlayer player
    ) {

        Objects.requireNonNull(
                player,
                "Player cannot be null"
        );


        AscendanceEvaluationResult evaluation =
                evaluate(
                        player
                );


        if (evaluation.status()
                == AscendanceEvaluationResult.Status.MAX_TIER) {

            return new AscendanceAttemptResult(
                    AscendanceAttemptResult.Status.MAX_TIER,
                    evaluation
            );
        }


        if (evaluation.status()
                == AscendanceEvaluationResult.Status.CONFIGURATION_ERROR) {

            return new AscendanceAttemptResult(
                    AscendanceAttemptResult.Status.CONFIGURATION_ERROR,
                    evaluation
            );
        }


        AscendanceProgressSnapshot progress =
                evaluation.progress();


        if (!progress.readyToAscend()) {

            return new AscendanceAttemptResult(
                    AscendanceAttemptResult.Status.NOT_READY,
                    evaluation
            );
        }


        /*
         * Re-read the authoritative player tier immediately before
         * mutation.
         *
         * This protects the transaction from ever applying a stale
         * evaluation if some future system modifies tier state.
         */

        EssenceSavedData savedData =
                EssenceSavedData.get(
                        player.server
                );


        AscendanceTierDefinition authoritativeTier =
                savedData.getTier(
                        player.getUUID()
                );


        if (!authoritativeTier
                .id()
                .equals(
                        evaluation.currentTier().id()
                )) {

            EssenceAscendance.LOGGER.warn(
                    "Player {} changed Ascendance tier during advancement evaluation; rejecting stale Ascension attempt",
                    player.getUUID()
            );


            AscendanceEvaluationResult refreshed =
                    evaluate(
                            player
                    );


            return new AscendanceAttemptResult(
                    refreshed.status()
                            == AscendanceEvaluationResult.Status.MAX_TIER
                            ? AscendanceAttemptResult.Status.MAX_TIER
                            : AscendanceAttemptResult.Status.NOT_READY,
                    refreshed
            );
        }


        /*
         * No Essence is consumed by Ascension.
         *
         * Investment represents qualification, not currency paid
         * during the tier transition.
         */

        savedData.setTier(
                player.getUUID(),
                evaluation.nextTier()
        );


        return new AscendanceAttemptResult(
                AscendanceAttemptResult.Status.SUCCESS,
                evaluation
        );
    }


    /*
     * ============================================================
     * DEPTH + BREADTH CALCULATION
     * ============================================================
     */

    private static AscendanceProgressSnapshot calculateProgress(
            ServerPlayer player,
            PlayerEssenceData playerData,
            AscendanceTierDefinition currentTier,
            AscendanceTierDefinition nextTier,
            AscendanceAdvancementDefinition advancement
    ) {

        BalanceProfileDefinition balanceProfile =
                EssenceConfigManager
                        .get()
                        .balanceProfile();


        Long currentTierDefaultCap =
                balanceProfile
                        .defaultTierCaps()
                        .get(
                                currentTier.id()
                        );


        if (currentTierDefaultCap == null
                || currentTierDefaultCap < 0L) {

            throw new IllegalStateException(
                    "No valid default investment cap exists for tier "
                            + currentTier.id()
            );
        }


        long requiredInvestment =
                advancement.getRequiredInvestment(
                        currentTierDefaultCap
                );


        long totalEffectiveInvestment =
                0L;


        int developedStats =
                0;


        EnumSet<StatCategory> representedCategories =
                EnumSet.noneOf(
                        StatCategory.class
                );


        for (StatDefinition stat :
                EssenceStatRegistry.values()) {

            StatInvestmentLimit limit =
                    StatProgressionService
                            .getInvestmentLimit(
                                    playerData,
                                    stat
                            );


            totalEffectiveInvestment =
                    Math.addExact(
                            totalEffectiveInvestment,
                            limit.effectiveInvestment()
                    );


            /*
             * A zero-cap stat has no current development capacity
             * and therefore cannot count toward Breadth.
             */

            if (limit.investmentCap() <= 0L) {
                continue;
            }


            long developedThreshold =
                    advancement.getDevelopedThreshold(
                            limit.investmentCap()
                    );


            if (limit.effectiveInvestment()
                    >= developedThreshold) {

                developedStats++;


                representedCategories.add(
                        stat.category()
                );
            }
        }


        MilestoneProgress worldProgress =
                MilestoneService.evaluate(
                        player,
                        advancement.worldRequirement()
                );


        return new AscendanceProgressSnapshot(
                currentTier.id(),
                nextTier.id(),
                totalEffectiveInvestment,
                requiredInvestment,
                developedStats,
                advancement.minimumDevelopedStats(),
                representedCategories.size(),
                advancement.minimumRepresentedCategories(),
                worldProgress
        );
    }


    /*
     * ============================================================
     * TIER ORDER
     * ============================================================
     */

    private static AscendanceTierDefinition findImmediateNextTier(
            AscendanceTierDefinition currentTier
    ) {

        int wantedOrder =
                currentTier.order() + 1;


        for (AscendanceTierDefinition tier :
                AscendanceTierRegistry.values()) {

            if (tier.order()
                    == wantedOrder) {

                return tier;
            }
        }


        return null;
    }


    private static AscendanceEvaluationResult configurationError(
            AscendanceTierDefinition currentTier,
            AscendanceTierDefinition nextTier
    ) {

        return new AscendanceEvaluationResult(
                AscendanceEvaluationResult.Status.CONFIGURATION_ERROR,
                currentTier,
                nextTier,
                null
        );
    }
}
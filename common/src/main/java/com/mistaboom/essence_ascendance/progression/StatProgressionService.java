package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class StatProgressionService {

    private StatProgressionService() {
    }


    /*
     * ============================================================
     * NORMAL INVESTMENT TRANSACTION
     * ============================================================
     *
     * Authoritative entry point for normal stat investment.
     *
     * Commands, GUI requests, networking, and future gameplay
     * systems must use this service.
     *
     * Administrative/debug mutation commands may deliberately
     * bypass it.
     */

    public static StatInvestmentResult invest(
            ServerPlayer player,
            StatDefinition stat,
            long amount
    ) {

        Objects.requireNonNull(
                player,
                "Player cannot be null"
        );

        Objects.requireNonNull(
                stat,
                "Stat cannot be null"
        );


        EssenceSavedData savedData =
                EssenceSavedData.get(
                        player.server
                );


        PlayerEssenceData playerData =
                savedData.getPlayerData(
                        player.getUUID()
                );


        EssenceDefinition requiredEssence =
                stat.essenceType();


        long availableBefore =
                playerData.getAvailable(
                        requiredEssence
                );


        /*
         * --------------------------------------------------------
         * AMOUNT VALIDATION
         * --------------------------------------------------------
         */

        if (amount <= 0) {

            long invested =
                    playerData.getInvested(
                            stat
                    );


            return result(
                    StatInvestmentResult.Status.INVALID_AMOUNT,
                    stat,
                    amount,
                    availableBefore,
                    invested,
                    -1L
            );
        }


        /*
         * --------------------------------------------------------
         * TIER INVESTMENT POLICY
         * --------------------------------------------------------
         */

        final StatInvestmentLimit limit;

        try {

            limit =
                    TierInvestmentPolicy.evaluate(
                            playerData,
                            stat
                    );

        } catch (RuntimeException exception) {

            EssenceAscendance.LOGGER.error(
                    "Could not resolve investment policy for player {} and stat {}",
                    player.getUUID(),
                    stat.id(),
                    exception
            );


            return result(
                    StatInvestmentResult.Status.CONFIGURATION_ERROR,
                    stat,
                    amount,
                    availableBefore,
                    playerData.getInvested(
                            stat
                    ),
                    -1L
            );
        }


        /*
         * --------------------------------------------------------
         * EXISTING CAP STATE
         * --------------------------------------------------------
         */

        if (limit.state()
                == StatInvestmentLimit.State.OVER_CAP) {

            return result(
                    StatInvestmentResult.Status.OVER_CAP,
                    stat,
                    amount,
                    availableBefore,
                    limit.storedInvestment(),
                    limit.investmentCap()
            );
        }


        if (limit.state()
                == StatInvestmentLimit.State.AT_CAP) {

            return result(
                    StatInvestmentResult.Status.AT_CAP,
                    stat,
                    amount,
                    availableBefore,
                    limit.storedInvestment(),
                    limit.investmentCap()
            );
        }


        /*
         * --------------------------------------------------------
         * NUMERIC SAFETY
         * --------------------------------------------------------
         */

        final long requestedTotal;

        try {

            requestedTotal =
                    Math.addExact(
                            limit.storedInvestment(),
                            amount
                    );

        } catch (ArithmeticException exception) {

            return result(
                    StatInvestmentResult.Status.NUMERIC_OVERFLOW,
                    stat,
                    amount,
                    availableBefore,
                    limit.storedInvestment(),
                    limit.investmentCap()
            );
        }


        /*
         * --------------------------------------------------------
         * TIER CAP ENFORCEMENT
         * --------------------------------------------------------
         *
         * Transactions are atomic.
         *
         * We do NOT automatically partially invest up to the cap.
         */

        if (requestedTotal
                > limit.investmentCap()) {

            return result(
                    StatInvestmentResult.Status.WOULD_EXCEED_CAP,
                    stat,
                    amount,
                    availableBefore,
                    limit.storedInvestment(),
                    limit.investmentCap()
            );
        }

        if (!TierInvestmentPolicy.validTarget(stat, playerData.getTier(),
                com.mistaboom.essence_ascendance.config.EssenceConfigManager.get().balanceProfile(),
                limit.storedInvestment(), requestedTotal)) {
            return result(StatInvestmentResult.Status.INVALID_AMOUNT, stat, amount, availableBefore,
                    limit.storedInvestment(), limit.investmentCap());
        }


        /*
         * --------------------------------------------------------
         * AVAILABLE ESSENCE
         * --------------------------------------------------------
         */

        if (availableBefore < amount) {

            return result(
                    StatInvestmentResult.Status.INSUFFICIENT_ESSENCE,
                    stat,
                    amount,
                    availableBefore,
                    limit.storedInvestment(),
                    limit.investmentCap()
            );
        }


        /*
         * --------------------------------------------------------
         * COMMIT
         * --------------------------------------------------------
         *
         * No mutation occurs before every validation above succeeds.
         */

        final boolean committed;

        try {

            committed =
                    savedData.invest(
                            player.getUUID(),
                            stat,
                            amount
                    );

        } catch (ArithmeticException exception) {

            EssenceAscendance.LOGGER.error(
                    "Unexpected numeric overflow while committing stat investment for player {} and stat {}",
                    player.getUUID(),
                    stat.id(),
                    exception
            );


            return result(
                    StatInvestmentResult.Status.TRANSACTION_FAILED,
                    stat,
                    amount,
                    availableBefore,
                    limit.storedInvestment(),
                    limit.investmentCap()
            );
        }


        if (!committed) {

            EssenceAscendance.LOGGER.warn(
                    "Validated stat investment unexpectedly failed during commit for player {} and stat {}",
                    player.getUUID(),
                    stat.id()
            );


            return result(
                    StatInvestmentResult.Status.TRANSACTION_FAILED,
                    stat,
                    amount,
                    availableBefore,
                    limit.storedInvestment(),
                    limit.investmentCap()
            );
        }


        /*
         * --------------------------------------------------------
         * AUTHORITATIVE POST-TRANSACTION STATE
         * --------------------------------------------------------
         */

        long availableAfter =
                playerData.getAvailable(
                        requiredEssence
                );


        StatInvestmentLimit updatedLimit =
                TierInvestmentPolicy.evaluate(
                        playerData,
                        stat
                );


        return new StatInvestmentResult(
                StatInvestmentResult.Status.SUCCESS,
                stat,
                amount,
                availableBefore,
                availableAfter,
                limit.storedInvestment(),
                updatedLimit.storedInvestment(),
                updatedLimit.investmentCap()
        );
    }


    /*
     * ============================================================
     * INVESTMENT LIMIT QUERY
     * ============================================================
     *
     * Future GUI, progression display, scaling, and advancement
     * calculations can consume the exact same tier policy.
     */

    public static StatInvestmentLimit getInvestmentLimit(
            ServerPlayer player,
            StatDefinition stat
    ) {

        return TierInvestmentPolicy.evaluate(
                player,
                stat
        );
    }


    public static StatInvestmentLimit getInvestmentLimit(
            PlayerEssenceData playerData,
            StatDefinition stat
    ) {

        return TierInvestmentPolicy.evaluate(
                playerData,
                stat
        );
    }


    /*
     * ============================================================
     * CAP QUERY
     * ============================================================
     */

    public static long getInvestmentCap(
            ServerPlayer player,
            StatDefinition stat
    ) {

        return getInvestmentLimit(
                player,
                stat
        ).investmentCap();
    }


    public static long getInvestmentCap(
            PlayerEssenceData playerData,
            StatDefinition stat
    ) {

        return getInvestmentLimit(
                playerData,
                stat
        ).investmentCap();
    }


    /*
     * ============================================================
     * EFFECTIVE INVESTMENT QUERY
     * ============================================================
     */

    public static long getEffectiveInvestment(
            ServerPlayer player,
            StatDefinition stat
    ) {

        return getInvestmentLimit(
                player,
                stat
        ).effectiveInvestment();
    }


    public static long getEffectiveInvestment(
            PlayerEssenceData playerData,
            StatDefinition stat
    ) {

        return getInvestmentLimit(
                playerData,
                stat
        ).effectiveInvestment();
    }


    /*
     * ============================================================
     * RESULT HELPER
     * ============================================================
     */

    private static StatInvestmentResult result(
            StatInvestmentResult.Status status,
            StatDefinition stat,
            long requestedAmount,
            long available,
            long invested,
            long investmentCap
    ) {

        return new StatInvestmentResult(
                status,
                stat,
                requestedAmount,
                available,
                available,
                invested,
                invested,
                investmentCap
        );
    }
}

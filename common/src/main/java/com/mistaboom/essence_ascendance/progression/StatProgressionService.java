package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
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
     * This is the authoritative entry point for normal player stat
     * investment.
     *
     * Commands, GUI requests, and future gameplay systems should use
     * this service rather than modifying PlayerEssenceData directly.
     *
     * Administrative/debug commands such as /essence setstat are
     * intentionally allowed to bypass this service.
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


        long investedBefore =
                playerData.getInvested(
                        stat
                );


        /*
         * --------------------------------------------------------
         * AMOUNT VALIDATION
         * --------------------------------------------------------
         */

        if (amount <= 0) {

            return result(
                    StatInvestmentResult.Status.INVALID_AMOUNT,
                    stat,
                    amount,
                    availableBefore,
                    investedBefore,
                    -1L
            );
        }


        /*
         * --------------------------------------------------------
         * CAP RESOLUTION
         * --------------------------------------------------------
         */

        final long investmentCap;

        try {

            investmentCap =
                    getInvestmentCap(
                            playerData,
                            stat
                    );

        } catch (RuntimeException exception) {

            EssenceAscendance.LOGGER.error(
                    "Could not resolve investment cap for player {} and stat {}",
                    player.getUUID(),
                    stat.id(),
                    exception
            );

            return result(
                    StatInvestmentResult.Status.CONFIGURATION_ERROR,
                    stat,
                    amount,
                    availableBefore,
                    investedBefore,
                    -1L
            );
        }


        /*
         * --------------------------------------------------------
         * EXISTING CAP STATE
         * --------------------------------------------------------
         */

        if (investedBefore > investmentCap) {

            return result(
                    StatInvestmentResult.Status.OVER_CAP,
                    stat,
                    amount,
                    availableBefore,
                    investedBefore,
                    investmentCap
            );
        }


        if (investedBefore == investmentCap) {

            return result(
                    StatInvestmentResult.Status.AT_CAP,
                    stat,
                    amount,
                    availableBefore,
                    investedBefore,
                    investmentCap
            );
        }


        /*
         * --------------------------------------------------------
         * NUMERIC SAFETY
         * --------------------------------------------------------
         */

        final long investedAfterRequested;

        try {

            investedAfterRequested =
                    Math.addExact(
                            investedBefore,
                            amount
                    );

        } catch (ArithmeticException exception) {

            return result(
                    StatInvestmentResult.Status.NUMERIC_OVERFLOW,
                    stat,
                    amount,
                    availableBefore,
                    investedBefore,
                    investmentCap
            );
        }


        /*
         * --------------------------------------------------------
         * CAP VALIDATION
         * --------------------------------------------------------
         *
         * We reject the entire request rather than partially filling
         * the remaining capacity.
         *
         * Example:
         *
         * Current = 9,000
         * Cap     = 10,000
         * Request = 2,000
         *
         * Result:
         * REJECT
         *
         * The player must explicitly request 1,000 or less.
         */

        if (investedAfterRequested > investmentCap) {

            return result(
                    StatInvestmentResult.Status.WOULD_EXCEED_CAP,
                    stat,
                    amount,
                    availableBefore,
                    investedBefore,
                    investmentCap
            );
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
                    investedBefore,
                    investmentCap
            );
        }


        /*
         * --------------------------------------------------------
         * COMMIT
         * --------------------------------------------------------
         *
         * All validation happens before mutation.
         *
         * PlayerEssenceData.invest() performs the actual paired
         * mutation:
         *
         * available Essence decreases
         * invested Essence increases
         *
         * EssenceSavedData then marks the save dirty.
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

            /*
             * This should already have been prevented by the
             * addExact validation above.
             */

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
                    investedBefore,
                    investmentCap
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
                    investedBefore,
                    investmentCap
            );
        }


        /*
         * Read authoritative post-transaction values.
         */

        long availableAfter =
                playerData.getAvailable(
                        requiredEssence
                );


        long investedAfter =
                playerData.getInvested(
                        stat
                );


        return new StatInvestmentResult(
                StatInvestmentResult.Status.SUCCESS,
                stat,
                amount,
                availableBefore,
                availableAfter,
                investedBefore,
                investedAfter,
                investmentCap
        );
    }


    /*
     * ============================================================
     * CAP QUERIES
     * ============================================================
     */

    public static long getInvestmentCap(
            ServerPlayer player,
            StatDefinition stat
    ) {

        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(
                                player.getUUID()
                        );


        return getInvestmentCap(
                playerData,
                stat
        );
    }


    public static long getInvestmentCap(
            PlayerEssenceData playerData,
            StatDefinition stat
    ) {

        return EssenceConfigManager
                .get()
                .balanceProfile()
                .getInvestmentCap(
                        playerData.getTier(),
                        stat
                );
    }


    /*
     * ============================================================
     * EFFECTIVE INVESTMENT
     * ============================================================
     *
     * Issue 4.5:
     *
     * effective investment =
     * min(stored investment, current cap)
     *
     * This method will eventually be consumed by:
     *
     * - stat scaling
     * - Ascendance depth/breadth calculations
     * - GUI display
     * - debug/progression commands
     */

    public static long getEffectiveInvestment(
            ServerPlayer player,
            StatDefinition stat
    ) {

        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(
                                player.getUUID()
                        );


        return getEffectiveInvestment(
                playerData,
                stat
        );
    }


    public static long getEffectiveInvestment(
            PlayerEssenceData playerData,
            StatDefinition stat
    ) {

        long stored =
                playerData.getInvested(
                        stat
                );


        long cap =
                getInvestmentCap(
                        playerData,
                        stat
                );


        return Math.min(
                stored,
                cap
        );
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
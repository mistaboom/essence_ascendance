package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class TierInvestmentPolicy {

    private TierInvestmentPolicy() {
    }


    /*
     * ============================================================
     * PLAYER QUERY
     * ============================================================
     */

    public static StatInvestmentLimit evaluate(
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


    /*
     * ============================================================
     * DATA QUERY
     * ============================================================
     *
     * This is the authoritative implementation of the tier
     * investment-cap rules defined by Issues 4 and 4.5.
     *
     * The selected balance profile determines the cap.
     * Per-stat overrides are resolved by BalanceProfileDefinition.
     *
     * Stored progression is never destroyed simply because it is
     * over the current cap.
     */

    public static StatInvestmentLimit evaluate(
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


        long storedInvestment =
                playerData.getInvested(
                        stat
                );


        long investmentCap =
                EssenceConfigManager
                        .get()
                        .balanceProfile()
                        .getInvestmentCap(
                                playerData.getTier(),
                                stat
                        );


        if (investmentCap < 0) {
            throw new IllegalStateException(
                    "Resolved negative investment cap for stat "
                            + stat.id()
                            + ": "
                            + investmentCap
            );
        }


        /*
         * Issue 4.5:
         *
         * effective investment =
         * min(stored investment, current cap)
         */

        long effectiveInvestment =
                Math.min(
                        storedInvestment,
                        investmentCap
                );


        long remainingCapacity;

        if (storedInvestment >= investmentCap) {

            remainingCapacity =
                    0L;

        } else {

            remainingCapacity =
                    investmentCap
                            - storedInvestment;
        }


        StatInvestmentLimit.State state;

        if (storedInvestment > investmentCap) {

            state =
                    StatInvestmentLimit.State.OVER_CAP;

        } else if (storedInvestment == investmentCap) {

            state =
                    StatInvestmentLimit.State.AT_CAP;

        } else {

            state =
                    StatInvestmentLimit.State.BELOW_CAP;
        }


        return new StatInvestmentLimit(
                stat,
                storedInvestment,
                effectiveInvestment,
                investmentCap,
                remainingCapacity,
                state
        );
    }
}
package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class TierInvestmentPolicy {

    private TierInvestmentPolicy() {
    }

    /** Complete target validation; unchanged and reduced over-cap storage retain the existing demotion policy. */
    public static boolean validTarget(StatDefinition stat, AscendanceTierDefinition tier,
                                      BalanceProfileDefinition profile, long current, long target) {
        if (current < 0 || target < 0) return false;
        if (!tier.grantsPower() && target != current) return false;
        long cap = profile.getInvestmentCap(tier, stat);
        if (cap < 0) throw new IllegalStateException("Negative Bonus cap for " + stat.id());
        if (target > cap) return target <= current;
        if (target == current) return true;
        BonusTrackDefinition resolved = profile.bonusTrack(stat.id());
        if (resolved == null || resolved.purchaseStyle() != BonusTrackDefinition.PurchaseStyle.THRESHOLD) return true;
        return BonusTrackCurve.isSnapInvestment(resolved.checkpoints(), resolved.investmentExponent(),
                resolved.snapPoints(), target, tier.id());
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

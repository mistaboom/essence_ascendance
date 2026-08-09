package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.stat.StatDefinition;

import java.util.Objects;

public record StatInvestmentLimit(
        StatDefinition stat,
        long storedInvestment,
        long effectiveInvestment,
        long investmentCap,
        long remainingCapacity,
        State state
) {

    public StatInvestmentLimit {

        Objects.requireNonNull(
                stat,
                "Stat cannot be null"
        );

        Objects.requireNonNull(
                state,
                "Investment limit state cannot be null"
        );


        if (storedInvestment < 0) {
            throw new IllegalArgumentException(
                    "Stored investment cannot be negative"
            );
        }


        if (effectiveInvestment < 0) {
            throw new IllegalArgumentException(
                    "Effective investment cannot be negative"
            );
        }


        if (investmentCap < 0) {
            throw new IllegalArgumentException(
                    "Investment cap cannot be negative"
            );
        }


        if (remainingCapacity < 0) {
            throw new IllegalArgumentException(
                    "Remaining capacity cannot be negative"
            );
        }


        if (effectiveInvestment > investmentCap) {
            throw new IllegalArgumentException(
                    "Effective investment cannot exceed investment cap"
            );
        }
    }


    public boolean canAcceptMoreInvestment() {
        return state == State.BELOW_CAP;
    }


    public boolean atOrOverCap() {
        return state == State.AT_CAP
                || state == State.OVER_CAP;
    }


    public boolean overCap() {
        return state == State.OVER_CAP;
    }


    public enum State {

        /*
         * Stored investment is below the cap and normal investment
         * may continue.
         */
        BELOW_CAP,

        /*
         * Stored investment exactly equals the cap.
         */
        AT_CAP,

        /*
         * Stored investment exceeds the cap.
         *
         * Issue 4.5 requires the excess to remain stored while only
         * the capped amount remains effective.
         */
        OVER_CAP
    }
}
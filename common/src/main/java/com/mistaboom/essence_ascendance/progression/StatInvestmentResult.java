package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.stat.StatDefinition;

import java.util.Objects;

public record StatInvestmentResult(
        Status status,
        StatDefinition stat,
        long requestedAmount,
        long availableBefore,
        long availableAfter,
        long investedBefore,
        long investedAfter,
        long investmentCap
) {

    public StatInvestmentResult {
        Objects.requireNonNull(
                status,
                "Investment status cannot be null"
        );

        Objects.requireNonNull(
                stat,
                "Stat cannot be null"
        );
    }


    public boolean success() {
        return status == Status.SUCCESS;
    }


    public long remainingCapacityBefore() {

        if (investmentCap < 0) {
            return 0L;
        }

        return Math.max(
                0L,
                investmentCap - investedBefore
        );
    }


    public long remainingCapacityAfter() {

        if (investmentCap < 0) {
            return 0L;
        }

        return Math.max(
                0L,
                investmentCap - investedAfter
        );
    }


    public enum Status {

        /*
         * Transaction completed successfully.
         */
        SUCCESS,

        /*
         * Requested amount was zero or negative.
         */
        INVALID_AMOUNT,

        /*
         * Player does not have enough of the Essence required by
         * this stat.
         */
        INSUFFICIENT_ESSENCE,

        /*
         * Stored investment is exactly at the currently allowed cap.
         */
        AT_CAP,

        /*
         * Stored investment is already above the current cap.
         *
         * This is a valid state under Issue 4.5 and must not cause
         * stored progression to be deleted.
         */
        OVER_CAP,

        /*
         * The requested transaction would push stored investment
         * beyond the currently allowed cap.
         */
        WOULD_EXCEED_CAP,

        /*
         * The requested arithmetic would overflow a long.
         */
        NUMERIC_OVERFLOW,

        /*
         * The active configuration cannot provide a valid cap for
         * this stat/tier combination.
         */
        CONFIGURATION_ERROR,

        /*
         * Validation succeeded but the underlying persistence
         * mutation unexpectedly failed.
         */
        TRANSACTION_FAILED
    }
}
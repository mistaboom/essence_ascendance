package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;

import java.util.Objects;

public record AscendanceEvaluationResult(
        Status status,
        AscendanceTierDefinition currentTier,
        AscendanceTierDefinition nextTier,
        AscendanceProgressSnapshot progress
) {

    public AscendanceEvaluationResult {

        Objects.requireNonNull(
                status,
                "Evaluation status cannot be null"
        );

        Objects.requireNonNull(
                currentTier,
                "Current tier cannot be null"
        );


        if (status == Status.AVAILABLE) {

            Objects.requireNonNull(
                    nextTier,
                    "Available Ascendance evaluation requires a next tier"
            );

            Objects.requireNonNull(
                    progress,
                    "Available Ascendance evaluation requires progress"
            );
        }
    }


    public boolean available() {
        return status == Status.AVAILABLE;
    }


    public enum Status {

        /*
         * A valid immediate next-tier transition exists.
         */
        AVAILABLE,

        /*
         * Player is already at the highest registered tier.
         */
        MAX_TIER,

        /*
         * The configured progression transition cannot be evaluated.
         */
        CONFIGURATION_ERROR
    }
}
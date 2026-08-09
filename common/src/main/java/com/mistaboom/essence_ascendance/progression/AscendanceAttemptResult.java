package com.mistaboom.essence_ascendance.progression;

import java.util.Objects;

public record AscendanceAttemptResult(
        Status status,
        AscendanceEvaluationResult evaluation
) {

    public AscendanceAttemptResult {

        Objects.requireNonNull(
                status,
                "Ascendance attempt status cannot be null"
        );

        Objects.requireNonNull(
                evaluation,
                "Ascendance evaluation cannot be null"
        );
    }


    public boolean success() {
        return status == Status.SUCCESS;
    }


    public enum Status {

        SUCCESS,

        /*
         * A valid transition exists, but one or more Depth,
         * Breadth, or world requirements are incomplete.
         */
        NOT_READY,

        /*
         * No higher registered tier exists.
         */
        MAX_TIER,

        /*
         * Transition/configuration could not be resolved safely.
         */
        CONFIGURATION_ERROR
    }
}
package com.mistaboom.essence_ascendance.attunement;

/** Analytic single-source pacing from the same integrated exposure curve used by the ledger. */
public final class AttunementPacing {
    private AttunementPacing() { }

    public static double rawUnits(AttunementProfile.Rate rate, AttunementProfile.Category category) {
        return category.target() / rate.contributionPerUnit();
    }

    public static double repeatedUnits(AttunementProfile.Rate rate, AttunementProfile.Category category,
                                       AttunementProfile.Policy policy) {
        double required = rawUnits(rate, category) / rate.referenceUnits();
        double low = required, high = required / policy.repetitionFloor();
        // Numeric convergence, not a gameplay count or cap.
        for (int i = 0; i < Long.SIZE; i++) {
            double work = low + (high - low) / 2;
            double credited = work * AttunementSourceHistory.averageEfficiency(1, work, policy.historyWindow(), policy.repetitionFloor());
            if (credited < required) low = work; else high = work;
        }
        return high * rate.referenceUnits();
    }
}

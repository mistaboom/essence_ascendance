package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;

/** Dimensionless purchase budgets; resource supply and tier effort still determine currency. */
public final class ProgressionPricing {
    private ProgressionPricing() {}

    /** Share a chapter's discretionary budget across its progression horizon. */
    public static double purchaseShare() {
        return 1 / Math.sqrt(ProgressionBand.values().length);
    }

    /** Rank purchases subdivide the shared budget; the existing high-authority ceiling remains. */
    public static double skillShare(double utility) {
        return Math.clamp(purchaseShare() * purchaseShare() * Math.sqrt(utility), .02, .25);
    }
}

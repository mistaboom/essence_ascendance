package com.mistaboom.essence_ascendance.attunement;

/** Audit record. finalContribution is normalized billionths of one seal. */
public record AttunementContribution(String actionId, String activityId, String categoryId,
        String sourceSignature, double baseValue, double investmentMultiplier,
        double repetitionMultiplier, double varietyMultiplier, long finalContribution,
        String rejectionReason) {
    public AttunementContribution {
        if (actionId == null || actionId.length() > 256 || activityId == null || activityId.length() > 128
                || categoryId == null || categoryId.length() > 128 || sourceSignature == null || sourceSignature.length() > 256
                || rejectionReason == null || rejectionReason.length() > 256 || !Double.isFinite(baseValue) || baseValue < 0
                || !Double.isFinite(investmentMultiplier) || investmentMultiplier < 1 || investmentMultiplier > 5
                || !Double.isFinite(repetitionMultiplier) || repetitionMultiplier <= 0 || repetitionMultiplier > 1
                || !Double.isFinite(varietyMultiplier) || varietyMultiplier < 1 || varietyMultiplier > 2
                || finalContribution < 0 || finalContribution > AttunementLedger.SCALE)
            throw new IllegalArgumentException("Invalid Attunement contribution");
    }
    public boolean credited() { return finalContribution > 0; }
}

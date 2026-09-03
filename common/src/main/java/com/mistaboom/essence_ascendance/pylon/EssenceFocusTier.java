package com.mistaboom.essence_ascendance.pylon;

/**
 * Essence Focus operational tiers and their machine-performance values. These are deliberately centralized so
 * recipes and final balance can be tuned later without touching machine logic.
 */
public enum EssenceFocusTier {
    DORMANT(
            "dormant",
            "Dormant",
            new EssencePylonContribution(500_000L, 1.0D, 5_000L, 0.25D, 1)
    ),
    AWAKENED(
            "awakened",
            "Awakened",
            new EssencePylonContribution(1_000_000L, 1.5D, 10_000L, 0.40D, 2)
    ),
    RESONANT(
            "resonant",
            "Resonant",
            new EssencePylonContribution(2_000_000L, 2.0D, 20_000L, 0.65D, 3)
    ),
    ASCENDANT(
            "ascendant",
            "Ascendant",
            new EssencePylonContribution(4_000_000L, 3.0D, 40_000L, 0.95D, 4)
    ),
    TRANSCENDENT(
            "transcendent",
            "Transcendent",
            new EssencePylonContribution(8_000_000L, 4.0D, 80_000L, 1.35D, 5)
    );

    /** A placed pylon remains weakly functional before a Focus is installed. */
    public static final EssencePylonContribution EMPTY_PYLON =
            new EssencePylonContribution(250_000L, 0.5D, 2_500L, 0.10D, 0);

    private final String serializedName;
    private final String displayName;
    private final EssencePylonContribution contribution;

    EssenceFocusTier(
            String serializedName,
            String displayName,
            EssencePylonContribution contribution
    ) {
        this.serializedName = serializedName;
        this.displayName = displayName;
        this.contribution = contribution;
    }

    public String serializedName() {
        return serializedName;
    }

    public String displayName() {
        return displayName;
    }

    public EssencePylonContribution contribution() {
        return contribution;
    }
}

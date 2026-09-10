package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Centralized provisional skill cost bands. Costs are always derived from the
 * configured profile's default cap for the skill's required tier.
 */
public enum SkillCostBand {
    FOUNDATION(5),
    ADVANCED(10),
    KEYSTONE(20);

    private static final long PERCENT_DENOMINATOR = 100L;

    private final int percentage;

    SkillCostBand(int percentage) {
        this.percentage = percentage;
    }

    public int percentage() {
        return percentage;
    }

    public String translationKey() {
        return "skill_cost_band.essence_ascendance."
                + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Returns ceil(tierCap * percentage / 100) without multiplying the full
     * tier cap first. This remains safe for every non-negative long cap.
     */
    public long cost(long tierCap) {
        if (tierCap < 0L) {
            throw new IllegalArgumentException("Skill tier cap cannot be negative");
        }

        long whole = Math.multiplyExact(
                tierCap / PERCENT_DENOMINATOR,
                percentage
        );
        long remainderProduct = (tierCap % PERCENT_DENOMINATOR) * percentage;
        long roundedRemainder = remainderProduct == 0L
                ? 0L
                : 1L + ((remainderProduct - 1L) / PERCENT_DENOMINATOR);

        return Math.addExact(whole, roundedRemainder);
    }

    public long cost(
            BalanceProfileDefinition profile,
            ResourceLocation requiredTierId
    ) {
        Objects.requireNonNull(profile, "Balance profile cannot be null");
        Objects.requireNonNull(requiredTierId, "Required tier ID cannot be null");

        AscendanceTierDefinition tier = AscendanceTierRegistry.get(requiredTierId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown Ascendance tier ID: " + requiredTierId
                ));

        return cost(profile.getDefaultInvestmentCap(tier));
    }
}

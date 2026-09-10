package com.mistaboom.essence_ascendance.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Immutable receipt for one permanent skill purchase.
 *
 * <p>The paid Essence ID is stored with the amount instead of being looked up
 * from the current skill definition. This keeps a future refund/audit exact if
 * a skill changes category or its definition is temporarily unavailable.</p>
 */
public record SkillPurchase(
        ResourceLocation essenceId,
        long paidCost
) {

    public SkillPurchase {
        Objects.requireNonNull(
                essenceId,
                "Paid Essence ID cannot be null"
        );

        if (paidCost < 0L) {
            throw new IllegalArgumentException(
                    "Paid skill cost cannot be negative"
            );
        }
    }
}

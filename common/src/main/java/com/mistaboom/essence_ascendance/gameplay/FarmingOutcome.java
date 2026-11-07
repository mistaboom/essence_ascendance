package com.mistaboom.essence_ascendance.gameplay;

import java.util.Objects;
import java.util.UUID;

/** Scalar observations at successful native rural-skill operations; no retained stacks or worlds. */
public sealed interface FarmingOutcome {
    Context context();
    record Context(String actionId, UUID actor, String dimension, long committedTick) {
        public Context {
            text(actionId); Objects.requireNonNull(actor); text(dimension);
            if (committedTick < 0) throw new IllegalArgumentException("Negative farming tick");
        }
    }
    record CropGrowth(Context context, String crop, boolean eligibleCrop, boolean advanced) implements FarmingOutcome {
        public CropGrowth { Objects.requireNonNull(context); text(crop); }
    }
    record PocketReward(Context context, String item, int delivered, boolean swimming) implements FarmingOutcome {
        public PocketReward { Objects.requireNonNull(context); text(item); }
    }
    record TramplePrevented(Context context, boolean matureCrop, boolean farmlandPreserved,
                            boolean cropPreserved) implements FarmingOutcome {
        public TramplePrevented { Objects.requireNonNull(context); }
    }
    private static void text(String value) {
        if (value == null || value.isBlank() || value.length() > 256) throw new IllegalArgumentException("Invalid farming identity");
    }
}

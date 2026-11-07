package com.mistaboom.essence_ascendance.gameplay;

import java.util.Objects;
import java.util.UUID;

/** Successful server harvest and skill reward facts. No mutable stacks or achievement identities. */
public sealed interface GatheringOutcome {
    Context context();
    record Context(String actionId, UUID actor, String dimension, long committedTick) {
        public Context { text(actionId); Objects.requireNonNull(actor); text(dimension); if (committedTick < 0) throw new IllegalArgumentException("Negative gathering tick"); }
    }
    /** Rhythm identity is actor-scoped, recreated by the ordinary skill's reset/deactivation. */
    record Momentum(Context context, String rhythm, double before, double after, double maximumSpeed, boolean eligibleOrigin) implements GatheringOutcome {
        public Momentum { Objects.requireNonNull(context); text(rhythm); fraction(before); fraction(after); if (!Double.isFinite(maximumSpeed) || maximumSpeed < 0) throw new IllegalArgumentException("Invalid momentum maximum"); }
    }
    record Harvest(Context context, String block, boolean generatedOrigin, boolean correctTool, boolean underwater, boolean aquaticBody) implements GatheringOutcome {
        public Harvest { Objects.requireNonNull(context); text(block); }
    }
    /** The selected ore's native loot actually spawned, rather than merely returning a nonempty loot list. */
    record BonusOre(Context context, String ore, int deliveredItems) implements GatheringOutcome {
        public BonusOre { Objects.requireNonNull(context); text(ore); if (deliveredItems < 0) throw new IllegalArgumentException("Negative ore delivery"); }
    }
    private static void text(String value) { if (value == null || value.isBlank() || value.length() > 256) throw new IllegalArgumentException("Invalid gathering identity"); }
    private static void fraction(double value) { if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid rhythm fraction"); }
}

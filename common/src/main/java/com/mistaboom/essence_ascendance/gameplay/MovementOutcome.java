package com.mistaboom.essence_ascendance.gameplay;

import java.util.Objects;
import java.util.UUID;

/** Successful movement checkpoints. Scalar actor/session identity, never a live stack or entity history. */
public sealed interface MovementOutcome {
    Context context();
    record Context(String actionId, UUID actor, String dimension, long committedTick) {
        public Context {
            Objects.requireNonNull(actor); text(actionId); text(dimension);
            if (committedTick < 0) throw new IllegalArgumentException("Negative movement tick");
        }
    }
    record Glide(Context context, String session, int boosts, double spatialPath) implements MovementOutcome {
        public Glide { Objects.requireNonNull(context); text(session); count(boosts); distance(spatialPath); }
    }
    record Sprint(Context context, String session, double horizontalPath, int vaults, boolean protectedByRush) implements MovementOutcome {
        public Sprint { Objects.requireNonNull(context); text(session); count(vaults); distance(horizontalPath); }
    }
    record Surface(Context context, String session, double horizontalPath) implements MovementOutcome {
        public Surface { Objects.requireNonNull(context); text(session); distance(horizontalPath); }
    }
    record Momentum(Context context, double amount, double maximum) implements MovementOutcome {
        public Momentum {
            Objects.requireNonNull(context);
            if (!Double.isFinite(amount)||!Double.isFinite(maximum)||amount<0||maximum<0||amount>maximum)
                throw new IllegalArgumentException("Invalid effective momentum");
        }
    }
    record AirborneHit(Context context, String session, String combatAction) implements MovementOutcome {
        public AirborneHit { Objects.requireNonNull(context); text(session); text(combatAction); }
    }
    private static void text(String value) {
        if(value==null||value.isBlank()||value.length()>256)throw new IllegalArgumentException("Invalid movement identity");
    }
    private static void count(int value) { if(value<0||value>1024)throw new IllegalArgumentException("Invalid movement count"); }
    private static void distance(double value) {
        if(!Double.isFinite(value)||value<0||value>MovementSessionTracker.PATH_CAP)throw new IllegalArgumentException("Invalid movement distance");
    }
}

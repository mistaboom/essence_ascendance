package com.mistaboom.essence_ascendance.gameplay;

import java.util.Objects;
import java.util.UUID;

/** Committed recovery facts. These contain actor-scoped scalars, never entities or mutable stacks. */
public sealed interface SurvivalOutcome {
    Context context();
    record Context(String actionId, UUID actor, String dimension, long committedTick) {
        public Context {
            text(actionId); Objects.requireNonNull(actor); text(dimension);
            if (committedTick < 0) throw new IllegalArgumentException("Negative survival tick");
        }
    }
    record SecondWind(Context context) implements SurvivalOutcome { public SecondWind { Objects.requireNonNull(context); } }
    record SpiritReform(Context context, boolean alive) implements SurvivalOutcome { public SpiritReform { Objects.requireNonNull(context); } }
    record AutomaticMeal(Context context, String encounter, int completedMeals) implements SurvivalOutcome {
        public AutomaticMeal {
            Objects.requireNonNull(context); text(encounter);
            if (completedMeals < 1 || completedMeals > 2) throw new IllegalArgumentException("Invalid encounter meal checkpoint");
        }
    }
    record MirroredEffect(Context context, UUID source, UUID target, boolean sourceWitch, boolean harmful, boolean applied) implements SurvivalOutcome {
        public MirroredEffect { Objects.requireNonNull(context); Objects.requireNonNull(source); Objects.requireNonNull(target); }
    }
    record CeilingDebt(Context context, double before, double after, boolean capacityReduced) implements SurvivalOutcome {
        public CeilingDebt { Objects.requireNonNull(context); nonnegative(before); nonnegative(after); }
    }
    record Adaptation(Context context, String damageType, double previous, double amount, double maximum) implements SurvivalOutcome {
        public Adaptation { Objects.requireNonNull(context); text(damageType); nonnegative(previous); nonnegative(amount); nonnegative(maximum); }
    }
    record Repair(Context context, String food, int amount, double y) implements SurvivalOutcome {
        public Repair { Objects.requireNonNull(context); text(food); if (!Double.isFinite(y)) throw new IllegalArgumentException("Invalid repair altitude"); }
    }
    record Death(Context context) implements SurvivalOutcome { public Death { Objects.requireNonNull(context); } }
    private static void text(String value) {
        if (value == null || value.isBlank() || value.length() > 256) throw new IllegalArgumentException("Invalid survival identity");
    }
    private static void nonnegative(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid survival amount");
    }
}

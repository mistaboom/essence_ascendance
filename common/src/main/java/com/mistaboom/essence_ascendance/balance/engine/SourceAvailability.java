package com.mistaboom.essence_ascendance.balance.engine;

import java.util.List;

/** Per underlying opportunity, independent of population or container history. Optional generation evidence. */
public record SourceAvailability(String underlyingSource, Category category, Scope scope,
                                 List<String> structures, List<String> dimensions, List<String> conditions,
                                 Timer refresh, Timer decay, boolean accessProven, double occurrenceChance,
                                 double expectedPerEvent, List<String> uncertainty) {
    public enum Category { FINITE_SHARED, FINITE_PERSONALIZED, REFRESHABLE, CONDITIONAL_RENEWABLE, ACCESS_LIMITED, UNKNOWN }
    public enum Scope { SHARED, PLAYER, TEAM, CONDITIONAL_PERSONALIZATION }
    public enum Applicability { OFF, ON, CONDITIONAL }
    public record Timer(Applicability applicability, int ticks, List<String> conditions) {
        public Timer {
            java.util.Objects.requireNonNull(applicability);
            if (ticks < 0) throw new IllegalArgumentException("Negative source timer");
            conditions = conditions.stream().sorted().distinct().toList();
        }
    }
    public SourceAvailability {
        java.util.Objects.requireNonNull(category); java.util.Objects.requireNonNull(scope);
        java.util.Objects.requireNonNull(refresh); java.util.Objects.requireNonNull(decay);
        if (underlyingSource == null || underlyingSource.isBlank() || !Double.isFinite(occurrenceChance) || occurrenceChance < 0 || occurrenceChance > 1
                || !Double.isFinite(expectedPerEvent) || expectedPerEvent < 0) throw new IllegalArgumentException("Invalid acquisition availability");
        structures = structures.stream().sorted().distinct().toList();
        dimensions = dimensions.stream().sorted().distinct().toList();
        conditions = conditions.stream().sorted().distinct().toList();
        uncertainty = uncertainty.stream().sorted().distinct().toList();
    }
    public boolean provenRenewable() {
        return category == Category.REFRESHABLE && accessProven && uncertainty.isEmpty();
    }
    /** Reuse normalized context/timers; only this item's per-event estimate changes. */
    public SourceAvailability event(double chance, double count, int unresolved) {
        List<String> predicates = new java.util.ArrayList<>(conditions);
        predicates.add("loot_conditions_unresolved=" + unresolved);
        List<String> unknown = new java.util.ArrayList<>(uncertainty);
        if (unresolved > 0) unknown.add("Unsupported loot condition/function/runtime modification; acquisition advisory only");
        return new SourceAvailability(underlyingSource, category, scope, structures, dimensions, predicates, refresh, decay,
                accessProven && unresolved == 0, chance, count, unknown);
    }
}

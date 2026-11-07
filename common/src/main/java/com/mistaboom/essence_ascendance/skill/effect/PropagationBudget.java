package com.mistaboom.essence_ascendance.skill.effect;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Per-root visited set and hard propagation budget shared by bursts and chains. */
public final class PropagationBudget {
    private final int maximumGeneration;
    private final int maximumTargets;
    private final com.mistaboom.essence_ascendance.gameplay.CombatOutcome.Cause combatCause;
    private final SharedBudget shared;

    private static final class SharedBudget {
        final Set<UUID> visited = new LinkedHashSet<>();
        int affectedTargets;
    }

    public PropagationBudget(int maximumGeneration, int maximumTargets) {
        this(maximumGeneration, maximumTargets, null);
    }

    /** Optional immutable origin; propagation never borrows the owner's currently held weapon. */
    public PropagationBudget(int maximumGeneration, int maximumTargets,
                             com.mistaboom.essence_ascendance.gameplay.CombatOutcome.Cause combatCause) {
        this(maximumGeneration, maximumTargets, combatCause, new SharedBudget());
    }

    private PropagationBudget(int maximumGeneration, int maximumTargets,
                              com.mistaboom.essence_ascendance.gameplay.CombatOutcome.Cause combatCause,
                              SharedBudget shared) {
        this.maximumGeneration = Math.max(0, maximumGeneration);
        this.maximumTargets = Math.max(0, maximumTargets);
        this.combatCause = combatCause;
        this.shared = shared;
    }

    public com.mistaboom.essence_ascendance.gameplay.CombatOutcome.Cause combatCause() { return combatCause; }

    /** Observation-only branch identity. Every view spends the original traversal/target budget. */
    public PropagationBudget withCombatCause(com.mistaboom.essence_ascendance.gameplay.CombatOutcome.Cause cause) {
        return java.util.Objects.equals(combatCause, cause) ? this
                : new PropagationBudget(maximumGeneration, maximumTargets, cause, shared);
    }

    public void seed(UUID entityId) {
        shared.visited.add(entityId);
    }

    public boolean tryVisit(UUID entityId, int generation) {
        if (generation < 0 || generation > maximumGeneration
                || shared.affectedTargets >= maximumTargets || !shared.visited.add(entityId)) return false;
        shared.affectedTargets++;
        return true;
    }

    public boolean canContinue(int generation) {
        return generation >= 0 && generation <= maximumGeneration && shared.affectedTargets < maximumTargets;
    }

    public boolean visited(UUID entityId) {
        return shared.visited.contains(entityId);
    }

    public int affectedTargets() {
        return shared.affectedTargets;
    }

    public Set<UUID> visitedIds() {
        return Collections.unmodifiableSet(shared.visited);
    }
}

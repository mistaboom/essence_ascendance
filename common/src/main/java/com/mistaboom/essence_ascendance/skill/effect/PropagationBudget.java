package com.mistaboom.essence_ascendance.skill.effect;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Per-root visited set and hard propagation budget shared by bursts and chains. */
public final class PropagationBudget {
    private final int maximumGeneration;
    private final int maximumTargets;
    private final Set<UUID> visited = new LinkedHashSet<>();
    private int affectedTargets;

    public PropagationBudget(int maximumGeneration, int maximumTargets) {
        this.maximumGeneration = Math.max(0, maximumGeneration);
        this.maximumTargets = Math.max(0, maximumTargets);
    }

    public void seed(UUID entityId) {
        visited.add(entityId);
    }

    public boolean tryVisit(UUID entityId, int generation) {
        if (generation < 0 || generation > maximumGeneration
                || affectedTargets >= maximumTargets || !visited.add(entityId)) return false;
        affectedTargets++;
        return true;
    }

    public boolean canContinue(int generation) {
        return generation >= 0 && generation <= maximumGeneration && affectedTargets < maximumTargets;
    }

    public boolean visited(UUID entityId) {
        return visited.contains(entityId);
    }

    public int affectedTargets() {
        return affectedTargets;
    }

    public Set<UUID> visitedIds() {
        return Collections.unmodifiableSet(visited);
    }
}

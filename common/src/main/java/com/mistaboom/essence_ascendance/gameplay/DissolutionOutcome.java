package com.mistaboom.essence_ascendance.gameplay;

import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

/** Immutable facts from one committed Crucible transaction, independent of any award. */
public record DissolutionOutcome(String actionId, UUID operator, ResourceLocation dimension,
                                 long machinePosition, int consumedItems, BigInteger creditedUnits,
                                 long committedTick, UUID machineIdentity, Set<String> consumedInputItems,
                                 int positiveCategoryMask, long overworldDayTime) {
    /** Compatibility for consumers whose rule requires no physical-instance or item history. */
    public DissolutionOutcome(String actionId, UUID operator, ResourceLocation dimension,
                              long machinePosition, int consumedItems, BigInteger creditedUnits,
                              long committedTick) {
        this(actionId, operator, dimension, machinePosition, consumedItems, creditedUnits,
                committedTick, null, Set.of(), 0, 0);
    }

    public DissolutionOutcome {
        Objects.requireNonNull(actionId);
        Objects.requireNonNull(operator);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(creditedUnits);
        consumedInputItems = Set.copyOf(consumedInputItems);
        if (actionId.isBlank() || actionId.length() > 256 || consumedItems <= 0 || creditedUnits.signum() <= 0
                || committedTick < 0 || consumedInputItems.size() > 9 || (positiveCategoryMask & ~63) != 0
                || consumedInputItems.stream().anyMatch(id -> id.length() > 256 || ResourceLocation.tryParse(id) == null))
            throw new IllegalArgumentException("A dissolution outcome requires a positive committed transaction");
    }
}

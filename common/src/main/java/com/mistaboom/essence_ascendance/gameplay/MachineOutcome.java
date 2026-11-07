package com.mistaboom.essence_ascendance.gameplay;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Scalar facts from committed operations. Machine instances are distinct from financial pools. */
public sealed interface MachineOutcome {
    UUID actionId();
    UUID actor();
    String dimension();
    long machinePosition();
    UUID machineIdentity();
    long committedTick();

    private static void validate(UUID action, UUID actor, String dimension, UUID machine, long tick) {
        Objects.requireNonNull(action); Objects.requireNonNull(actor); Objects.requireNonNull(machine);
        if (dimension == null || dimension.length() > 128 || ResourceLocation.tryParse(dimension) == null || tick < 0)
            throw new IllegalArgumentException("Invalid committed machine identity");
    }
    record Channel(UUID actionId, UUID actor, String dimension, long machinePosition, UUID machineIdentity,
                   long creditedUnits, long committedTick) implements MachineOutcome {
        public Channel {
            validate(actionId, actor, dimension, machineIdentity, committedTick);
            if (creditedUnits <= 0) throw new IllegalArgumentException("Channel requires positive committed credit");
        }
    }
    enum Result { OTHER, FILLED_ESSENTIUM, WEAPON_UPGRADE, ROD_UPGRADE }
    record Infusion(UUID actionId, UUID actor, String dimension, long machinePosition, UUID machineIdentity,
                    UUID sourceCrucibleIdentity, Result result, String outputItem, int outputCount,
                    int positiveCategoryMask, int finalTier, long committedTick) implements MachineOutcome {
        public Infusion {
            validate(actionId, actor, dimension, machineIdentity, committedTick); Objects.requireNonNull(result);
            if (outputItem == null || outputItem.length() > 128 || ResourceLocation.tryParse(outputItem) == null
                    || outputCount <= 0 || positiveCategoryMask < 0 || positiveCategoryMask > 63 || finalTier < 0 || finalTier > 5
                    || (result == Result.FILLED_ESSENTIUM && positiveCategoryMask == 0))
                throw new IllegalArgumentException("Invalid completed output metadata");
        }
    }
    record Infrastructure(UUID actionId, UUID actor, String dimension, long machinePosition, UUID machineIdentity,
                          boolean activatedPylon, int functioningPylons, int activeSlotLimit,
                          boolean runningCrucible, long committedTick) implements MachineOutcome {
        public Infrastructure {
            validate(actionId, actor, dimension, machineIdentity, committedTick);
            if (functioningPylons < 0 || activeSlotLimit < 0) throw new IllegalArgumentException("Invalid infrastructure capacity");
        }
    }
}

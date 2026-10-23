package com.mistaboom.essence_ascendance.verification;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if >=26.1.2
/*import net.minecraft.util.ProblemReporter;*/
//? if >=26.1.2
/*import net.minecraft.world.level.storage.TagValueInput;*/

/** Strict fixture load through the actual native block-entity persistence API. */
public final class BlockEntityTestNbtCompat {
    private BlockEntityTestNbtCompat() { }

    public static void load(BlockEntity entity, CompoundTag tag, HolderLookup.Provider registries) {
        //? if >=26.1.2
        /*ProblemReporter.Collector problems = new ProblemReporter.Collector();*/
        //? if >=26.1.2
        /*entity.loadWithComponents(TagValueInput.create(problems, registries, tag));*/
        //? if >=26.1.2
        /*if (!problems.isEmpty()) throw new AssertionError("Native block entity decode problems: " + problems.getReport());*/
        //? if <26.1.2
        entity.loadWithComponents(tag, registries);
    }
}

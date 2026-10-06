package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only generation inspection; no growth is invoked. */
@Mixin(SaplingBlock.class)
public interface SaplingGrowerAccess {
    @Accessor("treeGrower") TreeGrower essenceAscendance$treeGrower();
}

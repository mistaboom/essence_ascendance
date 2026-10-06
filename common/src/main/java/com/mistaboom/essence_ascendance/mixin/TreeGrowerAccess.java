package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Optional;

/** Audited native single-sapling/no-flowers branch, with every positive-probability alternative. */
@Mixin(TreeGrower.class)
public interface TreeGrowerAccess {
    @Accessor("tree") Optional<ResourceKey<ConfiguredFeature<?, ?>>> essenceAscendance$tree();
    @Accessor("secondaryTree") Optional<ResourceKey<ConfiguredFeature<?, ?>>> essenceAscendance$secondaryTree();
    @Accessor("secondaryChance") float essenceAscendance$secondaryChance();
}

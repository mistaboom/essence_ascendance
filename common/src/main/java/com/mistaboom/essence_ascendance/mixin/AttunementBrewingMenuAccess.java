package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.BrewingStandMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandMenu.class)
public interface AttunementBrewingMenuAccess {
    @Accessor("brewingStand") Container essenceAscendance$brewingStand();
}

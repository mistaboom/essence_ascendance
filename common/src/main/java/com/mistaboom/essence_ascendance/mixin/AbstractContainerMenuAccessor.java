package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla menu-data registration to reusable menu feature mixins. */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {

    @Invoker("addDataSlot")
    DataSlot essenceAscendance$addDataSlot(DataSlot dataSlot);
}

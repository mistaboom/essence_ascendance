package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reusable client accessor for container GUI origin coordinates. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenPositionAccess {
    @Accessor("leftPos")
    int essenceAscendance$getLeftPos();

    @Accessor("topPos")
    int essenceAscendance$getTopPos();
}

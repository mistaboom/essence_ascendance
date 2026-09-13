package com.mistaboom.essence_ascendance.attunement;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Implemented only on the actual brewing stand; one manually loaded ingredient funds one credited brew. */
public interface AttunementBrewingOwner {
    void essenceAscendance$claimBrewing(ServerPlayer player, ItemStack ingredient, int added);
    void essenceAscendance$invalidateBrewing();
}

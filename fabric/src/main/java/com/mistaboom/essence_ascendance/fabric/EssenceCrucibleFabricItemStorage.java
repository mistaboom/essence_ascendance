package com.mistaboom.essence_ascendance.fabric;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.minecraft.world.item.ItemStack;

/* Fabric Transfer API adapter: one exposed slot, insertion only. */
final class EssenceCrucibleFabricItemStorage extends SingleStackStorage {

    private final EssenceCrucibleBlockEntity crucible;

    EssenceCrucibleFabricItemStorage(EssenceCrucibleBlockEntity crucible) {
        this.crucible = crucible;
    }

    @Override
    protected ItemStack getStack() {
        return crucible.getItem(0);
    }

    @Override
    protected void setStack(ItemStack stack) {
        crucible.setItem(0, stack);
    }

    @Override
    protected boolean canInsert(ItemVariant variant) {
        return EssenceCrucibleBlockEntity.isValidNewInput(
                variant.toStack(1)
        );
    }

    @Override
    protected boolean canExtract(ItemVariant variant) {
        return false;
    }

    @Override
    public boolean supportsExtraction() {
        return false;
    }

    @Override
    protected void onFinalCommit() {
        crucible.setChanged();
    }
}

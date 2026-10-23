package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.attunement.AttunementBrewingOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric's vanilla two-argument container write invalidates manual brewing attribution. */
@Mixin(BaseContainerBlockEntity.class)
public abstract class AttunementBrewingInventorySetMixin {
    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void essenceAscendance$ingredientReplaced(int slot, ItemStack item, CallbackInfo ci) {
        if (slot == 3 && (Object) this instanceof AttunementBrewingOwner owner)
            owner.essenceAscendance$invalidateBrewing();
    }
}

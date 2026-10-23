package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.attunement.AttunementBrewingOwner;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's transactional three-argument write also covers its vanilla two-argument bridge. */
@Mixin(BaseContainerBlockEntity.class)
public abstract class AttunementBrewingTransferMixin {
    @WrapOperation(method = "setItem(ILnet/minecraft/world/item/ItemStack;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/core/NonNullList;set(ILjava/lang/Object;)Ljava/lang/Object;"))
    private Object essenceAscendance$ingredientReplaced(NonNullList<ItemStack> items, int slot,
            Object item, Operation<Object> original) {
        if (slot == 3 && (Object) this instanceof AttunementBrewingOwner owner)
            owner.essenceAscendance$invalidateBrewing();
        return original.call(items, slot, item);
    }
}

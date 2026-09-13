package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.attunement.AttunementBrewingOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Real container writes only; loader simulation APIs do not call these committed mutation points. */
@Mixin(BaseContainerBlockEntity.class)
public abstract class AttunementBrewingInventoryMixin {
    @Inject(method = "removeItem", at = @At("RETURN"))
    private void essenceAscendance$ingredientRemoved(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 3 && !cir.getReturnValue().isEmpty() && (Object) this instanceof AttunementBrewingOwner owner)
            owner.essenceAscendance$invalidateBrewing();
    }
    @Inject(method = "removeItemNoUpdate", at = @At("RETURN"))
    private void essenceAscendance$ingredientTaken(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 3 && !cir.getReturnValue().isEmpty() && (Object) this instanceof AttunementBrewingOwner owner)
            owner.essenceAscendance$invalidateBrewing();
    }
    @Inject(method = "setItem", at = @At("HEAD"))
    private void essenceAscendance$ingredientReplaced(int slot, ItemStack item, CallbackInfo ci) {
        if (slot == 3 && (Object) this instanceof AttunementBrewingOwner owner)
            owner.essenceAscendance$invalidateBrewing();
    }
    @Inject(method = "clearContent", at = @At("HEAD"))
    private void essenceAscendance$ingredientsCleared(CallbackInfo ci) {
        if ((Object) this instanceof AttunementBrewingOwner owner) owner.essenceAscendance$invalidateBrewing();
    }
}

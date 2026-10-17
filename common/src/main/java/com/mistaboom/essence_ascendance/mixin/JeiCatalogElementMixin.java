package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.compat.jei.JeiCatalogOrder;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only JEI's creative-order key changes; all other configured sorting stages remain native. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.ListElement", remap = false)
public abstract class JeiCatalogElementMixin {
    @Unique private int essenceAscendance$catalogIndex;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void essenceAscendance$captureOrder(ITypedIngredient<?> ingredient, int index, CallbackInfo callback) {
        essenceAscendance$catalogIndex = ingredient.getItemStack()
                .map(stack -> JeiCatalogOrder.INSTANCE.createdIndex(stack, index)).orElse(index);
    }

    @Inject(method = "getCreatedIndex", at = @At("RETURN"), cancellable = true, remap = false)
    private void essenceAscendance$creativeOrder(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(essenceAscendance$catalogIndex);
    }
}

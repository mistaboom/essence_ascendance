package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.compat.jei.JeiCatalogOrder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.IngredientFilter", remap = false)
public abstract class JeiCatalogFilterMixin {
    @Shadow(remap = false) public abstract void onIngredientSortOrderConfigChanged();

    @Inject(method = "onIngredientsAdded", at = @At("RETURN"), remap = false)
    private void essenceAscendance$sortNewVariants(CallbackInfo callback) {
        if (JeiCatalogOrder.INSTANCE.takePendingSort()) onIngredientSortOrderConfigChanged();
    }
}

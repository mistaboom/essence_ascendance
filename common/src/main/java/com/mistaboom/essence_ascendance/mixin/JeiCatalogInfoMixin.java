package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.compat.jei.JeiCatalogOrder;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes only JEI's already-resolved ingredient to the optional creative-order tie-breaker. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.ListElementInfo", remap = false)
public abstract class JeiCatalogInfoMixin implements JeiCatalogOrder.IngredientAccess {
    @Shadow(remap = false) public abstract ITypedIngredient<?> getTypedIngredient();

    @Override
    public ItemStack essenceAscendance$catalogStack() {
        return getTypedIngredient().getItemStack().orElse(ItemStack.EMPTY);
    }
}

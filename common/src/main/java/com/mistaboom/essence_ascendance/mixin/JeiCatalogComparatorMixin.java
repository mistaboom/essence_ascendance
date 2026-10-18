package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.compat.jei.JeiCatalogOrder;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;

/** Resolve equal family anchors by physical tier, only within JEI's creative sorting stage. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.IngredientSorterComparators", remap = false)
public abstract class JeiCatalogComparatorMixin {
    @Inject(method = "getCreativeMenuComparator", at = @At("RETURN"), cancellable = true, remap = false)
    private static void essenceAscendance$tierOrder(CallbackInfoReturnable<Comparator<Object>> callback) {
        callback.setReturnValue(JeiCatalogOrder.creativeComparator(callback.getReturnValue(), value ->
                value instanceof JeiCatalogOrder.IngredientAccess access ? access.essenceAscendance$catalogStack() : ItemStack.EMPTY));
    }
}

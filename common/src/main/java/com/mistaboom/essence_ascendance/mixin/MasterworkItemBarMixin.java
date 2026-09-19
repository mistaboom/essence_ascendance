package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Renders active stored Overdurability through the item's native durability-bar surface. */
@Mixin(ItemStack.class)
public abstract class MasterworkItemBarMixin {
    private static final int VANILLA_BAR_WIDTH = 13;

    @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$showOverdurability(CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack)(Object)this;
        if (EquipmentMaintenanceData.overdurability(stack) > 0
                && EquipmentMaintenanceData.overdurabilityCapacity(stack) > 0) cir.setReturnValue(true);
    }

    @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$overdurabilityWidth(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = (ItemStack)(Object)this;
        double capacity = EquipmentMaintenanceData.overdurabilityCapacity(stack);
        double value = Math.min(capacity, EquipmentMaintenanceData.overdurability(stack));
        if (capacity <= 0 || value <= 0) return;
        int width = Math.clamp((int)Math.round(VANILLA_BAR_WIDTH * value / capacity), 1, VANILLA_BAR_WIDTH);
        cir.setReturnValue(width);
    }

    @Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$overdurabilityColor(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = (ItemStack)(Object)this;
        if (EquipmentMaintenanceData.overdurability(stack) > 0
                && EquipmentMaintenanceData.overdurabilityCapacity(stack) > 0) {
            cir.setReturnValue(AscendancePalette.UTILITY);
        }
    }
}

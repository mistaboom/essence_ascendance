package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractContainerMenu.class)
abstract class EquipmentPolicyMenuMixin {
    @WrapMethod(method = "clicked")
    private void essenceAscendance$click(int slot, int button, ClickType type, Player player, Operation<Void> original) {
        TwoHandedEquipmentService.begin(player);
        try { original.call(slot, button, type, player); } finally { TwoHandedEquipmentService.end(player); }
    }
}

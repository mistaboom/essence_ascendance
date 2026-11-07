package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.gameplay.AdministrativeEquipmentMutation;
import net.minecraft.server.commands.LootCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;

/** The two native /loot entity/inventory mutation boundaries; ordinary item pickup stays gameplay. */
@Mixin(LootCommand.class)
abstract class AdministrativeLootCommandMixin {
    @WrapMethod(method = "setSlots")
    private static void essenceAscendance$replace(Entity target, List<ItemStack> items, int start,
                                                 int count, List<ItemStack> changed, Operation<Void> original) {
        try (var scope = AdministrativeEquipmentMutation.begin(List.of(target))) { original.call(target, items, start, count, changed); }
    }

    @WrapOperation(method = "playerGive", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"), require = 1, expect = 1)
    private static boolean essenceAscendance$give(Inventory inventory, ItemStack stack, Operation<Boolean> original) {
        try (var scope = AdministrativeEquipmentMutation.begin(List.of(inventory.player))) { return original.call(inventory, stack); }
    }
}

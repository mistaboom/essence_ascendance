package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.MenuCostModificationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies the committed enchanting-table lapis cost on the logical server. */
@Mixin(ItemStack.class)
public abstract class ItemStackMenuCostMixin {

    @ModifyVariable(
            method = "consume",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int essenceAscendance$modifyEnchantingLapisCost(
            int modifiedCost,
            int originalCost,
            LivingEntity entity
    ) {
        if (!(entity instanceof ServerPlayer player)
                || !(player.containerMenu instanceof EnchantmentMenu menu)
                || menu.getSlot(1).getItem() != (ItemStack) (Object) this) {
            return modifiedCost;
        }

        return MenuCostModificationService.consumeEnchantingLapis(
                player,
                modifiedCost
        );
    }
}

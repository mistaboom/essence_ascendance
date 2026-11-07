package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.gameplay.RodOutcomes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemEntity.class)
public abstract class RodItemTouchMixin {
    @WrapMethod(method="merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V")
    private static void essenceAscendance$committedItemMerge(ItemEntity receiver,ItemStack receiverStack,
                                                            ItemEntity donor,ItemStack donorStack,Operation<Void> original) {
        int receiverBefore=receiver.getItem().getCount(), donorBefore=donor.getItem().getCount();
        original.call(receiver,receiverStack,donor,donorStack);
        RodOutcomes.merged(receiver,donor,receiverBefore,donorBefore);
    }
    @WrapMethod(method="playerTouch")
    private void essenceAscendance$pulledItemRange(Player player,Operation<Void> original) {
        ItemEntity item=(ItemEntity)(Object)this;
        int before=item.getItem().getCount();
        RodOutcomes.itemTouch(item,player);
        original.call(player);
        RodOutcomes.itemPickup(item,player,before);
    }
}

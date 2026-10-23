package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gathering.AnimalFeedingState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Observe the inherited consumption implementation, including specialized animal interactions. */
//? if >=26.1.2
/*@Mixin(net.minecraft.world.entity.Mob.class)*/
//? if <26.1.2
@Mixin(Animal.class)
public abstract class AnimalFeedingConsumeMixin {
    @WrapMethod(method = "usePlayerItem")
    private void essenceAscendance$feed(Player player, InteractionHand hand, ItemStack stack, Operation<Void> original) {
        int before = stack.getCount();
        original.call(player, hand, stack);
        if (!player.level().isClientSide && stack.getCount() < before && (Object)this instanceof AnimalFeedingState animal)
            animal.essenceAscendance$fedAt(player.level().getGameTime());
    }
}

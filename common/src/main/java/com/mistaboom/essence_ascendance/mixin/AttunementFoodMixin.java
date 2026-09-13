package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FoodData.class)
public abstract class AttunementFoodMixin {
    @WrapMethod(method = "tick")
    private void essenceAscendance$consumed(Player player, Operation<Void> original) {
        double before = player instanceof ServerPlayer serverPlayer ? AttunementGameplay.food(serverPlayer) : 0;
        original.call(player);
        if (player instanceof ServerPlayer serverPlayer) AttunementGameplay.hungerConsumed(serverPlayer, before);
    }
}

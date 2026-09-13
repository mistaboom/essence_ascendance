package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class AttunementPlayerMixin {
    @WrapMethod(method = "eat")
    private ItemStack essenceAscendance$food(Level level, ItemStack stack, FoodProperties properties,
                                           Operation<ItemStack> original) {
        if (!((Object) this instanceof ServerPlayer player)) return original.call(level, stack, properties);
        double before = AttunementGameplay.food(player);
        ItemStack source = stack.copy();
        ItemStack result = original.call(level, stack, properties);
        AttunementGameplay.foodRestored(player, before, source);
        return result;
    }
    @WrapMethod(method = "giveExperiencePoints")
    private void essenceAscendance$experience(int amount, Operation<Void> original) {
        if (!((Object) this instanceof ServerPlayer player)) { original.call(amount); return; }
        int before = player.totalExperience;
        original.call(amount);
        AttunementGameplay.experience(player, before);
    }
}

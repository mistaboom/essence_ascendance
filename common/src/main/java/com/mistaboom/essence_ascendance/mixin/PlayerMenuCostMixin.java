package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.MenuCostModificationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies the committed enchanting-table level cost on the logical server. */
@Mixin(Player.class)
public abstract class PlayerMenuCostMixin {

    @ModifyVariable(
            method = "onEnchantmentPerformed",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int essenceAscendance$modifyEnchantingExperienceCost(int cost) {
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(serverPlayer.containerMenu instanceof EnchantmentMenu)) {
            return cost;
        }

        return MenuCostModificationService.consumeEnchantingExperience(
                serverPlayer,
                cost
        );
    }
}

package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fabric's native component-food conversion omits a full-inventory fallback. NeoForge already provides one. */
@Mixin(Player.class)
public abstract class FeastContainerReturnMixin {
    @WrapOperation(method = "eat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean essenceAscendance$returnContainer(Inventory inventory, ItemStack remainder, Operation<Boolean> original) {
        boolean inserted = original.call(inventory, remainder);
        if (!inserted && !remainder.isEmpty() && dev.architectury.platform.Platform.isFabric()
                && (Object) this instanceof ServerPlayer player
                && CommittedSkillService.isEffective(player, SkillIds.FEAST_REFLEX)) {
            var dropped = player.drop(remainder, false);
            if (dropped != null) dropped.setTarget(player.getUUID());
        }
        return inserted;
    }
}

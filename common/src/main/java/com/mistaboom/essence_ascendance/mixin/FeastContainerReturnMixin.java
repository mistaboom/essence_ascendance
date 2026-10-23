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
//? if >=26.1.2
/*@Mixin(ServerPlayer.class)*/
//? if <26.1.2
@Mixin(Player.class)
public abstract class FeastContainerReturnMixin {
    //? if >=26.1.2 {
    /*
    @WrapOperation(method = "handleExtraItemsCreatedOnUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private net.minecraft.world.entity.item.ItemEntity essenceAscendance$returnContainer(ServerPlayer player, ItemStack remainder,
            boolean random, Operation<net.minecraft.world.entity.item.ItemEntity> original) {
        var dropped = original.call(player, remainder, random);
        if (dropped != null && CommittedSkillService.isEffective(player, SkillIds.FEAST_REFLEX)) dropped.setTarget(player.getUUID());
        return dropped;
    }
    *///?} else {
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
    //?}

}

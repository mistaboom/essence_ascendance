package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService;
import com.mistaboom.essence_ascendance.gathering.RodHookAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Establish cast identity and deterministic motion BEFORE native tracking/spawn packet construction. */
@Mixin(FishingRodItem.class)
public abstract class FishingRodSpawnMixin {
    //? if >=26.1.2 {
    /*
    @WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectile(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private net.minecraft.world.entity.projectile.Projectile essenceAscendance$initializeCast(
            net.minecraft.world.entity.projectile.Projectile entity, net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.item.ItemStack rod, Operation<net.minecraft.world.entity.projectile.Projectile> original,
            Level useLevel, Player owner, InteractionHand hand) {
        if (owner instanceof ServerPlayer player && EquipmentFishingRodService.isAscendanceRod(rod) && entity instanceof RodHookAccess hook) {
            com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.track(player, com.mistaboom.essence_ascendance.config.EssenceConfigManager.skillEffects().posture().movement());
            hook.essenceAscendance$initializeRod(hand, rod, EquipmentFishingRodService.context(player, rod));
        }
        var result = original.call(entity, level, rod);
        if (level.getEntity(entity.getUUID()) != entity) entity.discard();
        return result;
    }
    *///?} else {
    @WrapOperation(method="use",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean essenceAscendance$initializeCast(Level level, Entity entity, Operation<Boolean> original,
            Level useLevel, Player owner, InteractionHand hand) {
        if(owner instanceof ServerPlayer player && EquipmentFishingRodService.isAscendanceRod(owner.getItemInHand(hand))
                && entity instanceof RodHookAccess hook) {
            com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.track(player,com.mistaboom.essence_ascendance.config.EssenceConfigManager.skillEffects().posture().movement());
            hook.essenceAscendance$initializeRod(hand,owner.getItemInHand(hand),EquipmentFishingRodService.context(player,owner.getItemInHand(hand)));
            boolean added=original.call(level,entity); if(!added)entity.discard(); return added;
        }
        return original.call(level,entity);
    }
    //?}

}

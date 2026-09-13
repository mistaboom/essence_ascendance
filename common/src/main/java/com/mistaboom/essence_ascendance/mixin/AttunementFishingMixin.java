package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FishingHook.class)
public abstract class AttunementFishingMixin {
    @WrapOperation(method = "retrieve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean essenceAscendance$landedCatch(Level level, Entity entity, Operation<Boolean> original) {
        boolean spawned = original.call(level, entity);
        FishingHook hook = (FishingHook) (Object) this;
        if (spawned && entity instanceof ItemEntity item && hook.getPlayerOwner() instanceof ServerPlayer player)
            AttunementGameplay.award(player, "catch:" + hook.getUUID(), "catch_fish",
                    AttunementGameplay.itemSignature(item.getItem()), AttunementGameplay.value(item.getItem()));
        return spawned;
    }
}

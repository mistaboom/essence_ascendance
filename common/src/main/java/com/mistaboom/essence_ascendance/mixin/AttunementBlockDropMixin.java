package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Observe the committed spawn shared by direct and captured block-drop pipelines. */
@Mixin(ServerLevel.class)
public abstract class AttunementBlockDropMixin {
    @WrapMethod(method = "addFreshEntity")
    private boolean essenceAscendance$spawnedHarvest(Entity entity, Operation<Boolean> original) {
        boolean spawned = original.call(entity);
        AttunementGameplay.spawnedHarvest(entity, spawned);
        return spawned;
    }
}

package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.guard.GuardLifecycle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class GuardLifecycleMixin {
    @WrapMethod(method = "startUsingItem")
    private void essenceAscendance$guardStarted(InteractionHand hand, Operation<Void> original) {
        LivingEntity holder = (LivingEntity)(Object)this;
        boolean alreadyUsing = holder.isUsingItem();
        original.call(hand);
        if (!alreadyUsing && holder instanceof ServerPlayer player) GuardLifecycle.started(player);
    }
    @Inject(method = "stopUsingItem", at = @At("HEAD"), require = 1)
    private void essenceAscendance$guardStopped(CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayer player) GuardLifecycle.forget(player);
    }
}

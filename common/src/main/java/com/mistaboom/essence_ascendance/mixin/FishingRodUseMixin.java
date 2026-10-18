package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve native block/entity priority, then latch one cast or reel per item-use press. */
@Mixin(Minecraft.class)
public abstract class FishingRodUseMixin {
    @Shadow public LocalPlayer player;
    @Unique private boolean essenceAscendance$rodPressLatched;
    @Inject(method="tick",at=@At("HEAD"))
    private void essenceAscendance$rodReleaseLatch(CallbackInfo ci) {
        if(!((Minecraft)(Object)this).options.keyUse.isDown())essenceAscendance$rodPressLatched=false;
    }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$rodPress(CallbackInfo ci) {
        if (player != null && essenceAscendance$rodPressLatched
                && (EquipmentFishingRodService.isAscendanceRod(player.getMainHandItem())
                || EquipmentFishingRodService.isAscendanceRod(player.getOffhandItem()))) ci.cancel();
    }
    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult essenceAscendance$rodAfterInteraction(MultiPlayerGameMode mode, Player user,
            InteractionHand hand, Operation<InteractionResult> original) {
        if (EquipmentFishingRodService.isAscendanceRod(user.getItemInHand(hand))) essenceAscendance$rodPressLatched = true;
        return original.call(mode, user, hand);
    }
}

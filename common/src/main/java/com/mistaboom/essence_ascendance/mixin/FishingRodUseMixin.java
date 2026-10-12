package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService;
import com.mistaboom.essence_ascendance.gathering.RodHookAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Route an Ascendance rod press through the ordinary native item-use packet. A hooked boat,
 * animal or interactive anchor must not consume the reeling press as boarding/feeding/block use. */
@Mixin(Minecraft.class)
public abstract class FishingRodUseMixin {
    @Shadow public LocalPlayer player;
    @Shadow public MultiPlayerGameMode gameMode;
    @Shadow private int rightClickDelay;
    @Unique private boolean essenceAscendance$rodPressLatched;
    @Inject(method="tick",at=@At("HEAD"))
    private void essenceAscendance$rodReleaseLatch(CallbackInfo ci) {
        if(!((Minecraft)(Object)this).options.keyUse.isDown())essenceAscendance$rodPressLatched=false;
    }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$rodPress(CallbackInfo ci) {
        if (player == null || gameMode == null || player.isSpectator() || player.isUsingItem()) return;
        InteractionHand hand = null;
        if (player.fishing instanceof RodHookAccess hook && hook.essenceAscendance$rodHand() != 0)
            hand = hook.essenceAscendance$rodHand() == 1 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        else if (EquipmentFishingRodService.isAscendanceRod(player.getMainHandItem())) hand = InteractionHand.MAIN_HAND;
        else if (player.getMainHandItem().isEmpty() && EquipmentFishingRodService.isAscendanceRod(player.getOffhandItem())) hand = InteractionHand.OFF_HAND;
        if (hand == null || !EquipmentFishingRodService.isAscendanceRod(player.getItemInHand(hand))) return;
        if(essenceAscendance$rodPressLatched){ci.cancel();return;}
        essenceAscendance$rodPressLatched=true;
        rightClickDelay = 4; // Native input repeat delay; active use latches this single press.
        var result = gameMode.useItem(player, hand);
        if (result.shouldSwing()) player.swing(hand);
        ci.cancel();
    }
}

package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.WeaponActionRuntime;
import com.mistaboom.essence_ascendance.network.WeaponActionInputPayload;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Own combat presses while giving native block/entity use priority over a right-click stab. */
@Mixin(value = Minecraft.class, priority = 1100)
abstract class WeaponActionInputMixin {
    @Shadow public LocalPlayer player;
    @Unique private boolean essenceAscendance$attackLatched, essenceAscendance$useLatched;
    @Inject(method = "tick", at = @At("HEAD"))
    private void essenceAscendance$release(CallbackInfo ci) {
        var client = (Minecraft)(Object)this;
        if (!client.options.keyAttack.isDown()) essenceAscendance$attackLatched = false;
        if (!client.options.keyUse.isDown()) essenceAscendance$useLatched = false;
    }
    @Unique private boolean essenceAscendance$ownsInput() {
        return player != null && WeaponActionRuntime.requiresActionRuntime(player.getMainHandItem());
    }
    @Unique private void essenceAscendance$press(boolean stab) {
        if (((Minecraft)(Object)this).getConnection() != null && NetworkManager.canServerReceive(WeaponActionInputPayload.TYPE))
            NetworkManager.sendToServer(new WeaponActionInputPayload(stab,++essenceAscendance$sequence,
                    player.level().getGameTime(),com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.clientDecision(player).revision()));
    }
    @Unique private long essenceAscendance$sequence;
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
    private boolean essenceAscendance$pressConsumed(KeyMapping key, Operation<Boolean> original) {
        boolean pressed = original.call(key);
        if (pressed) {
            var options = ((Minecraft)(Object)this).options;
            // Release and press can both arrive between ticks. The native click queue keeps
            // each press, whereas polling isDown alone loses that intervening release.
            if (key == options.keyAttack) essenceAscendance$attackLatched = false;
            if (key == options.keyUse) essenceAscendance$useLatched = false;
        }
        return pressed;
    }
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$attack(CallbackInfoReturnable<Boolean> cir) {
        if (!essenceAscendance$ownsInput()) return;
        if (!essenceAscendance$attackLatched) { essenceAscendance$attackLatched = true; essenceAscendance$press(false); }
        cir.setReturnValue(false);
    }
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$mining(boolean held, CallbackInfo ci) {
        if (essenceAscendance$ownsInput()) {
            var mode = ((Minecraft)(Object)this).gameMode;
            if (mode != null) mode.stopDestroyBlock();
            ci.cancel();
        }
    }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$use(CallbackInfo ci) {
        if (essenceAscendance$ownsInput() && essenceAscendance$useLatched) ci.cancel();
    }
    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult essenceAscendance$stabAfterInteraction(MultiPlayerGameMode mode, Player user,
            InteractionHand hand, Operation<InteractionResult> original) {
        if (hand != InteractionHand.MAIN_HAND || !essenceAscendance$ownsInput()) return original.call(mode, user, hand);
        if (!essenceAscendance$useLatched) { essenceAscendance$useLatched = true; essenceAscendance$press(true); }
        // FAIL ends native block use; here CONSUME ends the hand loop without an attack-animation packet.
        return InteractionResult.CONSUME;
    }
}

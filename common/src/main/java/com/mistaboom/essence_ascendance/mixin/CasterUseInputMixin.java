package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.item.AscendanceCasterItem;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native useItem rejects cooldowns before Item.use, so latch a rejected press here too. */
@Mixin(MultiPlayerGameMode.class)
abstract class CasterUseInputMixin {
    @Shadow private GameType localPlayerMode;

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$latchRejectedPress(Player player, InteractionHand hand,
                                                       CallbackInfoReturnable<InteractionResult> cir) {
        var stack = player.getItemInHand(hand);
        if (localPlayerMode != GameType.SPECTATOR && stack.getItem() instanceof AscendanceCasterItem
                && player.getCooldowns().isOnCooldown(stack.getItem())) {
            // No shot, animation or packet is generated. Releasing the button clears vanilla use state.
            player.startUsingItem(hand);
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }
}

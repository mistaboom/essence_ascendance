package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.projectile.ProjectileInterceptionService;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;

/** After native thread dispatch; discriminate right-click animations from actual ordinary melee input. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ProjectileUseInputMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleAnimate", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void essenceAscendance$attackBeforeSwingReset(ServerboundSwingPacket packet, CallbackInfo ci) {
        // This invocation follows native thread dispatch. ServerPlayer.swing resets attack strength
        // after its inherited animation, so RETURN would reject every air swing as unready.
        ProjectileInterceptionService.swing(player, packet.getHand());
    }

    @WrapOperation(method = "handlePlayerAction", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayerGameMode;handleBlockBreakAction(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V"))
    private void essenceAscendance$attackBeforeMining(ServerPlayerGameMode gameMode, BlockPos pos,
            ServerboundPlayerActionPacket.Action action, Direction face, int height, int sequence, Operation<Void> original) {
        if (ProjectileInterceptionService.startBlockAttack(player, action)) {
            // Preserve vanilla sequence acknowledgment after this call and undo client block-break prediction.
            // Packet positions are client supplied: correcting a prediction must not load a remote chunk.
            if (player.canInteractWithBlock(pos, 1.0) && player.level().hasChunkAt(pos))
                player.connection.send(new ClientboundBlockUpdatePacket(player.level(), pos));
            return;
        }
        original.call(gameMode, pos, action, face, height, sequence);
    }
    @Inject(method = "handleUseItem", at = @At("RETURN"))
    private void essenceAscendance$use(ServerboundUseItemPacket packet, CallbackInfo ci) { ProjectileInterceptionService.used(player); }
    @Inject(method = "handleUseItemOn", at = @At("RETURN"))
    private void essenceAscendance$useOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) { ProjectileInterceptionService.used(player); }
    @Inject(method = "handlePlayerAction", at = @At("RETURN"))
    private void essenceAscendance$blockAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                || packet.getAction() == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
                || packet.getAction() == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK)
            ProjectileInterceptionService.used(player);
    }
    @Inject(method = "handleInteract", at = @At("RETURN"))
    private void essenceAscendance$interaction(ServerboundInteractPacket packet, CallbackInfo ci) {
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override public void onInteraction(InteractionHand hand) { ProjectileInterceptionService.used(player); }
            @Override public void onInteraction(InteractionHand hand, Vec3 position) { ProjectileInterceptionService.used(player); }
            @Override public void onAttack() { }
        });
    }
}

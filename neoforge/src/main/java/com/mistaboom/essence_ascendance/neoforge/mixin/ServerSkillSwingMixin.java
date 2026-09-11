package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerSkillSwingMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleAnimate", at = @At("RETURN"))
    private void essenceAscendance$authoritativeSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        // RETURN is after vanilla's server-thread dispatch and animation handling.
        EquipmentDamageService.onServerSkillSwing(player, packet.getHand());
    }
}

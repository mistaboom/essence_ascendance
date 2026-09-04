package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes soulbound Ascendance artifacts before vanilla may either destroy
 * Curse-of-Vanishing stacks or create death-drop ItemEntities. The common
 * recovery service persists and restores them to their bound owner.
 */
@Mixin(Player.class)
public abstract class PlayerSoulboundMixin {

    @Inject(method = "destroyVanishingCursedItems", at = @At("HEAD"))
    private void essenceAscendance$captureSoulboundBeforeVanishing(CallbackInfo ci) {
        essenceAscendance$captureSoulbound();
    }

    @Inject(method = "dropEquipment", at = @At("HEAD"))
    private void essenceAscendance$captureSoulboundBeforeDeathDrops(CallbackInfo ci) {
        essenceAscendance$captureSoulbound();
    }

    private void essenceAscendance$captureSoulbound() {
        if ((Object) this instanceof ServerPlayer serverPlayer) {
            SoulboundEquipmentService.captureBeforeDeathDrops(serverPlayer);
        }
    }
}

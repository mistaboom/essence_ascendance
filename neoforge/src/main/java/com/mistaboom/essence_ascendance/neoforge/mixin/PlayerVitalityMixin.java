package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentVitalityService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerVitalityMixin {

    @ModifyVariable(
            method = "causeFoodExhaustion",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float essenceAscendance$modifyFoodExhaustion(
            float exhaustion
    ) {
        if ((Object) this instanceof ServerPlayer player) {
            return EquipmentVitalityService.modifyFoodExhaustion(
                    player,
                    exhaustion
            );
        }

        return exhaustion;
    }
}

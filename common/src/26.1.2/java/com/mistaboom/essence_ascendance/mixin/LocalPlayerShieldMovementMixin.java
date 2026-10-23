package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.client.EquipmentTooltipClientState;
import com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.skill.effect.GuardMobilityController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.1 computes use slowdown through UseEffects and a single local-player speed helper. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerShieldMovementMixin {
    @ModifyReturnValue(method = "itemUseSpeedMultiplier", at = @At("RETURN"), require = 1)
    private float essenceAscendance$useSpeed(float vanilla) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (EquipmentFishingRodService.isAscendanceRod(player.getUseItem())) return 1;
        if (!EquipmentShieldService.isGuarding(player)) return vanilla;
        return GuardMobilityController.movementMultiplier(player, vanilla,
                EquipmentTooltipClientState.guardedMovementPercent(player.getUseItem()));
    }

    @ModifyReturnValue(method = "isSlowDueToUsingItem", at = @At("RETURN"), require = 1)
    private boolean essenceAscendance$guardSprint(boolean vanilla) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        return vanilla && !GuardMobilityController.sprintAllowed(player);
    }
}

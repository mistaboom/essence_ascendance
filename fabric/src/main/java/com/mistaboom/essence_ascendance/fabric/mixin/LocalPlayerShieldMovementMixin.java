package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.client.EquipmentTooltipClientState;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.ShieldMath;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

/** Replace only vanilla's two item-use input multipliers, after sneak/input processing. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerShieldMovementMixin {
    @ModifyConstant(method = "aiStep",
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/tutorial/Tutorial;onInput(Lnet/minecraft/client/player/Input;)V")),
            constant = {@Constant(floatValue = 0.2F, ordinal = 0), @Constant(floatValue = 0.2F, ordinal = 1)},
            require = 2, expect = 2)
    private float essenceAscendance$shieldSlowdown(float vanillaMultiplier) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!EquipmentShieldService.isUsingShield(player)) return vanillaMultiplier;
        player.setSprinting(false);
        return ShieldMath.movementMultiplier(EquipmentTooltipClientState.guardedMovementPercent(player.getUseItem()));
    }
}

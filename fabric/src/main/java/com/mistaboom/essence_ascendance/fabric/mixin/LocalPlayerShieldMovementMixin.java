package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.client.EquipmentTooltipClientState;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.skill.effect.GuardMobilityController;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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

        // One shared policy replaces the former unconditional sprint cancellation.
        if (!GuardMobilityController.sprintAllowed(player)) player.setSprinting(false);
        if (!EquipmentShieldService.isGuarding(player)) return vanillaMultiplier;

        return GuardMobilityController.movementMultiplier(player, vanillaMultiplier,
                EquipmentTooltipClientState.guardedMovementPercent(player.getUseItem()));
    }
    /** Only the item-use prohibition changes; native hunger/input/blindness/riding checks still run. */
    @ModifyExpressionValue(method = "canStartSprinting", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"), require = 1, expect = 1)
    private boolean essenceAscendance$guardedSprintStart(boolean usingItem) {
        return usingItem && !GuardMobilityController.sprintAllowed((LocalPlayer) (Object) this);
    }
    /** Sprint intent is checked before the guard-specific slowdown, including low configured/rank multipliers. */
    @ModifyExpressionValue(method = "hasEnoughImpulseToStartSprinting", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/player/Input;forwardImpulse:F"), require = 1, expect = 1)
    private float essenceAscendance$guardedSprintIntent(float scaled) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!GuardMobilityController.sprintAllowed(player)) return scaled;
        float multiplier = GuardMobilityController.movementMultiplier(player, .2F,
                EquipmentTooltipClientState.guardedMovementPercent(player.getUseItem()));
        return Math.min(1, scaled / multiplier);
    }
}

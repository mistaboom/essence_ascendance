package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityShieldGuardMixin {
    @Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$functionalGuard(CallbackInfoReturnable<Boolean> ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!EquipmentShieldService.isShield(entity.getUseItem())) {
            return;
        }

        ci.setReturnValue(
                EquipmentShieldService.canGuard(entity, entity.getUseItem())
                        && entity.getTicksUsingItem()
                        >= EquipmentShieldService.raiseDelayTicks(entity, entity.getUseItem())
        );
    }
}

package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class GuardKnockbackMixin {
    @WrapMethod(method = "knockback")
    private void essenceAscendance$guardKnockback(double strength, double x, double z, Operation<Void> original) {
        EquipmentDamageService.observeGuardKnockback((LivingEntity)(Object)this, strength, x, z,
                () -> original.call(strength, x, z));
    }
}

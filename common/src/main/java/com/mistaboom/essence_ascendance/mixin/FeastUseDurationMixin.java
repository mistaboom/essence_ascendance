package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.skill.effect.VitalitySustenanceEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Shared native duration query; NeoForge's use-start event still receives and may cancel this duration. */
@Mixin(ItemStack.class)
public abstract class FeastUseDurationMixin {
    @ModifyReturnValue(method = "getUseDuration", at = @At("RETURN"))
    private int essenceAscendance$foodDuration(int original, LivingEntity user) {
        return VitalitySustenanceEffects.useDuration((ItemStack) (Object) this, user, original);
    }
}

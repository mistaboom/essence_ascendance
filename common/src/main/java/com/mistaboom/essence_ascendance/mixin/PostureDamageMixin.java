package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One protected native boundary shared by Fabric and NeoForge, after initial validation, before any shield block. */
@Mixin(LivingEntity.class)
public abstract class PostureDamageMixin {
    @Inject(method="hurt", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/LivingEntity;isDamageSourceBlocked(Lnet/minecraft/world/damagesource/DamageSource;)Z"),
            cancellable=true, require=1, expect=1, allow=1)
    private void essenceAscendance$postureDodge(DamageSource source, float amount, CallbackInfoReturnable<Boolean> result) {
        if ((Object)this instanceof ServerPlayer player && EquipmentDamageService.tryPostureDodge(player,source,amount)) {
            if ((Object)this instanceof com.mistaboom.essence_ascendance.posture.NativeDamageCleanup cleanup)
                cleanup.essenceAscendance$finishDodgedDamage(source);
            result.setReturnValue(false);
        }
    }
}

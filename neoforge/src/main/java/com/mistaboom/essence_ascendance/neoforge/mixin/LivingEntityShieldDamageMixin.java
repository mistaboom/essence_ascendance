package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityShieldDamageMixin {
    @WrapOperation(method = "hurt", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/neoforged/neoforge/common/damagesource/DamageContainer;setBlockedDamage(Lnet/neoforged/neoforge/event/entity/living/LivingShieldBlockEvent;)V"))
    private void essenceAscendance$completedBlock(DamageContainer container, LivingShieldBlockEvent event,
                                                 Operation<Void> original) {
        boolean eligible = !event.isCanceled() && event.getOriginalBlock() && event.getBlocked();
        if (eligible && (Object) this instanceof ServerPlayer player) {
            EquipmentDamageService.captureBlockingShield(player, container.getSource());
        }
        original.call(container, event);
        if (eligible && (Object) this instanceof ServerPlayer player) {
            // DamageContainer now contains NeoForge's final, clamped blocked amount.
            EquipmentDamageService.recordBlockedDamage(player, container.getSource(), container.getBlockedDamage());
            /*
             * Commit at the authoritative block operation. The surrounding
             * hurt method may subsequently return early because of vanilla's
             * post-hit immunity, but that must not erase a completed block.
             */
            EquipmentDamageService.commitBlock(player, container.getSource());
        }
    }
}

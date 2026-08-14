package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentVitalityService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/*
 * Fabric-only bridge for Healing Effectiveness.
 *
 * Status Resistance no longer hooks effect application here. The common
 * EquipmentVitalityService observes the authoritative active-effect list on the
 * server tick, which catches every source consistently.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityVitalityMixin {

    @ModifyVariable(
            method = "heal",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float essenceAscendance$modifyHealing(
            float amount
    ) {
        if ((Object) this instanceof ServerPlayer player) {
            return EquipmentVitalityService.modifyExternalHealing(
                    player,
                    amount
            );
        }

        return amount;
    }
}

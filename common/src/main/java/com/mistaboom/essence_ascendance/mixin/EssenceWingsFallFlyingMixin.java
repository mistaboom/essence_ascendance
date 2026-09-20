package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.movement.FlightAbilityRules;
import com.mistaboom.essence_ascendance.movement.MovementAbilityRules;
import com.mistaboom.essence_ascendance.skill.CommittedSkillAccess;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve a legitimately active Essence Wings glide through vanilla's chest-Elytra validation step. */
@Mixin(LivingEntity.class)
public abstract class EssenceWingsFallFlyingMixin {
    @Unique private boolean essenceAscendance$preserveWings;

    @Inject(method = "updateFallFlying", at = @At("HEAD"))
    private void essenceAscendance$captureWings(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        essenceAscendance$preserveWings = entity instanceof Player player && player.isFallFlying()
                && CommittedSkillAccess.isEffective(player, SkillIds.ESSENCE_WINGS)
                && FlightAbilityRules.wingsAllowed(player) && !MovementAbilityRules.supported(player);
    }

    @Inject(method = "updateFallFlying", at = @At("TAIL"))
    private void essenceAscendance$restoreWings(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (essenceAscendance$preserveWings && entity instanceof Player player && !player.isFallFlying()
                && FlightAbilityRules.wingsAllowed(player) && !MovementAbilityRules.supported(player)) {
            player.startFallFlying();
        }
        essenceAscendance$preserveWings = false;
    }
}

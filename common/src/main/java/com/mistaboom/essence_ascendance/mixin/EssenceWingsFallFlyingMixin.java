package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.movement.FlightAbilityRules;
import com.mistaboom.essence_ascendance.movement.MovementAbilityRules;
import com.mistaboom.essence_ascendance.skill.CommittedSkillAccess;
import com.mistaboom.essence_ascendance.skill.SkillIds;
//? if >=26.1.2 {
/*import com.mistaboom.essence_ascendance.skill.effect.MobilityFlightEffects;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.GameEvent;
*///? }
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve a legitimately active Essence Wings glide through vanilla's equipment validation step. */
@Mixin(LivingEntity.class)
public abstract class EssenceWingsFallFlyingMixin {
    //? if >=26.1.2 {
    /*// MC26 returns immediately after rejecting equipment-free gliding. Preserve our
    // owned session before that rejection, rather than briefly clearing/restarting it.
    // Native fall-distance upkeep has already run; native travel and rocket thrust
    // continue unchanged, and no physical glider is invented or damaged.
    @Inject(method = "updateFallFlying", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;canGlide()Z"),
            cancellable = true, require = 1, expect = 1, allow = 1)
    private void essenceAscendance$maintainOwnedWings(CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        var context = SkillEffectRuntime.observationContext(player);
        if (context != null && MobilityFlightEffects.ownedWings(context)
                && !MovementAbilityRules.supported(player)) {
            if ((player.getFallFlyingTicks() + 1) % 10 == 0) player.gameEvent(GameEvent.ELYTRA_GLIDE);
            ci.cancel();
        }
    }
    *///? }

    //? if <26.1.2 {
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
    //? }
}

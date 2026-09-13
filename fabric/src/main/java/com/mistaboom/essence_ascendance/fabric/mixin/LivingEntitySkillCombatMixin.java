package com.mistaboom.essence_ascendance.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySkillCombatMixin {
    @Shadow protected boolean dead;

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("RETURN"))
    private void essenceAscendance$serverInteractionSwing(InteractionHand hand, boolean updateSelf,
                                                           CallbackInfo ci) {
        // Accepted native item/entity interactions use swing(hand, true).
        // Client animation packets use swing(hand) -> swing(hand, false).
        // Pair an interaction's following client animation without calling it a miss.
        if (updateSelf && hand == InteractionHand.MAIN_HAND && (Object) this instanceof ServerPlayer player) {
            EquipmentDamageService.rememberMainSwing(player);
        }
    }

    @WrapMethod(method = "actuallyHurt")
    private void essenceAscendance$damageMeasurement(DamageSource source, float amount,
                                                      Operation<Void> original) {
        EquipmentDamageService.observeSkillHealthDamage((LivingEntity) (Object) this, source,
                () -> original.call(source, amount));
    }

    @WrapMethod(method = "hurt")
    private boolean essenceAscendance$skillDamageScope(DamageSource source, float amount,
                                                       Operation<Boolean> original) {
        return com.mistaboom.essence_ascendance.attunement.AttunementGameplay.damage((LivingEntity) (Object) this, source, amount,
                () -> EquipmentDamageService.withSkillDamageFrame((LivingEntity) (Object) this, source,
                        () -> original.call(source, amount)));
    }

    @WrapMethod(method = "die")
    private void essenceAscendance$completedSkillDeath(DamageSource source, Operation<Void> original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        boolean alreadyDead = dead;
        original.call(source);
        // The common death event is cancelable. Only a completed native death
        // transition can grant a kill, and duplicate die calls cannot grant twice.
        if (!entity.level().isClientSide && !alreadyDead && dead) {
            com.mistaboom.essence_ascendance.attunement.AttunementGameplay.defeated(entity, source);
            SkillEffectRuntime.onLivingDeath(entity, source);
            if (entity instanceof ServerPlayer player) PlayerRuntimeLifecycleService.onDeath(player);
        }
    }
}

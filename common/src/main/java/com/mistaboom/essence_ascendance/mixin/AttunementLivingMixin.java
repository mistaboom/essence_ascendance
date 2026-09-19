package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class AttunementLivingMixin {
    @WrapMethod(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z")
    private boolean essenceAscendance$effectSource(MobEffectInstance effect, Entity source, Operation<Boolean> original) {
        LivingEntity self = (LivingEntity)(Object)this;
        return com.mistaboom.essence_ascendance.utility.UtilityPotionService.apply(self, effect, source,
                (resolvedEffect, resolvedSource) -> {
                    boolean applied = com.mistaboom.essence_ascendance.status.StatusInterceptionService.apply(
                            self, resolvedEffect, resolvedSource, () -> original.call(resolvedEffect, resolvedSource));
                    if (applied && !com.mistaboom.essence_ascendance.status.StatusInterceptionService.secondary()
                            && self instanceof ServerPlayer player)
                        AttunementGameplay.effectApplied(player, resolvedEffect.getEffect(), resolvedSource);
                    return applied;
                });
    }
    @WrapMethod(method = "forceAddEffect")
    private void essenceAscendance$forcedEffect(MobEffectInstance effect, Entity source, Operation<Void> original) {
        com.mistaboom.essence_ascendance.status.StatusInterceptionService.apply((LivingEntity)(Object)this,
                effect, source, () -> { original.call(effect, source); return ((LivingEntity)(Object)this).getEffect(effect.getEffect()) == effect; });
    }
    @WrapMethod(method = "getDamageAfterArmorAbsorb")
    private float essenceAscendance$armorPrevention(DamageSource source, float amount, Operation<Float> original) {
        float remaining = original.call(source, amount);
        com.mistaboom.essence_ascendance.equipment.EquipmentDamageService.recordGuardPrevention((LivingEntity)(Object)this, source, amount, remaining);
        AttunementGameplay.prevented((LivingEntity) (Object) this, source, amount, remaining);
        return remaining;
    }
    @WrapMethod(method = "getDamageAfterMagicAbsorb")
    private float essenceAscendance$magicPrevention(DamageSource source, float amount, Operation<Float> original) {
        float remaining = original.call(source, amount);
        com.mistaboom.essence_ascendance.equipment.EquipmentDamageService.recordGuardPrevention((LivingEntity)(Object)this, source, amount, remaining);
        AttunementGameplay.prevented((LivingEntity) (Object) this, source, amount, remaining);
        return remaining;
    }
    @WrapMethod(method = "heal")
    private void essenceAscendance$confirmedHealing(float amount, Operation<Void> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        double before = self.getHealth();
        original.call(amount);
        if (self instanceof ServerPlayer player) AttunementGameplay.healed(player, before,
                player.isUsingItem() ? AttunementGameplay.itemSignature(player.getUseItem()) : "regeneration");
    }
}

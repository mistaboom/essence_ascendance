package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/*
 * Fabric-only bridge into vanilla's damage pipeline.
 *
 * Fabric's public living-damage events can allow/cancel damage and observe the
 * result, but they do not provide a mutable incoming amount at the point we
 * need. This mixin therefore does the smallest possible loader-specific work:
 * capture/replace the incoming float and report actual health loss back to the
 * common EquipmentDamageService.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @Unique
    private Deque<DamageSource> essenceAscendance$damageSources;

    @Unique
    private Deque<Float> essenceAscendance$healthBeforeDamage;

    @ModifyVariable(
            method = "hurt",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float essenceAscendance$modifyIncomingDamage(
            float amount,
            DamageSource source,
            float originalAmount
    ) {
        if ((Object) this instanceof ServerPlayer player) {
            return EquipmentDamageService.modifyIncomingDamage(
                    player,
                    source,
                    amount
            );
        }

        return amount;
    }

    @Inject(
            method = "actuallyHurt",
            at = @At("HEAD")
    )
    private void essenceAscendance$captureHealthBeforeDamage(
            DamageSource source,
            float amount,
            CallbackInfo callbackInfo
    ) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }

        if (essenceAscendance$damageSources == null) {
            essenceAscendance$damageSources = new ArrayDeque<>();
            essenceAscendance$healthBeforeDamage = new ArrayDeque<>();
        }

        essenceAscendance$damageSources.push(source);
        essenceAscendance$healthBeforeDamage.push(player.getHealth());
    }

    @Inject(
            method = "actuallyHurt",
            at = @At("RETURN")
    )
    private void essenceAscendance$reflectAfterDamage(
            DamageSource source,
            float amount,
            CallbackInfo callbackInfo
    ) {
        if (!((Object) this instanceof ServerPlayer player)
                || essenceAscendance$damageSources == null
                || essenceAscendance$damageSources.isEmpty()
                || essenceAscendance$healthBeforeDamage == null
                || essenceAscendance$healthBeforeDamage.isEmpty()) {
            return;
        }

        DamageSource capturedSource = essenceAscendance$damageSources.pop();
        float healthBefore = essenceAscendance$healthBeforeDamage.pop();

        float actualHealthDamage = Math.max(
                0.0F,
                healthBefore - player.getHealth()
        );

        EquipmentDamageService.reflectAfterDamage(
                player,
                capturedSource,
                actualHealthDamage
        );
    }
}

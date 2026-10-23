package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.posture.NativeDamageCleanup;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Stack;

/** Clear NeoForge's pending damage frame when a common dodge ends the hit early. */
@Mixin(LivingEntity.class)
public abstract class NeoForgeDamageCleanupMixin implements NativeDamageCleanup {
    @Shadow protected Stack<DamageContainer> damageContainers;

    @Override
    public void essenceAscendance$finishDodgedDamage(DamageSource source) {
        if (!damageContainers.empty() && damageContainers.peek().getSource() == source) damageContainers.pop();
    }
}

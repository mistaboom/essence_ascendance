package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.equipment.MobEffectDurationAccess;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/*
 * Fabric bridge that gives the common vitality service a real duration setter
 * for the authoritative MobEffectInstance object.
 */
@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceDurationMixin
        implements MobEffectDurationAccess {

    @Shadow
    private int duration;

    @Override
    public void essenceAscendance$setDurationTicks(
            int durationTicks
    ) {
        this.duration = durationTicks;
    }
}

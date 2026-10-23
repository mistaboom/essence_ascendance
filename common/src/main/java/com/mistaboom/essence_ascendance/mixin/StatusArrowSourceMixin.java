package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.status.StatusInterceptionService;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
//? if >=26.1.2
/*import net.minecraft.world.entity.projectile.arrow.Arrow;*/
//? if <26.1.2
import net.minecraft.world.entity.projectile.Arrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tipped-arrow base-potion and custom-effect branches both retain the actual direct arrow. */
@Mixin(Arrow.class)
public abstract class StatusArrowSourceMixin {
    //? if >=26.1.2
    /*@WrapOperation(method = "doPostHurtEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/alchemy/PotionContents;forEachEffect(Ljava/util/function/Consumer;F)V"), require = 1)*/
    //? if >=26.1.2
    /*private void essenceAscendance$source(net.minecraft.world.item.alchemy.PotionContents contents, java.util.function.Consumer<MobEffectInstance> nativeEffect, float durationScale, Operation<Void> original, LivingEntity target) {*/
        //? if >=26.1.2
        /*Entity arrow = (Entity)(Object)this;*/
        //? if >=26.1.2
        /*Entity owner = ((Arrow)(Object)this).getEffectSource();*/
        //? if >=26.1.2
        /*original.call(contents, (java.util.function.Consumer<MobEffectInstance>) effect -> StatusInterceptionService.withSource(target, effect, arrow, owner, () -> { nativeEffect.accept(effect); return true; }), durationScale);*/
    //? if >=26.1.2
    /*}*/
    //? if <26.1.2
    @WrapOperation(method = "doPostHurtEffects", at = @At(value = "INVOKE", target =
            //? if <26.1.2
            "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"), require = 2, expect = 2)
    //? if <26.1.2
    private boolean essenceAscendance$source(LivingEntity target, MobEffectInstance effect, Entity source, Operation<Boolean> original) {
        //? if <26.1.2
        return StatusInterceptionService.withSource(target, effect, (Entity)(Object)this, source,
                //? if <26.1.2
                () -> original.call(target, effect, source));
    //? if <26.1.2
    }
}

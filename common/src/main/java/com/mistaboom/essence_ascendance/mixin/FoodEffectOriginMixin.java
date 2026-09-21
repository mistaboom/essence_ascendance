package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.vitality.ConsumableRecoveryService;
import com.mistaboom.essence_ascendance.vitality.FoodEffectOrigin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectInstance.class)
public abstract class FoodEffectOriginMixin implements FoodEffectOrigin {
    @Unique private boolean essenceAscendance$food;
    @Shadow private MobEffectInstance hiddenEffect;
    public boolean essenceAscendance$foodOrigin() { return essenceAscendance$food; }
    public void essenceAscendance$foodOrigin(boolean value) { essenceAscendance$food = value; }
    public FoodEffectOrigin essenceAscendance$hiddenOrigin() { return (FoodEffectOrigin)hiddenEffect; }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void essenceAscendance$created(CallbackInfo ci) {
        essenceAscendance$food |= ConsumableRecoveryService.consumingFood();
    }
    @Inject(method = "setDetailsFrom", at = @At("RETURN"))
    private void essenceAscendance$copy(MobEffectInstance source, CallbackInfo ci) {
        essenceAscendance$food = ((FoodEffectOrigin) source).essenceAscendance$foodOrigin();
    }
    @WrapMethod(method = "update")
    private boolean essenceAscendance$merge(MobEffectInstance source, Operation<Boolean> original) {
        MobEffectInstance self = (MobEffectInstance)(Object)this;
        boolean replaces = source.getAmplifier() > self.getAmplifier()
                || source.getAmplifier() == self.getAmplifier() && (source.isInfiniteDuration()
                || !self.isInfiniteDuration() && source.getDuration() > self.getDuration());
        boolean changed = original.call(source);
        if (changed && replaces) essenceAscendance$food = ((FoodEffectOrigin)source).essenceAscendance$foodOrigin();
        return changed;
    }
    @Inject(method = "save", at = @At("RETURN"))
    private void essenceAscendance$save(CallbackInfoReturnable<Tag> ci) {
        if (ci.getReturnValue() instanceof CompoundTag tag) {
            FoodEffectOrigin part = this;
            int depth = 0;
            while (part != null && depth < 256) {
                if (part.essenceAscendance$foodOrigin()) tag.putBoolean("essence_ascendance_food_origin_" + depth, true);
                part = part.essenceAscendance$hiddenOrigin(); depth++;
            }
        }
    }
    @Inject(method = "load", at = @At("RETURN"))
    private static void essenceAscendance$load(CompoundTag tag, CallbackInfoReturnable<MobEffectInstance> ci) {
        FoodEffectOrigin part = (FoodEffectOrigin)ci.getReturnValue();
        int depth = 0;
        while (part != null && depth < 256) {
            part.essenceAscendance$foodOrigin(tag.getBoolean("essence_ascendance_food_origin_" + depth)
                    || depth == 0 && tag.getBoolean("essence_ascendance_food_origin"));
            part = part.essenceAscendance$hiddenOrigin(); depth++;
        }
    }
    @WrapMethod(method = "tick")
    private boolean essenceAscendance$tick(LivingEntity target, Runnable changed, Operation<Boolean> original) {
        if (!essenceAscendance$food || !(target instanceof ServerPlayer player)) return original.call(target, changed);
        boolean[] result = {false};
        ConsumableRecoveryService.withFoodHealing(player, () -> result[0] = original.call(target, changed));
        return result[0];
    }
}

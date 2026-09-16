package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mistaboom.essence_ascendance.skill.effect.VitalityRecoveryEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Modify only the two eligible natural regeneration increments; starvation and healing calls remain native. */
@Mixin(FoodData.class)
public abstract class FoodDataRecoveryMixin implements com.mistaboom.essence_ascendance.vitality.NaturalRecoveryClockAccess {
    @Shadow private int tickTimer;
    @Unique private int essenceAscendance$transientNaturalSurplus;
    public int essenceAscendance$naturalTimer() { return tickTimer; }
    public void essenceAscendance$naturalTimer(int value) { tickTimer = value; }
    public void essenceAscendance$naturalSurplus(int value) { essenceAscendance$transientNaturalSurplus = Math.max(0, value); }
    @ModifyExpressionValue(method = "addAdditionalSaveData", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/food/FoodData;tickTimer:I", opcode = Opcodes.GETFIELD))
    private int essenceAscendance$saveNativeProgress(int value) {
        return Math.max(0, value - essenceAscendance$transientNaturalSurplus);
    }
    @WrapOperation(method = "tick", at = {
            @At(value = "FIELD", target = "Lnet/minecraft/world/food/FoodData;tickTimer:I", opcode = Opcodes.PUTFIELD, ordinal = 0),
            @At(value = "FIELD", target = "Lnet/minecraft/world/food/FoodData;tickTimer:I", opcode = Opcodes.PUTFIELD, ordinal = 2)
    }, require = 2)
    private void essenceAscendance$naturalClock(FoodData food, int value, Operation<Void> original,
                                                @Local(argsOnly = true) Player player) {
        original.call(food, VitalityRecoveryEffects.advanceNaturalTimer(player, food, value));
    }
    @WrapOperation(method = "tick", at = {
            @At(value = "FIELD", target = "Lnet/minecraft/world/food/FoodData;tickTimer:I", opcode = Opcodes.PUTFIELD, ordinal = 1),
            @At(value = "FIELD", target = "Lnet/minecraft/world/food/FoodData;tickTimer:I", opcode = Opcodes.PUTFIELD, ordinal = 3),
            @At(value = "FIELD", target = "Lnet/minecraft/world/food/FoodData;tickTimer:I", opcode = Opcodes.PUTFIELD, ordinal = 4),
            @At(value = "FIELD", target = "Lnet/minecraft/world/food/FoodData;tickTimer:I", opcode = Opcodes.PUTFIELD, ordinal = 5),
            @At(value = "FIELD", target = "Lnet/minecraft/world/food/FoodData;tickTimer:I", opcode = Opcodes.PUTFIELD, ordinal = 6)
    }, require = 5)
    private void essenceAscendance$endNaturalClock(FoodData food, int value, Operation<Void> original,
                                                   @Local(argsOnly = true) Player player) {
        original.call(food, VitalityRecoveryEffects.leaveNaturalTimer(player, food, value));
    }
}

package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.valuation.TradeSamplingScope;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The native animal XP callback reads Level.random rather than Entity.random. */
@Mixin(Animal.class)
public abstract class EvidenceAnimalExperienceMixin {
    @WrapOperation(method = "getBaseExperienceReward", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/Level;random:Lnet/minecraft/util/RandomSource;"))
    private RandomSource essence$hypotheticalRewardRandom(Level level, Operation<RandomSource> original) {
        return TradeSamplingScope.rewardRandom(original.call(level));
    }
}

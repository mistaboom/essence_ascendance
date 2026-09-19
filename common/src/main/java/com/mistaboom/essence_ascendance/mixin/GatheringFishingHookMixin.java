package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gathering.GatheringFishingHookAccess;
import com.mistaboom.essence_ascendance.gathering.GatheringFishingService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public abstract class GatheringFishingHookMixin implements GatheringFishingHookAccess {
    @Shadow private int nibble;
    @Shadow private int timeUntilLured;
    @Shadow private int timeUntilHooked;
    @Shadow @Final @Mutable private int luck;

    @Unique private int essenceAscendance$lureBefore;
    @Unique private int essenceAscendance$hookBefore;
    @Unique private double essenceAscendance$lureCarry;
    @Unique private double essenceAscendance$hookCarry;
    @Unique private double essenceAscendance$reelCarry;

    @Inject(method = "catchingFish", at = @At("HEAD"))
    private void essenceAscendance$beforeFishingLogic(BlockPos pos, CallbackInfo ci) {
        if (((FishingHook) (Object) this).getPlayerOwner() instanceof ServerPlayer player) {
            GatheringFishingService.beforeFishingLogic(player, this);
        }
    }

    @Inject(method = "catchingFish", at = @At("TAIL"))
    private void essenceAscendance$afterFishingLogic(BlockPos pos, CallbackInfo ci) {
        if (((FishingHook) (Object) this).getPlayerOwner() instanceof ServerPlayer player) {
            GatheringFishingService.afterFishingLogic(player, this);
        }
    }

    @WrapMethod(method = "retrieve")
    private int essenceAscendance$fishingInstinctLoot(ItemStack rod, Operation<Integer> original) {
        if (!(((FishingHook) (Object) this).getPlayerOwner() instanceof ServerPlayer player)) {
            return original.call(rod);
        }
        int bonus = GatheringFishingService.virtualLuck(player);
        if (bonus <= 0) return original.call(rod);
        int nativeLuck = luck;
        luck = Math.max(0, nativeLuck + bonus);
        try {
            return original.call(rod);
        } finally {
            luck = nativeLuck;
        }
    }

    @Override public int essenceAscendance$nibble() { return nibble; }
    @Override public void essenceAscendance$setNibble(int ticks) { nibble = Math.max(0, ticks); }
    @Override public int essenceAscendance$timeUntilLured() { return timeUntilLured; }
    @Override public void essenceAscendance$setTimeUntilLured(int ticks) { timeUntilLured = Math.max(0, ticks); }
    @Override public int essenceAscendance$timeUntilHooked() { return timeUntilHooked; }
    @Override public void essenceAscendance$setTimeUntilHooked(int ticks) { timeUntilHooked = Math.max(0, ticks); }
    @Override public int essenceAscendance$lureBefore() { return essenceAscendance$lureBefore; }
    @Override public void essenceAscendance$setLureBefore(int ticks) { essenceAscendance$lureBefore = ticks; }
    @Override public int essenceAscendance$hookBefore() { return essenceAscendance$hookBefore; }
    @Override public void essenceAscendance$setHookBefore(int ticks) { essenceAscendance$hookBefore = ticks; }
    @Override public double essenceAscendance$lureCarry() { return essenceAscendance$lureCarry; }
    @Override public void essenceAscendance$setLureCarry(double carry) { essenceAscendance$lureCarry = carry; }
    @Override public double essenceAscendance$hookCarry() { return essenceAscendance$hookCarry; }
    @Override public void essenceAscendance$setHookCarry(double carry) { essenceAscendance$hookCarry = carry; }
    @Override public double essenceAscendance$reelCarry() { return essenceAscendance$reelCarry; }
    @Override public void essenceAscendance$setReelCarry(double carry) { essenceAscendance$reelCarry = carry; }
}

package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gathering.AnimalFeedingState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public abstract class AnimalFeedingMixin implements AnimalFeedingState {
    @Shadow @Final protected static int PARENT_AGE_AFTER_BREEDING;
    @Unique private long essenceAscendance$fedAt = Long.MIN_VALUE;
    public long essenceAscendance$fedAt() { return essenceAscendance$fedAt; }
    public void essenceAscendance$fedAt(long now) { essenceAscendance$fedAt = now; }
    public int essenceAscendance$feedWindow() { return PARENT_AGE_AFTER_BREEDING; }
    @WrapMethod(method = "usePlayerItem")
    private void essenceAscendance$feed(Player player, InteractionHand hand, ItemStack stack, Operation<Void> original) {
        int before = stack.getCount();
        original.call(player, hand, stack);
        if (!player.level().isClientSide && stack.getCount() < before)
            essenceAscendance$fedAt = player.level().getGameTime();
    }
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void essenceAscendance$save(CompoundTag tag, CallbackInfo ci) {
        if (essenceAscendance$fedAt != Long.MIN_VALUE) tag.putLong("essence_ascendance_fed_at", essenceAscendance$fedAt);
    }
    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void essenceAscendance$load(CompoundTag tag, CallbackInfo ci) {
        essenceAscendance$fedAt = tag.contains("essence_ascendance_fed_at") ? tag.getLong("essence_ascendance_fed_at") : Long.MIN_VALUE;
    }
}

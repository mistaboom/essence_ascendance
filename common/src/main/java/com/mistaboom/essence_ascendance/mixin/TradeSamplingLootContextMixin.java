package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.valuation.TradeSamplingScope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(LootContext.Builder.class)
public abstract class TradeSamplingLootContextMixin {
    @Shadow private RandomSource random;

    @Inject(method = "create", at = @At("HEAD"))
    private void essence$privateTradeLoot(Optional<ResourceLocation> sequence, CallbackInfoReturnable<LootContext> cir) {
        random = TradeSamplingScope.isolatedLootRandom(random);
    }
}

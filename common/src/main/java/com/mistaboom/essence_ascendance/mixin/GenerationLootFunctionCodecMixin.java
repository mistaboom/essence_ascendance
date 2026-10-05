package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.valuation.GenerationRegistrySerialization;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LootItemFunctionType.class)
public abstract class GenerationLootFunctionCodecMixin {
    @ModifyReturnValue(method = "codec", at = @At("RETURN"))
    private MapCodec<?> essenceAscendance$lootAcquisitionProjection(MapCodec<?> original) {
        return GenerationRegistrySerialization.lootFunctionCodec(original);
    }
}

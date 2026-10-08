package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.valuation.GenerationRegistrySerialization;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LootItemConditionType.class)
public abstract class GenerationLootConditionCodecMixin {
    @ModifyReturnValue(method = "codec", at = @At("RETURN"))
    private MapCodec<?> essenceAscendance$loadedConditionEvidence(MapCodec<?> original) {
        return GenerationRegistrySerialization.lootConditionCodec(original);
    }
}

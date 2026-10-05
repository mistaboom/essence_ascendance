package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.valuation.GenerationRegistrySerialization;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Covers the named-holder codec shared by conditions, items and other native registry fields. */
@Mixin(Registry.class)
public interface GenerationNamedHolderCodecMixin<E> {
    @ModifyReturnValue(method = "holderByNameCodec", at = @At("RETURN"))
    @SuppressWarnings("unchecked")
    private Codec<Holder<E>> essenceAscendance$generationNamedHolderCodec(Codec<Holder<E>> original) {
        return GenerationRegistrySerialization.namedHolderCodec((Registry<E>) (Object) this, original);
    }
}

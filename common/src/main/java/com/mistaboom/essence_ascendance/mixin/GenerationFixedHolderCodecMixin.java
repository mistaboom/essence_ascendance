package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.valuation.GenerationRegistrySerialization;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Normal serialization is unchanged; generation may identify a direct holder's exact registered value. */
@Mixin(RegistryFixedCodec.class)
public abstract class GenerationFixedHolderCodecMixin<E> {
    @Shadow @Final private ResourceKey<? extends Registry<E>> registryKey;

    @ModifyVariable(method = "encode(Lnet/minecraft/core/Holder;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Holder<E> essenceAscendance$canonicalGenerationHolder(Holder<E> value, Holder<E> original, DynamicOps<?> ops, Object prefix) {
        return GenerationRegistrySerialization.fixedHolder(registryKey, value, ops);
    }
}

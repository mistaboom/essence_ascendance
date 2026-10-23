package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Fabric's 26 model registration boundary for the native special-model codec map. */
@Mixin(SpecialModelRenderers.class)
public interface SpecialModelRenderersAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier,
            MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> essenceAscendance$models() {
        throw new AssertionError();
    }
}

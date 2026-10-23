package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Fabric's 26 registration boundary for native item tint codecs. */
@Mixin(ItemTintSources.class)
public interface ItemTintSourcesAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier,
            MapCodec<? extends ItemTintSource>> essenceAscendance$tints() {
        throw new AssertionError();
    }
}

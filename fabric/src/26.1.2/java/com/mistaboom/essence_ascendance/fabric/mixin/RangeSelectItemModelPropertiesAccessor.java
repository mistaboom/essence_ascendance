package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Register the authored item state selectors before client model resources load. */
@Mixin(RangeSelectItemModelProperties.class)
public interface RangeSelectItemModelPropertiesAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier,
            MapCodec<? extends RangeSelectItemModelProperty>> essenceAscendance$properties() {
        throw new AssertionError();
    }
}

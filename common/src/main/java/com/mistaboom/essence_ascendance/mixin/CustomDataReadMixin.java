package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.data.CustomDataReader;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapDecoder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The same read-only projection boundary on both native versions and both physical sides. */
@Mixin(CustomData.class)
abstract class CustomDataReadMixin implements CustomDataReader.Access {
    @Shadow @Final private CompoundTag tag;

    @Override
    public final <T> DataResult<T> essenceAscendance$readCustomData(MapDecoder<T> decoder) {
        return decoder.decode(NbtOps.INSTANCE, NbtOps.INSTANCE.getMap(tag).getOrThrow());
    }
}

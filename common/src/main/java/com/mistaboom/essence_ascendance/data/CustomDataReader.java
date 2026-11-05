package com.mistaboom.essence_ascendance.data;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapDecoder;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.component.CustomData;

/** Narrow immutable value projections; readers never need a copy of unrelated payment provenance. */
public final class CustomDataReader {
    private CustomDataReader() { }

    public static <T> DataResult<T> read(CustomData data, MapDecoder<T> decoder) {
        if ((Object) data instanceof Access access) return access.essenceAscendance$readCustomData(decoder);
        // Standalone verification has no transformed native classes. Production uses the common bridge.
        //? if >=26.1.2
        /*return decoder.decode(NbtOps.INSTANCE, NbtOps.INSTANCE.getMap(data.copyTag()).getOrThrow());*/
        //? if <26.1.2
        return data.read(decoder);
    }

    /** Mirrors the former native read(MapDecoder) contract without exposing the stored mutable tag. */
    public interface Access {
        <T> DataResult<T> essenceAscendance$readCustomData(MapDecoder<T> decoder);
    }
}

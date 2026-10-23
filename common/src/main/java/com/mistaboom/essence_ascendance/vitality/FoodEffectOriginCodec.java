package com.mistaboom.essence_ascendance.vitality;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

/** Adds provenance to the native effect map without replacing its schema or registry handling. */
public final class FoodEffectOriginCodec {
    private static final String PREFIX = "essence_ascendance_food_origin_";
    private FoodEffectOriginCodec() { }

    public static <A> Codec<A> wrap(Codec<A> nativeCodec) {
        return new Codec<>() {
            @Override public <T> DataResult<T> encode(A value, DynamicOps<T> ops, T prefix) {
                DataResult<T> encoded = nativeCodec.encode(value, ops, prefix);
                FoodEffectOrigin part = (FoodEffectOrigin) value;
                for (int depth = 0; part != null && depth < 256; depth++, part = part.essenceAscendance$hiddenOrigin()) {
                    if (part.essenceAscendance$foodOrigin()) {
                        T key = ops.createString(PREFIX + depth);
                        encoded = encoded.flatMap(map -> ops.mergeToMap(map, key, ops.createBoolean(true)));
                    }
                }
                return encoded;
            }

            @Override public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return nativeCodec.decode(ops, input).map(result -> {
                    var map = ops.getMap(input).result();
                    FoodEffectOrigin part = (FoodEffectOrigin) result.getFirst();
                    for (int depth = 0; part != null && depth < 256; depth++, part = part.essenceAscendance$hiddenOrigin()) {
                        String key = PREFIX + depth;
                        boolean origin = map.map(fields -> fields.get(key)).flatMap(value -> ops.getBooleanValue(value).result()).orElse(false);
                        if (depth == 0) origin |= map.map(fields -> fields.get("essence_ascendance_food_origin"))
                                .flatMap(value -> ops.getBooleanValue(value).result()).orElse(false);
                        part.essenceAscendance$foodOrigin(origin);
                    }
                    return result;
                });
            }
        };
    }
}

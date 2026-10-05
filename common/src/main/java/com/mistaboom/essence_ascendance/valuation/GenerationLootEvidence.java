package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.stream.Stream;

/** Acquisition projection only: an unrepresentable function remains explicit, unresolved evidence. */
final class GenerationLootEvidence {
    static final String UNRESOLVED = "essence_evidence_unresolved_function";
    static final String CONTAINER_UNRESOLVED = "essence_evidence_container_unresolved_function";

    static <F> MapCodec<F> functionCodec(MapCodec<F> original, GenerationRegistrySerialization scope) {
        return new MapCodec<>() {
            @Override public <T> RecordBuilder<T> encode(F input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                if (!scope.ownsLootOps(ops)) return original.encode(input, ops, prefix);
                // Isolate the component builder: never accept partial parameters or contaminate its enclosing table.
                var encoded = original.encode(input, ops, ops.mapBuilder()).build(ops.empty());
                if (encoded.isSuccess()) {
                    var fields = ops.getMap(encoded.getOrThrow()).getOrThrow();
                    fields.entries().forEach(field -> prefix.add(field.getFirst(), field.getSecond()));
                    return prefix;
                }
                String failure = encoded.error().orElseThrow().message();
                if (failure.length() > 500) failure = failure.substring(0, 500);
                String functionClass = input.getClass().getName();
                scope.unsupportedLootFunction(functionClass, failure);
                return prefix.add(UNRESOLVED, ops.createBoolean(true))
                        .add("essence_evidence_function_class", ops.createString(functionClass))
                        .add("essence_evidence_failure", ops.createString(failure));
            }
            @Override public <T> DataResult<F> decode(DynamicOps<T> ops, MapLike<T> input) { return original.decode(ops, input); }
            @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return original.keys(ops); }
        };
    }

    static int unresolvedFunctions(JsonElement functions) {
        if (functions == null || functions.isJsonNull() || functions.isJsonPrimitive()) return 0;
        if (functions.isJsonArray()) return functions.getAsJsonArray().asList().stream().mapToInt(GenerationLootEvidence::unresolvedFunctions).sum();
        var object = functions.getAsJsonObject();
        if (object.has(UNRESOLVED) && object.get(UNRESOLVED).getAsBoolean()) return 1;
        if (object.has(CONTAINER_UNRESOLVED) && object.get(CONTAINER_UNRESOLVED).getAsBoolean()) return 1;
        return object.entrySet().stream().mapToInt(field -> unresolvedFunctions(field.getValue())).sum();
    }
}

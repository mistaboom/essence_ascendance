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

    /** LootJS 3.7.0 uses a unit codec, which omits the actual predicate's HolderSet.
     * Preserve those loaded members only in our private evidence serialization;
     * no predicate runs and gameplay codecs/decoding remain untouched. */
    static <F> MapCodec<F> conditionCodec(MapCodec<F> original, GenerationRegistrySerialization scope) {
        return new MapCodec<>() {
            @Override public <T> RecordBuilder<T> encode(F input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                if (!scope.ownsLootOps(ops) || !"1.21.1-3.7.0".equals(scope.version("lootjs")) || input == null
                        || !input.getClass().getName().equals("com.almostreliable.lootjs.loot.condition.MatchBiome"))
                    return original.encode(input, ops, prefix);
                try {
                    var biomes = (net.minecraft.core.HolderSet<?>)input.getClass().getMethod("biomes").invoke(input);
                    var ids = biomes.stream().map(holder -> holder.unwrapKey().orElseThrow(
                            () -> new IllegalStateException("Unregistered biome predicate member")).location().toString()).sorted().toList();
                    return prefix.add("biomes", ops.createList(ids.stream().map(ops::createString)));
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot inspect loaded biome predicate", failure);
                }
            }
            @Override public <T> DataResult<F> decode(DynamicOps<T> ops, MapLike<T> input) { return original.decode(ops, input); }
            @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return original.keys(ops); }
        };
    }

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

    /** The entity acquisition scenario is a player kill with no equipment enchantments.
     * Native EnchantedCountIncreaseFunction returns the original stack at level zero, before
     * reading its count provider or applying its cap. This does not certify enchanted yields,
     * container loot, failed serialization, custom functions or arbitrary callback behavior. */
    static int unresolvedUnenchantedEntityFunctions(JsonElement functions) {
        if (functions == null || functions.isJsonNull() || functions.isJsonPrimitive()) return 0;
        if (functions.isJsonArray()) return functions.getAsJsonArray().asList().stream()
                .mapToInt(GenerationLootEvidence::unresolvedUnenchantedEntityFunctions).sum();
        var object = functions.getAsJsonObject();
        if (object.has(UNRESOLVED) && object.get(UNRESOLVED).getAsBoolean()) return 1;
        if (object.has("function") && object.get("function").getAsString().equals("minecraft:enchanted_count_increase")
                && object.has("enchantment") && object.has("count")
                && (!object.has("conditions") || object.getAsJsonArray("conditions").isEmpty())
                && java.util.Set.of("function", "enchantment", "count", "limit", "conditions", CONTAINER_UNRESOLVED,
                        "essence_evidence_failure").containsAll(object.keySet())) return 0;
        return unresolvedFunctions(functions);
    }
}

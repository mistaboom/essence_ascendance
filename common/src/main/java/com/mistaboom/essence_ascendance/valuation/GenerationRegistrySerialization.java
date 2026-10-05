package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Read-only fixed-holder canonicalization, restricted to this operation's exact serialization context. */
public final class GenerationRegistrySerialization implements AutoCloseable {
    private static final ThreadLocal<GenerationRegistrySerialization> ACTIVE = new ThreadLocal<>();
    private final HolderLookup.Provider registries;
    private final RegistryOps<JsonElement> ops;
    private final Map<ResourceKey<?>, IdentityHashMap<Object, Holder.Reference<?>>> references = new HashMap<>();
    private final boolean lootEvidence;
    private final Map<String, Long> unsupportedLootFunctions = new java.util.TreeMap<>();
    private String definition = "";
    private boolean closed;

    private GenerationRegistrySerialization(HolderLookup.Provider registries, boolean lootEvidence) {
        this.registries = registries;
        this.lootEvidence = lootEvidence;
        ops = registries.createSerializationContext(JsonOps.INSTANCE);
    }

    static GenerationRegistrySerialization open(HolderLookup.Provider registries) {
        return open(registries, false);
    }
    static GenerationRegistrySerialization open(HolderLookup.Provider registries, boolean lootEvidence) {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested generation registry serialization");
        var scope = new GenerationRegistrySerialization(registries, lootEvidence);
        ACTIVE.set(scope);
        return scope;
    }

    RegistryOps<JsonElement> ops() { return ops; }
    void definition(String value) { definition = value; }
    Map<String, Long> unsupportedLootFunctions() { return Map.copyOf(unsupportedLootFunctions); }
    boolean ownsLootOps(DynamicOps<?> encodingOps) { return !closed && ACTIVE.get() == this && lootEvidence && ops == encodingOps; }
    void unsupportedLootFunction(String functionClass, String failure) {
        unsupportedLootFunctions.merge(definition + " / " + functionClass + ": " + failure, 1L, Long::sum);
        BalancePerformance.increment("generation_unsupported_loot_function_representations");
    }
    public static <T> com.mojang.serialization.MapCodec<T> lootFunctionCodec(com.mojang.serialization.MapCodec<T> original) {
        var scope = ACTIVE.get();
        return scope == null || !scope.lootEvidence || scope.closed ? original : GenerationLootEvidence.functionCodec(original, scope);
    }

    /** Named-holder codecs use Registry's own codec rather than RegistryFixedCodec. */
    public static <E> Codec<Holder<E>> namedHolderCodec(Registry<E> registry, Codec<Holder<E>> original) {
        return new Codec<>() {
            @Override public <T> DataResult<T> encode(Holder<E> value, DynamicOps<T> encodingOps, T prefix) {
                return original.encode(fixedHolder(registry.key(), value, encodingOps), encodingOps, prefix);
            }
            @Override public <T> DataResult<Pair<Holder<E>, T>> decode(DynamicOps<T> decodingOps, T input) {
                return original.decode(decodingOps, input);
            }
        };
    }

    /** Shared by fixed and named registry codecs; inline-holder codecs keep their native semantics. */
    public static <E> Holder<E> fixedHolder(ResourceKey<? extends Registry<E>> registryKey, Holder<E> holder, DynamicOps<?> encodingOps) {
        var scope = ACTIVE.get();
        if (scope == null || scope.closed || scope.ops != encodingOps || holder.kind() != Holder.Kind.DIRECT) return holder;
        return scope.reference(registryKey, holder);
    }

    @SuppressWarnings("unchecked")
    private <E> Holder<E> reference(ResourceKey<? extends Registry<E>> registryKey, Holder<E> holder) {
        var known = references.computeIfAbsent(registryKey, ignored -> {
            var byIdentity = new IdentityHashMap<Object, Holder.Reference<?>>();
            registries.lookup(registryKey).ifPresent(lookup -> lookup.listElements().forEach(reference -> byIdentity.put(reference.value(), reference)));
            BalancePerformance.increment("generation_fixed_holder_registry_indexes");
            return byIdentity;
        });
        Holder.Reference<E> reference = (Holder.Reference<E>) known.get(holder.value());
        // No ID/default lookup: an unregistered object must not become minecraft:air or another fallback.
        if (reference == null || ops.owner(registryKey).filter(reference::canSerializeIn).isEmpty()) return holder;
        BalancePerformance.increment("generation_canonical_fixed_holder_references");
        return reference;
    }

    @Override public void close() {
        if (closed) return;
        if (ACTIVE.get() != this) throw new IllegalStateException("Generation serialization scope ownership changed");
        ACTIVE.remove(); references.clear(); unsupportedLootFunctions.clear(); closed = true;
    }
}

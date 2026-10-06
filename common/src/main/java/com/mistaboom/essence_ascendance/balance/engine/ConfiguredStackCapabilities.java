package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Static configured outputs from the existing normalized production graph, never another recipe scan.
 * Custom assembly, copied-base variants and missing station/operating access stay outside this proof. */
public final class ConfiguredStackCapabilities {
    private ConfiguredStackCapabilities() { }
    public static void collect(ProductionGraph graph, HolderLookup.Provider registries,
                               Map<String, ResourceEvidence> resources, CapabilitySink sink) {
        collect(graph, registries, resources, Set.of(), sink);
    }
    public static void collect(ProductionGraph graph, HolderLookup.Provider registries,
                               Map<String, ResourceEvidence> resources, Set<String> constrained, CapabilitySink sink) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var access = new ConfigurationAccess.Resolver(resources, constrained);
        for (var process : graph.processes()) {
            if (!process.metadata().containsKey("effective_definition")) continue;
            // Cheap text rejection before parsing large machine definitions. A component-free result
            // duplicates the ordinary equipment census and cannot add a configuration witness.
            if (!process.metadata().get("effective_definition").contains("\"components\"")) continue;
            var attempt = OptionalIntegration.attempt("configured_stacks:" + process.id(), "read static configured output " + process.family(), () -> {
                CapabilitySink staged = new CapabilitySink();
                collectProcess(process, ops, access, staged);
                return staged;
            });
            if (attempt.succeeded()) sink.merge(attempt.value().orElseThrow());
            else {
                sink.analyzed();
                sink.candidate(process.id(), "configured_stacks", "UNKNOWN: configured output failed; acquisition and capability evidence excluded; " + attempt.failure());
            }
        }
    }
    private static void collectProcess(ProductionGraph.Process process, RegistryOps<JsonElement> ops,
                                       ConfigurationAccess.Resolver access, CapabilitySink sink) {
            var definition = process.effectiveDefinition();
            if (!definition.has("result") || !definition.get("result").isJsonObject()) return;
            JsonObject result = definition.getAsJsonObject("result");
            if (!result.has("components")) return;
            sink.analyzed();
            if (!process.acquisitionComplete() || process.metadata().containsKey("variant_transform")
                    || !Set.of("minecraft:crafting", "minecraft:stonecutting").contains(process.family())) {
                sink.candidate(process.id(), "configured_stacks", "Configured output retained; custom assembly, base transfer or station/operating access unresolved");
                return;
            }
            // Audited ordinary crafting/stonecutting have no external operating resource. All inputs,
            // including reusable catalysts, must be accessible for this particular output configuration.
            var requirements = new ArrayList<>(process.inputs().stream().map(ProductionGraph.Input::alternatives).toList());
            requirements.add(List.of(process.family().equals("minecraft:stonecutting") ? "minecraft:stonecutter" : "minecraft:crafting_table"));
            var proof = access.require(requirements);
            if (!proof.placement().reachable()) {
                sink.candidate(process.id(), "configured_stacks", String.join("; ", proof.unknown())); return;
            }
            var decoded = ItemStack.CODEC.parse(ops, result).getOrThrow();
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(decoded.getItem()).toString();
            if (process.outputs().stream().noneMatch(o -> o.itemId().equals(id)) || id.startsWith("essence_ascendance:")) return;
            var placement = new CompetitiveCapabilities.Placement(proof.placement().stage(), true,
                    Math.min(process.confidence(), proof.placement().confidence()), proof.placement().acquisition());
            NativeCapabilityReader.stack(decoded, id, "recipe:" + process.id(), placement, sink);
    }
}

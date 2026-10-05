package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Read-only loaded modifier definitions. Never apply a modifier or roll a loot context. */
final class RuntimeLootAudit {
    static final String RUNTIME_MARKER = "essence_evidence_runtime_modifiers";
    record Modifier(String implementation, JsonObject definition, String limitation, BlockLootModifierAudit.Rule blockRule, boolean conditionsEnforced) {
        Modifier(String implementation, JsonObject definition, String limitation, BlockLootModifierAudit.Rule blockRule) {
            this(implementation, definition, limitation, blockRule, true);
        }
        Modifier(String implementation, JsonObject definition, String limitation) {
            this(implementation, definition, limitation, BlockLootModifierAudit.Rule.UNKNOWN);
        }
        boolean mayAffect(String table) {
            return !conditionsEnforced || tableConditions(definition.get("conditions"), table) != Truth.FALSE;
        }
    }
    private enum Truth { TRUE, FALSE, UNKNOWN }
    private static Truth tableConditions(JsonElement value, String table) {
        if (value == null) return Truth.TRUE;
        if (!value.isJsonArray()) return Truth.UNKNOWN;
        Truth result = Truth.TRUE;
        for (var term : value.getAsJsonArray()) result = and(result, tableCondition(term, table));
        return result;
    }
    private static Truth tableCondition(JsonElement value, String table) {
        if (value == null || !value.isJsonObject()) return Truth.UNKNOWN;
        var c = value.getAsJsonObject();
        String type = c.has("condition") ? c.get("condition").getAsString() : "";
        if (type.equals("neoforge:loot_table_id") && c.has("loot_table_id"))
            return table.equals(c.get("loot_table_id").getAsString()) ? Truth.TRUE : Truth.FALSE;
        if (type.equals("minecraft:inverted")) {
            var inner = tableCondition(c.get("term"), table);
            return inner == Truth.UNKNOWN ? inner : inner == Truth.TRUE ? Truth.FALSE : Truth.TRUE;
        }
        if ((type.equals("minecraft:any_of") || type.equals("minecraft:all_of")) && c.has("terms") && c.get("terms").isJsonArray()) {
            boolean all = type.equals("minecraft:all_of"); Truth result = all ? Truth.TRUE : Truth.FALSE;
            for (var term : c.getAsJsonArray("terms")) {
                var next = tableCondition(term, table);
                result = all ? and(result, next) : result == Truth.TRUE || next == Truth.TRUE ? Truth.TRUE
                        : result == Truth.UNKNOWN || next == Truth.UNKNOWN ? Truth.UNKNOWN : Truth.FALSE;
            }
            return result;
        }
        return Truth.UNKNOWN;
    }
    private static Truth and(Truth a, Truth b) { return a == Truth.FALSE || b == Truth.FALSE ? Truth.FALSE
            : a == Truth.UNKNOWN || b == Truth.UNKNOWN ? Truth.UNKNOWN : Truth.TRUE; }
    @SuppressWarnings("unchecked")
    static List<Modifier> capture(GenerationDataSnapshot inputs) {
        String version = inputs.installedVersion("neoforge");
        if (version == null) return List.of();
        if (!Set.of("21.1.248", "21.1.251").contains(version)) return List.of(new Modifier("neoforge:" + version, new JsonObject(),
                "Unaudited global-loot-modifier API; effective registry alone cannot certify runtime drops"));
        try {
            var getter = Class.forName("net.neoforged.neoforge.common.NeoForgeEventHandler").getDeclaredMethod("getLootModifierManager");
            getter.setAccessible(true);
            Object manager = Objects.requireNonNull(getter.invoke(null), "Global loot modifier manager not ready");
            var modifiers = (Collection<?>) manager.getClass().getMethod("getAllLootMods").invoke(manager);
            Codec<Object> codec = (Codec<Object>) Class.forName("net.neoforged.neoforge.common.loot.IGlobalLootModifier").getField("DIRECT_CODEC").get(null);
            var ops = inputs.server().registries().compositeAccess().createSerializationContext(JsonOps.INSTANCE);
            List<Modifier> result = new ArrayList<>();
            for (Object modifier : modifiers) {
                var encoded = codec.encodeStart(ops, modifier);
                var definition = encoded.isSuccess() ? encoded.getOrThrow().getAsJsonObject() : new JsonObject();
                result.add(new Modifier(modifier.getClass().getName(), definition,
                        "Unsupported runtime modifier output; base block-harvest contract is recorded separately"
                                + encoded.error().map(e -> ": " + e.message()).orElse(""),
                        encoded.isSuccess() ? BlockLootModifierAudit.captureRule(modifier.getClass().getName(), inputs) : BlockLootModifierAudit.Rule.UNKNOWN,
                        modifier.getClass().getMethod("apply", it.unimi.dsi.fastutil.objects.ObjectArrayList.class,
                                net.minecraft.world.level.storage.loot.LootContext.class).getDeclaringClass().getName()
                                .equals("net.neoforged.neoforge.common.loot.LootModifier")));
            }
            // Runtime order remains available in diagnostics; it is not executed or assumed commutative.
            return List.copyOf(result);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot inspect effective global loot modifiers", failure); }
    }
    static void mark(Map<ResourceLocation, JsonObject> tables, List<Modifier> modifiers, List<String> lootrExtensions) {
        for (var entry : tables.entrySet()) {
            List<String> reasons = new ArrayList<>();
            if (!lootrExtensions.isEmpty()) reasons.add("Unsupported Lootr extension callbacks; see saved lootr.unsupported");
            List<Integer> affected = new ArrayList<>();
            for (int index = 0; index < modifiers.size(); index++) if (modifiers.get(index).mayAffect(entry.getKey().toString())) affected.add(index);
            if (!affected.isEmpty()) reasons.add("Unsupported runtime modifier indices=" + affected + "; see saved runtimeModifiers");
            if (reasons.isEmpty()) continue;
            JsonArray functions = entry.getValue().has("functions") ? entry.getValue().getAsJsonArray("functions") : new JsonArray();
            JsonObject marker = new JsonObject(); marker.addProperty(GenerationLootEvidence.UNRESOLVED, true);
            marker.addProperty(RUNTIME_MARKER, lootrExtensions.isEmpty());
            marker.addProperty("essence_evidence_function_class", "runtime_loot_modification");
            marker.addProperty("essence_evidence_failure", String.join("; ", reasons)); functions.add(marker);
            entry.getValue().add("functions", functions);
        }
    }
}

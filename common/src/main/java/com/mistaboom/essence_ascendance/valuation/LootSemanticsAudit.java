package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;

/** One read-only semantic audit of the normalized graph, shared by all loot consumers. */
final class LootSemanticsAudit {
    static void auditTables(Map<ResourceLocation, JsonObject> tables) {
        Map<String, Set<String>> edges = new TreeMap<>();
        tables.forEach((id, table) -> { Set<String> references = new TreeSet<>(); audit(table, references, true); edges.put(id.toString(), references); });
        Set<String> cyclic = new TreeSet<>(), finished = new HashSet<>();
        for (String id : edges.keySet()) cycles(id, edges, new LinkedHashSet<>(), finished, cyclic);
        tables.forEach((id, table) -> {
            if (cyclic.contains(id.toString())) marker(table, "Cyclic nested loot reference; diagnostic-only partial expansion");
            if (edges.get(id.toString()).stream().anyMatch(ref -> !edges.containsKey(ref))) marker(table, "Missing nested loot definition");
        });
        BalancePerformance.count("loot_nested_references", edges.values().stream().mapToLong(Set::size).sum());
        BalancePerformance.count("loot_cyclic_tables", cyclic.size());
    }
    private static void cycles(String id, Map<String, Set<String>> edges, LinkedHashSet<String> active, Set<String> finished, Set<String> cyclic) {
        if (active.contains(id)) { boolean inCycle = false; for (String node : active) { if (node.equals(id)) inCycle = true; if (inCycle) cyclic.add(node); } return; }
        if (finished.contains(id)) return;
        active.add(id);
        for (String ref : edges.getOrDefault(id, Set.of())) cycles(ref, edges, active, finished, cyclic);
        active.remove(id); finished.add(id);
    }
    // Enchantment functions can replace BOOK with ENCHANTED_BOOK. Item-only
    // projections cannot discard that transformation, even on a parent table/pool.
    // Keep those results diagnostic until their configured output is evaluated.
    private static final Set<String> ITEM_PRESERVING = Set.of("minecraft:set_name", "minecraft:set_lore", "minecraft:set_components",
            "minecraft:set_custom_data", "minecraft:set_attributes", "minecraft:set_damage",
            "minecraft:set_potion", "minecraft:set_instrument",
            "minecraft:set_banner_pattern", "minecraft:copy_name", "minecraft:copy_custom_data", "minecraft:copy_components");
    private static final Set<String> ENCHANTING = Set.of("minecraft:set_enchantments", "minecraft:enchant_randomly", "minecraft:enchant_with_levels");
    private static void audit(JsonElement element, Set<String> references, boolean root) {
        if (element == null || element.isJsonPrimitive() || element.isJsonNull()) return;
        if (element.isJsonArray()) { for (var child : element.getAsJsonArray()) audit(child, references, false); return; }
        JsonObject object = element.getAsJsonObject();
        if (object.has("type") && "minecraft:loot_table".equals(object.get("type").getAsString())) {
            var value = object.has("value") ? object.get("value") : object.get("name");
            if (value != null && value.isJsonPrimitive()) references.add(value.getAsString());
        }
        if (object.has("functions") && object.get("functions").isJsonArray()) {
            int counts = 0;
            for (var value : object.getAsJsonArray("functions")) if (value.isJsonObject()) {
                var fn = value.getAsJsonObject();
                String type = fn.has("function") ? fn.get("function").getAsString() : "unknown";
                boolean supported = ITEM_PRESERVING.contains(type);
                // These native functions preserve a known non-book leaf's item/count.
                // Its enchantment components remain unproven, but it is still e.g. a
                // fishing rod for an ingredient/action that accepts any such item.
                supported |= ENCHANTING.contains(type) && "minecraft:item".equals(string(object, "type"))
                        && !string(object, "name").isEmpty() && !"minecraft:book".equals(string(object, "name"));
                if (type.equals("minecraft:set_count")) supported = !root && object.has("type") && "minecraft:item".equals(object.get("type").getAsString())
                        && ++counts == 1 && (!fn.has("add") || !fn.get("add").getAsBoolean()) && supportedNumber(fn.get("count"));
                if (fn.has("conditions") && !fn.getAsJsonArray("conditions").isEmpty()) supported = false;
                if (!supported && !fn.has(GenerationLootEvidence.UNRESOLVED) && !fn.has(GenerationLootEvidence.CONTAINER_UNRESOLVED)) {
                    // This estimate is deliberately narrower than the block-harvest evaluator.
                    // Do not overwrite that consumer's supported state/count/function semantics.
                    fn.addProperty(GenerationLootEvidence.CONTAINER_UNRESOLVED, true);
                    fn.addProperty("essence_evidence_failure", "Unmodeled effective loot function: " + type);
                    BalancePerformance.increment("loot_unsupported_encoded_functions");
                }
            }
        }
        for (String field : List.of("rolls", "bonus_rolls")) if (object.has(field) && !supportedNumber(object.get(field))) marker(object, "Unmodeled number provider: " + field);
        for (var field : List.copyOf(object.entrySet())) if (!field.getKey().equals("functions")) audit(field.getValue(), references, false);
    }
    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }
    static boolean supportedNumber(JsonElement value) {
        if (value == null || value.isJsonNull()) return false;
        if (value.isJsonPrimitive()) return value.getAsJsonPrimitive().isNumber();
        if (!value.isJsonObject()) return false;
        var object = value.getAsJsonObject();
        String type = object.has("type") ? object.get("type").getAsString() : "";
        return type.equals("minecraft:constant") && supportedNumber(object.get("value"))
                || type.equals("minecraft:uniform") && supportedNumber(object.get("min")) && supportedNumber(object.get("max"));
    }
    private static void marker(JsonObject owner, String reason) {
        var functions = owner.has("functions") ? owner.getAsJsonArray("functions") : new JsonArray();
        var marker = new JsonObject(); marker.addProperty(GenerationLootEvidence.UNRESOLVED, true); marker.addProperty("essence_evidence_failure", reason);
        functions.add(marker); owner.add("functions", functions);
    }
    /** Exact encoded predicates/functions and nested identities, once per table, rather than per item. */
    static List<String> facts(JsonElement element) {
        Set<String> facts = new TreeSet<>(); facts(element, facts); return List.copyOf(facts);
    }
    private static void facts(JsonElement element, Set<String> facts) {
        if (element == null || element.isJsonPrimitive() || element.isJsonNull()) return;
        if (element.isJsonArray()) { for (var value : element.getAsJsonArray()) facts(value, facts); return; }
        var object = element.getAsJsonObject();
        for (String field : List.of("conditions", "functions")) if (object.has(field)) facts.add(field + "=" + object.get(field));
        if (object.has("type") && "minecraft:loot_table".equals(object.get("type").getAsString())) facts.add("nested=" + object);
        for (var field : object.entrySet()) if (!Set.of("conditions", "functions").contains(field.getKey())) facts(field.getValue(), facts);
    }
}

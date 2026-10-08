package com.mistaboom.essence_ascendance.valuation;

import java.util.*;

/** Pure estimator dependency projection, scoped to one loaded table graph. Never caches native tool validity. */
final class BlockLootToolInputs {
    record Key(String selectedId, boolean empty, Set<String> selectedTags, Map<String, Integer> enchantments) { }
    private final Set<String> literals = new HashSet<>(), tags = new HashSet<>(), visited = new HashSet<>();
    private boolean complete = true, sensitive;
    private int nodes;

    static BlockLootToolInputs inspect(Map<String, Object> root, Map<String, Map<String, Object>> tables) {
        var inputs = new BlockLootToolInputs();
        inputs.read(root, tables, 0);
        return inputs;
    }

    static BlockLootToolInputs inspectConditions(List<Object> conditions) {
        var inputs = new BlockLootToolInputs();
        inputs.readConditions(conditions, 0);
        return inputs;
    }

    private void readConditions(Object raw, int depth) {
        if (!complete) return;
        if (depth > 32 || ++nodes > 30_000) { complete = false; return; }
        if (raw instanceof List<?> list) {
            for (Object condition : list) readConditions(condition, depth + 1);
        } else if (raw instanceof Map<?, ?> map) {
            // These are exactly the evaluator's recursive/tool-reading condition forms.
            // Opaque entity/component definitions and table-ID lists are not tool selectors.
            // Full enchantment state remains in every key, including table_bonus.
            String type = Objects.toString(map.get("condition"), "");
            switch (type) {
                case "minecraft:match_tool" -> read(map.get("predicate"), Map.of(), depth + 1);
                case "minecraft:all_of", "minecraft:any_of" -> readConditions(map.get("terms"), depth + 1);
                case "minecraft:inverted" -> readConditions(map.get("term"), depth + 1);
                default -> { }
            }
        }
    }

    boolean toolSensitive() { return sensitive || !complete; }

    Key key(ProceduralBlockLoot.Context context) {
        // The estimator reads tool identity only through item selectors and empty-stack tests.
        // Unknown components remain unknown; their signals never depend on a particular nonempty tool.
        // Full enchantments are retained for table_bonus, apply_bonus and enchantment predicates.
        if (!complete) return new Key(context.toolId(), context.toolId().equals("minecraft:air"), context.toolTags(), context.enchantments());
        Set<String> selectedTags = new HashSet<>();
        for (String tag : tags) if (context.toolTags().contains(tag)) selectedTags.add(tag);
        return new Key(literals.contains(context.toolId()) ? context.toolId() : null,
                context.toolId().equals("minecraft:air"), Set.copyOf(selectedTags), context.enchantments());
    }

    private void read(Object raw, Map<String, Map<String, Object>> tables, int depth) {
        if (!complete) return;
        if (depth > 32 || ++nodes > 30_000) { complete = false; return; }
        if (raw instanceof String value) {
            if (value.startsWith("#")) tags.add(value.substring(1)); else literals.add(value);
        } else if (raw instanceof List<?> list) {
            for (Object value : list) read(value, tables, depth + 1);
        } else if (raw instanceof Map<?, ?> map) {
            sensitive |= "minecraft:match_tool".equals(map.get("condition"))
                    || "minecraft:table_bonus".equals(map.get("condition"))
                    || "minecraft:apply_bonus".equals(map.get("function"));
            if ("minecraft:loot_table".equals(map.get("type"))) {
                Object ref = map.containsKey("value") ? map.get("value") : map.get("name");
                if (ref instanceof String id && visited.add(id)) {
                    if (tables.containsKey(id)) read(tables.get(id), tables, depth + 1);
                    else complete = false;
                }
            }
            // A superset of literal selectors is deliberately conservative, including nested/inlined tables.
            for (Object value : map.values()) read(value, tables, depth + 1);
        }
    }
}

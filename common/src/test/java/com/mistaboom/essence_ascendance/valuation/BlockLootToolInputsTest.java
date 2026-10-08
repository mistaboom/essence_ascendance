package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.Gson;
import java.util.*;

/** Differential check: projection equality must imply complete estimator equality, including unknowns. */
public final class BlockLootToolInputsTest {
    private static int checks;
    public static void main(String[] args) {
        List<String> predicates = List.of(
                "{}", "{\"items\":\"fixture:pick_3\"}", "{\"items\":\"#fixture:tools\"}",
                "{\"items\":[\"fixture:pick_3\",\"#fixture:tools\"]}",
                "{\"count\":{\"min\":1}}", "{\"components\":{\"fixture:unknown\":true}}",
                "{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":1}]}}",
                "{\"predicates\":{\"fixture:opaque\":true},\"items\":\"fixture:pick_3\"}");
        for (String predicate : predicates) {
            Map<String, Object> root = json("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":["
                    + "{\"type\":\"minecraft:item\",\"name\":\"fixture:ore\",\"conditions\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":" + predicate + "}]},"
                    + "{\"type\":\"minecraft:item\",\"name\":\"fixture:stone\"}]}]}]}");
            compare(root, Map.of());
            Map<String, Object> ref = json("{\"pools\":[{\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":\"fixture:child\"}]}]}");
            compare(ref, Map.of("fixture:child", root));
        }
        var bonus = json("{\"pools\":[{\"conditions\":[{\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:silk_touch\",\"chances\":[0.1,0.8]}],\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"fixture:ore\"}]}]}");
        require(BlockLootToolInputs.inspect(bonus, Map.of()).toolSensitive(), "Enchantment-only table lost tool routes");
        compare(bonus, Map.of());
        var function = json("{\"pools\":[{\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"fixture:ore\",\"functions\":[{\"function\":\"minecraft:apply_bonus\",\"enchantment\":\"minecraft:silk_touch\",\"formula\":\"minecraft:ore_drops\"}]}]}]}");
        require(BlockLootToolInputs.inspect(function, Map.of()).toolSensitive(), "Enchantment-only function lost tool routes");
        compare(function, Map.of());
        Object deep = Map.of("items", "fixture:pick_3");
        for (int i = 0; i < 40; i++) deep = Map.of("nested", deep);
        var bounded = BlockLootToolInputs.inspect(Map.of("root", deep), Map.of());
        require(!bounded.key(context(1, false)).equals(bounded.key(context(2, false))), "Truncated dependency projection reused another tool");
        var missing = BlockLootToolInputs.inspect(Map.of("type", "minecraft:loot_table", "value", "fixture:missing"), Map.of());
        require(!missing.key(context(1, false)).equals(missing.key(context(2, false))), "Missing referenced table reused another tool");
        System.out.println("BlockLootToolInputsTest: " + checks + " differential projection, tag, enchantment and bounded fallback checks PASS");
    }
    private static void compare(Map<String, Object> root, Map<String, Map<String, Object>> tables) {
        var inputs = BlockLootToolInputs.inspect(root, tables);
        Map<BlockLootToolInputs.Key, Map<String, ProceduralBlockLoot.Drop>> cache = new HashMap<>();
        for (int tool = 0; tool < 100; tool++) for (boolean silk : List.of(false, true)) {
            var context = context(tool, silk);
            var direct = ProceduralBlockLoot.estimate(root, context, tables::get, ignored -> List.of());
            var cached = cache.computeIfAbsent(inputs.key(context), ignored -> direct);
            require(direct.equals(cached), "Projection changed loot or uncertainty for tool " + tool + ": " + root);
        }
        require(cache.size() <= 12, "Equivalent tools were not collapsed: " + cache.size());
    }
    private static ProceduralBlockLoot.Context context(int tool, boolean silk) {
        return new ProceduralBlockLoot.Context("fixture:ore_block", Map.of(), Set.of(), tool == 0 ? "minecraft:air" : "fixture:pick_" + tool,
                tool % 2 == 0 ? Set.of("fixture:tools", "fixture:irrelevant_" + tool) : Set.of("fixture:irrelevant_" + tool),
                silk ? Map.of("minecraft:silk_touch", 1) : Map.of());
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> json(String text) { return new Gson().fromJson(text, Map.class); }
    private static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}

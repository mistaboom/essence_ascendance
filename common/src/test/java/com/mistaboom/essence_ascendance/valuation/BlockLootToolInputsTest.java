package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.Gson;
import java.util.*;

/** Differential check: projection equality must imply complete estimator equality, including unknowns. */
public final class BlockLootToolInputsTest {
    private static int checks;
    public static void main(String[] args) {
        nativeAbilityPredicates();
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
    private static void nativeAbilityPredicates() {
        var ability = Map.<String, Object>of("condition", "neoforge:can_item_perform_ability", "ability", "shears_dig");
        var inverted = Map.<String, Object>of("condition", "minecraft:inverted", "term", ability);
        var bare = context(1, false);
        var shearsFacts = new NativeToolFacts(false, Set.of(), Set.of(), Set.of(), Set.of(), Map.of("shears_dig", true));
        var otherFacts = new NativeToolFacts(false, Set.of(), Set.of(), Set.of(), Set.of(), Map.of("shears_dig", false));
        var shears = bare.withToolFacts(shearsFacts);
        var other = bare.withToolFacts(otherFacts);
        require(ProceduralBlockLoot.conditionsProven(List.of(ability), shears), "Audited native shears ability was not admitted");
        require(!ProceduralBlockLoot.conditionsMayApply(List.of(ability), other), "Known incompatible tool passed shears ability");
        require(ProceduralBlockLoot.conditionsProven(List.of(inverted), other), "Known native negative predicate did not unlock ordinary leaf drops");
        require(!ProceduralBlockLoot.conditionsMayApply(List.of(inverted), shears), "Inverted shears predicate accepted shears");
        require(!ProceduralBlockLoot.conditionsProven(List.of(inverted), bare), "Unknown tool implementation was assumed not to shear");
        require(!ProceduralBlockLoot.conditionsProven(List.of(Map.of("condition", "neoforge:can_item_perform_ability", "ability", "fixture:opaque")), shears),
                "Unrecognized native ability was admitted");
        require(!ProceduralBlockLoot.conditionsProven(List.of(Map.of("condition", "neoforge:can_item_perform_ability", "ability", "shears_dig", "opaque", true)), shears),
                "Extra native predicate fields were discarded");
        var table = Map.<String, Object>of("pools", List.of(Map.of("entries", List.of(
                Map.of("type", "minecraft:alternatives", "children", List.of(
                        Map.of("type", "minecraft:item", "name", "fixture:leaves", "conditions", List.of(ability)),
                        Map.of("type", "minecraft:item", "name", "fixture:sapling", "conditions", List.of(inverted))))))));
        var projection = BlockLootToolInputs.inspect(table, Map.of());
        require(projection.toolSensitive(), "Ability-only loot table discarded alternate tool contexts");
        require(!projection.key(shears).equals(projection.key(other)) && !projection.key(other).equals(projection.key(bare)),
                "Native ability true/false/unknown shared a loot cache key");
        var conditionProjection = BlockLootToolInputs.inspectConditions(List.of(inverted));
        require(!conditionProjection.key(shears).equals(conditionProjection.key(other)), "Modifier condition cache lost nested native ability facts");
        var sheared = ProceduralBlockLoot.estimate(table, shears, ignored -> null, ignored -> List.of());
        var ordinary = ProceduralBlockLoot.estimate(table, other, ignored -> null, ignored -> List.of());
        require(sheared.keySet().equals(Set.of("fixture:leaves")) && sheared.get("fixture:leaves").unresolved() == 0,
                "Native shears first-success loot branch was not exact");
        require(ordinary.keySet().equals(Set.of("fixture:sapling")) && ordinary.get("fixture:sapling").unresolved() == 0,
                "Native ordinary leaf drop remained unknown or double-counted sheared leaves");
        require(NativeToolFacts.nativeActionSet("net.minecraft.world.item.ShearsItem").equals("DEFAULT_SHEARS_ACTIONS")
                        && NativeToolFacts.nativeActionSet("fixture.CustomShears") == null,
                "Custom item callback owner borrowed native shears evidence");
        require(shearsFacts.withoutAccessories(ignored -> "").outsideGiantMiningTick(ignored -> "").abilities().equals(shearsFacts.abilities()),
                "Additional scenario facts erased known native abilities");
        require(NativeToolFacts.readNativeAbilities("fixture.CustomShears", () -> {
            throw new AssertionError("Unsupported runtime class method signatures were inspected");
        }).isEmpty(), "Custom tool class must be excluded before optional reflection");
        require(NativeToolFacts.readNativeAbilities("net.minecraft.world.item.ShearsItem", () -> {
            throw new RuntimeException("Attempted to load client-only class for invalid distribution DEDICATED_SERVER");
        }).isEmpty(), "Loader RuntimeException escaped optional native ability evidence");
        require(NativeToolFacts.readNativeAbilities("net.minecraft.world.item.ShearsItem", () -> {
            throw new NoClassDefFoundError("net/minecraft/client/multiplayer/ClientLevel");
        }).isEmpty(), "Unavailable method signature escaped optional native ability evidence");
        require(NativeToolFacts.readNativeAbilities("net.minecraft.world.item.ShearsItem", () -> Map.of("shears_dig", true))
                        .equals(Map.of("shears_dig", true)), "Exact native runtime class lost admitted ability evidence");
        require(NativeToolFacts.readNativeAbilities("net.minecraft.world.item.AirItem", () -> Map.of("shears_dig", false))
                        .equals(Map.of("shears_dig", false)), "Exact native empty-hand class lost negative ability evidence");
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

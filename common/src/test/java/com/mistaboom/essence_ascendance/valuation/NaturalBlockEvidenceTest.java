package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Checks source reachability and typed implicit placement, without generating a world. */
public final class NaturalBlockEvidenceTest {
    private static int assertions;
    private static final ResourceLocation COCOA = id("minecraft:cocoa");
    private static final ResourceLocation TREE = id("fixture:jungle_tree");
    private static final ResourceLocation PLACED = id("fixture:jungle_placed");
    private static final Map<ResourceLocation, JsonObject> BIOMES = Map.of(id("fixture:jungle"),
            json("{\"features\":[[\"fixture:jungle_placed\"]]}"));

    public static void main(String[] args) {
        JsonObject positive = tree("minecraft:tree", "minecraft:cocoa", "0.2");
        ProceduralNaturalBlockIndex natural = index(positive, "[]");
        check(natural.contains(COCOA), "positive loaded tree decorator is natural cocoa evidence");
        check(natural.signals(COCOA).stream().anyMatch(s -> s.contains("fixture:jungle_tree")
                && s.contains("minecraft:cocoa tree decorator")), "source records feature and typed decorator");
        check(!natural.contains(id("minecraft:cocoa_beans")), "decorator places a block, not an assumed loot item");
        check(index(tree("tree", "cocoa", "1"), "[]").contains(COCOA),
                "default Minecraft namespace is accepted by the registry codec");
        for (String probability : new String[]{"0", "-0.1", "1.1", "null", "\"0.2\"", "{}", "true"}) {
            check(!index(tree("minecraft:tree", "minecraft:cocoa", probability), "[]").contains(COCOA),
                    "invalid or zero probability cannot provide cocoa: " + probability);
        }
        JsonObject missingProbability = tree("minecraft:tree", "minecraft:cocoa", "0.2");
        missingProbability.getAsJsonObject("config").getAsJsonArray("decorators")
                .get(0).getAsJsonObject().remove("probability");
        check(!index(missingProbability, "[]").contains(COCOA), "required probability cannot be absent");
        check(!index(tree("fixture:tree", "minecraft:cocoa", "0.2"), "[]").contains(COCOA),
                "a modded feature is not inferred to have vanilla tree semantics");
        check(!index(tree("minecraft:tree", "fixture:cocoa", "0.2"), "[]").contains(COCOA),
                "a modded decorator is not inferred to place vanilla cocoa");
        check(!index(tree("minecraft:ore", "minecraft:cocoa", "0.2"), "[]").contains(COCOA),
                "decorator-looking data outside a tree is not evidence");
        check(!index(positive, "[{\"type\":\"minecraft:count\",\"count\":0}]").contains(COCOA),
                "zero placement count prevents implied decorator evidence");
        check(!ProceduralNaturalBlockIndex.fromData(Map.of(), Map.of(PLACED, placed("[]")),
                Map.of(TREE, positive), Map.of()).contains(COCOA), "unreachable tree definition provides no evidence");

        JsonObject selector = json("""
                {"type":"minecraft:random_selector","config":{"features":[
                  {"chance":0.5,"feature":{"feature":"fixture:jungle_tree","placement":[]}}
                ]}}
                """);
        check(index(selector, positive).contains(COCOA), "nested reachable tree has decorator evidence");
        selector.getAsJsonObject("config").getAsJsonArray("features").get(0).getAsJsonObject()
                .addProperty("chance", 0);
        check(!index(selector, positive).contains(COCOA), "zero-probability selector branch is unreachable");

        JsonObject explicitState = json("""
                {"type":"fixture:feature","config":{"state":{"Name":"fixture:natural_block"},
                  "target":{"Name":"fixture:replacement_target"}}}
                """);
        ProceduralNaturalBlockIndex explicit = index(explicitState, "[]");
        check(explicit.contains(id("fixture:natural_block")), "generic explicit state evidence still works");
        check(!explicit.contains(id("fixture:replacement_target")), "replacement predicate is not placement");
        System.out.println("NaturalBlockEvidenceTest: " + assertions + " source reachability and decorator checks PASS");
    }

    private static ProceduralNaturalBlockIndex index(JsonObject tree, String placement) {
        return ProceduralNaturalBlockIndex.fromData(BIOMES, Map.of(PLACED, placed(placement)),
                Map.of(TREE, tree), Map.of());
    }

    private static ProceduralNaturalBlockIndex index(JsonObject selector, JsonObject tree) {
        return ProceduralNaturalBlockIndex.fromData(BIOMES,
                Map.of(PLACED, json("{\"feature\":\"fixture:selector\",\"placement\":[]}")),
                Map.of(id("fixture:selector"), selector, TREE, tree), Map.of());
    }

    private static JsonObject tree(String featureType, String decoratorType, String probability) {
        return json("{\"type\":\"" + featureType + "\",\"config\":{\"decorators\":[{\"type\":\""
                + decoratorType + "\",\"probability\":" + probability + "}]}}");
    }

    private static JsonObject placed(String placement) {
        return json("{\"feature\":\"fixture:jungle_tree\",\"placement\":" + placement + "}");
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void check(boolean condition, String detail) {
        assertions++;
        if (!condition) throw new AssertionError(detail);
    }
}

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
        var overworld = id("minecraft:overworld");
        var otherOnly = Map.of(id("fixture:locked_dimension"), json("{\"default_block\":{\"Name\":\"minecraft:deepslate\"}}"));
        check(!ProceduralNaturalBlockIndex.inDimension(overworld, Map.of(), Map.of(), Map.of(), otherOnly)
                .contains(id("minecraft:deepslate")), "Loaded terrain in an unproved dimension cannot establish starting-world access");
        check(ProceduralNaturalBlockIndex.inDimension(overworld, Map.of(), Map.of(), Map.of(),
                Map.of(overworld, otherOnly.values().iterator().next())).contains(id("minecraft:deepslate")), "Actual starting-world terrain remains available");
        var otherBiome = Map.of(id("fixture:locked_dimension/biome/minecraft/jungle"), BIOMES.values().iterator().next());
        check(!ProceduralNaturalBlockIndex.inDimension(overworld, otherBiome, Map.of(PLACED, placed("[]")), Map.of(TREE, positive), Map.of())
                .contains(COCOA), "Another dimension's biome cannot seed a starting-world feature");
        implicitNativeFeatures();
        System.out.println("NaturalBlockEvidenceTest: " + assertions + " source reachability and native placement checks PASS");
    }

    private static void implicitNativeFeatures() {
        var cases = Map.of(
                "chorus_plant", java.util.List.of("chorus_plant", "chorus_flower"),
                "weeping_vines", java.util.List.of("nether_wart_block", "weeping_vines", "weeping_vines_plant"),
                "twisting_vines", java.util.List.of("twisting_vines", "twisting_vines_plant"));
        for (var entry : cases.entrySet()) {
            String type = entry.getKey();
            JsonObject feature = json("{\"type\":\"minecraft:" + type + "\",\"config\":"
                    + (type.equals("twisting_vines") ? "{\"spread_width\":8,\"spread_height\":4,\"max_height\":8}" : "{}") + "}");
            var loaded = index(feature, "[]");
            for (String block : entry.getValue()) {
                check(loaded.contains(id("minecraft:" + block)), "Loaded native feature places " + block);
                check(loaded.signals(id("minecraft:" + block)).stream().anyMatch(s -> s.contains("native minecraft:" + type)),
                        "Implicit native placement keeps its typed provenance");
                check(!ProceduralNaturalBlockIndex.fromData(Map.of(), Map.of(PLACED, placed("[]")),
                        Map.of(TREE, feature), Map.of()).contains(id("minecraft:" + block)),
                        "An unloaded native feature does not establish " + block);
                check(!index(feature, "[{\"type\":\"minecraft:count\",\"count\":0}]").contains(id("minecraft:" + block)),
                        "Disabled placement cannot establish " + block);
            }
            var selector = json("{\"type\":\"minecraft:random_selector\",\"config\":{\"features\":["
                    + "{\"chance\":0,\"feature\":{\"feature\":\"fixture:jungle_tree\",\"placement\":[]}}]}}");
            check(!index(selector, feature).contains(id("minecraft:" + entry.getValue().getFirst())),
                    "A zero-probability selector cannot reach native " + type);
            feature.addProperty("type", "fixture:" + type);
            check(!index(feature, "[]").contains(id("minecraft:" + entry.getValue().getFirst())),
                    "A similar modded name does not inherit native placement semantics");
        }
        var chorus = index(json("{\"type\":\"chorus_plant\",\"config\":{}}"), "[]");
        check(chorus.contains(id("minecraft:chorus_plant")) && !chorus.contains(id("minecraft:chorus_fruit"))
                        && !chorus.contains(id("minecraft:end_stone")),
                "Default namespace works; outputs are placed blocks, not loot or required substrate");
        for (String field : java.util.List.of("spread_width", "spread_height", "max_height")) {
            for (String invalid : java.util.List.of("0", "-1", "1.5", "null", "\"8\"", "true")) {
                var feature = json("{\"type\":\"minecraft:twisting_vines\",\"config\":{"
                        + "\"spread_width\":8,\"spread_height\":4,\"max_height\":8}}");
                feature.getAsJsonObject("config").add(field, JsonParser.parseString(invalid));
                check(!index(feature, "[]").contains(id("minecraft:twisting_vines")),
                        "Invalid native feature config cannot imply placement: " + field + "=" + invalid);
            }
        }
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

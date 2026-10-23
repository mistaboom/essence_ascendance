package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
//? if >=26.1.2
/*import net.minecraft.world.item.ToolMaterial;*/
//? if <26.1.2
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Explicit family mapping and bounded native-property policy; no server or acquisition simulation. */
public final class MaterialPropertyEstimatesTest {
    private static int checks;
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        boundedPolicy(); explicitFamilies(); bandsAndInvalidSources();
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); nativeProperties();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("MaterialPropertyEstimatesTest: " + checks + " property/family policy checks PASS");
    }
    private static MaterialPropertyEstimates.OreProperties ore(int tier, double hardness) {
        return new MaterialPropertyEstimates.OreProperties(tier, hardness);
    }
    private static double factor(int tier, double hardness, MaterialPropertyEstimates.YieldBand band) {
        return MaterialPropertyEstimates.factor(tier, hardness, band);
    }
    private static void boundedPolicy() {
        var normal = MaterialPropertyEstimates.YieldBand.SINGULAR;
        for (int tier = 0; tier <= 3; tier++) {
            double unit = 1 << tier;
            check(factor(tier, 0, normal) == unit && factor(tier, 3, normal) == unit,
                    "Low hardness never creates a negative/zero tier estimate");
            check(factor(tier, 12, normal) == 2 * unit, "Hardness uses a square-root policy");
            for (double hardness : List.of(48.0, 1000.0, Double.MAX_VALUE))
                check(factor(tier, hardness, normal) == 4 * unit, "Finite outlier hardness saturates at fourfold");
            check(factor(tier, 3, MaterialPropertyEstimates.YieldBand.DENSE) == .25 * unit
                            && factor(tier, 3, MaterialPropertyEstimates.YieldBand.SPARSE) == 4 * unit,
                    "Declared dense/sparse bands have exact policy weights, not claimed drop counts");
        }
        for (double hardness : List.of(-1.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY))
            check(Double.isNaN(factor(2, hardness, normal)), "Invalid or unbreakable hardness has no property estimate");
        for (int tier : List.of(-1, 4, Integer.MIN_VALUE, Integer.MAX_VALUE))
            check(Double.isNaN(factor(tier, 3, normal)), "Unknown or unsupported mining requirements retain fallback");
    }
    private static void explicitFamilies() {
        var properties = Map.of("opaque:a", ore(2,3), "unrelated:b", ore(2,12), "third:c", ore(0,3),
                "name:diamond_ore", ore(3,48));
        Map<String,List<String>> tags = new LinkedHashMap<>();
        tags.put("c:ores/q", List.of("unrelated:b", "opaque:a"));
        tags.put("c:ores/r", List.of("third:c"));
        for (String form : List.of("raw_materials", "ingots", "gems", "dusts", "nuggets"))
            tags.put("c:" + form + "/q", List.of("elsewhere:" + form));
        tags.put("c:gems/r", List.of("elsewhere:gems"));
        tags.put("c:ores_in_ground/stone", List.of("name:diamond_ore"));
        tags.put("other:ores/q", List.of("name:diamond_ore"));
        tags.put("c:raw_materials/missing", List.of("name:raw_diamond"));
        var values = MaterialPropertyEstimates.fromProperties(properties, tags);
        check(values.get("opaque:a") == 4 && values.get("unrelated:b") == 4,
                "Ordinary and harder variants share the cheapest explicitly declared family factor");
        for (String form : List.of("raw_materials", "ingots", "dusts", "nuggets"))
            check(values.get("elsewhere:" + form) == 4, "Opaque IDs bridge only through explicit conventional suffixes");
        check(values.get("elsewhere:gems") == 1, "Overlapping declared families select the lowest valid source");
        check(!values.containsKey("name:diamond_ore") && !values.containsKey("name:raw_diamond"),
                "Names, non-common namespaces, source-category tags and absent ore families cannot create links");
        var reversed = new LinkedHashMap<String,List<String>>();
        var entries = new ArrayList<>(tags.entrySet()); Collections.reverse(entries);
        for (var entry : entries) { var members = new ArrayList<>(entry.getValue()); Collections.reverse(members); reversed.put(entry.getKey(),members); }
        check(values.equals(MaterialPropertyEstimates.fromProperties(properties, reversed)),
                "Family iteration and member order do not alter factors");
        check(new ArrayList<>(values.keySet()).equals(values.keySet().stream().sorted().toList()), "Published factor order is deterministic");
    }
    private static void bandsAndInvalidSources() {
        var properties = Map.of("x:a",ore(1,3),"x:b",ore(1,3),"x:c",ore(1,3),"x:bad",ore(3,-1),"x:unknown",ore(-1,3));
        var tags = Map.of("c:ores",List.of("x:a","x:b","x:c"),
                "c:ore_rates/dense",List.of("x:a"),"c:ore_rates/sparse",List.of("x:c"),
                "c:ores/q",List.of("x:b","x:bad","x:unknown","missing:x"),"c:ingots/q",List.of("x:product"));
        var values = MaterialPropertyEstimates.fromProperties(properties,tags);
        check(values.get("x:a") == .5 && values.get("x:b") == 2 && values.get("x:c") == 8,
                "Dense, absent/singular and sparse tags alter coarse policy in the declared direction");
        check(values.get("x:product") == 2 && !values.containsKey("x:bad") && !values.containsKey("x:unknown"),
                "Invalid, missing and unknown ore members neither receive boosts nor cheapen valid family anchors");
        var conflicting = new HashMap<>(tags); conflicting.put("c:ore_rates/dense",List.of("x:a","x:c"));
        check(MaterialPropertyEstimates.fromProperties(properties,conflicting).get("x:c") == .5,
                "Conflicting yield tags use the lower policy band without multiplying contradictory claims");
        check(MaterialPropertyEstimates.fromProperties(properties,Map.of("c:ore_rates/dense",List.of("x:a"))).isEmpty(),
                "Yield tags alone never establish an ore or material family");
    }
    private static void nativeProperties() {
        Map<TagKey<Block>,List<Holder<Block>>> before = new HashMap<>();
        before.putAll(com.mistaboom.essence_ascendance.verification.RegistryTagTestCompat.snapshot(BuiltInRegistries.BLOCK));
        var blockTags = new HashMap<>(before);
        blockTags.put(ProceduralValuationTags.NEEDS_STONE_TOOL,List.of(Blocks.IRON_ORE.builtInRegistryHolder()));
        blockTags.put(ProceduralValuationTags.NEEDS_IRON_TOOL,List.of(Blocks.DIAMOND_ORE.builtInRegistryHolder(),Blocks.DEEPSLATE_DIAMOND_ORE.builtInRegistryHolder()));
        blockTags.put(ProceduralValuationTags.NEEDS_DIAMOND_TOOL,List.of(Blocks.ANCIENT_DEBRIS.builtInRegistryHolder()));
        //? if >=26.1.2
        /*blockTags.put(ToolMaterial.WOOD.incorrectBlocksForDrops(),List.of(Blocks.COPPER_ORE.builtInRegistryHolder()));*/
        //? if <26.1.2
        blockTags.put(Tiers.WOOD.getIncorrectBlocksForDrops(),List.of(Blocks.COPPER_ORE.builtInRegistryHolder()));
        //? if >=26.1.2
        /*blockTags.put(ToolMaterial.NETHERITE.incorrectBlocksForDrops(),List.of(Blocks.COAL_ORE.builtInRegistryHolder()));*/
        //? if <26.1.2
        blockTags.put(Tiers.NETHERITE.getIncorrectBlocksForDrops(),List.of(Blocks.COAL_ORE.builtInRegistryHolder()));
        try {
            com.mistaboom.essence_ascendance.verification.RegistryTagTestCompat.bind(BuiltInRegistries.BLOCK, blockTags);
            var items = List.of(Items.IRON_ORE,Items.DIAMOND_ORE,Items.DEEPSLATE_DIAMOND_ORE,Items.DIAMOND,
                    Items.ANCIENT_DEBRIS,Items.COPPER_ORE,Items.COAL_ORE,Items.BEDROCK,Items.PAPER);
            var tags = Map.of("c:ores",List.of("minecraft:iron_ore","minecraft:ancient_debris","minecraft:copper_ore","minecraft:coal_ore","minecraft:bedrock","minecraft:paper"),
                    "c:ores/q",List.of("minecraft:diamond_ore","minecraft:deepslate_diamond_ore"),
                    "c:gems/q",List.of("minecraft:diamond","unregistered:x"));
            var values = MaterialPropertyEstimates.collect(items,tags);
            check(values.get("minecraft:iron_ore") == 2 && values.get("minecraft:diamond_ore") == 4,
                    "Actual registered block properties and bound native mining tags reach the collector");
            check(values.get("minecraft:diamond") == 4 && values.get("minecraft:deepslate_diamond_ore") == 4,
                    "Native harder variants and explicit gem family retain the cheapest source estimate");
            check(values.get("minecraft:ancient_debris") > 8 && values.get("minecraft:ancient_debris") <= 32,
                    "Actual hard native ore receives a bounded hardness estimate");
            for (String id : List.of("minecraft:copper_ore","minecraft:coal_ore","minecraft:bedrock","minecraft:paper","unregistered:x"))
                check(!values.containsKey(id), "Unsupported tier, unbreakable, non-block or absent registry item remains unestimated: " + id);
            check(MaterialPropertyEstimates.collect(items,Map.of()).isEmpty(), "Native block identity alone never guesses ore membership");
        } finally { com.mistaboom.essence_ascendance.verification.RegistryTagTestCompat.bind(BuiltInRegistries.BLOCK, before); }
    }
    private static void check(boolean test,String message) { checks++; if (!test) throw new AssertionError(message); }
}

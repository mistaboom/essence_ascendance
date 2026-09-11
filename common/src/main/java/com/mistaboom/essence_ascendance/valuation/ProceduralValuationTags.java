package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/*
 * Loader-neutral tag keys used by the procedural valuation engine.
 *
 * The c: namespace is intentionally referenced by ID instead of importing a
 * Fabric- or NeoForge-specific convention-tag class. Both loaders can resolve
 * the same data tags while common gameplay code remains platform-neutral.
 */
final class ProceduralValuationTags {

    static final TagKey<Item> ORES = item("c", "ores");
    static final TagKey<Item> RAW_MATERIALS = item("c", "raw_materials");
    static final TagKey<Item> INGOTS = item("c", "ingots");
    static final TagKey<Item> GEMS = item("c", "gems");
    static final TagKey<Item> NUGGETS = item("c", "nuggets");
    static final TagKey<Item> STORAGE_BLOCKS = item("c", "storage_blocks");
    static final TagKey<Item> FOODS = item("c", "foods");
    static final TagKey<Item> ARMORS = item("c", "armors");
    static final TagKey<Item> MINING_TOOLS = item("c", "tools/mining_tool");
    static final TagKey<Item> MELEE_WEAPONS = item("c", "tools/melee_weapon");
    static final TagKey<Item> RANGED_WEAPONS = item("c", "tools/ranged_weapon");
    static final TagKey<Item> REDSTONE_DUSTS = item("c", "dusts/redstone");
    // Functional vanilla tags complement convention tags on both loaders.
    // Do not use enchantability or mineable/<tool> tags as item-function proof.
    static final TagKey<Item> AXES = item("minecraft", "axes");
    static final TagKey<Item> PICKAXES = item("minecraft", "pickaxes");
    static final TagKey<Item> SHOVELS = item("minecraft", "shovels");
    static final TagKey<Item> HOES = item("minecraft", "hoes");
    static final TagKey<Item> SWORDS = item("minecraft", "swords");
    static final TagKey<Item> ARROWS = item("minecraft", "arrows");
    static final TagKey<Item> BEDS = item("minecraft", "beds");
    static final TagKey<Item> WOOL = item("minecraft", "wool");
    static final TagKey<Item> WOOL_CARPETS = item("minecraft", "wool_carpets");
    static final TagKey<Item> FENCES = item("minecraft", "fences");
    static final TagKey<Item> FENCE_GATES = item("minecraft", "fence_gates");
    static final TagKey<Item> WALLS = item("minecraft", "walls");
    static final TagKey<Item> DOORS = item("minecraft", "doors");
    static final TagKey<Item> TRAPDOORS = item("minecraft", "trapdoors");
    static final TagKey<Item> FLOWERS = item("minecraft", "flowers");
    static final TagKey<Block> CLIMBABLE = block("minecraft", "climbable");
    static final TagKey<Item> FERTILIZERS = item("c", "fertilizers");
    static final TagKey<Item> CROPS = item("c", "crops");
    static final TagKey<Item> SEEDS = item("c", "seeds");
    static final TagKey<Item> SAPLINGS = item("minecraft", "saplings");
    static final TagKey<Item> LOGS = item("minecraft", "logs");
    static final TagKey<Item> LEAVES = item("minecraft", "leaves");

    static final TagKey<Item> ORES_IN_STONE = item("c", "ores_in_ground/stone");
    static final TagKey<Item> ORES_IN_DEEPSLATE = item("c", "ores_in_ground/deepslate");
    static final TagKey<Item> ORES_IN_NETHERRACK = item("c", "ores_in_ground/netherrack");
    static final TagKey<Item> ORES_IN_END_STONE = item("c", "ores_in_ground/end_stone");
    static final TagKey<Item> ORE_RATE_DENSE = item("c", "ore_rates/dense");
    static final TagKey<Item> ORE_RATE_SINGULAR = item("c", "ore_rates/singular");
    static final TagKey<Item> ORE_RATE_SPARSE = item("c", "ore_rates/sparse");

    static final TagKey<Block> BLOCK_ORES = block("c", "ores");
    static final TagKey<Block> BLOCK_ORES_IN_STONE = block("c", "ores_in_ground/stone");
    static final TagKey<Block> BLOCK_ORES_IN_DEEPSLATE = block("c", "ores_in_ground/deepslate");
    static final TagKey<Block> BLOCK_ORES_IN_NETHERRACK = block("c", "ores_in_ground/netherrack");
    static final TagKey<Block> BLOCK_ORES_IN_END_STONE = block("c", "ores_in_ground/end_stone");
    static final TagKey<Block> BLOCK_ORE_RATE_DENSE = block("c", "ore_rates/dense");
    static final TagKey<Block> BLOCK_ORE_RATE_SINGULAR = block("c", "ore_rates/singular");
    static final TagKey<Block> BLOCK_ORE_RATE_SPARSE = block("c", "ore_rates/sparse");
    static final TagKey<Block> NEEDS_STONE_TOOL = block("minecraft", "needs_stone_tool");
    static final TagKey<Block> NEEDS_IRON_TOOL = block("minecraft", "needs_iron_tool");
    static final TagKey<Block> NEEDS_DIAMOND_TOOL = block("minecraft", "needs_diamond_tool");

    private ProceduralValuationTags() {
    }

    private static TagKey<Item> item(String namespace, String path) {
        return TagKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(namespace, path)
        );
    }

    private static TagKey<Block> block(String namespace, String path) {
        return TagKey.create(
                Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(namespace, path)
        );
    }
}

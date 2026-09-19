package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dimension-aware ore catalog for Nature's Boon. Convention/custom tags define
 * pack content, while the existing procedural economy supplies relative rarity
 * weighting so ore values are not maintained in a second balancing table.
 */
public final class NaturalOreDropService {
    private static final TagKey<Block> STONE_ORES = blockTag("c", "ores_in_ground/stone");
    private static final TagKey<Block> DEEPSLATE_ORES = blockTag("c", "ores_in_ground/deepslate");
    private static final TagKey<Block> NETHERRACK_ORES = blockTag("c", "ores_in_ground/netherrack");
    private static final TagKey<Block> END_STONE_ORES = blockTag("c", "ores_in_ground/end_stone");

    /* The valuation engine already owns invalidation; identity changes whenever it rebuilds. */
    private static volatile List<?> valueSnapshot = List.of();
    private static volatile Map<ResourceLocation, Long> valueByItem = Map.of();

    private NaturalOreDropService() { }

    public static boolean eligibleSource(ServerLevel level, BlockState state) {
        Ground ground = Ground.resolve(level.dimension());
        if (ground == Ground.OVERWORLD && state.is(BlockTags.BASE_STONE_OVERWORLD)) return true;
        if (ground == Ground.NETHER && state.is(BlockTags.BASE_STONE_NETHER)) return true;
        if (ground == Ground.END && state.is(Blocks.END_STONE)) return true;
        return state.is(dimensionTag("sources", level.dimension()));
    }

    public static boolean dropRandomOre(ServerPlayer player, BlockPos pos, BlockState source, ItemStack tool) {
        ServerLevel level = player.serverLevel();
        if (!eligibleSource(level, source)) return false;
        List<Candidate> candidates = candidates(player, level.dimension());
        if (candidates.isEmpty()) return false;
        Candidate selected = weighted(candidates, player.getRandom().nextDouble());
        if (selected == null) return false;
        List<ItemStack> drops = Block.getDrops(selected.block().defaultBlockState(), level,
                pos, null, player, tool == null ? ItemStack.EMPTY : tool);
        boolean produced = false;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            Block.popResource(level, pos, drop);
            produced = true;
        }
        return produced;
    }

    public static int candidateCount(ServerPlayer player) { return candidates(player, player.serverLevel().dimension()).size(); }

    private static List<Candidate> candidates(ServerPlayer player, ResourceKey<Level> dimension) {
        Ground ground = Ground.resolve(dimension);
        LinkedHashSet<Block> blocks = new LinkedHashSet<>();
        switch (ground) {
            case OVERWORLD -> {
                addTagged(blocks, STONE_ORES);
                addTagged(blocks, DEEPSLATE_ORES);
            }
            case NETHER -> addTagged(blocks, NETHERRACK_ORES);
            case END -> addTagged(blocks, END_STONE_ORES);
            case CUSTOM -> { }
        }
        addTagged(blocks, dimensionTag("ores", dimension));
        addVanillaFallbacks(blocks, ground);

        Map<ResourceLocation, Long> values = valuationValues(player);
        List<Candidate> result = new ArrayList<>();
        for (Block block : blocks) {
            Item item = block.asItem();
            if (item == Items.AIR) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            long value = Math.max(1L, values.getOrDefault(itemId, 1L));
            result.add(new Candidate(block, 1.0 / value));
        }
        return List.copyOf(result);
    }

    private static Map<ResourceLocation, Long> valuationValues(ServerPlayer player) {
        var snapshot = ProceduralValuationEngine.evaluateAll(player.server);
        if (snapshot != valueSnapshot) {
            synchronized (NaturalOreDropService.class) {
                if (snapshot != valueSnapshot) {
                    valueByItem = Map.copyOf(snapshot.stream().collect(Collectors.toMap(
                            result -> result.itemId(), result -> result.totalValue(), Math::min)));
                    valueSnapshot = snapshot;
                }
            }
        }
        return valueByItem;
    }

    private static void addTagged(LinkedHashSet<Block> blocks, TagKey<Block> tag) {
        BuiltInRegistries.BLOCK.getTag(tag).ifPresent(entries ->
                entries.forEach(holder -> blocks.add(holder.value())));
    }

    private static Candidate weighted(List<Candidate> candidates, double unit) {
        double total = candidates.stream().mapToDouble(Candidate::weight).sum();
        if (!(total > 0) || !Double.isFinite(total)) return null;
        double cursor = Math.clamp(unit, 0, Math.nextDown(1.0)) * total;
        for (Candidate candidate : candidates) {
            cursor -= candidate.weight();
            if (cursor < 0) return candidate;
        }
        return candidates.getLast();
    }

    private static void addVanillaFallbacks(LinkedHashSet<Block> blocks, Ground ground) {
        if (ground == Ground.OVERWORLD) {
            blocks.addAll(List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
                    Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                    Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                    Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE));
        } else if (ground == Ground.NETHER) {
            blocks.addAll(List.of(Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS));
        }
    }

    private static TagKey<Block> dimensionTag(String branch, ResourceKey<Level> dimension) {
        ResourceLocation id = dimension.location();
        return blockTag(EssenceAscendance.MOD_ID,
                "natures_boon/" + branch + "/" + id.getNamespace() + "/" + id.getPath());
    }

    private static TagKey<Block> blockTag(String namespace, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private record Candidate(Block block, double weight) { }

    private enum Ground {
        OVERWORLD, NETHER, END, CUSTOM;
        static Ground resolve(ResourceKey<Level> dimension) {
            if (dimension.equals(Level.OVERWORLD)) return OVERWORLD;
            if (dimension.equals(Level.NETHER)) return NETHER;
            if (dimension.equals(Level.END)) return END;
            return CUSTOM;
        }
    }
}

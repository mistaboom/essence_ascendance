package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.ore.LatentOreBlock;
import com.mistaboom.essence_ascendance.ore.LatentOreBlockEntity;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationEngine;
import com.mistaboom.essence_ascendance.worldgen.PrimarySubstrateDiscovery;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared dimension-aware natural-ore catalog for Gathering skills. Convention/custom
 * tags define pack content, while the existing procedural economy supplies relative
 * rarity weighting so ore values are not maintained in a second balancing table.
 */
public final class NaturalOreDropService {
    private static final TagKey<Block> ORES = blockTag("c", "ores");
    private static final TagKey<Block> STONE_ORES = blockTag("c", "ores_in_ground/stone");
    private static final TagKey<Block> DEEPSLATE_ORES = blockTag("c", "ores_in_ground/deepslate");
    private static final TagKey<Block> NETHERRACK_ORES = blockTag("c", "ores_in_ground/netherrack");
    private static final TagKey<Block> END_STONE_ORES = blockTag("c", "ores_in_ground/end_stone");

    /* The valuation engine already owns invalidation; identity changes whenever it rebuilds. */
    private static volatile List<?> valueSnapshot = List.of();
    private static volatile Map<ResourceLocation, Long> valueByItem = Map.of();

    private NaturalOreDropService() { }

    public static boolean eligibleSource(ServerLevel level, BlockState state) {
        if (PrimarySubstrateDiscovery.selection(level).hosts().containsKey(state.getBlock())) return true;
        Ground ground = Ground.resolve(level.dimension());
        if (ground == Ground.OVERWORLD && state.is(BlockTags.BASE_STONE_OVERWORLD)) return true;
        if (ground == Ground.NETHER && state.is(BlockTags.BASE_STONE_NETHER)) return true;
        if (ground == Ground.END && state.is(Blocks.END_STONE)) return true;
        return state.is(dimensionTag("sources", level.dimension()));
    }

    public static boolean dropRandomOre(ServerPlayer player, BlockPos pos, BlockState source, ItemStack tool) {
        ServerLevel level = player.serverLevel();
        if (!eligibleSource(level, source)) return false;
        var primary = PrimarySubstrateDiscovery.selection(level);
        List<Candidate> candidates = candidates(player, level.dimension()).stream()
                .filter(candidate -> !(candidate.block() instanceof LatentOreBlock)
                        || primary.hosts().containsKey(source.getBlock()))
                .toList();
        if (candidates.isEmpty()) return false;
        Candidate selected = weighted(candidates, player.getRandom().nextDouble());
        if (selected == null) return false;
        BlockState oreState = selected.block().defaultBlockState();
        BlockEntity lootEntity = null;
        if (selected.block() instanceof LatentOreBlock) {
            // A synthetic native loot context still needs the selected host for Silk Touch.
            LatentOreBlockEntity ore = new LatentOreBlockEntity(pos, oreState);
            ore.setHost(source);
            lootEntity = ore;
        }
        List<ItemStack> drops = Block.getDrops(oreState, level,
                pos, lootEntity, player, tool == null ? ItemStack.EMPTY : tool);
        boolean produced = false;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            Block.popResource(level, pos, drop);
            produced = true;
        }
        return produced;
    }

    public static int candidateCount(ServerPlayer player) { return candidates(player, player.serverLevel().dimension()).size(); }

    /**
     * Dimension-correct natural ore catalog used by Nature's Boon and other mechanics that
     * actually create ore. This deliberately stays scoped to the player's current dimension.
     */
    public static Map<Block, Long> oreValues(ServerPlayer player) {
        return oreValues(player, player.serverLevel().dimension());
    }

    /**
     * Detection catalog used by Ore Sight. Detection answers "is this block an ore?", not
     * "should this ore naturally generate here?", so command/mod-placed ore remains visible
     * outside its normal dimension. Common ore tags provide mod coverage; explicit vanilla
     * fallbacks keep the survey reliable even when a pack has incomplete common tags.
     */
    public static Map<Block, Long> surveyOreValues(ServerPlayer player) {
        LinkedHashSet<Block> blocks = new LinkedHashSet<>();
        addTagged(blocks, ORES);
        addTagged(blocks, STONE_ORES);
        addTagged(blocks, DEEPSLATE_ORES);
        addTagged(blocks, NETHERRACK_ORES);
        addTagged(blocks, END_STONE_ORES);
        addTagged(blocks, dimensionTag("ores", player.serverLevel().dimension()));
        addVanillaFallbacks(blocks, Ground.OVERWORLD);
        addVanillaFallbacks(blocks, Ground.NETHER);
        return valuedBlocks(player, blocks);
    }

    public static boolean isNaturalOre(ServerPlayer player, BlockState state) {
        return state != null && !state.isAir() && oreValues(player).containsKey(state.getBlock());
    }

    private static List<Candidate> candidates(ServerPlayer player, ResourceKey<Level> dimension) {
        Map<Block, Long> values = oreValues(player, dimension);
        List<Candidate> result = new ArrayList<>(values.size());
        values.forEach((block, value) -> result.add(new Candidate(block, 1.0 / Math.max(1L, value))));
        return List.copyOf(result);
    }

    private static Map<Block, Long> oreValues(ServerPlayer player, ResourceKey<Level> dimension) {
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

        ServerLevel level = player.server.getLevel(dimension);
        if (level != null && PrimarySubstrateDiscovery.selection(level).enabled())
            blocks.add(EssenceInfuserContent.LATENT_ORE.get());

        return valuedBlocks(player, blocks);
    }

    private static Map<Block, Long> valuedBlocks(ServerPlayer player, LinkedHashSet<Block> blocks) {
        Map<ResourceLocation, Long> values = valuationValues(player);
        Map<Block, Long> result = new LinkedHashMap<>();
        for (Block block : blocks) {
            Item item = block.asItem();
            if (item == Items.AIR) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            result.put(block, Math.max(1L, values.getOrDefault(itemId, 1L)));
        }
        return Collections.unmodifiableMap(result);
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

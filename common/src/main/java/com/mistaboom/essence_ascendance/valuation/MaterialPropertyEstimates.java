package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
//? if >=26.1.2
/*import net.minecraft.world.item.ToolMaterial;*/
//? if <26.1.2
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Coarse mining-property policy, not acquisition, rarity or exact drop-count evidence.
 * Common family tags share estimates only; recipes remain the authority for material ratios. */
final class MaterialPropertyEstimates {
    private static final String ORES = "c:ores", FAMILY = ORES + "/";
    private static final List<String> FORMS = List.of("raw_materials", "ingots", "gems", "dusts", "nuggets");
    record OreProperties(int tier, double hardness) { }
    enum YieldBand { DENSE, SINGULAR, SPARSE }
    private MaterialPropertyEstimates() { }

    static Map<String, Double> collect(List<Item> items, Map<String, List<String>> commonItemTags) {
        Set<String> ores = oreIds(commonItemTags), present = new HashSet<>();
        Map<String, OreProperties> properties = new TreeMap<>();
        for (Item item : items) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            present.add(id);
            if (ores.contains(id) && item instanceof BlockItem block) {
                properties.put(id, new OreProperties(nativeTier(block.getBlock().defaultBlockState()),
                        block.getBlock().defaultDestroyTime()));
            }
        }
        var result = new TreeMap<>(fromProperties(properties, commonItemTags));
        result.keySet().retainAll(present);
        return Collections.unmodifiableMap(result);
    }

    private static int nativeTier(BlockState state) {
        //? if >=26.1.2
        /*if (state.is(ToolMaterial.NETHERITE.incorrectBlocksForDrops())) return -1;*/
        //? if <26.1.2
        if (state.is(Tiers.NETHERITE.getIncorrectBlocksForDrops())) return -1;
        if (state.is(ProceduralValuationTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (state.is(ProceduralValuationTags.NEEDS_IRON_TOOL)) return 2;
        if (state.is(ProceduralValuationTags.NEEDS_STONE_TOOL)) return 1;
        // A custom requirement outside the supported native needs_* tags is unknown.
        //? if >=26.1.2
        /*return state.is(ToolMaterial.WOOD.incorrectBlocksForDrops()) ? -1 : 0;*/
        //? if <26.1.2
        return state.is(Tiers.WOOD.getIncorrectBlocksForDrops()) ? -1 : 0;
    }

    /** NaN means no usable property estimate; callers retain their ordinary fallback. */
    static double factor(int tier, double hardness, YieldBand yield) {
        if (tier < 0 || tier > 3 || !Double.isFinite(hardness) || hardness < 0) return Double.NaN;
        double hardnessFactor = Math.min(4, Math.sqrt(Math.max(3, hardness) / 3));
        // These are declared policy bands. Common tags specify >1, 1 or <1 material, not these exact counts.
        double yieldFactor = switch (yield) { case DENSE -> .25; case SINGULAR -> 1; case SPARSE -> 4; };
        return (1 << tier) * hardnessFactor * yieldFactor;
    }

    /** Pure mapping seam: only explicitly declared conventional families participate. */
    static Map<String, Double> fromProperties(Map<String, OreProperties> properties, Map<String, List<String>> tags) {
        Set<String> dense = new HashSet<>(tags.getOrDefault("c:ore_rates/dense", List.of()));
        Set<String> singular = new HashSet<>(tags.getOrDefault("c:ore_rates/singular", List.of()));
        Set<String> sparse = new HashSet<>(tags.getOrDefault("c:ore_rates/sparse", List.of()));
        Map<String, Double> physical = new TreeMap<>();
        for (String id : oreIds(tags)) {
            var ore = properties.get(id);
            if (ore == null) continue;
            // Contradictory declared bands use the lower policy factor, never multiply together.
            YieldBand band = dense.contains(id) ? YieldBand.DENSE
                    : singular.contains(id) || !sparse.contains(id) ? YieldBand.SINGULAR : YieldBand.SPARSE;
            double value = factor(ore.tier(), ore.hardness(), band);
            if (Double.isFinite(value) && value > 0) physical.put(id, value);
        }
        Map<String, Double> result = new TreeMap<>(physical);
        tags.forEach((tag, members) -> {
            if (!family(tag)) return;
            double lowest = members.stream().filter(physical::containsKey).mapToDouble(physical::get).min().orElse(Double.NaN);
            if (!Double.isFinite(lowest)) return;
            for (String id : members) if (physical.containsKey(id)) result.merge(id, lowest, Math::min);
            String suffix = tag.substring(FAMILY.length());
            for (String form : FORMS) for (String id : tags.getOrDefault("c:" + form + "/" + suffix, List.of()))
                result.merge(id, lowest, Math::min);
        });
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> oreIds(Map<String, List<String>> tags) {
        Set<String> result = new TreeSet<>(tags.getOrDefault(ORES, List.of()));
        tags.forEach((tag, members) -> { if (family(tag)) result.addAll(members); });
        return result;
    }
    private static boolean family(String tag) { return tag.startsWith(FAMILY) && tag.length() > FAMILY.length(); }
}

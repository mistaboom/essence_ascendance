package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Minecraft adapter for the pure block-loot estimator. Does NOT harvest a block or enchant a real stack. */
final class ProceduralBlockHarvest {
    record HarvestDrop(Item output, double chance, double countWhenPresent, int unresolved,
                       Item reusableTool, boolean silkTouch, List<String> signals) {
        HarvestDrop { signals = List.copyOf(signals); }
    }
    private record Tool(Item item, String id, Set<String> tags, boolean silkTouch) { }

    private ProceduralBlockHarvest() { }

    static Map<Block, List<HarvestDrop>> discover(MinecraftServer server) {
        Map<String, Map<String, Object>> tables = loadTables(server);
        Map<Block, List<HarvestDrop>> result = new IdentityHashMap<>();
        List<Tool> tools = tools(server);
        Map<String, List<String>> itemTags = ProceduralValuationEngine.generationData(server).itemTags();
        for (Block block : BuiltInRegistries.BLOCK) {
            Map<String, Object> table = tables.get(block.getLootTable().location().toString());
            if (table == null) continue;
            try {
                Set<String> properties = supportedProperties(block);
                Map<Map<String, String>, BlockState> states = new LinkedHashMap<>();
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    Map<String, String> projection = new LinkedHashMap<>();
                    for (Property<?> p : state.getProperties())
                        if (properties.contains(p.getName())) projection.put(p.getName(), value(state, p));
                    states.putIfAbsent(Map.copyOf(projection), state);
                }
                String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
                boolean toolSensitive = usesTools(table, tables, new LinkedHashSet<>(), 0);
                List<Tool> contexts = toolSensitive ? tools : tools.subList(0, 1);
                // A cache key includes the reusable prerequisite; an easier tool can win in the acquisition graph.
                Map<String, HarvestDrop> unique = new LinkedHashMap<>();
                for (Map.Entry<Map<String, String>, BlockState> state : states.entrySet()) {
                    for (Tool tool : contexts) {
                        // Do not propose an unsuitable SILK harvesting tool for a gated block.
                        if (tool.silkTouch() && state.getValue().requiresCorrectToolForDrops()
                                && !new ItemStack(tool.item()).isCorrectToolForDrops(state.getValue())) continue;
                        var context = new ProceduralBlockLoot.Context(blockId, state.getKey(), properties, tool.id(),
                                tool.tags(), tool.silkTouch() ? Map.of("minecraft:silk_touch", 1) : Map.of());
                        Map<String, ProceduralBlockLoot.Drop> drops = ProceduralBlockLoot.estimate(table, context,
                                tables::get, id -> itemTags.getOrDefault(id, List.of()));
                        for (Map.Entry<String, ProceduralBlockLoot.Drop> entry : drops.entrySet()) {
                            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                            Item output = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                            if (output == null || output == Items.AIR) continue;
                            ProceduralBlockLoot.Drop drop = entry.getValue();
                            List<String> signals = new ArrayList<>(drop.signals());
                            if (block instanceof PinkPetalsBlock && !state.getKey().isEmpty())
                                signals.add("modeled flower-count state " + state.getKey()
                                        + "; one concrete state per harvest, not a sum of mutually exclusive states"
                                        + "; natural placement still required, state frequency/cultivation effort estimated");
                            else if (!state.getKey().isEmpty()) signals.add("reachable standard growth/plant state " + state.getKey()
                                    + "; growth time is estimated, not an unknown random drop gate");
                            if (tool.item() != Items.AIR) signals.add("reusable harvest tool " + tool.id() + "; tool acquisition required, wear amortized");
                            if (tool.silkTouch()) signals.add("Silk Touch I prerequisite; supported-item data checked; enchantment access/effort estimated");
                            signals.add("conditional branches evaluated in one harvest context; pools combined without counting alternatives twice");
                            HarvestDrop candidate = new HarvestDrop(output, drop.chance(), drop.expectedCount() / drop.chance(),
                                    drop.unresolved(), tool.item() == Items.AIR ? null : tool.item(), tool.silkTouch(), signals);
                            String key = entry.getKey() + "|" + tool.id() + "|" + tool.silkTouch() + "|" + drop.unresolved()
                                    + "|" + drop.chance() + "|" + candidate.countWhenPresent();
                            unique.putIfAbsent(key, candidate);
                        }
                    }
                }
                result.put(block, List.copyOf(unique.values()));
            } catch (RuntimeException exception) {
                EssenceAscendance.LOGGER.warn("Valuation skipped unsupported block harvest {}: {}",
                        BuiltInRegistries.BLOCK.getKey(block), exception.toString());
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> p) {
        return p.getName(state.getValue(p));
    }

    static Set<String> supportedProperties(Block block) {
        Set<String> result = new LinkedHashSet<>();
        // These runtime classes implement ordinary growth. Do not assume that arbitrary
        // properties such as energy, honey_level, charges or a mod's stage are free.
        if (block instanceof CropBlock || block instanceof CocoaBlock || block instanceof NetherWartBlock
                || block instanceof SweetBerryBushBlock || block instanceof StemBlock || block instanceof PitcherCropBlock)
            result.add("age");
        if (block instanceof DoublePlantBlock) result.add("half");
        // The CaveVines runtime capability produces berries during vine growth
        // and supports bonemeal. The engine harvest removes the berries again;
        // this is a reachable biological state, not an arbitrary boolean gate.
        if (block instanceof CaveVines) result.add(CaveVines.BERRIES.getName());
        // The runtime flower-bed class implements this count state. Do NOT whitelist
        // any unrelated block merely because it declares a property with this name.
        if (block instanceof PinkPetalsBlock) result.add(PinkPetalsBlock.AMOUNT.getName());
        return result;
    }

    private static List<Tool> tools(MinecraftServer server) {
        List<Tool> result = new ArrayList<>();
        result.add(new Tool(Items.AIR, "minecraft:air", Set.of(), false));
        Map<String, Object> silk = Map.of();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", "enchantment/silk_touch.json");
        try {
            var resource = server.getResourceManager().getResource(id);
            if (resource.isPresent()) try (BufferedReader reader = resource.get().openAsReader()) {
                silk = ProceduralBlockLoot.object(plain(JsonParser.parseReader(reader)));
            }
        } catch (IOException | RuntimeException exception) {
            EssenceAscendance.LOGGER.debug("Valuation Silk Touch data unavailable: {}", exception.toString());
        }
        Object support = silk.get("supported_items");
        boolean silkAvailable = silk.get("max_level") instanceof Number n && n.intValue() >= 1;
        for (Item item : BuiltInRegistries.ITEM.stream().filter(i -> i instanceof DiggerItem || i instanceof ShearsItem)
                .sorted(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString())).toList()) {
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            Set<String> tags = item.builtInRegistryHolder().tags().map(t -> t.location().toString())
                    .collect(java.util.stream.Collectors.toSet());
            result.add(new Tool(item, itemId, tags, false));
            if (silkAvailable && supportedItem(support, itemId, tags)) result.add(new Tool(item, itemId, tags, true));
        }
        return List.copyOf(result);
    }

    private static boolean supportedItem(Object selector, String id, Set<String> tags) {
        if (selector instanceof String s) return s.startsWith("#") ? tags.contains(s.substring(1)) : id.equals(s);
        if (selector instanceof List<?> values) return values.stream().anyMatch(v -> supportedItem(v, id, tags));
        return false;
    }

    private static boolean usesTools(Object raw, Map<String, Map<String, Object>> tables, Set<String> seen, int depth) {
        if (depth > 32) return true;
        if (raw instanceof List<?> list) return list.stream().anyMatch(v -> usesTools(v, tables, seen, depth + 1));
        if (!(raw instanceof Map<?, ?>)) return false;
        Map<String, Object> map = ProceduralBlockLoot.object(raw);
        if ("minecraft:match_tool".equals(map.get("condition"))) return true;
        if ("minecraft:loot_table".equals(map.get("type"))) {
            Object ref = map.getOrDefault("value", map.get("name"));
            if (ref instanceof String s && seen.add(s) && usesTools(tables.get(s), tables, seen, depth + 1)) return true;
        }
        return map.values().stream().anyMatch(v -> usesTools(v, tables, seen, depth + 1));
    }

    private static Map<String, Map<String, Object>> loadTables(MinecraftServer server) {
        Map<String, Map<String, Object>> tables = new LinkedHashMap<>();
        ProceduralValuationEngine.generationData(server).json("loot_evidence").forEach((id, value) ->
                tables.put(id.toString(), ProceduralBlockLoot.object(plain(value))));
        return tables;
    }

    private static Object plain(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : value.getAsJsonObject().entrySet()) map.put(e.getKey(), plain(e.getValue()));
            return map;
        }
        if (value.isJsonArray()) { List<Object> list = new ArrayList<>(); for (JsonElement v : value.getAsJsonArray()) list.add(plain(v)); return list; }
        if (value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean();
        if (value.getAsJsonPrimitive().isNumber()) return value.getAsDouble();
        return value.getAsString();
    }
}

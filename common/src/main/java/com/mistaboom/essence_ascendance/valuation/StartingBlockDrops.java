package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.reflect.TypeToken;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.*;

/** Read-only native hand-drop proof for finite starting structures; no loot or modifier callbacks run. */
public final class StartingBlockDrops {
    private StartingBlockDrops() { }
    public static Map<String, Double> guaranteedSingleHandDrops(GenerationDataSnapshot inputs, BlockState state) {
        Map<String, Double> result = new TreeMap<>();
        supportedHandDrops(inputs, state).forEach((item, drop) -> {
            if (drop.chance() == 1 && drop.expectedCount() == 1) result.put(item, 1.0);
        });
        return Collections.unmodifiableMap(result);
    }
    public record ExpectedDrop(double chance, double expectedCount) { }
    /** Native probability remains explicit and must never be used as a guaranteed finite stock. */
    public static Map<String, ExpectedDrop> supportedHandDrops(GenerationDataSnapshot inputs, BlockState state) {
        return supportedHandDrops(inputs, state, "");
    }
    /** Lower-bound harvest after reserving one replant item per emitted seed stack. */
    public static Map<String, ExpectedDrop> supportedHandDrops(GenerationDataSnapshot inputs, BlockState state, String reservedItem) {
        return supportedHandDrops(inputs, state, reservedItem, "", Set.of());
    }
    public static Map<String, ExpectedDrop> supportedHandDrops(GenerationDataSnapshot inputs, BlockState state,
            String reservedItem, String biome, Set<String> biomeTags) {
        return supportedHandDrops(inputs, state, reservedItem, biome, biomeTags, ignored -> { });
    }
    public static Map<String, ExpectedDrop> supportedHandDrops(GenerationDataSnapshot inputs, BlockState state,
            String reservedItem, String biome, Set<String> biomeTags, java.util.function.Consumer<String> excluded) {
        return supportedToolDrops(inputs, state, net.minecraft.world.item.ItemStack.EMPTY, reservedItem, biome, biomeTags, excluded);
    }
    /** Exact fresh, unenchanted native tool; all effective predicates/modifiers still apply. */
    public static Map<String, ExpectedDrop> supportedToolDrops(GenerationDataSnapshot inputs, BlockState state,
            net.minecraft.world.item.ItemStack tool, String reservedItem, String biome, Set<String> biomeTags,
            java.util.function.Consumer<String> excluded) {
        boolean nativeBlock = "minecraft".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())
                && state.getBlock().getClass().getPackageName().equals("net.minecraft.world.level.block");
        // Audited 21.1.19 class inherits native drop/break behavior; only geometry,
        // facing and waterlogging differ. Effective hardness/tool/loot still govern.
        boolean nativeCrate = "21.1.19".equals(inputs.installedVersion("ftbstuff"))
                && Set.of("SmallCrateBlock", "CrateBlock", "BarrelBlock").stream().anyMatch(name -> state.getBlock().getClass().getName()
                    .equals("dev.ftb.mods.ftbstuffnthings.blocks.lootdroppers." + name));
        if (state.hasBlockEntity() || state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)
                || !tool.isEmpty() && (!tool.getItem().getClass().getPackageName().equals("net.minecraft.world.item")
                    || tool.isEnchanted() || !tool.getComponents().equals(tool.getItem().getDefaultInstance().getComponents()))
                || !nativeBlock && !nativeCrate
                || state.getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) < 0) {
            excluded.accept("Hand harvest contract unavailable: " + state.getBlock().getClass().getName()
                    + "; ftbstuff=" + inputs.installedVersion("ftbstuff") + "; block entity=" + state.hasBlockEntity()
                    + "; tool required=" + state.requiresCorrectToolForDrops()); return Map.of();
        }
        ResourceLocation loot = state.getBlock().getLootTable().location();
        var definition = inputs.definition("loot_evidence", loot);
        if (definition == null) { excluded.accept("Effective loot unavailable: " + loot); return Map.of(); }
        Map<String, String> properties = new TreeMap<>();
        state.getProperties().forEach(property -> properties.put(property.getName(), propertyValue(state, property)));
        String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        String toolId = tool.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(tool.getItem()).toString();
        var toolTags = tool.isEmpty() ? Set.<String>of() : tool.getTags().map(t -> t.location().toString()).collect(java.util.stream.Collectors.toSet());
        var context = new ProceduralBlockLoot.Context(block, properties, properties.keySet(), toolId, toolTags, Map.of(), biome, biomeTags).withToolFacts(NativeToolFacts.capture(inputs, tool));
        Map<String, Map<String, Object>> tables = new HashMap<>();
        java.util.function.Function<String, Map<String, Object>> read = id -> tables.computeIfAbsent(id, key -> {
            var table = inputs.definition("loot_evidence", ResourceLocation.parse(key));
            return table == null ? Map.of() : BalanceDocument.GSON.fromJson(
                    handAbilityConditions(biomeConditions(table, inputs.installedVersion("lootjs")),
                            inputs.installedVersion("neoforge"), name -> nativeToolAbility(tool, name)), new TypeToken<Map<String,Object>>() {}.getType());
        });
        var drops = ProceduralBlockLoot.estimate(read.apply(loot.toString()), context, read,
                id -> inputs.itemTag(ResourceLocation.parse(id)), reservedItem);
        var modifiers = inputs.nativeBlockLootAudit().applicable(loot.toString(), context, drops.keySet());
        Map<String, ExpectedDrop> result = new TreeMap<>();
        drops.forEach((output, drop) -> {
            if (drop.unresolved() == 0 && drop.chance() > 0 && drop.expectedCount() > 0
                    && modifiers.stream().noneMatch(modifier -> BlockLootModifierAudit.couldChangeOutput(modifier, output)))
                result.put(output, new ExpectedDrop(drop.chance(), drop.expectedCount()));
            else excluded.accept(output + ": unresolved=" + drop.unresolved() + "; " + drop.signals()
                    + "; potentially changing modifiers=" + modifiers.stream().filter(m -> BlockLootModifierAudit.couldChangeOutput(m, output))
                    .map(RuntimeLootAudit.Modifier::implementation).toList());
        });
        if (drops.isEmpty()) excluded.accept("No positive drops for effective table " + loot + " in biome " + biome);
        return Collections.unmodifiableMap(result);
    }
    /** Audited LootJS 3.7.0 MatchBiome reads only ORIGIN and the loaded biome HolderSet.
     * The caller supplies the biome at that predicate's query position; no callback runs.
     * Other versions, extra fields and missing biome context remain unsupported. */
    static com.google.gson.JsonElement biomeConditions(com.google.gson.JsonElement value, String version) {
        if (!"1.21.1-3.7.0".equals(version) || value == null || value.isJsonPrimitive() || value.isJsonNull()) return value;
        if (value.isJsonArray()) {
            var result = new com.google.gson.JsonArray(); value.getAsJsonArray().forEach(v -> result.add(biomeConditions(v, version))); return result;
        }
        var object = value.getAsJsonObject();
        if (object.has("condition") && object.get("condition").getAsString().equals("lootjs:match_biome")
                && object.has("biomes") && Set.of("condition", "biomes").containsAll(object.keySet())) {
            var result = new com.google.gson.JsonObject(); result.addProperty("condition", "minecraft:location_check");
            var predicate = new com.google.gson.JsonObject(); predicate.add("biomes", object.get("biomes")); result.add("predicate", predicate); return result;
        }
        var result = new com.google.gson.JsonObject(); object.entrySet().forEach(e -> result.add(e.getKey(), biomeConditions(e.getValue(), version))); return result;
    }
    /** Native NeoForge predicate delegates to the actual empty stack's item ability.
     * Query that read-only capability, never infer shears/other abilities from a tag or name. */
    private static boolean nativeToolAbility(net.minecraft.world.item.ItemStack tool, String name) {
        try {
            var abilityType = Class.forName("net.neoforged.neoforge.common.ItemAbility");
            var ability = abilityType.getMethod("get", String.class).invoke(null, name);
            return (Boolean)net.minecraft.world.item.ItemStack.class.getMethod("canPerformAction", abilityType)
                    .invoke(tool, ability);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native hand ability API changed", failure); }
    }
    static com.google.gson.JsonElement handAbilityConditions(com.google.gson.JsonElement value, String version,
            java.util.function.Predicate<String> ability) {
        if (version == null || !Set.of("21.1.248", "21.1.250", "21.1.251").contains(version)
                || value == null || value.isJsonPrimitive() || value.isJsonNull()) return value;
        if (value.isJsonArray()) {
            var result = new com.google.gson.JsonArray(); value.getAsJsonArray().forEach(v -> result.add(handAbilityConditions(v, version, ability))); return result;
        }
        var object = value.getAsJsonObject();
        if (object.has("condition") && object.get("condition").getAsString().equals("neoforge:can_item_perform_ability")
                && object.keySet().equals(Set.of("condition", "ability")) && object.get("ability").isJsonPrimitive()) {
            var result = new com.google.gson.JsonObject(); result.addProperty("condition", "minecraft:random_chance");
            result.addProperty("chance", ability.test(object.get("ability").getAsString()) ? 1 : 0); return result;
        }
        var result = new com.google.gson.JsonObject(); object.entrySet().forEach(e -> result.add(e.getKey(), handAbilityConditions(e.getValue(), version, ability))); return result;
    }
    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}

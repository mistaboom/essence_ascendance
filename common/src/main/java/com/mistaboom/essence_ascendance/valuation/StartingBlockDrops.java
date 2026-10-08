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
        if (state.hasBlockEntity() || state.requiresCorrectToolForDrops()
                || !"minecraft".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())
                || !state.getBlock().getClass().getPackageName().equals("net.minecraft.world.level.block")) return Map.of();
        ResourceLocation loot = state.getBlock().getLootTable().location();
        var definition = inputs.definition("loot_evidence", loot);
        if (definition == null) return Map.of();
        Map<String, String> properties = new TreeMap<>();
        state.getProperties().forEach(property -> properties.put(property.getName(), propertyValue(state, property)));
        String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        var context = new ProceduralBlockLoot.Context(block, properties, properties.keySet(), "minecraft:air", Set.of(), Map.of());
        Map<String, Map<String, Object>> tables = new HashMap<>();
        java.util.function.Function<String, Map<String, Object>> read = id -> tables.computeIfAbsent(id, key -> {
            var table = inputs.definition("loot_evidence", ResourceLocation.parse(key));
            return table == null ? Map.of() : BalanceDocument.GSON.fromJson(table, new TypeToken<Map<String,Object>>() {}.getType());
        });
        var drops = ProceduralBlockLoot.estimate(read.apply(loot.toString()), context, read,
                id -> inputs.itemTag(ResourceLocation.parse(id)), reservedItem);
        var modifiers = new BlockLootModifierAudit(inputs.runtimeLootModifiers()).applicable(loot.toString(), context, drops.keySet());
        Map<String, ExpectedDrop> result = new TreeMap<>();
        drops.forEach((output, drop) -> {
            if (drop.unresolved() == 0 && drop.chance() > 0 && drop.expectedCount() > 0
                    && modifiers.stream().noneMatch(modifier -> BlockLootModifierAudit.couldChangeOutput(modifier, output)))
                result.put(output, new ExpectedDrop(drop.chance(), drop.expectedCount()));
        });
        return Collections.unmodifiableMap(result);
    }
    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}

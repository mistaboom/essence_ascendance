package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Generation-only factual corrections applied before graph solving, never a parallel runtime authority. */
final class ValuationGenerationInputs {
    private static Map<Item, Map<String, Object>> items = Map.of();
    private ValuationGenerationInputs() { }

    static void clear() {
        items = Map.of();
    }

    static void configure(BalanceOverrides overrides) {
        var orderedFacts = overrides.facts().stream().sorted(Comparator.comparingInt(BalanceOverrides.FactOverride::priority)
                .thenComparingDouble(f -> f.number("confidence").orElse(1.0)).thenComparing(BalanceOverrides.FactOverride::id)).toList();
        Map<Item, Map<String, Object>> resolved = new IdentityHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Map<String, Object> values = new TreeMap<>();
            for (var fact : orderedFacts) if (matchesItem(item, fact)) values.putAll(fact.values());
            if (!values.isEmpty()) resolved.put(item, Map.copyOf(values));
        }
        items = resolved;
    }

    static boolean itemAllowed(Item item) {
        Map<String, Object> values = items.getOrDefault(item, Map.of());
        return !Boolean.TRUE.equals(values.get("disabled"))
                && !Boolean.TRUE.equals(values.get("creative_only")) && !Boolean.TRUE.equals(values.get("administrative"))
                && !Boolean.TRUE.equals(values.get("joke"));
    }
    static Double economicValue(Item item) {
        Object value = items.getOrDefault(item, Map.of()).get("resource_value");
        return value instanceof Number number ? number.doubleValue() : null;
    }
    private static boolean matchesItem(Item item, BalanceOverrides.FactOverride fact) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return switch (fact.kind()) {
            case ITEM -> matches(id, fact.selector());
            case ITEM_TAG -> {
                ResourceLocation tag = ResourceLocation.tryParse(fact.selector().replaceFirst("^#", ""));
                yield tag != null && new ItemStack(item).is(TagKey.create(Registries.ITEM, tag));
            }
            case BLOCK -> item instanceof BlockItem block && matches(BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString(), fact.selector());
            case BLOCK_TAG -> {
                ResourceLocation tag = ResourceLocation.tryParse(fact.selector().replaceFirst("^#", ""));
                yield item instanceof BlockItem block && tag != null && block.getBlock().defaultBlockState().is(TagKey.create(Registries.BLOCK, tag));
            }
            default -> false;
        };
    }
    private static boolean matches(String id, String selector) {
        return id.matches(java.util.regex.Pattern.quote(selector).replace("*", "\\E.*\\Q"));
    }
}

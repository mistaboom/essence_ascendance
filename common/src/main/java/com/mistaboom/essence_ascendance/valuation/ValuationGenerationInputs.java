package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Generation-only factual corrections applied before graph solving, never a parallel runtime authority. */
final class ValuationGenerationInputs {
    private static BalanceOverrides inputs = BalanceOverrides.empty();
    private static List<BalanceOverrides.FactOverride> orderedFacts = List.of();
    private static Map<Item, Map<String, Object>> items = Map.of();
    private static final Map<String, Boolean> SOURCE_ALLOWED = new TreeMap<>();
    private static final Map<String, Map<String, Object>> RECIPE_FACTS = new TreeMap<>();
    private ValuationGenerationInputs() { }

    static void clear() {
        inputs = BalanceOverrides.empty(); orderedFacts = List.of(); items = Map.of();
        SOURCE_ALLOWED.clear(); RECIPE_FACTS.clear();
    }

    static void configure(BalanceOverrides overrides) {
        inputs = overrides;
        orderedFacts = inputs.facts().stream().sorted(Comparator.comparingInt(BalanceOverrides.FactOverride::priority)
                .thenComparingDouble(f -> f.number("confidence").orElse(1.0)).thenComparing(BalanceOverrides.FactOverride::id)).toList();
        SOURCE_ALLOWED.clear(); RECIPE_FACTS.clear();
        Map<Item, Map<String, Object>> resolved = new IdentityHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Map<String, Object> values = new TreeMap<>();
            for (var fact : ordered()) if (matchesItem(item, fact)) values.putAll(fact.values());
            if (!values.isEmpty()) resolved.put(item, Map.copyOf(values));
        }
        items = resolved;
    }

    static boolean itemAllowed(Item item) {
        Map<String, Object> values = items.getOrDefault(item, Map.of());
        return !Boolean.FALSE.equals(values.get("attainable")) && !Boolean.TRUE.equals(values.get("disabled"))
                && !Boolean.TRUE.equals(values.get("creative_only")) && !Boolean.TRUE.equals(values.get("administrative"));
    }
    static boolean declaresSource(Item item) {
        Map<String, Object> values = items.getOrDefault(item, Map.of());
        return Boolean.TRUE.equals(values.get("attainable")) || Boolean.TRUE.equals(values.get("passive_generation"))
                || "effectively_infinite".equals(values.get("availability"));
    }
    static Double economicValue(Item item) {
        Object value = items.getOrDefault(item, Map.of()).get("resource_value");
        return value instanceof Number number ? number.doubleValue() : null;
    }
    static double confidence(Item item) {
        Object value = items.getOrDefault(item, Map.of()).get("confidence");
        return value instanceof Number number ? number.doubleValue() : .8;
    }
    static double progression(Item item, double fallback) {
        Object stage = items.getOrDefault(item, Map.of()).get("stage");
        if (stage == null) return fallback;
        int index = List.of("entry", "early", "mid", "late", "apex").indexOf(stage.toString());
        return index < 0 ? fallback : index / 4.0;
    }
    static boolean recipeAllowed(ResourceLocation id, RecipeType<?> type) {
        Map<String, Object> values = recipeFacts(id, type);
        return !Boolean.TRUE.equals(values.get("disabled")) && !Boolean.FALSE.equals(values.get("attainable"));
    }
    static boolean sourceAllowed(String id) {
        Boolean cached = SOURCE_ALLOWED.get(id); if (cached != null) return cached;
        Map<String, Object> values = new TreeMap<>();
        for (var fact : ordered()) if (fact.kind() == BalanceOverrides.SubjectKind.SOURCE && matches(id, fact.selector())) values.putAll(fact.values());
        boolean allowed = !Boolean.TRUE.equals(values.get("disabled")) && !Boolean.FALSE.equals(values.get("attainable"));
        SOURCE_ALLOWED.put(id, allowed); return allowed;
    }
    static int outputCount(ResourceLocation id, RecipeType<?> type, int observed) {
        Object count = recipeFacts(id, type).get("output_count");
        if (!(count instanceof Number number)) return observed;
        double value = number.doubleValue();
        if (value < 1 || value != Math.rint(value) || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Recipe " + id + " output_count must be a positive whole item count; probability belongs to the production graph");
        return (int) value;
    }

    /** A runtime recipe may expose a type that was never registered. This is diagnostic identity only. */
    static String recipeFamily(RecipeType<?> type) {
        if (type == null) return "unregistered_type:null";
        ResourceLocation registered = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return registered == null ? "unregistered_type:" + type.getClass().getName() : registered.toString();
    }

    private static Map<String, Object> recipeFacts(ResourceLocation id, RecipeType<?> type) {
        String family = recipeFamily(type);
        String key = id + "/" + family;
        Map<String, Object> cached = RECIPE_FACTS.get(key); if (cached != null) return cached;
        Map<String, Object> values = new TreeMap<>();
        for (var fact : ordered()) {
            if ((fact.kind() == BalanceOverrides.SubjectKind.RECIPE && matches(id.toString(), fact.selector()))
                    || (fact.kind() == BalanceOverrides.SubjectKind.RECIPE_FAMILY && matches(family, fact.selector()))) values.putAll(fact.values());
        }
        RECIPE_FACTS.put(key, values); return values;
    }
    private static List<BalanceOverrides.FactOverride> ordered() {
        return orderedFacts;
    }
    private static boolean matchesItem(Item item, BalanceOverrides.FactOverride fact) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return switch (fact.kind()) {
            case ITEM, SOURCE -> matches(id, fact.selector());
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

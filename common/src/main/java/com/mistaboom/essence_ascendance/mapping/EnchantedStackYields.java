package com.mistaboom.essence_ascendance.mapping;

import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;
import com.mistaboom.essence_ascendance.balance.engine.EnchantmentSemantics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;

/** Shared server/client projection within a generated, conservation-checked item budget.
 * Never creates a runtime price override or credits enchantment power back into skill calibration. */
public final class EnchantedStackYields {
    public static final String POLICY = "effect_scored_conserved_books_v1";
    public static final double HALF_SATURATION = 5;
    public static final String BOOK = "minecraft:book", ENCHANTED_BOOK = "minecraft:enchanted_book";
    private EnchantedStackYields() { }

    public static boolean hasDynamicYield(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.is(Items.ENCHANTED_BOOK)
                || !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty());
    }

    /** Item-only recipe inputs must pay at the cheapest possible component state. */
    public static long minimumInput(String item, Map<String, Long> values) {
        long budget = values.getOrDefault(item, 0L);
        return item.equals(ENCHANTED_BOOK) ? Math.min(budget, values.getOrDefault(BOOK, 0L)) : budget;
    }

    public static Map<ResourceLocation, Long> resolve(ItemStack stack, Map<ResourceLocation, Long> base,
                                                    Map<ResourceLocation, Long> plainBook) {
        if (stack == null || stack.isEmpty() || base.isEmpty()) return Map.of();
        boolean book = stack.is(Items.ENCHANTED_BOOK);
        var component = book ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
        var enchantments = stack.getOrDefault(component, ItemEnchantments.EMPTY);
        if (!book && enchantments.isEmpty()) return base;
        long ceiling = total(base) / FractionalAmountService.SCALE;
        long carrier = book ? Math.min(ceiling, total(plainBook) / FractionalAmountService.SCALE) : ceiling;
        List<EnchantmentSemantics.Facts> facts = new ArrayList<>();
        List<EnchantmentSemantics.Facts> positiveStartFacts = new ArrayList<>();
        // Sort by registry identity: component insertion order must not change rounding.
        enchantments.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().unwrapKey()
                .map(key -> key.location().toString()).orElse("~unregistered"))).forEach(entry -> {
            if (entry.getIntValue() <= 0 || (!book && !entry.getKey().value().getSupportedItems()
                    //? if >=26.1.2
                    /*.contains(stack.typeHolder()))) return;*/
                    //? if <26.1.2
                    .contains(stack.getItemHolder()))) return;
            var analysis = EnchantmentSemantics.analyze(entry.getKey(), entry.getIntValue());
            if (analysis.strength() > 0) {
                facts.add(analysis);
                if (book && EnchantmentSemantics.analyze(entry.getKey(), 1).strength() > 0) positiveStartFacts.add(analysis);
            }
        });
        Map<ResourceLocation, Long> result = new TreeMap<>();
        if (book) {
            add(result, allocate(plainBook, carrier));
            double strength = facts.stream().mapToDouble(EnchantmentSemantics.Facts::strength).sum();
            // Ceiling a concave, subadditive total preserves subadditivity even when the
            // carrier's generated yield is zero. Rounding individual category contributions
            // would lose small effects; flooring a total could mint a unit on combination.
            long premium = premium(ceiling - carrier, strength);
            double positiveStartStrength = positiveStartFacts.stream().mapToDouble(EnchantmentSemantics.Facts::strength).sum();
            // Effects that are zero at level I may become positive later. Their combined
            // marginal premium receives ONE carrier allowance, not one per enchantment.
            // Any two input books pay >= 2B + F(Ra) + F(Rb) >= 2B + F(Routput),
            // so this cap admits real later effects while preserving every merge partition.
            premium = Math.min(premium, carrier + premium(ceiling - carrier, positiveStartStrength));
            Map<ResourceLocation, Long> effectWeights = new TreeMap<>();
            for (var fact : facts) add(effectWeights, weights(normalizedRoutes(fact)));
            add(result, allocate(effectWeights, premium));
        } else {
            Map<ResourceLocation, Long> effectWeights = new TreeMap<>();
            for (var fact : facts) add(effectWeights, weights(normalizedRoutes(fact)));
            if (effectWeights.isEmpty()) return base;
            double strength = facts.stream().mapToDouble(EnchantmentSemantics.Facts::strength).sum();
            long redirected = (long)Math.floor(ceiling * strength / (HALF_SATURATION + strength));
            add(result, allocate(base, ceiling - redirected));
            add(result, allocate(effectWeights, redirected));
        }
        result.replaceAll((id, units) -> Math.multiplyExact(units, FractionalAmountService.SCALE));
        return Map.copyOf(result);
    }

    private static long premium(long budget, double strength) {
        return Math.min(budget, (long)Math.ceil(budget * strength / (HALF_SATURATION + strength)));
    }

    private static Map<String, Double> normalizedRoutes(EnchantmentSemantics.Facts fact) {
        double total = fact.routes().values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Double> result = new TreeMap<>();
        fact.routes().forEach((category, value) -> result.put(category, value / total * fact.strength()));
        return result;
    }

    private static Map<ResourceLocation, Long> weights(Map<String, Double> routes) {
        Map<ResourceLocation, Long> result = new TreeMap<>();
        routes.forEach((category, strength) -> {
            if (strength > 0) result.put(ResourceLocation.fromNamespaceAndPath("essence_ascendance", category),
                    Math.max(1, Math.round(Math.min(1_000_000, strength) * 1_000_000)));
        });
        return result;
    }
    private static void add(Map<ResourceLocation, Long> target, Map<ResourceLocation, Long> values) {
        values.forEach((id, value) -> { if (value > 0) target.merge(id, value, Math::addExact); });
    }
    public static long total(Map<ResourceLocation, Long> values) {
        long total = 0;
        for (long value : values.values()) total = Math.addExact(total, value);
        return total;
    }
    /** Exact largest-remainder allocation in whole Essence. Category IDs break ties. */
    public static Map<ResourceLocation, Long> allocate(Map<ResourceLocation, Long> weights, long units) {
        long total = total(weights);
        if (total <= 0 || units <= 0) return Map.of();
        var denominator = java.math.BigInteger.valueOf(total);
        Map<ResourceLocation, Long> result = new TreeMap<>();
        Map<ResourceLocation, java.math.BigInteger> fractions = new TreeMap<>();
        long assigned = 0;
        for (var entry : weights.entrySet()) {
            if (entry.getValue() <= 0) continue;
            var divided = java.math.BigInteger.valueOf(units).multiply(java.math.BigInteger.valueOf(entry.getValue()))
                    .divideAndRemainder(denominator);
            long value = divided[0].longValueExact();
            result.put(entry.getKey(), value); fractions.put(entry.getKey(), divided[1]); assigned += value;
        }
        var ordered = fractions.entrySet().stream().sorted(Map.Entry.<ResourceLocation, java.math.BigInteger>comparingByValue()
                .reversed().thenComparing(Map.Entry.comparingByKey())).toList();
        for (long remaining = units - assigned, index = 0; index < remaining; index++)
            result.merge(ordered.get((int)index).getKey(), 1L, Long::sum);
        result.values().removeIf(value -> value == 0);
        return Map.copyOf(result);
    }
}

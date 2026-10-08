package com.mistaboom.essence_ascendance.valuation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Bounded, read-only estimator for block loot in a SPECIFIC harvest context.
 * No random sampling, world mutations, custom callbacks, or item prices.
 * Expansions preserve first-success alternatives (including empty entries),
 * sequence short-circuiting, and weighted selection after entry conditions.
 * Unknown predicates/functions taint evidence, rather than becoming satisfied
 * prerequisites. These estimates feed the existing acquisition graph only.
 */
final class ProceduralBlockLoot {
    private static final int MAX_DEPTH = 32;
    private static final int MAX_EXPANSIONS = 128;
    private static final int MAX_STEPS = 30_000;
    private static final int MAX_COUNT_OUTCOMES = 256;

    record Context(String blockId, Map<String, String> properties, Set<String> supportedProperties,
                   String toolId, Set<String> toolTags, Map<String, Integer> enchantments,
                   String biomeId, Set<String> biomeTags, NativeToolFacts toolFacts) {
        Context(String blockId, Map<String, String> properties, Set<String> supportedProperties,
                String toolId, Set<String> toolTags, Map<String, Integer> enchantments, String biomeId, Set<String> biomeTags) {
            this(blockId, properties, supportedProperties, toolId, toolTags, enchantments, biomeId, biomeTags, NativeToolFacts.UNKNOWN);
        }
        Context withToolFacts(NativeToolFacts facts) {
            return new Context(blockId, properties, supportedProperties, toolId, toolTags, enchantments, biomeId, biomeTags, facts);
        }
        Context(String blockId, Map<String, String> properties, Set<String> supportedProperties,
                String toolId, Set<String> toolTags, Map<String, Integer> enchantments) {
            this(blockId, properties, supportedProperties, toolId, toolTags, enchantments, "", Set.of());
        }
        Context {
            properties = Map.copyOf(properties);
            supportedProperties = Set.copyOf(supportedProperties);
            toolTags = Set.copyOf(toolTags);
            enchantments = Map.copyOf(enchantments);
            biomeTags = Set.copyOf(biomeTags);
        }
    }

    record Drop(double chance, double expectedCount, int unresolved, List<String> signals) {
        Drop { signals = List.copyOf(signals); }
    }
    private record Truth(double probability, Set<String> unknown) { }
    private record Count(double mean, double positive, Set<String> unknown) { }

    /** A single emitted stack, including zero-count outcomes until all its functions have run. */
    private record StackCounts(Map<Integer, Double> probabilities, Set<String> unknown) {
        StackCounts {
            // Sorted iteration also makes floating-point accumulation deterministic across JVMs.
            probabilities = Collections.unmodifiableMap(new TreeMap<>(probabilities));
            unknown = Set.copyOf(unknown);
        }
        Amount amount() {
            double occurrence = 0, count = 0;
            for (Map.Entry<Integer, Double> outcome : probabilities.entrySet()) {
                if (outcome.getKey() > 0) {
                    occurrence += outcome.getValue();
                    count += outcome.getKey() * outcome.getValue();
                }
            }
            return new Amount(occurrence, count, unknown);
        }
    }
    private record Leaf(Map<String, Object> json, double weight) { }
    private record Expansion(double probability, List<Leaf> leaves, boolean success, Set<String> unknown) { }
    private record Amount(double occurrence, double count, Set<String> unknown) { }

    private final Context context;
    private final Function<String, Map<String, Object>> tables;
    private final Function<String, List<String>> tags;
    private final Set<String> active = new LinkedHashSet<>();
    private final Set<String> observations = new LinkedHashSet<>();
    private int steps;
    private String reservedItem = "";

    private ProceduralBlockLoot(Context context, Function<String, Map<String, Object>> tables,
                                Function<String, List<String>> tags) {
        this.context = context;
        this.tables = tables;
        this.tags = tags;
    }

    static Map<String, Drop> estimate(Map<String, Object> root, Context context,
                                     Function<String, Map<String, Object>> tables,
                                     Function<String, List<String>> tags) {
        return estimate(root, context, tables, tags, "");
    }

    /** Conservatively reserve one seed per emitted seed stack, after all native functions. */
    static Map<String, Drop> estimate(Map<String, Object> root, Context context,
                                     Function<String, Map<String, Object>> tables,
                                     Function<String, List<String>> tags, String reservedItem) {
        ProceduralBlockLoot model = new ProceduralBlockLoot(context, tables, tags);
        model.reservedItem = reservedItem;
        try {
            Map<String, Amount> amounts = model.table(root, 0, List.of());
            Map<String, Drop> result = new LinkedHashMap<>();
            amounts.forEach((id, amount) -> {
                if (amount.occurrence() <= 0.0 || amount.count() <= 0.0) return;
                List<String> signals = new ArrayList<>(model.observations);
                signals.addAll(amount.unknown().stream().sorted().toList());
                result.put(id, new Drop(clamp(amount.occurrence()), amount.count(),
                        amount.unknown().size(), signals));
            });
            return Map.copyOf(result);
        } catch (UnsupportedOperationException exception) {
            // A bounded fallback keeps diagnostics without certifying a truncated path.
            Map<String, Drop> result = new LinkedHashMap<>();
            model.fallback(root, result, new LinkedHashSet<>(), 0);
            return Map.copyOf(result);
        }
    }

    private void guard(int depth) {
        if (depth > MAX_DEPTH || ++steps > MAX_STEPS)
            throw new UnsupportedOperationException("block loot evaluation budget exceeded");
    }

    static boolean conditionsMayApply(Object conditions, Context context) {
        var model = new ProceduralBlockLoot(context, ignored -> Map.of(), ignored -> List.of());
        try { var gate = model.conditions(conditions, 0); return gate.probability() > 0 || !gate.unknown().isEmpty(); }
        catch (UnsupportedOperationException unsupported) { return true; }
    }
    static boolean conditionsProven(Object conditions, Context context) {
        var model = new ProceduralBlockLoot(context, ignored -> Map.of(), ignored -> List.of());
        try { var gate = model.conditions(conditions, 0); return gate.probability() == 1 && gate.unknown().isEmpty(); }
        catch (UnsupportedOperationException unsupported) { return false; }
    }

    private Map<String, Amount> table(Map<String, Object> root, int depth, List<Object> inheritedFunctions) {
        guard(depth);
        Map<String, Amount> result = new LinkedHashMap<>();
        List<Object> tableFunctions = functionChain(root.get("functions"), inheritedFunctions);
        for (Object raw : list(root.get("pools"))) {
            Map<String, Object> pool = object(raw);
            List<Object> poolFunctions = functionChain(pool.get("functions"), tableFunctions);
            Truth condition = conditions(pool.get("conditions"), depth + 1);
            Object rawRolls = pool.getOrDefault("rolls", 1);
            StackCounts rollDistribution = integerCounts(rawRolls, depth + 1);
            Amount rollAmount = rollDistribution.amount();
            Count rolls = new Count(rollAmount.count(), rollAmount.occurrence(), rollAmount.unknown());
            if (condition.probability() <= 0 || rolls.mean() <= 0) continue;
            List<Expansion> expanded = List.of(new Expansion(1, List.of(), true, Set.of()));
            for (Object entry : list(pool.get("entries")))
                expanded = combine(expanded, expand(object(entry), depth + 1), false);
            Map<String, Amount> oneRoll = new LinkedHashMap<>();
            for (Expansion option : expanded) {
                double totalWeight = option.leaves().stream().mapToDouble(Leaf::weight).sum();
                if (totalWeight <= 0) continue;
                for (Leaf leaf : option.leaves()) {
                    double selection = option.probability() * leaf.weight() / totalWeight;
                    Map<String, Amount> emitted = emit(leaf.json(), depth + 1, poolFunctions);
                    for (Map.Entry<String, Amount> entry : emitted.entrySet()) {
                        Amount a = entry.getValue();
                        // Different outcomes/leaves within ONE roll are exclusive.
                        mergeExclusive(oneRoll, entry.getKey(), new Amount(selection * a.occurrence(),
                                selection * a.count(), union(a.unknown(), option.unknown())));
                    }
                }
            }
            for (Map.Entry<String, Amount> entry : oneRoll.entrySet()) {
                Amount a = entry.getValue();
                Set<String> unknown = union(a.unknown(), condition.unknown(), rolls.unknown());
                // Native getInt rolls: sum the probability of at least one output
                // over every bounded count outcome, never use the mean as a roll count.
                double occurrence = 0;
                for (var outcome : rollDistribution.probabilities().entrySet())
                    occurrence += outcome.getValue() * (1 - Math.pow(1 - clamp(a.occurrence()), Math.max(0, outcome.getKey())));
                occurrence *= condition.probability();
                double count = condition.probability() * rolls.mean() * a.count();
                mergeIndependent(result, entry.getKey(), new Amount(occurrence, count, unknown));
            }
        }
        return result;
    }

    private List<Expansion> expand(Map<String, Object> entry, int depth) {
        guard(depth);
        Truth test = conditions(entry.get("conditions"), depth + 1);
        List<Expansion> inner = new ArrayList<>();
        String type = text(entry.get("type"));
        if (test.probability() > 0) {
            if (Set.of("minecraft:item", "minecraft:empty", "minecraft:loot_table", "minecraft:tag").contains(type)) {
                double weight = finite(entry.getOrDefault("weight", 1), 1);
                if (type.equals("minecraft:tag") && Boolean.TRUE.equals(entry.get("expand"))) {
                    List<Leaf> leaves = new ArrayList<>();
                    for (String item : tags.apply(text(entry.get("name")))) {
                        Map<String, Object> leaf = new LinkedHashMap<>(entry);
                        leaf.put("type", "minecraft:item"); leaf.put("name", item);
                        leaves.add(new Leaf(leaf, Math.max(0, weight)));
                    }
                    inner.add(new Expansion(1, leaves, !leaves.isEmpty(), Set.of()));
                } else inner.add(new Expansion(1, List.of(new Leaf(entry, Math.max(0, weight))), true, Set.of()));
            } else if (type.equals("minecraft:alternatives")) {
                List<Expansion> pending = List.of(new Expansion(1, List.of(), false, Set.of()));
                for (Object child : list(entry.get("children"))) {
                    List<Expansion> next = new ArrayList<>();
                    for (Expansion prefix : pending) {
                        if (prefix.success()) { next.add(prefix); continue; }
                        for (Expansion suffix : expand(object(child), depth + 1)) next.add(join(prefix, suffix));
                    }
                    bounded(next); pending = next;
                }
                inner.addAll(pending);
            } else if (type.equals("minecraft:group") || type.equals("minecraft:sequence")) {
                List<Expansion> pending = List.of(new Expansion(1, List.of(), true, Set.of()));
                for (Object child : list(entry.get("children")))
                    pending = combine(pending, expand(object(child), depth + 1), type.equals("minecraft:sequence"));
                if (type.equals("minecraft:group"))
                    pending = pending.stream().map(e -> new Expansion(e.probability(), e.leaves(), true, e.unknown())).toList();
                inner.addAll(pending);
            } else {
                throw new UnsupportedOperationException("unsupported block loot entry " + type);
            }
        }
        List<Expansion> result = new ArrayList<>();
        if (test.probability() < 1) result.add(new Expansion(1 - test.probability(), List.of(), false, test.unknown()));
        for (Expansion e : inner) result.add(new Expansion(e.probability() * test.probability(), e.leaves(),
                e.success(), union(e.unknown(), test.unknown())));
        bounded(result);
        return result;
    }

    private List<Expansion> combine(List<Expansion> a, List<Expansion> b, boolean sequence) {
        List<Expansion> result = new ArrayList<>();
        for (Expansion left : a) {
            if (sequence && !left.success()) { result.add(left); continue; }
            for (Expansion right : b) result.add(join(left, right));
        }
        bounded(result); return result;
    }

    private static Expansion join(Expansion a, Expansion b) {
        List<Leaf> leaves = new ArrayList<>(a.leaves()); leaves.addAll(b.leaves());
        return new Expansion(a.probability() * b.probability(), List.copyOf(leaves), b.success(), union(a.unknown(), b.unknown()));
    }

    private static void bounded(List<Expansion> results) {
        if (results.size() > MAX_EXPANSIONS) throw new UnsupportedOperationException("too many block loot alternatives");
    }

    private Map<String, Amount> emit(Map<String, Object> leaf, int depth, List<Object> inheritedFunctions) {
        guard(depth);
        Map<String, Amount> result = new LinkedHashMap<>();
        List<Object> functions = functionChain(leaf.get("functions"), inheritedFunctions);
        switch (text(leaf.get("type"))) {
            case "minecraft:item" -> result.put(text(leaf.get("name")), stackFunctions(functions, depth + 1, text(leaf.get("name"))));
            case "minecraft:empty" -> { }
            case "minecraft:tag" -> {
                for (String item : tags.apply(text(leaf.get("name")))) result.put(item, stackFunctions(functions, depth + 1, item));
            }
            case "minecraft:loot_table" -> {
                Object reference = leaf.getOrDefault("value", leaf.get("name"));
                if (reference instanceof Map<?, ?>) result.putAll(table(object(reference), depth + 1, functions));
                else {
                    String id = text(reference);
                    if (!active.add(id)) throw new UnsupportedOperationException("recursive block loot reference");
                    try {
                        Map<String, Object> target = tables.apply(id);
                        if (target == null) throw new UnsupportedOperationException("missing block loot reference");
                        result.putAll(table(target, depth + 1, functions));
                    } finally { active.remove(id); }
                }
            }
            default -> throw new UnsupportedOperationException("unsupported block loot leaf");
        }
        return result;
    }

    private Truth conditions(Object raw, int depth) {
        if (raw == null) return truth(true);
        if (!(raw instanceof List<?>)) return unknown("malformed condition list");
        Truth value = truth(true);
        for (Object term : list(raw)) value = and(value, condition(object(term), depth + 1));
        return value;
    }

    private Truth condition(Map<String, Object> condition, int depth) {
        guard(depth);
        String type = text(condition.get("condition"));
        if (context.toolFacts().falseConditions().contains(type)) return truth(false);
        switch (type) {
            case "minecraft:location_check" -> {
                if (!Set.of("condition", "predicate", "offsetX", "offsetY", "offsetZ").containsAll(condition.keySet())
                        || finite(condition.getOrDefault("offsetX", 0), 1) != 0
                        || finite(condition.getOrDefault("offsetY", 0), 1) != 0
                        || finite(condition.getOrDefault("offsetZ", 0), 1) != 0
                        || context.biomeId().isEmpty()) return unknown("unproven loot location context");
                var predicate = object(condition.get("predicate"));
                if (!predicate.keySet().equals(Set.of("biomes"))) return unknown("unsupported loot location predicate");
                var selector = predicate.get("biomes");
                var ids = selector instanceof String s ? List.of(s) : list(selector);
                if (ids.isEmpty()) return unknown("empty loot biome selector");
                for (var id : ids) {
                    String value = text(id);
                    if (value.startsWith("#") ? context.biomeTags().contains(value.substring(1)) : context.biomeId().equals(value))
                        return truth(true);
                }
                return truth(false);
            }
            case "minecraft:survives_explosion" -> { return truth(true); }
            case "minecraft:inverted" -> {
                Truth t = condition(object(condition.get("term")), depth + 1);
                return new Truth(1 - t.probability(), t.unknown());
            }
            case "minecraft:all_of", "minecraft:any_of" -> {
                boolean all = type.equals("minecraft:all_of");
                if (!(condition.get("terms") instanceof List<?>)) return unknown("malformed logical predicate");
                Truth value = truth(all);
                for (Object term : list(condition.get("terms"))) {
                    Truth next = condition(object(term), depth + 1);
                    value = all ? and(value, next) : or(value, next);
                }
                return value;
            }
            case "minecraft:random_chance" -> {
                Count p = number(condition.get("chance"), depth + 1);
                return new Truth(clamp(p.mean()), p.unknown());
            }
            case "minecraft:table_bonus" -> {
                String enchantment = text(condition.get("enchantment"));
                int level = context.enchantments().getOrDefault(enchantment, 0);
                List<?> chances = list(condition.get("chances"));
                if (chances.isEmpty()) return unknown("empty enchantment bonus table");
                Count p = number(chances.get(Math.min(level, chances.size() - 1)), depth + 1);
                return new Truth(clamp(p.mean()), p.unknown());
            }
            case "minecraft:block_state_property" -> {
                if (!text(condition.get("block")).equals(context.blockId())) return truth(false);
                if (condition.containsKey("properties") && !(condition.get("properties") instanceof Map<?, ?>))
                    return unknown("malformed block-state predicate");
                Truth value = truth(true);
                for (Map.Entry<String, Object> property : object(condition.get("properties")).entrySet()) {
                    if (!context.supportedProperties().contains(property.getKey())) {
                        value = and(value, unknown("unmodeled state prerequisite: " + property.getKey()));
                    } else {
                        value = and(value, matchesRange(context.properties().get(property.getKey()), property.getValue()));
                        observations.add("modeled block-state prerequisite: " + property.getKey());
                    }
                }
                return value;
            }
            case "minecraft:match_tool" -> {
                if (condition.containsKey("predicate") && !(condition.get("predicate") instanceof Map<?, ?>))
                    return unknown("malformed tool predicate");
                return tool(object(condition.get("predicate")), depth + 1);
            }
            default -> { return unknown("unmodeled loot condition: " + type); }
        }
    }

    private Truth tool(Map<String, Object> predicate, int depth) {
        guard(depth);
        Truth value = truth(true);
        for (Map.Entry<String, Object> entry : predicate.entrySet()) {
            switch (entry.getKey()) {
                case "items" -> value = and(value, itemSelector(entry.getValue()));
                case "predicates" -> {
                    for (Map.Entry<String, Object> p : object(entry.getValue()).entrySet()) {
                        if (context.toolFacts().falseComponentPredicates().contains(p.getKey())) {
                            value = and(value, truth(false)); continue;
                        }
                        if (!p.getKey().equals("minecraft:enchantments")) {
                            value = and(value, unknown("unmodeled tool component predicate: " + p.getKey())); continue;
                        }
                        if (!(p.getValue() instanceof List<?>)) { value = and(value, unknown("malformed enchantment predicate")); continue; }
                        for (Object e : list(p.getValue())) {
                            Map<String, Object> enchant = object(e);
                            if (!Set.of("enchantments", "levels").containsAll(enchant.keySet())) {
                                value = and(value, unknown("unsupported enchantment constraint")); continue;
                            }
                            List<?> ids = enchant.get("enchantments") instanceof String s ? List.of(s) : list(enchant.get("enchantments"));
                            if (ids.isEmpty()) { value = and(value, unknown("unbounded enchantment selector")); continue; }
                            Truth match = truth(false);
                            for (Object rawId : ids) {
                                String id = text(rawId);
                                if (!id.equals("minecraft:silk_touch") && !id.equals("minecraft:fortune")) {
                                    match = or(match, unknown("unmodeled enchantment access: " + id)); continue;
                                }
                                int level = context.enchantments().getOrDefault(id, 0);
                                Truth atLevel = level == 0 ? truth(false) : matchesRange(String.valueOf(level), enchant.getOrDefault("levels", Map.of("min", 1)));
                                match = or(match, atLevel);
                            }
                            value = and(value, match);
                        }
                    }
                }
                case "components" -> value = and(value, entry.getValue() instanceof Map<?, ?> components
                        ? components.isEmpty() ? truth(true) : context.toolId().equals("minecraft:air")
                            || context.toolFacts().exactComponents() && !context.toolFacts().components().containsAll(components.keySet()) ? truth(false)
                        : unknown("unmodeled tool component values") : unknown("malformed tool components"));
                case "count" -> value = and(value, matchesRange(context.toolId().equals("minecraft:air") ? "0" : "1", entry.getValue()));
                default -> value = and(value, unknown("unmodeled tool field: " + entry.getKey()));
            }
        }
        observations.add("tool/enchantment predicate evaluated for the selected harvest context");
        return value;
    }

    private Truth itemSelector(Object raw) {
        List<?> selectors = raw instanceof String s ? List.of(s) : list(raw);
        if (selectors.isEmpty()) return truth(false);
        for (Object value : selectors) {
            String selector = text(value);
            if (selector.startsWith("#") ? context.toolTags().contains(selector.substring(1)) : context.toolId().equals(selector)) return truth(true);
        }
        return truth(false);
    }

    /**
     * Keep outer functions on the stack pipeline, not on an aggregate item total.
     * Order is leaf -> pool -> table -> referencing leaf -> outer pool/table.
     * Clamping E[count] is not E[clamped count], and two capped stacks still add.
     */
    private static List<Object> functionChain(Object own, List<Object> inherited) {
        if (own == null) return inherited;
        if (!(own instanceof List<?> functions))
            throw new UnsupportedOperationException("malformed block loot function list");
        if (functions.isEmpty()) return inherited;
        List<Object> result = new ArrayList<>(functions.size() + inherited.size());
        result.addAll(functions); result.addAll(inherited);
        return List.copyOf(result);
    }

    private Amount stackFunctions(List<Object> functions, int depth, String item) {
        guard(depth);
        StackCounts counts = new StackCounts(Map.of(1, 1.0), Set.of());
        for (Object raw : functions) {
            guard(depth);
            Map<String, Object> fn = object(raw);
            String type = text(fn.get("function"));
            if (Boolean.TRUE.equals(fn.get(RuntimeLootAudit.RUNTIME_MARKER))) continue; // audited separately for this concrete harvest
            Truth gate = conditions(fn.get("conditions"), depth + 1);
            if (gate.probability() <= 0) continue;
            StackCounts changed = counts;
            if (Boolean.TRUE.equals(fn.get(GenerationLootEvidence.UNRESOLVED))) {
                changed = taint(counts, "unsupported loot function representation: " + type);
            } else if (type.equals("minecraft:set_count")) {
                StackCounts supplied = integerCounts(fn.get("count"), depth + 1);
                changed = Boolean.TRUE.equals(fn.get("add"))
                        ? addCounts(counts, supplied, depth + 1)
                        : nonnegative(supplied, counts.unknown());
            } else if (type.equals("minecraft:limit_count")) {
                changed = limitCounts(counts, fn.get("limit"));
            } else if (type.equals("minecraft:apply_bonus")) {
                int level = context.enchantments().getOrDefault(text(fn.get("enchantment")), 0);
                String formula = text(fn.get("formula"));
                if (level != 0) changed = taint(counts, "bonus formula above baseline is unmodeled");
                else if (formula.equals("minecraft:binomial_with_bonus_count")) {
                    Map<String, Object> parameters = object(fn.get("parameters"));
                    // Preserve the distribution: a later count limit must clamp each bonus outcome.
                    StackCounts bonus = binomialCounts(parameters.get("extra"), parameters.get("probability"), depth + 1);
                    changed = addCounts(counts, bonus, depth + 1);
                } else if (!Set.of("minecraft:ore_drops", "minecraft:uniform_bonus_count").contains(formula))
                    changed = taint(counts, "unmodeled bonus formula");
            } else if (!Set.of("minecraft:explosion_decay", "minecraft:copy_name", "minecraft:copy_components",
                    "minecraft:copy_state", "minecraft:copy_nbt", "minecraft:set_name").contains(type)) {
                changed = taint(counts, "unmodeled loot function: " + type);
            }
            counts = mixCounts(counts, changed, gate);
        }
        if (item.equals(reservedItem)) {
            var retained = new TreeMap<Integer, Double>();
            counts.probabilities().forEach((count, probability) -> retained.merge(Math.max(0, count - 1), probability, Double::sum));
            counts = new StackCounts(retained, counts.unknown());
        }
        return counts.amount();
    }

    /** Exact bounded count distribution; unsupported/context-dependent providers stay diagnostic-only. */
    private StackCounts integerCounts(Object raw, int depth) {
        guard(depth);
        Double constant = constantNumber(raw);
        if (constant != null) {
            Integer value = integer(constant);
            return value == null ? unknownCounts("count outside supported integer range")
                    : new StackCounts(Map.of(value, 1.0), Set.of());
        }
        Map<String, Object> obj = object(raw);
        String type = text(obj.get("type"));
        if (type.equals("minecraft:uniform") || (!obj.containsKey("type") && obj.containsKey("min") && obj.containsKey("max"))) {
            Integer low = integer(constantNumber(obj.get("min"))), high = integer(constantNumber(obj.get("max")));
            if (low == null || high == null || low > high)
                return unknownCounts("unmodeled or invalid uniform count bounds");
            long size = (long) high - low + 1;
            if (size > MAX_COUNT_OUTCOMES) return unknownCounts("count distribution exceeds evaluation budget");
            Map<Integer, Double> probabilities = new TreeMap<>();
            for (long value = low; value <= high; value++) probabilities.put((int) value, 1.0 / size);
            return new StackCounts(probabilities, Set.of());
        }
        if (type.equals("minecraft:binomial")) return binomialCounts(obj.get("n"), obj.get("p"), depth + 1);
        return unknownCounts("unmodeled count number provider");
    }

    private StackCounts binomialCounts(Object rawTrials, Object rawProbability, int depth) {
        guard(depth);
        Integer trials = integer(constantNumber(rawTrials));
        Double p = constantNumber(rawProbability);
        if (trials == null || trials < 0 || p == null || p < 0 || p > 1)
            return unknownCounts("unmodeled or invalid binomial count parameters");
        if (p == 0 || trials == 0) return new StackCounts(Map.of(0, 1.0), Set.of());
        if (p == 1) return new StackCounts(Map.of(trials, 1.0), Set.of());
        if (trials >= MAX_COUNT_OUTCOMES) return unknownCounts("count distribution exceeds evaluation budget");
        double[] probabilities = new double[trials + 1]; probabilities[0] = 1;
        for (int n = 0; n < trials; n++) {
            for (int k = n + 1; k >= 0; k--) {
                guard(depth);
                probabilities[k] = probabilities[k] * (1 - p) + (k == 0 ? 0 : probabilities[k - 1] * p);
            }
        }
        Map<Integer, Double> result = new TreeMap<>();
        for (int k = 0; k <= trials; k++) if (probabilities[k] > 0) result.put(k, probabilities[k]);
        return new StackCounts(result, Set.of());
    }

    private StackCounts addCounts(StackCounts old, StackCounts added, int depth) {
        Map<Integer, Double> probabilities = new TreeMap<>();
        for (Map.Entry<Integer, Double> a : old.probabilities().entrySet()) {
            for (Map.Entry<Integer, Double> b : added.probabilities().entrySet()) {
                guard(depth);
                long count = (long) a.getKey() + b.getKey();
                if (count > Integer.MAX_VALUE || count < Integer.MIN_VALUE)
                    return taint(old, "count arithmetic outside supported integer range");
                probabilities.merge((int) Math.max(0, count), a.getValue() * b.getValue(), Double::sum);
                if (probabilities.size() > MAX_COUNT_OUTCOMES)
                    return taint(old, "count distribution exceeds evaluation budget");
            }
        }
        return new StackCounts(probabilities, union(old.unknown(), added.unknown()));
    }

    private StackCounts limitCounts(StackCounts old, Object raw) {
        Integer min = null, max = null;
        if (raw instanceof Number) {
            min = integer(constantNumber(raw)); max = min;
            if (min == null) return taint(old, "invalid count limit");
        } else if (raw instanceof Map<?, ?>) {
            Map<String, Object> bounds = object(raw);
            if (!Set.of("min", "max").containsAll(bounds.keySet()))
                return taint(old, "unmodeled count-limit fields");
            if (bounds.containsKey("min")) {
                min = integer(constantNumber(bounds.get("min")));
                if (min == null) return taint(old, "unmodeled count-limit minimum");
            }
            if (bounds.containsKey("max")) {
                max = integer(constantNumber(bounds.get("max")));
                if (max == null) return taint(old, "unmodeled count-limit maximum");
            }
        } else return taint(old, "missing or malformed count limit");
        if (min != null && max != null && min > max) return taint(old, "inverted count limit");
        Map<Integer, Double> probabilities = new TreeMap<>();
        for (Map.Entry<Integer, Double> outcome : old.probabilities().entrySet()) {
            int count = outcome.getKey();
            if (min != null) count = Math.max(count, min);
            if (max != null) count = Math.min(count, max);
            probabilities.merge(Math.max(0, count), outcome.getValue(), Double::sum);
        }
        observations.add("modeled per-stack count limit: min=" + (min == null ? "unbounded" : min)
                + ", max=" + (max == null ? "unbounded" : max));
        return new StackCounts(probabilities, old.unknown());
    }

    private static StackCounts nonnegative(StackCounts counts, Set<String> priorUnknown) {
        Map<Integer, Double> probabilities = new TreeMap<>();
        counts.probabilities().forEach((count, p) -> probabilities.merge(Math.max(0, count), p, Double::sum));
        return new StackCounts(probabilities, union(counts.unknown(), priorUnknown));
    }

    private static StackCounts mixCounts(StackCounts old, StackCounts changed, Truth gate) {
        Set<String> unknown = union(old.unknown(), changed.unknown(), gate.unknown());
        double p = gate.probability();
        if (p == 1) return new StackCounts(changed.probabilities(), unknown);
        Map<Integer, Double> probabilities = new TreeMap<>();
        old.probabilities().forEach((count, chance) -> probabilities.merge(count, chance * (1 - p), Double::sum));
        changed.probabilities().forEach((count, chance) -> probabilities.merge(count, chance * p, Double::sum));
        if (probabilities.size() > MAX_COUNT_OUTCOMES) return taint(old, "count distribution exceeds evaluation budget");
        return new StackCounts(probabilities, unknown);
    }

    private static StackCounts taint(StackCounts counts, String reason) {
        return new StackCounts(counts.probabilities(), union(counts.unknown(), Set.of(reason)));
    }
    private static StackCounts unknownCounts(String reason) { return new StackCounts(Map.of(1, 1.0), Set.of(reason)); }

    /** Only context-free constants are accepted for limits and distribution parameters. */
    private static Double constantNumber(Object raw) {
        if (raw instanceof Number n) return Double.isFinite(n.doubleValue()) ? n.doubleValue() : null;
        Map<String, Object> obj = object(raw);
        if ("minecraft:constant".equals(obj.get("type")) && obj.get("value") instanceof Number n)
            return Double.isFinite(n.doubleValue()) ? n.doubleValue() : null;
        return null;
    }
    private static Integer integer(Double value) {
        if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return null;
        return (int) Math.floor(value);
    }

    private Count number(Object raw, int depth) {
        guard(depth);
        if (raw instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0)
            return new Count(n.doubleValue(), n.doubleValue() > 0 ? 1 : 0, Set.of());
        Map<String, Object> obj = object(raw);
        String type = text(obj.get("type"));
        if (type.equals("minecraft:constant")) return number(obj.get("value"), depth + 1);
        if (type.equals("minecraft:uniform") || (!obj.containsKey("type") && obj.containsKey("min") && obj.containsKey("max"))) {
            Count low = number(obj.get("min"), depth + 1), high = number(obj.get("max"), depth + 1);
            double positive = high.mean() < 1 ? 0 : low.mean() >= 1 ? 1 : Math.floor(high.mean()) / (Math.floor(high.mean()) + 1);
            return new Count((low.mean() + high.mean()) / 2, positive, union(low.unknown(), high.unknown()));
        }
        if (type.equals("minecraft:binomial")) {
            Count n = number(obj.get("n"), depth + 1), p = number(obj.get("p"), depth + 1);
            return new Count(n.mean() * clamp(p.mean()), 1 - Math.pow(1 - clamp(p.mean()), n.mean()), union(n.unknown(), p.unknown()));
        }
        return new Count(1, 1, Set.of("unmodeled number provider"));
    }

    private static Truth matchesRange(String actual, Object test) {
        if (actual == null) return truth(false);
        if (test instanceof String || test instanceof Number || test instanceof Boolean)
            return truth(actual.equals(String.valueOf(test)) || (test instanceof Number n && finite(actual, Double.NaN) == n.doubleValue()));
        Map<String, Object> range = object(test);
        if (!Set.of("min", "max").containsAll(range.keySet()) || range.isEmpty()) return unknown("unmodeled property range");
        double value = finite(actual, Double.NaN);
        if (!Double.isFinite(value)) return unknown("non-numeric property range");
        return truth(value >= finite(range.getOrDefault("min", -Double.MAX_VALUE), -Double.MAX_VALUE)
                && value <= finite(range.getOrDefault("max", Double.MAX_VALUE), Double.MAX_VALUE));
    }

    private void fallback(Object node, Map<String, Drop> out, Set<String> seen, int depth) {
        if (depth > MAX_DEPTH) return;
        if (node instanceof List<?> list) { for (Object child : list) fallback(child, out, seen, depth + 1); return; }
        if (!(node instanceof Map<?, ?>)) return;
        Map<String, Object> obj = object(node);
        if (text(obj.get("type")).equals("minecraft:item"))
            out.put(text(obj.get("name")), new Drop(1, 1, 1, List.of("unsupported/budget-limited block loot; diagnostic only")));
        if (text(obj.get("type")).equals("minecraft:loot_table")) {
            Object ref = obj.getOrDefault("value", obj.get("name"));
            if (ref instanceof Map<?, ?>) fallback(ref, out, seen, depth + 1);
            else if (seen.add(text(ref))) fallback(tables.apply(text(ref)), out, seen, depth + 1);
        }
        for (String key : List.of("pools", "entries", "children")) fallback(obj.get(key), out, seen, depth + 1);
    }

    private static Truth truth(boolean value) { return new Truth(value ? 1 : 0, Set.of()); }
    private static Truth unknown(String reason) { return new Truth(0.5, Set.of(reason)); }
    private static Truth and(Truth a, Truth b) {
        if ((a.probability() == 0 && a.unknown().isEmpty()) || (b.probability() == 0 && b.unknown().isEmpty())) return truth(false);
        return new Truth(a.probability() * b.probability(), union(a.unknown(), b.unknown()));
    }
    private static Truth or(Truth a, Truth b) {
        if ((a.probability() == 1 && a.unknown().isEmpty()) || (b.probability() == 1 && b.unknown().isEmpty())) return truth(true);
        return new Truth(1 - (1 - a.probability()) * (1 - b.probability()), union(a.unknown(), b.unknown()));
    }
    private static void mergeExclusive(Map<String, Amount> map, String key, Amount next) {
        Amount old = map.get(key);
        map.put(key, old == null ? next : new Amount(old.occurrence() + next.occurrence(), old.count() + next.count(), union(old.unknown(), next.unknown())));
    }
    private static void mergeIndependent(Map<String, Amount> map, String key, Amount next) {
        Amount old = map.get(key);
        map.put(key, old == null ? next : new Amount(1 - (1 - old.occurrence()) * (1 - next.occurrence()),
                old.count() + next.count(), union(old.unknown(), next.unknown())));
    }
    @SafeVarargs private static Set<String> union(Set<String>... sets) {
        Set<String> result = new LinkedHashSet<>(); for (Set<String> set : sets) result.addAll(set); return Set.copyOf(result);
    }
    @SuppressWarnings("unchecked") static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
    private static List<?> list(Object value) { return value instanceof List<?> list ? list : List.of(); }
    private static String text(Object value) { return value instanceof String s ? s : ""; }
    private static double finite(Object value, double fallback) {
        try { double n = Double.parseDouble(String.valueOf(value)); return Double.isFinite(n) ? n : fallback; }
        catch (NumberFormatException exception) { return fallback; }
    }
    private static double clamp(double n) { return Double.isFinite(n) ? Math.max(0, Math.min(1, n)) : 0; }
}

package com.mistaboom.essence_ascendance.balance.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Factual generator inputs and an explicitly separate resolved-value escape hatch. */
public record BalanceOverrides(List<FactOverride> facts, Map<String, Object> exactValues) {
    public enum SubjectKind {
        ITEM, BLOCK, ITEM_TAG, BLOCK_TAG, RECIPE, RECIPE_FAMILY, SOURCE, ENEMY, PROVIDER, CAPABILITY
    }

    public record FactOverride(String id, SubjectKind kind, String selector, int priority,
                               Map<String, Object> values, String source, int line) {
        public FactOverride {
            values = immutableValues(values);
        }

        public Optional<Double> number(String key) {
            Object value = values.get(key);
            return value instanceof Number number ? Optional.of(number.doubleValue()) : Optional.empty();
        }

        public Optional<String> text(String key) {
            return Optional.ofNullable(values.get(key)).filter(String.class::isInstance).map(String.class::cast);
        }

        public Optional<Boolean> flag(String key) {
            return Optional.ofNullable(values.get(key)).filter(Boolean.class::isInstance).map(Boolean.class::cast);
        }

        public List<String> strings(String key) {
            Object value = values.get(key);
            if (!(value instanceof List<?> list)) return List.of();
            return list.stream().map(String.class::cast).toList();
        }
    }

    private static final Set<String> FLAGS = Set.of("attainable", "disabled", "creative_only", "administrative", "joke",
            "quest_gated", "include_reference", "renewable", "passive_generation", "flight", "flying_speed_compatible", "vein_mining", "area_mining");
    private static final Set<String> NUMBERS = Set.of("confidence", "resource_value", "throughput", "health", "armor", "damage",
            "attack_speed", "toughness", "mining_speed", "harvest_level", "durability", "enchantability", "startup_cost",
            "marginal_cost", "parallelizability", "player_attention", "processing_time", "output_count", "probability");
    /** Supported reference families; body is accepted as evidence but excluded from player frontiers. */
    public static final Set<String> EQUIPMENT_SLOTS = Set.of("head", "chest", "legs", "feet", "body", "offhand",
            "mainhand_melee", "mainhand_bow", "mainhand_crossbow", "mainhand_caster", "mainhand_tool");
    private static final Set<String> TEXT = Set.of("stage", "availability", "automation", "classification", "reason", "dimension", "gate", "slot");
    private static final Set<String> ARRAYS = Set.of("capabilities", "dependencies", "acquisition_sources");
    private static final Set<String> FACT_KEYS;

    static {
        Set<String> keys = new LinkedHashSet<>(Set.of("id", "kind", "selector", "priority"));
        keys.addAll(FLAGS);
        keys.addAll(NUMBERS);
        keys.addAll(TEXT);
        keys.addAll(ARRAYS);
        FACT_KEYS = Set.copyOf(keys);
    }

    public BalanceOverrides {
        facts = facts.stream().sorted(Comparator.comparing(FactOverride::id)).toList();
        exactValues = immutableValues(exactValues);
    }

    public static BalanceOverrides empty() { return new BalanceOverrides(List.of(), Map.of()); }

    public static BalanceOverrides parse(String toml, String source) {
        List<FactOverride> facts = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        Map<String, Object> exact = new TreeMap<>();
        for (BalanceToml.Table table : BalanceToml.parse(toml, source)) {
            if (table.name().isEmpty()) {
                BalanceToml.requireKeys(table, Set.of("schema_version"), source);
                BalanceToml.Value schema = table.values().get("schema_version");
                if (schema != null && (!(schema.value() instanceof Long version) || version != 1L)) {
                    throw BalanceToml.error(source, schema.line(), "schema_version must be integer 1; replace obsolete development inputs");
                }
            } else if (table.name().equals("fact") && table.array()) {
                if (facts.size() >= 10_000) throw BalanceToml.error(source, table.line(), "At most 10000 facts are supported; prefer tags or families");
                BalanceToml.requireKeys(table, FACT_KEYS, source);
                FactOverride fact = fact(table, source);
                if (!ids.add(fact.id())) throw BalanceToml.error(source, table.line(), "Duplicate fact id '" + fact.id() + "'; give each fact a unique id");
                facts.add(fact);
            } else if (table.name().equals("exact") && !table.array()) {
                table.values().forEach((path, value) -> {
                    // Bracket indexes and dotted identifiers are deliberately avoided: paths use JSON Pointer syntax.
                    if (!path.startsWith("/") || path.length() < 2 || path.contains("//")
                            || path.matches(".*~(?:[^01]|$).*")) {
                        throw BalanceToml.error(source, value.line(), "Exact key '" + path
                                + "' must be a JSON Pointer, for example /runtime/statMaxBonuses/essence_ascendance:melee_damage");
                    }
                    exact.put(path, value.value());
                });
            } else {
                throw BalanceToml.error(source, table.line(), "Unknown override table '" + table.name() + "'; use [[fact]] or [exact]");
            }
        }
        return new BalanceOverrides(facts, exact);
    }

    private static FactOverride fact(BalanceToml.Table table, String source) {
        String kindName = requiredString(table, "kind", source);
        SubjectKind kind;
        try { kind = SubjectKind.valueOf(kindName.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) {
            throw BalanceToml.error(source, table.values().get("kind").line(), "Unknown subject kind '" + kindName
                    + "'; expected item, block, item_tag, block_tag, recipe, recipe_family, source, enemy, provider or capability");
        }
        String selector = requiredString(table, "selector", source);
        if (selector.isBlank() || selector.chars().anyMatch(Character::isWhitespace)) {
            throw BalanceToml.error(source, table.values().get("selector").line(), "selector must be a nonempty identifier without whitespace");
        }
        String id = optionalString(table, "id", kindName + ":" + selector, source);
        if (id.isBlank()) throw BalanceToml.error(source, table.line(), "fact id must not be blank");
        int priority = 1000;
        BalanceToml.Value priorityValue = table.values().get("priority");
        if (priorityValue != null) {
            if (!(priorityValue.value() instanceof Long value) || value < -100_000 || value > 100_000) {
                throw BalanceToml.error(source, priorityValue.line(), "priority must be an integer between -100000 and 100000");
            }
            priority = ((Number) priorityValue.value()).intValue();
        }
        Map<String, Object> values = new TreeMap<>();
        table.values().forEach((key, entry) -> {
            if (Set.of("id", "kind", "selector", "priority").contains(key)) return;
            Object value = entry.value();
            if (FLAGS.contains(key) && !(value instanceof Boolean)) throw BalanceToml.error(source, entry.line(), key + " must be true or false");
            if (TEXT.contains(key) && (!(value instanceof String text) || text.isBlank())) throw BalanceToml.error(source, entry.line(), key + " must be a nonempty string");
            if (NUMBERS.contains(key)) {
                if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() < 0.0) {
                    throw BalanceToml.error(source, entry.line(), key + " must be a nonnegative finite number");
                }
                if (Set.of("confidence", "probability", "player_attention").contains(key) && ((Number) value).doubleValue() > 1.0) {
                    throw BalanceToml.error(source, entry.line(), key + " must be between 0 and 1");
                }
                if (key.equals("output_count")) {
                    double count = ((Number) value).doubleValue();
                    if (count < 1 || count > Integer.MAX_VALUE || count != Math.rint(count)) {
                        throw BalanceToml.error(source, entry.line(), "output_count must be a positive whole number no greater than " + Integer.MAX_VALUE + "; use probability for fractional expected outputs");
                    }
                }
            }
            if (ARRAYS.contains(key) && (!(value instanceof List<?> list)
                    || list.stream().anyMatch(element -> !(element instanceof String text) || text.isBlank()))) {
                throw BalanceToml.error(source, entry.line(), key + " must be an array of nonempty strings");
            }
            if (key.equals("slot")) checkChoice(table, key, EQUIPMENT_SLOTS, source);
            if (key.equals("stage")) checkChoice(table, key, Set.of("entry", "early", "mid", "late", "apex"), source);
            if (key.equals("availability")) checkChoice(table, key, Set.of("finite", "renewable_manual", "renewable_automated", "effectively_infinite", "unknown", "administrative"), source);
            if (key.equals("automation")) checkChoice(table, key, Set.of("none", "player_gated", "bounded", "scalable", "passive", "infinite", "unknown"), source);
            if (key.equals("classification") && kind == SubjectKind.ENEMY) {
                checkChoice(table, key, Set.of("routine", "elite", "boss", "apex", "unknown"), source);
            }
            values.put(key, value);
        });
        if (values.isEmpty() && kind != SubjectKind.PROVIDER) {
            throw BalanceToml.error(source, table.line(), "Fact '" + id + "' does not correct any factual field");
        }
        contradictory(values, "disabled", true, "attainable", true, table, source);
        contradictory(values, "creative_only", true, "include_reference", true, table, source);
        contradictory(values, "administrative", true, "include_reference", true, table, source);
        contradictory(values, "attainable", false, "include_reference", true, table, source);
        contradictory(values, "disabled", true, "include_reference", true, table, source);
        contradictory(values, "availability", "effectively_infinite", "renewable", false, table, source);
        contradictory(values, "passive_generation", true, "automation", "player_gated", table, source);
        return new FactOverride(id, kind, selector, priority, values, source, table.line());
    }

    private static void contradictory(Map<String, Object> values, String first, Object firstValue, String second, Object secondValue,
                                      BalanceToml.Table table, String source) {
        if (firstValue.equals(values.get(first)) && secondValue.equals(values.get(second))) {
            throw BalanceToml.error(source, table.values().get(second).line(), first + " = " + firstValue
                    + " contradicts " + second + " = " + secondValue + "; correct the factual override");
        }
    }

    private static void checkChoice(BalanceToml.Table table, String key, Set<String> choices, String source) {
        BalanceToml.Value value = table.values().get(key);
        if (!(value.value() instanceof String text) || !choices.contains(text)) {
            throw BalanceToml.error(source, value.line(), key + " must be one of " + String.join(", ", choices.stream().sorted().toList()));
        }
    }

    private static String requiredString(BalanceToml.Table table, String key, String source) {
        if (!table.values().containsKey(key)) throw BalanceToml.error(source, table.line(), "[[fact]] requires '" + key + "'");
        return optionalString(table, key, "", source);
    }

    private static String optionalString(BalanceToml.Table table, String key, String fallback, String source) {
        BalanceToml.Value value = table.values().get(key);
        if (value == null) return fallback;
        if (!(value.value() instanceof String text)) throw BalanceToml.error(source, value.line(), key + " must be a quoted string");
        return text;
    }

    private static Map<String, Object> immutableValues(Map<String, Object> values) {
        Map<String, Object> copy = new TreeMap<>();
        values.forEach((key, value) -> copy.put(key, value instanceof List<?> list ? List.copyOf(list) : value));
        return Collections.unmodifiableMap(new LinkedHashMap<>(copy));
    }
}

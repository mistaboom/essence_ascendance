package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mistaboom.essence_ascendance.balance.engine.OptionalIntegration;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

/** Read-only loaded modifier definitions. Never apply a modifier or roll a loot context. */
final class RuntimeLootAudit {
    static final String RUNTIME_MARKER = "essence_evidence_runtime_modifiers";
    record Modifier(String implementation, JsonObject definition, String limitation, BlockLootModifierAudit.Rule blockRule, boolean conditionsEnforced) {
        Modifier(String implementation, JsonObject definition, String limitation, BlockLootModifierAudit.Rule blockRule) {
            this(implementation, definition, limitation, blockRule, true);
        }
        Modifier(String implementation, JsonObject definition, String limitation) {
            this(implementation, definition, limitation, BlockLootModifierAudit.Rule.UNKNOWN);
        }
        boolean mayAffect(String table) {
            return !conditionsEnforced || tableConditions(definition.get("conditions"), table) != Truth.FALSE;
        }
        java.util.function.Predicate<String> tableFilter() {
            if (!conditionsEnforced) return ignored -> true;
            var scope = compileConditions(definition.get("conditions"));
            return table -> scope.named().getOrDefault(table, scope.other()) != Truth.FALSE;
        }
    }
    private enum Truth { TRUE, FALSE, UNKNOWN }
    /** Table-only predicates have finitely many named exceptions and one default.
     * Compile once per audit instead of traversing large JSON disjunctions for every
     * table/harvest. UNKNOWN still admits a modifier; no runtime predicate is invoked. */
    private record TableScope(Truth other, Map<String, Truth> named) { }
    private static TableScope constantScope(Truth value) { return new TableScope(value, Map.of()); }
    private static TableScope compileConditions(JsonElement value) {
        if (value == null) return constantScope(Truth.TRUE);
        if (!value.isJsonArray()) return constantScope(Truth.UNKNOWN);
        return combineScopes(value.getAsJsonArray().asList().stream().map(RuntimeLootAudit::compileCondition).toList(), true);
    }
    private static TableScope compileCondition(JsonElement value) {
        if (value == null || !value.isJsonObject()) return constantScope(Truth.UNKNOWN);
        var c = value.getAsJsonObject();
        String type = c.has("condition") ? c.get("condition").getAsString() : "";
        if (type.equals("neoforge:loot_table_id") && c.has("loot_table_id"))
            return new TableScope(Truth.FALSE, Map.of(c.get("loot_table_id").getAsString(), Truth.TRUE));
        if (type.equals("minecraft:inverted")) {
            var inner = compileCondition(c.get("term")); var named = new HashMap<String, Truth>();
            inner.named().forEach((table, truth) -> named.put(table, invert(truth)));
            return new TableScope(invert(inner.other()), Map.copyOf(named));
        }
        if ((type.equals("minecraft:any_of") || type.equals("minecraft:all_of")) && c.has("terms") && c.get("terms").isJsonArray())
            return combineScopes(c.getAsJsonArray("terms").asList().stream().map(RuntimeLootAudit::compileCondition).toList(), type.equals("minecraft:all_of"));
        return constantScope(Truth.UNKNOWN);
    }
    private static Truth invert(Truth truth) { return truth == Truth.UNKNOWN ? truth : truth == Truth.TRUE ? Truth.FALSE : Truth.TRUE; }
    private static TableScope combineScopes(List<TableScope> scopes, boolean all) {
        // Counts avoid quadratic map copying for the large any_of lists common in packs.
        int[] defaults = new int[Truth.values().length];
        for (var scope : scopes) defaults[scope.other().ordinal()]++;
        var counts = new HashMap<String, int[]>();
        for (var scope : scopes) scope.named().forEach((table, truth) -> {
            var row = counts.computeIfAbsent(table, ignored -> defaults.clone());
            row[scope.other().ordinal()]--; row[truth.ordinal()]++;
        });
        Truth other = combinedTruth(defaults, all); var named = new HashMap<String, Truth>();
        counts.forEach((table, row) -> { Truth truth = combinedTruth(row, all); if (truth != other) named.put(table, truth); });
        return new TableScope(other, Map.copyOf(named));
    }
    private static Truth combinedTruth(int[] counts, boolean all) {
        if (counts[(all ? Truth.FALSE : Truth.TRUE).ordinal()] > 0) return all ? Truth.FALSE : Truth.TRUE;
        if (counts[Truth.UNKNOWN.ordinal()] > 0) return Truth.UNKNOWN;
        return all ? Truth.TRUE : Truth.FALSE;
    }
    private static Truth tableConditions(JsonElement value, String table) {
        if (value == null) return Truth.TRUE;
        if (!value.isJsonArray()) return Truth.UNKNOWN;
        Truth result = Truth.TRUE;
        for (var term : value.getAsJsonArray()) result = and(result, tableCondition(term, table));
        return result;
    }
    private static Truth tableCondition(JsonElement value, String table) {
        if (value == null || !value.isJsonObject()) return Truth.UNKNOWN;
        var c = value.getAsJsonObject();
        String type = c.has("condition") ? c.get("condition").getAsString() : "";
        if (type.equals("neoforge:loot_table_id") && c.has("loot_table_id"))
            return table.equals(c.get("loot_table_id").getAsString()) ? Truth.TRUE : Truth.FALSE;
        if (type.equals("minecraft:inverted")) {
            var inner = tableCondition(c.get("term"), table);
            return inner == Truth.UNKNOWN ? inner : inner == Truth.TRUE ? Truth.FALSE : Truth.TRUE;
        }
        if ((type.equals("minecraft:any_of") || type.equals("minecraft:all_of")) && c.has("terms") && c.get("terms").isJsonArray()) {
            boolean all = type.equals("minecraft:all_of"); Truth result = all ? Truth.TRUE : Truth.FALSE;
            for (var term : c.getAsJsonArray("terms")) {
                var next = tableCondition(term, table);
                result = all ? and(result, next) : result == Truth.TRUE || next == Truth.TRUE ? Truth.TRUE
                        : result == Truth.UNKNOWN || next == Truth.UNKNOWN ? Truth.UNKNOWN : Truth.FALSE;
            }
            return result;
        }
        return Truth.UNKNOWN;
    }
    private static Truth and(Truth a, Truth b) { return a == Truth.FALSE || b == Truth.FALSE ? Truth.FALSE
            : a == Truth.UNKNOWN || b == Truth.UNKNOWN ? Truth.UNKNOWN : Truth.TRUE; }
    static List<Modifier> capture(GenerationDataSnapshot inputs) {
        String version = inputs.installedVersion("neoforge");
        if (version == null) return List.of();
        return captureOptional(version, () -> {
            try { return captureEffective(inputs); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot inspect effective global loot modifiers", failure); }
        });
    }
    static List<Modifier> captureOptional(String version, Supplier<List<Modifier>> capture) {
        if (version == null) return List.of();
        var attempt = OptionalIntegration.attempt("neoforge:" + version, "inspect effective global loot modifiers", () -> List.copyOf(capture.get()));
        return attempt.value().orElseGet(() -> unknown(version, attempt.failure()));
    }
    static List<Modifier> unknown(String version, String failure) {
        // Missing modifier information can affect every table; never represent it as ABSENT.
        return List.of(new Modifier("neoforge:" + version, new JsonObject(),
                "Global-loot-modifier capture unavailable: " + failure, BlockLootModifierAudit.Rule.UNKNOWN, false));
    }
    @SuppressWarnings("unchecked")
    private static List<Modifier> captureEffective(GenerationDataSnapshot inputs) throws ReflectiveOperationException {
        var handlerType = Class.forName("net.neoforged.neoforge.common.NeoForgeEventHandler");
        var managerType = Class.forName("net.neoforged.neoforge.common.loot.LootModifierManager");
        var modifierType = Class.forName("net.neoforged.neoforge.common.loot.IGlobalLootModifier");
        var getter = handlerType.getDeclaredMethod("getLootModifierManager");
        var allModifiers = managerType.getMethod("getAllLootMods");
        var codecField = modifierType.getField("DIRECT_CODEC");
        if (!inspectionApiSupported(getter, handlerType, managerType, allModifiers, modifierType, codecField))
            throw new IllegalStateException("Global loot modifier inspection API signature changed");
        getter.setAccessible(true);
        Object manager = Objects.requireNonNull(invoke(getter, null), "Global loot modifier manager not ready");
        if (manager.getClass() != managerType) throw new IllegalStateException("Custom global loot modifier manager is not audited");
        var modifiers = (Collection<?>) Objects.requireNonNull(invoke(allModifiers, manager), "Global loot modifiers are not ready");
        Codec<Object> codec = (Codec<Object>) Objects.requireNonNull(codecField.get(null), "Global loot modifier codec is not ready");
        var ops = inputs.server().registries().compositeAccess().createSerializationContext(JsonOps.INSTANCE);
        List<Modifier> result = new ArrayList<>();
        for (Object modifier : modifiers) {
            if (!modifierType.isInstance(modifier)) throw new IllegalStateException("Global loot modifier implementation no longer matches its interface");
            var apply = modifier.getClass().getMethod("apply", it.unimi.dsi.fastutil.objects.ObjectArrayList.class,
                    net.minecraft.world.level.storage.loot.LootContext.class);
            if (java.lang.reflect.Modifier.isStatic(apply.getModifiers())
                    || apply.getReturnType() != it.unimi.dsi.fastutil.objects.ObjectArrayList.class)
                throw new IllegalStateException("Global loot modifier apply signature changed");
            var encoded = codec.encodeStart(ops, modifier);
            var definition = encoded.isSuccess() ? encoded.getOrThrow().getAsJsonObject() : new JsonObject();
            result.add(new Modifier(modifier.getClass().getName(), definition,
                    "Unsupported runtime modifier output; base block-harvest contract is recorded separately"
                            + encoded.error().map(e -> ": " + e.message()).orElse(""),
                    encoded.isSuccess() ? BlockLootModifierAudit.captureRule(modifier.getClass().getName(), inputs) : BlockLootModifierAudit.Rule.UNKNOWN,
                    apply.getDeclaringClass().getName().equals("net.neoforged.neoforge.common.loot.LootModifier")));
        }
        // Runtime order remains available in diagnostics; it is not executed or assumed commutative.
        return List.copyOf(result);
    }
    static boolean inspectionApiSupported(Method getter, Class<?> handlerType, Class<?> managerType,
                                          Method allModifiers, Class<?> modifierType, Field codecField) {
        return getter.getDeclaringClass() == handlerType && getter.getName().equals("getLootModifierManager")
                && java.lang.reflect.Modifier.isStatic(getter.getModifiers()) && getter.getParameterCount() == 0
                && getter.getReturnType() == managerType
                && allModifiers.getDeclaringClass() == managerType && allModifiers.getName().equals("getAllLootMods")
                && !java.lang.reflect.Modifier.isStatic(allModifiers.getModifiers()) && allModifiers.getParameterCount() == 0
                && allModifiers.getReturnType() == Collection.class
                && codecField.getDeclaringClass() == modifierType && codecField.getName().equals("DIRECT_CODEC")
                && java.lang.reflect.Modifier.isStatic(codecField.getModifiers())
                && java.lang.reflect.Modifier.isFinal(codecField.getModifiers()) && codecField.getType() == Codec.class;
    }
    private static Object invoke(Method method, Object receiver) throws ReflectiveOperationException {
        try { return method.invoke(receiver); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error fatal) throw fatal;
            throw failure;
        }
    }
    static void mark(Map<ResourceLocation, JsonObject> tables, List<Modifier> modifiers, List<String> lootrExtensions) {
        var filters = modifiers.stream().map(Modifier::tableFilter).toList();
        for (var entry : tables.entrySet()) {
            List<String> reasons = new ArrayList<>();
            if (!lootrExtensions.isEmpty()) reasons.add("Unsupported Lootr extension callbacks; see saved lootr.unsupported");
            List<Integer> affected = new ArrayList<>();
            for (int index = 0; index < modifiers.size(); index++) if (filters.get(index).test(entry.getKey().toString())) affected.add(index);
            if (!affected.isEmpty()) reasons.add("Unsupported runtime modifier indices=" + affected + "; see saved runtimeModifiers");
            if (reasons.isEmpty()) continue;
            JsonArray functions = entry.getValue().has("functions") ? entry.getValue().getAsJsonArray("functions") : new JsonArray();
            JsonObject marker = new JsonObject(); marker.addProperty(GenerationLootEvidence.UNRESOLVED, true);
            marker.addProperty(RUNTIME_MARKER, lootrExtensions.isEmpty());
            marker.addProperty("essence_evidence_function_class", "runtime_loot_modification");
            marker.addProperty("essence_evidence_failure", String.join("; ", reasons)); functions.add(marker);
            entry.getValue().add("functions", functions);
        }
    }
}

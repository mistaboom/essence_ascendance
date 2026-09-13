package com.mistaboom.essence_ascendance.mapping;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomically published exact yields and derived whole-number command projections.
 * All factual and exact overrides are resolved and conservation-checked during
 * generation; no runtime selector or priority layer can bypass the profile.
 */
public final class ItemEssenceMappingRegistry {
    private static volatile State state = State.empty();
    private static volatile ReloadReport lastReload = ReloadReport.notLoaded();
    private ItemEssenceMappingRegistry() { }

    public static ItemEssenceMappingResult resolve(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack == null || stack.isEmpty() ? Items.AIR : stack.getItem());
        if (stack == null || stack.isEmpty()) return unmapped(id);
        return state.resolved().getOrDefault(id, unmapped(id));
    }

    /** Exact generated dissolution amounts in millionths of Essence. */
    public static Map<EssenceDefinition, Long> resolveDissolution(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Map.of();
        return state.microYields().getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), Map.of());
    }

    /** Build both exact gameplay and display projections before one atomic publish. */
    public static synchronized void installResolved(
            Map<ResourceLocation, Map<EssenceDefinition, Long>> microYields,
            LoadSummary summary, Runnable beforePublish) {
        Map<ResourceLocation, ItemEssenceMappingResult> resolved = new LinkedHashMap<>();
        Map<ResourceLocation, Map<EssenceDefinition, Long>> exact = new LinkedHashMap<>();
        Map<ResourceLocation, String> origins = new LinkedHashMap<>();
        List<ItemEssenceMappingDefinition> definitions = new ArrayList<>();
        microYields.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = entry.getKey();
            if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown generated item " + id);
            Map<EssenceDefinition, Long> floors = new LinkedHashMap<>();
            Map<EssenceDefinition, Long> positive = new LinkedHashMap<>();
            long total = 0;
            for (Map.Entry<EssenceDefinition, Long> output : entry.getValue().entrySet()) {
                if (output.getKey() == null || output.getValue() < 0) throw new IllegalArgumentException("Invalid generated route " + id);
                total = Math.addExact(total, output.getValue());
                if (output.getValue() > 0) positive.put(output.getKey(), output.getValue());
                long whole = output.getValue() / FractionalAmountService.SCALE;
                if (whole > 0) floors.put(output.getKey(), whole);
            }
            ResourceLocation ruleId = ResourceLocation.fromNamespaceAndPath("essence_ascendance", "generated/" + id.getNamespace() + "/" + id.getPath());
            definitions.add(new ItemEssenceMappingDefinition(ruleId, 0,
                    ItemEssenceMappingDefinition.SelectorType.ITEM, id, BuiltInRegistries.ITEM.get(id), null, floors));
            resolved.put(id, new ItemEssenceMappingResult(id, List.of(ruleId), List.of(ruleId), 0, floors));
            exact.put(id, Map.copyOf(positive));
            origins.put(id, positive.isEmpty() ? "generated_block" : "generated_balance");
        });
        long next = Math.addExact(state.generation(), 1);
        State candidate = new State(next, List.copyOf(definitions), Map.copyOf(resolved), Map.copyOf(origins), Map.copyOf(exact));
        ReloadReport report = new ReloadReport(true, next, definitions.size(), 0, 0, 0, 0,
                definitions.size(), definitions.size(), 0, summary.warnings(), List.of());
        beforePublish.run();
        state = candidate;
        lastReload = report;
    }

    public static String source(ResourceLocation itemId) {
        return state.origins().getOrDefault(itemId, "none");
    }
    public static List<ItemEssenceMappingDefinition> definitions() { return state.definitions(); }
    public static ReloadReport lastReload() { return lastReload; }
    public static long generation() { return state.generation(); }

    static synchronized void rejectReload(LoadSummary summary, List<String> errors) {
        lastReload = new ReloadReport(false, state.generation(), summary.bundledDefaultCount(), summary.removedDefaultCount(),
                summary.replacedDefaultCount(), summary.configFileCount(), summary.configMappingCount(),
                state.definitions().size(), countItemRules(state.definitions()),
                state.definitions().size() - countItemRules(state.definitions()), summary.warnings(), errors);
        EssenceAscendance.LOGGER.error("Rejected Essence mapping reload; keeping generation {}: {}", state.generation(), errors);
    }

    static synchronized void clear() {
        state = State.empty(Math.addExact(state.generation(), 1L));
        lastReload = ReloadReport.notLoaded();
    }

    private static int countItemRules(List<ItemEssenceMappingDefinition> definitions) {
        return (int) definitions.stream().filter(rule -> rule.selectorType() == ItemEssenceMappingDefinition.SelectorType.ITEM).count();
    }
    private static ItemEssenceMappingResult unmapped(ResourceLocation id) {
        return new ItemEssenceMappingResult(id, List.of(), List.of(), null, Map.of());
    }
    private record State(long generation, List<ItemEssenceMappingDefinition> definitions,
                         Map<ResourceLocation, ItemEssenceMappingResult> resolved,
                         Map<ResourceLocation, String> origins,
                         Map<ResourceLocation, Map<EssenceDefinition, Long>> microYields) {
        static State empty() { return empty(0L); }
        static State empty(long generation) { return new State(generation, List.of(), Map.of(), Map.of(), Map.of()); }
    }

    public record LoadSummary(
            int bundledDefaultCount,
            int removedDefaultCount,
            int replacedDefaultCount,
            int configFileCount,
            int configMappingCount,
            List<String> warnings
    ) {
        public LoadSummary {
            warnings =
                    List.copyOf(
                            warnings
                    );
        }

        static LoadSummary empty() {
            return new LoadSummary(
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of()
            );
        }
    }

    public record ReloadReport(
            boolean successful,
            long generation,
            int bundledDefaultCount,
            int removedDefaultCount,
            int replacedDefaultCount,
            int configFileCount,
            int configMappingCount,
            int activeMappingCount,
            int explicitItemRuleCount,
            int tagRuleCount,
            List<String> warnings,
            List<String> errors
    ) {
        public ReloadReport {
            warnings =
                    List.copyOf(
                            warnings
                    );

            errors =
                    List.copyOf(
                            errors
                    );
        }

        private static ReloadReport notLoaded() {
            return new ReloadReport(
                    false,
                    0L,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of(
                            "Mappings have not completed their first server startup load yet."
                    )
            );
        }
    }
}

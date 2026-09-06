package com.mistaboom.essence_ascendance.mapping;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomically published, pre-resolved generation. Explicit rules beat the generated
 * baseline even at a lower numeric priority. Priority/tied addition remains
 * unchanged WITHIN the explicit layer; empty winning output blocks the baseline.
 * Tags resolve only while staging a generation, so a rejected reload cannot mix
 * old numbers with newly changed tag membership. No graph evaluation on ticks.
 */
public final class ItemEssenceMappingRegistry {
    private static final Comparator<ItemEssenceMappingDefinition> ORDER = Comparator
            .comparingInt(ItemEssenceMappingDefinition::priority).reversed()
            .thenComparing(definition -> definition.id().toString());
    private static volatile State state = State.empty();
    private static volatile ReloadReport lastReload = ReloadReport.notLoaded();
    private ItemEssenceMappingRegistry() { }

    public static ItemEssenceMappingResult resolve(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack == null || stack.isEmpty() ? Items.AIR : stack.getItem());
        if (stack == null || stack.isEmpty()) return unmapped(id);
        return state.resolved().getOrDefault(id, unmapped(id));
    }

    public static String source(ResourceLocation itemId) {
        return state.origins().getOrDefault(itemId, "none");
    }
    public static List<ItemEssenceMappingDefinition> definitions() { return state.definitions(); }
    public static ReloadReport lastReload() { return lastReload; }
    public static long generation() { return state.generation(); }

    static synchronized void install(List<ItemEssenceMappingDefinition> explicit,
                                     Map<ResourceLocation, ItemEssenceMappingDefinition> generated,
                                     LoadSummary summary, Runnable beforePublish) {
        List<ItemEssenceMappingDefinition> ordered = explicit.stream().sorted(ORDER).toList();
        Map<ResourceLocation, ItemEssenceMappingResult> resolved = new LinkedHashMap<>();
        Map<ResourceLocation, String> origins = new LinkedHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            ItemStack stack = new ItemStack(item);
            List<ItemEssenceMappingDefinition> matches = ordered.stream().filter(rule -> rule.matches(stack)).toList();
            if (!matches.isEmpty()) {
                resolved.put(id, resolveRules(id, matches));
                origins.put(id, resolved.get(id).outputs().isEmpty() ? "explicit_block" : "explicit");
            } else if (generated.containsKey(id)) {
                resolved.put(id, resolveRules(id, List.of(generated.get(id))));
                origins.put(id, "procedural");
            }
        }
        List<ItemEssenceMappingDefinition> all = new ArrayList<>(ordered);
        all.addAll(generated.values());
        all.sort(ORDER);
        long next = Math.addExact(state.generation(), 1L);
        State complete = new State(next, List.copyOf(all), Map.copyOf(resolved), Map.copyOf(origins));
        ReloadReport report = new ReloadReport(true, next, summary.bundledDefaultCount(), summary.removedDefaultCount(),
                summary.replacedDefaultCount(), summary.configFileCount(), summary.configMappingCount(), all.size(),
                countItemRules(all), all.size() - countItemRules(all), summary.warnings(), List.of());
        // Persist a newly calculated cache only after ALL merged-table validation.
        // A failed write/rename cannot replace either the live table or its report.
        beforePublish.run();
        state = complete;
        lastReload = report;
        EssenceAscendance.LOGGER.info("Installed Essence mapping generation {}: {} generated defaults, {} explicit rules, {} resolved items",
                next, generated.size(), explicit.size(), resolved.size());
        summary.warnings().forEach(warning -> EssenceAscendance.LOGGER.warn("Essence mappings: {}", warning));
    }

    private static ItemEssenceMappingResult resolveRules(ResourceLocation itemId,
                                                        List<ItemEssenceMappingDefinition> matches) {
        int priority = matches.getFirst().priority();
        List<ResourceLocation> applied = new ArrayList<>();
        Map<EssenceDefinition, Long> outputs = new LinkedHashMap<>();
        for (ItemEssenceMappingDefinition rule : matches) {
            if (rule.priority() != priority) break;
            applied.add(rule.id());
            rule.outputs().forEach((essence, amount) -> {
                if (amount < 0) throw new IllegalArgumentException("Negative output in " + rule.id());
                if (amount > 0) outputs.merge(essence, amount, Math::addExact);
            });
        }
        // Also check the complete multi-Essence total before publication.
        long total = 0L;
        for (long amount : outputs.values()) total = Math.addExact(total, amount);
        return new ItemEssenceMappingResult(itemId, matches.stream().map(ItemEssenceMappingDefinition::id).toList(),
                applied, priority, outputs);
    }

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
                         Map<ResourceLocation, String> origins) {
        static State empty() { return empty(0L); }
        static State empty(long generation) { return new State(generation, List.of(), Map.of(), Map.of()); }
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

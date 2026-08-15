package com.mistaboom.essence_ascendance.mapping;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Runtime registry for the final, already-merged item -> Attribute Essence
 * mapping generation.
 *
 * Item resolution:
 * 1. collect every matching mapping;
 * 2. determine the highest matching priority;
 * 3. ignore lower-priority mappings;
 * 4. merge all rules tied at the winning priority additively.
 *
 * A winning mapping with an empty outputs object therefore blocks lower rules.
 */
public final class ItemEssenceMappingRegistry {

    private static final Comparator<ItemEssenceMappingDefinition> DEFINITION_ORDER =
            Comparator
                    .comparingInt(
                            ItemEssenceMappingDefinition::priority
                    )
                    .reversed()
                    .thenComparing(
                            definition ->
                                    definition.id()
                                            .toString()
                    );

    private static volatile State state =
            State.empty();

    private static volatile ReloadReport lastReload =
            ReloadReport.notLoaded();

    private static long generation =
            0L;

    private ItemEssenceMappingRegistry() {
    }

    public static ItemEssenceMappingResult resolve(
            ItemStack stack
    ) {
        ResourceLocation itemId =
                stack == null
                        || stack.isEmpty()
                        ? BuiltInRegistries.ITEM
                                .getKey(
                                        net.minecraft.world.item.Items.AIR
                                )
                        : BuiltInRegistries.ITEM
                                .getKey(
                                        stack.getItem()
                                );

        if (stack == null
                || stack.isEmpty()) {
            return unmapped(
                    itemId
            );
        }

        State snapshot =
                state;

        List<ItemEssenceMappingDefinition> matches =
                new ArrayList<>();

        for (ItemEssenceMappingDefinition definition :
                snapshot.definitions()) {

            if (definition.matches(
                    stack
            )) {
                matches.add(
                        definition
                );
            }
        }

        if (matches.isEmpty()) {
            return unmapped(
                    itemId
            );
        }

        matches.sort(
                DEFINITION_ORDER
        );

        int winningPriority =
                matches.getFirst()
                        .priority();

        List<ResourceLocation> matchedIds =
                matches.stream()
                        .map(
                                ItemEssenceMappingDefinition::id
                        )
                        .toList();

        List<ResourceLocation> appliedIds =
                new ArrayList<>();

        Map<EssenceDefinition, Long> outputs =
                new LinkedHashMap<>();

        for (ItemEssenceMappingDefinition definition :
                matches) {

            if (definition.priority()
                    != winningPriority) {
                break;
            }

            appliedIds.add(
                    definition.id()
            );

            for (Map.Entry<EssenceDefinition, Long> output :
                    definition.outputs()
                            .entrySet()) {

                outputs.merge(
                        output.getKey(),
                        output.getValue(),
                        Math::addExact
                );
            }
        }

        List<Map.Entry<EssenceDefinition, Long>> sortedOutputs =
                outputs.entrySet()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        entry ->
                                                entry.getKey()
                                                        .id()
                                                        .toString()
                                )
                        )
                        .toList();

        Map<EssenceDefinition, Long> orderedOutputs =
                new LinkedHashMap<>();

        for (Map.Entry<EssenceDefinition, Long> entry :
                sortedOutputs) {

            orderedOutputs.put(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        return new ItemEssenceMappingResult(
                itemId,
                matchedIds,
                appliedIds,
                winningPriority,
                orderedOutputs
        );
    }

    public static List<ItemEssenceMappingDefinition> definitions() {
        return state.definitions();
    }

    public static ReloadReport lastReload() {
        return lastReload;
    }

    public static long generation() {
        return generation;
    }

    static synchronized void install(
            List<ItemEssenceMappingDefinition> definitions,
            LoadSummary summary
    ) {
        List<ItemEssenceMappingDefinition> ordered =
                definitions.stream()
                        .sorted(
                                DEFINITION_ORDER
                        )
                        .toList();

        generation++;

        state =
                new State(
                        ordered
                );

        long itemRules =
                ordered.stream()
                        .filter(
                                definition ->
                                        definition.selectorType()
                                                == ItemEssenceMappingDefinition.SelectorType.ITEM
                        )
                        .count();

        long tagRules =
                ordered.size()
                        - itemRules;

        lastReload =
                new ReloadReport(
                        true,
                        generation,
                        summary.bundledDefaultCount(),
                        summary.removedDefaultCount(),
                        summary.replacedDefaultCount(),
                        summary.configFileCount(),
                        summary.configMappingCount(),
                        ordered.size(),
                        (int) itemRules,
                        (int) tagRules,
                        List.copyOf(
                                summary.warnings()
                        ),
                        List.of()
                );

        EssenceAscendance.LOGGER.info(
                "Loaded {} active Essence item mappings ({} bundled defaults, {} removed, {} replaced, {} config mappings, generation {})",
                ordered.size(),
                summary.bundledDefaultCount(),
                summary.removedDefaultCount(),
                summary.replacedDefaultCount(),
                summary.configMappingCount(),
                generation
        );

        for (String warning :
                summary.warnings()) {

            EssenceAscendance.LOGGER.warn(
                    "Essence item mapping: {}",
                    warning
            );
        }
    }

    static synchronized void rejectReload(
            LoadSummary summary,
            List<String> errors
    ) {
        lastReload =
                new ReloadReport(
                        false,
                        generation,
                        summary.bundledDefaultCount(),
                        summary.removedDefaultCount(),
                        summary.replacedDefaultCount(),
                        summary.configFileCount(),
                        summary.configMappingCount(),
                        state.definitions()
                                .size(),
                        countItemRules(
                                state.definitions()
                        ),
                        countTagRules(
                                state.definitions()
                        ),
                        List.copyOf(
                                summary.warnings()
                        ),
                        List.copyOf(
                                errors
                        )
                );

        EssenceAscendance.LOGGER.error(
                "Rejected Essence item-mapping config reload with {} error(s). Keeping generation {} with {} active mappings.",
                errors.size(),
                generation,
                state.definitions()
                        .size()
        );

        for (String error :
                errors) {

            EssenceAscendance.LOGGER.error(
                    "  {}",
                    error
            );
        }
    }

    private static int countItemRules(
            List<ItemEssenceMappingDefinition> definitions
    ) {
        return (int) definitions.stream()
                .filter(
                        definition ->
                                definition.selectorType()
                                        == ItemEssenceMappingDefinition.SelectorType.ITEM
                )
                .count();
    }

    private static int countTagRules(
            List<ItemEssenceMappingDefinition> definitions
    ) {
        return definitions.size()
                - countItemRules(
                        definitions
                );
    }

    private static ItemEssenceMappingResult unmapped(
            ResourceLocation itemId
    ) {
        return new ItemEssenceMappingResult(
                itemId,
                List.of(),
                List.of(),
                null,
                Map.of()
        );
    }

    private record State(
            List<ItemEssenceMappingDefinition> definitions
    ) {
        private State {
            definitions =
                    List.copyOf(
                            definitions
                    );
        }

        private static State empty() {
            return new State(
                    List.of()
            );
        }
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

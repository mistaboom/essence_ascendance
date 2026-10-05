package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.engine.SourceAvailability;
import java.util.*;
import com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.Timer;
import static com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.*;

/** Detached effective settings. No reference to Lootr classes, levels, containers, players or inventories. */
public record LootrPolicy(boolean installed, boolean disabled, boolean team,
                          Set<String> blockedTables, Set<String> blockedMods, Set<String> blockedDimensions,
                          Rule refresh, Rule decay, List<String> conversionConditions, List<String> unsupported,
                          Map<String, List<String>> conversionTags) {
    public LootrPolicy(boolean installed, boolean disabled, boolean team, Set<String> blockedTables, Set<String> blockedMods,
                       Set<String> blockedDimensions, Rule refresh, Rule decay, List<String> conversionConditions, List<String> unsupported) {
        this(installed, disabled, team, blockedTables, blockedMods, blockedDimensions, refresh, decay, conversionConditions, unsupported, Map.of());
    }
    public record Rule(boolean all, Set<String> tables, Set<String> mods, Set<String> dimensions,
                       Set<String> structures, int ticks, boolean startTicking, boolean performTicking) {
        public Rule {
            tables = sorted(tables); mods = sorted(mods); dimensions = sorted(dimensions); structures = sorted(structures);
        }
        Timer timer(String table, String structure, List<String> contexts) {
            boolean known = all || tables.contains(table) || mods.contains(namespace(table));
            boolean spatial = structures.contains(structure) || contexts.stream().anyMatch(dimensions::contains);
            boolean unresolvedSpatial = (!structures.isEmpty() && structure.isEmpty()) || (!dimensions.isEmpty() && contexts.isEmpty());
            Applicability applies = known ? Applicability.ON : spatial || unresolvedSpatial ? Applicability.CONDITIONAL : Applicability.OFF;
            return new Timer(applies, ticks, List.of("all=" + all, "tables=" + new TreeSet<>(tables), "mods=" + new TreeSet<>(mods),
                    "dimensions=" + new TreeSet<>(dimensions), "structures=" + new TreeSet<>(structures),
                    "start_while_ticking=" + startTicking, "perform_while_ticking=" + performTicking,
                    "Container must support refresh/decay; ticking requires hasBeenOpened; open/trap/ticking starts and processes timer; no guaranteed throughput"));
        }
    }
    public LootrPolicy {
        blockedTables = sorted(blockedTables); blockedMods = sorted(blockedMods); blockedDimensions = sorted(blockedDimensions);
        conversionConditions = List.copyOf(conversionConditions); unsupported = List.copyOf(unsupported);
        Map<String, List<String>> tags = new TreeMap<>();
        conversionTags.forEach((key, values) -> tags.put(key, values.stream().sorted().distinct().toList()));
        conversionTags = Collections.unmodifiableMap(tags);
    }
    private static final Rule OFF = new Rule(false, Set.of(), Set.of(), Set.of(), Set.of(), 0, false, false);
    public static final LootrPolicy ABSENT = new LootrPolicy(false, false, false, Set.of(), Set.of(), Set.of(), OFF, OFF, List.of(), List.of());
    public SourceAvailability describe(String table, String structure, List<String> dimensions, boolean conversionProven,
                                       boolean accessProven, double chance, double count, int unresolved) {
        boolean excluded = !installed || disabled || blockedTables.contains(table) || blockedMods.contains(namespace(table))
                || (!dimensions.isEmpty() && dimensions.stream().allMatch(blockedDimensions::contains));
        boolean mixed = dimensions.stream().anyMatch(blockedDimensions::contains);
        List<String> conditions = new ArrayList<>(), uncertainty = new ArrayList<>(unsupported);
        if (unresolved > 0) conditions.add("loot_conditions_unresolved=" + unresolved);
        if (unresolved > 0) uncertainty.add("Unsupported loot condition/function/runtime modification; acquisition advisory only");
        if (!accessProven) uncertainty.add("Configured structure access not proven; registry presence alone is insufficient");
        Scope scope = Scope.SHARED;
        Timer r = OFF.timer(table, structure, dimensions), d = OFF.timer(table, structure, dimensions);
        Category category = Category.FINITE_SHARED;
        if (!excluded) {
            conditions.addAll(conversionConditions);
            scope = conversionProven && !mixed && !dimensions.isEmpty() ? team ? Scope.TEAM : Scope.PLAYER : Scope.CONDITIONAL_PERSONALIZATION;
            if (scope == Scope.CONDITIONAL_PERSONALIZATION)
                uncertainty.add("Conversion requires an eligible container/type and allowed dimension; shared fallback is the same opportunity");
            r = refresh.timer(table, structure, dimensions); d = decay.timer(table, structure, dimensions);
            category = d.applicability() != Applicability.OFF ? Category.ACCESS_LIMITED
                    : r.applicability() == Applicability.OFF ? Category.FINITE_PERSONALIZED
                    : r.applicability() == Applicability.ON && scope != Scope.CONDITIONAL_PERSONALIZATION
                        ? Category.REFRESHABLE : Category.CONDITIONAL_RENEWABLE;
        } else conditions.add("Lootr absent/disabled or conversion excluded; ordinary finite shared fallback");
        if (!unsupported.isEmpty()) category = Category.UNKNOWN;
        return new SourceAvailability(table, category, scope, structure.isEmpty() ? List.of() : List.of(structure), dimensions,
                conditions, r, d, accessProven && unresolved == 0, chance, count, uncertainty);
    }
    private static String namespace(String id) { return id.split(":", 2)[0]; }
    private static Set<String> sorted(Set<String> values) { return Collections.unmodifiableSortedSet(new TreeSet<>(values)); }
}

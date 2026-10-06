package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Mechanical dependency closure. Layout hints and catalog tiers have no authority here. */
public final class SkillProgressionGraph {
    private SkillProgressionGraph() { }

    public static Map<ResourceLocation, ResourceLocation> close(Collection<SkillDefinition> definitions,
            Map<ResourceLocation, ResourceLocation> candidates) {
        return close(definitions, candidates, Map.of());
    }

    public static Map<ResourceLocation, ResourceLocation> close(Collection<SkillDefinition> definitions,
            Map<ResourceLocation, ResourceLocation> candidates, Map<ResourceLocation, ResourceLocation> minimumTiers) {
        Map<ResourceLocation, SkillDefinition> skills = new TreeMap<>();
        definitions.forEach(s -> { if (skills.put(s.id(), s) != null) throw new IllegalArgumentException("Duplicate skill node: " + s.id()); });
        if (!skills.keySet().equals(candidates.keySet())) throw new IllegalArgumentException("Incomplete generated skill placement");
        var ordered = new ArrayList<SkillDefinition>(); var done = new HashSet<ResourceLocation>();
        for (var skill : skills.values()) visit(skill, skills, done, new HashSet<>(), ordered);
        var tiers = AscendanceTierRegistry.powerTiers().stream().sorted(Comparator.comparingInt(t -> t.order())).map(t -> t.id()).toList();
        var peers = new TreeMap<ResourceLocation, List<ResourceLocation>>();
        skills.values().forEach(s -> s.choiceGroupId().ifPresent(g -> peers.computeIfAbsent(g, k -> new ArrayList<>()).add(s.id())));
        var cohorts = new ArrayList<List<ResourceLocation>>();
        for (var members : peers.values()) {
            // Count choice ancestors even through skills outside this choice group.
            // Replacement descendants occupy a later cohort than the alternatives they extend.
            var depth = new HashMap<ResourceLocation, Integer>();
            var levels = new TreeMap<Integer, List<ResourceLocation>>();
            for (var skill : ordered) {
                int previous = dependencies(skill).stream().mapToInt(id -> depth.get(id)).max().orElse(0);
                depth.put(skill.id(), previous + (members.contains(skill.id()) ? 1 : 0));
                if (members.contains(skill.id())) levels.computeIfAbsent(previous, k -> new ArrayList<>()).add(skill.id());
            }
            cohorts.addAll(levels.values());
        }
        var lower = new TreeMap<ResourceLocation, Integer>();
        var upper = new TreeMap<ResourceLocation, Integer>();
        for (var skill : ordered) {
            int minimum = tiers.indexOf(gated(skill, minimumTiers.getOrDefault(skill.id(), tiers.getFirst()), 1));
            if (minimum < 0 || !tiers.contains(candidates.get(skill.id()))) throw new IllegalArgumentException("Unpowered skill tier");
            for (int rank = 1; rank <= skill.rankPolicy().projectionRanks(); rank++) {
                for (var requirement : skill.prerequisiteRanks(rank).entrySet()) {
                    var parent = skills.get(requirement.getKey());
                    if (requirement.getValue() > parent.rankPolicy().projectionRanks())
                        throw new IllegalArgumentException("Unreachable prerequisite rank: " + skill.id());
                    minimum = Math.max(minimum, tiers.indexOf(gated(parent, tiers.getFirst(), requirement.getValue())) + 1);
                }
            }
            lower.put(skill.id(), minimum);
            upper.put(skill.id(), tiers.size() - 1);
        }
        // Reserve room for every descendant before applying soft utility candidates.
        // Otherwise an Apex parent would leave no legal tier for its dependent.
        settle(ordered, cohorts, upper, false);
        for (var skill : ordered) {
            var id = skill.id();
            if (upper.get(id) < lower.get(id)) throw new IllegalArgumentException("No tier space for skill dependencies: " + id);
            lower.put(id, Math.max(lower.get(id), Math.min(upper.get(id), tiers.indexOf(candidates.get(id)))));
        }
        settle(ordered, cohorts, lower, true);
        var result = new TreeMap<ResourceLocation, ResourceLocation>();
        lower.forEach((id, value) -> {
            if (value > upper.get(id)) throw new IllegalArgumentException("Conflicting skill tier constraints: " + id);
            result.put(id, tiers.get(value));
        });
        return Collections.unmodifiableMap(result);
    }

    private static Set<ResourceLocation> dependencies(SkillDefinition skill) {
        var result = new TreeSet<>(skill.prerequisites());
        skill.rankPolicy().rankGates().values().forEach(g -> result.addAll(g.prerequisites().keySet()));
        return result;
    }

    private static void settle(List<SkillDefinition> ordered, List<List<ResourceLocation>> cohorts,
            Map<ResourceLocation, Integer> values, boolean forward) {
        for (int pass = 0; pass <= ordered.size(); pass++) {
            boolean changed = false;
            for (var skill : forward ? ordered : ordered.reversed()) for (var parent : dependencies(skill)) {
                var id = forward ? skill.id() : parent;
                int value = forward ? Math.max(values.get(id), values.get(parent) + 1)
                        : Math.min(values.get(id), values.get(skill.id()) - 1);
                changed |= values.put(id, value) != value;
            }
            for (var cohort : cohorts) {
                int value = forward ? cohort.stream().mapToInt(values::get).max().orElseThrow()
                        : cohort.stream().mapToInt(values::get).min().orElseThrow();
                for (var id : cohort) changed |= values.put(id, value) != value;
            }
            if (!changed) return;
        }
        throw new IllegalArgumentException("Choice alignment conflicts with skill dependencies");
    }

    private static void visit(SkillDefinition skill, Map<ResourceLocation, SkillDefinition> skills,
            Set<ResourceLocation> done, Set<ResourceLocation> path, List<SkillDefinition> ordered) {
        if (done.contains(skill.id())) return;
        if (!path.add(skill.id())) throw new IllegalArgumentException("Skill rank dependency cycle: " + path + " / " + skill.id());
        var dependencies = new TreeSet<>(skill.prerequisites());
        skill.rankPolicy().rankGates().values().forEach(g -> dependencies.addAll(g.prerequisites().keySet()));
        for (var id : dependencies) {
            var parent = skills.get(id);
            if (parent == null) throw new IllegalArgumentException("Missing skill prerequisite: " + id);
            visit(parent, skills, done, path, ordered);
        }
        path.remove(skill.id()); done.add(skill.id()); ordered.add(skill);
    }

    public static ResourceLocation gated(SkillDefinition skill, ResourceLocation base, int rank) {
        var result = base;
        for (var entry : skill.rankPolicy().rankGates().entrySet())
            if (entry.getKey() <= rank && entry.getValue().requiredTierId() != null
                    && order(entry.getValue().requiredTierId()) > order(result)) result = entry.getValue().requiredTierId();
        return result;
    }
    private static int order(ResourceLocation tier) { return AscendanceTierRegistry.get(tier).orElseThrow().order(); }
}

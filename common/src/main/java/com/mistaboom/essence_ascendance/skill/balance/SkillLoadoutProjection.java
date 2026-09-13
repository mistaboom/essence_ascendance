package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.skill.SkillActivationPolicy;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationContext;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationResult;
import com.mistaboom.essence_ascendance.skill.SkillStateEvaluator;
import com.mistaboom.essence_ascendance.skill.requirement.BonusInvestmentRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.DiscoveryRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.PermanentMilestoneRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToDoubleBiFunction;

import static com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics.Delivery;
import static com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics.Equipment;

/**
 * Deterministic, bounded generation-time projections. Catalog activation rules
 * remain owned by SkillStateEvaluator; this class enumerates its legal inputs.
 * Independent dependency/choice components avoid the exponential full-catalog
 * Cartesian product. Each returned scenario is one actual compatible selection.
 */
public final class SkillLoadoutProjection {
    private static final Comparator<ResourceLocation> IDS = Comparator.comparing(ResourceLocation::toString);
    private static final int MAX_COMPONENT_STATES = 4096;
    private static final int MAX_REFINEMENT_PASSES = 4;

    private SkillLoadoutProjection() { }

    /** A single usable attack family is evaluated once, never once per supported delivery. */
    public record EquipmentContext(String id, Delivery delivery, Set<Equipment> equipment, int nearbyTargets) {
        public EquipmentContext {
            equipment = Set.copyOf(equipment);
            if (id == null || id.isBlank() || nearbyTargets < 1 || nearbyTargets > 64)
                throw new IllegalArgumentException("Invalid projection equipment context");
        }
        public boolean permits(SkillBalanceSemantics.Descriptor descriptor) {
            return (descriptor.deliveries().isEmpty() || (delivery != null && descriptor.deliveries().contains(delivery)))
                    && equipment.containsAll(descriptor.equipment());
        }
    }

    public record Scenario(String id, String equipmentContext, String objective,
                           Map<ResourceLocation, ResourceLocation> selections,
                           Map<ResourceLocation, Integer> activeRanks,
                           Map<ResourceLocation, Integer> contributingRanks,
                           Map<CapabilityAxis, Double> axisPressure,
                           Map<CapabilityAxis, Double> capabilityPressure,
                           Map<ResourceLocation, Map<CapabilityAxis, Double>> categoryPressure,
                           double confidence) {
        public Scenario {
            selections = sorted(selections); activeRanks = sorted(activeRanks); contributingRanks = sorted(contributingRanks);
            axisPressure = axes(axisPressure); capabilityPressure = axes(capabilityPressure);
            Map<ResourceLocation, Map<CapabilityAxis, Double>> categories = new TreeMap<>(IDS);
            categoryPressure.forEach((categoryId, values) -> categories.put(categoryId, axes(values)));
            categoryPressure = Collections.unmodifiableMap(categories);
        }
    }

    /** Envelopes are maxima across different builds, not one simultaneously achievable loadout. */
    public record Projection(List<Scenario> scenarios, Map<CapabilityAxis, Double> axisEnvelope,
                             Map<CapabilityAxis, Double> conservativeUpperBounds,
                             int evaluatedComponentStates, List<String> diagnostics) {
        public Projection {
            scenarios = List.copyOf(scenarios); axisEnvelope = axes(axisEnvelope);
            conservativeUpperBounds = axes(conservativeUpperBounds); diagnostics = List.copyOf(diagnostics);
        }
    }

    public static List<EquipmentContext> representativeEquipment() {
        return List.of(
                new EquipmentContext("melee_shield", Delivery.MELEE,
                        Set.of(Equipment.MELEE_WEAPON, Equipment.SHIELD, Equipment.REPAIRABLE_GEAR), 3),
                new EquipmentContext("ranged", Delivery.RANGED,
                        Set.of(Equipment.RANGED_WEAPON, Equipment.REPAIRABLE_GEAR), 3),
                new EquipmentContext("caster", Delivery.CASTER,
                        Set.of(Equipment.CASTER_FOCUS, Equipment.REPAIRABLE_GEAR), 3),
                new EquipmentContext("gathering_tool", null,
                        Set.of(Equipment.ASCENDANCE_TOOL, Equipment.PICKAXE, Equipment.REPAIRABLE_GEAR), 1),
                new EquipmentContext("fishing", null, Set.of(Equipment.FISHING_ROD, Equipment.REPAIRABLE_GEAR), 1),
                new EquipmentContext("unequipped", null, Set.of(), 1));
    }

    /**
     * Potential after the tier's declared milestones/investment gates
     * are met. It does not claim these gates are met by every player at that tier.
     * A rankScale greater than one models future repeat ranks without enabling them.
     */
    public static Projection project(Collection<SkillDefinition> definitions, ResourceLocation tierId,
                                     Map<ResourceLocation, Integer> targetRanks,
                                     ToDoubleBiFunction<ResourceLocation, Integer> rankScale,
                                     Map<CapabilityAxis, Double> budgets, boolean includePlanned) {
        return project(definitions, tierId, targetRanks, rankScale, budgets, includePlanned, representativeEquipment());
    }

    public static Projection project(Collection<SkillDefinition> definitions, ResourceLocation tierId,
                                     Map<ResourceLocation, Integer> targetRanks,
                                     ToDoubleBiFunction<ResourceLocation, Integer> rankScale,
                                     Map<CapabilityAxis, Double> budgets, boolean includePlanned,
                                     List<EquipmentContext> equipmentContexts) {
        List<SkillDefinition> catalog = definitions.stream().sorted(Comparator.comparing(SkillDefinition::id, IDS)).toList();
        SkillBalanceSemantics.validate(catalog);
        Map<ResourceLocation, SkillDefinition> byId = new TreeMap<>(IDS);
        for (SkillDefinition definition : catalog) {
            if (byId.putIfAbsent(definition.id(), definition) != null) throw new IllegalArgumentException("Duplicate projection skill");
        }
        for (double budget : budgets.values()) {
            if (!Double.isFinite(budget) || budget <= 0) throw new IllegalArgumentException("Projection axis budgets must be positive");
        }
        Map<ResourceLocation, Integer> ranks = eligibleRanks(byId, tierId, targetRanks, includePlanned);
        List<List<SkillDefinition>> components = components(catalog, ranks);
        List<List<Candidate>> candidates = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        int evaluated = 0;
        for (List<SkillDefinition> component : components) {
            List<Candidate> choices = enumerate(component, tierId, ranks);
            evaluated += choices.size();
            candidates.add(choices);
        }
        long planned = catalog.stream().filter(definition -> !SkillBalanceSemantics.require(definition.id()).implemented()).count();
        diagnostics.add("Semantics registered=" + catalog.size() + "; implemented=" + (catalog.size() - planned)
                + "; planned=" + planned + "; includedPlanned=" + includePlanned + ". Planned mechanics are projections, not enabled effects.");
        diagnostics.add("Projection assumes declared permanent gates and live investment requirements are met; it does not bypass runtime gates.");
        diagnostics.add("Representative uptime, reliability, cost and risk are normalized policy estimates at confidence 0.45; not measured combat data.");
        diagnostics.add("Choice/dependency components=" + components.size() + "; evaluated component states=" + evaluated
                + "; each component is exhaustively enumerated up to " + MAX_COMPONENT_STATES + ", with overflow rejected.");
        diagnostics.add("Axis envelopes and conservative upper bounds may come from different builds; only each named scenario is jointly reachable.");

        List<Scenario> scenarios = new ArrayList<>();
        Map<CapabilityAxis, Double> envelope = new EnumMap<>(CapabilityAxis.class);
        Map<CapabilityAxis, Double> upperBounds = new EnumMap<>(CapabilityAxis.class);
        Set<CapabilityAxis> objectives = EnumSet.noneOf(CapabilityAxis.class);
        ranks.keySet().forEach(id -> objectives.addAll(SkillBalanceSemantics.require(id).weights().keySet()));
        for (EquipmentContext equipment : equipmentContexts.stream().sorted(Comparator.comparing(EquipmentContext::id)).toList()) {
            List<List<Scored>> scored = candidates.stream().map(component -> component.stream()
                    .map(candidate -> score(candidate, byId, ranks, rankScale, equipment)).toList()).toList();
            mergeMaximum(upperBounds, upperBound(scored));
            List<CapabilityAxis> axisObjectives = new ArrayList<>();
            axisObjectives.add(null); // Broad generalist plus individual-axis specializations.
            axisObjectives.addAll(objectives);
            for (CapabilityAxis objective : axisObjectives) {
                List<Scored> selected = choose(scored, objective, budgets);
                Scenario scenario = scenario(equipment, objective, selected, ranks);
                scenarios.add(scenario);
                mergeMaximum(envelope, scenario.axisPressure());
            }
        }
        diagnostics.add("Scenario optimization uses at most " + MAX_REFINEMENT_PASSES
                + " deterministic component refinement passes; conservative upper bounds protect against unsampled composition extremes.");
        return new Projection(scenarios, envelope, upperBounds, evaluated, diagnostics);
    }

    /** Evaluate a committed real-player context with its actual gates and selected toggles. */
    public static Scenario evaluate(Collection<SkillDefinition> definitions, SkillEvaluationContext context,
                                    ToDoubleBiFunction<ResourceLocation, Integer> rankScale,
                                    EquipmentContext equipment) {
        Map<ResourceLocation, SkillDefinition> byId = new TreeMap<>(IDS);
        definitions.forEach(definition -> byId.put(definition.id(), definition));
        Set<ResourceLocation> active = new TreeSet<>(IDS);
        SkillStateEvaluator.evaluateAll(definitions, context).forEach((id, evaluation) -> {
            if (evaluation.effective()) active.add(id);
        });
        Candidate candidate = new Candidate(context.authoritativeLoadoutSelections(), active);
        Scored scored = score(candidate, byId, context.authoritativeRanks(), rankScale, equipment);
        return scenario(equipment, null, List.of(scored), context.authoritativeRanks());
    }

    private static Map<ResourceLocation, Integer> eligibleRanks(Map<ResourceLocation, SkillDefinition> byId,
                                                               ResourceLocation tierId,
                                                               Map<ResourceLocation, Integer> targetRanks,
                                                               boolean includePlanned) {
        int tierOrder = tierOrder(tierId);
        Map<ResourceLocation, Integer> ranks = new TreeMap<>(IDS);
        for (Map.Entry<ResourceLocation, Integer> entry : targetRanks.entrySet()) {
            SkillDefinition definition = byId.get(entry.getKey());
            if (definition == null) throw new IllegalArgumentException("Unknown projected skill " + entry.getKey());
            int rank = entry.getValue();
            if (rank < 0 || rank > 64) throw new IllegalArgumentException("Projection rank must be in [0,64]");
            if (!includePlanned && !SkillBalanceSemantics.require(definition.id()).implemented()) continue;
            while (rank > 0 && tierOrder(definition.requiredTierId(rank)) > tierOrder) rank--;
            if (rank > 0) ranks.put(definition.id(), rank);
        }
        boolean changed;
        do {
            changed = false;
            for (ResourceLocation id : List.copyOf(ranks.keySet())) {
                int rank = ranks.get(id);
                while (rank > 0 && !prerequisiteRanksSatisfied(byId.get(id), rank, ranks)) rank--;
                if (rank != ranks.get(id)) {
                    if (rank == 0) ranks.remove(id); else ranks.put(id, rank);
                    changed = true;
                }
            }
        } while (changed);
        return ranks;
    }

    private static boolean prerequisiteRanksSatisfied(SkillDefinition definition, int rank, Map<ResourceLocation, Integer> ranks) {
        for (var entry : definition.prerequisiteRanks(rank).entrySet()) {
            if (ranks.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    private static int tierOrder(ResourceLocation id) {
        return AscendanceTierRegistry.get(id).orElseThrow(() -> new IllegalArgumentException("Unknown projection tier " + id)).order();
    }

    private static List<List<SkillDefinition>> components(List<SkillDefinition> catalog, Map<ResourceLocation, Integer> ranks) {
        Map<ResourceLocation, Set<ResourceLocation>> edges = new TreeMap<>(IDS);
        Map<ResourceLocation, SkillDefinition> byId = new TreeMap<>(IDS);
        Map<ResourceLocation, List<ResourceLocation>> groups = new TreeMap<>(IDS);
        for (SkillDefinition definition : catalog) {
            if (!ranks.containsKey(definition.id())) continue;
            byId.put(definition.id(), definition);
            edges.put(definition.id(), new TreeSet<>(IDS));
            if (definition.choiceGroup() != null) groups.computeIfAbsent(definition.choiceGroup(), ignored -> new ArrayList<>()).add(definition.id());
        }
        for (SkillDefinition definition : byId.values()) {
            for (ResourceLocation parent : definition.prerequisiteRanks(ranks.get(definition.id())).keySet()) link(edges, definition.id(), parent);
            if (definition.replacementTarget() != null) link(edges, definition.id(), definition.replacementTarget());
        }
        for (List<ResourceLocation> group : groups.values()) {
            for (int i = 1; i < group.size(); i++) link(edges, group.getFirst(), group.get(i));
        }
        List<List<SkillDefinition>> result = new ArrayList<>();
        Set<ResourceLocation> visited = new LinkedHashSet<>();
        for (ResourceLocation root : edges.keySet()) {
            if (!visited.add(root)) continue;
            List<ResourceLocation> queue = new ArrayList<>(List.of(root));
            List<SkillDefinition> component = new ArrayList<>();
            for (int i = 0; i < queue.size(); i++) {
                ResourceLocation next = queue.get(i);
                component.add(byId.get(next));
                for (ResourceLocation neighbor : edges.get(next)) if (visited.add(neighbor)) queue.add(neighbor);
            }
            component.sort(Comparator.comparing(SkillDefinition::id, IDS));
            result.add(List.copyOf(component));
        }
        return result;
    }

    private static void link(Map<ResourceLocation, Set<ResourceLocation>> edges, ResourceLocation left, ResourceLocation right) {
        if (!edges.containsKey(right)) throw new IllegalArgumentException("Missing projected prerequisite " + right);
        edges.get(left).add(right); edges.get(right).add(left);
    }

    private static List<Candidate> enumerate(List<SkillDefinition> component, ResourceLocation tierId,
                                             Map<ResourceLocation, Integer> allRanks) {
        Map<ResourceLocation, List<ResourceLocation>> groups = new TreeMap<>(IDS);
        Map<ResourceLocation, Integer> ranks = new TreeMap<>(IDS);
        Map<ResourceLocation, ResourceLocation> toggles = new TreeMap<>(IDS);
        Set<ResourceLocation> milestones = new TreeSet<>(IDS), discoveries = new TreeSet<>(IDS);
        Map<ResourceLocation, Long> investment = new TreeMap<>(IDS);
        for (SkillDefinition definition : component) {
            int rank = allRanks.get(definition.id());
            ranks.put(definition.id(), rank);
            if (definition.activationPolicy() == SkillActivationPolicy.SELECTABLE && definition.choiceGroup() == null)
                throw new IllegalArgumentException("Selectable projected skill has no choice group: " + definition.id());
            if (definition.choiceGroup() != null)
                groups.computeIfAbsent(definition.choiceGroup(), ignored -> new ArrayList<>()).add(definition.id());
            if (definition.activationPolicy() == SkillActivationPolicy.TOGGLE) toggles.put(definition.id(), definition.id());
            for (int r = 1; r <= rank; r++) {
                for (SkillRequirement requirement : definition.requirements(r)) {
                    if (requirement instanceof PermanentMilestoneRequirement gate) milestones.add(gate.milestoneId());
                    else if (requirement instanceof DiscoveryRequirement gate) discoveries.add(gate.discoveryId());
                    else if (requirement instanceof BonusInvestmentRequirement gate)
                        investment.merge(gate.essenceId(), gate.minimumInvestment(), Math::max);
                    else throw new IllegalArgumentException("Projection cannot satisfy unknown requirement type " + requirement.getClass().getName());
                }
            }
        }
        List<Map<ResourceLocation, ResourceLocation>> selections = new ArrayList<>(List.of(toggles));
        for (var group : groups.entrySet()) {
            if ((long) selections.size() * (group.getValue().size() + 1) > MAX_COMPONENT_STATES)
                throw new IllegalStateException("Skill projection component exceeds " + MAX_COMPONENT_STATES
                        + " states near " + group.getKey() + "; split coupled groups or raise the explicit solver bound");
            List<Map<ResourceLocation, ResourceLocation>> expanded = new ArrayList<>();
            for (Map<ResourceLocation, ResourceLocation> existing : selections) {
                expanded.add(existing); // No selection supports replacement fallback semantics.
                for (ResourceLocation choice : group.getValue()) {
                    Map<ResourceLocation, ResourceLocation> next = new TreeMap<>(IDS);
                    next.putAll(existing); next.put(group.getKey(), choice); expanded.add(next);
                }
            }
            selections = expanded;
        }
        List<Candidate> result = new ArrayList<>();
        for (Map<ResourceLocation, ResourceLocation> selected : selections) {
            SkillEvaluationContext context = SkillEvaluationContext.committed(tierId, ranks, selected, milestones, discoveries, investment);
            Set<ResourceLocation> active = new TreeSet<>(IDS);
            Map<ResourceLocation, SkillEvaluationResult> evaluated = SkillStateEvaluator.evaluateAll(component, context);
            evaluated.forEach((id, state) -> { if (state.effective()) active.add(id); });
            result.add(new Candidate(sorted(selected), Collections.unmodifiableSet(active)));
        }
        return List.copyOf(result);
    }

    private static Scored score(Candidate candidate, Map<ResourceLocation, SkillDefinition> byId,
                                Map<ResourceLocation, Integer> ranks,
                                ToDoubleBiFunction<ResourceLocation, Integer> rankScale, EquipmentContext equipment) {
        Vector vector = new Vector();
        Map<ResourceLocation, Vector> categories = new TreeMap<>(IDS);
        Set<ResourceLocation> contributing = new TreeSet<>(IDS);
        for (ResourceLocation id : candidate.active()) {
            var descriptor = SkillBalanceSemantics.require(id);
            if (!equipment.permits(descriptor)) continue;
            double scaling = rankScale.applyAsDouble(id, ranks.get(id));
            if (!Double.isFinite(scaling) || scaling < 0 || scaling > 64)
                throw new IllegalArgumentException("Invalid projected rank contribution for " + id);
            contributing.add(id);
            Vector category = categories.computeIfAbsent(byId.get(id).essenceId(), ignored -> new Vector());
            for (var contribution : descriptor.contributions()) {
                int targets = Math.min(equipment.nearbyTargets(), contribution.targets());
                double availability = descriptor.expectedAvailability();
                // Binary access stays a binary/policy axis at every rank. Repeated
                // purchases cannot turn five flight ranks into five simultaneous flights.
                double amount = contribution.weight() * (contribution.form() == SkillBalanceSemantics.Form.CAPABILITY ? 1 : scaling)
                        * availability * (1 + .5 * (targets - 1));
                vector.add(contribution.axis(), contribution.form(), amount);
                category.add(contribution.axis(), contribution.form(), amount);
            }
        }
        return new Scored(candidate, contributing, vector, categories);
    }

    private static List<Scored> choose(List<List<Scored>> components, CapabilityAxis objective,
                                       Map<CapabilityAxis, Double> budgets) {
        List<Scored> chosen = new ArrayList<>();
        for (List<Scored> candidates : components) {
            Scored best = candidates.getFirst();
            double score = objective(best.vector(), objective, budgets);
            for (Scored candidate : candidates) {
                double value = objective(candidate.vector(), objective, budgets);
                if (value > score + 1.0e-12) { score = value; best = candidate; }
            }
            chosen.add(best);
        }
        for (int pass = 0; pass < MAX_REFINEMENT_PASSES; pass++) {
            boolean changed = false;
            for (int i = 0; i < components.size(); i++) {
                Vector others = new Vector();
                for (int j = 0; j < chosen.size(); j++) if (j != i) others.merge(chosen.get(j).vector());
                Scored best = chosen.get(i);
                double score = objective(others.plus(best.vector()), objective, budgets);
                for (Scored candidate : components.get(i)) {
                    double value = objective(others.plus(candidate.vector()), objective, budgets);
                    if (value > score + 1.0e-12) { score = value; best = candidate; }
                }
                if (best != chosen.get(i)) { chosen.set(i, best); changed = true; }
            }
            if (!changed) break;
        }
        return chosen;
    }

    private static double objective(Vector vector, CapabilityAxis axis, Map<CapabilityAxis, Double> budgets) {
        if (axis != null) return vector.pressure(axis) / budgets.getOrDefault(axis, 1.0);
        double result = 0;
        for (CapabilityAxis candidate : CapabilityAxis.values())
            result += Math.log1p(vector.pressure(candidate) / budgets.getOrDefault(candidate, 1.0));
        return result;
    }

    private static Scenario scenario(EquipmentContext equipment, CapabilityAxis objective,
                                      List<Scored> selected, Map<ResourceLocation, Integer> ranks) {
        Map<ResourceLocation, ResourceLocation> selections = new TreeMap<>(IDS);
        Map<ResourceLocation, Integer> active = new TreeMap<>(IDS), contributing = new TreeMap<>(IDS);
        Map<ResourceLocation, Vector> categories = new TreeMap<>(IDS);
        Vector vector = new Vector();
        for (Scored score : selected) {
            selections.putAll(score.candidate().selections());
            score.candidate().active().forEach(id -> active.put(id, ranks.get(id)));
            score.contributing().forEach(id -> contributing.put(id, ranks.get(id)));
            vector.merge(score.vector());
            score.categories().forEach((category, value) -> categories.computeIfAbsent(category, ignored -> new Vector()).merge(value));
        }
        Map<ResourceLocation, Map<CapabilityAxis, Double>> categoryPressure = new TreeMap<>(IDS);
        categories.forEach((category, value) -> categoryPressure.put(category, value.pressures()));
        String objectiveName = objective == null ? "broad_generalist" : objective.name().toLowerCase(java.util.Locale.ROOT);
        return new Scenario(equipment.id() + "/" + objectiveName, equipment.id(), objectiveName,
                selections, active, contributing, vector.pressures(), vector.capabilities, categoryPressure, .45);
    }

    private static Map<CapabilityAxis, Double> upperBound(List<List<Scored>> components) {
        Vector upper = new Vector();
        for (List<Scored> candidates : components) {
            Vector component = new Vector();
            for (Scored scored : candidates) {
                mergeMaximum(component.flat, scored.vector().flat);
                mergeMaximum(component.logMultiplier, scored.vector().logMultiplier);
                mergeMaximum(component.capabilities, scored.vector().capabilities);
            }
            upper.merge(component);
        }
        return upper.pressures();
    }

    private record Candidate(Map<ResourceLocation, ResourceLocation> selections, Set<ResourceLocation> active) { }
    private record Scored(Candidate candidate, Set<ResourceLocation> contributing, Vector vector,
                          Map<ResourceLocation, Vector> categories) { }

    private static final class Vector {
        private final Map<CapabilityAxis, Double> flat = new EnumMap<>(CapabilityAxis.class);
        private final Map<CapabilityAxis, Double> logMultiplier = new EnumMap<>(CapabilityAxis.class);
        private final Map<CapabilityAxis, Double> capabilities = new EnumMap<>(CapabilityAxis.class);
        void add(CapabilityAxis axis, SkillBalanceSemantics.Form form, double value) {
            switch (form) {
                case FLAT -> flat.merge(axis, value, Double::sum);
                case MULTIPLIER -> logMultiplier.merge(axis, Math.log1p(value), Double::sum);
                case CAPABILITY -> capabilities.merge(axis, value, Math::max);
            }
        }
        void merge(Vector other) {
            other.flat.forEach((axis, value) -> flat.merge(axis, value, Double::sum));
            other.logMultiplier.forEach((axis, value) -> logMultiplier.merge(axis, value, Double::sum));
            mergeMaximum(capabilities, other.capabilities);
        }
        Vector plus(Vector other) { Vector result = new Vector(); result.merge(this); result.merge(other); return result; }
        double pressure(CapabilityAxis axis) {
            double value = (1 + flat.getOrDefault(axis, 0.0)) * Math.exp(logMultiplier.getOrDefault(axis, 0.0)) - 1
                    + capabilities.getOrDefault(axis, 0.0);
            if (!Double.isFinite(value)) throw new IllegalStateException("Skill composition exceeds finite projection range");
            return Math.max(0, value);
        }
        Map<CapabilityAxis, Double> pressures() {
            Map<CapabilityAxis, Double> values = new EnumMap<>(CapabilityAxis.class);
            for (CapabilityAxis axis : CapabilityAxis.values()) if (pressure(axis) > 0) values.put(axis, pressure(axis));
            return values;
        }
    }

    private static void mergeMaximum(Map<CapabilityAxis, Double> target, Map<CapabilityAxis, Double> values) {
        values.forEach((axis, value) -> target.merge(axis, value, Math::max));
    }
    private static <V> Map<ResourceLocation, V> sorted(Map<ResourceLocation, V> values) {
        Map<ResourceLocation, V> result = new TreeMap<>(IDS); result.putAll(values); return Collections.unmodifiableMap(result);
    }
    private static Map<CapabilityAxis, Double> axes(Map<CapabilityAxis, Double> values) {
        Map<CapabilityAxis, Double> result = new EnumMap<>(CapabilityAxis.class); result.putAll(values); return Collections.unmodifiableMap(result);
    }
}

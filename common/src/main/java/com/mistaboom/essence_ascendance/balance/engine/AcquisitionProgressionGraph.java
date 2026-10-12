package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mistaboom.essence_ascendance.balance.quest.QuestEvidence;
import java.util.*;

/** Generation-scoped shared reachability projection. Stages describe source availability, never strength. */
public final class AcquisitionProgressionGraph {
    private AcquisitionProgressionGraph() { }
    public record Placement(ProgressionBand stage, Map<String, String> exclusiveClaims,
                            double quantity, boolean finite, boolean renewable, String source) {
        public Placement { exclusiveClaims = Collections.unmodifiableMap(new TreeMap<>(exclusiveClaims)); }
        public static Placement ordinary(ProgressionBand stage) { return ordinary(stage, false); }
        public static Placement ordinary(ProgressionBand stage, boolean renewable) { return new Placement(stage, Map.of(), 0, false, renewable, "ordinary_acquisition"); }
    }
    public record Result(Map<String, Placement> items, Map<String, Placement> quests, List<String> unresolved, long ruleEvaluations) {
        public Result { items = Collections.unmodifiableMap(new TreeMap<>(items)); quests = Collections.unmodifiableMap(new TreeMap<>(quests)); unresolved = List.copyOf(unresolved); }
    }
    private record Rule(String id, List<List<String>> groups, List<Double> counts, List<Boolean> consumed,
                        List<String> threshold, int needed, String target, double quantity,
                        double probability, boolean finite, String exclusiveGroup, String selection, boolean supported) { }
    public record Constraints(Set<String> excludedItems, Set<String> excludedRecipes,
                              Map<String, ProgressionBand> stages, Set<String> nonrenewableItems) {
        public static final Constraints NONE = new Constraints(Set.of(), Set.of(), Map.of(), Set.of());
        public Constraints { excludedItems = Set.copyOf(excludedItems); excludedRecipes = Set.copyOf(excludedRecipes);
            stages = Map.copyOf(stages); nonrenewableItems = Set.copyOf(nonrenewableItems); }
        Placement apply(String item, Placement p) {
            return new Placement(stages.getOrDefault(item, p.stage()), p.exclusiveClaims(), p.quantity(), p.finite(),
                    p.renewable() && !nonrenewableItems.contains(item), p.source());
        }
    }
    public static Result solve(Map<String, Placement> ordinary, ProductionGraph production, QuestEvidence quests) {
        return solve(ordinary, production, quests, Constraints.NONE);
    }
    public static Result solve(Map<String, Placement> ordinary, ProductionGraph production, QuestEvidence quests,
                               Constraints constraints) {
        Map<String, Placement> nodes = new HashMap<>(); ordinary.forEach((id, p) -> {
            if (!constraints.excludedItems().contains(id)) nodes.put("item/" + id, constraints.apply(id, p));
        });
        List<Rule> rules = new ArrayList<>();
        // One shared production graph: reward-produced inputs can unlock normal crafting/machines.
        for (var p : production.processes()) if (p.acquisitionComplete() && !p.inputs().isEmpty()
                && !constraints.excludedRecipes().contains(p.id())) {
            List<List<String>> groups = p.inputs().stream().map(i -> i.alternatives().stream().map(id -> "item/" + id).toList()).toList();
            List<Double> counts = p.inputs().stream().map(ProductionGraph.Input::count).toList();
            for (var o : p.outputs()) rules.add(new Rule(p.id(), groups, counts, p.inputs().stream().map(ProductionGraph.Input::consumed).toList(), List.of(), 0, "item/" + o.itemId(),
                    o.count(), o.probability(), false, "", "", true));
        }
        for (var q : quests.quests()) {
            List<List<String>> groups = q.tasks().stream().filter(t -> !t.items().isEmpty())
                    .map(t -> t.items().stream().map(id -> "item/" + id).toList()).toList();
            List<Double> counts = q.tasks().stream().filter(t -> !t.items().isEmpty()).map(t -> (double) t.count()).toList();
            boolean supported = q.unresolved().isEmpty() && q.tasks().stream().allMatch(QuestEvidence.Task::supported);
            rules.add(new Rule("quest/" + q.id(), groups, counts, q.tasks().stream().filter(t -> !t.items().isEmpty()).map(QuestEvidence.Task::consumed).toList(),
                    q.enforced() ? q.prerequisites().stream().map(id -> "quest/" + id).toList() : List.of(),
                    q.enforced() ? q.required() : 0, "quest/" + q.id(), 1, 1, false, "", "", supported));
            for (var r : q.rewards()) if (r.supported()
                    && !constraints.excludedRecipes().contains("essence_ascendance:quest/" + q.id() + "/" + r.id()))
                rules.add(new Rule("reward/" + q.id() + "/" + r.id(),
                    List.of(List.of("quest/" + q.id())), List.of(1d), List.of(false), List.of(), 0, "item/" + r.item(),
                    r.count(), 1, !q.repeatable(), q.repeatable() ? "" : r.group(), r.id(), supported));
        }
        Map<String, List<Integer>> subscribers = new HashMap<>();
        for (int i = 0; i < rules.size(); i++) {
            int index = i; Rule r = rules.get(i);
            r.groups.stream().flatMap(List::stream).distinct().forEach(id -> subscribers.computeIfAbsent(id, k -> new ArrayList<>()).add(index));
            r.threshold.stream().distinct().forEach(id -> subscribers.computeIfAbsent(id, k -> new ArrayList<>()).add(index));
        }
        Deque<Integer> queue = new ArrayDeque<>(); BitSet queued = new BitSet();
        for (int i = 0; i < rules.size(); i++) { queue.add(i); queued.set(i); }
        long evaluations = 0; Set<String> unknown = new TreeSet<>();
        while (!queue.isEmpty()) {
            int index = queue.removeFirst(); queued.clear(index); Rule r = rules.get(index); evaluations++;
            if (!r.supported) continue;
            Placement candidate = evaluate(r, nodes);
            if (candidate == null) continue;
            if (r.target.startsWith("item/")) {
                String item = r.target.substring(5);
                if (constraints.excludedItems().contains(item)) continue;
                candidate = constraints.apply(item, candidate);
            }
            Placement old = nodes.get(r.target);
            // Preserve an independently reachable item. Equal-stage renewable/less constrained
            // proofs precede finite choices. Never infer availability from unseeded cycles.
            if (old != null && compare(candidate, old) >= 0) continue;
            nodes.put(r.target, candidate);
            for (int next : subscribers.getOrDefault(r.target, List.of())) if (!queued.get(next)) { queued.set(next); queue.add(next); }
        }
        Map<String, Placement> items = new TreeMap<>(), completed = new TreeMap<>();
        nodes.forEach((id, p) -> { if (id.startsWith("item/")) items.put(id.substring(5), p); else completed.put(id.substring(6), p); });
        for (var q : quests.quests()) if (!completed.containsKey(q.id())) unknown.add(q.id() + ": unresolved task/condition, insufficient finite supply, exclusive claims or unseeded prerequisite cycle");
        return new Result(items, completed, List.copyOf(unknown), evaluations);
    }

    /** Always project from captured definitions, never metadata degraded by an earlier pass. */
    static ProductionGraph projectProduction(ProductionGraph original, Result solved, Constraints constraints) {
        List<ProductionGraph.Process> processes = new ArrayList<>();
        for (var process : original.processes()) {
            if (!process.metadata().containsKey("quest_id")) { processes.add(process); continue; }
            Map<String, String> metadata = new TreeMap<>(process.metadata());
            var placement = solved.quests().get(metadata.get("quest_id"));
            boolean supported = placement != null && process.confidence() >= .85
                    && !constraints.excludedRecipes().contains(process.id())
                    && process.outputs().stream().noneMatch(o -> constraints.excludedItems().contains(o.itemId()));
            metadata.put("acquisition_complete", Boolean.toString(supported));
            if (!supported) metadata.put("conservation_complete", "false");
            metadata.put("repeat_supply_proven", Boolean.toString(supported && placement.renewable()));
            if ("renewable".equals(metadata.get("renewability")) && (placement == null || !placement.renewable()))
                metadata.put("renewability", placement != null && placement.finite() ? "finite" : "unknown");
            metadata.put("availability_stage", supported ? placement.stage().name() : "unresolved");
            metadata.put("acquisition_semantics", "Marginal reward opportunity, not guaranteed stock or unrestricted material conversion");
            processes.add(new ProductionGraph.Process(process.id(), process.family(), process.inputs(), process.outputs(),
                    process.processingTicks(), process.externalCost(), process.provider(), process.confidence(), metadata));
        }
        return new ProductionGraph(processes, original.warnings());
    }
    private static Placement evaluate(Rule r, Map<String, Placement> nodes) {
        List<Placement> requirements = new ArrayList<>();
        Map<String, Double> submitted = new HashMap<>(), collected = new HashMap<>();
        for (int i = 0; i < r.groups.size(); i++) {
            double count = r.counts.get(i);
            Placement selected = r.groups.get(i).stream().map(nodes::get).filter(Objects::nonNull)
                    .filter(p -> !p.finite || p.quantity >= count).min(AcquisitionProgressionGraph::compare).orElse(null);
            if (selected == null) return null; requirements.add(selected);
            Placement chosen = selected;
            String id = r.groups.get(i).stream().filter(key -> nodes.get(key) == chosen).findFirst().orElseThrow();
            if (r.consumed.get(i)) submitted.merge(id, count, Double::sum); else collected.merge(id, count, Math::max);
        }
        for (String id : submitted.keySet()) {
            Placement p = nodes.get(id);
            if (p.finite && p.quantity < Math.max(submitted.get(id), collected.getOrDefault(id, 0d))) return null;
        }
        List<Placement> prerequisites = r.threshold.stream().map(nodes::get).filter(Objects::nonNull)
                .sorted(AcquisitionProgressionGraph::compare).toList();
        if (prerequisites.size() < r.needed) return null;
        requirements.addAll(prerequisites.subList(0, r.needed));
        if (!r.id.startsWith("quest/") && !r.id.startsWith("reward/")
                && requirements.stream().allMatch(p -> p.source.equals("ordinary_acquisition"))) return null;
        int stage = 0; Map<String, String> claims = new TreeMap<>(); boolean finiteInput = false, renewable = !r.finite;
        for (int i = 0; i < requirements.size(); i++) {
            var p = requirements.get(i);
            stage = Math.max(stage, p.stage.ordinal());
            if (i < r.groups.size() && (r.consumed.get(i) || r.id.startsWith("reward/"))) {
                finiteInput |= p.finite; renewable &= p.renewable;
            }
            for (var c : p.exclusiveClaims.entrySet()) {
                String old = claims.putIfAbsent(c.getKey(), c.getValue()); if (old != null && !old.equals(c.getValue())) return null;
            }
        }
        if (!r.exclusiveGroup.isEmpty()) {
            String old = claims.putIfAbsent(r.exclusiveGroup, r.selection);
            if (old != null && !old.equals(r.selection)) return null;
        }
        // Positive-chance production is repeatable access only when consumed
        // materials are independently renewable. A finite attempt cannot grant
        // guaranteed random stock; deterministic coproduct rules remain valid.
        if (r.probability < 1 && (finiteInput || !renewable || r.consumed.stream().noneMatch(Boolean::booleanValue))) return null;
        return new Placement(ProgressionBand.at(stage), claims, r.quantity, r.finite || finiteInput, renewable && !finiteInput, r.id);
    }
    private static int compare(Placement a, Placement b) {
        int stage = Integer.compare(a.stage.ordinal(), b.stage.ordinal()); if (stage != 0) return stage;
        int finite = Boolean.compare(a.finite, b.finite); if (finite != 0) return finite;
        int renewable = -Boolean.compare(a.renewable, b.renewable); if (renewable != 0) return renewable;
        int claims = Integer.compare(a.exclusiveClaims.size(), b.exclusiveClaims.size()); if (claims != 0) return claims;
        if (a.finite) { int quantity = -Double.compare(a.quantity, b.quantity); if (quantity != 0) return quantity; }
        return a.source.compareTo(b.source);
    }
}

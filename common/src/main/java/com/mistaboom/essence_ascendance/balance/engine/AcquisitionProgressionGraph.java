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
    public record Result(Map<String, Placement> items, Map<String, Placement> quests, List<String> unresolved, long ruleEvaluations,
                         Map<String, List<String>> selectedInputs) {
        public Result { items = Collections.unmodifiableMap(new TreeMap<>(items)); quests = Collections.unmodifiableMap(new TreeMap<>(quests));
            unresolved = List.copyOf(unresolved); selectedInputs = Collections.unmodifiableMap(new TreeMap<>(selectedInputs)); }
    }
    private record Rule(String id, List<List<String>> groups, List<Double> counts, List<Boolean> consumed,
                        List<String> threshold, int needed, String target, double quantity,
                        double probability, boolean finite, String exclusiveGroup, String selection, boolean supported) { }
    // Keep the actual selected proof, not only its final item count. Two descendants
    // can each be individually reachable while competing for the same reward stock.
    private record Requirement(Witness witness, double count, boolean consumed) { }
    private record Witness(String target, Placement placement, Rule rule, List<Requirement> requirements,
                           Map<String, Double> outputs, boolean finiteAncestry) { }
    private record Candidate(Placement placement, Witness witness) { }
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
        Map<String, Witness> witnesses = new HashMap<>();
        nodes.forEach((id, p) -> witnesses.put(id, new Witness(id, p, null, List.of(), Map.of(), p.finite())));
        Map<String, Map<String, Double>> processOutputs = new HashMap<>();
        for (var process : production.processes()) {
            Map<String, Double> outputs = new TreeMap<>();
            process.outputs().stream().filter(o -> o.probability() == 1)
                    .forEach(o -> outputs.merge("item/" + o.itemId(), o.count(), Double::sum));
            processOutputs.put(process.id(), Map.copyOf(outputs));
        }
        List<Rule> rules = new ArrayList<>();
        // One shared production graph: reward-produced inputs can unlock normal crafting/machines.
        for (var p : production.processes()) if (p.acquisitionComplete() && !p.metadata().containsKey("quest_id") && !p.inputs().isEmpty()
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
                    r.count(), r.probability(), !q.repeatable(), q.repeatable() ? "" : r.group(), r.id(), supported));
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
        Map<String, String> failedJoint = new TreeMap<>();
        while (!queue.isEmpty()) {
            int index = queue.removeFirst(); queued.clear(index); Rule r = rules.get(index); evaluations++;
            if (!r.supported) continue;
            boolean finiteRisk = r.finite || java.util.stream.Stream.concat(r.groups.stream().flatMap(List::stream), r.threshold.stream())
                    .map(witnesses::get).filter(Objects::nonNull).anyMatch(Witness::finiteAncestry);
            Choices choices = new Choices(); ProofBudget budget = new ProofBudget();
            Candidate evaluated = null;
            int attempts = 0;
            do {
                choices.begin(); attempts++;
                evaluated = evaluate(r, nodes, witnesses, processOutputs, choices);
                if (evaluated != null && (!evaluated.witness().finiteAncestry()
                        || new JointStockProof(budget).prove(evaluated.witness()))) break;
                evaluated = null;
            } while (finiteRisk && !budget.exhausted && attempts < 64 && choices.next());
            if (evaluated == null && finiteRisk) failedJoint.put(r.target,
                    r.finite && r.probability < 1 ? "finite random reward is an opportunity; guaranteed stock unknown"
                    : budget.exhausted || attempts == 64 ? "joint finite acquisition search budget exhausted; access unknown"
                            : "no supported joint finite acquisition witness among current input proofs");
            if (evaluated == null) continue;
            Placement candidate = evaluated.placement();
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
            Witness proof = evaluated.witness();
            witnesses.put(r.target, new Witness(proof.target(), candidate, proof.rule(), proof.requirements(), proof.outputs(), proof.finiteAncestry()));
            for (int next : subscribers.getOrDefault(r.target, List.of())) if (!queued.get(next)) { queued.set(next); queue.add(next); }
        }
        Map<String, Placement> items = new TreeMap<>(), completed = new TreeMap<>();
        nodes.forEach((id, p) -> { if (id.startsWith("item/")) items.put(id.substring(5), p); else completed.put(id.substring(6), p); });
        failedJoint.forEach((target, reason) -> { if (!nodes.containsKey(target)) unknown.add(target + ": " + reason); });
        for (var q : quests.quests()) if (!completed.containsKey(q.id())) unknown.add(q.id() + ": unresolved task/condition, insufficient finite supply, exclusive claims or unseeded prerequisite cycle");
        Map<String, List<String>> selectedInputs = new TreeMap<>();
        witnesses.forEach((id, witness) -> { if (id.startsWith("item/") && witness.rule() != null)
            selectedInputs.put(id.substring(5), witness.requirements().stream().limit(witness.rule().groups.size())
                    .map(input -> input.witness().target().startsWith("item/") ? input.witness().target().substring(5) : input.witness().target()).toList()); });
        return new Result(items, completed, List.copyOf(unknown), evaluations, selectedInputs);
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
    private static Candidate evaluate(Rule r, Map<String, Placement> nodes, Map<String, Witness> witnesses,
                                      Map<String, Map<String, Double>> processOutputs, Choices choices) {
        List<Placement> requirements = new ArrayList<>();
        List<Requirement> proofRequirements = new ArrayList<>();
        for (int i = 0; i < r.groups.size(); i++) {
            double count = r.counts.get(i);
            List<String> available = r.groups.get(i).stream().filter(nodes::containsKey)
                    .sorted((a, b) -> { int order = compare(nodes.get(a), nodes.get(b)); return order != 0 ? order : a.compareTo(b); }).toList();
            int selection = choices.pick(available.size());
            if (available.isEmpty()) return null;
            String id = available.get(selection);
            Placement selected = nodes.get(id); requirements.add(selected);
            proofRequirements.add(new Requirement(witnesses.get(id), count, r.consumed.get(i)));
        }
        List<Witness> prerequisites = r.threshold.stream().map(witnesses::get).filter(Objects::nonNull)
                .sorted((a, b) -> { int order = compare(a.placement(), b.placement()); return order != 0 ? order : a.target().compareTo(b.target()); }).toList();
        if (prerequisites.size() < r.needed) return null;
        // Select increasing indices so threshold alternatives share the same
        // lazy assignment budget without enumerating permutations or subsets.
        int start = 0;
        for (int i = 0; i < r.needed; i++) {
            int selected = start + choices.pick(prerequisites.size() - (r.needed - i) - start + 1);
            var prerequisite = prerequisites.get(selected); start = selected + 1;
            requirements.add(prerequisite.placement());
            proofRequirements.add(new Requirement(prerequisite, 1, false));
        }
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
        boolean repeatableReward = r.id.startsWith("reward/") && !r.finite;
        if (r.probability < 1 && (r.finite || finiteInput || !renewable
                || !repeatableReward && r.consumed.stream().noneMatch(Boolean::booleanValue))) return null;
        Placement placement = new Placement(ProgressionBand.at(stage), claims, r.quantity, r.finite || finiteInput, renewable && !finiteInput, r.id);
        Map<String, Double> outputs = processOutputs.getOrDefault(r.id, Map.of(r.target, r.quantity));
        return new Candidate(placement, new Witness(r.target, placement, r, List.copyOf(proofRequirements), outputs,
                r.finite || proofRequirements.stream().anyMatch(p -> p.witness().finiteAncestry())));
    }

    /** Replay one joint bill with shared leftovers and deterministic coproducts.
     * This is an acquisition witness, never a farm rate or an economic price.
     * Only finite ancestry invokes this bounded check; ordinary graph closure keeps
     * its inexpensive projection. A budget failure leaves access unproved. */
    private static final class JointStockProof {
        private final Map<String, Double> inventory = new HashMap<>(), drawn = new HashMap<>(), reserved = new HashMap<>();
        private final ProofBudget budget;
        JointStockProof(ProofBudget budget) { this.budget = budget; }
        boolean prove(Witness witness) { return acquire(witness, witness.placement().quantity(), false, 0); }
        private boolean acquire(Witness witness, double count, boolean consumed, int depth) {
            if (++budget.visits > 4096 || depth > 128) { budget.exhausted = true; return false; }
            if (witness == null || !Double.isFinite(count) || count <= 0) return false;
            String item = witness.target();
            double missing = Math.max(0, count - inventory.getOrDefault(item, 0d));
            if (missing > 0) {
                Rule rule = witness.rule();
                if (rule == null) {
                    if (witness.placement().finite()) {
                        String stock = witness.placement().source() + "\n" + item;
                        double used = drawn.getOrDefault(stock, 0d);
                        if (used + missing > witness.placement().quantity()) return false;
                        drawn.put(stock, used + missing);
                    }
                    inventory.merge(item, missing, Double::sum);
                } else if (rule.finite) {
                    // One-time reward stock can be split among descendants, but
                    // completing its prerequisite again cannot replenish the stock.
                    String stock = rule.id + "\n" + item;
                    double used = drawn.getOrDefault(stock, 0d);
                    if (used + missing > rule.quantity || !requirements(witness, depth)) return false;
                    drawn.put(stock, used + missing);
                    inventory.merge(item, missing, Double::sum);
                } else if (rule.probability < 1 && witness.placement().renewable()) {
                    // The earlier rule check requires independent renewable
                    // consumed inputs. Repeated trials establish conditional
                    // access, never a guaranteed yield from finite attempts.
                    if (!requirements(witness, depth)) return false;
                    inventory.merge(item, missing, Double::sum);
                } else {
                    double produced = witness.outputs().getOrDefault(item, 0d);
                    if (!(produced > 0)) return false;
                    long operations = (long) Math.ceil(missing / produced);
                    if (operations > 4096 - budget.visits) { budget.exhausted = true; return false; }
                    for (long operation = 0; operation < operations; operation++) {
                        if (!requirements(witness, depth)) return false;
                        witness.outputs().forEach((output, quantity) -> inventory.merge(output, quantity, Double::sum));
                    }
                }
            }
            if (inventory.getOrDefault(item, 0d) < count) return false;
            if (consumed) inventory.merge(item, -count, Double::sum);
            return true;
        }
        private boolean requirements(Witness witness, int depth) {
            boolean recipe = !witness.rule().id.startsWith("quest/") && !witness.rule().id.startsWith("reward/");
            // Reserve recipe catalysts while its consumed inputs are obtained.
            // Quest collection tasks are historical observations and may precede
            // submission of the same item, so they do not require a second copy.
            Map<String, Requirement> reusable = new LinkedHashMap<>();
            for (var requirement : witness.requirements()) if (!requirement.consumed()) {
                reusable.merge(requirement.witness().target(), requirement,
                        (a, b) -> a.count() >= b.count() ? a : b);
            }
            List<Requirement> reserved = new ArrayList<>();
            for (var requirement : reusable.values()) {
                boolean repeatQuest = witness.rule().id.startsWith("reward/") && !witness.rule().finite;
                if (recipe) {
                    String item = requirement.witness().target();
                    double additional = Math.max(0, requirement.count() - this.reserved.getOrDefault(item, 0d));
                    if (additional > 0) {
                        if (!acquire(requirement.witness(), additional, true, depth + 1)) return false;
                        this.reserved.merge(item, additional, Double::sum);
                        reserved.add(new Requirement(requirement.witness(), additional, false));
                    }
                } else if (!acquire(requirement.witness(), requirement.count(), repeatQuest, depth + 1)) return false;
            }
            for (var requirement : witness.requirements()) if (requirement.consumed()
                    && !acquire(requirement.witness(), requirement.count(), true, depth + 1)) return false;
            for (var requirement : reserved) {
                this.reserved.merge(requirement.witness().target(), -requirement.count(), Double::sum);
                inventory.merge(requirement.witness().target(), requirement.count(), Double::sum);
            }
            return true;
        }
    }
    private static final class ProofBudget { int visits; boolean exhausted; }
    /** Lazy bounded depth-first alternatives; never materialize a Cartesian product. */
    private static final class Choices {
        final List<Integer> chosen = new ArrayList<>(), sizes = new ArrayList<>();
        void begin() { sizes.clear(); }
        int pick(int size) { int index = sizes.size(); sizes.add(size); if (chosen.size() <= index) chosen.add(0); return chosen.get(index); }
        boolean next() {
            for (int i = sizes.size() - 1; i >= 0; i--) if (chosen.get(i) + 1 < sizes.get(i)) {
                chosen.set(i, chosen.get(i) + 1); chosen.subList(i + 1, chosen.size()).clear(); return true;
            }
            return false;
        }
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

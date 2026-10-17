package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import java.math.BigInteger;
import java.util.*;

/** Bounded material estimates. Recipe arithmetic is factual; seeds are policy, never access proof. */
final class SimpleValuationSolver {
    record Seed(long value, int priority, Map<String, Double> route, Map<String, Long> retainedMaterials) {
        Seed(long value, int priority, Map<String, Double> route) { this(value, priority, route, Map.of()); }
        Seed {
            if (value <= 0 || value > ProceduralValuationSettings.MAX_VALUE) throw new IllegalArgumentException("Invalid estimate");
            route = Map.copyOf(route); retainedMaterials = Map.copyOf(retainedMaterials);
            if (retainedMaterials.values().stream().anyMatch(x -> x <= 0)) throw new IllegalArgumentException("Invalid retained material count");
        }
    }
    record Value(long amount, Map<String, Long> route, String recipe, String status) { }
    record Result(Map<String, Value> values, int passes, int unresolvedProcesses) { }
    private record Conversion(String from, String to, long inputs, long outputs) { }
    private static final class Group {
        final Map<String, Long> weights;
        final Seed anchor;
        long unit;
        String recipe = "", problem = "";
        Group(Map<String, Long> weights, Map<String, Seed> seeds) {
            this.weights = weights;
            String id = weights.keySet().stream().min(Comparator
                    .<String>comparingInt(x -> -seeds.get(x).priority())
                    .thenComparingLong(weights::get).thenComparing(x -> x)).orElseThrow();
            anchor = seeds.get(id);
            long maximum = weights.values().stream().mapToLong(Long::longValue).max().orElseThrow();
            unit = Math.min(anchor.value() / weights.get(id), ProceduralValuationSettings.MAX_VALUE / maximum);
            if (unit == 0) problem = "ZERO_WHOLE_UNIT_CONVERSION_PRECISION";
        }
        long amount(String item) { return Math.multiplyExact(unit, weights.get(item)); }
    }

    static Result solve(Map<String, Seed> supplied, ProductionGraph graph) {
        Map<String, Seed> seeds = new TreeMap<>(supplied);
        Map<String, Group> groups = groups(seeds, graph);
        List<ProductionGraph.Process> recipes = graph.processes().stream().filter(SimpleValuationSolver::complete).toList();
        var composition = compose(seeds, groups, graph);
        List<ProductionGraph.Process> constraints = new ArrayList<>(recipes);
        constraints.addAll(composition.estimateBounds());
        boolean changed = false; int passes = 0;
        for (; passes < ProceduralValuationSettings.MAX_ESTIMATE_PASSES; passes++) {
            Map<Group, Long> bounds = new IdentityHashMap<>();
            Map<Group, String> reasons = new IdentityHashMap<>();
            for (var process : constraints) {
                BigInteger cost = inputCost(process, groups), output = outputCost(process, groups);
                if (cost == null || output == null || output.signum() == 0 || output.compareTo(cost) <= 0) continue;
                for (var product : process.outputs()) {
                    Group group = groups.get(product.itemId());
                    long bound = BigInteger.valueOf(group.unit).multiply(cost).divide(output).longValueExact();
                    if (bound < bounds.getOrDefault(group, group.unit)) {
                        bounds.put(group, bound); reasons.put(group, process.id());
                    }
                }
            }
            changed = !bounds.isEmpty();
            bounds.forEach((group, value) -> { group.unit = value; group.recipe = reasons.get(group); });
            if (!changed) { passes++; break; }
        }
        int unresolved = graph.processes().size() - recipes.size();
        for (var process : recipes) {
            BigInteger cost = inputCost(process, groups), output = outputCost(process, groups);
            if (cost == null || output == null || output.compareTo(cost) > 0) unresolved++;
        }
        Map<String, Value> values = new TreeMap<>();
        Map<Group, Map<String, Long>> routes = new IdentityHashMap<>();
        for (var entry : groups.entrySet()) {
            Group group = entry.getValue();
            var primitive = routes.computeIfAbsent(group, g -> allocate(g.unit, g.anchor.route()));
            Map<String, Long> routed = new TreeMap<>();
            primitive.forEach((family, amount) -> routed.put(family, Math.multiplyExact(amount, group.weights.get(entry.getKey()))));
            String status = !group.problem.isEmpty() ? group.problem : group.unit == 0 ? "ZERO_REQUIRED_BY_COMPLETE_RECIPE_CONSTRAINTS" : changed || composition.bounded() ? "ESTIMATED_BOUNDED_RECIPE_PASS_LIMIT"
                    : group.weights.size() > 1 ? "ESTIMATED_EXACT_CONVERSION_FAMILY" : "ESTIMATED_STANDARD_RECIPES";
            values.put(entry.getKey(), new Value(group.amount(entry.getKey()), Map.copyOf(routed), group.recipe, status));
        }
        return new Result(Map.copyOf(values), passes, unresolved);
    }

    private record Composition(ProductionGraph.Process process, ProductionGraph.Output output, Group target,
                               Set<Group> dependencies, boolean recoveredContents) {
        int quality() { return complete(process) ? 2 : recoveredContents ? 1 : 0; }
    }
    private record CompositionResult(boolean bounded, List<ProductionGraph.Process> estimateBounds) { }

    /** Bounded dependency propagation. Exact conversions are already one group; remaining cycles
     * retain a deterministic property anchor and cannot recursively manufacture a premium. */
    private static CompositionResult compose(Map<String, Seed> seeds, Map<String, Group> groups, ProductionGraph graph) {
        List<Group> ordered = groups.values().stream().distinct().sorted(Comparator
                .<Group>comparingInt(g -> -g.anchor.priority()).thenComparing(g -> g.weights.keySet().stream().sorted().findFirst().orElseThrow())).toList();
        Map<Group, Integer> order = new IdentityHashMap<>(); Map<Group, Set<Group>> edges = new IdentityHashMap<>();
        Map<Group, Long> baseline = new IdentityHashMap<>();
        for (int i = 0; i < ordered.size(); i++) { order.put(ordered.get(i), i); edges.put(ordered.get(i), new HashSet<>()); baseline.put(ordered.get(i), ordered.get(i).unit); }
        List<Composition> candidates = new ArrayList<>();
        for (var process : graph.processes()) {
            if (!nominal(process)) continue;
            var primary = process.outputs().stream().filter(x -> !x.byproduct()).toList();
            if (primary.size() != 1) continue;
            var output = primary.getFirst(); Group target = groups.get(output.itemId());
            if (target == null || !target.problem.isEmpty()) continue;
            Set<Group> dependencies = new HashSet<>(); boolean known = true;
            for (var input : process.inputs()) if (input.consumed()) for (String item : input.alternatives()) {
                Group dependency = groups.get(item); if (dependency == null) known = false; else dependencies.add(dependency);
            }
            for (var returned : process.outputs()) if (returned.byproduct()) {
                Group dependency = groups.get(returned.itemId()); if (dependency == null) known = false; else dependencies.add(dependency);
            }
            // An internal conversion is already valued exactly; no self-referential uplift.
            if (!known || dependencies.contains(target)) continue;
            candidates.add(new Composition(process, output, target, dependencies, false));
            for (Group dependency : dependencies) edges.get(dependency).add(target);
        }
        seeds.forEach((item, seed) -> seed.retainedMaterials().keySet().forEach(reference -> {
            Group from = groups.get(reference), to = groups.get(item); if (from != null && from != to) edges.get(from).add(to);
        }));
        Map<Group, Integer> components = components(ordered, edges, order);
        var recovered = recoveredContents(graph, groups, components);
        orderByDecomposition(ordered, recovered, order);
        candidates.addAll(recovered);
        candidates.removeIf(candidate -> candidate.dependencies().stream().anyMatch(dependency ->
                components.get(dependency).equals(components.get(candidate.target())) && order.get(dependency) >= order.get(candidate.target())));
        // Opaque getters can omit stack quantities or catalysts. They must not undercut a
        // represented static bill. Proven recoverable contents also outrank such guesses.
        Map<Group, Integer> quality = new IdentityHashMap<>();
        candidates.forEach(candidate -> quality.merge(candidate.target(), candidate.quality(), Math::max));
        candidates.removeIf(candidate -> candidate.quality() < quality.get(candidate.target()));
        boolean changed = false;
        for (int pass = 0; pass < ProceduralValuationSettings.MAX_ESTIMATE_PASSES; pass++) {
            Map<Group, Long> composed = new IdentityHashMap<>(); Map<Group, String> reasons = new IdentityHashMap<>();
            for (var candidate : candidates) {
                BigInteger budget = inputCost(candidate.process(), groups);
                for (var returned : candidate.process().outputs()) if (returned.byproduct())
                    budget = budget.subtract(BigInteger.valueOf(groups.get(returned.itemId()).amount(returned.itemId())).multiply(integer(returned.count())));
                BigInteger denominator = integer(candidate.output().count()).multiply(BigInteger.valueOf(candidate.target().weights.get(candidate.output().itemId())));
                long maximum = ProceduralValuationSettings.MAX_VALUE / candidate.target().weights.values().stream().mapToLong(Long::longValue).max().orElseThrow();
                long estimate = budget.max(BigInteger.ONE).divide(denominator).max(BigInteger.ONE)
                        .min(BigInteger.valueOf(maximum)).longValueExact();
                if (estimate < composed.getOrDefault(candidate.target(), Long.MAX_VALUE)) {
                    composed.put(candidate.target(), estimate); reasons.put(candidate.target(), candidate.process().id());
                }
            }
            // Filled containers retain their physical return; stored-effect carriers receive an
            // explicit carrier-relative estimate budget before actual effect scoring at runtime.
            for (var entry : seeds.entrySet()) {
                Group target = groups.get(entry.getKey());
                if (!target.problem.isEmpty() || target.weights.size() != 1) continue;
                for (var reference : entry.getValue().retainedMaterials().entrySet()) {
                    Group dependency = groups.get(reference.getKey());
                    if (dependency == null || dependency == target || components.get(dependency).equals(components.get(target)) && order.get(dependency) >= order.get(target)) continue;
                    long floor = BigInteger.valueOf(dependency.amount(reference.getKey())).multiply(BigInteger.valueOf(reference.getValue()))
                            .min(BigInteger.valueOf(ProceduralValuationSettings.MAX_VALUE)).longValueExact();
                    composed.merge(target, Math.max(baseline.get(target), floor), Math::max);
                }
            }
            changed = false;
            for (var entry : composed.entrySet()) {
                if (entry.getKey().unit != entry.getValue()) changed = true;
                entry.getKey().unit = entry.getValue();
                if (reasons.containsKey(entry.getKey())) entry.getKey().recipe = reasons.get(entry.getKey());
            }
            if (!changed) break;
        }
        // These selected content estimates must follow later reductions of their material bill.
        // Complete construction bills have already displaced them through quality precedence.
        // Keep them out of the factual production graph and its unresolved-process accounting.
        return new CompositionResult(changed, candidates.stream().filter(Composition::recoveredContents)
                .map(Composition::process).toList());
    }

    /** A complete single-material disassembly names a conservative content value for its input.
     * Only use it inside an existing recipe cycle; no reverse craft or catalyst behavior is claimed. */
    private static List<Composition> recoveredContents(ProductionGraph graph, Map<String, Group> groups,
                                                       Map<Group, Integer> components) {
        List<Composition> result = new ArrayList<>();
        for (var process : graph.processes()) {
            if (!complete(process) || process.inputs().size() != 1 || process.outputs().size() != 1) continue;
            var input = process.inputs().getFirst(); var output = process.outputs().getFirst();
            if (!input.consumed() || input.alternatives().size() != 1 || output.byproduct()) continue;
            String item = input.alternatives().getFirst();
            Group refined = groups.get(item), base = groups.get(output.itemId());
            if (refined == null || base == null || refined == base || !refined.problem.isEmpty() || !base.problem.isEmpty()
                    || !components.get(refined).equals(components.get(base))) continue;
            // Primitive weights belong to different material families and are not comparable.
            // Adding a smaller denomination must not reverse this declared disassembly direction.
            if (output.count() <= input.count()) continue;
            var content = new ProductionGraph.Output(item, input.count(), 1, false);
            var estimate = new ProductionGraph.Process(process.id() + "#recoverable_contents", process.family(),
                    List.of(new ProductionGraph.Input(List.of(output.itemId()), output.count(), true)), List.of(content),
                    0, 0, "complete_disassembly_contents", process.confidence(),
                    Map.of("conservation_complete", "false", "acquisition_complete", "false"));
            result.add(new Composition(estimate, content, refined, Set.of(base), true));
        }
        return result;
    }

    /** Quantities orient refinements before the existing deterministic SCC cycle break.
     * Conflicting relations retain deterministic fallback order; real amplification is still
     * checked by the unchanged complete-recipe conservation pass. */
    private static void orderByDecomposition(List<Group> ordered, List<Composition> recovered, Map<Group, Integer> order) {
        if (recovered.isEmpty()) return;
        Map<Group, Set<Group>> successors = new IdentityHashMap<>();
        Map<Group, Integer> incoming = new IdentityHashMap<>();
        for (Group group : ordered) { successors.put(group, new HashSet<>()); incoming.put(group, 0); }
        for (var candidate : recovered) for (Group base : candidate.dependencies())
            if (successors.get(base).add(candidate.target())) incoming.merge(candidate.target(), 1, Integer::sum);
        var ready = new PriorityQueue<Group>(Comparator.comparingInt(order::get));
        for (Group group : ordered) if (incoming.get(group) == 0) ready.add(group);
        List<Group> sorted = new ArrayList<>(ordered.size());
        while (!ready.isEmpty()) {
            Group group = ready.remove(); sorted.add(group);
            for (Group next : successors.get(group)) if (incoming.merge(next, -1, Integer::sum) == 0) ready.add(next);
        }
        Set<Group> included = new HashSet<>(sorted);
        for (Group group : ordered) if (!included.contains(group)) sorted.add(group);
        for (int index = 0; index < sorted.size(); index++) order.put(sorted.get(index), index);
    }

    /** Iterative Kosaraju traversal; no recursion or enumeration of recipe choices. */
    private static Map<Group, Integer> components(List<Group> nodes, Map<Group, Set<Group>> edges, Map<Group, Integer> order) {
        Map<Group, List<Group>> forward = new IdentityHashMap<>(), reverse = new IdentityHashMap<>();
        for (Group node : nodes) { forward.put(node, edges.get(node).stream().sorted(Comparator.comparingInt(order::get)).toList()); reverse.put(node, new ArrayList<>()); }
        for (Group node : nodes) for (Group target : forward.get(node)) reverse.get(target).add(node);
        Set<Group> visited = new HashSet<>(); List<Group> finish = new ArrayList<>();
        record Visit(Group node, boolean done) { }
        for (Group start : nodes) {
            if (visited.contains(start)) continue;
            ArrayDeque<Visit> stack = new ArrayDeque<>(); stack.push(new Visit(start, false));
            while (!stack.isEmpty()) {
                var visit = stack.pop();
                if (visit.done()) { finish.add(visit.node()); continue; }
                if (!visited.add(visit.node())) continue;
                stack.push(new Visit(visit.node(), true));
                for (Group target : forward.get(visit.node()).reversed()) if (!visited.contains(target)) stack.push(new Visit(target, false));
            }
        }
        Map<Group, Integer> result = new IdentityHashMap<>(); int id = 0;
        for (Group start : finish.reversed()) {
            if (result.containsKey(start)) continue;
            ArrayDeque<Group> pending = new ArrayDeque<>(); pending.add(start); result.put(start, id);
            while (!pending.isEmpty()) for (Group previous : reverse.get(pending.removeFirst()))
                if (!result.containsKey(previous)) { result.put(previous, id); pending.add(previous); }
            id++;
        }
        return result;
    }

    private static boolean complete(ProductionGraph.Process process) {
        return process.conservationComplete() && nominal(process);
    }
    private static boolean nominal(ProductionGraph.Process process) {
        return !process.inputs().isEmpty()
                && process.inputs().stream().anyMatch(ProductionGraph.Input::consumed)
                && process.inputs().stream().allMatch(x -> integral(x.count()))
                && process.outputs().stream().allMatch(x -> x.probability() == 1 && integral(x.count()));
    }
    private static boolean integral(double count) { return Double.isFinite(count) && count > 0 && count <= Integer.MAX_VALUE && count == Math.rint(count); }
    private static BigInteger integer(double count) { return BigInteger.valueOf((long) count); }
    private static BigInteger inputCost(ProductionGraph.Process process, Map<String, Group> groups) {
        BigInteger sum = BigInteger.ZERO;
        for (var input : process.inputs()) if (input.consumed()) {
            Long minimum = null;
            for (String item : input.alternatives()) {
                Group group = groups.get(item);
                // Missing alternatives are unknown, not free. Do not certify a partially represented bill.
                if (group == null) return null;
                long value = group.amount(item); minimum = minimum == null ? value : Math.min(minimum, value);
            }
            if (minimum == null) return null;
            sum = sum.add(BigInteger.valueOf(minimum).multiply(integer(input.count())));
        }
        return sum;
    }
    private static BigInteger outputCost(ProductionGraph.Process process, Map<String, Group> groups) {
        BigInteger sum = BigInteger.ZERO;
        for (var output : process.outputs()) {
            Group group = groups.get(output.itemId()); if (group == null) return null;
            sum = sum.add(BigInteger.valueOf(group.amount(output.itemId())).multiply(integer(output.count())));
        }
        return sum;
    }

    private static Map<String, Group> groups(Map<String, Seed> seeds, ProductionGraph graph) {
        List<Conversion> conversions = new ArrayList<>(); Set<String> keys = new HashSet<>();
        for (var process : graph.processes()) {
            if (!complete(process) || process.outputs().size() != 1 || process.inputs().stream().anyMatch(x -> !x.consumed())) continue;
            String to = process.outputs().getFirst().itemId();
            if (!seeds.containsKey(to)) continue;
            // A homogeneous choice is a real recipe even when its slots use tags. Intersect
            // choices instead of enumerating mixed-slot combinations; only exact reciprocal
            // pairs below become one denomination family, not every member of a tag.
            Set<String> origins = new TreeSet<>(process.inputs().getFirst().alternatives());
            for (var in : process.inputs()) origins.retainAll(new HashSet<>(in.alternatives()));
            origins.remove(to); origins.retainAll(seeds.keySet());
            if (origins.isEmpty()) continue;
            long input = 0; for (var in : process.inputs()) input = Math.addExact(input, (long) in.count());
            long output = (long) process.outputs().getFirst().count();
            long gcd = BigInteger.valueOf(input).gcd(BigInteger.valueOf(output)).longValueExact();
            for (String from : origins) {
                var conversion = new Conversion(from, to, input / gcd, output / gcd);
                conversions.add(conversion); keys.add(key(conversion));
            }
        }
        Map<String, List<ProceduralConservationMath.Edge<String>>> edges = new TreeMap<>();
        for (var c : conversions) if (keys.contains(key(new Conversion(c.to, c.from, c.outputs, c.inputs)))) {
            edges.computeIfAbsent(c.from, x -> new ArrayList<>()).add(new ProceduralConservationMath.Edge<>(c.from, c.to, c.inputs, c.outputs));
        }
        Map<String, Group> result = new TreeMap<>(); Set<String> visited = new HashSet<>();
        for (String first : seeds.keySet()) {
            if (!visited.add(first)) continue;
            List<String> members = new ArrayList<>(); List<ProceduralConservationMath.Edge<String>> component = new ArrayList<>();
            members.add(first);
            for (int i = 0; i < members.size(); i++) for (var edge : edges.getOrDefault(members.get(i), List.of())) {
                component.add(edge); if (visited.add(edge.target())) members.add(edge.target());
            }
            members.sort(String::compareTo);
            var plan = ProceduralConservationMath.plan(members, component, ProceduralValuationSettings.MAX_VALUE);
            if (!plan.valid()) {
                // Exact proportions cannot be represented safely; no independently saturated forms.
                for (String item : members) { Group g = new Group(Map.of(item, 1L), seeds); g.unit = 0;
                    g.problem = "UNREPRESENTABLE_CONVERSION_FAMILY: " + plan.problem(); result.put(item, g); }
            } else {
                Group group = new Group(plan.units(), seeds); members.forEach(item -> result.put(item, group));
            }
        }
        return result;
    }
    private static String key(Conversion c) { return c.from + "\0" + c.to + "\0" + c.inputs + "/" + c.outputs; }

    static Map<String, Long> allocate(long total, Map<String, Double> weights) {
        Map<String, Double> selected = new TreeMap<>();
        weights.forEach((key, value) -> { if (Double.isFinite(value) && value > 0) selected.put(key, value); });
        if (selected.isEmpty()) selected.put("essence_ascendance:utility", 1.0);
        Map<String, Long> result = new TreeMap<>(); double sum = selected.values().stream().mapToDouble(Double::doubleValue).sum();
        long remaining = total; int index = 0;
        for (var entry : selected.entrySet()) {
            long amount = ++index == selected.size() ? remaining : Math.min(remaining, (long) Math.floor(total * entry.getValue() / sum));
            if (amount > 0) result.put(entry.getKey(), amount); remaining -= amount;
        }
        return Map.copyOf(result);
    }
}

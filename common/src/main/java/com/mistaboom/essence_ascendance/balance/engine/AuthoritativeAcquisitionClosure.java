package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mistaboom.essence_ascendance.balance.quest.QuestEvidence;
import java.util.*;

/** Extends existing acquisition proofs after native witnesses, without pricing an invented source. */
final class AuthoritativeAcquisitionClosure {
    private static final String PROVIDER = "authoritative_acquisition_closure";
    record Result(Map<String, ResourceEvidence> resources, AcquisitionProgressionGraph.Result progression,
                  AcquisitionProgressionGraph.Constraints constraints) { }
    private record Cost(double value, double confidence, List<String> inputs) { }
    private AuthoritativeAcquisitionClosure() { }

    static Result resolve(Map<String, ResourceEvidence> before, Map<String, ResourceEvidence> enriched,
                          Map<String, AcquisitionProgressionGraph.Placement> ordinary,
                          Map<String, AcquisitionProgressionGraph.Placement> previous,
                          ProductionGraph production, QuestEvidence quests, EvidenceSink sink) {
        var constraints = constraints(enriched, production, sink);
        Map<String, AcquisitionProgressionGraph.Placement> seeds = new TreeMap<>();
        // Recompute quest stock and crafted descendants under current overrides. A
        // cached descendant is not an independent source when its parent was denied.
        ordinary.forEach((id, p) -> {
            if (p.source().equals("ordinary_acquisition") && !constraints.excludedItems().contains(id)) seeds.put(id, p);
        });
        for (var resource : enriched.values()) {
            if (!resource.reachable() || constraints.excludedItems().contains(resource.itemId())) continue;
            Set<String> oldSources = new HashSet<>();
            var original = before.get(resource.itemId());
            if (original != null) original.sources().forEach(s -> oldSources.add(s.id()));
            for (var source : resource.sources()) {
                var availability = source.availability();
                // Expected output of finite exploration is not guaranteed starting stock.
                // Only an explicit independent renewable operation removes finite quest claims.
                if (oldSources.contains(source.id()) || !source.renewable() || source.confidence() < .5
                        || !(source.expectedOutput() > 0) || availability == null || !availability.accessProven()
                        || !availability.uncertainty().isEmpty() || !(availability.occurrenceChance() > 0)
                        || !(availability.expectedPerEvent() > 0)
                        || availability.category() != SourceAvailability.Category.CONDITIONAL_RENEWABLE
                        || denied(sink, EvidenceFact.Subject.SOURCE, source.id())) continue;
                var candidate = new AcquisitionProgressionGraph.Placement(source.stage(), Map.of(), 0, false,
                        true, PROVIDER + "/" + source.id());
                var old = seeds.get(resource.itemId());
                if (old == null || old.finite() || !old.exclusiveClaims().isEmpty()
                        || candidate.stage().ordinal() <= old.stage().ordinal()) seeds.put(resource.itemId(), candidate);
            }
        }
        // Material-only closure requires a paid consumed input. Native resource-only
        // machines retain their existing specialized proof; no generic free production.
        var graph = new ProductionGraph(production.processes().stream()
                .filter(p -> !p.metadata().containsKey("quest_id") && p.acquisitionComplete()
                        && p.inputs().stream().anyMatch(ProductionGraph.Input::consumed))
                .filter(p -> !p.outputs().isEmpty()).toList(), production.warnings());
        var solved = AcquisitionProgressionGraph.solve(seeds, graph, quests, constraints);
        Map<String, ResourceEvidence> result = new TreeMap<>(enriched);
        previous.forEach((id, p) -> {
            var resource = result.get(id);
            if (!p.source().equals("ordinary_acquisition") && !solved.items().containsKey(id)
                    && resource != null && resource.reachable()) {
                var warnings = new ArrayList<>(resource.warnings()); warnings.add("unreachable_or_excluded");
                result.put(id, new ResourceEvidence(id, resource.stage(), resource.availability(), resource.automation(),
                        false, resource.external(), resource.economicValue(), resource.confidence(), resource.sources(), warnings));
                sink.add(new EvidenceFact(EvidenceFact.Subject.ITEM, id, "attainable", EvidenceFact.Value.flag(false),
                        PROVIDER, EvidenceFact.Origin.INFERRED, resource.confidence(), 1, resource.stage(), List.of(),
                        "Prior quest/recipe proof no longer resolves under authoritative exclusions"));
            } else if (!p.source().equals("ordinary_acquisition") && resource != null && resource.reachable()
                    && !p.equals(solved.items().get(id))) {
                // An earlier quest may have displaced an ordinary source in the first
                // projection. Restore the surviving witness's stage and classification.
                var proof = solved.items().get(id);
                var availability = availability(resource, proof, sink);
                for (String property : List.of("stage", "availability")) if (!overridden(sink, id, property))
                    sink.add(new EvidenceFact(EvidenceFact.Subject.ITEM, id, property,
                            EvidenceFact.Value.text(property.equals("stage") ? proof.stage().name() : availability.name()),
                            PROVIDER, EvidenceFact.Origin.INFERRED, resource.confidence(), 1, proof.stage(), List.of(),
                            "Winning acquisition witness refreshed after authoritative quest/recipe constraints"));
                result.put(id, new ResourceEvidence(id, proof.stage(), availability, resource.automation(),
                        true, resource.external(), resource.economicValue(), resource.confidence(), resource.sources(), resource.warnings()));
            }
        });
        Map<String, ResourceEvidence> paid = new TreeMap<>();
        seeds.keySet().forEach(id -> { if (result.containsKey(id)) paid.put(id, result.get(id)); });
        // The same quest projection owns reward stock and exclusive selections. Keep
        // their existing diagnostic prices; never convert a reward to ordinary supply.
        for (var entry : solved.items().entrySet()) if (entry.getValue().source().startsWith("reward/")) {
            var resource = result.get(entry.getKey());
            if (resource != null) {
                var updated = publish(resource, entry.getValue(), resource.economicValue(), resource.confidence(),
                        List.of(), null, sink);
                result.put(entry.getKey(), updated); paid.put(entry.getKey(), updated);
            }
        }
        Map<String, ProductionGraph.Process> processes = new TreeMap<>();
        graph.processes().forEach(p -> processes.put(p.id(), p));
        Map<String, AcquisitionProgressionGraph.Placement> pending = new TreeMap<>(solved.items());
        pending.keySet().removeAll(paid.keySet());
        boolean changed;
        do {
            changed = false;
            for (var it = pending.entrySet().iterator(); it.hasNext();) {
                var entry = it.next(); var resource = result.get(entry.getKey());
                var process = processes.get(entry.getValue().source());
                if (resource == null || process == null) continue;
                var stageOverride = sink.get(EvidenceFact.Subject.ITEM, entry.getKey(), "stage");
                Cost cost = cost(process, entry.getKey(), entry.getValue(), solved.items(), paid, solved.selectedInputs().get(entry.getKey()),
                        stageOverride != null && stageOverride.origin() == EvidenceFact.Origin.OVERRIDE);
                if (cost == null) continue;
                double value = Math.min(resource.economicValue(), cost.value());
                var explicit = sink.get(EvidenceFact.Subject.ITEM, entry.getKey(), "resource_value");
                if (explicit != null && explicit.origin() == EvidenceFact.Origin.OVERRIDE) value = resource.economicValue();
                var updated = publish(resource, entry.getValue(), value, Math.min(process.confidence(), cost.confidence()),
                        cost.inputs(), process, sink);
                result.put(entry.getKey(), updated); paid.put(entry.getKey(), updated);
                it.remove(); changed = true;
            }
        } while (changed);
        return new Result(Collections.unmodifiableMap(result), solved, constraints);
    }

    private static Cost cost(ProductionGraph.Process process, String output,
                             AcquisitionProgressionGraph.Placement proof,
                             Map<String, AcquisitionProgressionGraph.Placement> placements,
                             Map<String, ResourceEvidence> paid, List<String> witnessedInputs, boolean explicitOutputStage) {
        double total = 0, confidence = 1; List<String> selected = new ArrayList<>();
        if (witnessedInputs == null || witnessedInputs.size() != process.inputs().size()) return null;
        for (int index = 0; index < process.inputs().size(); index++) {
            var input = process.inputs().get(index); String chosen = witnessedInputs.get(index);
            var placement = placements.get(chosen);
            // The shared solver proved this exact joint assignment. Choosing a
            // cheaper alternative here could spend the same finite ancestry twice.
            if (!input.alternatives().contains(chosen) || !paid.containsKey(chosen) || placement == null
                    || !explicitOutputStage && placement.stage().ordinal() > proof.stage().ordinal()
                    || placement.exclusiveClaims().entrySet().stream().anyMatch(c -> !c.getValue().equals(proof.exclusiveClaims().get(c.getKey())))) return null;
            var resource = paid.get(chosen); selected.add(chosen);
            confidence = Math.min(confidence, resource.confidence());
            if (input.consumed()) total += resource.economicValue() * input.count();
        }
        double count = process.outputs().stream().filter(o -> o.itemId().equals(output))
                .mapToDouble(ProductionGraph.Output::expectedCount).sum();
        return count > 0 && Double.isFinite(total) ? new Cost(total / count, confidence, List.copyOf(selected)) : null;
    }

    private static ResourceEvidence publish(ResourceEvidence resource, AcquisitionProgressionGraph.Placement proof,
                                             double value, double confidence, List<String> dependencies,
                                             ProductionGraph.Process process, EvidenceSink sink) {
        if (resource.reachable()) return resource;
        var sources = new ArrayList<>(resource.sources());
        String reason = "Authoritative native/quest acquisition closure; finite=" + proof.finite()
                + "; quantity=" + proof.quantity() + "; exclusive claims=" + proof.exclusiveClaims()
                + "; witness=" + proof.source() + "; paid consumed-input value bound=" + value
                + "; reusable setup required without recurring material credit; no inferred rate";
        if (process != null) sources.add(new AcquisitionSource(process.id(), AcquisitionSource.Kind.RECIPE,
                proof.stage(), proof.quantity(), proof.renewable(), false, 0, confidence, dependencies, reason));
        var warnings = resource.warnings().stream().filter(w -> !w.equals("unreachable_or_excluded")).toList();
        var availability = availability(resource, proof, sink);
        for (String property : List.of("attainable", "stage", "resource_value", "availability")) {
            if (overridden(sink, resource.itemId(), property)) continue;
            EvidenceFact.Value fact = switch (property) {
                case "attainable" -> EvidenceFact.Value.flag(true);
                case "stage" -> EvidenceFact.Value.text(proof.stage().name());
                case "resource_value" -> EvidenceFact.Value.number(value);
                default -> EvidenceFact.Value.text(availability.name());
            };
            sink.add(new EvidenceFact(EvidenceFact.Subject.ITEM, resource.itemId(), property, fact, PROVIDER,
                    EvidenceFact.Origin.INFERRED, confidence, 1, proof.stage(), dependencies, reason));
        }
        return new ResourceEvidence(resource.itemId(), proof.stage(), availability, resource.automation(), true,
                resource.external(), value, confidence, sources, warnings);
    }

    private static Availability availability(ResourceEvidence resource, AcquisitionProgressionGraph.Placement proof, EvidenceSink sink) {
        var availability = proof.renewable() ? Availability.RENEWABLE_MANUAL : Availability.FINITE;
        if (overridden(sink, resource.itemId(), "availability")) availability = resource.availability();
        if (overridden(sink, resource.itemId(), "renewable") && !sink.flag(EvidenceFact.Subject.ITEM, resource.itemId(), "renewable", false)
                && (availability == Availability.RENEWABLE_MANUAL || availability == Availability.RENEWABLE_AUTOMATED)) availability = Availability.FINITE;
        return availability;
    }

    private static AcquisitionProgressionGraph.Constraints constraints(Map<String, ResourceEvidence> resources,
                                                                       ProductionGraph graph, EvidenceSink sink) {
        Set<String> deniedItems = new HashSet<>(), deniedRecipes = new HashSet<>(), nonrenewable = new HashSet<>();
        Map<String, ProgressionBand> stages = new TreeMap<>();
        for (String id : resources.keySet()) {
            if (denied(sink, EvidenceFact.Subject.ITEM, id)) deniedItems.add(id);
            var stage = sink.get(EvidenceFact.Subject.ITEM, id, "stage");
            if (stage != null && stage.origin() == EvidenceFact.Origin.OVERRIDE) stages.put(id, resources.get(id).stage());
            var renewable = sink.get(EvidenceFact.Subject.ITEM, id, "renewable");
            if (renewable != null && renewable.origin() == EvidenceFact.Origin.OVERRIDE && !renewable.value().flag()) nonrenewable.add(id);
            if (overridden(sink, id, "availability") && switch (resources.get(id).availability()) {
                case FINITE, ADMINISTRATIVE, UNKNOWN -> true;
                default -> false;
            }) nonrenewable.add(id);
        }
        graph.processes().forEach(p -> { if (denied(sink, EvidenceFact.Subject.RECIPE, p.id())) deniedRecipes.add(p.id()); });
        return new AcquisitionProgressionGraph.Constraints(deniedItems, deniedRecipes, stages, nonrenewable);
    }
    private static boolean overridden(EvidenceSink sink, String item, String property) {
        var fact = sink.get(EvidenceFact.Subject.ITEM, item, property);
        return fact != null && fact.origin() == EvidenceFact.Origin.OVERRIDE;
    }
    private static boolean denied(EvidenceSink sink, EvidenceFact.Subject subject, String id) {
        if (List.of("disabled", "creative_only", "administrative", "joke").stream().anyMatch(p -> sink.flag(subject, id, p, false))) return true;
        var attainable = sink.get(subject, id, "attainable");
        return attainable != null && attainable.origin() == EvidenceFact.Origin.OVERRIDE && !attainable.value().flag();
    }
}

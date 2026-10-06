package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Generation projection of shared equipment/acquisition/production. No Essence calibration rules. */
public final class CompetitiveCapabilities {
    private CompetitiveCapabilities() { }
    public static JsonObject collect(PackEvidenceContext context, Map<String, ResourceEvidence> resources,
                                     List<EquipmentReference> equipment, List<CapabilityEvidence> declared,
                                     List<PackEvidenceProvider> providers, GenerationProviders runs, EvidenceSink sharedFacts) {
        BalancePerformance.increment("competitive_capability_runs");
        CapabilitySink sink = new CapabilitySink();
        try (var phase = BalancePerformance.phase("competitive_capabilities")) {
            equipment(equipment, resources, sink, sharedFacts);
            OptionalIntegration.attempt("native_capabilities", "collect", () -> {
                CapabilitySink staged = new CapabilitySink(); NativeCapabilityReader.collect(context, resources, staged); return staged;
            }).value().ifPresentOrElse(sink::merge,
                    () -> sink.candidate("native_capabilities", "native_capabilities", "Native compatibility read failed; partial capabilities excluded",
                            Set.of(), CapabilitySink.Reason.READ_FAILED));
            production(context.inputs().production(), resources, sink);
            for (CapabilityEvidence source : declared) {
                sink.analyzed();
                List<Measurement> measured = source.axes().entrySet().stream().map(e -> measurement(e.getKey(), e.getValue(),
                        "provider_native_axis", "declared axis; original provider conditions apply", Scope.unknown(), Operation.manual(),
                        "shared_evidence", Origin.GENERIC_SYSTEM, List.of("Axis normalization requires provider contract"))).toList();
                sink.add(new Functional(source, "declared", measured, sources(resources, source.subjectId()), false));
            }
            for (PackEvidenceProvider provider : providers) {
                int before = sink.evidenceCount();
                runs.collect("evidence", provider, "competitive_capabilities", CapabilitySink::new,
                        staged -> provider.collectCapabilities(context, resources, staged), sink::merge);
                runs.emitted("evidence", provider, sink.evidenceCount() - before, 0, .8);
            }
            for (PackEvidenceProvider provider : com.mistaboom.essence_ascendance.balance.capability.InstalledCapabilityProviders.all()) {
                if (!runs.prepare("capability", provider, GenerationProviders.disabled(provider.id(), context.overrides()))) {
                    if (runs.status("capability", provider) == ProviderReadiness.Status.FAILED
                            || runs.status("capability", provider) == ProviderReadiness.Status.DISABLED
                            || runs.dependenciesInstalled("capability", provider))
                        sink.candidate(provider.id(), provider.id(), runs.detail("capability", provider),
                                runs.capabilityAxes("capability", provider), runs.unavailableReason("capability", provider));
                    continue;
                }
                int before = sink.evidenceCount();
                if (!runs.collect("capability", provider, "collect", CapabilitySink::new,
                        staged -> provider.collectCapabilities(context, resources, staged), sink::merge))
                    sink.candidate(provider.id(), provider.id(), "Failed compatibility read excluded; see provider diagnostics",
                            runs.capabilityAxes("capability", provider), CapabilitySink.Reason.READ_FAILED);
                runs.emitted("capability", provider, sink.evidenceCount() - before, 0, .8);
            }
            JsonObject report = report(sink, context.settings().outlierPolicy().name());
            report.add("providerDiagnostics", runs.capabilityDiagnostics());
            return report;
        }
    }
    /** Explicit development replay of accepted saved facts; never invoked by saved-profile startup/export. */
    public static JsonObject replay(PackEvidence saved, ProductionGraph production) {
        CapabilitySink sink = new CapabilitySink();
        EvidenceSink facts = new EvidenceSink(); saved.facts().forEach(facts::add);
        equipment(saved.equipment(), saved.resources(), sink, facts);
        production(production, saved.resources(), sink);
        saved.resources().keySet().forEach(id -> NativeCapabilityReader.candidateName(id, sink));
        JsonObject report = report(sink, "WINSORIZE");
        report.addProperty("basis", "Partial read-only projection of accepted saved equipment/acquisition/production. No native enchantment census or optional adapter capture; not a live Chat 7B rebuild.");
        return report;
    }
    public static JsonObject report(CapabilitySink sink, String policy) {
        var frontiers = RobustFrontiers.competitive(sink.evidence(), policy);
        JsonObject result = new JsonObject(); result.addProperty("contract", "competitive-capability-1");
        result.addProperty("purpose", "Factual competition only; does not modify Essence strengths. Null means unknown; binary presence is not throughput.");
        result.addProperty("outlierPolicy", policy);
        result.add("evidence", BalanceDocument.GSON.toJsonTree(sink.evidence()));
        result.add("frontiers", BalanceDocument.GSON.toJsonTree(frontiers));
        result.add("candidates", BalanceDocument.GSON.toJsonTree(sink.candidates()));
        result.add("unsupportedCounts", BalanceDocument.GSON.toJsonTree(sink.unsupportedCounts()));
        result.add("candidateCoverage", BalanceDocument.GSON.toJsonTree(sink.candidateCoverage()));
        result.add("unscopedCandidateReasons", BalanceDocument.GSON.toJsonTree(sink.unscopedCandidateReasons()));
        result.add("nativeDefinitions", BalanceDocument.GSON.toJsonTree(sink.definitions()));
        Map<String, Long> counts = new TreeMap<>(sink.counts()); counts.put("frontierCount", (long)frontiers.size());
        result.add("counts", BalanceDocument.GSON.toJsonTree(counts));
        counts.forEach((key, value) -> BalancePerformance.count("competitive_" + key, value));
        return result;
    }
    static void equipment(List<EquipmentReference> equipment, Map<String, ResourceEvidence> resources, CapabilitySink sink, EvidenceSink sharedFacts) {
        for (EquipmentReference ref : equipment) {
            sink.analyzed();
            ResourceEvidence resource = resources.get(ref.itemId());
            List<Measurement> measured = new ArrayList<>();
            for (var axis : ref.axes().entrySet()) {
                if (axis.getValue() <= 0) continue;
                boolean projectileEstimate = ref.capabilities().contains("vanilla_projectile_estimate_requires_provider_for_custom_behavior")
                        && List.of(CapabilityAxis.BURST_DAMAGE, CapabilityAxis.SUSTAINED_DAMAGE, CapabilityAxis.ATTACK_RATE, CapabilityAxis.RANGE).contains(axis.getKey());
                if (projectileEstimate) { sink.candidate(ref.itemId(), "native_equipment", "Projectile behavior requires verified configuration; default estimates excluded"); continue; }
                EvidenceFact original = sharedFacts.get(EvidenceFact.Subject.ITEM, ref.itemId(), "axis." + axis.getKey());
                String provider = original == null ? "resolved_equipment" : original.provider();
                Origin origin = original != null && original.origin() == EvidenceFact.Origin.OBSERVED ? Origin.NATIVE : Origin.GENERIC_SYSTEM;
                measured.add(measurement(axis.getKey(), axis.getValue(), unit(axis.getKey()), "equipment slot=" + ref.slot(),
                        equipmentScope(axis.getKey()), Operation.manual(), provider, origin,
                        ref.capabilities().contains("unknown_dynamic_behavior") ? List.of("Default measurement excludes affixes/materials/upgrades/custom behavior") : List.of()));
            }
            var placement = placement(resources, ref.itemId());
            boolean usable = ref.included() && resource != null && resource.external() && placement.reachable();
            sink.add(new Functional(new CapabilityEvidence(ref.itemId(), placement.stage(), ref.axes(), usable,
                    Math.min(ref.confidence(), placement.confidence()), ref.reason()), "effective_default", measured, placement.acquisition(), usable));
        }
    }
    public static Scope equipmentScope(CapabilityAxis axis) {
        return switch (axis) {
            case BURST_DAMAGE, SUSTAINED_DAMAGE, MELEE_DAMAGE, RANGED_DAMAGE, MAGIC_DAMAGE, DAMAGE_OVER_TIME, CRITICAL_DAMAGE ->
                    new Scope("combat_target", "single", null, 1.0);
            case MINING_SPEED, HARVEST_LEVEL, HARVEST_SPEED -> new Scope("harvest_target", "single", null, 1.0);
            case DURABILITY, DURABILITY_REDUCTION, INDESTRUCTIBILITY, REPAIR, ITEM_LOSS_PREVENTION -> new Scope("equipped_item", "single", null, 1.0);
            case AREA_DAMAGE, AREA_MINING, VEIN_MINING -> Scope.unknown();
            default -> Scope.self();
        };
    }
    public record Placement(ProgressionBand stage, boolean reachable, double confidence, List<AcquisitionSource> acquisition) { }
    /** Retain independent early quest/loot opportunities; a late crafting classification is not a global use gate. */
    public static Placement placement(Map<String, ResourceEvidence> resources, String id) {
        ResourceEvidence resource = resources.get(id);
        if (resource == null || !resource.reachable() || !resource.external()) return new Placement(ProgressionBand.APEX, false, 0, List.of());
        List<AcquisitionSource> proven = resource.sources().stream().filter(s -> s.kind() != AcquisitionSource.Kind.ADMINISTRATIVE
                // The source snapshot uses ENTRY placeholders for recipe/machine rows. Those rows
                // describe ingredients, not a solved route band. Supplied-block actions also are not
                // independent natural access. Only independent source opportunities may lower placement.
                && s.kind() != AcquisitionSource.Kind.RECIPE && s.kind() != AcquisitionSource.Kind.MACHINE
                && s.kind() != AcquisitionSource.Kind.PLAYER_ACTION
                && s.expectedOutput() > 0 && s.confidence() >= .5 && (s.availability() == null || s.availability().accessProven()))
                .sorted(Comparator.comparing(AcquisitionSource::stage).thenComparing(AcquisitionSource::id)).toList();
        ProgressionBand stage = proven.isEmpty() ? resource.stage() : proven.getFirst().stage();
        // Shared solver reachability remains the proof; uncertainty stays on the original source records.
        List<AcquisitionSource> witnesses = proven.isEmpty() ? resource.sources().stream()
                .filter(s -> s.kind() != AcquisitionSource.Kind.ADMINISTRATIVE && s.expectedOutput() > 0 && s.confidence() >= .5
                        && (s.availability() == null || s.availability().accessProven())).limit(3).toList()
                : proven.stream().filter(s -> s.stage() == stage).limit(3).toList();
        return new Placement(stage, true, resource.confidence(), witnesses);
    }
    private static List<AcquisitionSource> sources(Map<String, ResourceEvidence> resources, String id) { return placement(resources, id).acquisition(); }
    public static Measurement measurement(CapabilityAxis axis, Double magnitude, String unit, String applies, Scope scope,
                                          Operation operation, String provider, Origin origin, List<String> unknown) {
        return new Measurement(family(axis), axis, magnitude, unit, applies, scope, operation, provider, origin, unknown);
    }
    public static CapabilityFamily family(CapabilityAxis axis) {
        return switch (axis) {
            case ARMOR, TOUGHNESS, EFFECTIVE_HEALTH, MAX_HEALTH, DAMAGE_REDUCTION, AVOIDANCE, BLOCKING, REFLECTION,
                 RECOVERY, HEALING, REGENERATION, STATUS_RESISTANCE, SHIELD_CAPACITY, DAMAGE_IMMUNITY,
                 KNOCKBACK_RESISTANCE, BURST_SURVIVAL, SUSTAINED_SURVIVAL -> CapabilityFamily.DEFENSE;
            case DURABILITY, DURABILITY_REDUCTION, INDESTRUCTIBILITY, REPAIR, ITEM_LOSS_PREVENTION -> CapabilityFamily.ITEM_PRESERVATION;
            case GROUND_SPEED, JUMP, VERTICAL_MOVEMENT, FALL_CONTROL, GLIDING, FLIGHT, ABILITIES_FLYING_SPEED, TELEPORTATION, STEP_HEIGHT -> CapabilityFamily.MOBILITY;
            case MINING_SPEED, AREA_MINING, VEIN_MINING, HARVEST_LEVEL, CROP_YIELD, DROP_YIELD, HARVEST_SPEED,
                 FISHING_PRODUCTIVITY, FISHING_TIME_REDUCTION, REACH, TOOL_VERSATILITY -> CapabilityFamily.MANUAL_GATHERING;
            case AUTOMATED_EXTRACTION, AUTOMATED_FARMING, AUTOMATED_FISHING, MOB_FARMING, PASSIVE_GENERATION, AUTOMATION_INTERACTION -> CapabilityFamily.RESOURCE_AUTOMATION;
            case CROP_ACCELERATION, TREE_ACCELERATION, ANIMAL_ACCELERATION -> CapabilityFamily.BIOLOGICAL_GROWTH;
            case SPAWN_SUPPRESSION, MOB_REPULSION, HAZARD_SUPPRESSION -> CapabilityFamily.WORLD_CONTROL;
            case MACHINE_ACCELERATION, BLOCK_ENTITY_ACCELERATION, ENTITY_ACCELERATION, LOCAL_TIME_ACCELERATION -> CapabilityFamily.TIME_ACCELERATION;
            case INFORMATION -> CapabilityFamily.INFORMATION;
            case INVENTORY -> CapabilityFamily.STORAGE;
            case CONVERSION, THROUGHPUT, RESOURCE_CONSUMPTION -> CapabilityFamily.PROCESSING;
            case CONVENIENCE, ENCHANTING_EFFICIENCY, ANVIL_EFFICIENCY, EXPERIENCE -> CapabilityFamily.MAGIC_UTILITY;
            default -> CapabilityFamily.COMBAT_OFFENSE;
        };
    }
    private static String unit(CapabilityAxis axis) {
        return switch (axis) {
            case BURST_DAMAGE, MELEE_DAMAGE, RANGED_DAMAGE, MAGIC_DAMAGE, MAX_HEALTH, SHIELD_CAPACITY -> "health_points";
            case SUSTAINED_DAMAGE -> "health_points_per_second";
            case ATTACK_RATE -> "attacks_per_second";
            case DURABILITY -> "uses";
            case MINING_SPEED -> "native_destroy_speed";
            case REACH, RANGE -> "blocks";
            case FLIGHT, GLIDING, BLOCKING, INDESTRUCTIBILITY -> "presence";
            default -> "native_" + axis.name().toLowerCase(Locale.ROOT);
        };
    }
    public static void production(ProductionGraph graph, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
        if (graph == null) return;
        for (var process : graph.processes()) {
            sink.analyzed();
            String function = process.metadata().get("function");
            CapabilityAxis axis = function == null ? null : switch (function) {
                case "automated_resource_extraction" -> CapabilityAxis.AUTOMATED_EXTRACTION;
                case "automated_fishing" -> CapabilityAxis.AUTOMATED_FISHING;
                case "automated_farming" -> CapabilityAxis.AUTOMATED_FARMING;
                case "mob_farming" -> CapabilityAxis.MOB_FARMING;
                case "passive_generation" -> CapabilityAxis.PASSIVE_GENERATION;
                case "crop_growth_acceleration" -> CapabilityAxis.CROP_ACCELERATION;
                case "machine_tick_acceleration" -> CapabilityAxis.MACHINE_ACCELERATION;
                default -> null;
            };
            if (axis == null) {
                if (process.sourceProducer() && !process.metadata().containsKey("quest_id"))
                    sink.candidate(process.id(), process.provider(), "Source production has no supported competitive function/access contract");
                continue;
            }
            // Access must be linked to a source item; output availability does not prove access to its producing machine.
            String source = process.metadata().getOrDefault("capability_source_item", process.id());
            var placement = placement(resources, source);
            List<String> unknown = new ArrayList<>();
            if (!placement.reachable()) unknown.add("Machine/setup access not proven by shared acquisition");
            Double rate = null;
            if ("true".equals(process.metadata().get("capability_rate_complete")) && process.duration() != null && process.duration() > 0)
                rate = process.outputs().stream().mapToDouble(ProductionGraph.Output::expectedCount).sum() * 20 / process.duration();
            if (rate == null) unknown.add("Throughput/eligible pool/operating dependencies unresolved; binary function only");
            List<String> costs = process.inputs().stream().filter(ProductionGraph.Input::consumed).map(Object::toString).toList();
            costs = new ArrayList<>(costs);
            for (String key : List.of("energy", "durability_cost", "access")) if (process.metadata().containsKey(key)) costs.add(key + "=" + process.metadata().get(key));
            String activity = process.metadata().getOrDefault("player_activity", "unknown");
            Activity playerActivity = activity.startsWith("passive") ? Activity.PASSIVE : activity.startsWith("active") || activity.equals("player_active")
                    ? Activity.PLAYER_ACTIVE : Activity.UNKNOWN;
            Automation automation = "manual".equals(process.metadata().get("operation")) ? Automation.NONE
                    : "automated".equals(process.metadata().get("operation")) || "automated_processing".equals(process.metadata().get("operation")) ? Automation.BOUNDED : Automation.UNKNOWN;
            Renewal renewal = switch (process.metadata().getOrDefault("renewability", "unknown")) {
                case "renewable" -> Renewal.RENEWABLE; case "finite" -> Renewal.FINITE; default -> Renewal.UNKNOWN;
            };
            if (costs.isEmpty()) costs.add("Operating costs unmeasured");
            if (process.externalCost() > 0) costs.add("external_cost=" + process.externalCost() + "; native units");
            var operation = new Operation(automation, playerActivity, renewal, rate, null, null, null,
                    costs, List.of(process.metadata().getOrDefault("setup", "setup unmeasured")));
            Measurement m = measurement(axis, rate == null ? 1.0 : rate, rate == null ? "presence" : "items_per_second",
                    "source function=" + function + "; outputs=" + process.outputs().stream().map(ProductionGraph.Output::itemId).toList(),
                    Scope.unknown(), operation, process.provider(), Origin.GENERIC_SYSTEM, unknown);
            sink.add(new Functional(new CapabilityEvidence(source, placement.stage(), Map.of(), placement.reachable(), Math.min(process.confidence(), placement.confidence()),
                    "Normalized process " + process.id()), process.id(), List.of(m), placement.acquisition(), placement.reachable()));
            if (!unknown.isEmpty()) sink.candidate(process.id(), process.provider(), String.join("; ", unknown));
        }
    }
}

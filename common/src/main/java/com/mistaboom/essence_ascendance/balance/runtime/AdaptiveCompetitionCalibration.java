package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineConfig;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillProgressionPolicy;
import com.mistaboom.essence_ascendance.skill.balance.SkillProgressionGraph;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.*;

/** Operation-scoped consumer of the already collected compact 7B frontiers. Never discovers sources.
 * Rules relate our native mechanics to generic axes; source/provider identifiers are diagnostic only. */
public final class AdaptiveCompetitionCalibration {
    public enum Relationship {
        DIRECT(.85), STRONG(.60), INDIRECT(.30), COMPLEMENTARY(0);
        final double elasticity;
        Relationship(double elasticity) { this.elasticity = elasticity; }
    }
    public record Link(CapabilityAxis axis, Relationship relationship, double share) {
        public Link { if (!Double.isFinite(share) || share < 0 || share > 1) throw new IllegalArgumentException("Invalid competition share"); }
    }
    private record Reference(RobustFrontiers.CompetitiveFrontier frontier, CapabilityEvidence.Functional witness, CapabilityEvidence.Measurement measurement,
                             double confidence, List<String> sources) { }
    private record Pressure(double factor, Reference reference, Link link) { }
    private record AvailabilityObservation(String subject, String configuration, ProgressionBand stage,
            boolean reachable, boolean attainable, double confidence, CapabilityEvidence.Measurement measurement) { }
    private final PackEvidence evidence;
    private final Map<ProgressionBand, Map<CapabilityAxis, List<Reference>>> references = new EnumMap<>(ProgressionBand.class);
    private final List<Reference> availabilityReferences = new ArrayList<>();
    private final Map<String, List<Link>> links = new TreeMap<>();
    private final Map<String, JsonObject> rows = new TreeMap<>();
    private final Map<String, JsonObject> availability = new TreeMap<>();
    private final Map<ResourceLocation, SkillProgressionPolicy.Decision> intrinsicDecisions = new TreeMap<>();
    private final List<AvailabilityObservation> availabilityObservations = new ArrayList<>();
    private final List<JsonObject> candidateDiagnostics = new ArrayList<>();
    private final List<JsonObject> providerDiagnostics = new ArrayList<>();
    private final JsonObject candidateCoverage = new JsonObject();
    private final JsonObject unscopedCandidateReasons = new JsonObject();
    private boolean scopedCoverageAvailable, scopedCoverageSupplied;
    private int diagnosticReadFailures, unscopedCandidateSamples;
    private final Set<String> warnings = new TreeSet<>();
    private final Map<ProgressionBand, Map<String, Pressure>> pressureCache = new EnumMap<>(ProgressionBand.class);
    private long nanos;
    private int timingDepth;
    private long startTiming() { timingDepth++; return System.nanoTime(); }
    private void endTiming(long start) { if (--timingDepth == 0) nanos += System.nanoTime() - start; }

    public AdaptiveCompetitionCalibration(PackEvidence evidence, JsonObject capabilities) {
        this.evidence = evidence;
        long start = startTiming();
        mappings();
        if (capabilities != null) {
            if (!capabilities.has("contract") || !capabilities.get("contract").getAsString().equals("competitive-capability-1"))
                throw new IllegalArgumentException("Unsupported competitive capability contract");
            readAvailabilityDiagnostics(capabilities);
            // Calibration decodes only frontier representatives. The diagnostic projection above
            // never feeds reference selection or inspects native definitions/live registries.
            for (JsonElement element : capabilities.getAsJsonArray("frontiers")) {
                var frontier = BalanceDocument.GSON.fromJson(element, RobustFrontiers.CompetitiveFrontier.class);
                if (!Double.isFinite(frontier.magnitude()) || frontier.magnitude() < 0) throw new IllegalArgumentException("Invalid competitive frontier");
                var admitted = frontier.representatives().stream().filter(f -> f.attainable() && f.source().reachable()
                        && !f.source().subjectId().startsWith("essence_ascendance:")
                        && f.source().confidence() >= .5 && f.source().stage().ordinal() <= frontier.band().ordinal()).toList();
                if (admitted.isEmpty()) continue;
                var representative = admitted.getFirst();
                var measured = representative.measurements().stream().filter(m -> m.comparisonKey().equals(frontier.comparisonKey())
                        && m.magnitude() != null && m.origin() != CapabilityEvidence.Origin.NOMENCLATURE).findFirst().orElseThrow();
                var reference = new Reference(frontier, representative, measured, representative.source().confidence(), admitted.stream()
                        .map(f -> f.source().subjectId() + " / " + f.configuration() + "; " + f.source().reason()).toList());
                // Diagnostics count every retained admissible witness. Calibration continues
                // to use exactly the same first representative and Reference as before.
                for (var alternative : admitted) alternative.measurements().stream()
                        .filter(m -> m.comparisonKey().equals(frontier.comparisonKey())
                                && m.magnitude() != null && m.origin() != CapabilityEvidence.Origin.NOMENCLATURE)
                        .findFirst().ifPresent(m -> availabilityReferences.add(new Reference(frontier, alternative, m,
                                alternative.source().confidence(), List.of())));
                references.computeIfAbsent(frontier.band(), ignored -> new EnumMap<>(CapabilityAxis.class))
                        .computeIfAbsent(measured.axis(), ignored -> new ArrayList<>()).add(reference);
            }
            if (capabilities.has("candidates")) diagnosticRead("candidate warning samples", () -> {
                for (var element : capabilities.getAsJsonArray("candidates")) diagnosticRead("candidate warning identity/detail", () -> {
                    var candidate = element.getAsJsonObject();
                    warnings.add("Competitive pressure may be underestimated because " + diagnosticString(candidate, "subject")
                            + " behavior is unsupported: " + diagnosticString(candidate, "detail"));
                });
            });
        }
        endTiming(start);
    }
    /** Project diagnostic fields only. This never admits an extra source into calibration. */
    private void readAvailabilityDiagnostics(JsonObject capabilities) {
        scopedCoverageSupplied = capabilities.has("candidateCoverage");
        if (capabilities.has("candidateCoverage")) diagnosticRead("candidate coverage", () -> {
            var staged = new JsonObject();
            for (var entry : capabilities.getAsJsonObject("candidateCoverage").entrySet()) {
                CapabilityAxis.valueOf(entry.getKey()); staged.add(entry.getKey(), diagnosticReasonCounts(entry.getValue()));
            }
            staged.entrySet().forEach(entry -> candidateCoverage.add(entry.getKey(), entry.getValue()));
            scopedCoverageAvailable = true;
        });
        if (capabilities.has("unscopedCandidateReasons")) diagnosticRead("unscoped candidate counts", () -> {
            var staged = diagnosticReasonCounts(capabilities.get("unscopedCandidateReasons"));
            staged.entrySet().forEach(entry -> unscopedCandidateReasons.add(entry.getKey(), entry.getValue()));
        });
        if (capabilities.has("candidates")) diagnosticRead("candidate samples", () -> {
            for (var element : capabilities.getAsJsonArray("candidates")) diagnosticRead("candidate scope/reason", () -> {
                var candidate = element.getAsJsonObject(); validateDiagnosticAxes(candidate, "axes");
                if (candidate.has("reason")) CapabilitySink.Reason.valueOf(candidate.get("reason").getAsString());
                candidateDiagnostics.add(candidate.deepCopy());
                if (!candidate.has("axes") || candidate.getAsJsonArray("axes").isEmpty()) unscopedCandidateSamples++;
            });
        });
        if (capabilities.has("providerDiagnostics")) diagnosticRead("provider samples", () -> {
            for (var element : capabilities.getAsJsonArray("providerDiagnostics")) diagnosticRead("provider scope/status", () -> {
                var provider = element.getAsJsonObject(); validateDiagnosticAxes(provider, "capabilityAxes");
                if (provider.has("status")) ProviderReadiness.Status.valueOf(provider.get("status").getAsString());
                providerDiagnostics.add(provider.deepCopy());
            });
        });
        if (capabilities.has("evidence")) diagnosticRead("factual measurement samples", () -> {
            for (var element : capabilities.getAsJsonArray("evidence")) diagnosticRead("factual measurement", () -> {
                var fact = element.getAsJsonObject(); var source = fact.getAsJsonObject("source");
                var staged = new ArrayList<AvailabilityObservation>();
                for (var measured : fact.getAsJsonArray("measurements")) {
                    var measurement = Objects.requireNonNull(BalanceDocument.GSON.fromJson(measured,
                            CapabilityEvidence.Measurement.class), "Diagnostic measurement is null");
                    staged.add(new AvailabilityObservation(source.get("subjectId").getAsString(),
                            fact.get("configuration").getAsString(), ProgressionBand.valueOf(source.get("stage").getAsString()),
                            source.get("reachable").getAsBoolean(), fact.get("attainable").getAsBoolean(),
                            source.get("confidence").getAsDouble(), measurement));
                }
                availabilityObservations.addAll(staged);
            });
        });
    }
    private static JsonObject diagnosticReasonCounts(JsonElement element) {
        JsonObject staged = new JsonObject();
        for (var entry : element.getAsJsonObject().entrySet()) {
            CapabilitySink.Reason.valueOf(entry.getKey());
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Reason count must be a number");
            long count = entry.getValue().getAsBigDecimal().longValueExact();
            if (count < 0) throw new IllegalArgumentException("Reason count must be nonnegative");
            staged.addProperty(entry.getKey(), count);
        }
        return staged;
    }
    private static void validateDiagnosticAxes(JsonObject row, String field) {
        if (!row.has(field)) return;
        for (var element : row.getAsJsonArray(field)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Diagnostic axis must be a string");
            CapabilityAxis.valueOf(element.getAsString());
        }
    }
    private static String diagnosticString(JsonObject row, String field) {
        var value = row.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Diagnostic " + field + " must be a string");
        return value.getAsString();
    }
    private void diagnosticRead(String field, Runnable read) {
        try { read.run(); }
        catch (RuntimeException | LinkageError failure) { diagnosticFailure(field, failure); }
    }
    private void diagnosticFailure(String field, Throwable failure) {
        diagnosticReadFailures++;
        warnings.add("Skill availability diagnostic " + field + " is unreadable (" + failure.getClass().getSimpleName()
                + "); affected coverage is unknown. Valid compact frontiers still determine tiers, effects and prices.");
    }
    public static AdaptiveCompetitionCalibration neutral(PackEvidence evidence) { return new AdaptiveCompetitionCalibration(evidence, null); }
    private static Link direct(CapabilityAxis axis, double share) { return new Link(axis, Relationship.DIRECT, share); }
    private static Link strong(CapabilityAxis axis, double share) { return new Link(axis, Relationship.STRONG, share); }
    private static Link indirect(CapabilityAxis axis, double share) { return new Link(axis, Relationship.INDIRECT, share); }
    private void map(String feature, Link... values) { links.put(feature, List.of(values)); }
    private void mappings() {
        map("equipment.meleeDamage", direct(MELEE_DAMAGE, .6), direct(BURST_DAMAGE, .6), direct(SUSTAINED_DAMAGE, .6));
        map("equipment.rangedDamage", direct(RANGED_DAMAGE, .6), strong(SUSTAINED_DAMAGE, .6));
        map("equipment.magicDamage", direct(MAGIC_DAMAGE, .6), strong(SUSTAINED_DAMAGE, .6));
        map("equipment.fullSetArmor", direct(ARMOR, .65));
        map("equipment.fullSetToughness", direct(TOUGHNESS, .65));
        map("equipment.miningSpeed", direct(MINING_SPEED, .5), direct(HARVEST_SPEED, .6));
        map("equipment.durability", direct(DURABILITY, .45), strong(INDESTRUCTIBILITY, .15));
        map("equipment.harvestLevel", direct(HARVEST_LEVEL, 1));
        for (var stat : EssenceStatRegistry.values()) {
            String name = stat.id().getPath();
            CapabilityAxis axis = BonusSemantics.require(stat).axis();
            map("bonus." + name, direct(axis, .25));
        }
        map("bonus.melee_damage", direct(MELEE_DAMAGE, .25), direct(BURST_DAMAGE, .25), direct(SUSTAINED_DAMAGE, .25));
        map("bonus.ranged_damage", direct(RANGED_DAMAGE, .25), strong(SUSTAINED_DAMAGE, .25));
        map("bonus.magic_damage", direct(MAGIC_DAMAGE, .25), strong(SUSTAINED_DAMAGE, .25));
        map("bonus.max_health", direct(MAX_HEALTH, .4), strong(SHIELD_CAPACITY, .4), strong(EFFECTIVE_HEALTH, .4), indirect(BURST_SURVIVAL, .4));
        map("bonus.durability_efficiency", direct(DURABILITY_REDUCTION, .5), strong(DURABILITY, .25), strong(INDESTRUCTIBILITY, .5), strong(REPAIR, .35));
        map("bonus.mining_speed", direct(MINING_SPEED, .25), strong(AREA_MINING, .4), strong(VEIN_MINING, .4), indirect(AUTOMATED_EXTRACTION, .4));
        map("bonus.fortune", direct(DROP_YIELD, .25), indirect(AUTOMATED_EXTRACTION, .2));
        map("bonus.looting", direct(DROP_YIELD, .15));
        map("bonus.luck", direct(DROP_YIELD, .1));
        map("bonus.crop_yield", direct(CROP_YIELD, .4), indirect(AUTOMATED_FARMING, .25));
        map("bonus.flight_speed", direct(ABILITIES_FLYING_SPEED, .5), strong(FLIGHT, .2), indirect(TELEPORTATION, .25));
        map("bonus.movement_speed", direct(GROUND_SPEED, .5), strong(TELEPORTATION, .25), strong(FLIGHT, .15));
        // Explicit sibling allocations: a single substitute is not fully granted to every consumer.
        map("skill.natures_boon", direct(DROP_YIELD, .5), strong(AREA_MINING, .4), strong(VEIN_MINING, .4),
                indirect(MINING_SPEED, .1), indirect(AUTOMATED_EXTRACTION, .4));
        map("skill.tool_instinct", direct(MINING_SPEED, .075), strong(AREA_MINING, .1), strong(VEIN_MINING, .1));
        map("skill.mining_momentum", direct(MINING_SPEED, .075), strong(AREA_MINING, .1), strong(VEIN_MINING, .1));
        map("skill.verdant_stride", direct(CROP_ACCELERATION, 1), direct(TREE_ACCELERATION, 1), strong(AUTOMATED_FARMING, .75));
        map("skill.fishers_call", direct(FISHING_PRODUCTIVITY, .55), direct(FISHING_TIME_REDUCTION, .55), strong(AUTOMATED_FISHING, .55));
        map("skill.pocket_nets", strong(FISHING_PRODUCTIVITY, .3), strong(AUTOMATED_FISHING, .3));
        map("skill.fishing_instinct", direct(FISHING_PRODUCTIVITY, .15), direct(FISHING_TIME_REDUCTION, .45), indirect(AUTOMATED_FISHING, .15));
        map("skill.industrious_presence", direct(MACHINE_ACCELERATION, 1), direct(BLOCK_ENTITY_ACCELERATION, 1), strong(LOCAL_TIME_ACCELERATION, 1));
        map("skill.sanctuary", direct(SPAWN_SUPPRESSION, 1), strong(MOB_REPULSION, 1));
        map("skill.restful_mending", direct(REPAIR, .4), strong(INDESTRUCTIBILITY, .15), strong(DURABILITY, .1), strong(DURABILITY_REDUCTION, .2));
        map("skill.metabolic_mending", direct(REPAIR, .25), strong(INDESTRUCTIBILITY, .1), strong(DURABILITY, .1), strong(DURABILITY_REDUCTION, .15));
        map("skill.masterwork_tempering", direct(DURABILITY, .1), strong(INDESTRUCTIBILITY, .1), strong(DURABILITY_REDUCTION, .15));
        map("skill.running_momentum", direct(GROUND_SPEED, .5), strong(FLIGHT, .2), indirect(TELEPORTATION, .2));
        map("skill.charged_jump", direct(JUMP, .35), strong(VERTICAL_MOVEMENT, .35), strong(FLIGHT, .1));
        map("skill.double_jump", direct(JUMP, .35), strong(VERTICAL_MOVEMENT, .35), strong(FLIGHT, .1));
        map("skill.vector_jump", direct(JUMP, .3), strong(VERTICAL_MOVEMENT, .3), strong(FLIGHT, .1));
        map("skill.fatigue_flight", strong(FLIGHT, .15), strong(TELEPORTATION, .1));
        map("skill.essence_wings", direct(GLIDING, .5), strong(FLIGHT, .15));
        map("skill.untethered_flight", direct(FLIGHT, .15));
        map("skill.frenzy", direct(MELEE_DAMAGE, .15), direct(SUSTAINED_DAMAGE, .05));
        map("skill.combustion", direct(BURST_DAMAGE, .05), strong(MAGIC_DAMAGE, .05));
        map("skill.shatter", direct(BURST_DAMAGE, .05), strong(MAGIC_DAMAGE, .05));
        map("skill.static_charge", direct(BURST_DAMAGE, .05), strong(MAGIC_DAMAGE, .05));
        map("skill.rising_recovery", direct(REGENERATION, .4), strong(RECOVERY, .4));
        map("skill.life_steal", direct(HEALING, .4), strong(REGENERATION, .2));
        map("skill.bulwark_stance", direct(DAMAGE_REDUCTION, .2), strong(SHIELD_CAPACITY, .2), strong(ARMOR, .2));
        map("skill.adaptive_guard", direct(DAMAGE_REDUCTION, .2), strong(SHIELD_CAPACITY, .2), strong(TOUGHNESS, .35));
        map("skill.evasive_current", direct(AVOIDANCE, .4), strong(SHIELD_CAPACITY, .2));
        map("skill.threat_sense", direct(INFORMATION, .5));
        map("skill.hunters_ledger", direct(INFORMATION, .5));
        map("skill.waylight", direct(HAZARD_SUPPRESSION, 1));
        map("skill.herdkeeper", direct(ANIMAL_ACCELERATION, 1));
        map("skill.animal_gift", strong(PASSIVE_GENERATION, .2));
    }
    private double reference(CapabilityAxis axis, ProgressionBand band) {
        return Math.max(1, evidence.reference(band, axis, switch (axis) {
            case MINING_SPEED, HARVEST_SPEED -> 8;
            case DURABILITY -> 1500;
            case MAX_HEALTH, EFFECTIVE_HEALTH, SHIELD_CAPACITY -> 20;
            case MELEE_DAMAGE, RANGED_DAMAGE, MAGIC_DAMAGE, BURST_DAMAGE -> 8;
            case SUSTAINED_DAMAGE -> 12;
            case ARMOR -> 20;
            case TOUGHNESS -> 8;
            default -> 1;
        }));
    }
    /** Converts only documented units to dimensionless pressure; unknown units never acquire a magnitude. */
    private double logPressure(Reference ref) {
        var m = ref.measurement; double value = ref.frontier.magnitude(), ratio;
        String unit = m.unit(); CapabilityAxis axis = m.axis();
        if (unit.equals("presence")) {
            if (value <= 0) return 0;
            if (!Set.of(INDESTRUCTIBILITY, FLIGHT, GLIDING, BLOCKING, TELEPORTATION, AUTOMATED_EXTRACTION,
                    AUTOMATED_FISHING, AUTOMATED_FARMING, PASSIVE_GENERATION, SPAWN_SUPPRESSION, MOB_REPULSION,
                    HAZARD_SUPPRESSION, CROP_ACCELERATION, ANIMAL_ACCELERATION, TREE_ACCELERATION,
                    MACHINE_ACCELERATION, BLOCK_ENTITY_ACCELERATION, LOCAL_TIME_ACCELERATION, INFORMATION).contains(axis)) {
                warnings.add("Competitive pressure may be underestimated because binary " + axis + " has no supported numeric response; sources=" + ref.sources);
                return 0;
            }
            ratio = axis == INDESTRUCTIBILITY ? 100 : 2; // finite identity compensation, never infinite throughput
        } else if (unit.equals("native_destroy_speed") || unit.equals("uses") || unit.equals("health_points")
                || unit.equals("health_points_per_second") || unit.equals("attacks_per_second") || unit.equals("native_armor")
                || unit.equals("native_toughness") || unit.equals("native_harvest_level")) ratio = value / reference(axis, ref.frontier.band());
        else if (unit.equals("native_effective_health")) ratio = value / reference(EFFECTIVE_HEALTH, ref.frontier.band());
        else if (unit.equals("attribute_total_factor") || unit.equals("multiplier")) ratio = value;
        else if (unit.equals("attribute_multiplier_addition") || unit.equals("extra_tick_calls_per_server_tick")) ratio = 1 + value;
        else if (unit.equals("bonemeal_growth_chance_per_action") && Set.of(CROP_ACCELERATION, TREE_ACCELERATION).contains(axis))
            ratio = value > 0 ? 2 : 1; // Native growth action identity; action cadence is unmeasured.
        else if (unit.equals("attribute_addition")) {
            // Movement/jump additions use their native base units, not mining/damage units.
            double base = axis == GROUND_SPEED ? .1 : axis == JUMP ? .42 : reference(axis, ref.frontier.band());
            ratio = 1 + value / base;
        } else if (unit.endsWith(":multiply")) ratio = value;
        else if (unit.equals("wear_transform:fraction_reduced") || unit.equals("wear_transform:binomial_probability") || unit.equals("fraction"))
            ratio = 1 / Math.max(.01, 1 - Math.min(1, value));
        else if (unit.equals("blocks") || ((unit.equals("cube_radius_blocks") || unit.equals("sphere_radius_blocks"))
                && Set.of(SPAWN_SUPPRESSION, MOB_REPULSION, HAZARD_SUPPRESSION).contains(axis)))
            ratio = value / 16; // Radius relevance, never volume cubed or an assertion of identical geometry.
        else if (unit.equals("items_per_second")) ratio = 1 + value; // relevance relative to one active item/s, NOT matching a machine rate
        else if (unit.equals("targets") || unit.equals("count") || unit.equals("levels") || unit.startsWith("native_fishing_luck:")
                || unit.startsWith("durability_per_xp:")) ratio = 1 + value;
        else if (unit.equals("health_points:add") || unit.equals("health_points:set")) ratio = 1 + value / reference(BURST_DAMAGE, ref.frontier.band());
        else if (unit.startsWith("enchantment_protection_points:")) ratio = 1 / Math.max(.2, 1 - Math.min(20, value) / 25);
        else if (unit.startsWith("native_wait_reduction:")) ratio = 1 + value / 5;
        else {
            warnings.add("Competitive pressure may be underestimated because " + axis + " unit " + unit + " is unsupported by calibration; sources=" + ref.sources);
            return 0;
        }
        double pressure = Math.log(Math.max(1, Math.min(Double.MAX_VALUE, ratio)));
        if (m.operation().uptimeBound() != null) pressure *= m.operation().uptimeBound();
        else if (m.applicability().contains("conditional=") || m.unsupported().stream().anyMatch(s -> s.contains("Conditional peak"))) pressure *= .5;
        return pressure * ref.confidence;
    }
    private Pressure pressure(String feature, ProgressionBand band) {
        return pressureCache.computeIfAbsent(band, ignored -> new HashMap<>())
                .computeIfAbsent(feature, ignored -> calculatePressure(feature, band));
    }
    private Pressure calculatePressure(String feature, ProgressionBand band) {
        Pressure best = new Pressure(1, null, null);
        for (Link link : links.getOrDefault(feature, List.of())) for (Reference ref : references.getOrDefault(band, Map.of()).getOrDefault(link.axis, List.of())) {
            if (!compatible(feature, ref.measurement)) continue;
            double log = logPressure(ref);
            // Smooth diminishing log elasticity, finite even for Double.MAX_VALUE; no additive substitute amplification.
            double factor = Math.exp(link.share * link.relationship.elasticity * log / (1 + log / 12));
            if (factor > best.factor || best.reference == null) best = new Pressure(factor, ref, link);
        }
        return best;
    }
    private static boolean compatible(String feature, CapabilityEvidence.Measurement measurement) {
        if (feature.startsWith("skill.") && !com.mistaboom.essence_ascendance.skill.balance.SkillFunctionalScope.compatible(
                ResourceLocation.fromNamespaceAndPath("essence_ascendance", feature.substring(6)), measurement)) return false;
        String applies = measurement.applicability();
        // One source's protected wear pool cannot protect every tool or armor item we sell.
        if (measurement.axis() == INDESTRUCTIBILITY && measurement.scope().targets().startsWith("source_equipment:")) return false;
        if (feature.equals("skill.sanctuary") && (measurement.scope().targets().equals("passive_mob_spawns")
                || applies.contains("affected_monsters=") && !applies.contains("affected_monsters=true"))) return false;
        String family = feature.contains("meleeDamage") || feature.equals("bonus.melee_damage") ? "melee"
                : feature.contains("rangedDamage") || feature.equals("bonus.ranged_damage") ? "ranged"
                : feature.contains("magicDamage") || feature.equals("bonus.magic_damage") ? "caster" : null;
        if (family == null || !applies.startsWith("equipment slot=")) return true;
        if (applies.contains("mainhand_bow") || applies.contains("mainhand_crossbow")) return family.equals("ranged");
        if (applies.contains("mainhand_caster")) return family.equals("caster");
        if (applies.contains("mainhand_melee") || applies.contains("mainhand_tool")) return family.equals("melee");
        return false;
    }
    public double factor(String feature, ProgressionBand band) {
        long start = startTiming(); double result = pressure(feature, band).factor; endTiming(start); return result;
    }
    private static ProgressionBand band(ResourceLocation tier) {
        var tiers = AscendanceTierRegistry.powerTiers().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        for (int i = 0; i < tiers.size(); i++) if (tiers.get(i).id().equals(tier)) return ProgressionBand.at(i);
        throw new IllegalArgumentException("No competitive band for non-powered tier " + tier);
    }
    /** Intrinsic utility supplies every initial decision. Supported external functional access
     * can lower it independently of equivalent strength. Catalog placement is diagnostic only. */
    public Map<ResourceLocation, ResourceLocation> skillTiers() {
        long start = startTiming();
        var tiers = AscendanceTierRegistry.powerTiers().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        Map<ResourceLocation, ResourceLocation> result = new TreeMap<>();
        Map<ResourceLocation, ResourceLocation> minimumTiers = new TreeMap<>();
        var intrinsic = BalanceDocument.GSON.toJsonTree(SkillEffectBalanceSettings.defaults()).getAsJsonObject();
        var operating = new com.mistaboom.essence_ascendance.skill.balance.SkillOperatingAccess.Resolver(evidence);
        for (var skill : SkillRegistry.values()) {
            ResourceLocation original = skill.catalogRequiredTierId();
            var semantics = com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics.require(skill.id());
            var decision = SkillProgressionPolicy.evaluate(skill, semantics, intrinsic);
            var operatingAccess = operating.evaluate(semantics);
            intrinsicDecisions.put(skill.id(), decision);
            int earliest = decision.band().ordinal(); Reference witness = null;
            String feature = "skill." + skill.id().getPath();
            // Availability and strength relationships are intentionally separate contracts.
            Map<CapabilityAxis, Link> accessLinks = new EnumMap<>(CapabilityAxis.class);
            for (var axis : com.mistaboom.essence_ascendance.skill.balance.SkillFunctionalScope.availabilityAxes(semantics))
                accessLinks.put(axis, direct(axis, 1));
            for (var candidateBand : ProgressionBand.values()) {
                if (candidateBand.ordinal() >= earliest) break;
                for (var link : accessLinks.values()) {
                    if (link.relationship != Relationship.DIRECT && link.relationship != Relationship.STRONG) continue;
                    for (var ref : references.getOrDefault(candidateBand, Map.of()).getOrDefault(link.axis, List.of())) {
                        if (ref.frontier.magnitude() > 0 && compatible(feature, ref.measurement) && accessUnit(ref.measurement)) {
                            earliest = candidateBand.ordinal(); witness = ref; break;
                        }
                    }
                }
            }
            int functionalBand = earliest;
            earliest = Math.max(earliest, operatingAccess.earliestSupportedSetup().ordinal());
            result.put(skill.id(), tiers.get(earliest).id());
            minimumTiers.put(skill.id(), tiers.get(operatingAccess.earliestSupportedSetup().ordinal()).id());
            JsonObject row = new JsonObject(); row.addProperty("skill", skill.id().toString());
            row.addProperty("catalogTier", original.toString());
            row.addProperty("model", SkillProgressionPolicy.CONTRACT);
            row.add("intrinsicDecision", BalanceDocument.GSON.toJsonTree(decision));
            row.add("mechanics", BalanceDocument.GSON.toJsonTree(skill.progressionRequirements()));
            row.add("semantics", BalanceDocument.GSON.toJsonTree(semantics));
            row.add("operatingAccess", BalanceDocument.GSON.toJsonTree(operatingAccess));
            row.addProperty("functionalTierBeforeSetup", tiers.get(functionalBand).id().toString());
            row.addProperty("candidateTier", tiers.get(earliest).id().toString());
            row.add("consideredAxes", BalanceDocument.GSON.toJsonTree(accessLinks.keySet()));
            row.add("prerequisiteRanks", BalanceDocument.GSON.toJsonTree(skill.prerequisiteRanks(1)));
            row.add("requirementIds", BalanceDocument.GSON.toJsonTree(skill.requirements(1).stream().map(r -> r.id().toString()).toList()));
            row.add("witness", witness == null ? JsonNull.INSTANCE : BalanceDocument.GSON.toJsonTree(witness.witness));
            row.addProperty("policy", "Retain prerequisite ranks, rank-specific gates, milestones and live requirements; no ownership assumed.");
            availabilityProvenance(row, feature, accessLinks, band(original));
            availability.put(skill.id().toString(), row);
        }
        var closed = SkillProgressionGraph.close(SkillRegistry.values(), result, minimumTiers);
        result.clear(); result.putAll(closed);
        result.forEach((id, tier) -> {
            var row = availability.get(id.toString()); row.addProperty("generatedTier", tier.toString());
            row.add("prerequisiteClamps", prerequisiteClamps(SkillRegistry.require(id), result,
                    ResourceLocation.parse(row.get("candidateTier").getAsString())));
            String reason = !tier.toString().equals(row.get("candidateTier").getAsString()) ? "PROGRESSION_GRAPH_CONSTRAINT"
                    : !row.get("candidateTier").equals(row.get("functionalTierBeforeSetup")) ? "EXTERNAL_OPERATING_SETUP_GATE"
                    : !row.get("witness").isJsonNull() ? "EARLIER_SUPPORTED_SUBSTITUTE"
                    : "EXPLICIT_MECHANICAL_UTILITY_POLICY";
            row.addProperty("decisionReason", reason);
            row.addProperty("decision", switch (reason) {
                case "PROGRESSION_GRAPH_CONSTRAINT" -> "strict prerequisite tier separation, equal-tier choice peers and descendant tier space constrain the candidate";
                case "EARLIER_SUPPORTED_SUBSTITUTE" -> "earlier supported functional substitute admitted";
                case "EXTERNAL_OPERATING_SETUP_GATE" -> "earliest supported external operating equipment constrains the computed functional tier";
                default -> "computed from native first-state measurements, semantic scope and operating restrictions; external evidence coverage reported separately";
            });
        });
        endTiming(start); return Collections.unmodifiableMap(result);
    }

    /** Price policy runs after actual native rank publication and never feeds back into tier/strength.
     * The economy's tier budgets retain its explicit effort assumptions; they are not measured rates. */
    public RuntimeBalanceDefinition priceSkills(RuntimeBalanceDefinition runtime, Map<String, Double> categoryFactors,
            com.mistaboom.essence_ascendance.balance.config.BalanceSettings settings,
            com.mistaboom.essence_ascendance.balance.economy.EconomyProfile economy) {
        var json = runtime.toJson(); var effects = json.getAsJsonObject("effects");
        var economyDecisions = new HashMap<String, com.mistaboom.essence_ascendance.skill.balance.SkillEconomyAccess.Decision>();
        var curves = new TreeMap<String, com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedSkill>();
        for (var skill : SkillRegistry.values()) {
            var curve = runtime.skillCurves().get(skill.id().toString());
            Objects.requireNonNull(intrinsicDecisions.get(skill.id()), "Skill placement must precede pricing");
            double category = categoryFactors.getOrDefault(skill.essenceId().toString(), 1.0);
            double configured = SkillProgressionPolicy.configuredPriceFactor(skill, settings, evidence);
            var ranks = new ArrayList<com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedRank>();
            var first = effects.deepCopy();
            curve.ranks().getFirst().parameters().forEach((path, value) -> ProgressionRequirements.write(first, path, value));
            var publishedUtility = SkillProgressionPolicy.evaluate(skill,
                    com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics.require(skill.id()), first);
            long previous = 0; var prices = new JsonArray();
            for (var rank : curve.ranks()) {
                var requiredTier = SkillProgressionGraph.gated(skill, curve.requiredTierId(), rank.rank());
                long budget = runtime.config().balanceProfile().defaultTierCaps().get(requiredTier);
                double alternative = 1 / Math.sqrt(factor("skill." + skill.id().getPath(), band(requiredTier)));
                var supply = economyDecisions.computeIfAbsent(requiredTier + "/" + skill.essenceId(), ignored ->
                        com.mistaboom.essence_ascendance.skill.balance.SkillEconomyAccess.evaluate(evidence, economy, band(requiredTier), skill.essenceId().toString()));
                var nativeState = effects.deepCopy();
                rank.parameters().forEach((path, value) -> ProgressionRequirements.write(nativeState, path, value));
                double benefit = SkillProgressionPolicy.rankUtility(skill, nativeState, first);
                long price = SkillProgressionPolicy.price(budget, publishedUtility.utility(), category * configured * supply.priceFactor(), alternative, benefit, previous);
                ranks.add(new com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedRank(
                        rank.rank(), price, rank.powerMultiplier(), rank.parameters()));
                var row = new JsonObject(); row.addProperty("rank", rank.rank()); row.addProperty("price", price);
                row.addProperty("nativeBenefitRelativeToFirst", benefit); row.addProperty("tierBudget", budget);
                row.addProperty("categoryFactor", category); row.addProperty("alternativeFactor", alternative);
                row.addProperty("configuredPriceFactor", configured);
                row.add("economicSupply", BalanceDocument.GSON.toJsonTree(supply));
                row.addProperty("publishedUtility", publishedUtility.utility());
                row.add("parameters", BalanceDocument.GSON.toJsonTree(rank.parameters())); prices.add(row); previous = price;
            }
            availability.get(skill.id().toString()).add("rankDecisions", prices);
            curves.put(skill.id().toString(), new com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedSkill(
                    curve.maximumRank(), ranks, curve.requiredTierId()));
        }
        json.add("skillCurves", BalanceDocument.GSON.toJsonTree(curves));
        return RuntimeBalanceDefinition.fromJson(json);
    }
    private void availabilityProvenance(JsonObject row, String feature, Map<CapabilityAxis, Link> accessLinks,
            ProgressionBand catalogBand) {
        try {
            var staged = new JsonObject(); availabilityProvenanceDetails(staged, feature, accessLinks, catalogBand);
            staged.entrySet().forEach(entry -> row.add(entry.getKey(), entry.getValue()));
        } catch (RuntimeException | LinkageError failure) {
            diagnosticFailure("skill row projection", failure);
            JsonObject counts = new JsonObject(); counts.addProperty("admittedEarlierTier", 0);
            counts.addProperty("admittedSameTier", 0); counts.addProperty("admittedLaterTier", 0);
            row.add("competitionCounts", counts); row.addProperty("evidenceStatus", "UNKNOWN");
            row.add("evidenceGaps", BalanceDocument.GSON.toJsonTree(List.of("DIAGNOSTIC_ROW_PROJECTION_UNREADABLE")));
            row.add("candidateReasonCountsByAxis", new JsonObject()); row.add("relevantCandidates", new JsonArray());
            row.add("relevantProviders", new JsonArray()); row.addProperty("scopedCandidateCoverageAvailable", false);
            row.addProperty("coveragePolicy", "Optional diagnostic projection is unreadable. Tiers, effects and prices continue to use valid compact frontiers; diagnostic zero counts do not establish absence.");
        }
    }
    private void availabilityProvenanceDetails(JsonObject row, String feature, Map<CapabilityAxis, Link> accessLinks,
            ProgressionBand catalogBand) {
        Set<CapabilityAxis> axes = new TreeSet<>();
        accessLinks.values().stream().filter(link -> link.relationship == Relationship.DIRECT
                || link.relationship == Relationship.STRONG).forEach(link -> axes.add(link.axis));
        Set<String> admitted = new HashSet<>();
        long earlier = 0, same = 0, later = 0;
        JsonArray admittedSamples = new JsonArray();
        for (var ref : availabilityReferences) {
            if (!axes.contains(ref.measurement.axis()) || ref.frontier.magnitude() <= 0 || ref.measurement.magnitude() <= 0
                    || !compatible(feature, ref.measurement) || !accessUnit(ref.measurement)) continue;
            String key = observationKey(ref.witness.source().subjectId(), ref.witness.configuration(),
                    ref.witness.source().stage(), ref.measurement);
            if (!admitted.add(key)) continue; // Cumulative frontiers repeat the same measurement witness/acquisition band.
            int relative = ref.witness.source().stage().compareTo(catalogBand);
            if (relative < 0) earlier++; else if (relative == 0) same++; else later++;
            if (admittedSamples.size() < 8) admittedSamples.add(observationSample(ref.witness.source().subjectId(),
                    ref.witness.configuration(), ref.witness.source().stage(), ref.measurement, "ADMITTED"));
        }
        long observed = 0;
        Map<String, Long> rejectedEarlier = new TreeMap<>(), rejectedAll = new TreeMap<>();
        Set<String> observedKeys = new HashSet<>(); JsonArray rejectedSamples = new JsonArray();
        for (var observation : availabilityObservations) {
            if (!axes.contains(observation.measurement.axis())) continue;
            String key = observationKey(observation.subject, observation.configuration, observation.stage, observation.measurement);
            if (!observedKeys.add(key + "/" + observation.reachable + "/" + observation.attainable + "/" + observation.confidence)) continue;
            observed++;
            String rejection = !observation.attainable ? "UNATTAINABLE_CONFIGURATION"
                    : !observation.reachable ? "UNPROVEN_SOURCE_ACCESS"
                    : !Double.isFinite(observation.confidence) || observation.confidence < .5 ? "LOW_CONFIDENCE"
                    : observation.measurement.origin() == CapabilityEvidence.Origin.NOMENCLATURE ? "NOMENCLATURE_ONLY"
                    : observation.measurement.magnitude() == null ? "UNMEASURED_MAGNITUDE"
                    : observation.measurement.magnitude() <= 0 ? "NONPOSITIVE_MAGNITUDE"
                    : !compatible(feature, observation.measurement) ? "INCOMPATIBLE_SCOPE"
                    : !accessUnit(observation.measurement) ? "UNSUPPORTED_AVAILABILITY_UNIT"
                    : !admitted.contains(key) ? "NOT_SELECTED_FOR_COMPACT_FRONTIER" : null;
            if (rejection != null) {
                rejectedAll.merge(rejection, 1L, Long::sum);
                if (observation.stage.compareTo(catalogBand) < 0) {
                    rejectedEarlier.merge(rejection, 1L, Long::sum);
                    if (rejectedSamples.size() < 8) rejectedSamples.add(observationSample(observation.subject,
                            observation.configuration, observation.stage, observation.measurement, rejection));
                }
            }
        }
        JsonObject counts = new JsonObject(); counts.addProperty("admittedEarlierTier", earlier);
        counts.addProperty("admittedSameTier", same); counts.addProperty("admittedLaterTier", later);
        counts.addProperty("observedMeasurements", observed);
        counts.add("rejectedEarlierByReason", BalanceDocument.GSON.toJsonTree(rejectedEarlier));
        counts.add("rejectedAllByReason", BalanceDocument.GSON.toJsonTree(rejectedAll));
        row.add("competitionCounts", counts); row.add("admittedCompetitionSamples", admittedSamples);
        row.add("rejectedEarlierSamples", rejectedSamples);

        JsonObject relevantCoverage = new JsonObject(); Set<String> gaps = new TreeSet<>();
        for (var axis : axes) if (candidateCoverage.has(axis.name())) {
            var reasons = candidateCoverage.getAsJsonObject(axis.name()); relevantCoverage.add(axis.name(), reasons.deepCopy());
            reasons.entrySet().stream().filter(entry -> entry.getValue().getAsLong() > 0)
                    .forEach(entry -> gaps.add(entry.getKey()));
        }
        JsonArray relevantCandidates = new JsonArray(); long relevantCandidateSamples = 0;
        for (var candidate : candidateDiagnostics) if (intersects(candidate, "axes", axes)) {
            relevantCandidateSamples++; if (relevantCandidates.size() < 12) relevantCandidates.add(candidate.deepCopy());
        }
        JsonArray relevantProviders = new JsonArray(); long relevantProviderCount = 0;
        for (var provider : providerDiagnostics) if (intersects(provider, "capabilityAxes", axes)) {
            relevantProviderCount++; if (relevantProviders.size() < 12) relevantProviders.add(provider.deepCopy());
            String status = provider.has("status") ? provider.get("status").getAsString() : "UNKNOWN";
            if (!status.equals("AVAILABLE")) gaps.add("PROVIDER_" + status);
        }
        if (earlier + same + later == 0) gaps.add(observed == 0 ? "NO_AXIS_MEASUREMENTS" : "NO_SUPPORTED_AVAILABILITY_MEASUREMENT");
        if (!scopedCoverageAvailable) gaps.add(scopedCoverageSupplied ? "CANDIDATE_AXIS_COVERAGE_UNREADABLE"
                : "LEGACY_CANDIDATE_AXIS_COVERAGE_UNKNOWN");
        if (diagnosticReadFailures > 0) gaps.add("DIAGNOSTIC_EVIDENCE_READ_INCOMPLETE");
        rejectedAll.keySet().stream().filter(reason -> !reason.equals("NOT_SELECTED_FOR_COMPACT_FRONTIER")).forEach(gaps::add);
        row.addProperty("evidenceStatus", observed > 0 || earlier + same + later > 0 || relevantCandidateSamples > 0
                || !relevantCoverage.isEmpty() || relevantProviderCount > 0 ? "PARTIAL" : "UNKNOWN");
        row.addProperty("coveragePolicy", "Captured evidence is not an exhaustive proof that earlier alternatives are absent. Candidate totals are uncapped per-axis counts; samples are bounded. Unscoped diagnostics cannot be attributed to this skill.");
        row.add("evidenceGaps", BalanceDocument.GSON.toJsonTree(gaps)); row.add("candidateReasonCountsByAxis", relevantCoverage);
        row.addProperty("relevantCandidateSamplesAvailable", relevantCandidateSamples);
        row.add("relevantCandidates", relevantCandidates); row.add("relevantProviders", relevantProviders);
        row.addProperty("relevantProviderCount", relevantProviderCount);
        row.addProperty("unscopedCandidateSamplesGlobal", unscopedCandidateSamples);
        row.add("unscopedCandidateReasonCountsGlobal", unscopedCandidateReasons.deepCopy());
        row.addProperty("scopedCandidateCoverageAvailable", scopedCoverageAvailable);
    }
    private static String observationKey(String subject, String configuration, ProgressionBand stage,
            CapabilityEvidence.Measurement measurement) {
        return subject + "/" + configuration + "/" + stage + "/" + measurement.comparisonKey();
    }
    private static JsonObject observationSample(String subject, String configuration, ProgressionBand stage,
            CapabilityEvidence.Measurement measurement, String disposition) {
        JsonObject sample = new JsonObject(); sample.addProperty("subject", subject); sample.addProperty("configuration", configuration);
        sample.addProperty("stage", stage.name()); sample.addProperty("axis", measurement.axis().name());
        sample.addProperty("unit", measurement.unit()); sample.addProperty("provider", measurement.provider());
        sample.addProperty("applicability", measurement.applicability()); sample.addProperty("disposition", disposition);
        return sample;
    }
    private static boolean intersects(JsonObject row, String field, Set<CapabilityAxis> axes) {
        if (!row.has(field) || !row.get(field).isJsonArray()) return false;
        for (var element : row.getAsJsonArray(field)) if (element.isJsonPrimitive()
                && axes.stream().anyMatch(axis -> axis.name().equals(element.getAsString()))) return true;
        return false;
    }
    private static JsonArray prerequisiteClamps(SkillDefinition skill, Map<ResourceLocation, ResourceLocation> resolved,
            ResourceLocation candidateTier) {
        JsonArray clamps = new JsonArray();
        for (var prerequisite : skill.prerequisiteRanks(1).entrySet()) {
            var parent = SkillRegistry.require(prerequisite.getKey()); var baseTier = resolved.get(parent.id());
            var bindingTier = baseTier; int bindingRank = 1; JsonArray gates = new JsonArray();
            for (int rank = 1; rank <= prerequisite.getValue(); rank++) {
                var gate = parent.rankPolicy().rankGates().get(rank);
                if (gate != null && gate.requiredTierId() != null) {
                    JsonObject retained = new JsonObject(); retained.addProperty("rank", rank);
                    retained.addProperty("tier", gate.requiredTierId().toString()); gates.add(retained);
                    if (order(gate.requiredTierId()) > order(bindingTier)) { bindingTier = gate.requiredTierId(); bindingRank = rank; }
                }
            }
            if (order(bindingTier) < order(candidateTier)) continue;
            JsonObject clamp = new JsonObject(); clamp.addProperty("prerequisiteSkill", parent.id().toString());
            clamp.addProperty("requiredRank", prerequisite.getValue()); clamp.addProperty("effectiveBaseTier", baseTier.toString());
            clamp.addProperty("bindingRank", bindingRank); clamp.addProperty("bindingTier", bindingTier.toString());
            clamp.add("retainedRankTierGates", gates); clamp.addProperty("generatedSkillTier", resolved.get(skill.id()).toString());
            clamps.add(clamp);
        }
        return clamps;
    }
    private static int order(ResourceLocation tier) { return AscendanceTierRegistry.get(tier).orElseThrow().order(); }
    private static boolean accessUnit(CapabilityEvidence.Measurement measurement) {
        var axis = measurement.axis(); String unit = measurement.unit();
        if (unit.equals("bonemeal_growth_chance_per_action")) return Set.of(CROP_ACCELERATION, TREE_ACCELERATION).contains(axis);
        if (unit.equals("extra_tick_calls_per_server_tick")) return axis == BLOCK_ENTITY_ACCELERATION;
        if (unit.equals("multiplier")) return Set.of(CROP_ACCELERATION, TREE_ACCELERATION, ANIMAL_ACCELERATION,
                MACHINE_ACCELERATION, BLOCK_ENTITY_ACCELERATION, LOCAL_TIME_ACCELERATION).contains(axis) && measurement.magnitude() > 1;
        if (unit.equals("blocks") || unit.endsWith("radius_blocks")) return Set.of(SPAWN_SUPPRESSION, MOB_REPULSION, HAZARD_SUPPRESSION).contains(axis);
        if (unit.equals("targets") || unit.equals("count")) return Set.of(AREA_MINING, VEIN_MINING).contains(axis) && measurement.magnitude() > 1;
        return unit.equals("presence") && Set.of(FLIGHT, GLIDING, TELEPORTATION, AUTOMATED_EXTRACTION,
                AUTOMATED_FARMING, AUTOMATED_FISHING, PASSIVE_GENERATION, SPAWN_SUPPRESSION, MOB_REPULSION,
                HAZARD_SUPPRESSION, CROP_ACCELERATION, TREE_ACCELERATION, ANIMAL_ACCELERATION,
                MACHINE_ACCELERATION, BLOCK_ENTITY_ACCELERATION, LOCAL_TIME_ACCELERATION, INFORMATION).contains(axis);
    }
    private double adapt(String feature, ProgressionBand band, String path, double baseline, double minimum, double ceiling, boolean prevention, boolean inverse) {
        var pressure = pressure(feature, band);
        double target = pressure.factor == 1 ? baseline : prevention
                ? ceiling - (ceiling - baseline) / pressure.factor : inverse ? baseline / pressure.factor : Math.max(minimum, baseline) * pressure.factor;
        double result = Math.clamp(target, minimum, ceiling);
        record(feature, band, path, baseline, target, result, pressure, result != target ? "native mechanic limit" : "none");
        return result;
    }
    private void record(String feature, ProgressionBand band, String path, double baseline, double target, double value, Pressure pressure, String limit) {
        JsonObject row = new JsonObject(); row.addProperty("feature", feature); row.addProperty("path", path); row.addProperty("band", band.name());
        row.addProperty("baseline", baseline); row.addProperty("adaptiveTarget", target); row.addProperty("generatedValue", value); row.addProperty("limit", limit);
        row.addProperty("factor", pressure.factor);
        if (pressure.reference != null) {
            var ref = pressure.reference; row.addProperty("capability", ref.measurement.family() + "/" + ref.measurement.axis());
            row.addProperty("externalFrontier", ref.frontier.magnitude()); row.addProperty("unit", ref.measurement.unit());
            row.addProperty("relationship", pressure.link.relationship.name()); row.addProperty("share", pressure.link.share);
            row.addProperty("confidence", ref.confidence); row.addProperty("robustCap", ref.frontier.capped());
            row.addProperty("measuredMaximum", ref.frontier.measuredMaximum()); row.addProperty("comparisonKey", ref.frontier.comparisonKey());
            row.add("sources", BalanceDocument.GSON.toJsonTree(ref.sources)); row.add("scope", BalanceDocument.GSON.toJsonTree(ref.measurement.scope()));
            row.add("operation", BalanceDocument.GSON.toJsonTree(ref.measurement.operation())); row.addProperty("applicability", ref.measurement.applicability());
        } else { row.addProperty("capability", "none admitted"); row.addProperty("relationship", "COMPLEMENTARY"); row.addProperty("confidence", 0); row.add("sources", new JsonArray()); }
        rows.putIfAbsent(path + "/" + band, row);
    }
    public void equipment(Map<ResourceLocation, EquipmentBaselineConfig.TierBaseline> equipment) {
        long start = startTiming();
        JsonObject previous = null;
        for (var tier : AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList()) {
            if (!tier.grantsPower()) { previous = BalanceDocument.GSON.toJsonTree(equipment.get(tier.id())).getAsJsonObject(); continue; }
            JsonObject row = BalanceDocument.GSON.toJsonTree(equipment.get(tier.id())).getAsJsonObject();
            var band = band(tier.id());
            for (String field : List.of("fullSetArmor", "fullSetToughness", "meleeDamage", "rangedDamage", "magicDamage", "miningSpeed", "durability", "harvestLevel")) {
                double base = row.get(field).getAsDouble();
                double limit = field.equals("durability") ? Integer.MAX_VALUE / 64 : field.equals("harvestLevel") ? 32 : Float.MAX_VALUE / 1024.0;
                double value = adapt("equipment." + field, band, "equipment/" + tier.id() + "/" + field, base,
                        base == 0 && factor("equipment." + field, band) > 1 ? 1 : base, limit, false, false);
                if (field.equals("harvestLevel")) {
                    // Discrete access follows admitted levels, not a multiplier of an ordinal.
                    value = base;
                    for (var ref : references.getOrDefault(band, Map.of()).getOrDefault(HARVEST_LEVEL, List.of()))
                        if (ref.measurement.unit().equals("native_harvest_level")) value = Math.max(value, Math.min(32, Math.floor(ref.frontier.magnitude())));
                    // The requested target itself is discrete; report no invented ordinal multiplier.
                    var decision = rows.get("equipment/" + tier.id() + "/" + field + "/" + band);
                    decision.addProperty("adaptiveTarget", value); decision.addProperty("generatedValue", value);
                    decision.addProperty("limit", "discrete native harvest access; maximum 32");
                }
                if (previous != null) value = Math.max(value, previous.get(field).getAsDouble());
                if (field.equals("durability") || field.equals("harvestLevel")) value = Math.floor(value);
                row.addProperty(field, value);
            }
            equipment.put(tier.id(), BalanceDocument.GSON.fromJson(row, EquipmentBaselineConfig.TierBaseline.class)); previous = row;
        }
        endTiming(start);
    }
    public SkillEffectBalanceSettings skills(SkillEffectBalanceSettings baseline) {
        long start = startTiming(); JsonObject effects = BalanceDocument.GSON.toJsonTree(baseline).getAsJsonObject();
        for (var skill : SkillRegistry.values()) {
            String feature = "skill." + skill.id().getPath();
            if (!links.containsKey(feature)) continue;
            var band = band(skill.requiredTierId());
            for (var outcome : skill.progressionRequirements().outcomes()) {
                double base = ProgressionRequirements.read(effects, outcome.path());
                boolean prevention = outcome.maximum() == 1 && outcome.scale() > 0;
                double value = adapt(feature, band, "effects/" + outcome.path(), base, outcome.minimum(), outcome.maximum(), prevention, outcome.scale() < 0);
                if (outcome.quantum() == 1) value = Math.max(outcome.minimum(), Math.floor(value));
                ProgressionRequirements.write(effects, outcome.path(), value);
            }
            // Pulse-based throughput receives shorter bounded player pulses as probabilities saturate.
            String pulse = skill.id().equals(SkillIds.VERDANT_STRIDE) ? "gathering/verdantStride/growthPulseTicks"
                    : skill.id().equals(SkillIds.POCKET_NETS) ? "gathering/pocketNets/pulseTicks" : null;
            if (pulse != null) {
                double base = ProgressionRequirements.read(effects, pulse);
                ProgressionRequirements.write(effects, pulse, Math.max(20, Math.floor(adapt(feature, band, "effects/" + pulse, base, 20, 72_000, false, true))));
            }
            if (skill.id().equals(SkillIds.VERDANT_STRIDE)) {
                double nativeChance = references.getOrDefault(band, Map.of()).getOrDefault(CROP_ACCELERATION, List.of()).stream()
                        .filter(ref -> ref.measurement.unit().equals("bonemeal_growth_chance_per_action"))
                        .mapToDouble(ref -> ref.frontier.magnitude()).max().orElse(0);
                if (nativeChance > 0) {
                    var growth = effects.getAsJsonObject("gathering").getAsJsonObject("verdantStride");
                    growth.addProperty("boneMealGrowth", true);
                    // One bounded passive growth action/second is an explicit design scenario,
                    // not an inferred crouch cadence or a claim of equal sustained throughput.
                    growth.addProperty("growthPulseTicks", 20);
                    growth.addProperty("growthChance", Math.min(1, Math.max(nativeChance, growth.get("growthChance").getAsDouble())));
                    warnings.add("Verdant Stride uses native bonemeal growth with one bounded action/second as a calibration scenario; competitor crouch cadence and sustained uptime remain unmeasured.");
                }
            }
        }
        endTiming(start);
        var result = BalanceDocument.GSON.fromJson(effects, SkillEffectBalanceSettings.class); result.validate(); return result;
    }
    public double bonusMaximum(StatDefinition stat, double baseline) {
        long start = startTiming();
        var semantic = BonusSemantics.require(stat);
        // Step traversal uses measured native boundaries. It is complementary to generic speed pressure.
        if (semantic.response() == BonusSemantics.Response.STEP_BOUNDARY) { endTiming(start); return baseline; }
        boolean prevention = semantic.response() == BonusSemantics.Response.PREVENTION;
        double ceiling = prevention ? 90 : 1_000_000;
        if (stat.id().getPath().equals("max_health"))
            ceiling = Math.min(ceiling, Math.max(0, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH.value()
                    .sanitizeValue(Double.MAX_VALUE) - RuntimeReferencePolicy.playerHealth()) / 2);
        double result = adapt("bonus." + stat.id().getPath(), ProgressionBand.APEX, "statMaxBonuses/" + stat.id(), baseline, 0,
                ceiling, prevention, false);
        endTiming(start); return result;
    }
    /** Ratio removes apex pressure from early checkpoints while retaining the final calibrated maximum. */
    public double bonusFraction(StatDefinition stat, ProgressionBand band, double fraction) {
        double apex = factor("bonus." + stat.id().getPath(), ProgressionBand.APEX);
        double local = factor("bonus." + stat.id().getPath(), band);
        if (apex == 1 || BonusSemantics.require(stat).response() == BonusSemantics.Response.STEP_BOUNDARY) return fraction;
        // Prevention uses remaining-loss response, so use the actual native target ratio rather than factor alone.
        var row = rows.get("statMaxBonuses/" + stat.id() + "/APEX");
        double base = row == null ? 0 : row.get("baseline").getAsDouble();
        double target = row == null ? 0 : row.get("generatedValue").getAsDouble();
        double ratio = local / apex;
        if (BonusSemantics.require(stat).response() == BonusSemantics.Response.PREVENTION && target > 0)
            ratio = (90 - (90 - base) / local) / target;
        return band == ProgressionBand.APEX ? fraction : fraction * Math.min(1, ratio);
    }
    public boolean localized(StatDefinition stat) { return BonusSemantics.require(stat).response() != BonusSemantics.Response.STEP_BOUNDARY
            && factor("bonus." + stat.id().getPath(), ProgressionBand.APEX)
            > factor("bonus." + stat.id().getPath(), ProgressionBand.ENTRY) + 1e-9; }
    public void bonusCheckpoint(StatDefinition stat, ProgressionBand band, double originalFraction, double finalValue) {
        long start = startTiming();
        var endpoint = rows.get("statMaxBonuses/" + stat.id() + "/APEX");
        if (endpoint == null) { endTiming(start); return; } // neutral compatibility overload
        double baseline = endpoint.get("baseline").getAsDouble() * originalFraction;
        record("bonus." + stat.id().getPath(), band, "bonusCheckpoints/" + stat.id(), baseline, finalValue, finalValue,
                pressure("bonus." + stat.id().getPath(), band), "endpoint/native headroom; local progression fraction");
        endTiming(start);
    }
    /** Freeze generic combat references before exact overrides. A weapon winner retains only its
     * own witnessed cadence; if absent, use the existing encounter cadence as a modeling assumption. */
    public void combatReferences(Map<String, Double> composition) {
        long start = startTiming();
        for (var band : ProgressionBand.values()) {
            for (String family : List.of("melee_shield", "ranged", "caster")) {
                double baseRate = Math.max(.1, evidence.reference(band, ATTACK_RATE, 1));
                double baseDps = Math.max(1, evidence.reference(band, SUSTAINED_DAMAGE, 1));
                var old = new RuntimeReferencePolicy.Weapon(baseDps / baseRate, baseRate, baseDps / baseRate, false);
                double damage = old.damage(), rate = old.rate(), best = old.dps();
                CapabilityAxis axis = family.equals("ranged") ? RANGED_DAMAGE : family.equals("caster") ? MAGIC_DAMAGE : MELEE_DAMAGE;
                for (var candidateAxis : List.of(axis, SUSTAINED_DAMAGE, BURST_DAMAGE)) {
                    // Generic burst applies to melee; magic/ranged need explicit damage family or sustained evidence.
                    if (candidateAxis == BURST_DAMAGE && !family.equals("melee_shield")) continue;
                    for (var ref : references.getOrDefault(band, Map.of()).getOrDefault(candidateAxis, List.of())) {
                        if (!compatible("equipment." + (family.equals("ranged") ? "rangedDamage" : family.equals("caster") ? "magicDamage" : "meleeDamage"), ref.measurement)) continue;
                        if (ref.measurement.applicability().contains("conditional=")) continue;
                        double witnessRate = old.rate();
                        for (var m : ref.witness.measurements())
                            if (m.axis() == ATTACK_RATE && m.unit().equals("attacks_per_second") && m.magnitude() != null)
                                witnessRate = Math.max(.1, m.magnitude());
                        double witnessDamage;
                        if (ref.measurement.unit().equals("health_points")) witnessDamage = ref.frontier.magnitude();
                        else if (ref.measurement.unit().equals("health_points_per_second")) witnessDamage = ref.frontier.magnitude() / witnessRate;
                        else continue;
                        if (witnessDamage * witnessRate > best) {
                            damage = Math.min(Float.MAX_VALUE / 1024.0, witnessDamage); rate = Math.min(1024, witnessRate); best = damage * rate;
                        }
                    }
                }
                if (best > old.dps()) {
                    composition.put("competitive_" + band + "_" + family + "_damage", damage);
                    composition.put("competitive_" + band + "_" + family + "_rate", rate);
                }
            }
            double health = 20;
            for (var axis : List.of(MAX_HEALTH, SHIELD_CAPACITY))
                for (var ref : references.getOrDefault(band, Map.of()).getOrDefault(axis, List.of()))
                    if (ref.measurement.unit().equals("health_points")) health = Math.max(health, Math.min(1_000_000, ref.frontier.magnitude()));
                    else if (axis == MAX_HEALTH && ref.measurement.unit().equals("attribute_addition")) health = Math.max(health,
                            Math.min(1_000_000, 20 + ref.frontier.magnitude()));
            if (health > 20) composition.put("competitive_" + band + "_health", health);
            // Alternative survival envelopes retain one witnessed armor/toughness pair.
            // Effective health never becomes raw health, and independent configurations are never composed.
            double ehp = 20;
            double incoming = composition.getOrDefault("enemy_damage_" + band.name().toLowerCase(Locale.ROOT), 4.0);
            for (var axis : List.of(ARMOR, TOUGHNESS, DAMAGE_REDUCTION, EFFECTIVE_HEALTH))
                for (var ref : references.getOrDefault(band, Map.of()).getOrDefault(axis, List.of())) {
                    if (ref.measurement.applicability().contains("conditional=")) continue;
                    double candidate = 20, magnitude = ref.frontier.magnitude(); String unit = ref.measurement.unit();
                    if (axis == EFFECTIVE_HEALTH && (unit.equals("native_effective_health") || unit.equals("health_points"))) candidate = magnitude;
                    else if (axis == DAMAGE_REDUCTION && unit.equals("fraction")) candidate = 20 / Math.max(.01, 1 - Math.min(.99, magnitude));
                    else if (axis == DAMAGE_REDUCTION && unit.startsWith("enchantment_protection_points:")) candidate = 20 / Math.max(.2, 1 - Math.min(20, magnitude) / 25);
                    else if ((axis == ARMOR || axis == TOUGHNESS) && (unit.equals("native_armor") || unit.equals("native_toughness") || unit.equals("attribute_addition"))) {
                        double a = axis == ARMOR ? magnitude : 0, t = axis == TOUGHNESS ? magnitude : 0;
                        for (var m : ref.witness.measurements()) {
                            if (m.magnitude() == null) continue;
                            if (axis == ARMOR && m.axis() == TOUGHNESS && m.unit().equals("native_toughness")) t = m.magnitude();
                            if (axis == TOUGHNESS && m.axis() == ARMOR && m.unit().equals("native_armor")) a = m.magnitude();
                        }
                        candidate = 20 / BuildComposition.armorDamageFraction(incoming, a, t);
                    }
                    ehp = Math.max(ehp, Math.min(Float.MAX_VALUE / 1024.0, candidate));
                }
            if (ehp > 20) composition.put("competitive_" + band + "_effective_health", ehp);
        }
        endTiming(start);
    }
    /** Final persisted values are read after all existing safeguards, floors, quantization and exact overrides. */
    public JsonObject complete(RuntimeBalanceDefinition runtime) {
        long start = startTiming(); JsonObject values = runtime.toJson();
        var finalEffects = values.getAsJsonObject("effects");
        for (var skill : SkillRegistry.values()) {
            var decision = availability.get(skill.id().toString()); if (decision == null) continue;
            var curve = runtime.skillCurves().get(skill.id().toString());
            var handler = com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry.get(skill.id());
            decision.addProperty("implementation", handler == null ? "UNSUPPORTED: no registered handler" : handler.getClass().getName());
            decision.addProperty("generatedTier", curve.requiredTierId().toString());
            var publishedRanks = new JsonArray();
            for (var rank : curve.ranks()) {
                var state = finalEffects.deepCopy(); rank.parameters().forEach((path, value) -> ProgressionRequirements.write(state, path, value));
                var row = new JsonObject();
                if (decision.has("rankDecisions") && decision.getAsJsonArray("rankDecisions").size() >= rank.rank())
                    row = decision.getAsJsonArray("rankDecisions").get(rank.rank() - 1).getAsJsonObject().deepCopy();
                row.addProperty("rank", rank.rank()); row.addProperty("publishedPrice", rank.cost());
                row.addProperty("requiredTier", SkillProgressionGraph.gated(skill, curve.requiredTierId(), rank.rank()).toString());
                row.add("prerequisites", BalanceDocument.GSON.toJsonTree(skill.prerequisiteRanks(rank.rank())));
                row.add("requirements", BalanceDocument.GSON.toJsonTree(skill.requirements(rank.rank()).stream().map(r -> r.id().toString()).toList()));
                row.add("parameters", BalanceDocument.GSON.toJsonTree(rank.parameters()));
                var outcomes = new JsonObject();
                for (var outcome : skill.progressionRequirements().outcomes()) outcomes.addProperty(outcome.path(), outcome.measure(state));
                row.add("measuredOutcomes", outcomes);
                var mechanics = new JsonObject();
                for (String path : rank.parameters().keySet()) {
                    String parent = path.substring(0, path.lastIndexOf('/'));
                    JsonElement section = state; for (String part : parent.split("/")) section = section.getAsJsonObject().get(part);
                    mechanics.add(parent, section.deepCopy());
                }
                row.add("nativeMechanics", mechanics); publishedRanks.add(row);
            }
            decision.add("rankDecisions", publishedRanks);
        }
        for (var row : rows.values()) {
            String path = row.get("path").getAsString(); double value;
            if (path.startsWith("statMaxBonuses/") || path.startsWith("bonusCheckpoints/")) {
                var id = ResourceLocation.parse(path.substring(path.indexOf('/') + 1));
                var tier = AscendanceTierRegistry.powerTiers().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList()
                        .get(ProgressionBand.valueOf(row.get("band").getAsString()).ordinal());
                var track = runtime.config().balanceProfile().bonusTrack(id);
                value = track.maximumEffect() * track.checkpoint(tier.id()).effectFraction();
            } else value = ProgressionRequirements.read(values, path);
            row.addProperty("finalGeneratedValue", value);
            if (Math.abs(value - row.get("generatedValue").getAsDouble()) > 1e-6)
                row.addProperty("limit", row.get("limit").getAsString() + "; final composition/native grid/meaningful publication or exact override");
        }
        endTiming(start);
        BalancePerformance.increment("adaptive_calibration_runs"); BalancePerformance.count("adaptive_calibration_frontiers", references.values().stream()
                .flatMap(m -> m.values().stream()).mapToLong(List::size).sum());
        JsonObject report = new JsonObject(); report.addProperty("contract", "adaptive-competition-1");
        report.addProperty("policy", "Generic substitutes use maximum pressure, allocated sibling shares and diminishing log elasticity. Skills use unlock bands; equipment/Bonuses use local bands. Mechanical floors, native caps, composition validation and exact overrides remain authoritative.");
        report.addProperty("combatReferencePolicy", "Physical weapon winners retain witnessed cadence or use the compact band cadence as an explicit encounter assumption. Survival envelopes retain witnessed armor pairs; EHP is not raw health. Different configurations are alternatives, never summed or assembled into one measured build.");
        report.addProperty("calibrationNanos", nanos); report.add("rows", BalanceDocument.GSON.toJsonTree(rows.values()));
        report.add("warnings", BalanceDocument.GSON.toJsonTree(warnings)); report.add("relationships", BalanceDocument.GSON.toJsonTree(links));
        report.add("skillAvailability", BalanceDocument.GSON.toJsonTree(availability.values()));
        JsonObject coverage = new JsonObject(); coverage.addProperty("scopedCandidateCoverageAvailable", scopedCoverageAvailable);
        coverage.addProperty("diagnosticEvidenceReadFailures", diagnosticReadFailures);
        coverage.addProperty("factualMeasurementsProjected", availabilityObservations.size());
        coverage.addProperty("unscopedCandidateSamplesGlobal", unscopedCandidateSamples);
        coverage.add("unscopedCandidateReasonCountsGlobal", unscopedCandidateReasons.deepCopy());
        coverage.addProperty("policy", "Skill decisions explain retained bounds and captured evidence; PARTIAL/UNKNOWN coverage never certifies that all earlier alternatives were checked. Diagnostics do not alter tier, effect or price selection.");
        report.add("skillAvailabilityCoverage", coverage);
        return report;
    }
}

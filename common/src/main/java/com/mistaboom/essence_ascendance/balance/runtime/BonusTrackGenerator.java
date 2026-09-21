package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillCapabilityRoutes;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Resolves semantic marginal utility against reachable pack evidence and the existing tier economy. */
public final class BonusTrackGenerator {
    private BonusTrackGenerator() {}
    /** Final local publication. Allocation, desirability and prices were resolved before floor grants. */
    public static RuntimeBalanceDefinition publishMeaningful(RuntimeBalanceDefinition nominal) {
        return publishMeaningful(nominal, Map.of());
    }
    public static RuntimeBalanceDefinition publishMeaningful(RuntimeBalanceDefinition nominal,
            Map<ResourceLocation, com.mistaboom.essence_ascendance.skill.ProgressionRequirements.Bonus> requirements) {
        var json = nominal.toJson();
        var tracks = new TreeMap<ResourceLocation, BonusTrackDefinition>();
        for (var stat : EssenceStatRegistry.values()) {
            var track = nominal.config().balanceProfile().bonusTracks().get(stat.id());
            tracks.put(stat.id(), meaningful(track, requirements.getOrDefault(stat.id(), track.progressionRequirements())));
        }
        json.getAsJsonObject("balanceProfile").add("bonusTracks", toJson(tracks));
        for (var entry : tracks.entrySet()) {
            json.getAsJsonObject("statMaxBonuses").addProperty(entry.getKey().toString(), entry.getValue().maximumEffect());
            var caps = json.getAsJsonObject("balanceProfile").getAsJsonObject("statOverrides").getAsJsonObject(entry.getKey().toString());
            entry.getValue().checkpoints().forEach(point -> caps.addProperty(point.tierId().toString(), point.cumulativeCap()));
        }
        return RuntimeBalanceDefinition.fromJson(json);
    }
    public static BonusTrackDefinition meaningful(BonusTrackDefinition nominal,
            com.mistaboom.essence_ascendance.skill.ProgressionRequirements.Bonus requirements) {
        if (nominal.applicability() == BonusTrackDefinition.Applicability.UNAVAILABLE) return nominal;
        var tiers = AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        var points = nominal.checkpoints().stream().filter(BonusTrackDefinition.Checkpoint::purchasable).toList();
        boolean nativeStep = nominal.inputs().containsKey("native_base_step_height");
        double quantum = nativeStep ? .000001 : RuntimeValueQuantization.statStep(nominal.unit());
        double ceiling = nominal.inputs().getOrDefault("native_maximum_effect", 1_000_000.0);
        var candidates = points.stream().map(p -> {
            double value = p.effectFraction() * nominal.maximumEffect();
            return nativeStep ? Math.rint(value * 1_000_000) / 1_000_000 : value;
        }).toList();
        var values = new ArrayList<>(com.mistaboom.essence_ascendance.progression.MeaningfulProgression.select(
                candidates, requirements.firstStateFloor(), requirements.tierImprovementFloor(), quantum, ceiling));
        // Route evidence remains a hard lower bound; optional bonuses never become skill prerequisites.
        int earliest = tiers.indexOf(AscendanceTierRegistry.powerTiers().stream()
                .min(Comparator.comparingInt(AscendanceTierDefinition::order)).orElseThrow());
        if (nominal.compatibilityRequirements().contains("ABILITIES_FLYING_SPEED")
                || nominal.compatibilityRequirements().contains("native menu cost hook"))
            earliest = tiers.indexOf(AscendanceTierRegistry.get(nominal.startTier()).orElseThrow());
        double desirability = nominal.inputs().getOrDefault("native_response", 0.0);
        double breadth = nominal.inputs().getOrDefault("breadth", 1.0);
        double score = breadth == 1 ? 0 : desirability / (Math.log(2) + desirability);
        int firstPurchasable = earliest;
        int start = Math.max(earliest, firstPurchasable + (int) Math.ceil((tiers.size() - firstPurchasable - values.size()) * score));
        if (nominal.inputs().containsKey("native_base_step_height")) start = earliest;
        start = Math.min(tiers.size() - 1, start);
        // A late native route can offer fewer states. Retain the first and endpoint without filler subdivision.
        int room = tiers.size() - start;
        while (values.size() > room) values.remove(values.size() == 2 ? 0 : values.size() - 2);
        int end = start + values.size() - 1;
        double maximum = values.getLast();
        var checkpoints = new ArrayList<BonusTrackDefinition.Checkpoint>();
        var snaps = new ArrayList<Double>(); snaps.add(0.0);
        long cumulative = 0; double fraction = 0;
        for (int index = 0; index < tiers.size(); index++) {
            long segment = 0;
            if (index >= start && index <= end) {
                int state = index - start;
                fraction = state == values.size() - 1 ? 1.0 : values.get(state) / maximum;
                // Nominal cost schedule is independent of any floor overage or final published effect.
                long cost = points.get(Math.min(state, points.size() - 1)).segmentCost();
                segment = Math.max(1, cost);
                cumulative = Math.addExact(cumulative, segment);
                snaps.add(fraction);
            }
            checkpoints.add(new BonusTrackDefinition.Checkpoint(tiers.get(index).id(), cumulative, segment,
                    fraction, index >= start, segment > 0));
        }
        var inputs = new TreeMap<>(nominal.inputs());
        inputs.keySet().removeIf(key -> key.startsWith("native_threshold_"));
        inputs.put("first_state_floor", requirements.firstStateFloor());
        inputs.put("later_state_floor", requirements.tierImprovementFloor());
        inputs.put("nominal_endpoint", nominal.maximumEffect());
        inputs.put("placement_desirability", score);
        var reasons = new ArrayList<>(nominal.evidence());
        reasons.removeIf(reason -> reason.contains("intervening investment and applied effect remain continuous"));
        reasons.add("Complete native states; first floor is local ignored overage. Later quantized candidates must meet the improvement floor; larger changes remain intact. Contiguous route-compatible placement uses nominal desirability and state count.");
        return new BonusTrackDefinition(nominal.statId(), nominal.category(), nominal.unit(), maximum,
                tiers.get(start).id(), tiers.get(end).id(), checkpoints, nominal.investmentExponent(),
                BonusTrackDefinition.PurchaseStyle.FUNDED_STATES, snaps, nominal.applicability(),
                nominal.compatibilityRequirements(), nominal.confidence(), reasons, nominal.source(), inputs);
    }
    public static Map<ResourceLocation, BonusTrackDefinition> resolve(PackEvidence evidence, BalanceSettings settings,
            Map<ResourceLocation, Long> tierCaps, Map<ResourceLocation, Double> tierFractions,
            Map<ResourceLocation, Double> maxima, Map<String, Double> categorySupply, double exponent) {
        var allTiers = AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        var tiers = allTiers.stream().filter(AscendanceTierDefinition::grantsPower).toList();
        Map<ResourceLocation, BonusTrackDefinition> result = new TreeMap<>();
        for (var stat : EssenceStatRegistry.values()) {
            var semantic = BonusSemantics.require(stat);
            List<String> reasons = new ArrayList<>();
            reasons.add("Native mechanic: " + semantic.nativeMechanism() + "; conditions=" + semantic.conditions());
            int start = 0, end = tiers.size() - 1;
            double maximum = maxima.get(stat.id());
            if (stat.id().equals(EssenceStats.SWIM_SPEED.id()))
                maximum = Math.min(maximum, NativeBonusMechanics.additivePercentHeadroom(Attributes.WATER_MOVEMENT_EFFICIENCY));
            double confidence = .75;
            String source = "native_semantics+reachable_environment";
            List<String> compatibility = List.of();
            boolean available = maximum > 0;
            if (semantic.axis() == CapabilityAxis.ANVIL_EFFICIENCY || semantic.axis() == CapabilityAxis.ENCHANTING_EFFICIENCY) {
                Route route = menuRoute(evidence, semantic.axis());
                reasons.addAll(route.evidence());
                available &= route.stage() != null;
                start = route.stage() == null ? end : Math.min(end, route.stage().ordinal());
                confidence = route.confidence(); source = route.source();
                compatibility = List.of("native menu cost hook", semantic.axis().name());
            } else if (!stat.id().equals(EssenceStats.FLIGHT_SPEED.id())) {
                reasons.add("Native action/attribute has an intrinsic entry-stage route; contextual use changes breadth, not the player's permanent catalog. Registered Ascendance equipment supplies its own activation route");
            }
            if (stat.id().equals(EssenceStats.FLIGHT_SPEED.id())) {
                compatibility = List.of("FLIGHT", "ABILITIES_FLYING_SPEED", "GLIDING alone is incompatible");
                Route route = flightRoute(evidence, true);
                reasons.addAll(route.evidence());
                available &= route.stage() != null && settings.flightPolicy() != BalanceSettings.FlightPolicy.RESTRICT;
                start = route.stage() == null ? tiers.size() - 1 : Math.min(end, route.stage().ordinal());
                confidence = route.confidence(); source = route.source();
            }
            List<Double> thresholds = List.of();
            double baseStep = 0;
            if (semantic.response() == BonusSemantics.Response.STEP_BOUNDARY) {
                var nativeStep = NativeBonusMechanics.step(evidence, maximum);
                baseStep = nativeStep.base();
                thresholds = nativeStep.thresholds();
                available &= !thresholds.isEmpty();
                maximum = thresholds.isEmpty() ? 0 : thresholds.getLast();
                // Meaningful traversal boundaries determine useful segment endpoints, not purchase gates.
                end = Math.min(end, Math.max(0, thresholds.size() - 1));
                reasons.addAll(nativeStep.evidence());
                reasons.add("Native base step height=" + baseStep + "; resolved traversal boundary additions=" + thresholds);
                reasons.add("Coalesced meaningful collision boundaries set the useful checkpoints and completion; intervening investment and applied effect remain continuous, with no snapping. Jump remains an available alternative");
                source = nativeStep.source(); confidence = nativeStep.confidence();
            }
            double marginal = response(semantic, maximum, thresholds.size());
            double breadth = 1.0 / (1 + semantic.conditions().size());
            double alternative = alternative(evidence, semantic.axis(), reasons);
            double span = (end - start + 1.0) / tiers.size();
            double headroom = 1.0 / (1 + settings.compositionSafeguard() * Math.max(0, marginal));
            double intrinsic = available ? Math.max(.01, marginal * breadth * alternative * Math.sqrt(span) * headroom) : 0;
            // Shared dimensionless exchange anchor: a 100% broadly applicable multiplier is one utility unit.
            double utility = intrinsic / Math.log(2);
            double economy = categorySupply.getOrDefault(stat.essenceType().id().toString(), 1.0);
            Map<String, Double> inputs = new TreeMap<>();
            inputs.put("native_response", marginal); inputs.put("breadth", breadth); inputs.put("environment_alternatives", alternative);
            inputs.put("useful_span", span); inputs.put("composition_headroom", headroom); inputs.put("marginal_power", intrinsic);
            inputs.put("category_supply", economy); inputs.put("utility_price_multiplier", utility);
            if (stat.id().equals(EssenceStats.SWIM_SPEED.id())) inputs.put("native_maximum_effect",
                    NativeBonusMechanics.additivePercentHeadroom(Attributes.WATER_MOVEMENT_EFFICIENCY));
            if (baseStep > 0) inputs.put("native_base_step_height", baseStep);
            for (int index = 0; index < thresholds.size(); index++) inputs.put("native_threshold_" + (index + 1), thresholds.get(index));
            List<Double> thresholdFractions = new ArrayList<>();
            if (!thresholds.isEmpty()) {
                thresholdFractions.add(0.0);
                for (double threshold : thresholds) thresholdFractions.add(threshold == maximum ? 1.0 : threshold / maximum);
            }
            List<BonusTrackDefinition.Checkpoint> checkpoints = new ArrayList<>();
            long cumulative = 0; double priorFraction = 0;
            double lower = start == 0 ? 0 : tierFractions.get(tiers.get(start - 1).id());
            double upper = tierFractions.get(tiers.get(end).id());
            for (var tier : allTiers) {
                int index = tiers.indexOf(tier);
                long segment = 0; double fraction = priorFraction;
                boolean unlocked = available && index >= start;
                if (unlocked && index <= end) {
                    fraction = thresholds.isEmpty() ? Math.clamp((tierFractions.get(tier.id()) - lower) / (upper - lower), 0, 1)
                            : thresholdFractions.get(Math.min(thresholdFractions.size() - 1, index - start + 1));
                    if (index == end) fraction = 1;
                    long priorEconomic = index == 0 ? 0 : tierCaps.get(tiers.get(index - 1).id());
                    // Adjacent caps preserve every previous tier's economic growth. Delayed tracks never pay locked segments.
                    segment = fraction > priorFraction ? safeCost((tierCaps.get(tier.id()) - priorEconomic) * economy * utility) : 0;
                    cumulative = Math.addExact(cumulative, segment);
                }
                checkpoints.add(new BonusTrackDefinition.Checkpoint(tier.id(), cumulative, segment, fraction, unlocked, segment > 0));
                priorFraction = fraction;
            }
            reasons.add("Price = adjacent category-economy segment × normalized native response × breadth × alternatives × useful-span × composition headroom");
            reasons.add("Marginal power is price-independent for normalized Bonus Attunement; assumptions are modeling estimates, not measured player uptime");
            result.put(stat.id(), new BonusTrackDefinition(stat.id(), stat.category(), stat.unit(), maximum,
                    tiers.get(start).id(), tiers.get(end).id(), checkpoints, exponent,
                    BonusTrackDefinition.PurchaseStyle.CONTINUOUS,
                    List.of(), available ? BonusTrackDefinition.Applicability.AVAILABLE : BonusTrackDefinition.Applicability.UNAVAILABLE,
                    compatibility, confidence, reasons, source, inputs));
        }
        return Collections.unmodifiableMap(result);
    }
    private static long safeCost(double value) {
        if (!Double.isFinite(value) || value > Long.MAX_VALUE / 100_000.0) throw new IllegalArgumentException("Bonus cost exceeds safe accounting bounds");
        return Math.max(1, Math.round(value));
    }
    static double nativeStepBase() {
        return RuntimeReferencePolicy.usingBootstrapReferences() ? Attributes.STEP_HEIGHT.value().getDefaultValue()
                : Player.createAttributes().build().getValue(Attributes.STEP_HEIGHT);
    }
    private static double response(BonusSemantics.Mechanic semantic, double maximum, int thresholds) {
        return switch (semantic.response()) {
            case MULTIPLIER -> Math.log1p(maximum / 100) * (semantic.axis() == CapabilityAxis.JUMP ? 2 : 1);
            case PREVENTION -> -Math.log(Math.max(.01, 1 - maximum / 100));
            case HEALTH -> Math.log1p(maximum * 2 / RuntimeReferencePolicy.playerHealth());
            case RECOVERY -> Math.log1p(maximum * 2 / RuntimeReferencePolicy.playerHealth());
            case AIR -> Math.log1p(maximum / 15); // Native 300 air ticks / 20 ticks per second.
            case REACH_DISTANCE -> Math.log1p(maximum / Attributes.BLOCK_INTERACTION_RANGE.value().getDefaultValue());
            case STEP_BOUNDARY -> maximum <= 0 ? 0 : Math.log1p(maximum / (nativeStepBase() + maximum));
            case YIELD_LEVEL -> Math.log1p(maximum); // Fractional yields already use expectation, not integer-only purchase gates.
            case LUCK -> Math.log1p(maximum) / 2; // Quality-sensitive pools only; opaque loot frequency remains uncertain.
        };
    }
    private static double alternative(PackEvidence evidence, CapabilityAxis axis, List<String> reasons) {
        long population = evidence.equipment().stream().filter(e -> e.included() && e.reachable()).count();
        long alternatives = evidence.equipment().stream().filter(e -> e.included() && e.reachable() && e.axes().getOrDefault(axis, 0.0) > 0).count();
        // Equipment is an alternative, not a prerequisite, except for explicit mechanism compatibility.
        double coverage = population == 0 ? 0 : alternatives / (double) population;
        reasons.add("Reachable included equipment covering " + axis + ": " + alternatives + "/" + population
                + "; no observations means neutral alternatives fallback, not invented availability");
        return 1 / (1 + coverage);
    }
    public record Route(ProgressionBand stage, double confidence, String source, List<String> evidence) {}
    private static Route menuRoute(PackEvidence evidence, CapabilityAxis axis) {
        List<CapabilityEvidence> routes = new ArrayList<>();
        Set<String> nativeMenus = new HashSet<>();
        for (var capability : evidence.capabilities()) if (capability.axes().getOrDefault(axis, 0.0) > 0) {
            var resource = evidence.resources().get(capability.subjectId());
            var stage = resource == null || resource.stage().ordinal() < capability.stage().ordinal() ? capability.stage() : resource.stage();
            routes.add(new CapabilityEvidence(capability.subjectId(), stage, capability.axes(),
                    capability.reachable() && (resource == null || resource.reachable()),
                    resource == null ? capability.confidence() : Math.min(capability.confidence(), resource.confidence()), capability.reason()));
        }
        // Registered native block classes prove the menu mechanic; a modded item name never does.
        for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            boolean matches = axis == CapabilityAxis.ANVIL_EFFICIENCY
                    ? block instanceof net.minecraft.world.level.block.AnvilBlock
                    : block == net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE;
            if (!matches) continue;
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(block.asItem()).toString();
            var resource = evidence.resources().get(id);
            if (resource != null) {
                nativeMenus.add(id);
                routes.add(new CapabilityEvidence(id, resource.stage(), Map.of(axis, 1.0),
                        resource.reachable(), resource.confidence(), "Registered native menu block with acquisition stage"));
            }
        }
        var selected = routes.stream().filter(route -> route.reachable() && (nativeMenus.contains(route.subjectId()) || route.confidence() >= .6))
                .min(Comparator.comparing(CapabilityEvidence::stage).thenComparing(CapabilityEvidence::subjectId));
        if (selected.isPresent()) {
            var route = selected.orElseThrow();
            return new Route(route.stage(), route.confidence(), "native_menu+acquisition", List.of(route.subjectId()
                    + " @ " + route.stage() + ": " + route.reason() + "; confidence=" + route.confidence()));
        }
        if (evidence.resources().isEmpty()) return new Route(ProgressionBand.ENTRY, .4, "empty_evidence_native_menu_fallback",
                List.of("No acquisition evidence supplied; entry-stage native menu fallback is provisional, with explicit low confidence"));
        return new Route(null, 0, "conservative_unavailable", List.of("No reliable reachable native menu or provider capability for " + axis));
    }
    public static Route flightRoute(PackEvidence evidence, boolean includeDeclaredRoutes) {
        List<CapabilityEvidence> candidates = new ArrayList<>(evidence.capabilities());
        for (var item : evidence.equipment()) if (item.included())
            candidates.add(new CapabilityEvidence(item.itemId(), item.stage(), item.axes(), item.reachable(), item.confidence(), item.reason()));
        if (includeDeclaredRoutes) candidates.addAll(SkillCapabilityRoutes.declaredRoutes());
        EvidenceSink resolvedClaims = new EvidenceSink(); evidence.facts().forEach(resolvedClaims::add);
        List<CapabilityEvidence> normalized = new ArrayList<>();
        for (var candidate : candidates) {
            var resource = evidence.resources().get(candidate.subjectId());
            var stage = resource == null || resource.stage().ordinal() < candidate.stage().ordinal() ? candidate.stage() : resource.stage();
            double reliability = resource == null ? candidate.confidence() : Math.min(candidate.confidence(), resource.confidence());
            Map<CapabilityAxis, Double> axes = new EnumMap<>(CapabilityAxis.class); axes.putAll(candidate.axes());
            for (var axis : List.of(CapabilityAxis.FLIGHT, CapabilityAxis.ABILITIES_FLYING_SPEED)) {
                var claim = resolvedClaims.get(resource == null ? EvidenceFact.Subject.CAPABILITY : EvidenceFact.Subject.ITEM,
                        candidate.subjectId(), "axis." + axis.name());
                if (claim != null) {
                    reliability = Math.min(reliability, claim.confidence());
                    axes.put(axis, claim.value().type() == EvidenceFact.ValueType.FLAG ? (claim.value().flag() ? 1.0 : 0.0) : claim.value().number());
                }
            }
            normalized.add(new CapabilityEvidence(candidate.subjectId(), stage, axes,
                    candidate.reachable() && (resource == null || resource.reachable()), reliability,
                    candidate.reason() + (resource == null ? "" : "; acquisition stage=" + resource.stage() + ", confidence=" + resource.confidence())));
        }
        candidates = normalized;
        candidates.sort(Comparator.comparing(CapabilityEvidence::stage).thenComparing(CapabilityEvidence::subjectId));
        List<String> reasons = new ArrayList<>();
        CapabilityEvidence selected = null;
        for (var candidate : candidates) {
            if (candidate.axes().getOrDefault(CapabilityAxis.FLIGHT, 0.0) <= 0
                    && candidate.axes().getOrDefault(CapabilityAxis.GLIDING, 0.0) <= 0) continue;
            var resource = evidence.resources().get(candidate.subjectId());
            boolean reachable = candidate.reachable() && (resource == null || resource.reachable());
            boolean compatible = candidate.axes().getOrDefault(CapabilityAxis.FLIGHT, 0.0) > 0
                    && candidate.axes().getOrDefault(CapabilityAxis.ABILITIES_FLYING_SPEED, 0.0) > 0;
            boolean reliable = candidate.confidence() >= .6;
            reasons.add(candidate.subjectId() + " @ " + candidate.stage() + ": reachable=" + reachable
                    + ", FLIGHT+ABILITIES_FLYING_SPEED=" + compatible + ", confidence=" + candidate.confidence() + "; " + candidate.reason());
            evidence.facts().stream().filter(f -> f.subjectId().equals(candidate.subjectId())
                    && (f.property().startsWith("axis.") || f.property().equals("stage") || f.property().equals("attainable")))
                    .forEach(f -> reasons.add("Claim " + f.property() + " provider=" + f.provider() + " origin=" + f.origin()
                            + " confidence=" + f.confidence() + " reason=" + f.reason()));
            if (selected == null && reachable && compatible && reliable) selected = candidate;
        }
        if (selected == null) {
            reasons.add("Conservative unavailable fallback: no reachable reliable standard-flight speed contract; GLIDING and opaque jetpacks do not qualify");
            return new Route(null, 0, "conservative_unavailable", List.copyOf(reasons));
        }
        return new Route(selected.stage(), selected.confidence(), selected.reason().contains("DECLARED_NOT_IMPLEMENTED")
                ? "explicit_declared_skill_route" : "verified_capability_provider", List.copyOf(reasons));
    }
    public static JsonObject diagnostics(RuntimeBalanceDefinition runtime) {
        JsonObject result = new JsonObject();
        result.addProperty("policy", "semantic_marginal_utility_v1");
        result.addProperty("purchasePolicy", "continuous_integer_essence_targets");
        result.add("tracks", runtime.toJson().getAsJsonObject("balanceProfile").getAsJsonObject("bonusTracks").deepCopy());
        return result;
    }
    public static JsonObject toJson(Map<ResourceLocation, BonusTrackDefinition> tracks) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().registerTypeAdapter(ResourceLocation.class,
                (JsonSerializer<ResourceLocation>) (id, type, context) -> new JsonPrimitive(id.toString())).create();
        return gson.toJsonTree(tracks).getAsJsonObject();
    }
    /** Calibration changes native maxima repeatedly; keep the resolved runtime mirror exact at every validation boundary. */
    static void synchronizeMaxima(JsonObject json) {
        JsonObject profile = json.getAsJsonObject("balanceProfile");
        JsonObject tracks = profile.getAsJsonObject("bonusTracks");
        if (tracks == null) return;
        for (var entry : tracks.entrySet()) {
            JsonObject track = entry.getValue().getAsJsonObject();
            double maximum = json.getAsJsonObject("statMaxBonuses").get(entry.getKey()).getAsDouble();
            track.addProperty("maximumEffect", maximum);
            if (maximum == 0) {
                track.addProperty("applicability", "UNAVAILABLE");
                for (var element : track.getAsJsonArray("checkpoints")) {
                    JsonObject point = element.getAsJsonObject();
                    point.addProperty("cumulativeCap", 0); point.addProperty("segmentCost", 0); point.addProperty("effectFraction", 0);
                    point.addProperty("available", false); point.addProperty("purchasable", false);
                    profile.getAsJsonObject("statOverrides").getAsJsonObject(entry.getKey()).addProperty(point.get("tierId").getAsString(), 0);
                }
                track.getAsJsonObject("inputs").addProperty("marginal_power", 0);
            }
        }
    }
    /** Human exact overrides remain exceptional and authoritative; reconcile only duplicated facts, never infer new policy. */
    static void synchronizeExactOverrides(JsonObject json, Map<String, Object> overrides) {
        JsonObject profile = json.getAsJsonObject("balanceProfile");
        JsonObject tracks = profile.getAsJsonObject("bonusTracks");
        for (var entry : tracks.entrySet()) {
            String id = entry.getKey(); JsonObject track = entry.getValue().getAsJsonObject();
            String prefix = "/runtime/balanceProfile/bonusTracks/" + id;
            List<String> paths = overrides.keySet().stream().filter(path -> path.startsWith(prefix + "/")
                    || path.startsWith("/runtime/balanceProfile/statOverrides/" + id + "/")
                    || path.equals("/runtime/statMaxBonuses/" + id)).sorted().toList();
            if (paths.isEmpty()) continue;
            boolean trackMax = overrides.containsKey(prefix + "/maximumEffect");
            boolean statMax = overrides.containsKey("/runtime/statMaxBonuses/" + id);
            if (trackMax && statMax && track.get("maximumEffect").getAsDouble() != json.getAsJsonObject("statMaxBonuses").get(id).getAsDouble())
                throw new IllegalArgumentException("Contradictory exact Bonus maxima " + id);
            if (trackMax) json.getAsJsonObject("statMaxBonuses").add(id, track.get("maximumEffect"));
            long previous = 0;
            for (int index = 0; index < track.getAsJsonArray("checkpoints").size(); index++) {
                JsonObject point = track.getAsJsonArray("checkpoints").get(index).getAsJsonObject();
                String tier = point.get("tierId").getAsString();
                String capPath = "/runtime/balanceProfile/statOverrides/" + id + "/" + tier;
                String pointPath = prefix + "/checkpoints/" + index;
                long mirror = profile.getAsJsonObject("statOverrides").getAsJsonObject(id).get(tier).getAsLong();
                long cap = point.get("cumulativeCap").getAsLong();
                if (overrides.containsKey(capPath)) {
                    if (overrides.containsKey(pointPath + "/cumulativeCap") && mirror != cap)
                        throw new IllegalArgumentException("Contradictory exact Bonus caps " + id + "/" + tier);
                    cap = mirror;
                }
                long segment = cap - previous;
                if (overrides.containsKey(pointPath + "/segmentCost") && point.get("segmentCost").getAsLong() != segment)
                    throw new IllegalArgumentException("Exact segment cost must equal adjacent cumulative caps " + id);
                point.addProperty("cumulativeCap", cap); point.addProperty("segmentCost", segment);
                point.addProperty("purchasable", point.get("available").getAsBoolean() && segment > 0);
                profile.getAsJsonObject("statOverrides").getAsJsonObject(id).addProperty(tier, cap);
                previous = cap;
            }
            track.addProperty("source", "exact_override:" + String.join(",", paths));
            track.getAsJsonArray("evidence").add("Authoritative exact resolved override paths: " + paths);
        }
        synchronizeMaxima(json);
    }
}

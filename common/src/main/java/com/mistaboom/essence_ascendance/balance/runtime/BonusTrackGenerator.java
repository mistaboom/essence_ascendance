package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.config.BonusProgressionConfig;
import com.mistaboom.essence_ascendance.skill.ProgressionRequirements;
import com.mistaboom.essence_ascendance.equipment.ShieldMath;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Generates native meaningful Bonus states from common power/economy inputs and optional human bounds. */
public final class BonusTrackGenerator {
    private BonusTrackGenerator() {}
    /** Final local publication. Allocation, desirability and prices were resolved before floor grants. */
    public static RuntimeBalanceDefinition publishMeaningful(RuntimeBalanceDefinition nominal) {
        return publishMeaningful(nominal, Map.of());
    }
    public static RuntimeBalanceDefinition publishMeaningful(RuntimeBalanceDefinition nominal,
            Map<ResourceLocation, com.mistaboom.essence_ascendance.skill.ProgressionRequirements.Bonus> requirements) {
        return publishMeaningful(nominal, requirements, BonusProgressionConfig.automatic());
    }
    public static RuntimeBalanceDefinition publishMeaningful(RuntimeBalanceDefinition nominal,
            Map<ResourceLocation, ProgressionRequirements.Bonus> requirements, BonusProgressionConfig configuration) {
        for (String id : configuration.entries().keySet()) if (EssenceStatRegistry.get(ResourceLocation.parse(id)).isEmpty())
            throw new IllegalArgumentException("bonus_progression.toml: Unknown Bonus ID " + id);
        var json = nominal.toJson();
        var tracks = new TreeMap<ResourceLocation, BonusTrackDefinition>();
        for (var stat : EssenceStatRegistry.values()) {
            var track = nominal.config().balanceProfile().bonusTracks().get(stat.id());
            var authored = requirements.getOrDefault(stat.id(), track.progressionRequirements());
            try {
                tracks.put(stat.id(), publishNative(nominal, stat, track, authored, configuration.entries().get(stat.id().toString())));
            } catch (IllegalArgumentException invalid) {
                if (!configuration.entries().containsKey(stat.id().toString())) throw invalid;
                throw new IllegalArgumentException("bonus_progression.toml: " + stat.id() + ": " + invalid.getMessage(), invalid);
            }
        }
        json.getAsJsonObject("balanceProfile").add("bonusTracks", toJson(tracks));
        for (var entry : tracks.entrySet()) {
            json.getAsJsonObject("statMaxBonuses").addProperty(entry.getKey().toString(), entry.getValue().maximumEffect());
            var caps = json.getAsJsonObject("balanceProfile").getAsJsonObject("statOverrides").getAsJsonObject(entry.getKey().toString());
            entry.getValue().checkpoints().forEach(point -> caps.addProperty(point.tierId().toString(), point.cumulativeCap()));
        }
        return RuntimeBalanceDefinition.fromJson(json);
    }

    /** Native floors and placement references are sampled once, before the shared planner runs. */
    static BonusTrackDefinition publishNative(RuntimeBalanceDefinition runtime, StatDefinition stat,
            BonusTrackDefinition track, ProgressionRequirements.Bonus authored) {
        return publishNative(runtime, stat, track, authored, null);
    }
    private static BonusTrackDefinition publishNative(RuntimeBalanceDefinition runtime, StatDefinition stat,
            BonusTrackDefinition track, ProgressionRequirements.Bonus authored, BonusProgressionConfig.Entry configured) {
        if (track.applicability() == BonusTrackDefinition.Applicability.UNAVAILABLE) {
            if (configured != null) throw new IllegalArgumentException("Configured Bonus has no supported native state");
            return track;
        }
        var inputs = new TreeMap<>(track.inputs());
        inputs.keySet().removeIf(key -> key.startsWith("configured_"));
        var reasons = new ArrayList<>(track.evidence());
        NativeBonusMechanics.limit(stat, runtime.config().shieldBalance()).ifPresent(limit -> inputs.put("native_maximum_effect", limit.maximum()));
        if (NativeDamageBonusFloor.supported(stat)) {
            var contexts = NativeDamageBonusFloor.contexts(runtime, stat);
            var property = switch (stat.id().getPath()) {
                case "melee_damage" -> com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.MELEE_DAMAGE;
                case "ranged_damage" -> com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.RANGED_DAMAGE;
                default -> com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.MAGIC_DAMAGE;
            };
            inputs.put("native_damage_increment_floor", NativeDamageBonusFloor.MINIMUM_DAMAGE_INCREMENT);
            inputs.put("placement_zero_damage_reference", RuntimeReferencePolicy.playerHit());
            for (var tier : AscendanceTierRegistry.powerTiers()) {
                var active = contexts.stream().filter(context -> context.tier().equals(tier.id())).toList();
                inputs.put("native_damage_floor_" + tier.id().getPath(), active.stream().mapToDouble(NativeDamageBonusFloor.Context::valueForOneDamage)
                        .max().orElseThrow(() -> new IllegalArgumentException("No supported native damage profile for " + stat.id() + " at " + tier.id())));
                // A lower-tier player may hold higher-tier physical gear. Its Bonus activation is capped
                // by the player, but its larger physical damage still consumes attribute headroom.
                inputs.put("native_damage_ceiling_" + tier.id().getPath(), contexts.stream()
                        .filter(context -> AscendanceTierRegistry.get(context.tier()).orElseThrow().order() >= tier.order())
                        .mapToDouble(NativeDamageBonusFloor.Context::maximumValue).min().orElseThrow());
                inputs.put("placement_damage_base_" + tier.id().getPath(), runtime.config().equipmentBaselineConfig().baselineFor(tier).value(property));
            }
            reasons.add("Flat damage placement compares the first gain with the generated weapon-family baseline. Every completed increment separately adds at least one native damage on every eligible profile, holding the same equipment fixed; equipment upgrades never fund an increment. Headroom includes higher-tier physical gear that can activate this player-tier checkpoint, including gear above the track's completion tier.");
            reasons.add("A zero-damage family uses the native unarmed hit only as a finite placement reference; native outcome checks retain its actual zero physical damage.");
        }
        if (stat.id().getPath().equals("guard_readiness")) inputs.put("native_guard_minimum_disable_ticks", (double)runtime.config().shieldBalance().minimumDisableTicks());
        NativeCooldownBonusFloor.record(runtime, stat, inputs, reasons);
        if (stat.id().equals(EssenceStats.MELEE_ATTACK_SPEED.id())) NativeMeleeRateBonusFloor.record(runtime, inputs, reasons);
        var prepared = new BonusTrackDefinition(track.statId(), track.category(), track.unit(), track.maximumEffect(),
                track.startTier(), track.completionTier(), track.checkpoints(), track.investmentExponent(), track.purchaseStyle(),
                track.snapPoints(), track.applicability(), track.compatibilityRequirements(), track.confidence(), reasons, track.source(), inputs);
        return plan(prepared, authored, configured);
    }

    private static boolean guardImproves(double previous, double value, int minimum) {
        return ShieldMath.disableTicks(100, value, minimum) < ShieldMath.disableTicks(100, previous, minimum)
                || ShieldMath.raiseDelayTicks(5, value, 1) < ShieldMath.raiseDelayTicks(5, previous, 1);
    }
    public static BonusTrackDefinition meaningful(BonusTrackDefinition nominal, ProgressionRequirements.Bonus requirements) {
        return plan(nominal, requirements, null);
    }
    private static BonusTrackDefinition plan(BonusTrackDefinition nominal, ProgressionRequirements.Bonus required,
            BonusProgressionConfig.Entry configured) {
        if (nominal.applicability() == BonusTrackDefinition.Applicability.UNAVAILABLE) return nominal;
        var tiers = AscendanceTierRegistry.powerTiers().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        var stat = EssenceStatRegistry.get(nominal.statId()).orElseThrow();
        var semantic = BonusSemantics.require(stat);
        boolean step = nominal.inputs().containsKey("native_base_step_height");
        double quantum = step ? .000001 : RuntimeValueQuantization.statStep(nominal.unit());
        var mechanics = mechanics(nominal, semantic, tiers);
        double first = configured == null ? required.firstStateFloor() : Math.max(required.firstStateFloor(), configured.minimumBonusAtLowest());
        BonusProgressionPlanner.Plan selected;
        if (configured == null) {
            double maximum = step ? RuntimeValueQuantization.up(nominal.maximumEffect(), quantum) : nominal.maximumEffect();
            selected = BonusProgressionPlanner.automatic(tiers.size(), maximum, first, required.tierImprovementFloor(), quantum, mechanics);
            // Emit exactly the controls used by the configured path, including a first native floor overage.
            first = RuntimeValueQuantization.reconstructedGridValue(selected.values().getFirst(), quantum);
            selected = BonusProgressionPlanner.configured(selected.start(), selected.end(), selected.values().getLast(),
                    first, required.tierImprovementFloor(), quantum, mechanics);
        } else {
            int start = tiers.indexOf(AscendanceTierRegistry.get(ResourceLocation.parse(configured.minTier())).orElseThrow());
            int end = tiers.indexOf(AscendanceTierRegistry.get(ResourceLocation.parse(configured.maxTier())).orElseThrow());
            if (configured.bonusAtMax() < first) throw new IllegalArgumentException("bonus_at_max is below the first floor");
            selected = BonusProgressionPlanner.configured(start, end, configured.bonusAtMax(), first,
                    required.tierImprovementFloor(), quantum, mechanics);
        }
        var prices = nominal.checkpoints().stream().filter(BonusTrackDefinition.Checkpoint::purchasable).toList();
        var checkpoints = new ArrayList<BonusTrackDefinition.Checkpoint>();
        var snaps = new ArrayList<Double>(); snaps.add(0.0);
        long cumulative = 0; double fraction = 0;
        double maximum = selected.values().getLast();
        for (var tier : AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList()) {
            int index = tiers.indexOf(tier), state = index - selected.start();
            long segment = 0;
            if (index >= selected.start() && index <= selected.end()) {
                fraction = index == selected.end() ? 1 : selected.values().get(state) / maximum;
                segment = prices.get(Math.min(state, prices.size() - 1)).segmentCost();
                cumulative = Math.addExact(cumulative, segment); snaps.add(fraction);
            }
            checkpoints.add(new BonusTrackDefinition.Checkpoint(tier.id(), cumulative, segment, fraction,
                    index >= selected.start(), segment > 0));
        }
        var inputs = new TreeMap<>(nominal.inputs());
        inputs.keySet().removeIf(key -> key.startsWith("native_threshold_") || key.startsWith("configured_")
                || key.equals("nominal_endpoint") || key.equals("native_cooldown_preferred_start_order"));
        inputs.put("first_state_floor", first); inputs.put("later_state_floor", required.tierImprovementFloor());
        inputs.put("generated_state_count", (double) selected.values().size());
        inputs.put("placement_first_impact", mechanics.impact(selected.start(), selected.values().getFirst()));
        inputs.put("placement_impact_allowance", BonusProgressionPlanner.FIRST_TIER_IMPACT * Math.pow(2, selected.start()));
        inputs.put("increment_growth", BonusProgressionPlanner.INCREMENT_GROWTH);
        var reasons = new ArrayList<>(new LinkedHashSet<>(nominal.evidence()));
        reasons.removeIf(reason -> reason.startsWith("Complete native states;") || reason.startsWith("Shared native progression:"));
        reasons.add("Shared native progression: first-impact allowances 5%, 10%, 20%, 40%, 80%; later increments target 1.5 growth with a small final remainder merged. Native minima/caps are checked on unchanged equipment at each active tier. The useful span stops when its endpoint is reached; there is no final-tier anchor. Explicit four-control bounds use the identical planner.");
        return new BonusTrackDefinition(nominal.statId(), nominal.category(), nominal.unit(), maximum,
                tiers.get(selected.start()).id(), tiers.get(selected.end()).id(), checkpoints, nominal.investmentExponent(),
                BonusTrackDefinition.PurchaseStyle.FUNDED_STATES, snaps, nominal.applicability(), nominal.compatibilityRequirements(),
                nominal.confidence(), reasons, nominal.source(), inputs);
    }

    private static BonusProgressionPlanner.Mechanics mechanics(BonusTrackDefinition nominal, BonusSemantics.Mechanic semantic,
            List<AscendanceTierDefinition> tiers) {
        return new BonusProgressionPlanner.Mechanics() {
            private String suffix(int tier) { return tiers.get(tier).id().getPath(); }
            private List<NativeMeleeRateBonusFloor.Context> melee(int tier) {
                var result = new ArrayList<NativeMeleeRateBonusFloor.Context>();
                int count = nominal.inputs().getOrDefault("native_melee_count_" + suffix(tier), 0.0).intValue();
                for (int index = 0; index < count; index++) result.add(new NativeMeleeRateBonusFloor.Context(nominal.statId(),
                        nominal.inputs().get("native_melee_rate_" + suffix(tier) + "_" + index),
                        nominal.inputs().get("native_melee_applicability_" + suffix(tier) + "_" + index)));
                return result;
            }
            public double ceiling(int tier) {
                double ceiling = nominal.inputs().getOrDefault("native_maximum_effect", 1_000_000.0);
                ceiling = Math.min(ceiling, nominal.inputs().getOrDefault("native_damage_ceiling_" + suffix(tier), 1_000_000.0));
                if (nominal.inputs().containsKey(NativeMeleeRateBonusFloor.MARKER))
                    ceiling = Math.min(ceiling, melee(tier).stream().mapToDouble(NativeMeleeRateBonusFloor.Context::maximumPercent).min().orElseThrow());
                if (nominal.inputs().containsKey(NativeCooldownBonusFloor.MARKER)) {
                    double rate = nominal.inputs().get(NativeCooldownBonusFloor.key(tiers.get(tier)));
                    double oneTick = RuntimeValueQuantization.up(Math.max(0, (20 / 1.5 / rate - 1) * 100), 1);
                    if (NativeCooldownBonusFloor.ticks(rate, oneTick) > 1) oneTick++;
                    ceiling = Math.min(ceiling, oneTick);
                }
                return ceiling;
            }
            public boolean improves(int tier, double previous, double value) {
                if (value <= previous || value > ceiling(tier)) return false;
                double damageFloor = nominal.inputs().getOrDefault("native_damage_floor_" + suffix(tier), 0.0);
                if (value - previous + 1e-9 < damageFloor) return false;
                if (nominal.inputs().containsKey("native_guard_minimum_disable_ticks")
                        && !guardImproves(previous, value, nominal.inputs().get("native_guard_minimum_disable_ticks").intValue())) return false;
                if (nominal.inputs().containsKey(NativeCooldownBonusFloor.MARKER)) {
                    double rate = nominal.inputs().get(NativeCooldownBonusFloor.key(tiers.get(tier)));
                    if (NativeCooldownBonusFloor.ticks(rate, value) >= NativeCooldownBonusFloor.ticks(rate, previous)) return false;
                }
                return !nominal.inputs().containsKey(NativeMeleeRateBonusFloor.MARKER)
                        || melee(tier).stream().allMatch(context -> context.outcome(value) > context.outcome(previous));
            }
            public double impact(int tier, double value) {
                double baseline = switch (semantic.response()) {
                    case FLAT_DAMAGE -> {
                        double physical = nominal.inputs().getOrDefault("placement_damage_base_" + suffix(tier), RuntimeReferencePolicy.playerHit());
                        yield physical > 0 ? physical : nominal.inputs().getOrDefault("placement_zero_damage_reference", RuntimeReferencePolicy.playerHit());
                    }
                    case HEALTH -> RuntimeReferencePolicy.playerHealth();
                    case RECOVERY -> RuntimeReferencePolicy.playerHealth()
                            / nominal.inputs().getOrDefault("recovery_comparison_seconds", BalanceSettings.defaults().generation().survivalWindowSeconds());
                    case AIR -> 15;
                    case REACH_DISTANCE -> NativeBonusMechanics.baseline(Attributes.BLOCK_INTERACTION_RANGE);
                    case STEP_BOUNDARY -> nominal.inputs().getOrDefault("native_base_step_height", nativeStepBase());
                    default -> 1;
                };
                return BonusSemantics.normalizedImpact(semantic, value, baseline);
            }
        };
    }
    public static Map<ResourceLocation, BonusTrackDefinition> resolve(BalanceSettings settings,
            Map<ResourceLocation, Long> tierCaps, Map<ResourceLocation, Double> tierFractions,
            Map<ResourceLocation, Double> maxima, Map<String, Double> categorySupply, double exponent) {
        var allTiers = AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        var tiers = allTiers.stream().filter(AscendanceTierDefinition::grantsPower).toList();
        Map<ResourceLocation, BonusTrackDefinition> result = new TreeMap<>();
        for (var stat : EssenceStatRegistry.values()) {
            var semantic = BonusSemantics.require(stat);
            List<String> reasons = new ArrayList<>();
            reasons.add("Native mechanic: " + semantic.nativeMechanism() + "; conditions=" + semantic.conditions());
            double maximum = maxima.get(stat.id());
            var nativeLimit = NativeBonusMechanics.limit(stat);
            if (nativeLimit.isPresent()) {
                maximum = Math.min(maximum, nativeLimit.orElseThrow().maximum());
                reasons.add("Native saturation ceiling=" + nativeLimit.orElseThrow().maximum() + "; " + nativeLimit.orElseThrow().evidence());
            }
            List<Double> thresholds = List.of();
            double baseStep = 0;
            if (semantic.response() == BonusSemantics.Response.STEP_BOUNDARY) {
                var nativeStep = NativeBonusMechanics.step(maximum);
                baseStep = nativeStep.base();
                thresholds = nativeStep.thresholds();
                maximum = thresholds.isEmpty() ? 0 : thresholds.getLast();
                reasons.addAll(nativeStep.evidence());
            }
            boolean available = maximum > 0;
            double marginal = response(semantic, maximum, settings.generation().survivalWindowSeconds());
            double breadth = 1.0 / (1 + semantic.conditions().size());
            double headroom = 1.0 / (1 + settings.compositionSafeguard() * Math.max(0, marginal));
            double intrinsic = available ? Math.max(.01, marginal * breadth * headroom) : 0;
            double utility = intrinsic / Math.log(2);
            double economy = categorySupply.getOrDefault(stat.essenceType().id().toString(), 1.0);
            Map<String, Double> inputs = new TreeMap<>();
            inputs.put("native_response", marginal); inputs.put("breadth", breadth);
            inputs.put("composition_headroom", headroom); inputs.put("marginal_power", intrinsic);
            inputs.put("category_supply", economy); inputs.put("utility_price_multiplier", utility);
            if (semantic.response() == BonusSemantics.Response.RECOVERY) {
                inputs.put("recovery_comparison_seconds", settings.generation().survivalWindowSeconds());
                reasons.add("Recovery rate is compared as potential healing over the configured survival window of "
                        + settings.generation().survivalWindowSeconds()
                        + " seconds, divided by native player health. This comparison requires sufficient missing health; it does not assume healing uptime.");
            }
            inputs.put("purchase_budget_share", ProgressionPricing.purchaseShare());
            nativeLimit.ifPresent(limit -> inputs.put("native_maximum_effect", limit.maximum()));
            if (baseStep > 0) inputs.put("native_base_step_height", baseStep);
            for (int index = 0; index < thresholds.size(); index++) inputs.put("native_threshold_" + (index + 1), thresholds.get(index));
            int stateCount = thresholds.isEmpty() ? tiers.size() : Math.min(tiers.size(), thresholds.size());
            List<BonusTrackDefinition.Checkpoint> checkpoints = new ArrayList<>();
            long cumulative = 0; double priorFraction = 0;
            for (var tier : allTiers) {
                int index = tiers.indexOf(tier);
                long segment = 0; double fraction = priorFraction;
                boolean unlocked = available && index >= 0;
                if (unlocked && index < stateCount) {
                    fraction = thresholds.isEmpty() ? tierFractions.get(tier.id())
                            : thresholds.get(Math.min(thresholds.size() - 1, index)) / maximum;
                    if (index == stateCount - 1) fraction = 1;
                    long priorEconomic = index == 0 ? 0 : tierCaps.get(tiers.get(index - 1).id());
                    segment = fraction > priorFraction ? safeCost((tierCaps.get(tier.id()) - priorEconomic)
                            * economy * utility * ProgressionPricing.purchaseShare()) : 0;
                    cumulative = Math.addExact(cumulative, segment);
                }
                checkpoints.add(new BonusTrackDefinition.Checkpoint(tier.id(), cumulative, segment, fraction, unlocked, segment > 0));
                priorFraction = fraction;
            }
            reasons.add("Price = adjacent category-economy segment × normalized native response × conditional breadth × composition headroom × shared purchase-budget share");
            reasons.add("The shared native planner chooses the span and accelerating increments from normalized first-impact allowances and actual native headroom");
            result.put(stat.id(), new BonusTrackDefinition(stat.id(), stat.category(), stat.unit(), maximum,
                    tiers.getFirst().id(), tiers.get(stateCount - 1).id(), checkpoints, exponent,
                    BonusTrackDefinition.PurchaseStyle.CONTINUOUS, List.of(),
                    available ? BonusTrackDefinition.Applicability.AVAILABLE : BonusTrackDefinition.Applicability.UNAVAILABLE,
                    semantic.conditions(), .75, reasons, "generic_native_progression", inputs));
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
    private static double response(BonusSemantics.Mechanic semantic, double maximum, double recoveryWindowSeconds) {
        return switch (semantic.response()) {
            case FLAT_DAMAGE -> Math.log1p(maximum / RuntimeReferencePolicy.playerHit());
            case MULTIPLIER -> Math.log1p(maximum / 100) * (semantic.axis() == CapabilityAxis.JUMP ? 2 : 1);
            case PREVENTION -> -Math.log(Math.max(.01, 1 - maximum / 100));
            case HEALTH -> Math.log1p(maximum * 2 / RuntimeReferencePolicy.playerHealth());
            case RECOVERY -> Math.log1p(maximum * 2 * recoveryWindowSeconds / RuntimeReferencePolicy.playerHealth());
            case AIR -> Math.log1p(maximum / 15); // Native 300 air ticks / 20 ticks per second.
            case REACH_DISTANCE -> Math.log1p(maximum / Attributes.BLOCK_INTERACTION_RANGE.value().getDefaultValue());
            case STEP_BOUNDARY -> maximum <= 0 ? 0 : Math.log1p(maximum / (nativeStepBase() + maximum));
            case YIELD_LEVEL -> Math.log1p(maximum); // Fractional yields already use expectation, not integer-only purchase gates.
            case LUCK -> Math.log1p(maximum) / 2; // Quality-sensitive pools only; opaque loot frequency remains uncertain.
        };
    }
    public static JsonObject diagnostics(RuntimeBalanceDefinition runtime) {
        JsonObject result = new JsonObject();
        result.addProperty("policy", "shared_native_impact_progression_v3");
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

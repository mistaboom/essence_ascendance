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

    /** Final candidate settings include human overrides; generation-time native floors never reinterpret saved tracks. */
    static BonusTrackDefinition publishNative(RuntimeBalanceDefinition runtime, StatDefinition stat,
            BonusTrackDefinition track, ProgressionRequirements.Bonus authored) {
        return publishNative(runtime, stat, track, authored, null);
    }
    private static BonusTrackDefinition publishNative(RuntimeBalanceDefinition runtime, StatDefinition stat,
            BonusTrackDefinition track, ProgressionRequirements.Bonus authored, BonusProgressionConfig.Entry configured) {
        if (configured != null && track.applicability() == BonusTrackDefinition.Applicability.UNAVAILABLE)
            throw new IllegalArgumentException("Configured Bonus has no supported native state");
        if (track.applicability() == BonusTrackDefinition.Applicability.UNAVAILABLE) return track;
        var inputs = new TreeMap<>(track.inputs());
        inputs.keySet().removeIf(key -> key.startsWith("configured_"));
        var reasons = new ArrayList<>(track.evidence());
        double first = authored.firstStateFloor(), later = authored.tierImprovementFloor();
        var limit = NativeBonusMechanics.limit(stat, runtime.config().shieldBalance());
        limit.ifPresent(value -> {
            inputs.put("native_maximum_effect", value.maximum());
            reasons.add("Final candidate native ceiling=" + value.maximum() + "; " + value.evidence());
        });
        if (NativeDamageBonusFloor.supported(stat)) {
            var contexts = NativeDamageBonusFloor.contexts(runtime, stat);
            var weakest = contexts.stream().max(Comparator.comparingDouble(NativeDamageBonusFloor.Context::percentForOneDamage)).orElseThrow();
            first = Math.max(first, weakest.percentForOneDamage());
            later = Math.max(later, weakest.percentForOneDamage());
            inputs.put("native_damage_increment_floor", NativeDamageBonusFloor.MINIMUM_DAMAGE_INCREMENT);
            inputs.put("native_damage_reference_base", weakest.baseDamage());
            inputs.put("native_damage_reference_applicability", weakest.applicability());
            inputs.put("native_damage_context_count", (double) contexts.size());
            inputs.put("native_maximum_effect", Math.min(inputs.getOrDefault("native_maximum_effect", 1_000_000.0),
                    contexts.stream().mapToDouble(NativeDamageBonusFloor.Context::maximumPercent).min().orElseThrow()));
            reasons.add("Each completed state adds at least 1 native damage as well as the authored percentage floor, "
                    + "holding the same supported equipment fixed. Conservative weakest powered context=" + weakest.profile()
                    + " at " + weakest.tier() + ", rounded physical damage=" + weakest.baseDamage()
                    + ", held applicability=" + weakest.applicability() + ". Unarmed/Latent/fractured gear has no damage Bonus. "
                    + "Lower-tier gear caps activation at its own tier; higher player tiers do not bypass that cap. "
                    + "Damage references are fully charged, unmitigated base attacks, not critical/enchantment/skill uptime claims.");
        }
        if (stat.id().getPath().equals("guard_readiness")) {
            int minimum = runtime.config().shieldBalance().minimumDisableTicks();
            inputs.put("native_guard_minimum_disable_ticks", (double) minimum);
            double firstNativeTick = 1;
            while (firstNativeTick <= 100 && !guardImproves(0, firstNativeTick, minimum)) firstNativeTick++;
            first = Math.max(first, firstNativeTick);
            reasons.add("Every active endpoint must additionally save at least one whole native raise or vanilla disable tick, "
                    + "using this candidate's configured minimum disable ticks=" + minimum);
        }
        NativeCooldownBonusFloor.record(runtime, stat, inputs, reasons);
        if (stat.id().equals(EssenceStats.MELEE_ATTACK_SPEED.id())) NativeMeleeRateBonusFloor.record(runtime, inputs, reasons);
        if (configured != null) {
            first = Math.max(first, configured.minimumBonusAtLowest());
            double ceiling = inputs.getOrDefault("native_maximum_effect", 1_000_000.0);
            if (configured.bonusAtMax() > ceiling || configured.bonusAtMax() < first)
                throw new IllegalArgumentException("bonus_at_max=" + configured.bonusAtMax() + " conflicts with first floor=" + first + " or native ceiling=" + ceiling);
            inputs.put("configured_min_tier_order", (double)AscendanceTierRegistry.get(ResourceLocation.parse(configured.minTier())).orElseThrow().order());
            inputs.put("configured_max_tier_order", (double)AscendanceTierRegistry.get(ResourceLocation.parse(configured.maxTier())).orElseThrow().order());
            inputs.put("configured_maximum", configured.bonusAtMax());
            track = configuredTrack(track, configured, first);
        }
        double maximum = Math.min(track.maximumEffect(), inputs.getOrDefault("native_maximum_effect", 1_000_000.0));
        if (maximum <= 0) throw new IllegalArgumentException("No native headroom for available Bonus " + stat.id());
        var prepared = new BonusTrackDefinition(track.statId(), track.category(), track.unit(), maximum,
                track.startTier(), track.completionTier(), track.checkpoints(), track.investmentExponent(),
                track.purchaseStyle(), track.snapPoints(), track.applicability(), track.compatibilityRequirements(),
                track.confidence(), reasons, track.source(), inputs);
        var required = new ProgressionRequirements.Bonus(authored.home(), first, later, authored.unit(), authored.quantization(),
                authored.mechanicalCap(), authored.earliestConstraint(), authored.latestConstraint(), authored.powerDesirability(), authored.compatibility());
        var published = meaningful(prepared, required);
        if (configured != null) return published;
        // The automatic result uses exactly the same four controls we emit for editing. An untouched
        // generated file therefore recreates every endpoint, not just its maximum and tier boundaries.
        var generated = BonusProgressionConfig.generatedEntry(published);
        var canonical = configuredTrack(prepared, generated, generated.minimumBonusAtLowest());
        var canonicalRequired = new ProgressionRequirements.Bonus(required.home(), generated.minimumBonusAtLowest(),
                required.tierImprovementFloor(), required.unit(), required.quantization(), required.mechanicalCap(),
                required.earliestConstraint(), required.latestConstraint(), required.powerDesirability(), required.compatibility());
        return meaningful(canonical, canonicalRequired);
    }

    private static BonusTrackDefinition configuredTrack(BonusTrackDefinition nominal, BonusProgressionConfig.Entry configured, double first) {
        var tiers = AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        int start = tiers.indexOf(AscendanceTierRegistry.get(ResourceLocation.parse(configured.minTier())).orElseThrow());
        int end = tiers.indexOf(AscendanceTierRegistry.get(ResourceLocation.parse(configured.maxTier())).orElseThrow());
        var points = new ArrayList<BonusTrackDefinition.Checkpoint>();
        long cumulative = 0; double fraction = 0;
        var prices = nominal.checkpoints().stream().filter(BonusTrackDefinition.Checkpoint::purchasable).toList();
        for (int index = 0; index < tiers.size(); index++) {
            long segment = 0;
            if (index >= start && index <= end) {
                double value = start == end ? configured.bonusAtMax()
                        : first + (configured.bonusAtMax() - first) * (index - start) / (end - start);
                if (index > start && value <= fraction * configured.bonusAtMax())
                    throw new IllegalArgumentException("Configured span has no positive improvement at " + tiers.get(index).id());
                fraction = index == end ? 1 : value / configured.bonusAtMax();
                // Meaningful publication assigns prices by state ordinal. Moving identical controls
                // through the emitted config must not switch to a different nominal tier's price.
                segment = prices.get(Math.min(index - start, prices.size() - 1)).segmentCost();
                cumulative = Math.addExact(cumulative, segment);
            }
            points.add(new BonusTrackDefinition.Checkpoint(tiers.get(index).id(), cumulative, segment, fraction, index >= start, segment > 0));
        }
        var inputs = new TreeMap<>(nominal.inputs());
        inputs.keySet().removeIf(key -> key.startsWith("native_threshold_"));
        inputs.put("configured_min_tier_order", (double)AscendanceTierRegistry.get(ResourceLocation.parse(configured.minTier())).orElseThrow().order());
        inputs.put("configured_max_tier_order", (double)AscendanceTierRegistry.get(ResourceLocation.parse(configured.maxTier())).orElseThrow().order());
        inputs.put("configured_maximum", configured.bonusAtMax());
        return new BonusTrackDefinition(nominal.statId(), nominal.category(), nominal.unit(), configured.bonusAtMax(),
                ResourceLocation.parse(configured.minTier()), ResourceLocation.parse(configured.maxTier()), points,
                nominal.investmentExponent(), BonusTrackDefinition.PurchaseStyle.CONTINUOUS, List.of(), nominal.applicability(),
                nominal.compatibilityRequirements(), nominal.confidence(), nominal.evidence(), nominal.source(), inputs);
    }

    private static boolean guardImproves(double previous, double value, int minimum) {
        return ShieldMath.disableTicks(100, value, minimum) < ShieldMath.disableTicks(100, previous, minimum)
                || ShieldMath.raiseDelayTicks(5, value, 1) < ShieldMath.raiseDelayTicks(5, previous, 1);
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
            if (nativeStep) return Math.rint(value * 1_000_000) / 1_000_000;
            // Fractions serialize the endpoint indirectly. Restore a grid value only when the
            // multiplication error is a few representable doubles, not a genuine partial unit.
            return RuntimeValueQuantization.reconstructedGridValue(value, quantum);
        }).toList();
        final ArrayList<Double> values;
        try {
            values = new ArrayList<>(com.mistaboom.essence_ascendance.progression.MeaningfulProgression.select(
                    candidates, requirements.firstStateFloor(), requirements.tierImprovementFloor(), quantum, ceiling));
        } catch (IllegalArgumentException incompatible) {
            throw new IllegalArgumentException("Cannot publish Bonus " + nominal.statId() + ": first floor="
                    + requirements.firstStateFloor() + ", later floor=" + requirements.tierImprovementFloor()
                    + ", quantum=" + quantum + ", native ceiling=" + ceiling, incompatible);
        }
        if (nominal.inputs().containsKey("native_guard_minimum_disable_ticks")) {
            int minimum = nominal.inputs().get("native_guard_minimum_disable_ticks").intValue();
            double previous = 0;
            for (var iterator = values.iterator(); iterator.hasNext();) {
                double value = iterator.next();
                if (guardImproves(previous, value, minimum)) previous = value;
                else iterator.remove();
            }
            if (values.isEmpty()) throw new IllegalArgumentException("No meaningful native shield timing state for " + nominal.statId());
        }
        int earliest = tiers.indexOf(AscendanceTierRegistry.powerTiers().stream()
                .min(Comparator.comparingInt(AscendanceTierDefinition::order)).orElseThrow());
        // One placement rule for every Bonus: put its meaningful state count at the end of progression.
        // A native same-tier mechanic may require an earlier feasible span; no acquisition graph participates.
        int start = Math.max(earliest, tiers.size() - values.size());
        boolean configured = nominal.inputs().containsKey("configured_min_tier_order");
        int configuredEnd = tiers.size() - 1;
        if (configured) {
            int firstOrder = nominal.inputs().get("configured_min_tier_order").intValue();
            int lastOrder = nominal.inputs().get("configured_max_tier_order").intValue();
            start = tiers.indexOf(tiers.stream().filter(tier -> tier.order() == firstOrder).findFirst().orElseThrow());
            configuredEnd = tiers.indexOf(tiers.stream().filter(tier -> tier.order() == lastOrder).findFirst().orElseThrow());
            earliest = start;
        }
        int preferredStart = start;
        if (nominal.inputs().containsKey(NativeCooldownBonusFloor.MARKER)) {
            var plan = NativeCooldownBonusFloor.select(nominal, requirements, candidates, tiers, earliest, start);
            start = plan.start();
            values.clear(); values.addAll(plan.values());
        }
        if (nominal.inputs().containsKey(NativeMeleeRateBonusFloor.MARKER)) {
            var plan = NativeMeleeRateBonusFloor.select(nominal, requirements, candidates, tiers, earliest, start);
            start = plan.start();
            values.clear(); values.addAll(plan.values());
        }
        // A late native route can offer fewer states. Retain the first and endpoint without filler subdivision.
        int room = tiers.size() - start;
        while (values.size() > room) values.remove(values.size() == 2 ? 0 : values.size() - 2);
        int end = start + values.size() - 1;
        double maximum = values.getLast();
        if (configured && (start != preferredStart || end != configuredEnd
                || Math.abs(maximum - nominal.inputs().get("configured_maximum")) > 1e-7))
            throw new IllegalArgumentException("Configured min_tier/max_tier/bonus_at_max cannot provide a meaningful native increment at every active tier; requested "
                    + nominal.startTier() + " through " + nominal.completionTier() + " at " + nominal.maximumEffect()
                    + ", feasible result " + tiers.get(start).id() + " through " + tiers.get(end).id() + " at " + maximum);
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
        inputs.put("generated_state_count", (double) values.size());
        var reasons = new ArrayList<>(new LinkedHashSet<>(nominal.evidence()));
        reasons.removeIf(reason -> reason.contains("intervening investment and applied effect remain continuous")
                || reason.startsWith("Complete native states;") || reason.startsWith("Native whole-tick placement:")
                || reason.startsWith("Native melee partial-charge placement:"));
        reasons.add("Complete native states; first floor is local ignored overage. Later quantized candidates must meet the improvement floor; larger changes remain intact. Contiguous placement uses the common final-tier preference and meaningful state count, or the explicit human tier bounds.");
        if (nominal.inputs().containsKey(NativeCooldownBonusFloor.MARKER)) {
            inputs.put("native_cooldown_preferred_start_order", (double)tiers.get(preferredStart).order());
            reasons.add("Native whole-tick placement: preferred start=" + tiers.get(preferredStart).id()
                    + "; actual useful span=" + tiers.get(start).id() + " through " + tiers.get(end).id()
                    + ". A shorter span or earlier start reflects actual same-tier cooldown saturation; no supported track is silently removed.");
        }
        if (nominal.inputs().containsKey(NativeMeleeRateBonusFloor.MARKER))
            reasons.add("Native melee partial-charge placement: preferred start=" + tiers.get(preferredStart).id()
                    + "; actual useful span=" + tiers.get(start).id() + " through " + tiers.get(end).id()
                    + ". Same-tier native saturation is independent of the procedural budget; saved percentage units are unchanged.");
        return new BonusTrackDefinition(nominal.statId(), nominal.category(), nominal.unit(), maximum,
                tiers.get(start).id(), tiers.get(end).id(), checkpoints, nominal.investmentExponent(),
                BonusTrackDefinition.PurchaseStyle.FUNDED_STATES, snaps, nominal.applicability(),
                nominal.compatibilityRequirements(), nominal.confidence(), reasons, nominal.source(), inputs);
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
            double marginal = response(semantic, maximum, thresholds.size());
            double breadth = 1.0 / (1 + semantic.conditions().size());
            double headroom = 1.0 / (1 + settings.compositionSafeguard() * Math.max(0, marginal));
            double intrinsic = available ? Math.max(.01, marginal * breadth * headroom) : 0;
            double utility = intrinsic / Math.log(2);
            double economy = categorySupply.getOrDefault(stat.essenceType().id().toString(), 1.0);
            Map<String, Double> inputs = new TreeMap<>();
            inputs.put("native_response", marginal); inputs.put("breadth", breadth);
            inputs.put("composition_headroom", headroom); inputs.put("marginal_power", intrinsic);
            inputs.put("category_supply", economy); inputs.put("utility_price_multiplier", utility);
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
            reasons.add("Unlock placement uses meaningful native state count, not pack acquisition graphs, substitutes, or an individual Bonus placement rule");
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
    public static JsonObject diagnostics(RuntimeBalanceDefinition runtime) {
        JsonObject result = new JsonObject();
        result.addProperty("policy", "generic_native_progression_v2");
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

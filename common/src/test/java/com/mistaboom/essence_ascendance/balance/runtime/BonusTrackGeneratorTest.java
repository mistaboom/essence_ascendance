package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import java.util.*;

/** Real-registry generic native progression, pricing and continuous purchase curves. */
public final class BonusTrackGeneratorTest {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        var vanilla = resolve();
        sharedPlanner();
        breadthPlacement();
        nativeSaturation();
        var movement = vanilla.get(EssenceStats.MOVEMENT_SPEED.id());
        var step = vanilla.get(EssenceStats.STEP_HEIGHT.id());
        var flight = vanilla.get(EssenceStats.FLIGHT_SPEED.id());
        var recovery = vanilla.get(EssenceStats.HEALTH_REGENERATION.id());
        double recoveryWindow = BalanceSettings.defaults().generation().survivalWindowSeconds();
        check(recovery.inputs().get("recovery_comparison_seconds") == recoveryWindow,
                "The saved comparison duration comes from the configured survival window");
        check(Math.abs(recovery.inputs().get("placement_first_impact")
                        - recovery.activeValues().getFirst() * 2 * recoveryWindow / RuntimeReferencePolicy.playerHealth()
                        * BonusSemantics.applicabilityBreadth(BonusSemantics.require(EssenceStats.HEALTH_REGENERATION))) < 1e-9,
                "Recovery placement combines healing over the named window with the shared applicability policy");
        check(step.checkpoints().getLast().cumulativeCap() < movement.checkpoints().getLast().cumulativeCap() / 5,
                "Step must be materially cheaper than broadly applicable movement");
        check(step.activeStateCount() == 2 && step.startTier().equals(AscendanceTiers.RESONANT.id())
                        && step.completionTier().equals(AscendanceTiers.ASCENDANT.id()),
                "Step's first traversal impact, shared applicability and two native states determine its span");
        check(step.snapPoints().size() == step.activeStateCount() + 1 && step.purchaseStyle() == BonusTrackDefinition.PurchaseStyle.FUNDED_STATES,
                "Step Height publishes complete native traversal states");
        check(step.inputs().containsKey("first_state_floor") && step.inputs().containsKey("later_state_floor"),
                "Resolved floors remain auditable");
        check(Math.abs(step.maximumEffect() + step.inputs().get("native_base_step_height") - 1.5) < 1e-7,
                "Step maximum must reach a real traversal boundary, without an imperceptible tail");
        check(step.inputs().get("marginal_power") < movement.inputs().get("marginal_power") / 2,
                "Cheap conditional step convenience must not dominate normalized broad movement power");
        check(flight.applicability() == BonusTrackDefinition.Applicability.AVAILABLE,
                "A supported native Bonus remains available independently of acquisition evidence");
        check(flight.evidence().stream().noneMatch(reason -> reason.contains("acquisition stage=")),
                "Removed acquisition inference leaked into Bonus output");
        check(vanilla.equals(resolve()), "Identical native inputs must generate deterministic tracks");
        check(vanilla.values().stream().map(BonusTrackDefinition::completionTier).distinct().count() > 1,
                "Useful native state counts and normalized impact naturally produce different completion tiers");
        long earlier = 0;
        for (var point : movement.checkpoints()) if (point.purchasable()) {
            check(point.segmentCost() > earlier, "Existing growing tier economic effort must survive semantic repricing");
            earlier = point.segmentCost();
        }
        check(vanilla.values().stream().filter(track -> track.category() == StatCategory.MOBILITY)
                .map(track -> track.checkpoints().getLast().cumulativeCap()).distinct().count() >= 5,
                "Same-category percentages must not automatically share costs");
        var resolvedProfile = new com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition(
                ResourceLocation.parse("test:smooth_resolved"), "Smooth resolved fixtures", caps(), Map.of(), fractions(), .8, vanilla);
        for (var track : vanilla.values()) {
            var semantic = BonusSemantics.require(EssenceStatRegistry.get(track.statId()).orElseThrow());
            check(track.inputs().get("breadth") == BonusSemantics.applicabilityBreadth(semantic),
                    "Pricing and placement share the same applicability policy: " + track.statId());
            double priorValue = 0;
            for (double value : track.activeValues()) {
                double floor = priorValue == 0 ? track.progressionRequirements().firstStateFloor()
                        : track.progressionRequirements().tierImprovementFloor();
                check(value - priorValue + 1e-9 >= floor, "Breadth cannot discount a native meaningful floor: " + track.statId());
                priorValue = value;
            }
            boolean started = false;
            for (var point : track.checkpoints()) {
                started |= point.tierId().equals(track.startTier());
                double value = BonusTrackCurve.progressionForInvestment(track.checkpoints(), track.investmentExponent(), Long.MAX_VALUE, point.tierId());
                check(started || value == 0, "Every bonus is neutral before its generated start: " + track.statId());
                check(value == point.effectFraction(), "Generated boundary uses this track's own checkpoint: " + track.statId());
            }
            check(track.purchaseStyle() == BonusTrackDefinition.PurchaseStyle.FUNDED_STATES && !track.snapPoints().isEmpty(),
                    "Generated Bonuses retain meaningful tier checkpoints");
            if (track.applicability() == BonusTrackDefinition.Applicability.AVAILABLE
                    && track.completionTier().equals(AscendanceTiers.TRANSCENDENT.id())) {
                var ascendant = track.checkpoint(AscendanceTiers.ASCENDANT.id());
                var transcendent = track.checkpoint(AscendanceTiers.TRANSCENDENT.id());
                check(transcendent.segmentCost() > 0 && transcendent.cumulativeCap() > ascendant.cumulativeCap()
                        && transcendent.effectFraction() > ascendant.effectFraction(), "Ordinary long tracks must add Transcendent capacity and effect");
                long midway = ascendant.cumulativeCap() + (transcendent.cumulativeCap() - ascendant.cumulativeCap()) / 2;
                double midwayEffect = StatScalingService.realizedProgressionForInvestment(
                        EssenceStatRegistry.get(track.statId()).orElseThrow(), midway, AscendanceTiers.TRANSCENDENT, resolvedProfile);
                check(midwayEffect > ascendant.effectFraction() && midwayEffect < transcendent.effectFraction(),
                        "Partial final-tier investment grants fractional additional power");
            }
            for (var point : track.checkpoints()) {
            double previous = -1;
            for (int sample = 0; sample <= 100; sample++) {
                long amount = point.cumulativeCap() * sample / 100;
                double fraction = BonusTrackCurve.progressionForInvestment(track.checkpoints(), track.investmentExponent(), amount, point.tierId());
                long inverse = BonusTrackCurve.investmentForProgression(track.checkpoints(), track.investmentExponent(), fraction, point.tierId());
                check(fraction >= previous, "Resolved segment curve decreased");
                check(Math.abs(amount - inverse) <= 1, "Resolved segment inverse failed round trip");
                var stat = EssenceStatRegistry.get(track.statId()).orElseThrow();
                var tier = AscendanceTierRegistry.get(point.tierId()).orElseThrow();
                check(TierInvestmentPolicy.validTarget(stat, tier, resolvedProfile, 0, amount),
                        "Smooth generated target was rejected by server purchase validation");
                double realized = StatScalingService.realizedProgressionForInvestment(stat, amount, tier, resolvedProfile);
                check(realized == fraction,
                        "Applied gameplay effect continuously follows the generated segment curve");
                previous = fraction;
            }

            }
        }
        var maxima = maxima(); var caps = caps(); var fractions = fractions();
        var supplyScaled = BonusTrackGenerator.resolve(BalanceSettings.defaults(), caps, fractions, maxima,
                Map.of(EssenceTypes.MOBILITY.id().toString(), 2.0), .8);
        check(supplyScaled.get(EssenceStats.STEP_HEIGHT.id()).inputs().get("marginal_power").equals(step.inputs().get("marginal_power")),
                "Category supply must not distort intrinsic normalized power");
        var json = new com.google.gson.JsonObject(); var profile = new com.google.gson.JsonObject();
        json.add("balanceProfile", profile); profile.add("bonusTracks", BonusTrackGenerator.toJson(vanilla));
        var mirrors = new com.google.gson.JsonObject(); var maximumJson = new com.google.gson.JsonObject();
        for (var track : vanilla.values()) {
            var mirror = new com.google.gson.JsonObject();
            track.checkpoints().forEach(point -> mirror.addProperty(point.tierId().toString(), point.cumulativeCap()));
            mirrors.add(track.statId().toString(), mirror); maximumJson.addProperty(track.statId().toString(), track.maximumEffect());
        }
        profile.add("statOverrides", mirrors); json.add("statMaxBonuses", maximumJson);
        String movementId = EssenceStats.MOVEMENT_SPEED.id().toString(), dormantId = movement.startTier().toString();
        long exactCap = movement.checkpoint(movement.startTier()).cumulativeCap() + 3;
        mirrors.getAsJsonObject(movementId).addProperty(dormantId, exactCap);
        BonusTrackGenerator.synchronizeExactOverrides(json, Map.of("/runtime/balanceProfile/statOverrides/" + movementId + "/" + dormantId, exactCap));
        var exactTrack = profile.getAsJsonObject("bonusTracks").getAsJsonObject(movementId);
        var dormant = exactTrack.getAsJsonArray("checkpoints").get(AscendanceTierRegistry.get(movement.startTier()).orElseThrow().order()).getAsJsonObject();
        check(dormant.get("cumulativeCap").getAsLong() == exactCap && dormant.get("segmentCost").getAsLong() == exactCap,
                "Exact per-stat caps must authoritatively reconcile track checkpoints and segment prices");
        check(exactTrack.get("source").getAsString().contains("exact_override"), "Exact Bonus cap provenance must survive resolution");
        System.out.println("BonusTrackGeneratorTest: " + checks + " generic costs, native thresholds, impact placement and curve checks PASS");
    }
    private static void sharedPlanner() {
        var damage = plannerMechanics(EssenceStats.MELEE_DAMAGE, 6, 5);
        var three = BonusProgressionPlanner.automatic(5, 5, 1, 1, .1, damage);
        check(three.start() == 2 && three.end() == 4 && three.values().equals(List.of(1.0, 2.6, 5.0)),
                "Raw-impact placement retains its span while distributing the remainder over meaningful later increments");
        check(BonusProgressionPlanner.configured(three.start(), three.end(), 5, 1, 1, .1, damage).equals(three),
                "The four emitted controls reproduce the exact accelerating curve");
        var shortDamage = BonusProgressionPlanner.automatic(5, 2.5, 1, 1, .1, damage);
        check(shortDamage.start() == 2 && shortDamage.end() == 3 && shortDamage.values().equals(List.of(1.0, 2.5)),
                "A smaller useful maximum completes before the final tier without front-loading its maximum");
        var health = BonusProgressionPlanner.automatic(5, 7, 1, .5, .5,
                plannerMechanics(EssenceStats.MAX_HEALTH, 20, 502));
        check(health.start() == 1 && health.values().equals(List.of(1.0, 2.5, 4.5, 7.0)),
                "Unconditional health preserves its ten-percent first tier and meaningful increasing curve");
        var equalImpactDamage = BonusProgressionPlanner.automatic(5, 5, 1, 1, .1,
                plannerMechanics(EssenceStats.MELEE_DAMAGE, 10, 100));
        check(equalImpactDamage.start() == health.start(), "Equal normalized health and damage gains receive the same placement");
        var recovery = BonusProgressionPlanner.automatic(5, .35, .1, .05, .01,
                plannerMechanics(EssenceStats.HEALTH_REGENERATION, 20.0 / 10, 100));
        check(recovery.start() == health.start(),
                "A tenth of a heart per second over ten comparison seconds matches the impact of one extra heart");
        check(recovery.values().equals(List.of(.1, .2, .35)),
                "Internal decimal growth retains the feasible third state instead of rounding 0.15 to 0.16");
        check(BonusProgressionPlanner.configured(0, 2, 5.6, 1, 1, .1,
                        plannerMechanics(EssenceStats.MELEE_DAMAGE, 6, 10)).values().equals(List.of(1.0, 2.9, 5.6)),
                "A tenths-grid distributed target of 2.9 cannot truncate to 2.8 through binary division");
        check(BonusProgressionPlanner.configured(0, 2, .57, .1, .1, .01,
                        plannerMechanics(EssenceStats.HEALTH_REGENERATION, 2, 1)).values().equals(List.of(.1, .29, .57)),
                "A hundredths-grid distributed target of 0.29 cannot truncate to 0.28 through binary division");
        var exactBoundary = new BonusProgressionPlanner.Mechanics() {
            public double ceiling(int tier) { return .29; }
            public boolean improves(int tier, double previous, double value) { return previous == 0 && value == .29; }
            public double impact(int tier, double value) { return .01; }
        };
        check(BonusProgressionPlanner.automatic(5, .29, .1, .1, .01, exactBoundary).values().equals(List.of(.29)),
                "A native boundary exactly on a fractional grid is retained by the ceiling search");
        var resistance = BonusProgressionPlanner.automatic(5, 10, 5, 5, 1,
                plannerMechanics(EssenceStats.MELEE_RESISTANCE, 1, 80));
        check(resistance.start() == 1 && resistance.end() == 2,
                "Five-percent prevention uses its effective-health impact and a short useful span");
        var nativeCap = BonusProgressionPlanner.automatic(5, 100, 10, 5, 1,
                plannerMechanics(EssenceStats.MOVEMENT_SPEED, 1, 20));
        check(nativeCap.start() == 1 && nativeCap.end() == 2 && nativeCap.values().equals(List.of(10.0, 20.0)),
                "A native cap terminates a useful track without filling later tiers");
        var single = BonusProgressionPlanner.automatic(5, 19, 10, 10, 1,
                plannerMechanics(EssenceStats.MOVEMENT_SPEED, 1, 100));
        check(single.start() == 2 && single.values().equals(List.of(19.0)),
                "Placement measures the published single-state value rather than its smaller authored floor");
        var restricted = new BonusProgressionPlanner.Mechanics() {
            public double ceiling(int tier) { return tier <= 1 ? 100 : 0; }
            public boolean improves(int tier, double previous, double value) {
                return tier <= 1 && value > previous && value <= ceiling(tier);
            }
            public double impact(int tier, double value) { return value / 100; }
        };
        var fittingFloor = BonusProgressionPlanner.automatic(5, 100, 10, 5, 1, restricted);
        check(fittingFloor.start() == 1 && fittingFloor.values().equals(List.of(10.0)),
                "A budget-fitting first-only route takes precedence over an unnecessary earlier floor exception");
        boolean invalid = false;
        try { BonusProgressionPlanner.configured(0, 1, 20.5, 10, 5, 1,
                plannerMechanics(EssenceStats.MOVEMENT_SPEED, 1, 100)); }
        catch (IllegalArgumentException expected) { invalid = expected.getMessage().contains("unit grid"); }
        check(invalid, "An explicit unrepresentable maximum fails instead of silently rounding");
        boolean saturated = false;
        try { BonusProgressionPlanner.automatic(5, 5, 1, 1, .1, plannerMechanics(EssenceStats.MELEE_DAMAGE, 6, 0)); }
        catch (IllegalArgumentException expected) { saturated = true; }
        check(saturated, "A favorable placement score cannot bypass absent native headroom");
        for (var plan : List.of(three, shortDamage, health, equalImpactDamage, resistance, nativeCap)) {
            double previous = 0, increment = 0;
            for (double value : plan.values()) {
                check(value - previous + 1e-9 >= increment, "Later completed increments never become smaller");
                increment = value - previous; previous = value;
            }
        }
    }
    private static void breadthPlacement() {
        var maximum = new TreeMap<>(maxima());
        maximum.put(EssenceStats.BREATH_HOLD.id(), 62.9);
        maximum.put(EssenceStats.JUMP_HEIGHT.id(), 52.0);
        maximum.put(EssenceStats.FISHING_SPEED.id(), 104.0);
        maximum.put(EssenceStats.CROP_YIELD.id(), 104.0);
        var nominal = BonusTrackGenerator.resolve(BalanceSettings.defaults(), caps(), fractions(), maximum, Map.of(), .8);
        var resolved = new TreeMap<ResourceLocation, BonusTrackDefinition>();
        nominal.forEach((id, track) -> resolved.put(id, BonusTrackGenerator.meaningful(track, track.progressionRequirements())));
        var breath = resolved.get(EssenceStats.BREATH_HOLD.id());
        check(breath.startTier().equals(AscendanceTiers.RESONANT.id())
                        && projectedValues(breath).equals(List.of(5.0, 28.2, 62.9)),
                "Shared applicability advances air capacity and spreads its large remaining maximum; actual=" + breath.activeValues()
                        + ", start=" + breath.startTier());
        var jump = resolved.get(EssenceStats.JUMP_HEIGHT.id());
        check(jump.startTier().equals(AscendanceTiers.RESONANT.id())
                        && projectedValues(jump).equals(List.of(10.0, 27.0, 52.0)),
                "Squared jump response uses shared applicability and a distributed final remainder; actual=" + jump.activeValues()
                        + ", start=" + jump.startTier());
        var fishing = resolved.get(EssenceStats.FISHING_SPEED.id());
        check(fishing.startTier().equals(AscendanceTiers.DORMANT.id())
                        && projectedValues(fishing).equals(List.of(10.0, 22.0, 40.0, 66.0, 104.0)),
                "An earlier useful start can support five native states without a large final dump; actual=" + fishing.activeValues()
                        + ", start=" + fishing.startTier());
        var crop = resolved.get(EssenceStats.CROP_YIELD.id());
        check(crop.startTier().equals(AscendanceTiers.AWAKENED.id())
                        && projectedValues(crop).equals(List.of(20.0, 54.0, 104.0)),
                "A crop response uses the same shared scope policy and later interval weights; actual=" + crop.activeValues()
                        + ", start=" + crop.startTier());
        double oneCondition = BonusSemantics.applicabilityBreadth(BonusSemantics.require(EssenceStats.CROP_YIELD));
        for (var stat : List.of(EssenceStats.MELEE_DAMAGE, EssenceStats.RANGED_DAMAGE, EssenceStats.MAGIC_DAMAGE,
                EssenceStats.JUMP_HEIGHT, EssenceStats.HEALTH_REGENERATION))
            check(BonusSemantics.applicabilityBreadth(BonusSemantics.require(stat)) == oneCondition,
                    "Equal declared scope receives equal breadth without a category or Bonus exemption: " + stat.id());
        check(BonusSemantics.applicabilityBreadth(BonusSemantics.require(EssenceStats.MAX_HEALTH)) == 1,
                "An unconditional native benefit receives no breadth discount");
        for (var track : List.of(breath, jump, fishing, crop)) {
            var values = track.activeValues();
            check(values.getFirst() == track.progressionRequirements().firstStateFloor(),
                    "Tail distribution keeps the first native minimum fixed");
            double previous = 0, increment = 0;
            for (double value : values) {
                check(value - previous + 1e-9 >= increment, "Distributed native increments remain nondecreasing");
                increment = value - previous; previous = value;
            }
        }
    }
    private static List<Double> projectedValues(BonusTrackDefinition track) {
        double quantum = track.inputs().containsKey("native_base_step_height") ? .000001 : RuntimeValueQuantization.statStep(track.unit());
        return track.activeValues().stream().map(value -> RuntimeValueQuantization.reconstructedGridValue(value, quantum)).toList();
    }
    private static BonusProgressionPlanner.Mechanics plannerMechanics(StatDefinition stat, double baseline, double cap) {
        return new BonusProgressionPlanner.Mechanics() {
            public double ceiling(int tier) { return cap; }
            public boolean improves(int tier, double previous, double value) { return value > previous && value <= cap; }
            public double impact(int tier, double value) { return BonusSemantics.normalizedImpact(BonusSemantics.require(stat), value, baseline); }
        };
    }
    private static void nativeSaturation() {
        var strong = new TreeMap<>(maxima());
        var bounded = List.of(EssenceStats.GUARDED_MOVEMENT, EssenceStats.GUARD_READINESS,
                EssenceStats.SWIM_SPEED, EssenceStats.SNEAK_SPEED, EssenceStats.ATTACK_KNOCKBACK,
                EssenceStats.KNOCKBACK_RESISTANCE, EssenceStats.MAX_HEALTH, EssenceStats.REACH, EssenceStats.LUCK,
                EssenceStats.MOVEMENT_SPEED, EssenceStats.MINING_SPEED, EssenceStats.JUMP_HEIGHT);
        for (var stat : bounded) strong.put(stat.id(), 1_000_000.0);
        var resolved = BonusTrackGenerator.resolve(BalanceSettings.defaults(), caps(), fractions(),
                strong, Map.of(), .8);
        for (var stat : bounded) {
            var track = BonusTrackGenerator.meaningful(resolved.get(stat.id()),
                    com.mistaboom.essence_ascendance.skill.ProgressionRequirements.bonus(stat.id()));
            double maximum = NativeBonusMechanics.limit(stat).orElseThrow().maximum();
            check(track.maximumEffect() <= maximum, "Strong requested allocations exceed native saturation: " + stat.id());
            check(track.inputs().get("native_maximum_effect") == maximum, "Saved native ceiling missing: " + stat.id());
            double prior = 0;
            for (double value : track.activeValues()) {
                check(value - prior + 1e-9 >= (prior == 0 ? track.progressionRequirements().firstStateFloor()
                        : track.progressionRequirements().tierImprovementFloor()), "Native bounded states lost authored floors: " + stat.id());
                prior = value;
            }
        }
        var readiness = BonusTrackGenerator.meaningful(resolved.get(EssenceStats.GUARD_READINESS.id()),
                com.mistaboom.essence_ascendance.skill.ProgressionRequirements.bonus(EssenceStats.GUARD_READINESS.id()));
        int previous = NativeBonusMechanics.NATIVE_SHIELD_DISABLE_TICKS;
        for (double percent : readiness.activeValues()) {
            int ticks = com.mistaboom.essence_ascendance.equipment.ShieldMath.disableTicks(100, percent, 20);
            check(previous - ticks >= 10, "Purchased guard tier must save native cooldown ticks after quantization");
            previous = ticks;
        }
        check(previous == 20 && readiness.maximumEffect() == 80,
                "Guard completion must stop at the actual vanilla timing ceiling");
    }
    private static Map<ResourceLocation, BonusTrackDefinition> resolve() {
        var result = new TreeMap<ResourceLocation, BonusTrackDefinition>();
        BonusTrackGenerator.resolve(BalanceSettings.defaults(), caps(), fractions(), maxima(), Map.of(), .8)
                .forEach((id, track) -> result.put(id, BonusTrackGenerator.meaningful(track, track.progressionRequirements())));
        return result;
    }
    private static Map<ResourceLocation, Long> caps() {
        Map<ResourceLocation, Long> values = new LinkedHashMap<>();
        long[] caps = {0, 1000, 4000, 14000, 50000, 180000}; int index = 0;
        for (var tier : AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList()) values.put(tier.id(), caps[index++]);
        return values;
    }
    private static Map<ResourceLocation, Double> fractions() {
        Map<ResourceLocation, Double> values = new LinkedHashMap<>();
        double[] fractions = {0, .12, .27, .46, .7, 1}; int index = 0;
        for (var tier : AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList()) values.put(tier.id(), fractions[index++]);
        return values;
    }
    private static Map<ResourceLocation, Double> maxima() {
        Map<ResourceLocation, Double> values = new LinkedHashMap<>();
        for (var stat : EssenceStatRegistry.values()) values.put(stat.id(), StatScalingDefaults.get(stat.id()).orElseThrow());
        values.put(EssenceStats.STEP_HEIGHT.id(), 1.0); values.put(EssenceStats.MOVEMENT_SPEED.id(), 52.0);
        return values;
    }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); checks++; }
}

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
        nativeSaturation();
        var movement = vanilla.get(EssenceStats.MOVEMENT_SPEED.id());
        var step = vanilla.get(EssenceStats.STEP_HEIGHT.id());
        var flight = vanilla.get(EssenceStats.FLIGHT_SPEED.id());
        check(step.checkpoints().getLast().cumulativeCap() < movement.checkpoints().getLast().cumulativeCap() / 5,
                "Step must be materially cheaper than broadly applicable movement");
        check(step.activeStateCount() == 2 && step.completionTier().equals(AscendanceTiers.TRANSCENDENT.id()), "Step uses the common final-tier placement rule for its two useful native states");
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
        check(vanilla.values().stream().allMatch(track -> track.completionTier().equals(AscendanceTiers.TRANSCENDENT.id())),
                "All ordinary tracks use the same final-tier placement preference; native saturation is tested separately");
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
        System.out.println("BonusTrackGeneratorTest: " + checks + " generic costs, native thresholds, common placement and curve checks PASS");
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

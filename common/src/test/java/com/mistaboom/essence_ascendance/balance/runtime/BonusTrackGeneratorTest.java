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

/** Real-registry resolution across distinct capability environments; no named-pack or inventory fixtures. */
public final class BonusTrackGeneratorTest {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        var vanilla = resolve(evidence(List.of()));
        var movement = vanilla.get(EssenceStats.MOVEMENT_SPEED.id());
        var step = vanilla.get(EssenceStats.STEP_HEIGHT.id());
        var flight = vanilla.get(EssenceStats.FLIGHT_SPEED.id());
        check(step.checkpoints().getLast().cumulativeCap() < movement.checkpoints().getLast().cumulativeCap() / 5,
                "Step must be materially cheaper than broadly applicable movement");
        check(step.completionTier().equals(AscendanceTiers.AWAKENED.id()), "Two useful block boundaries should complete in two segments");
        check(step.snapPoints().isEmpty() && step.purchaseStyle() == BonusTrackDefinition.PurchaseStyle.CONTINUOUS,
                "Step Height must use the same smooth purchase behavior as every other Bonus");
        check(step.inputs().containsKey("native_threshold_1") && step.inputs().containsKey("native_threshold_2"),
                "Native traversal thresholds should remain diagnostic valuation inputs");
        check(Math.abs(step.maximumEffect() + step.inputs().get("native_base_step_height") - 1.5) < 1e-7,
                "Step maximum must reach a real traversal boundary, without an imperceptible tail");
        check(step.inputs().get("marginal_power") < movement.inputs().get("marginal_power") / 2,
                "Cheap conditional step convenience must not dominate normalized broad movement power");
        check(flight.startTier().equals(AscendanceTiers.RESONANT.id()), "Vanilla-like profile reserves explicitly declared native standard flight at catalog tier");
        check(flight.evidence().stream().anyMatch(reason -> reason.contains("; IMPLEMENTED: explicit standard-flight")),
                "Implemented standard flight must advertise implemented capability evidence");
        check(flight.evidence().stream().noneMatch(reason -> reason.contains("DECLARED_NOT_IMPLEMENTED")),
                "Implemented standard flight must not retain declaration-only capability evidence");
        var early = resolve(evidence(List.of(capability("test:verified_route", ProgressionBand.ENTRY, true, .95,
                Map.of(CapabilityAxis.FLIGHT, 1.0, CapabilityAxis.ABILITIES_FLYING_SPEED, 1.0))))).get(EssenceStats.FLIGHT_SPEED.id());
        check(early.startTier().equals(AscendanceTiers.DORMANT.id()), "Verified early standard flight must unlock early");
        var glidingEvidence = evidence(List.of(capability("test:glider", ProgressionBand.ENTRY, true, 1,
                Map.of(CapabilityAxis.GLIDING, 1.0))));
        check(resolve(glidingEvidence).get(EssenceStats.FLIGHT_SPEED.id()).startTier().equals(AscendanceTiers.RESONANT.id()),
                "Gliding must not shift standard-flight tier");
        check(BonusTrackGenerator.flightRoute(glidingEvidence, false).stage() == null, "Gliding-only environment without native declarations must stay unavailable");
        var lateResource = new ResourceEvidence("test:verified_route", ProgressionBand.LATE, Availability.FINITE,
                Automation.NONE, true, true, 1, .95, List.of(), List.of());
        var claimedEarly = capability("test:verified_route", ProgressionBand.ENTRY, true, .95,
                Map.of(CapabilityAxis.FLIGHT, 1.0, CapabilityAxis.ABILITIES_FLYING_SPEED, 1.0));
        var acquisitionConflict = new PackEvidence(Map.of(lateResource.itemId(), lateResource), List.of(), List.of(),
                Map.of(), List.of(), List.of(), Map.of(), List.of(claimedEarly));
        check(BonusTrackGenerator.flightRoute(acquisitionConflict, false).stage() == ProgressionBand.LATE,
                "Early capability claims cannot bypass a later resource acquisition stage");
        var uncertainClaim = new EvidenceFact(EvidenceFact.Subject.ITEM, lateResource.itemId(), "axis.ABILITIES_FLYING_SPEED",
                EvidenceFact.Value.number(1), "test:uncertain_mechanism", EvidenceFact.Origin.INFERRED, .1, 0,
                ProgressionBand.ENTRY, List.of(), "Unverified dynamic flight behavior");
        var equipmentDuplicate = new EquipmentReference(lateResource.itemId(), "chest", ProgressionBand.ENTRY,
                claimedEarly.axes(), List.of(), true, true, .95, "Reliable physical equipment measurements");
        var uncertainEquipment = new PackEvidence(Map.of(lateResource.itemId(), lateResource), List.of(equipmentDuplicate),
                List.of(), Map.of(), List.of(uncertainClaim), List.of(), Map.of(), List.of(claimedEarly));
        check(BonusTrackGenerator.flightRoute(uncertainEquipment, false).stage() == null,
                "Equipment duplicates cannot launder uncertain winning mechanism evidence");
        for (var opaque : List.of(
                capability("test:jetpack_named_but_unknown", ProgressionBand.ENTRY, true, 1, Map.of(CapabilityAxis.FLIGHT, 1.0)),
                capability("test:uncertain", ProgressionBand.ENTRY, true, .3, Map.of(CapabilityAxis.FLIGHT, 1.0, CapabilityAxis.ABILITIES_FLYING_SPEED, 1.0)),
                capability("test:unreachable", ProgressionBand.ENTRY, false, 1, Map.of(CapabilityAxis.FLIGHT, 1.0, CapabilityAxis.ABILITIES_FLYING_SPEED, 1.0)))) {
            var opaqueEvidence = evidence(List.of(opaque));
            check(BonusTrackGenerator.flightRoute(opaqueEvidence, false).stage() == null, "Opaque/unreliable/unreachable route must fail conservatively");
            check(resolve(opaqueEvidence).get(EssenceStats.FLIGHT_SPEED.id()).startTier().equals(AscendanceTiers.RESONANT.id()),
                    "Opaque source must not displace the declared route");
        }
        check(vanilla.equals(resolve(evidence(List.of()))), "Identical evidence must generate deterministic tracks");
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
            check(track.purchaseStyle() == BonusTrackDefinition.PurchaseStyle.CONTINUOUS && track.snapPoints().isEmpty(),
                    "Generated Bonuses must never acquire threshold-only purchase gates");
            if (track.applicability() == BonusTrackDefinition.Applicability.AVAILABLE
                    && track.completionTier().equals(AscendanceTiers.TRANSCENDENT.id())) {
                var ascendant = track.checkpoint(AscendanceTiers.ASCENDANT.id());
                var transcendent = track.checkpoint(AscendanceTiers.TRANSCENDENT.id());
                check(transcendent.segmentCost() > 0 && transcendent.cumulativeCap() > ascendant.cumulativeCap()
                        && transcendent.effectFraction() > ascendant.effectFraction(), "Ordinary long tracks must add Transcendent capacity and effect");
                long midway = ascendant.cumulativeCap() + (transcendent.cumulativeCap() - ascendant.cumulativeCap()) / 2;
                double midwayEffect = BonusTrackCurve.progressionForInvestment(track.checkpoints(), track.investmentExponent(),
                        midway, AscendanceTiers.TRANSCENDENT.id());
                check(midwayEffect > ascendant.effectFraction() && midwayEffect < 1,
                        "Final tier segment must remain purchasable between its endpoints");
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
                check(StatScalingService.realizedProgressionForInvestment(stat, amount, tier, resolvedProfile) == fraction,
                        "Smooth generated effect was quantized to a native threshold");
                previous = fraction;
            }
            if (point.tierId().equals(AscendanceTiers.TRANSCENDENT.id()) && track == step)
                check(point.segmentCost() == 0 && !point.purchasable(), "Completed tracks cannot charge later tiers");
            }
        }
        var maxima = maxima(); var caps = caps(); var fractions = fractions();
        var supplyScaled = BonusTrackGenerator.resolve(evidence(List.of()), BalanceSettings.defaults(), caps, fractions, maxima,
                Map.of(EssenceTypes.MOBILITY.id().toString(), 2.0), .8);
        check(supplyScaled.get(EssenceStats.STEP_HEIGHT.id()).inputs().get("marginal_power").equals(step.inputs().get("marginal_power")),
                "Category supply must not distort intrinsic normalized power");
        var parsed = BalanceOverrides.parse("schema_version=1\n[[fact]]\nid=\"verified\"\nkind=\"capability\"\nselector=\"test:route\"\nflight=true\nflying_speed_compatible=true\nconfidence=0.95\nstage=\"entry\"\nreason=\"Verified Abilities flight implementation\"\n", "test");
        check(parsed.facts().getFirst().flag("flying_speed_compatible").orElse(false), "Factual overrides need explicit speed mechanism compatibility");
        var json = new com.google.gson.JsonObject(); var profile = new com.google.gson.JsonObject();
        json.add("balanceProfile", profile); profile.add("bonusTracks", BonusTrackGenerator.toJson(vanilla));
        var mirrors = new com.google.gson.JsonObject(); var maximumJson = new com.google.gson.JsonObject();
        for (var track : vanilla.values()) {
            var mirror = new com.google.gson.JsonObject();
            track.checkpoints().forEach(point -> mirror.addProperty(point.tierId().toString(), point.cumulativeCap()));
            mirrors.add(track.statId().toString(), mirror); maximumJson.addProperty(track.statId().toString(), track.maximumEffect());
        }
        profile.add("statOverrides", mirrors); json.add("statMaxBonuses", maximumJson);
        String movementId = EssenceStats.MOVEMENT_SPEED.id().toString(), dormantId = AscendanceTiers.DORMANT.id().toString();
        long exactCap = movement.checkpoint(AscendanceTiers.DORMANT.id()).cumulativeCap() + 3;
        mirrors.getAsJsonObject(movementId).addProperty(dormantId, exactCap);
        BonusTrackGenerator.synchronizeExactOverrides(json, Map.of("/runtime/balanceProfile/statOverrides/" + movementId + "/" + dormantId, exactCap));
        var exactTrack = profile.getAsJsonObject("bonusTracks").getAsJsonObject(movementId);
        var dormant = exactTrack.getAsJsonArray("checkpoints").get(1).getAsJsonObject();
        check(dormant.get("cumulativeCap").getAsLong() == exactCap && dormant.get("segmentCost").getAsLong() == exactCap,
                "Exact per-stat caps must authoritatively reconcile track checkpoints and segment prices");
        check(exactTrack.get("source").getAsString().contains("exact_override"), "Exact Bonus cap provenance must survive resolution");
        System.out.println("BonusTrackGeneratorTest: " + checks + " semantic costs, native thresholds, flight compatibility and curve checks PASS");
    }
    private static Map<ResourceLocation, BonusTrackDefinition> resolve(PackEvidence evidence) {
        return BonusTrackGenerator.resolve(evidence, BalanceSettings.defaults(), caps(), fractions(), maxima(), Map.of(), .8);
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
    private static PackEvidence evidence(List<CapabilityEvidence> capabilities) { return new PackEvidence(Map.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of(), capabilities); }
    private static CapabilityEvidence capability(String id, ProgressionBand band, boolean reachable, double confidence, Map<CapabilityAxis, Double> axes) {
        return new CapabilityEvidence(id, band, axes, reachable, confidence, "explicit test provider contract");
    }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); checks++; }
}

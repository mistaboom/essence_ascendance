package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.attunement.AttunementService;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition;
import com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition.*;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.data.SkillPurchase;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.network.AscendanceNexusTransactionResultPayload.Status;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import java.util.*;

/** Real-registry invariants for per-track economics, smooth purchase/effect realization and normalized development. */
public final class BonusTrackProgressionTest {
    private static int assertions;
    private static final long[] COSTS = {0, 100, 400, 1600, 6400, 25600};
    private static final double[] FRACTIONS = {0, .1, .25, .45, .7, 1};
    private static final double EXPONENT = .62;
    private static List<AscendanceTierDefinition> tiers;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        tiers = AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        var profile = profile(1);
        curves(profile);
        continuousAndAuthority(profile);
        completeTransactionPlanning(profile);
        developmentAndReceipts(profile);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("BonusTrackProgressionTest: " + assertions + " checks PASS");
    }

    private static void curves(BalanceProfileDefinition profile) {
        for (var stat : EssenceStatRegistry.values()) {
            var track = profile.bonusTrack(stat.id());
            for (var tier : tiers) {
                long cap = profile.getInvestmentCap(tier, stat);
                double previous = -1;
                for (int sample = 0; sample <= 1000; sample++) {
                    long amount = cap * sample / 1000;
                    double fraction = StatScalingService.progressionForInvestment(stat, amount, tier, profile);
                    check(fraction >= previous, "Curve decreases for " + stat.id()); previous = fraction;
                    check(Math.abs(StatScalingService.investmentForProgression(stat, fraction, tier, profile) - amount) <= 1,
                            "Piecewise inverse changes integer investment");
                    check(fraction <= track.checkpoint(tier.id()).effectFraction(), "Curve exceeds own tier ceiling");
                }
                check(StatScalingService.progressionForInvestment(stat, Long.MAX_VALUE, tier, profile)
                                == track.checkpoint(tier.id()).effectFraction(), "Demotion does not clamp effective progression");
            }
        }
        var movement = profile.bonusTrack(EssenceStats.MOVEMENT_SPEED.id());
        check(movement.checkpoints().getLast().segmentCost() > movement.checkpoints().get(1).segmentCost() * 100,
                "Late-tier economic growth flattened");
        double halfway = StatScalingService.progressionForInvestment(EssenceStats.MOVEMENT_SPEED, 50,
                AscendanceTiers.DORMANT, profile);
        check(halfway > .05 && halfway < .1, "Within-segment diminishing returns lost");
        for (var stat : List.of(EssenceStats.MOVEMENT_SPEED, EssenceStats.FLIGHT_SPEED)) {
            long previousTierCap = profile.getInvestmentCap(AscendanceTiers.ASCENDANT, stat);
            long apexCap = profile.getInvestmentCap(AscendanceTiers.TRANSCENDENT, stat);
            check(apexCap > previousTierCap, "Long Bonus track lost its Transcendent capacity");
            check(TierInvestmentPolicy.validTarget(stat, AscendanceTiers.TRANSCENDENT, profile, previousTierCap, previousTierCap + 1),
                    "Transcendent cannot continue a previously full Ascendant track");
            check(StatScalingService.realizedProgressionForInvestment(stat, previousTierCap + 1, AscendanceTiers.TRANSCENDENT, profile)
                            > StatScalingService.maximumProgression(stat, AscendanceTiers.ASCENDANT, profile),
                    "First Transcendent investment failed to add an effect");
            check(StatScalingService.realizedProgressionForInvestment(stat, apexCap, AscendanceTiers.TRANSCENDENT, profile) == 1,
                    "Long Bonus track cannot reach its final effect endpoint");
        }
        for (var tier : List.of(AscendanceTiers.LATENT, AscendanceTiers.DORMANT, AscendanceTiers.AWAKENED)) {
            check(profile.getInvestmentCap(tier, EssenceStats.FLIGHT_SPEED) == 0, "Delayed track has early purchase capacity");
            check(StatScalingService.maximumProgression(EssenceStats.FLIGHT_SPEED, tier, profile) == 0,
                    "Delayed track uses global tier ceiling");
        }
        for (var tier : List.of(AscendanceTiers.RESONANT, AscendanceTiers.ASCENDANT, AscendanceTiers.TRANSCENDENT)) {
            check(profile.getInvestmentCap(tier, EssenceStats.STEP_HEIGHT) == 90, "Completed track charges later tier");
            check(StatScalingService.maximumProgression(EssenceStats.STEP_HEIGHT, tier, profile) == 1,
                    "Completed track fails to reach full effect");
        }
        for (var tier : tiers) check(StatScalingService.maximumProgression(EssenceStats.SWIM_SPEED, tier, profile) == 0,
                "Unavailable track has power");
    }

    private static void continuousAndAuthority(BalanceProfileDefinition profile) {
        var step = profile.bonusTrack(EssenceStats.STEP_HEIGHT.id());
        var apex = AscendanceTiers.TRANSCENDENT;
        check(step.purchaseStyle() == PurchaseStyle.CONTINUOUS && step.snapPoints().isEmpty(), "Step fixture must use uniform continuous purchase behavior");
        double previous = -1;
        for (long amount = 0; amount <= step.checkpoint(apex.id()).cumulativeCap(); amount++) {
            check(TierInvestmentPolicy.validTarget(EssenceStats.STEP_HEIGHT, apex, profile, 0, amount),
                    "Authoritative validator rejected a partial Step purchase");
            double raw = StatScalingService.progressionForInvestment(EssenceStats.STEP_HEIGHT, amount, apex, profile);
            double realized = StatScalingService.realizedProgressionForInvestment(EssenceStats.STEP_HEIGHT, amount, apex, profile);
            check(realized == raw && realized > previous, "Applied Step effect snapped or stopped increasing between tier checkpoints");
            check(Math.abs(StatScalingService.investmentForProgression(EssenceStats.STEP_HEIGHT, realized, apex, profile) - amount) <= 1,
                    "Smooth Step purchase/effect inverse changed integer investment");
            previous = realized;
        }
        check(TierInvestmentPolicy.validTarget(EssenceStats.STEP_HEIGHT, apex, profile, 0, 1), "Single-Essence partial Step purchase rejected");
        check(!TierInvestmentPolicy.validTarget(EssenceStats.STEP_HEIGHT, apex, profile, 0, 91), "Cap overflow accepted");
        check(!TierInvestmentPolicy.validTarget(EssenceStats.STEP_HEIGHT, apex, profile, 0, -1), "Negative refund target accepted");
        check(TierInvestmentPolicy.validTarget(EssenceStats.STEP_HEIGHT, apex, profile, 1, 1), "Untouched stored allocation destroyed");
        check(TierInvestmentPolicy.validTarget(EssenceStats.STEP_HEIGHT, AscendanceTiers.DORMANT, profile, 90, 40),
                "Demotion over-cap refund rejected");
        check(TierInvestmentPolicy.validTarget(EssenceStats.STEP_HEIGHT, AscendanceTiers.DORMANT, profile, 90, 0), "Full refund rejected");
        check(!TierInvestmentPolicy.validTarget(EssenceStats.STEP_HEIGHT, AscendanceTiers.DORMANT, profile, 90, 91),
                "Demotion allows over-cap increase");
        check(!TierInvestmentPolicy.validTarget(EssenceStats.FLIGHT_SPEED, AscendanceTiers.DORMANT, profile, 0, 1),
                "Delayed capability permits early purchase");
        check(TierInvestmentPolicy.validTarget(EssenceStats.MOVEMENT_SPEED, apex, profile, 0, 123), "Continuous purchase quantized");

        var player = player(Map.of(EssenceStats.STEP_HEIGHT.id(), 90L));
        long before = player.nexusRevision();
        check(player.applyNexusTransaction(Map.of(EssenceStats.MOVEMENT_SPEED.id(), 73L), Map.of(EssenceTypes.MOBILITY.id(), 17L),
                        player.getOwnedSkills(), player.getLoadoutSelections(), player.getTierId()), "Atomic refund/reallocation failed");
        check(player.nexusRevision() == before + 1 && player.getInvested(EssenceStats.STEP_HEIGHT) == 0
                        && player.getInvested(EssenceStats.MOVEMENT_SPEED) == 73 && player.getAvailable(EssenceTypes.MOBILITY) == 17,
                "Exact refund and final target conservation failed");
    }

    private static void developmentAndReceipts(BalanceProfileDefinition profile) {
        String category = EssenceTypes.MOBILITY.id().toString();
        var original = player(Map.of(EssenceStats.MOVEMENT_SPEED.id(), 6400L, EssenceStats.STEP_HEIGHT.id(), 90L));
        var repriced = player(Map.of(EssenceStats.MOVEMENT_SPEED.id(), 640000L, EssenceStats.STEP_HEIGHT.id(), 9000L));
        var expensive = profile(100);
        double development = BonusDevelopment.fraction(original, category, profile);
        check(Math.abs(development - BonusDevelopment.fraction(repriced, category, expensive)) < 1e-12,
                "Identical realized Bonus power changed when prices multiplied by 100");
        check(AttunementService.investment(original, category, profile) == AttunementService.investment(repriced, category, expensive),
                "Repricing changed Attunement acceleration");
        var cheapOnly = player(Map.of(EssenceStats.STEP_HEIGHT.id(), 90L));
        check(BonusDevelopment.fraction(cheapOnly, category, profile) < .06, "Cheap Step Bonus dominates category development");
        var developed = player(Map.of(EssenceStats.MOVEMENT_SPEED.id(), 25600L, EssenceStats.STEP_HEIGHT.id(), 90L,
                EssenceStats.FLIGHT_SPEED.id(), 6400L));
        check(BonusDevelopment.fraction(developed, category, profile) == 1, "Unavailable or early-completed tracks suppress full acceleration");
        developed.setTier(AscendanceTiers.DORMANT);
        check(BonusDevelopment.fraction(developed, category, profile) == 1, "Demotion clamps effects but loses current-tier development");
        var locked = player(Map.of(EssenceStats.FLIGHT_SPEED.id(), 6400L)); locked.setTier(AscendanceTiers.DORMANT);
        check(BonusDevelopment.fraction(locked, category, profile) == 0, "Stored delayed investment accelerates before start");
        long bonus = AttunementService.investment(original, category, profile);
        original.recordSkillPurchase(ResourceLocation.parse("test:historical_skill"),
                new SkillPurchase(EssenceTypes.MOBILITY.id(), List.of(17L, 31L, 79L)));
        check(AttunementService.investment(original, category, profile) == bonus + 127,
                "Historical skill receipts were normalized or repriced");
        original.setAvailable(EssenceTypes.MOBILITY, Long.MAX_VALUE);
        check(AttunementService.investment(original, category, profile) == bonus + 127, "Wallet accelerated Attunement");
    }

    private static void completeTransactionPlanning(BalanceProfileDefinition profile) {
        var player = player(Map.of(EssenceStats.STEP_HEIGHT.id(), 90L));
        var before = player.save();
        Map<ResourceLocation, Long> targets = new LinkedHashMap<>();
        EssenceStatRegistry.values().forEach(stat -> targets.put(stat.id(), player.getInvested(stat)));
        targets.put(EssenceStats.STEP_HEIGHT.id(), 0L);
        targets.put(EssenceStats.MOVEMENT_SPEED.id(), 73L);
        var result = BonusTransactionPlan.resolve(player, profile, targets);
        check(result.accepted(), "Complete mixed refund/spend proposal rejected");
        var plan = result.plan();
        ResourceLocation mobility = EssenceTypes.MOBILITY.id();
        check(plan.refunds().get(mobility) == 90 && plan.spending().get(mobility) == 73,
                "Planner charged slider movement instead of exact final target difference");
        check(plan.currentCategoryTotals().get(mobility) == 90 && plan.targetCategoryTotals().get(mobility) == 73,
                "Projected skill allocation totals changed semantics");
        var settlement = plan.settle(player.getAllAvailable(), Map.of(), Map.of());
        check(settlement.accepted() && settlement.balances().get(mobility) == 17, "Exact Bonus refund funding lost");
        check(player.save().equals(before), "Production planning mutated player data before atomic commit");
        check(plan.settle(Map.of(), Map.of(), Map.of(mobility, 18L)).status() == Status.INSUFFICIENT_ESSENCE,
                "Bonus refund double-spent on skills");
        var crossFunded = plan.settle(Map.of(), Map.of(mobility, 10L), Map.of(mobility, 22L));
        check(crossFunded.accepted() && crossFunded.balances().get(mobility) == 5,
                "Same-transaction historical skill refund cannot fund final Bonus/skill targets");
        check(plan.settle(Map.of(mobility, Long.MAX_VALUE), Map.of(), Map.of()).status() == Status.INVALID_PROPOSAL,
                "Refund/wallet overflow accepted");
        check(player.save().equals(before), "Failed settlement partially refunded or spent Essence");

        var missing = new LinkedHashMap<>(targets); missing.remove(EssenceStats.STEP_HEIGHT.id());
        check(BonusTransactionPlan.resolve(player, profile, missing).status() == Status.INCOMPLETE_BONUS_STATE,
                "Missing registered Bonus target accepted");
        missing.put(ResourceLocation.parse("test:unknown_bonus"), 0L);
        check(BonusTransactionPlan.resolve(player, profile, missing).status() == Status.UNKNOWN_STAT,
                "Unknown replacement Bonus target accepted");
        var invalid = new LinkedHashMap<>(targets); invalid.put(EssenceStats.STEP_HEIGHT.id(), null);
        check(BonusTransactionPlan.resolve(player, profile, invalid).status() == Status.INVALID_PROPOSAL, "Null target accepted");
        invalid.put(EssenceStats.STEP_HEIGHT.id(), -1L);
        check(BonusTransactionPlan.resolve(player, profile, invalid).status() == Status.INVALID_PROPOSAL, "Negative target accepted");
        invalid.put(EssenceStats.STEP_HEIGHT.id(), 1L);
        var partialStep = BonusTransactionPlan.resolve(player, profile, invalid);
        check(partialStep.accepted() && partialStep.plan().refunds().get(mobility) == 89,
                "Partial Step refund must retain the exact final target and refund difference");
        check(partialStep.plan().settle(Map.of(), Map.of(), Map.of()).balances().get(mobility) == 16,
                "Partial Step refund and mixed reallocation failed exact accounting");
        invalid.put(EssenceStats.STEP_HEIGHT.id(), 91L);
        check(BonusTransactionPlan.resolve(player, profile, invalid).status() == Status.CAP_EXCEEDED, "Overcap increase accepted");
        invalid.put(EssenceStats.STEP_HEIGHT.id(), 0L); invalid.put(EssenceStats.MOVEMENT_SPEED.id(), 100L);
        var excessive = BonusTransactionPlan.resolve(player, profile, invalid);
        check(excessive.accepted() && excessive.plan().settle(Map.of(), Map.of(), Map.of()).status() == Status.INSUFFICIENT_ESSENCE,
                "Final target expenditure bypassed affordability");
        check(player.save().equals(before), "Rejected complete proposal altered player baseline");

        long revision = player.nexusRevision();
        check(player.applyNexusTransaction(plan.investments(), settlement.balances(), player.getOwnedSkills(),
                        player.getLoadoutSelections(), player.getTierId()), "Validated production plan did not commit");
        check(player.nexusRevision() == revision + 1 && player.getInvested(EssenceStats.STEP_HEIGHT) == 0
                        && player.getInvested(EssenceStats.MOVEMENT_SPEED) == 73 && player.getAvailable(EssenceTypes.MOBILITY) == 17,
                "Production plan atomic commit did not conserve exact Essence and increment once");
    }

    private static PlayerEssenceData player(Map<ResourceLocation, Long> investments) {
        var result = new PlayerEssenceData();
        result.applyNexusTransaction(investments, Map.of(), Map.of(), Map.of(), AscendanceTiers.TRANSCENDENT.id());
        return result;
    }

    private static BalanceProfileDefinition profile(long priceMultiplier) {
        Map<ResourceLocation, Long> defaults = new LinkedHashMap<>();
        for (int index = 0; index < tiers.size(); index++) defaults.put(tiers.get(index).id(), COSTS[index]);
        Map<ResourceLocation, BonusTrackDefinition> tracks = new LinkedHashMap<>();
        for (var stat : EssenceStatRegistry.values()) tracks.put(stat.id(), track(stat, new long[6], new double[6],
                PurchaseStyle.CONTINUOUS, List.of(), 0, priceMultiplier));
        tracks.put(EssenceStats.MOVEMENT_SPEED.id(), track(EssenceStats.MOVEMENT_SPEED, COSTS, FRACTIONS,
                PurchaseStyle.CONTINUOUS, List.of(), 1, priceMultiplier));
        tracks.put(EssenceStats.STEP_HEIGHT.id(), track(EssenceStats.STEP_HEIGHT, new long[]{0,10,40,90,90,90},
                new double[]{0,.4,.9,1,1,1}, PurchaseStyle.CONTINUOUS, List.of(), .08, priceMultiplier));
        tracks.put(EssenceStats.FLIGHT_SPEED.id(), track(EssenceStats.FLIGHT_SPEED, new long[]{0,0,0,160,1600,6400},
                new double[]{0,0,0,.2,.6,1}, PurchaseStyle.CONTINUOUS, List.of(), .5, priceMultiplier));
        return new BalanceProfileDefinition(ResourceLocation.parse("test:resolved_" + priceMultiplier), "Resolved fixtures",
                defaults, Map.of(), Map.of(), EXPONENT, tracks);
    }

    private static BonusTrackDefinition track(StatDefinition stat, long[] caps, double[] fractions,
                                               PurchaseStyle style, List<Double> snaps, double power, long multiplier) {
        List<Checkpoint> points = new ArrayList<>();
        long previous = 0; int start = 0, end = 0;
        for (int index = 0; index < tiers.size(); index++) {
            long cap = caps[index] * multiplier;
            points.add(new Checkpoint(tiers.get(index).id(), cap, cap - previous, fractions[index], cap > 0, cap > previous));
            if (start == 0 && cap > 0) start = index;
            if (end == 0 && fractions[index] == 1) end = index;
            previous = cap;
        }
        return new BonusTrackDefinition(stat.id(), stat.category(), stat.unit(), power > 0 ? 1 : 0,
                tiers.get(start).id(), tiers.get(end).id(), points, EXPONENT, style, snaps,
                power > 0 ? Applicability.AVAILABLE : Applicability.UNAVAILABLE, List.of(), 1, List.of("Synthetic native mechanics fixture"),
                "test", Map.of("marginal_power", power));
    }

    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}

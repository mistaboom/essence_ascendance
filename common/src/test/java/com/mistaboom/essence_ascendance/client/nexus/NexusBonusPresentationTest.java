package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.attunement.AttunementSnapshot;
import com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition;
import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.network.BonusTrackSnapshot;
import com.mistaboom.essence_ascendance.network.BonusTrackSnapshotCodecTest;
import com.mistaboom.essence_ascendance.progression.BonusTrackCurve;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tests actual GUI arithmetic and shared draft state without requiring an OpenGL window. */
public final class NexusBonusPresentationTest {
    private static int checks;
    private static final ResourceLocation PROFILE = ResourceLocation.parse("essence_ascendance:generated_test");
    private static final ResourceLocation TIER = BonusTrackSnapshotCodecTest.TIERS.getLast();

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        geometry();
        tierLabelsAndUnlockMarkers();
        viewAndDragging();
        sharedDraft();
        System.out.println("NexusBonusPresentationTest: " + checks + " checks PASS");
    }

    private static void geometry() {
        for (int start = 1; start <= 5; start++) for (int end = start; end <= 5; end++) {
            var facts = BonusTrackSnapshotCodecTest.track(start, end, false);
            for (int height : new int[] {0, 1, 7, 38, 96, 300, 600}) {
                var layout = new NexusBonusTrackLayout(facts, 30, 30 + height);
                check(layout.startY() <= layout.bottom() && layout.completionY() >= layout.top()
                        && layout.completionY() <= layout.startY(), "Every useful span fits a responsive rail");
                check(layout.startY() == layout.yForPosition((start == 0 ? 0 : facts.tierPositions().get(start - 1))),
                        "Delayed track begins at the entry boundary of its first useful tier");
                check(layout.completionY() == layout.yForPosition(facts.tierPositions().get(end)),
                        "Early completion terminates on its own tier guide");
                check(layout.effectForY(layout.bottom() + 30) == 0 && layout.effectForY(layout.top() - 30) == 1,
                        "Dragging outside useful span clamps to the exact start and completion");
                for (int i = 0; i <= 100; i++) {
                    double effect = i / 100.0;
                    double position = layout.positionForEffect(effect);
                    check(Math.abs(layout.effectForPosition(position) - effect) < 1e-12,
                            "All independent spans round-trip between effect and global position");
                    check(layout.yForEffect(effect) >= layout.completionY() && layout.yForEffect(effect) <= layout.startY(),
                            "Every knob lies within its useful segment");
                }
                for (int i = start; i <= end; i++) check(layout.yForEffect(facts.checkpoints().get(i).effectFraction())
                                == layout.yForPosition(facts.tierPositions().get(i)),
                        "Per-track effect checkpoints meet the shared global tier guides");
            }
        }
        var unavailable = new NexusBonusTrackLayout(BonusTrackSnapshotCodecTest.unavailable(), 0, 100);
        check(unavailable.effectForY(-100) == 0 && unavailable.effectForY(200) == 0, "Unavailable track cannot acquire effect through geometry");
        for (int width : new int[] {1, 24, 65, 66, 70, 135, 136, 210, 360, 720, 1200}) {
            for (int trackCount : new int[] {0, 1, 2, 5, 12, 30}) {
                int count = NexusBonusTrackLayout.visibleCount(width, trackCount, 66, 4);
                int column = NexusBonusTrackLayout.trackWidth(width, count, 66, 4);
                check(column * count + Math.max(0, count - 1) * 4 <= width,
                        "Responsive paging fits whole columns into narrow and wide viewports");
                check(count >= 1 && count <= Math.max(1, trackCount) && column > 0, "Paging always has a positive hit region");
            }
        }
    }

    private static void tierLabelsAndUnlockMarkers() {
        var tiers = BonusTrackSnapshotCodecTest.TIERS;
        double[] labelPositions = {0, .1, .3, .5, .7, .9};
        int[] metalColors = {0xA86E4B, 0xB78650, 0xC99F54, 0xDFB95F, 0xF2CF78, 0xFFE7AA};
        for (int start = 1; start <= 5; start++) for (int end = start; end <= 5; end++) {
            var facts = BonusTrackSnapshotCodecTest.track(start, end, false);
            for (int height : new int[] {0, 1, 7, 38, 96, 151, 300, 600}) {
                var layout = new NexusBonusTrackLayout(facts, 30, 30 + height);
                for (int band = 1; band <= 5; band++) {
                    double center = layout.bandCenterPosition(band);
                    check(Math.abs(center - labelPositions[band]) < 1e-12,
                            "Tier labels identify the interiors of the five purchasable bands after onboarding, independent of track span");
                    check(center > (band == 0 ? 0 : facts.tierPositions().get(band - 1)) && center < facts.tierPositions().get(band),
                            "Tier label was assigned to a cap boundary instead of its own tier band");
                    int labelY = layout.bandCenterY(band);
                    int lowerBoundaryY = layout.yForPosition((band == 0 ? 0 : facts.tierPositions().get(band - 1)));
                    int upperBoundaryY = layout.yForPosition(facts.tierPositions().get(band));
                    check(labelY >= upperBoundaryY && labelY <= lowerBoundaryY,
                            "Responsive tier label leaves its band");
                    check(Math.abs((labelY - upperBoundaryY) - (lowerBoundaryY - labelY)) <= 1,
                            "Tier label sits against a boundary instead of being vertically centered");
                    if (lowerBoundaryY - upperBoundaryY >= 3)
                        check(labelY > upperBoundaryY && labelY < lowerBoundaryY,
                                "Readable tier labels must not overlap either rail boundary");
                }
                check(AscendancePalette.tierMetalRgb(facts.startTier()) == metalColors[start],
                        "Permanent rail start uses another tier's metal accent");
                check(AscendancePalette.tierMetalRgb(facts.completionTier()) == metalColors[end],
                        "Permanent completion loses the tier that completed its purchases");
                for (int player = 0; player <= 5; player++) {
                    int nextPurchase = Math.min(end, Math.max(start, player + 1));
                    ResourceLocation expectedUnlock = tiers.get(nextPurchase);
                    ResourceLocation boundaryUnlock = layout.boundaryUnlockTier(player);
                    ResourceLocation capUnlock = layout.capUnlockTier(tiers.get(player));
                    check(boundaryUnlock.equals(expectedUnlock) && capUnlock.equals(expectedUnlock),
                            "Cap marker identifies the tier that opens its next purchasable section");
                    check(AscendancePalette.tierMetalRgb(capUnlock) == metalColors[nextPurchase],
                            "Temporary cap marker color is not the next unlocking tier's exact metal color");
                    if (player < start)
                        check(capUnlock.equals(facts.startTier()), "Locked delayed rail advertises an earlier unavailable tier");
                    if (player >= end)
                        check(capUnlock.equals(facts.completionTier()), "Completed rail implies another purchase at a later tier");
                    if (player >= start && player < end)
                        check(!capUnlock.equals(tiers.get(player)), "Temporary cap kept the current tier's color instead of its unlock tier");
                }
                check(layout.capUnlockTier(ResourceLocation.parse("test:unregistered_tier")).equals(facts.startTier()),
                        "Missing player tier falls back to the rail's real starting tier");
            }
        }

        var flight = new NexusBonusTrackLayout(BonusTrackSnapshotCodecTest.track(3, 5, false), 20, 220);
        check(flight.bandCenterY(3) == 120 && flight.startY() == 140,
                "Resonant Flight label belongs inside its band while the start remains on the Awakened boundary");
        check(flight.capUnlockTier(AscendanceTiers.DORMANT.id()).equals(AscendanceTiers.RESONANT.id())
                        && flight.capUnlockTier(AscendanceTiers.AWAKENED.id()).equals(AscendanceTiers.RESONANT.id()),
                "Delayed Flight start/cap must identify Resonant, even when viewed from Dormant");
        check(AscendancePalette.tierMetalRgb(flight.capUnlockTier(AscendanceTiers.AWAKENED.id())) == 0xDFB95F,
                "Flight's Resonant unlock marker has the wrong tier metal");
        check(flight.capUnlockTier(AscendanceTiers.RESONANT.id()).equals(AscendanceTiers.ASCENDANT.id()),
                "Resonant Flight cap must show the next Ascendant unlock");

        var step = new NexusBonusTrackLayout(BonusTrackSnapshotCodecTest.track(1, 2, false), 20, 220);
        check(step.bandCenterY(2) == 160 && step.completionY() == 140,
                "Awakened Step label belongs below its permanent completion boundary");
        check(step.capUnlockTier(AscendanceTiers.DORMANT.id()).equals(AscendanceTiers.AWAKENED.id()),
                "Dormant Step cap must identify its final Awakened section");
        for (var tier : List.of(AscendanceTiers.AWAKENED, AscendanceTiers.RESONANT, AscendanceTiers.ASCENDANT, AscendanceTiers.TRANSCENDENT))
            check(step.capUnlockTier(tier.id()).equals(AscendanceTiers.AWAKENED.id())
                            && AscendancePalette.tierMetalRgb(step.capUnlockTier(tier.id())) == 0xC99F54,
                    "Completed Step endpoint must retain Awakened's metal through later player tiers");

        var base = BonusTrackSnapshotCodecTest.track(1, 5, false);
        var gapFacts = new BonusTrackSnapshot(base.unit(), base.startTier(), base.completionTier(), List.of(
                new BonusTrackDefinition.Checkpoint(tiers.get(0), 0, 0, 0, false, false),
                new BonusTrackDefinition.Checkpoint(tiers.get(1), 100, 100, .2, true, true),
                new BonusTrackDefinition.Checkpoint(tiers.get(2), 100, 0, .2, true, false),
                new BonusTrackDefinition.Checkpoint(tiers.get(3), 100, 0, .2, true, false),
                new BonusTrackDefinition.Checkpoint(tiers.get(4), 16100, 16000, .7, true, true),
                new BonusTrackDefinition.Checkpoint(tiers.get(5), 116100, 100000, 1, true, true)),
                base.tierPositions(), base.investmentExponent(), base.purchaseStyle(), List.of(0.0, .2, .7, 1.0), base.applicability());
        var gap = new NexusBonusTrackLayout(gapFacts, 20, 220);
        for (var tier : List.of(AscendanceTiers.DORMANT, AscendanceTiers.AWAKENED, AscendanceTiers.RESONANT))
            check(gap.capUnlockTier(tier.id()).equals(AscendanceTiers.ASCENDANT.id()),
                    "Unlock color must skip checkpoint plateaus that sell no additional effect");
        check(gap.bandCenterY(2) == step.bandCenterY(2),
                "A track's purchase plateau cannot move the shared Awakened tier label");
    }

    private static void viewAndDragging() {
        var stepBase = BonusTrackSnapshotCodecTest.track(1, 2, false);
        var stepFacts = new BonusTrackSnapshot(StatUnit.BLOCKS, stepBase.startTier(), stepBase.completionTier(), stepBase.checkpoints(),
                stepBase.tierPositions(), stepBase.investmentExponent(), stepBase.purchaseStyle(), stepBase.snapPoints(), stepBase.applicability());
        var movementFacts = BonusTrackSnapshotCodecTest.track(1, 5, false);
        var stepState = state(EssenceStats.STEP_HEIGHT, stepFacts, 100, TIER);
        var movementState = state(EssenceStats.MOVEMENT_SPEED, movementFacts, 100, TIER);
        var snapshot = snapshot(10, 1000, Map.of(EssenceStats.STEP_HEIGHT.id(), stepState, EssenceStats.MOVEMENT_SPEED.id(), movementState), TIER);
        var categories = NexusCategoryViewFactory.build(snapshot);
        check(categories.size() == 1 && categories.getFirst().tracks().size() == 2, "View factory includes synchronized category tracks only");
        var step = categories.getFirst().tracks().stream().filter(track -> track.stat().id().equals(EssenceStats.STEP_HEIGHT.id())).findFirst().orElseThrow();
        var movement = categories.getFirst().tracks().stream().filter(track -> track.stat().id().equals(EssenceStats.MOVEMENT_SPEED.id())).findFirst().orElseThrow();
        var tooltipTracks = List.of(step, movement);
        check(NexusProgressionTrack.tooltipTrack(tooltipTracks, 0, null) == step,
                "Dragging retains the tooltip outside the hover area");
        check(NexusProgressionTrack.tooltipTrack(tooltipTracks, 0, movement) == step,
                "Crossing another track while dragging keeps the grabbed track's tooltip");
        check(NexusProgressionTrack.tooltipTrack(tooltipTracks, -1, movement) == movement
                        && NexusProgressionTrack.tooltipTrack(tooltipTracks, -1, null) == null,
                "Releasing returns to ordinary hover tooltips");
        check(step.milestones().size() == 2, "Step Height presents generated complete-state checkpoints");
        check(movement.milestones().size() == 5, "Movement presents its generated state count");
        check(step.dragTarget(1, 599, TIER) == 599 && step.dragTarget(1, 600, TIER) == 600,
                "Step Height dragging uses every affordable Essence rather than snapping to milestones");
        check(step.dragTarget(.17, 600, TIER) > 0 && step.dragTarget(.17, 600, TIER) < 100,
                "Step Height accepts partial investments between native traversal boundaries");
        check(step.progression(100, TIER) == .5 && step.progression(99, TIER) > 0
                        && step.progression(99, TIER) < .5,
                "Step Height preview grants fractional benefit before the tier target");
        int bottomY = step.handleY(0, TIER, 20, 320);
        int partialY = step.handleY(50, TIER, 20, 320);
        int firstBenefitY = step.handleY(100, TIER, 20, 320);
        check(partialY < bottomY && partialY > firstBenefitY && step.progression(50, TIER) > 0,
                "Handle and earned effect advance together between tier markers");
        for (double mouseY = firstBenefitY; mouseY <= bottomY; mouseY++) {
            long target = step.dragTarget(step.layout(20, 320).effectForY(mouseY), 600, TIER);
            check(Math.abs(step.handleY(target, TIER, 20, 320) - mouseY) <= 2,
                    "Rendered handle follows the drag coordinate to integer-Essence precision");
        }
        check(step.handleY(step.dragTarget(1, 50, TIER), TIER, 20, 320) == partialY,
                "Affordability clamps at partial funding rather than a completed tier");
        for (int bottom : new int[] {80, 320}) {
            for (long amount = 0; amount <= 600; amount++) {
                int handle = step.handleY(amount, TIER, 20, bottom);
                for (int offset = -3; offset <= 3; offset++) {
                    double pointer = handle + offset;
                    var drag = step.beginDrag(pointer, amount, TIER, 20, bottom, 7);
                    check(drag.grabbedHandle() && step.dragTarget(drag, pointer, 600, TIER, 20, bottom) == amount,
                            "Clicking anywhere on a rounded handle must retain the exact investment, including zero");
                    long raised = step.dragTarget(drag, pointer - 12, 600, TIER, 20, bottom);
                    long lowered = step.dragTarget(drag, pointer + 12, 600, TIER, 20, bottom);
                    check(raised >= amount && raised <= 600 && lowered <= amount && lowered >= 0,
                            "Relative handle motion must respect direction and investment bounds");
                    check(step.dragTarget(drag, pointer, 600, TIER, 20, bottom) == amount,
                            "Returning to the grab position must restore the exact starting amount");
                }
            }
        }
        var railClick = step.beginDrag(20, 0, TIER, 20, 320, 7);
        check(!railClick.grabbedHandle() && step.dragTarget(railClick, 20, 600, TIER, 20, 320) == 600,
                "Clicking the rail outside the handle must still select that position");
        var affordableDrag = step.beginDrag(partialY, 50, TIER, 20, 320, 7);
        check(step.dragTarget(affordableDrag, -1000, 123, TIER, 20, 320) == 123,
                "Handle dragging must retain the shared draft's affordability cap");
        for (long amount = 1; amount <= 600; amount++) {
            check(step.progression(amount, TIER) >= step.progression(amount - 1, TIER), "Complete-state preview is monotonic");
            double funding = step.fundingProgression(amount, TIER);
            check(step.dragTarget(funding, 600, TIER) == amount, "Funding coordinate preserves exact partial investment");
            check(step.handleY(amount, TIER, 20, 320) <= step.handleY(amount - 1, TIER, 20, 320),
                    "Every incremental investment moves or preserves the handle monotonically");
        }
        for (var unit : StatUnit.values()) {
            check(NexusProgressionTrack.effectLabel(unit, 1.25).equals(unit == StatUnit.PERCENT ? "+1.25%" : "+1.25"),
                    "Every slider footer uses only a signed number and optional percent suffix");
            check(NexusProgressionTrack.effectLabel(unit, 2).equals(unit == StatUnit.PERCENT ? "+2%" : "+2"),
                    "Whole effects have no redundant decimals or unit prose");
        }
        long previous = -1;
        for (int i = 0; i <= 1000; i++) {
            long target = movement.dragTarget(i / 1000.0, Long.MAX_VALUE, TIER);
            check(target >= previous, "Continuous dragging remains monotonic");
            previous = target;
            double effect = BonusTrackCurve.progressionForInvestment(movementFacts.checkpoints(), movementFacts.investmentExponent(), target, TIER);
            check(movement.dragTarget(effect, Long.MAX_VALUE, TIER) == target, "Funding coordinate round-trips every staged target");
        }
        long previousCap = 0;
        for (var checkpoint : movementFacts.checkpoints()) {
            if (checkpoint.segmentCost() == 0) continue;
            // Equal effect samples can legitimately share one integer Essence target in a cheap segment.
            // Verify the actual contract instead: adjacent Essence amounts remain independently selectable
            // within every segment, including the expensive late-game portions of the curve.
            for (long offset = 1; offset <= Math.min(64, checkpoint.segmentCost()); offset++) {
                for (long target : new long[] {previousCap + offset, checkpoint.cumulativeCap() - offset + 1}) {
                    double effect = BonusTrackCurve.progressionForInvestment(movementFacts.checkpoints(), movementFacts.investmentExponent(), target, TIER);
                    check(movement.progression(target, TIER) >= movement.progression(target - 1, TIER), "Funding never reduces complete-state effect");
                    check(movement.dragTarget(effect, Long.MAX_VALUE, TIER) == target,
                            "Every segment retains per-Essence continuous selection through the inverse curve");
                }
            }
            previousCap = checkpoint.cumulativeCap();
        }
        var delayedFacts = BonusTrackSnapshotCodecTest.track(3, 5, false);
        var delayed = new NexusProgressionTrack(EssenceStats.FLIGHT_SPEED,
                state(EssenceStats.FLIGHT_SPEED, delayedFacts, 0, AscendanceTiers.AWAKENED.id()), List.of());
        check(delayed.dragTarget(1, Long.MAX_VALUE, AscendanceTiers.AWAKENED.id()) == 0, "Pre-start track remains locked even with unlimited funds");
        var disabled = new NexusProgressionTrack(EssenceStats.FLIGHT_SPEED,
                state(EssenceStats.FLIGHT_SPEED, BonusTrackSnapshotCodecTest.unavailable(), 0, TIER), List.of());
        check(disabled.dragTarget(1, Long.MAX_VALUE, TIER) == 0, "Unavailable track cannot stage a purchase");
    }

    private static void sharedDraft() {
        var facts = BonusTrackSnapshotCodecTest.track(1, 5, false);
        var first = EssenceStats.MOVEMENT_SPEED;
        var second = EssenceStats.SWIM_SPEED;
        var essence = first.essenceType().id();
        var stats = Map.of(first.id(), state(first, facts, 400, TIER), second.id(), state(second, facts, 200, TIER));
        var baseline = snapshot(10, 1000, stats, TIER);
        hudDraftPreferences(baseline);
        var ids = Map.of(first.id(), essence, second.id(), essence);
        var draft = new NexusDraft();
        check(draft.synchronize(baseline) == NexusDraft.SyncOutcome.CAPTURED, "Draft captures authoritative baseline");
        draft.stageBonus(first.id(), 100);
        draft.stageBonus(second.id(), 600);
        var skill = ResourceLocation.parse("test:skill");
        draft.stagePurchase(skill, essence, 250, 1);
        check(draft.projectedAvailable(essence, baseline, ids) == 650, "Cross-mode refund, increase and skill spending share one exact reservoir");
        check(draft.finalBonusTargets(baseline).get(first.id()) == 100 && draft.finalBonusTargets(baseline).get(second.id()) == 600,
                "Atomic transaction uses complete final targets rather than incremental slider events");
        check(draft.synchronize(snapshot(11, 1100, stats, TIER)) == NexusDraft.SyncOutcome.REBASED_BALANCES && draft.baseRevision() == 11,
                "Income-only revision updates preserve the cross-mode draft");
        var changedFacts = new BonusTrackSnapshot(facts.unit(), facts.startTier(), facts.completionTier(), facts.checkpoints(),
                facts.tierPositions(), .5, facts.purchaseStyle(), facts.snapPoints(), facts.applicability());
        var repriced = Map.of(first.id(), state(first, changedFacts, 400, TIER), second.id(), stats.get(second.id()));
        check(draft.synchronize(snapshot(12, 1100, repriced, TIER)) == NexusDraft.SyncOutcome.INVALIDATED,
                "Changed resolved curve invalidates a draft even if current caps/profile ID stayed equal");
        draft.clearAndCapture(baseline);
        check(!draft.invalidated() && !draft.hasChanges(baseline), "Discard clears every cross-mode target and invalidation");
        var demoted = snapshot(13, 1000, Map.of(first.id(), state(first, facts, 800, AscendanceTiers.DORMANT.id())), AscendanceTiers.DORMANT.id());
        draft.clearAndCapture(demoted);
        check(draft.finalBonusTargets(demoted).get(first.id()) == 800, "Opening a demoted Bonus preserves over-cap stored investment");
        draft.stageBonus(first.id(), 700);
        check(draft.projectedAvailable(essence, demoted, Map.of(first.id(), essence)) == 1100
                && draft.finalBonusTargets(demoted).get(first.id()) == 700, "Partial over-cap refunds remain exact target operations");
    }

    private static void hudDraftPreferences(ClientEssenceState.Snapshot snapshot) {
        var owned = ResourceLocation.parse("test:owned");
        var staged = ResourceLocation.parse("test:staged");
        var group = ResourceLocation.parse("test:group");
        var stat = EssenceStats.MOVEMENT_SPEED;
        var essence = stat.essenceType().id();
        for (boolean bonusFirst : new boolean[]{true, false}) {
            var baseline = withPurchase(snapshot, owned, essence, List.of(100L), true);
            var hidden = withPurchase(snapshot, owned, essence, List.of(100L), false);
            var draft = new NexusDraft();
            draft.synchronize(baseline);
            if (bonusFirst) draft.stageBonus(stat.id(), 500);
            draft.stagePurchase(staged, essence, 250, 1);
            draft.stageLoadout(group, staged);
            if (!bonusFirst) draft.stageBonus(stat.id(), 500);
            for (var preference : List.of(hidden, baseline, hidden, baseline)) {
                check(draft.synchronize(preference) == NexusDraft.SyncOutcome.UNCHANGED && !draft.invalidated(),
                        "HUD off/on must preserve a mixed draft staged in either screen order");
                check(draft.finalBonusTargets(preference).get(stat.id()) == 500
                                && draft.projectedRanks(preference).get(staged) == 1
                                && draft.finalLoadouts(preference).get(group).equals(staged)
                                && draft.projectedAvailable(essence, preference, Map.of(stat.id(), essence)) == 650,
                        "Visibility sync preserves staged bonuses, purchases, loadouts and shared budget");
            }
            var ranked = withPurchase(snapshot, owned, essence, List.of(100L, 200L), false);
            check(draft.synchronize(ranked) == NexusDraft.SyncOutcome.INVALIDATED,
                    "Actual purchased ranks still invalidate a stale draft");
            draft.clearAndCapture(baseline);
            draft.stageBonus(stat.id(), 500);
            check(draft.synchronize(withPurchase(snapshot, owned, essence, List.of(101L), true)) == NexusDraft.SyncOutcome.INVALIDATED,
                    "Changed paid receipt still invalidates at the same rank");
        }
    }

    private static ClientEssenceState.Snapshot withPurchase(ClientEssenceState.Snapshot s, ResourceLocation skill,
            ResourceLocation essence, List<Long> paidCosts, boolean hudEnabled) {
        return new ClientEssenceState.Snapshot(s.ready(), s.playerRevision(), s.tierId(), s.balanceProfileId(),
                s.availableEssence(), s.stats(), s.skillMilestones(), s.completedMilestones(),
                Map.of(skill, new ClientEssenceState.SkillPurchaseSnapshot(skill, essence, paidCosts, hudEnabled)),
                s.loadoutSelections(), s.attunement(), s.progress());
    }

    private static ClientEssenceState.StatSnapshot state(StatDefinition stat, BonusTrackSnapshot facts, long stored, ResourceLocation tier) {
        long cap = BonusTrackCurve.maximumInvestment(facts.checkpoints(), tier);
        double fraction = BonusTrackCurve.progressionForInvestment(facts.checkpoints(), facts.investmentExponent(), stored, tier);
        return new ClientEssenceState.StatSnapshot(stat.id(), stored, Math.min(stored, cap), cap, fraction,
                BonusTrackCurve.maximumProgression(facts.checkpoints(), tier), 1, fraction, facts);
    }

    private static ClientEssenceState.Snapshot snapshot(long revision, long available,
            Map<ResourceLocation, ClientEssenceState.StatSnapshot> stats, ResourceLocation tier) {
        return new ClientEssenceState.Snapshot(true, revision, tier, PROFILE,
                Map.of(EssenceStats.MOVEMENT_SPEED.essenceType().id(), available), stats, Map.of(), Set.of(), Map.of(), Map.of(),
                AttunementSnapshot.empty(), ClientEssenceState.snapshot().progress());
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}

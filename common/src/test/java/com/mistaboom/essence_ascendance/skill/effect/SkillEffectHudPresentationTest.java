package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared cards and the real presentation cache, independent of a Minecraft client or world. */
public final class SkillEffectHudPresentationTest {
    private static int checks;

    public static void main(String[] args) {
        cardContract();
        retainedPresentation();
        independentCards();
        postureCards();
        mobilityCards();
        var failures = new ArrayList<String>();
        SkillEffectHudDiagnostics.validate(failures);
        check(failures.isEmpty(), "Existing generic HUD codec diagnostics: " + failures);
        System.out.println("SkillEffectHudPresentationTest: " + checks + " checks passed");
    }

    private static void cardContract() {
        var badge = SkillEffectHudEntry.Text.literal("1.25×");
        var lines = List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.guard.amplifier", "1.25"));
        var timed = SkillEffectHudCards.timed(SkillIds.GUARD_AMPLIFIER, true, 0x70BCD4,
                badge, lines, "hud.essence_ascendance.guard.remaining", 160);
        check(timed.id().equals(SkillIds.GUARD_AMPLIFIER) && timed.sourceSkill().equals(SkillIds.GUARD_AMPLIFIER),
                "Shared timed cards retain catalog identity and source");
        check(timed.title().equals(SkillEffectHudEntry.Text.translated(SkillRegistry.require(SkillIds.GUARD_AMPLIFIER).nameTranslationKey())),
                "Shared card title uses catalog localization");
        check(timed.badge().equals(badge) && timed.lines().equals(lines), "Compact badge and detail remain separate fields");
        check(timed.active() && timed.accent() == 0xFF5B8FD9, "RGB accents become opaque on the shared entry boundary");
        check(timed.meter().kind() == SkillEffectHudEntry.MeterKind.TIMER && timed.meter().expiresAt() == 160,
                "Timed card carries the server expiry unchanged");
        check(timed.meter().label().equals(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.guard.remaining")),
                "Timed card retains its localized footer");
        var progress = SkillEffectHudCards.progress(SkillIds.STATIC_CHARGE, true, 0x2270BCD4,
                SkillEffectHudEntry.Text.literal("25.0/100.0"), List.of(), .25);
        check(progress.accent() == 0xFFD65368, "An accidental partial alpha cannot make a shared skill card translucent");
        check(progress.meter().equals(SkillEffectHudEntry.Meter.progress(.25)), "Progress cards use the existing normalized meter contract");
        var expected = SkillEffectHudEntry.skill(SkillIds.FRENZY, true, 0xFFE87929,
                SkillEffectHudEntry.Text.translated("hud.essence_ascendance.stacks", "2", "5"),
                List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.damage", "1.08")),
                SkillEffectHudEntry.Meter.timer("hud.essence_ascendance.chain", 200));
        var migrated = SkillEffectHudCards.timed(SkillIds.FRENZY, true, 0xFFE87929,
                SkillEffectHudCards.count(2, 5), expected.lines(), "hud.essence_ascendance.chain", 200);
        check(migrated.equals(expected), "Shared factory preserves the existing offense card fields exactly");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            check(SkillEffectHudCards.decimal(1.25).equals("1.3"), "Shared badge decimals are stable across client locales");
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static void retainedPresentation() {
        var display = new SkillEffectHudPresentation();
        check(SkillEffectHudPresentation.DEFAULT_GRACE_TICKS == 60, "Confirmed event cards retain sixty-tick grace");
        var active = card(SkillIds.STORED_FORCE, true, "2.00/8.00", 300);
        var inactive = card(SkillIds.STORED_FORCE, false, "0.00/8.00", 0);
        display.replace(List.of(inactive), 100);
        check(display.visibleEntries(100).isEmpty() && display.visibleEntries(10_000).isEmpty(),
                "Effective but unused rewards do not flash an initially inactive card");
        display.replace(List.of(active), 110);
        check(display.visibleEntries(110).equals(List.of(active)), "Active packet displays the complete card");
        check(display.visibleEntries(999).equals(List.of(active)), "An active card does not require periodic packets");
        display.replace(List.of(inactive), 1_000);
        check(display.visibleEntries(1_000).equals(List.of(active)), "Consumption retains the last nonzero badge, details and timer immediately");
        check(display.visibleEntries(1_059).equals(List.of(active)), "Last active card remains through the final grace tick");
        display.replace(List.of(inactive), 1_010);
        check(display.visibleEntries(1_060).isEmpty(), "Repeated inactive snapshots do not extend the exact grace boundary");
        var reactivated = card(SkillIds.STORED_FORCE, true, "4.00/8.00", 1_500);
        display.replace(List.of(reactivated), 1_070);
        check(display.visibleEntries(1_070).equals(List.of(reactivated)), "Rearming refreshes the cached value and expiry");
        display.replace(List.of(inactive), 1_080);
        check(display.visibleEntries(1_139).equals(List.of(reactivated)), "A later activation has its own grace period");
        check(display.visibleEntries(1_140).isEmpty(), "Second grace period also ends exactly on time");
        display.replace(List.of(active), 1_150);
        display.replace(List.of(), 1_151);
        check(display.visibleEntries(1_151).isEmpty(), "Omission from an ineffective skill removes its card immediately");
        display.replace(List.of(inactive), 1_152);
        check(display.visibleEntries(1_152).isEmpty(), "Re-adding an inactive skill cannot resurrect its earlier card");
        display.replace(List.of(active), 1_153);
        display.clear();
        check(display.visibleEntries(1_153).isEmpty(), "Disconnect, respawn and dimension cleanup clear presentation state");
        display.replace(List.of(inactive), 1_154);
        check(display.visibleEntries(1_154).isEmpty(), "A lifecycle reset also forgets the last active appearance");
    }

    private static void independentCards() {
        var display = new SkillEffectHudPresentation();
        var primary = card(SkillIds.RIPOSTE, true, "Armed", 200);
        var auxiliaryId = ResourceLocation.fromNamespaceAndPath("essence_ascendance", "test/riposte_auxiliary");
        var auxiliary = new SkillEffectHudEntry(auxiliaryId, primary.sourceSkill(), true, primary.accent(),
                primary.title(), SkillEffectHudEntry.Text.literal("Second card"), primary.lines(), primary.meter()).asEvent();
        var auxiliaryInactive = new SkillEffectHudEntry(auxiliaryId, primary.sourceSkill(), false, primary.accent(),
                primary.title(), SkillEffectHudEntry.Text.literal("Default"), List.of(), SkillEffectHudEntry.Meter.none()).asEvent();
        display.replace(List.of(primary, auxiliary), 10);
        check(display.visibleEntries(10).equals(List.of(primary, auxiliary)), "Several cards from one source preserve server order and independent IDs");
        display.replace(List.of(primary, auxiliaryInactive), 20);
        check(display.visibleEntries(79).equals(List.of(primary, auxiliary)), "One card can remain active while its sibling lingers");
        check(display.visibleEntries(80).equals(List.of(primary)), "Only the inactive sibling expires");
        display.replace(List.of(auxiliary), 81);
        check(display.visibleEntries(81).equals(List.of(auxiliary)), "Removing one card never leaves a stale card for the same source");
    }

    private static SkillEffectHudEntry card(ResourceLocation id, boolean active, String badge, long expiry) {
        return SkillEffectHudCards.timed(id, active, 0xFFC65C, SkillEffectHudEntry.Text.literal(badge),
                List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.next_confirmed_melee")),
                "hud.essence_ascendance.counterattack", expiry).asEvent();
    }

    private static void postureCards() {
        var settings = com.mistaboom.essence_ascendance.config.PostureBalanceSettings.defaults();
        for (var id : List.of(SkillIds.EVASIVE_CURRENT, SkillIds.BULWARK_STANCE, SkillIds.ADAPTIVE_GUARD)) {
            var active = PostureHandler.card(id, .5, 3, 200, settings, true, false);
            var empty = PostureHandler.card(id, 0, 0, 0, settings, false, false);
            var peaceful = PostureHandler.card(id, .5, 3, 200, settings, false, false);
            check(peaceful.active() == id.equals(SkillIds.ADAPTIVE_GUARD),
                    "Only adaptation can display outside recent mob/player combat");
            check(active.id().equals(id) && active.sourceSkill().equals(id), "Posture identity uses shared card contract");
            check(active.active() && !empty.active() && active.lines().size() == 1, "Active/inactive posture cards have one detail line");
            check((active.accent() >>> 24) == 255, "Posture accent remains opaque");
            if (id.equals(SkillIds.ADAPTIVE_GUARD)) {
                check(active.badge().equals(SkillEffectHudCards.count(3, settings.adaptive().maximumStacks())), "Adaptation badge displays bounded stacks");
                check(active.meter().kind() == SkillEffectHudEntry.MeterKind.TIMER && active.meter().expiresAt() == 200, "Adaptation footer shows real memory expiry");
                check(active.lines().equals(List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.posture.adaptive", "15.0"))), "Adaptive detail matches next same-type hit mitigation");
            } else {
                check(active.badge().equals(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.percent", "50.0")), "Posture badge displays meter fraction");
                check(active.meter().equals(SkillEffectHudEntry.Meter.progress(.5)), "Posture progress uses actual server meter");
                check(active.lines().equals(List.of(SkillEffectHudEntry.Text.translated(id.equals(SkillIds.EVASIVE_CURRENT)
                        ? "hud.essence_ascendance.posture.evasive" : "hud.essence_ascendance.posture.bulwark", "10.0"))), "Posture detail is scaled to meter");
            }
            var display = new SkillEffectHudPresentation();
            display.replace(List.of(empty), 0);
            check(display.visibleEntries(0).isEmpty(), "Initial posture is hidden");
            display.replace(List.of(active), 10); display.replace(List.of(empty), 20);
            check(display.visibleEntries(20).isEmpty(), "Empty posture immediately stops advertising the previous meter");
            check(!empty.active(), "Closing presentation cannot reactivate gameplay snapshot");
            display.replace(List.of(active), 90); display.replace(List.of(), 91);
            check(display.visibleEntries(91).isEmpty(), "Posture opt-out/choice switch removes card immediately");
            if (!id.equals(SkillIds.ADAPTIVE_GUARD)) {
                display.replace(List.of(peaceful), 100);
                check(display.visibleEntries(100).isEmpty(), "Charged posture stays hidden during ordinary travel or waiting");
                display.replace(List.of(active), 110);
                display.replace(List.of(peaceful), 210);
                check(display.visibleEntries(210).isEmpty(), "Combat expiry removes live posture immediately");
            } else {
                var firstHit = PostureHandler.card(id, 0, 1, 300, settings, false, false);
                check(firstHit.active(), "The first adapting hit reveals the card even for environmental damage");
            }
        }
        var dodged = PostureHandler.card(SkillIds.EVASIVE_CURRENT, 0, 0, 0, settings, true, true);
        check(dodged.active() && dodged.lines().equals(List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.posture.dodged"))),
                "Confirmed dodge visibly opens the card even when consuming the entire meter");
        check(dodged.meter().equals(SkillEffectHudEntry.Meter.progress(0))
                        && dodged.badge().equals(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.percent", "0.0"))
                        && dodged.accent() == 0xFF5B8FD9,
                "Dodged replaces only the existing dodge line; the regular badge, color and actual meter remain");
        var depleted = PostureHandler.card(SkillIds.EVASIVE_CURRENT, 0, 0, 0, settings, true, false);
        var dodgeDisplay = new SkillEffectHudPresentation();
        var charged = PostureHandler.card(SkillIds.EVASIVE_CURRENT, 1, 0, 0, settings, true, false);
        dodgeDisplay.replace(List.of(charged), 90);
        dodgeDisplay.replace(List.of(depleted), 91);
        check(depleted.active() && dodgeDisplay.visibleEntries(91).equals(List.of(depleted))
                        && depleted.lines().equals(List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.posture.evasive", "0.0"))),
                "A real hit immediately replaces the full combat card with zero chance instead of retaining stale charge");
        dodgeDisplay.replace(List.of(dodged), 100);
        dodgeDisplay.replace(List.of(depleted), 140);
        check(dodgeDisplay.visibleEntries(140).equals(List.of(depleted)),
                "Expired dodge feedback returns to the regular zero-percent line during combat");
        var peaceful = PostureHandler.card(SkillIds.EVASIVE_CURRENT, 0, 0, 0, settings, false, false);
        dodgeDisplay.replace(List.of(peaceful), 200);
        check(dodgeDisplay.visibleEntries(200).isEmpty(), "Zero-meter combat card disappears when combat ends");
        check(com.mistaboom.essence_ascendance.status.StatusEffectHandlers.mirror() instanceof SkillEffectHudHandler,
                "Mirror cooldown uses shared card handler");
        check(!(com.mistaboom.essence_ascendance.status.StatusEffectHandlers.pureState() instanceof SkillEffectHudHandler),
                "Pure State adds no permanent passive overlay");
    }

    private static void mobilityCards() {
        for (boolean owned : new boolean[]{false, true}) for (boolean gliding : new boolean[]{false, true}) {
            var wings = MobilityFlightEffects.wingsCard(owned, gliding);
            check(wings.active() == (owned && gliding), "Wings requires both owned glide state and real native gliding");
            check(wings.meter().kind() == SkillEffectHudEntry.MeterKind.NONE && !wings.retainAfterActive(),
                    "Wings has neither a fake progress bar nor stale closing state");
        }
        var display = new SkillEffectHudPresentation();
        display.replace(List.of(MobilityFlightEffects.wingsCard(true, true)), 100);
        check(display.visibleEntries(100).size() == 1, "Actual glide is visible");
        display.replace(List.of(MobilityFlightEffects.wingsCard(true, false)), 101);
        check(display.visibleEntries(101).isEmpty(), "Stopped glide cannot keep saying GLIDING during grace");
        for (var id : List.of(SkillIds.DOUBLE_JUMP, SkillIds.VECTOR_JUMP)) {
            for (boolean allowed : new boolean[]{false,true}) for (boolean airborne : new boolean[]{false,true})
                for (boolean ready : new boolean[]{false,true}) {
                    var card = MobilityJumpEffects.airJumpCard(id, allowed, airborne, ready);
                    check(card.active() == (allowed && airborne && ready), "Air-jump hint is available only when usable");
                    check(card.meter().kind() == SkillEffectHudEntry.MeterKind.NONE, "Binary air jump has no resource bar");
                    if (!ready) check(card.lines().isEmpty(), "Spent air jump cannot offer another jump");
                }
            display.replace(List.of(MobilityJumpEffects.airJumpCard(id,true,true,true)), 200);
            display.replace(List.of(MobilityJumpEffects.airJumpCard(id,true,true,false)), 201);
            check(display.visibleEntries(201).isEmpty(), "Consumed jump is not held as Ready");
        }
        for (double stamina : new double[]{0,.5,1}) {
            var flying = MobilityFlightEffects.flightCard(SkillIds.UNTETHERED_FLIGHT, true, true, stamina);
            check(flying.active() && flying.meter().kind() == SkillEffectHudEntry.MeterKind.NONE && flying.lines().isEmpty(),
                    "Unlimited flight never advertises a limiting stamina resource");
            check(!MobilityFlightEffects.flightCard(SkillIds.UNTETHERED_FLIGHT,false,true,stamina).active(),
                    "Flight permission alone does not claim actual flying");
            check(MobilityFlightEffects.flightCard(SkillIds.FATIGUE_FLIGHT,true,false,stamina).meter().fraction() == stamina,
                    "Fatigue Flight retains the actual finite stamina meter");
        }
        check(!MobilityFlightEffects.boostCard(false,1).active(), "Ordinary Elytra flight cannot advertise a Wings-only boost");
        var charging = MobilityFlightEffects.boostCard(true,.5);
        check(charging.meter().fraction() == .5 && charging.lines().isEmpty(), "Recharge is real but cannot offer a premature boost");
        check(!MobilityFlightEffects.boostCard(true,1).lines().isEmpty(), "A charged Wings boost advertises its usable input");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

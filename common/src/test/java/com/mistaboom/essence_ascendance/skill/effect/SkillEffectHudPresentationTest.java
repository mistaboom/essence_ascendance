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
        check(timed.active() && timed.accent() == 0xFF70BCD4, "RGB accents become opaque on the shared entry boundary");
        check(timed.meter().kind() == SkillEffectHudEntry.MeterKind.TIMER && timed.meter().expiresAt() == 160,
                "Timed card carries the server expiry unchanged");
        check(timed.meter().label().equals(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.guard.remaining")),
                "Timed card retains its localized footer");
        var progress = SkillEffectHudCards.progress(SkillIds.STATIC_CHARGE, true, 0x2270BCD4,
                SkillEffectHudEntry.Text.literal("25.0/100.0"), List.of(), .25);
        check(progress.accent() == 0xFF70BCD4, "An accidental partial alpha cannot make a shared skill card translucent");
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
            check(SkillEffectHudCards.decimal(1.25).equals("1.25"), "Shared badge decimals are stable across client locales");
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static void retainedPresentation() {
        var display = new SkillEffectHudPresentation();
        check(SkillEffectHudPresentation.DEFAULT_GRACE_TICKS == 60, "Every shared card uses the existing sixty-tick grace");
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
                primary.title(), SkillEffectHudEntry.Text.literal("Second card"), primary.lines(), primary.meter());
        var auxiliaryInactive = new SkillEffectHudEntry(auxiliaryId, primary.sourceSkill(), false, primary.accent(),
                primary.title(), SkillEffectHudEntry.Text.literal("Default"), List.of(), SkillEffectHudEntry.Meter.none());
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
                "hud.essence_ascendance.counterattack", expiry);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

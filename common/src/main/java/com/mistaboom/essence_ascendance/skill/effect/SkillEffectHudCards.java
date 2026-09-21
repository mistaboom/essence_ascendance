package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/** Shared card templates: title, status with timer/progress, then two effect detail rows. */
public final class SkillEffectHudCards {
    private SkillEffectHudCards() { }

    /** Catalog-derived definitions: one home, title and geometry per registered skill. */
    public record Definition(ResourceLocation skill, ResourceLocation home, String titleKey) {
        public int height() { return SkillEffectHudLayout.HEIGHT; }
        public int accent() { return com.mistaboom.essence_ascendance.visual.AscendancePalette.categoryArgb(home); }
    }
    public static List<Definition> definitions() {
        return com.mistaboom.essence_ascendance.skill.SkillRegistry.values().stream()
                .map(skill -> new Definition(skill.id(), skill.essenceId(), skill.nameTranslationKey())).toList();
    }


    public static SkillEffectHudEntry timed(ResourceLocation skill, boolean active, int accent,
                                            Text badge, List<Text> details, String timerKey, long expiresAt) {
        return SkillEffectHudEntry.skill(skill, active, accent, badge, details,
                SkillEffectHudEntry.Meter.timer(timerKey, expiresAt));
    }

    public static SkillEffectHudEntry progress(ResourceLocation skill, boolean active, int accent,
                                               Text badge, List<Text> details, double fraction) {
        return SkillEffectHudEntry.skill(skill, active, accent, badge, details,
                SkillEffectHudEntry.Meter.progress(fraction));
    }

    public static Text count(int value, int maximum) {
        return Text.translated("hud.essence_ascendance.stacks", Integer.toString(value), Integer.toString(maximum));
    }

    /** Glance-readable display only. Stored and calculated values retain full precision. */
    public static String compact(double value) {
        if (!Double.isFinite(value)) return "0";
        if (Math.abs(value) >= 1_000_000) return compact(value / 1_000_000) + "M";
        if (Math.abs(value) >= 1_000) return compact(value / 1_000) + "k";
        if (value > 0 && value < .1) return "<0.1";
        if (value < 0 && value > -.1) return ">-0.1";
        return java.math.BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    public static String decimal(double value) { return compact(value); }
}

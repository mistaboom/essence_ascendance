package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/** Shared offense-style card templates: compact badge, effect details, then timer or progress footer. */
public final class SkillEffectHudCards {
    private SkillEffectHudCards() { }

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

    public static String decimal(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}

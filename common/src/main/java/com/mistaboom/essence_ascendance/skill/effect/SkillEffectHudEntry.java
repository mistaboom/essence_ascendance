package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Presentation data, never gameplay authority. A handler may emit several independently keyed cards. */
public record SkillEffectHudEntry(ResourceLocation id, ResourceLocation sourceSkill,
                                  boolean active, int accent, Text title, Text badge,
                                  List<Text> lines, Meter meter, boolean retainAfterActive) {
    public static final int MAX_LINES = 3;

    public SkillEffectHudEntry(ResourceLocation id, ResourceLocation sourceSkill, boolean active, int accent,
                               Text title, Text badge, List<Text> lines, Meter meter) {
        this(id, sourceSkill, active, accent, title, badge, lines, meter, false);
    }

    /** Completed outcomes may linger; live readiness, resources and movement states may not. */
    public SkillEffectHudEntry asEvent() {
        return new SkillEffectHudEntry(id, sourceSkill, active, accent, title, badge, lines, meter, true);
    }

    public SkillEffectHudEntry {
        // Accents are opaque RGB. A missing alpha byte must never make a new
        // skill's border/badge invisible or change the shared card appearance.
        accent = com.mistaboom.essence_ascendance.visual.AscendancePalette.categoryArgb(
                SkillRegistry.require(sourceSkill).essenceId());
        Objects.requireNonNull(id);
        Objects.requireNonNull(sourceSkill);
        Objects.requireNonNull(title);
        Objects.requireNonNull(badge);
        Objects.requireNonNull(meter);
        lines = List.copyOf(lines);
        if (lines.size() > MAX_LINES) throw new IllegalArgumentException("Too many HUD detail lines");
    }

    /** Default title follows the existing catalog/localization, including future renames. */
    public static SkillEffectHudEntry skill(ResourceLocation skill, boolean active, int accent,
                                            Text badge, List<Text> lines, Meter meter) {
        return new SkillEffectHudEntry(skill, skill, active, accent,
                Text.translated(SkillRegistry.require(skill).nameTranslationKey()), badge, lines, meter);
    }

    /** A bounded localization key with literal arguments, or a literal such as a target's name. */
    public record Text(String translationKey, String literal, List<String> arguments) {
        public static final int MAX_LENGTH = 192;
        public static final int MAX_ARGUMENTS = 4;

        public Text {
            Objects.requireNonNull(translationKey);
            Objects.requireNonNull(literal);
            arguments = List.copyOf(arguments);
            if (translationKey.length() > MAX_LENGTH || literal.length() > MAX_LENGTH
                    || arguments.size() > MAX_ARGUMENTS
                    || arguments.stream().anyMatch(value -> value.length() > MAX_LENGTH)) {
                throw new IllegalArgumentException("HUD text exceeds its bounds");
            }
            if (translationKey.isEmpty() && !arguments.isEmpty()) {
                throw new IllegalArgumentException("Literal HUD text cannot have translation arguments");
            }
        }

        public static Text literal(String value) {
            return new Text("", value.substring(0, Math.min(value.length(), MAX_LENGTH)), List.of());
        }

        public static Text translated(String key, String... arguments) {
            return new Text(key, "", List.of(arguments));
        }
    }

    public enum MeterKind { NONE, TIMER, PROGRESS }

    /** TIMER counts down an absolute server tick; PROGRESS fills a normalized [0,1] bar. */
    public record Meter(MeterKind kind, Text label, long expiresAt, double fraction) {
        public Meter {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(label);
            if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0 || expiresAt < 0L) {
                throw new IllegalArgumentException("Invalid HUD meter");
            }
        }

        public static Meter none() { return new Meter(MeterKind.NONE, Text.literal(""), 0L, 0.0); }
        public static Meter timer(String key, long expiry) {
            return new Meter(MeterKind.TIMER, Text.translated(key), Math.max(0L, expiry), 0.0);
        }
        public static Meter progress(double fraction) {
            return new Meter(MeterKind.PROGRESS, Text.literal(""), 0L, SkillEffectMath.clamp(fraction, 0.0, 1.0));
        }
    }
}

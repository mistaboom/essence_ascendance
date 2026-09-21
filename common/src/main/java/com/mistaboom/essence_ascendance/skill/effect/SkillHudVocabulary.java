package com.mistaboom.essence_ascendance.skill.effect;

import java.util.List;
import java.util.function.Function;

/** Localized full-term/compact-term pairs shared by every HUD provider. Nexus prose never uses this vocabulary. */
public final class SkillHudVocabulary {
    public static final List<String> TERMS = List.of("attack_damage", "attack_speed", "movement_speed", "maximum_health",
            "health", "cooldown", "duration", "damage", "experience", "overdurability", "resistance", "regeneration");
    private SkillHudVocabulary() { }
    public static String compact(String text, Function<String, String> translate) {
        for (String term : TERMS) {
            String full = translate.apply("hud.essence_ascendance.term." + term);
            if (!full.isBlank()) text = text.replaceAll("(?iu)" + java.util.regex.Pattern.quote(full),
                    java.util.regex.Matcher.quoteReplacement(translate.apply("hud.essence_ascendance.abbr." + term)));
        }
        return text;
    }
}

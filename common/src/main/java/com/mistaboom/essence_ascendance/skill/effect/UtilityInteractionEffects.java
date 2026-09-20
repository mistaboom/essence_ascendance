package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.utility.FriendlyDamageService;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Binary Utility interaction safeguards whose native hooks live outside the tick dispatcher. */
public final class UtilityInteractionEffects {
    private UtilityInteractionEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new FriendlyFireWard(), new EnchantingInsight());
    }

    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.utility." + key, args);
    }

    private static final class FriendlyFireWard implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.FRIENDLY_FIRE_WARD; }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var state = FriendlyDamageService.snapshot(context);
            boolean blockedNow = state != null && state.lastBlockedAt() == context.now();
            String targetName = state == null ? "" : state.targetName();
            if (targetName.length() > Text.MAX_LENGTH) targetName = targetName.substring(0, Text.MAX_LENGTH);
            List<Text> details = targetName.isBlank()
                    ? List.of()
                    : List.of(text("friendly_fire_target", targetName));
            return SkillEffectHudEntry.skill(id(), blockedNow, AscendancePalette.UTILITY,
                    text("friendly_fire_blocked"), details, SkillEffectHudEntry.Meter.none());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Binary responsible-player damage gate; no invented damage multiplier or numeric tuning.",
                    "Direct melee, player-owned projectiles, and player-attributed ability damage are rejected against allied players and owned/allied tamed creatures before native damage commits.");
        }
    }

    private static final class EnchantingInsight implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.ENCHANTING_INSIGHT; }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Binary enchanting information capability; no invented efficiency scalar.",
                    "The server preserves current offers while the inserted item is unchanged. The effective client previews the full deterministic enchantment list for the hovered offer before commitment.");
        }
    }
}

package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.gathering.GatheringSalvageService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Grindstone salvage branch; the menu transaction itself is handled by GatheringSalvageService. */
public final class GatheringSalvageEffects {
    private GatheringSalvageEffects() { }

    public static List<SkillEffectHandler> handlers() { return List.of(new SalvagersCraft()); }

    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.gathering." + key, args);
    }

    private static final class SalvagersCraft implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.SALVAGERS_CRAFT; }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.SalvagersCraft tuning = context.settings().gathering().salvagersCraft();
            GatheringSalvageService.HudState state = GatheringSalvageService.hudState(context);
            return SkillEffectHudEntry.skill(id(), state.grindstoneOpen(), AscendancePalette.GATHERING,
                    text(state.salvageReady() ? "salvage_ready" : "salvage_button"),
                    List.of(text("salvage_last", Integer.toString(state.lastMaterials()), Integer.toString(state.lastExperience())),
                            text("salvage_material", SkillEffectHudCards.compact(tuning.materialRecoveryFraction() * 100.0D))),
                    SkillEffectHudEntry.Meter.none());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.SalvagersCraft tuning = context.settings().gathering().salvagersCraft();
            GatheringSalvageService.HudState state = GatheringSalvageService.hudState(context);
            return List.of("Grindstone open=" + state.grindstoneOpen() + "; salvage preview ready=" + state.salvageReady(),
                    "Generated material recovery fraction=" + tuning.materialRecoveryFraction()
                            + "; bonus stored-enchantment XP fraction=" + tuning.bonusExperienceFraction(),
                    "Last salvage materials=" + state.lastMaterials() + "; bonus XP=" + state.lastExperience()
                            + "; the dedicated recycle button commits destructive salvage; ordinary result pickup stays vanilla.");
        }
    }
}

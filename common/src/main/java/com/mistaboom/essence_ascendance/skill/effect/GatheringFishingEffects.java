package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.gathering.GatheringFishingService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Fishing branch; gameplay remains inside the native FishingHook lifecycle. */
public final class GatheringFishingEffects {
    private GatheringFishingEffects() { }

    public static List<SkillEffectHandler> handlers() { return List.of(new FishingInstinct()); }

    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.gathering." + key, args);
    }

    private static String seconds(int ticks) {
        return SkillEffectHudCards.compact(ticks / 20.0D);
    }

    private static final class FishingInstinct implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.FISHING_INSTINCT; }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.FishingInstinct tuning = context.settings().gathering().fishingInstinct();
            GatheringFishingService.HudState state = GatheringFishingService.hudState(context.player());
            Text status = switch (state.phase()) {
                case BITE -> text("fishing_bite_now", seconds(state.remainingTicks()));
                case APPROACHING -> text("fishing_approaching", seconds(state.remainingTicks()));
                case WAITING -> text("fishing_waiting", seconds(state.remainingTicks()));
                case CAST -> text("fishing_cast");
                case NONE -> text("fishing_cast");
            };
            return SkillEffectHudEntry.skill(id(), state.active(), AscendancePalette.GATHERING,
                    text("fishing_bite_speed", SkillEffectHudCards.compact(tuning.biteSpeedMultiplier())),
                    List.of(status,
                            text("fishing_reel_window", SkillEffectHudCards.compact(tuning.reelWindowMultiplier())),
                            text("fishing_luck", SkillEffectHudCards.compact(tuning.virtualLuckLevels()))),
                    SkillEffectHudEntry.Meter.none());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.FishingInstinct tuning = context.settings().gathering().fishingInstinct();
            GatheringFishingService.HudState state = GatheringFishingService.hudState(context.player());
            return List.of("Native bite countdown multiplier=" + tuning.biteSpeedMultiplier()
                            + "; reel-window multiplier=" + tuning.reelWindowMultiplier(),
                    "Expected virtual Luck=" + tuning.virtualLuckLevels()
                            + "; fractional levels are stochastically realized only during native hook retrieval.",
                    "HUD phase=" + state.phase() + "; remaining native/modified hook ticks=" + state.remainingTicks());
        }
    }
}

package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.gathering.GatheringFishingService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Fishing branch; native fishing owns the primary lifecycle while skill rewards compose around it. */
public final class GatheringFishingEffects {
    private static final String NEXT_PULSE_KEY = "hud.essence_ascendance.gathering.next_pulse";

    private GatheringFishingEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new FishingInstinct(), new FishersCall(), new PocketNets());
    }

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
                case CAST, NONE -> text("fishing_cast");
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

    private static final class FishersCall implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.FISHERS_CALL; }
        @Override public void tick(SkillEffectRuntime.Context context) { GatheringFishingService.reconcileShoal(context); }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            GatheringFishingService.ShoalHudState state = GatheringFishingService.shoalHudState(context);
            return SkillEffectHudCards.timed(id(), state.active(), AscendancePalette.GATHERING,
                    SkillEffectHudCards.count(state.stacks(), state.maximumStacks()),
                    List.of(text("shoal_bite_speed", SkillEffectHudCards.compact(state.biteSpeedMultiplier())),
                            text("shoal_extra_catch", SkillEffectHudCards.compact(state.extraCatchChance() * 100.0D))),
                    "hud.essence_ascendance.chain", state.expiresAt());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.FishersCall tuning = context.settings().gathering().fishersCall();
            GatheringFishingService.ShoalHudState state = GatheringFishingService.shoalHudState(context);
            return List.of("Shoal stacks=" + state.stacks() + "/" + state.maximumStacks()
                            + "; shared expiry tick=" + state.expiresAt(),
                    "Current bite multiplier=" + state.biteSpeedMultiplier() + "; current extra-catch chance="
                            + state.extraCatchChance(),
                    "Generated maximum bite multiplier=" + tuning.maximumBiteSpeedMultiplier()
                            + "; maximum extra-catch chance=" + tuning.maximumExtraCatchChance()
                            + "; native retrieval misses reset the chain.");
        }
    }

    private static final class PocketNets implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.POCKET_NETS; }
        @Override public void tick(SkillEffectRuntime.Context context) { GatheringFishingService.tickPocketNets(context); }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.PocketNets tuning = context.settings().gathering().pocketNets();
            GatheringFishingService.PocketNetHudState state = GatheringFishingService.pocketNetHudState(context);
            return SkillEffectHudCards.timed(id(), state.swimming(), AscendancePalette.GATHERING,
                    text("nets_chance", SkillEffectHudCards.compact(tuning.dropChance() * 100.0D)),
                    List.of(text("nets_last", Integer.toString(state.lastDrops())),
                            text("nets_period", seconds(tuning.pulseTicks()))),
                    NEXT_PULSE_KEY, state.nextPulseTick());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.PocketNets tuning = context.settings().gathering().pocketNets();
            GatheringFishingService.PocketNetHudState state = GatheringFishingService.pocketNetHudState(context);
            return List.of("In water=" + state.swimming() + "; pulse ticks=" + tuning.pulseTicks()
                            + "; single proc chance=" + tuning.dropChance(),
                    "Last passive fishing reward item count=" + state.lastDrops()
                            + "; next pulse tick=" + state.nextPulseTick(),
                    "One skill roll occurs per pulse; success resolves one non-empty fishing reward. Overflow follows ordinary player-drop behavior.");
        }
    }
}

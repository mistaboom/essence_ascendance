package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.gathering.GatheringRuralService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Growth/breeding branch backed by one shared nearby-world scheduler. */
public final class GatheringRuralEffects {
    private static final String NEXT_PULSE_KEY = "hud.essence_ascendance.gathering.next_pulse";

    private GatheringRuralEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new VerdantStride(), new Herdkeeper(), new AnimalGift());
    }

    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.gathering." + key, args);
    }

    private static final class VerdantStride implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.VERDANT_STRIDE; }
        @Override public void tick(SkillEffectRuntime.Context context) { GatheringRuralService.tickVerdantStride(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) { GatheringRuralService.clear(context, id()); }

        @Override
        public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.VerdantStride tuning = context.settings().gathering().verdantStride();
            GatheringRuralService.PulseHudState state = GatheringRuralService.pulseHudState(context, id());
            int crops = GatheringRuralService.nearbyGrowingCropCount(context.player(), tuning.radiusBlocks());
            return SkillEffectHudCards.timed(id(), crops > 0, AscendancePalette.GATHERING,
                    text("verdant_crops", Integer.toString(crops)),
                    List.of(text("verdant_extra_ticks", Integer.toString(state.successfulEvents())),
                            text("verdant_chance", SkillEffectHudCards.compact(tuning.growthChance() * 100.0D))),
                    NEXT_PULSE_KEY, state.nextPulseTick());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.VerdantStride tuning = context.settings().gathering().verdantStride();
            GatheringRuralService.PulseHudState state = GatheringRuralService.pulseHudState(context, id());
            return List.of("Growth radius=" + tuning.radiusBlocks() + "; pulse ticks=" + tuning.growthPulseTicks()
                            + "; native random-tick chance per eligible crop=" + tuning.growthChance(),
                    "Last pulse eligible crops=" + state.eligibleTargets() + "; extra native random ticks="
                            + state.successfulEvents() + "; next pulse tick=" + state.nextPulseTick(),
                    "Farmland and turtle-egg trampling are binary protections routed through their native block hooks.");
        }
    }

    private static final class Herdkeeper implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.HERDKEEPER; }
        @Override public void tick(SkillEffectRuntime.Context context) { GatheringRuralService.tickHerdkeeper(context); }

        @Override
        public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.Herdkeeper tuning = context.settings().gathering().herdkeeper();
            GatheringRuralService.HerdkeeperHudState state = GatheringRuralService.herdkeeperHudState(
                    context.player(), tuning.radiusBlocks());
            return SkillEffectHudEntry.skill(id(), state.nearbyAnimals() > 0, AscendancePalette.GATHERING,
                    text("herd_animals", Integer.toString(state.nearbyAnimals())),
                    List.of(text("herd_cooldowns", Integer.toString(state.recoveringCooldowns())),
                            text("herd_recovery", SkillEffectHudCards.compact(tuning.breedingRecoveryMultiplier())),
                            text("herd_range", SkillEffectHudCards.compact(tuning.radiusBlocks()))),
                    SkillEffectHudEntry.Meter.none());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.Herdkeeper tuning = context.settings().gathering().herdkeeper();
            GatheringRuralService.HerdkeeperHudState state = GatheringRuralService.herdkeeperHudState(
                    context.player(), tuning.radiusBlocks());
            return List.of("Gathering radius=" + tuning.radiusBlocks() + "; breeding recovery multiplier="
                            + tuning.breedingRecoveryMultiplier(),
                    "Nearby non-tamed animals=" + state.nearbyAnimals() + "; positive breeding cooldowns="
                            + state.recoveringCooldowns()
                            + "; navigation remains native while positive breeding cooldown receives generated extra recovery.");
        }
    }

    private static final class AnimalGift implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.ANIMAL_GIFT; }
        @Override public void tick(SkillEffectRuntime.Context context) { GatheringRuralService.tickAnimalGift(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) { GatheringRuralService.clear(context, id()); }

        @Override
        public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.AnimalGift tuning = context.settings().gathering().animalGift();
            GatheringRuralService.PulseHudState state = GatheringRuralService.pulseHudState(context, id());
            int ready = GatheringRuralService.giftReadyCount(context.player(), tuning.radiusBlocks());
            return SkillEffectHudCards.timed(id(), ready > 0, AscendancePalette.GATHERING,
                    text("gift_ready", Integer.toString(ready)),
                    List.of(text("gift_last", Integer.toString(state.successfulEvents())),
                            text("gift_chance", SkillEffectHudCards.compact(tuning.giftChance() * 100.0D))),
                    NEXT_PULSE_KEY, state.nextPulseTick());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.AnimalGift tuning = context.settings().gathering().animalGift();
            GatheringRuralService.PulseHudState state = GatheringRuralService.pulseHudState(context, id());
            return List.of("Gift radius=" + tuning.radiusBlocks() + "; pulse ticks=" + tuning.giftPulseTicks()
                            + "; per-eligible-animal chance=" + tuning.giftChance(),
                    "Last pulse gift-ready animals=" + state.eligibleTargets() + "; gifts produced="
                            + state.successfulEvents() + "; next pulse tick=" + state.nextPulseTick(),
                    "Production is dispatched through the shared renewable-product registry and native providers.");
        }
    }
}

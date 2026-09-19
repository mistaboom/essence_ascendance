package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.gathering.GatheringSurveyService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Shared survey-skill handlers; world scanning and rendering live in their reusable services. */
public final class GatheringSurveyEffects {
    private GatheringSurveyEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new OreSight(), new TreasureSense());
    }

    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.gathering." + key, args);
    }

    private static SkillEffectHudEntry card(SkillEffectRuntime.Context context, ResourceLocation skill,
                                            String targetKey) {
        int targets = GatheringSurveyService.targetCount(context, skill);
        double range = skill.equals(SkillIds.ORE_SIGHT)
                ? context.settings().gathering().oreSight().rangeBlocks()
                : context.settings().gathering().treasureSense().rangeBlocks();
        List<Text> details = skill.equals(SkillIds.ORE_SIGHT) && targets > 0
                ? List.of(text("survey_range", SkillEffectHudCards.compact(range)), text("prime_deposit"))
                : List.of(text("survey_range", SkillEffectHudCards.compact(range)));
        return SkillEffectHudEntry.skill(skill, targets > 0, AscendancePalette.GATHERING,
                text(targetKey, Integer.toString(targets)), details, SkillEffectHudEntry.Meter.none());
    }

    private static final class OreSight implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.ORE_SIGHT; }
        @Override public void tick(SkillEffectRuntime.Context context) { GatheringSurveyService.tickOreSight(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) { context.discardState(id()); }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            return card(context, id(), "ore_targets");
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Survey range=" + context.settings().gathering().oreSight().rangeBlocks()
                            + "; visible ore blocks=" + GatheringSurveyService.targetCount(context, id()),
                    "Requires a held pickaxe; loaded chunks only; ore identity/value comes from the shared procedural ore catalog; the highest-value connected deposit is emphasized.");
        }
    }

    private static final class TreasureSense implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.TREASURE_SENSE; }
        @Override public void tick(SkillEffectRuntime.Context context) { GatheringSurveyService.tickTreasureSense(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) { context.discardState(id()); }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            return card(context, id(), "treasure_targets");
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Survey range=" + context.settings().gathering().treasureSense().rangeBlocks()
                            + "; unopened loot containers=" + GatheringSurveyService.targetCount(context, id()),
                    "Loaded chunks only; a RandomizableContainerBlockEntity is eligible only while it still owns an unopened loot table.");
        }
    }
}

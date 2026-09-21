package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.utility.ExplosionContainmentService;
import com.mistaboom.essence_ascendance.utility.UtilitySanctuaryService;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Utility aura/domain handlers; world hooks delegate to shared services. */
public final class UtilityWorldEffects {
    private UtilityWorldEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new Sanctuary(), new IndustriousPresence(), new ContainmentField());
    }

    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.utility." + key, args);
    }

    private static final class Sanctuary implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.SANCTUARY; }
        @Override public void tick(SkillEffectRuntime.Context context) { UtilitySanctuaryService.tick(context); }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var tuning = context.settings().utility().sanctuary();
            var state = UtilitySanctuaryService.snapshot(context);
            List<Text> details = new ArrayList<>();
            details.add(text("sanctuary_radius", SkillEffectHudCards.compact(tuning.radiusBlocks())));
            details.add(text("sanctuary_spawn_suppressed"));
            if (state.pacifiedHostiles() > 0) {
                details.add(text("sanctuary_pacified", Integer.toString(state.pacifiedHostiles())));
            }
            SkillEffectHudEntry.Meter meter = state.nearbyHostiles() > 0
                    && state.nextDisengageAt() > context.now()
                    ? SkillEffectHudEntry.Meter.timer("hud.essence_ascendance.utility.sanctuary_disengage",
                    state.nextDisengageAt())
                    : SkillEffectHudEntry.Meter.none();
            return SkillEffectHudEntry.skill(id(), state.nearbyHostiles() > 0 || state.pacifiedHostiles() > 0, AscendancePalette.UTILITY,
                    text("sanctuary_hostiles", Integer.toString(state.nearbyHostiles())), details, meter);
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var tuning = context.settings().utility().sanctuary();
            var state = UtilitySanctuaryService.snapshot(context);
            return List.of("Radius=" + tuning.radiusBlocks() + "; target disengage delay="
                            + tuning.disengageDelayTicks() + " ticks; targeting hostiles=" + state.nearbyHostiles()
                            + "; pacified hostiles=" + state.pacifiedHostiles(),
                    "Natural monster spawn candidates inside an effective Sanctuary are rejected. Existing monster targets disengage after their generated targeting/player-aggression quiet delay; incoming hits do not renew that delay, and completed disengagement blocks vanilla retargeting until the player resumes accepted hostile damage or leaves the field.");
        }
    }

    private static final class IndustriousPresence implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.INDUSTRIOUS_PRESENCE; }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var tuning = context.settings().utility().industriousPresence();
            return SkillEffectHudEntry.skill(id(), SkillHudEvents.active(context, id()), AscendancePalette.UTILITY,
                    text("industrious_speed", SkillEffectHudCards.compact(tuning.processingSpeedMultiplier())),
                    List.of(text("industrious_radius", SkillEffectHudCards.compact(tuning.radiusBlocks())),
                            text("industrious_processors")),
                    SkillEffectHudEntry.Meter.none()).asEvent();
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var tuning = context.settings().utility().industriousPresence();
            return List.of("Radius=" + tuning.radiusBlocks() + "; processor tick multiplier="
                            + tuning.processingSpeedMultiplier(),
                    "Eligible ticking work blocks use fractional native extra ticks. Eligibility is the essence_ascendance:industrious_processors block tag so datapacks and mod integrations can extend it without another gameplay hook.");
        }
    }

    private static final class ContainmentField implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.CONTAINMENT_FIELD; }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var tuning = context.settings().utility().containmentField();
            var state = ExplosionContainmentService.snapshot(context);
            List<Text> details = new ArrayList<>();
            details.add(text("containment_radius", SkillEffectHudCards.compact(tuning.radiusBlocks())));
            boolean recent = state.containedThisTick() > 0 && context.now() >= state.lastContainedAt()
                    && context.now() - state.lastContainedAt() < SkillHudEvents.EVENT_TICKS;
            if (recent) {
                details.add(text("containment_caught", Integer.toString(state.containedThisTick())));
            } else {
                details.add(text("containment_protection"));
            }
            return SkillEffectHudEntry.skill(id(), recent, AscendancePalette.UTILITY,
                    text("containment_active"), details, SkillEffectHudEntry.Meter.none()).asEvent();
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var tuning = context.settings().utility().containmentField();
            return List.of("Radius=" + tuning.radiusBlocks() + " blocks from player to explosion center",
                    "Contained explosions still damage and knock back ordinary creatures; block destruction/fire, dropped-item damage, and effects on every field owner containing the explosion are suppressed.");
        }
    }
}

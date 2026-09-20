package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.vitality.VitalityDeathDefianceService;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** HUD/debug bindings for the two exclusive lethal-interception styles. Gameplay authority lives in the shared service. */
public final class VitalityDeathDefianceEffects {
    private VitalityDeathDefianceEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new SecondWind(), new SpiritWalk());
    }

    private abstract static class Base implements SkillEffectHudHandler {
        @Override public void deactivate(SkillEffectRuntime.Context context) {
            VitalityDeathDefianceService.deactivate(context, id());
        }
        @Override public boolean hudWhileIneffective(SkillEffectRuntime.Context context) {
            return VitalityDeathDefianceService.snapshot(context, id()).active();
        }
        SkillEffectHudEntry card(SkillEffectRuntime.Context context, boolean spirit) {
            var state = VitalityDeathDefianceService.snapshot(context, id());
            boolean cooling = state.cooldownUntil() > context.now();
            if (state.active()) {
                return SkillEffectHudCards.timed(id(), true, AscendancePalette.VITALITY,
                        text(spirit ? "spirit_active" : "second_wind_active"),
                        spirit
                                ? List.of(text("spirit_harmless"), text("spirit_reform", compact(state.reformHealthFraction() * 100)))
                                : List.of(text("second_wind_healing", compact(state.recoveryHealthPerSecond())),
                                        text("second_wind_strength", compact(state.damageBonus() * 100))),
                        "hud.essence_ascendance.vitality.active_time", state.activeUntil());
            }
            return SkillEffectHudCards.timed(id(), cooling, AscendancePalette.VITALITY,
                    text("death_defiance_cooldown"),
                    List.of(text(spirit ? "spirit_ready_hint" : "second_wind_ready_hint")),
                    "hud.essence_ascendance.vitality.cooldown_time", state.cooldownUntil());
        }
    }

    private static final class SecondWind extends Base {
        @Override public ResourceLocation id() { return SkillIds.SECOND_WIND; }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) { return card(context, false); }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var s = context.settings().vitality().deathDefiance().secondWind();
            var state = VitalityDeathDefianceService.snapshot(context, id());
            return List.of("Active=" + state.active() + "; cooldown remaining=" + Math.max(0, state.cooldownUntil() - context.now()) + " ticks",
                    "Generated recovery=" + s.recoveryHealthFraction() + " max HP over " + s.recoveryTicks()
                            + " ticks; strength damage bonus=" + s.strengthDamageBonus(),
                    "Last trigger cleared delayed HP=" + state.clearedDebt() + "; harmful effects=" + state.clearedEffects(),
                    "Lethal save leaves exactly one HP; bypass-invulnerability damage is never intercepted.");
        }
    }

    private static final class SpiritWalk extends Base {
        @Override public ResourceLocation id() { return SkillIds.SPIRIT_WALK; }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) { return card(context, true); }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var s = context.settings().vitality().deathDefiance().spiritWalk();
            var state = VitalityDeathDefianceService.snapshot(context, id());
            return List.of("Active=" + state.active() + "; cooldown remaining=" + Math.max(0, state.cooldownUntil() - context.now()) + " ticks",
                    "Generated intangible duration=" + s.durationTicks() + " ticks; reform health fraction=" + s.reformHealthFraction(),
                    "Active Spirit Walk cancels ordinary incoming damage and all player-attributed outgoing damage; movement remains native.",
                    "Fall distance is cleared while intangible; bypass-invulnerability damage is never intercepted.");
        }
    }

    private static SkillEffectHudEntry.Text text(String path, String... args) {
        return SkillEffectHudEntry.Text.translated("hud.essence_ascendance.vitality." + path, args);
    }
    private static String compact(double value) { return SkillEffectHudCards.compact(value); }
}

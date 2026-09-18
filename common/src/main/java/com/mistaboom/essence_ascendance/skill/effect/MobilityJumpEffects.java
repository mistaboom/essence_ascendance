package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.movement.MovementAbilityRules;
import com.mistaboom.essence_ascendance.movement.MovementAbilityService;
import com.mistaboom.essence_ascendance.movement.MovementAbilityState;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import static com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudCards.compact;

/** One input/resource lifecycle and the established HUD card contract for all jump styles. */
public final class MobilityJumpEffects {
    private MobilityJumpEffects() { }
    public static List<SkillEffectHandler> handlers() {
        return List.of(new Impact(), new Jump(SkillIds.CHARGED_JUMP), new Jump(SkillIds.DOUBLE_JUMP), new Jump(SkillIds.VECTOR_JUMP));
    }
    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.mobility." + key, args);
    }
    private static final class Impact implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.IMPACT_CONTROL; }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Generated impact reduction=" + context.settings().mobility().impactControl().damageReduction(),
                    "Native fall plus tagged movement impacts reuse player/equipment Fall Resistance once. Native fall-damage multiplier transfers to non-fall collisions only. No combat/admin immunity, extra fall attribute or idle HUD card.");
        }
    }
    private record Jump(ResourceLocation id) implements SkillEffectHudHandler {
        @Override public void reconcile(SkillEffectRuntime.Context context) { MovementAbilityService.observe(context, id); }
        @Override public void tick(SkillEffectRuntime.Context context) { reconcile(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) { context.discardState(id); }
        @Override public void movementDiscontinuity(SkillEffectRuntime.Context context) { context.discardState(id); }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            MovementAbilityState state = context.existingState(id);
            boolean allowed = MovementAbilityRules.allowed(context.player());
            if (id.equals(SkillIds.CHARGED_JUMP)) {
                var tuning = context.settings().mobility().chargedJump();
                double charge = state == null ? 0 : state.charge(context.now(), tuning.chargeTicks());
                return SkillEffectHudCards.progress(id, allowed && state != null && state.charging(), AscendancePalette.MOBILITY,
                        text("jump_charge", compact(charge * 100)),
                        List.of(text("jump_launch", compact(Math.sqrt(1 + tuning.heightBonus() * charge))), text("jump_release")), charge);
            }
            boolean ready = state != null && state.airAvailable();
            boolean airborne = state != null && !state.grounded();
            return SkillEffectHudCards.progress(id, allowed && airborne && ready, AscendancePalette.MOBILITY,
                    text(ready ? "air_jump_ready" : "air_jump_spent"),
                    List.of(text(id.equals(SkillIds.VECTOR_JUMP) ? "vector_jump_hint" : "double_jump_hint")), ready ? 1 : 0);
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            MovementAbilityState state = context.existingState(id);
            var tuning = context.settings().mobility();
            return List.of("Grounded=" + (state != null && state.grounded()) + "; charging=" + (state != null && state.charging())
                            + "; air jump available=" + (state != null && state.airAvailable()),
                    "Charge ticks=" + tuning.chargedJump().chargeTicks() + "; charged/double launch multipliers="
                            + Math.sqrt(1 + tuning.chargedJump().heightBonus()) + "/" + Math.sqrt(1 + tuning.doubleJump().heightBonus())
                            + "; vector impulse multiplier=" + Math.sqrt(1 + tuning.vectorJump().impulseBonus())
                            + "; downward brake=" + tuning.vectorJump().brakeFraction(),
                    "Fresh ordinary Jump edges, elapsed server ticks, native collision support and one air credit per landing. Vector replaces Double. Mode/config/skill/lifecycle discontinuities reset; input expiry cancels, never launches.");
        }
    }
}

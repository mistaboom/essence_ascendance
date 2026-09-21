package com.mistaboom.essence_ascendance.status;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.*;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** The two exclusive effects use the same lifecycle state and status transaction. */
public final class StatusEffectHandlers {
    private StatusEffectHandlers() { }
    public static SkillEffectHudHandler mirror() {
        return new SkillEffectHudHandler() {
            @Override public ResourceLocation id() { return SkillIds.STATUS_MIRROR; }
            @Override public void reconcile(SkillEffectRuntime.Context context) { StatusInterceptionService.state(context, id()); }
            @Override public List<String> debugLines(SkillEffectRuntime.Context context) { return StatusInterceptionService.debugLines(context.player()); }
            @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
                var state = StatusInterceptionService.state(context, id());
                return SkillEffectHudCards.timed(id(), state.cooldownUntil > context.now(), com.mistaboom.essence_ascendance.visual.AscendancePalette.DEFENSE,
                        SkillEffectHudEntry.Text.translated("hud.essence_ascendance.status.cooldown"),
                        List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.status.mirror")),
                        "hud.essence_ascendance.remaining", state.cooldownUntil);
            }
        };
    }
    public static SkillEffectHandler pureState() {
        return new SkillEffectHandler() {
            @Override public ResourceLocation id() { return SkillIds.PURE_STATE; }
            @Override public void reconcile(SkillEffectRuntime.Context context) { StatusInterceptionService.state(context, id()); }
            @Override public List<String> debugLines(SkillEffectRuntime.Context context) { return StatusInterceptionService.debugLines(context.player()); }
        };
    }
}

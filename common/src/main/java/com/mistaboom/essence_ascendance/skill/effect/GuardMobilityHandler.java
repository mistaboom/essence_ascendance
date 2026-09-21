package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Registration and invalidation share the common movement controller; collision never enters attack rewards. */
public record GuardMobilityHandler(ResourceLocation id) implements SkillEffectHandler {
    @Override public void deactivate(SkillEffectRuntime.Context context) {
        GuardMobilityController.remove(context.player()); context.discardState(id());
    }
    @Override public List<SkillEffectHudEntry> hudEntries(SkillEffectRuntime.Context context) {
        if (!id.equals(com.mistaboom.essence_ascendance.skill.SkillIds.GUARDED_ADVANCE)) return SkillEffectHandler.super.hudEntries(context);
        boolean active = GuardMobilityController.active(context.player()) && context.player().getDeltaMovement().horizontalDistanceSqr() > .0001;
        return List.of(SkillEffectHudEntry.skill(id, active, 0,
                SkillEffectHudEntry.Text.translated("hud.essence_ascendance.event.guarded"),
                List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.event.vault",
                        SkillEffectHudCards.compact(GuardMobilityController.stepHeight(context.player(), .6F)))), SkillEffectHudEntry.Meter.none()));
    }
    @Override public List<String> debugLines(SkillEffectRuntime.Context context) { return GuardMobilityController.diagnostics(context.player()); }
}

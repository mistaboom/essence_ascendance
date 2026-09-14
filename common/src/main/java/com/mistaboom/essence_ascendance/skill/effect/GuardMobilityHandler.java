package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Registration and invalidation share the common movement controller; collision never enters attack rewards. */
public record GuardMobilityHandler(ResourceLocation id) implements SkillEffectHandler {
    @Override public void deactivate(SkillEffectRuntime.Context context) {
        GuardMobilityController.remove(context.player()); context.discardState(id());
    }
    @Override public List<String> debugLines(SkillEffectRuntime.Context context) { return GuardMobilityController.diagnostics(context.player()); }
}

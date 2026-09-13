package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.projectile.ProjectileControlService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Catalog bindings; prerequisites and ranks remain owned by the committed selection system. */
public final class DefenseProjectileEffects {
    private DefenseProjectileEffects() { }
    public static List<SkillEffectHandler> handlers() {
        return List.of(new Effect(SkillIds.PROJECTILE_DRAG_FIELD), new Effect(SkillIds.INTERCEPTOR), new Effect(SkillIds.TRAJECTORY_THEFT));
    }
    private record Effect(ResourceLocation id) implements SkillEffectHandler {
        @Override public void tick(SkillEffectRuntime.Context context) {
            if (id.equals(SkillIds.PROJECTILE_DRAG_FIELD)) ProjectileControlService.scan(context);
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Projectile control " + id.getPath() + ": server swing/spatial contracts; settings=" + context.settings().projectiles().control());
        }
    }
}

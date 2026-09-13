package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.projectile.ProjectilePath;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/** Catalog bindings only. Effective-state evaluation already owns choices, prerequisites and exclusions. */
public final class OffenseProjectileEffects {
    private static final List<PathEffect> PATHS = List.of(new PathEffect(SkillIds.HOMING_PROJECTILE, ProjectilePath.HOMING),
            new PathEffect(SkillIds.RICOCHET, ProjectilePath.RICOCHET), new PathEffect(SkillIds.PIERCING_PROJECTILE, ProjectilePath.PIERCING));
    private OffenseProjectileEffects() { }
    public static List<SkillEffectHandler> handlers() {
        return java.util.stream.Stream.<SkillEffectHandler>concat(PATHS.stream(), java.util.stream.Stream.of(
                new PayloadEffect(SkillIds.EXPLOSIVE_PAYLOAD), new PayloadEffect(SkillIds.ROOTING_PAYLOAD))).toList();
    }
    public static ProjectilePath selectedPath(Set<ResourceLocation> effective) {
        var selected = PATHS.stream().filter(path -> effective.contains(path.id())).toList();
        return selected.size() == 1 ? selected.getFirst().path() : ProjectilePath.NONE;
    }
    private record PathEffect(ResourceLocation id, ProjectilePath path) implements SkillEffectHandler {
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Projectile path " + path + ": captured once at launch; current settings=" + context.settings().projectiles());
        }
    }
    private record PayloadEffect(ResourceLocation id) implements SkillEffectHandler {
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var settings = context.settings().projectiles();
            return List.of("Projectile payload " + id + ": independent launch snapshot; distinct confirmed creature victims only; trigger budget="
                            + settings.payloadTriggerBudget() + "; secondary damage cannot retrigger a payload.",
                    "Explosion radius=" + settings.explosiveRadius() + "; confirmed-damage scale=" + settings.explosiveDamageScale()
                            + "; creature cap=" + context.settings().combustion().targetsPerBurst() + "; terrain/fire=false.",
                    "Root duration=" + settings.rootDurationTicks() + "; first-application expiry cap=" + settings.rootMaxDurationTicks()
                            + "; recovery gap=duration; downward gravity and attacks remain; riding/teleport/death/unload release.");
        }
    }
}

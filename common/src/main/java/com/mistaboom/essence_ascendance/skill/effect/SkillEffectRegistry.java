package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Only completed implementations are registered. Construction rejects catalog drift. */
public final class SkillEffectRegistry {
    private static final Map<ResourceLocation, SkillEffectHandler> HANDLERS =
            validated(java.util.stream.Stream.of(OffenseCombatStanceEffects.handlers(),
                    OffenseElementalImbuementEffects.handlers(), OffenseProjectileEffects.handlers(),
                    DefenseProjectileEffects.handlers(), com.mistaboom.essence_ascendance.guard.GuardReflectionEffects.handlers(),
                    GuardCounterattackService.handlers(),
                    VitalityRecoveryEffects.handlers(),
                    VitalitySustenanceEffects.handlers(),
                    VitalityDamageEffects.handlers(),
                    VitalityWardEffects.handlers(),
                    java.util.List.<SkillEffectHandler>of(
                            new PostureHandler(com.mistaboom.essence_ascendance.skill.SkillIds.EVASIVE_CURRENT),
                            new PostureHandler(com.mistaboom.essence_ascendance.skill.SkillIds.BULWARK_STANCE),
                            new PostureHandler(com.mistaboom.essence_ascendance.skill.SkillIds.ADAPTIVE_GUARD),
                            com.mistaboom.essence_ascendance.status.StatusEffectHandlers.mirror(),
                            com.mistaboom.essence_ascendance.status.StatusEffectHandlers.pureState(),
                            new GuardMobilityHandler(com.mistaboom.essence_ascendance.skill.SkillIds.GUARDED_ADVANCE),
                            new GuardMobilityHandler(com.mistaboom.essence_ascendance.skill.SkillIds.SHIELD_RAM)))
                    .flatMap(Collection::stream).toList());

    private SkillEffectRegistry() { }

    public static Set<ResourceLocation> implementedIds() { return HANDLERS.keySet(); }
    public static boolean isImplemented(ResourceLocation id) { return HANDLERS.containsKey(id); }
    public static Collection<SkillEffectHandler> handlers() { return HANDLERS.values(); }
    public static SkillEffectHandler get(ResourceLocation id) { return HANDLERS.get(id); }

    static Map<ResourceLocation, SkillEffectHandler> validated(Collection<SkillEffectHandler> handlers) {
        Map<ResourceLocation, SkillEffectHandler> result = new LinkedHashMap<>();
        for (SkillEffectHandler handler : handlers) {
            Objects.requireNonNull(handler, "Skill effect handler cannot be null");
            SkillRegistry.require(handler.id());
            if (result.putIfAbsent(handler.id(), handler) != null) {
                throw new IllegalArgumentException("Duplicate gameplay skill effect: " + handler.id());
            }
        }
        return Collections.unmodifiableMap(result);
    }
}

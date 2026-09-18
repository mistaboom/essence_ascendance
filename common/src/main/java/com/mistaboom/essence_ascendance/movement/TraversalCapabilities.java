package com.mistaboom.essence_ascendance.movement;

import com.mistaboom.essence_ascendance.skill.CommittedSkillAccess;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Set;

/** Declarative binary traversal contracts. Hooks consume capabilities, not individual skill IDs.
 * Restoration to native full effectiveness is not a generated speed/damage magnitude. Costs,
 * availability and capability pressure still belong to the existing generated balance pipeline. */
public final class TraversalCapabilities {
    public enum Capability {
        TERRAIN_DRAG, TERRAIN_CONTACT, FIRM_FOOTING,
        WATER_BODY, WATER_VISION, LIQUID_SURFACE,
        LAVA_BODY, LAVA_VISION, LAVA_PROTECTION
    }
    public record Profile(ResourceLocation skill, Set<Capability> capabilities) {
        public Profile { capabilities = Set.copyOf(capabilities); }
    }
    private static final List<Profile> PROFILES = List.of(
            profile(SkillIds.TERRAIN_FREEDOM, Capability.TERRAIN_DRAG, Capability.TERRAIN_CONTACT, Capability.FIRM_FOOTING),
            profile(SkillIds.AQUATIC_BODY, Capability.WATER_BODY, Capability.WATER_VISION),
            profile(SkillIds.WATER_WALKING, Capability.LIQUID_SURFACE),
            profile(SkillIds.LAVABORN, Capability.LAVA_BODY, Capability.LAVA_VISION, Capability.LAVA_PROTECTION));

    private TraversalCapabilities() { }
    private static Profile profile(ResourceLocation skill, Capability... capabilities) {
        return new Profile(skill, Set.of(capabilities));
    }
    public static List<Profile> profiles() { return PROFILES; }
    public static boolean isBinarySkill(ResourceLocation skill) {
        return PROFILES.stream().anyMatch(profile -> profile.skill().equals(skill));
    }
    public static boolean has(Entity entity, Capability capability) {
        if (entity == null) return false;
        for (Profile profile : PROFILES) if (profile.capabilities().contains(capability)
                && CommittedSkillAccess.isEffective(entity, profile.skill())) return true;
        return false;
    }
}

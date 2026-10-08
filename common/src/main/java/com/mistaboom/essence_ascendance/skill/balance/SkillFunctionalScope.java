package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Semantic target domains for axes whose name alone does not identify a useful substitute.
 * Providers publish these domains in Measurement.scope.targets; unknown is not universal.
 * These declarations contain no access tiers, currency or source/pack exceptions. */
public final class SkillFunctionalScope {
    private SkillFunctionalScope() { }
    private static final Map<ResourceLocation, Set<String>> INFORMATION = new HashMap<>();
    private static final Map<ResourceLocation, Set<String>> GENERATION = new HashMap<>();
    private static final Map<ResourceLocation, Set<String>> RESISTANCE = new HashMap<>();
    static {
        INFORMATION.put(SkillIds.ORE_SIGHT, Set.of("ore_blocks"));
        INFORMATION.put(SkillIds.TREASURE_SENSE, Set.of("unopened_loot_containers"));
        INFORMATION.put(SkillIds.THREAT_SENSE, Set.of("hostile_entities"));
        INFORMATION.put(SkillIds.HUNTERS_LEDGER, Set.of("entity_drops"));
        INFORMATION.put(SkillIds.WAYLIGHT, Set.of("hostile_spawn_locations"));
        INFORMATION.put(SkillIds.ENCHANTING_INSIGHT, Set.of("enchantment_offers"));
        INFORMATION.put(SkillIds.AQUATIC_BODY, Set.of("underwater_visibility"));
        GENERATION.put(SkillIds.NATURES_BOON, Set.of("ore_resources", "mineral_resources"));
        GENERATION.put(SkillIds.ANIMAL_GIFT, Set.of("animal_products"));
        RESISTANCE.put(SkillIds.PURE_STATE, Set.of("harmful_effects"));
        RESISTANCE.put(SkillIds.STATUS_MIRROR, Set.of("harmful_effects"));
        RESISTANCE.put(SkillIds.TERRAIN_FREEDOM, Set.of("terrain_movement_penalties"));
        RESISTANCE.put(SkillIds.LAVABORN, Set.of("fire_damage", "lava_movement"));
        RESISTANCE.put(SkillIds.AQUATIC_BODY, Set.of("drowning"));
    }
    public static synchronized void register(ResourceLocation skill, CapabilityAxis axis, Set<String> targets) {
        var registry = axis == CapabilityAxis.INFORMATION ? INFORMATION : axis == CapabilityAxis.PASSIVE_GENERATION ? GENERATION
                : axis == CapabilityAxis.STATUS_RESISTANCE ? RESISTANCE : null;
        if (registry == null || targets.isEmpty() || registry.putIfAbsent(skill, Set.copyOf(targets)) != null)
            throw new IllegalArgumentException("Unsupported/duplicate functional domain " + skill + "/" + axis);
    }
    public static boolean compatible(ResourceLocation skill, CapabilityEvidence.Measurement measurement) {
        if (measurement.axis() == CapabilityAxis.JUMP && measurement.scope().targets().equals("ground_jump"))
            return Set.of(SkillIds.CHARGED_JUMP, SkillIds.MOMENTUM_VAULT).contains(skill);
        var domains = switch (measurement.axis()) {
            case INFORMATION -> INFORMATION.getOrDefault(skill, Set.of());
            case PASSIVE_GENERATION -> GENERATION.getOrDefault(skill, Set.of());
            case STATUS_RESISTANCE -> RESISTANCE.getOrDefault(skill, Set.of());
            default -> null;
        };
        return domains == null || domains.contains(measurement.scope().targets());
    }
    /** Functional unlock witnesses follow the dominant declared function. Strength calibration
     * may use complementary axes, but e.g. a teleportation consumable does not prove flight. */
    public static Set<CapabilityAxis> availabilityAxes(SkillBalanceSemantics.Descriptor descriptor) {
        var weights = descriptor.weights(); double maximum = weights.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        var axes = EnumSet.noneOf(CapabilityAxis.class);
        weights.forEach((axis, weight) -> { if (weight == maximum) axes.add(axis); });
        for (var axis : List.copyOf(axes)) switch (axis) {
            case GLIDING -> axes.add(CapabilityAxis.FLIGHT);
            case BLOCK_ENTITY_ACCELERATION -> axes.addAll(Set.of(CapabilityAxis.MACHINE_ACCELERATION, CapabilityAxis.LOCAL_TIME_ACCELERATION));
            case FISHING_PRODUCTIVITY, FISHING_TIME_REDUCTION -> axes.add(CapabilityAxis.AUTOMATED_FISHING);
            case PASSIVE_GENERATION -> { if (GENERATION.getOrDefault(descriptor.skillId(), Set.of()).contains("ore_resources")) axes.add(CapabilityAxis.AUTOMATED_EXTRACTION); }
            case AUTOMATION_INTERACTION -> { if (GENERATION.containsKey(descriptor.skillId())) axes.add(CapabilityAxis.PASSIVE_GENERATION); }
            default -> { }
        }
        return Collections.unmodifiableSet(axes);
    }
}

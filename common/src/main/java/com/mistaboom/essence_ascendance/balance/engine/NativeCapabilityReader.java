package com.mistaboom.essence_ascendance.balance.engine;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.enchantment.effects.*;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Reads native enchantment effects for enchanted-stack dissolution without executing gameplay callbacks. */
public final class NativeCapabilityReader {
    private NativeCapabilityReader() { }
    public static List<Measurement> enchantmentMeasurements(String enchantmentId, Enchantment enchantment, int level,
                                                            boolean effectiveStack, CapabilitySink sink) {
        List<Measurement> measurements = new ArrayList<>();
        List<String> commonUnknown = effectiveStack ? List.of() : List.of("Definition maximum; attainable level/application/setup requires typed acquisition proof");
        for (EnchantmentAttributeEffect effect : enchantment.getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
            String attribute = effect.attribute().unwrapKey().map(k -> k.location().toString()).orElse("unknown");
            CapabilityAxis axis = attributeAxis(attribute);
            Double amount = levelValue(effect.amount(), level);
            if (axis == null || amount == null || amount < 0) {
                sink.candidate(enchantmentId, "native_enchantments", "Unsupported attribute/negative transformation " + attribute); continue;
            }
            String unit = effect.operation() == AttributeModifier.Operation.ADD_VALUE ? "attribute_addition" : "attribute_multiplier_addition";
            measurements.add(measurement(axis, amount, unit,
                    "attribute=" + attribute + "; operation=" + effect.operation() + "; slots=" + enchantment.definition().slots(),
                    Scope.self(), Operation.manual(), "native_enchantments", Origin.NATIVE, commonUnknown));
        }
        valueEffects(enchantment, EnchantmentEffectComponents.DAMAGE, CapabilityAxis.BURST_DAMAGE, "health_points", level, measurements, commonUnknown, sink, enchantmentId);
        valueEffects(enchantment, EnchantmentEffectComponents.DAMAGE_PROTECTION, CapabilityAxis.DAMAGE_REDUCTION, "enchantment_protection_points", level, measurements, commonUnknown, sink, enchantmentId);
        valueEffects(enchantment, EnchantmentEffectComponents.ITEM_DAMAGE, CapabilityAxis.DURABILITY_REDUCTION, "wear_transform", level, measurements, commonUnknown, sink, enchantmentId);
        valueEffects(enchantment, EnchantmentEffectComponents.REPAIR_WITH_XP, CapabilityAxis.REPAIR, "durability_per_xp", level, measurements, commonUnknown, sink, enchantmentId);
        valueEffects(enchantment, EnchantmentEffectComponents.FISHING_TIME_REDUCTION, CapabilityAxis.FISHING_TIME_REDUCTION, "native_wait_reduction", level, measurements, commonUnknown, sink, enchantmentId);
        valueEffects(enchantment, EnchantmentEffectComponents.FISHING_LUCK_BONUS, CapabilityAxis.FISHING_PRODUCTIVITY, "native_fishing_luck", level, measurements, commonUnknown, sink, enchantmentId);
        measurements.addAll(NativeEnchantmentEffects.read(enchantment, level, sink, enchantmentId));
        return List.copyOf(measurements);
    }
    private static void valueEffects(Enchantment enchantment,
            net.minecraft.core.component.DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> component,
            CapabilityAxis axis, String unit, int level, List<Measurement> output, List<String> commonUnknown,
            CapabilitySink sink, String id) {
        for (var conditional : enchantment.getEffects(component)) {
            if (impossibleRequirements(conditional.requirements())) {
                sink.candidate(id, "native_enchantments", "Impossible native match_tool condition: explicitly empty item alternatives; effect excluded");
                continue;
            }
            CapabilityAxis measuredAxis = axis;
            var effect = conditional.effect(); Double amount = null; String transform = "unknown";
            if (effect instanceof AddValue add) { amount = levelValue(add.value(), level); transform = "add"; }
            else if (effect instanceof MultiplyValue multiply) { amount = levelValue(multiply.factor(), level); transform = "multiply"; }
            else if (effect instanceof SetValue set) { amount = levelValue(set.value(), level); transform = "set"; }
            else if (effect instanceof RemoveBinomial binomial && axis == CapabilityAxis.DURABILITY_REDUCTION) {
                amount = levelValue(binomial.chance(), level); transform = "binomial_probability";
                if (amount != null && amount > 1) amount = null;
            }
            if (axis == CapabilityAxis.DURABILITY_REDUCTION && transform.equals("multiply") && amount != null) {
                amount = amount <= 1 ? 1 - amount : null; transform = "fraction_reduced";
            }
            if (axis == CapabilityAxis.DURABILITY_REDUCTION && transform.equals("add") && amount != null) {
                if (amount >= 0) continue; // Increased wear is not a preservation competitor.
                amount = -amount; transform = "wear_subtraction";
            }
            if (axis == CapabilityAxis.DURABILITY_REDUCTION && transform.equals("set") && amount != null) {
                if (amount != 0) { sink.candidate(id, "native_enchantments", "Fixed wear amount needs baseline/event semantics; not a reduction ratio"); continue; }
                measuredAxis = CapabilityAxis.INDESTRUCTIBILITY; amount = 1.0; transform = "zero_native_wear";
            }
            if (amount == null || amount < 0) { sink.candidate(id, "native_enchantments", "Unsupported value effect " + effect.getClass().getName()); continue; }
            List<String> unknown = new ArrayList<>(commonUnknown);
            String applies = "effect=" + BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getKey(component) + "; transform=" + transform;
            if (conditional.requirements().isPresent()) {
                // Conditions are not evaluated against invented targets. Conditional peaks are separate from unconditional power.
                var condition = net.minecraft.world.level.storage.loot.predicates.LootItemCondition.DIRECT_CODEC
                        .encodeStart(RegistryOps.create(JsonOps.INSTANCE,
                                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)),
                                conditional.requirements().get()).result();
                applies += "; conditional=" + condition.map(Object::toString).orElse("unencoded:" + conditional.requirements().get().getClass().getName());
                unknown.add("Conditional peak; predicate match frequency/uptime unmeasured");
                if (condition.isEmpty()) { sink.candidate(id, "native_enchantments", "Conditional effect predicate cannot encode; numeric frontier excluded"); amount = null; }
            }
            output.add(measurement(measuredAxis, amount, measuredAxis == CapabilityAxis.INDESTRUCTIBILITY ? "presence" : unit + ":" + transform, applies,
                    equipmentScope(measuredAxis), Operation.manual(), "native_enchantments", Origin.NATIVE, unknown));
        }
    }
    /** Native ItemPredicate requires membership whenever items is present. A direct
     * empty list cannot match any tool; omitted items remain unrestricted. Do not
     * evaluate tags, custom predicates, inversion or gameplay callbacks here. */
    static boolean impossibleRequirements(Optional<net.minecraft.world.level.storage.loot.predicates.LootItemCondition> requirements) {
        if (requirements.orElse(null) instanceof net.minecraft.world.level.storage.loot.predicates.MatchTool match) {
            //? if >=26.1.2
            /*var items = match.predicate().flatMap(net.minecraft.advancements.criterion.ItemPredicate::items);*/
            //? if <26.1.2
            var items = match.predicate().flatMap(net.minecraft.advancements.critereon.ItemPredicate::items);
            return items.isPresent() && items.get().getClass() == net.minecraft.core.HolderSet.Direct.class
                    && items.get().size() == 0;
        }
        return false;
    }
    /** Never execute a custom level implementation. */
    static Double levelValue(LevelBasedValue value, int level) {
        if (nativeLevel(value, 0)) {
            double result = value.calculate(level); return Double.isFinite(result) ? result : null;
        }
        return null;
    }
    private static boolean nativeLevel(LevelBasedValue value, int depth) {
        if (depth > 32) return false;
        if (value instanceof LevelBasedValue.Constant || value instanceof LevelBasedValue.Linear || value instanceof LevelBasedValue.LevelsSquared) return true;
        if (value instanceof LevelBasedValue.Fraction f) return nativeLevel(f.numerator(), depth + 1) && nativeLevel(f.denominator(), depth + 1);
        if (value instanceof LevelBasedValue.Clamped c) return nativeLevel(c.value(), depth + 1);
        if (value instanceof LevelBasedValue.Lookup l) return nativeLevel(l.fallback(), depth + 1);
        return false;
    }
    public static CapabilityAxis attributeAxis(String id) {
        return switch (id) {
            case "minecraft:player.submerged_mining_speed", "minecraft:player.mining_efficiency", "minecraft:player.block_break_speed" -> CapabilityAxis.MINING_SPEED;
            case "minecraft:generic.attack_damage" -> CapabilityAxis.MELEE_DAMAGE;
            case "minecraft:generic.attack_speed" -> CapabilityAxis.ATTACK_RATE;
            case "minecraft:generic.armor" -> CapabilityAxis.ARMOR;
            case "minecraft:generic.armor_toughness" -> CapabilityAxis.TOUGHNESS;
            case "minecraft:generic.max_health" -> CapabilityAxis.MAX_HEALTH;
            case "minecraft:generic.knockback_resistance" -> CapabilityAxis.KNOCKBACK_RESISTANCE;
            case "minecraft:generic.movement_speed", "minecraft:player.sneaking_speed", "minecraft:generic.water_movement_efficiency" -> CapabilityAxis.GROUND_SPEED;
            case "minecraft:generic.oxygen_bonus" -> CapabilityAxis.RESOURCE_CONSUMPTION;
            case "minecraft:player.sweeping_damage_ratio" -> CapabilityAxis.AREA_DAMAGE;
            case "minecraft:generic.step_height" -> CapabilityAxis.STEP_HEIGHT;
            case "minecraft:generic.jump_strength" -> CapabilityAxis.JUMP;
            case "minecraft:player.block_interaction_range", "minecraft:player.entity_interaction_range" -> CapabilityAxis.REACH;
            default -> null;
        };
    }
    public static Scope equipmentScope(CapabilityAxis axis) {
        return switch (axis) {
            case BURST_DAMAGE, SUSTAINED_DAMAGE, MELEE_DAMAGE, RANGED_DAMAGE, MAGIC_DAMAGE, DAMAGE_OVER_TIME, CRITICAL_DAMAGE ->
                    new Scope("combat_target", "single", null, 1.0);
            case MINING_SPEED, HARVEST_LEVEL, HARVEST_SPEED -> new Scope("harvest_target", "single", null, 1.0);
            case DURABILITY, DURABILITY_REDUCTION, INDESTRUCTIBILITY, REPAIR, ITEM_LOSS_PREVENTION -> new Scope("equipped_item", "single", null, 1.0);
            case AREA_DAMAGE, AREA_MINING, VEIN_MINING -> Scope.unknown();
            default -> Scope.self();
        };
    }
    public static Measurement measurement(CapabilityAxis axis, Double magnitude, String unit, String applies, Scope scope,
                                          Operation operation, String provider, Origin origin, List<String> unknown) {
        return new Measurement(family(axis), axis, magnitude, unit, applies, scope, operation, provider, origin, unknown);
    }
    public static CapabilityFamily family(CapabilityAxis axis) {
        return switch (axis) {
            case ARMOR, TOUGHNESS, EFFECTIVE_HEALTH, MAX_HEALTH, DAMAGE_REDUCTION, AVOIDANCE, BLOCKING, REFLECTION,
                 RECOVERY, HEALING, REGENERATION, STATUS_RESISTANCE, SHIELD_CAPACITY, DAMAGE_IMMUNITY,
                 KNOCKBACK_RESISTANCE, BURST_SURVIVAL, SUSTAINED_SURVIVAL -> CapabilityFamily.DEFENSE;
            case DURABILITY, DURABILITY_REDUCTION, INDESTRUCTIBILITY, REPAIR, ITEM_LOSS_PREVENTION -> CapabilityFamily.ITEM_PRESERVATION;
            case GROUND_SPEED, JUMP, VERTICAL_MOVEMENT, FALL_CONTROL, GLIDING, FLIGHT, ABILITIES_FLYING_SPEED, TELEPORTATION, STEP_HEIGHT -> CapabilityFamily.MOBILITY;
            case MINING_SPEED, AREA_MINING, VEIN_MINING, HARVEST_LEVEL, CROP_YIELD, DROP_YIELD, HARVEST_SPEED,
                 FISHING_PRODUCTIVITY, FISHING_TIME_REDUCTION, REACH, TOOL_VERSATILITY -> CapabilityFamily.MANUAL_GATHERING;
            case AUTOMATED_EXTRACTION, AUTOMATED_FARMING, AUTOMATED_FISHING, MOB_FARMING, PASSIVE_GENERATION, AUTOMATION_INTERACTION -> CapabilityFamily.RESOURCE_AUTOMATION;
            case CROP_ACCELERATION, TREE_ACCELERATION, ANIMAL_ACCELERATION -> CapabilityFamily.BIOLOGICAL_GROWTH;
            case SPAWN_SUPPRESSION, MOB_REPULSION, HAZARD_SUPPRESSION -> CapabilityFamily.WORLD_CONTROL;
            case MACHINE_ACCELERATION, BLOCK_ENTITY_ACCELERATION, ENTITY_ACCELERATION, LOCAL_TIME_ACCELERATION -> CapabilityFamily.TIME_ACCELERATION;
            case INFORMATION -> CapabilityFamily.INFORMATION;
            case INVENTORY -> CapabilityFamily.STORAGE;
            case CONVERSION, THROUGHPUT, RESOURCE_CONSUMPTION -> CapabilityFamily.PROCESSING;
            case CONVENIENCE, ENCHANTING_EFFICIENCY, ANVIL_EFFICIENCY, EXPERIENCE -> CapabilityFamily.MAGIC_UTILITY;
            default -> CapabilityFamily.COMBAT_OFFENSE;
        };
    }
}

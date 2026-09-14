package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.*;

/** Native mechanic declarations, not prices or tier assignments. Conditions express applicability breadth. */
final class BonusSemantics {
    enum Response { MULTIPLIER, PREVENTION, HEALTH, RECOVERY, AIR, REACH_DISTANCE, STEP_BOUNDARY, YIELD_LEVEL, LUCK }
    record Mechanic(CapabilityAxis axis, Response response, List<String> conditions, String nativeMechanism) {}
    private BonusSemantics() {}
    static Mechanic require(StatDefinition stat) {
        return switch (stat.id().getPath()) {
            case "movement_speed" -> mechanic(GROUND_SPEED, Response.MULTIPLIER, "movement speed attribute");
            case "swim_speed" -> mechanic(GROUND_SPEED, Response.MULTIPLIER, "swimming movement multiplier", "submerged");
            case "guarded_movement" -> mechanic(GROUND_SPEED, Response.MULTIPLIER, "shield use movement multiplier", "shield raised", "guarding");
            case "sneak_speed" -> mechanic(GROUND_SPEED, Response.MULTIPLIER, "sneaking speed attribute", "sneaking");
            case "flight_speed" -> mechanic(FLIGHT, Response.MULTIPLIER, "Abilities#flyingSpeed", "compatible standard flight");
            case "jump_height" -> mechanic(JUMP, Response.MULTIPLIER, "jump strength attribute; squared ballistic height", "jumping");
            case "step_height" -> mechanic(VERTICAL_MOVEMENT, Response.STEP_BOUNDARY, "additive step-height attribute", "ground obstacle", "jump alternative");
            case "melee_damage" -> mechanic(SUSTAINED_DAMAGE, Response.MULTIPLIER, "melee hit damage", "melee hit");
            case "ranged_damage" -> mechanic(SUSTAINED_DAMAGE, Response.MULTIPLIER, "ranged hit damage", "projectile hit");
            case "magic_damage" -> mechanic(SUSTAINED_DAMAGE, Response.MULTIPLIER, "caster hit damage", "caster hit");
            case "melee_attack_speed" -> mechanic(ATTACK_RATE, Response.MULTIPLIER, "attack cooldown recovery", "melee hit", "repeated attack");
            case "ranged_attack_speed" -> mechanic(ATTACK_RATE, Response.MULTIPLIER, "bow draw time", "projectile hit", "repeated attack");
            case "magic_cast_speed" -> mechanic(ATTACK_RATE, Response.MULTIPLIER, "caster cooldown", "caster hit", "repeated attack");
            case "projectile_speed" -> mechanic(DELIVERY_RELIABILITY, Response.MULTIPLIER, "projectile travel velocity", "projectile", "travel time matters");
            case "attack_knockback" -> mechanic(CROWD_CONTROL, Response.MULTIPLIER, "attack knockback attribute", "hit", "target accepts knockback");
            case "melee_resistance" -> mechanic(DAMAGE_REDUCTION, Response.PREVENTION, "incoming melee damage fraction", "melee damage");
            case "ranged_resistance" -> mechanic(DAMAGE_REDUCTION, Response.PREVENTION, "incoming ranged damage fraction", "projectile damage");
            case "magic_resistance" -> mechanic(DAMAGE_REDUCTION, Response.PREVENTION, "incoming magic damage fraction", "magic damage");
            case "fall_resistance" -> mechanic(FALL_CONTROL, Response.PREVENTION, "incoming fall damage fraction", "fall damage", "failed landing");
            case "fire_resistance" -> mechanic(DAMAGE_REDUCTION, Response.PREVENTION, "incoming fire damage fraction", "fire damage", "hazard exposure");
            case "explosion_resistance" -> mechanic(DAMAGE_REDUCTION, Response.PREVENTION, "incoming explosion damage fraction", "explosion", "blast exposure");
            case "knockback_resistance" -> mechanic(AVOIDANCE, Response.PREVENTION, "knockback resistance attribute", "incoming knockback");
            case "status_resistance" -> mechanic(STATUS_RESISTANCE, Response.PREVENTION, "harmful status duration", "harmful status", "duration matters");
            case "damage_reflection" -> mechanic(REFLECTION, Response.MULTIPLIER, "reflected incoming damage", "incoming hit", "reflectable source");
            case "guard_readiness" -> mechanic(BLOCKING, Response.MULTIPLIER, "shield raise and recovery cooldown", "shield", "guard transition");
            case "max_health" -> mechanic(EFFECTIVE_HEALTH, Response.HEALTH, "maximum health attribute in hearts");
            case "health_regeneration" -> mechanic(REGENERATION, Response.RECOVERY, "health regeneration in hearts per second", "missing health");
            case "healing_effectiveness" -> mechanic(HEALING, Response.MULTIPLIER, "received healing multiplier", "missing health", "healing source");
            case "hunger_efficiency" -> mechanic(RESOURCE_CONSUMPTION, Response.PREVENTION, "exhaustion reduction", "food exhaustion");
            case "breath_hold" -> mechanic(CONVENIENCE, Response.AIR, "additional underwater air seconds", "submerged", "air exhausted");
            case "mining_speed" -> mechanic(MINING_SPEED, Response.MULTIPLIER, "block break speed", "mining");
            case "fortune" -> mechanic(DROP_YIELD, Response.YIELD_LEVEL, "expected fractional Fortune yield", "fortune-compatible loot");
            case "looting" -> mechanic(DROP_YIELD, Response.YIELD_LEVEL, "expected fractional Looting yield", "entity kill", "looting-compatible loot");
            case "crop_yield" -> mechanic(CROP_YIELD, Response.MULTIPLIER, "crop yield multiplier", "crop harvest");
            case "experience_gain" -> mechanic(EXPERIENCE, Response.MULTIPLIER, "experience pickup multiplier", "experience source");
            case "durability_efficiency" -> mechanic(DURABILITY, Response.PREVENTION, "durability loss prevention", "damageable item");
            case "reach" -> mechanic(REACH, Response.REACH_DISTANCE, "block/entity interaction range attributes");
            case "anvil_efficiency" -> mechanic(ANVIL_EFFICIENCY, Response.PREVENTION, "anvil experience cost", "anvil use", "paid operation");
            case "enchanting_efficiency" -> mechanic(ENCHANTING_EFFICIENCY, Response.PREVENTION, "enchanting experience cost", "enchanting", "paid operation");
            case "luck" -> mechanic(DROP_YIELD, Response.LUCK, "loot quality weighting", "loot table uses quality", "eligible pool");
            default -> throw new IllegalArgumentException("Missing native Bonus semantics " + stat.id());
        };
    }
    private static Mechanic mechanic(CapabilityAxis axis, Response response, String mechanism, String... conditions) {
        return new Mechanic(axis, response, List.of(conditions), mechanism);
    }
}

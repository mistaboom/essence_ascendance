package com.mistaboom.essence_ascendance.balance.engine;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.enchantment.effects.*;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Additional native records with explicit physical units. Reads no callbacks or random outcomes. */
final class NativeEnchantmentEffects {
    private NativeEnchantmentEffects() { }
    static final Set<String> COMPONENTS = Set.of("minecraft:damage_immunity", "minecraft:smash_damage_per_fallen_block",
            "minecraft:knockback", "minecraft:armor_effectiveness", "minecraft:post_attack", "minecraft:hit_block",
            "minecraft:equipment_drops", "minecraft:location_changed", "minecraft:tick", "minecraft:ammo_use",
            "minecraft:projectile_piercing", "minecraft:projectile_spawned", "minecraft:projectile_spread",
            "minecraft:projectile_count", "minecraft:trident_return_acceleration", "minecraft:block_experience",
            "minecraft:mob_experience", "minecraft:crossbow_charge_time", "minecraft:trident_spin_attack_strength");

    static List<Measurement> read(Enchantment enchantment, int level, CapabilitySink sink, String id) {
        List<Measurement> out = new ArrayList<>();
        for (var component : enchantment.effects()) {
            var componentId = BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getKey(component.type());
            if (componentId != null && COMPONENTS.contains(componentId.toString()))
                audit(component.value(), componentId.toString(), sink, id, 0);
        }
        values(enchantment, EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK, CapabilityAxis.BURST_DAMAGE, "health_per_fallen_block", false, level, out);
        values(enchantment, EnchantmentEffectComponents.KNOCKBACK, CapabilityAxis.CROWD_CONTROL, "knockback_strength", false, level, out);
        values(enchantment, EnchantmentEffectComponents.ARMOR_EFFECTIVENESS, CapabilityAxis.ARMOR_PENETRATION, "armor_effectiveness_reduction", true, level, out);
        values(enchantment, EnchantmentEffectComponents.AMMO_USE, CapabilityAxis.RESOURCE_CONSUMPTION, "ammunition_reduction", true, level, out);
        values(enchantment, EnchantmentEffectComponents.PROJECTILE_PIERCING, CapabilityAxis.DELIVERY_RELIABILITY, "additional_pierced_entities", false, level, out);
        values(enchantment, EnchantmentEffectComponents.PROJECTILE_COUNT, CapabilityAxis.AREA_DAMAGE, "additional_projectiles", false, level, out);
        values(enchantment, EnchantmentEffectComponents.PROJECTILE_SPREAD, CapabilityAxis.DELIVERY_RELIABILITY, "projectile_spread_reduction", true, level, out);
        values(enchantment, EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION, CapabilityAxis.CONVENIENCE, "return_acceleration", false, level, out);
        values(enchantment, EnchantmentEffectComponents.BLOCK_EXPERIENCE, CapabilityAxis.EXPERIENCE, "block_xp_change", false, level, out);
        values(enchantment, EnchantmentEffectComponents.MOB_EXPERIENCE, CapabilityAxis.EXPERIENCE, "mob_xp_change", false, level, out);
        var charge = enchantment.effects().get(EnchantmentEffectComponents.CROSSBOW_CHARGE_TIME);
        if (charge != null) value(charge, CapabilityAxis.ATTACK_RATE, "charge_seconds_reduced", true, level, "crossbow_charge_time", out, 0);
        var spin = enchantment.effects().get(EnchantmentEffectComponents.TRIDENT_SPIN_ATTACK_STRENGTH);
        if (spin != null) value(spin, CapabilityAxis.VERTICAL_MOVEMENT, "spin_strength", false, level, "trident_spin_attack_strength", out, 0);
        for (var effect : enchantment.getEffects(EnchantmentEffectComponents.DAMAGE_IMMUNITY))
            if (!NativeCapabilityReader.impossibleRequirements(effect.requirements()))
                add(CapabilityAxis.DAMAGE_IMMUNITY, 1.0, "presence", condition("damage_immunity", effect.requirements().isPresent()), out);
        for (var effect : enchantment.getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS))
            if (!NativeCapabilityReader.impossibleRequirements(effect.requirements()))
                value(effect.effect(), CapabilityAxis.DROP_YIELD, "equipment_drop_probability", false, level,
                        condition("equipment_drops", effect.requirements().isPresent()), out, 0);
        for (var effect : enchantment.getEffects(EnchantmentEffectComponents.POST_ATTACK))
            if (!NativeCapabilityReader.impossibleRequirements(effect.requirements()))
                location(effect.effect(), level, condition("post_attack; enchanted=" + effect.enchanted() + "; affected=" + effect.affected(), effect.requirements().isPresent()),
                        new AttackTargets(effect.enchanted(), effect.affected()), out, 0);
        for (var effect : enchantment.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED))
            if (!NativeCapabilityReader.impossibleRequirements(effect.requirements()))
                location(effect.effect(), level, condition("projectile_spawned", effect.requirements().isPresent()), out, 0);
        for (var effect : enchantment.getEffects(EnchantmentEffectComponents.HIT_BLOCK))
            if (!NativeCapabilityReader.impossibleRequirements(effect.requirements()))
                location(effect.effect(), level, condition("hit_block", effect.requirements().isPresent()), out, 0);
        for (var effect : enchantment.getEffects(EnchantmentEffectComponents.LOCATION_CHANGED))
            if (!NativeCapabilityReader.impossibleRequirements(effect.requirements()))
                location(effect.effect(), level, condition("location_changed", effect.requirements().isPresent()), out, 0);
        for (var effect : enchantment.getEffects(EnchantmentEffectComponents.TICK))
            if (!NativeCapabilityReader.impossibleRequirements(effect.requirements()))
                location(effect.effect(), level, condition("tick", effect.requirements().isPresent()), out, 0);
        return List.copyOf(out);
    }
    /** XP suppression can accompany loot-predicate harvesting without expressing its benefit
     * in this definition. Only these completely understood nonpositive effects permit the
     * same weak supported-equipment fallback as an empty native effect map. */
    static boolean onlyExperienceSuppression(Enchantment enchantment, int level) {
        for (var component : enchantment.effects()) {
            if (component.type() != EnchantmentEffectComponents.BLOCK_EXPERIENCE
                    && component.type() != EnchantmentEffectComponents.MOB_EXPERIENCE) return false;
            if (!(component.value() instanceof List<?> entries)) return false;
            for (var entry : entries) {
                if (!(entry instanceof ConditionalEffect<?> conditional) || !(conditional.effect() instanceof SetValue set)) return false;
                if (NativeCapabilityReader.impossibleRequirements(conditional.requirements())) return false;
                Double amount = NativeCapabilityReader.levelValue(set.value(), level);
                if (amount == null || amount != 0) return false;
            }
        }
        return true;
    }

    private static void audit(Object value, String component, CapabilitySink sink, String id, int depth) {
        if (depth > 16) {
            sink.candidate(id, "native_enchantments", "Nested native effect depth excluded: " + component); return;
        }
        if (value instanceof List<?> list) list.forEach(child -> audit(child, component, sink, id, depth + 1));
        else if (value instanceof ConditionalEffect<?> effect) {
            if (NativeCapabilityReader.impossibleRequirements(effect.requirements()))
                sink.candidate(id, "native_enchantments", "Impossible native match_tool condition: explicitly empty item alternatives; effect excluded");
            else audit(effect.effect(), component, sink, id, depth + 1);
        } else if (value instanceof TargetedConditionalEffect<?> effect) {
            if (NativeCapabilityReader.impossibleRequirements(effect.requirements()))
                sink.candidate(id, "native_enchantments", "Impossible native match_tool condition: explicitly empty item alternatives; effect excluded");
            else audit(effect.effect(), component, sink, id, depth + 1);
        } else if (value instanceof AllOf.ValueEffects all) all.effects().forEach(child -> audit(child, component, sink, id, depth + 1));
        else if (value instanceof AllOf.EntityEffects all) all.effects().forEach(child -> audit(child, component, sink, id, depth + 1));
        else if (value instanceof AllOf.LocationBasedEffects all) all.effects().forEach(child -> audit(child, component, sink, id, depth + 1));
        else if (!(value instanceof AddValue || value instanceof MultiplyValue || value instanceof SetValue
                || value instanceof EnchantmentAttributeEffect || value instanceof DamageImmunity || value instanceof Ignite
                || value instanceof DamageEntity || value instanceof ReplaceDisk || value instanceof ExplodeEffect
                || value instanceof SummonEntityEffect || value instanceof ApplyMobEffect
                || value instanceof PlaySoundEffect || value instanceof SpawnParticlesEffect))
            sink.candidate(id, "native_enchantments", "Unsupported nested effect in " + component + ": " + value.getClass().getName()
                    + "; behavior/cost not evaluated", Set.of(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR);
    }

    private static void values(Enchantment enchantment, DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> component,
                               CapabilityAxis axis, String unit, boolean reduction, int level, List<Measurement> out) {
        String source = BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getKey(component).toString();
        for (var effect : enchantment.getEffects(component)) if (!NativeCapabilityReader.impossibleRequirements(effect.requirements()))
            value(effect.effect(), axis, unit, reduction, level,
                condition(source, effect.requirements().isPresent()), out, 0);
    }
    private static void value(EnchantmentValueEffect effect, CapabilityAxis axis, String unit, boolean reduction,
                              int level, String source, List<Measurement> out, int depth) {
        if (depth > 16) return;
        if (effect instanceof AllOf.ValueEffects all) {
            // Preserve distinct native operations. Economic scores merge duplicate axes by max;
            // skill consumers retain units/conditions and cannot treat this as a composed scalar.
            for (var child : all.effects()) value(child, axis, unit, reduction, level, source + "; composition=all_of", out, depth + 1);
            return;
        }
        Double amount = null; String operation = "unknown";
        if (effect instanceof AddValue add) { amount = NativeCapabilityReader.levelValue(add.value(), level); operation = "add"; if (amount != null && reduction) amount = -amount; }
        else if (effect instanceof MultiplyValue multiply) { amount = NativeCapabilityReader.levelValue(multiply.factor(), level); operation = "relative_change";
            if (amount != null) amount = reduction ? 1 - amount : amount - 1; }
        else if (effect instanceof SetValue set) { amount = NativeCapabilityReader.levelValue(set.value(), level); operation = "set";
            // A fixed zero consumption is established; other fixed baselines are unknown.
            if (reduction) amount = amount != null && amount == 0 ? 1.0 : null; }
        add(axis, amount, unit + ":" + operation, source, out);
    }
    private static void location(EnchantmentLocationBasedEffect effect, int level, String source, List<Measurement> out, int depth) {
        location(effect, level, source, new AttackTargets(null, null), out, depth);
    }
    private record AttackTargets(EnchantmentTarget enchanted, EnchantmentTarget affected) {
        boolean self() { return enchanted != null && enchanted == affected; }
        boolean opposing() { return enchanted != null && affected != null
                && (enchanted == EnchantmentTarget.VICTIM) != (affected == EnchantmentTarget.VICTIM); }
        boolean reflected() { return opposing() && enchanted == EnchantmentTarget.VICTIM; }
    }
    private static void location(EnchantmentLocationBasedEffect effect, int level, String source,
                                 AttackTargets targets, List<Measurement> out, int depth) {
        if (depth > 16) return;
        if (effect instanceof AllOf.EntityEffects all) {
            all.effects().forEach(child -> location(child, level, source, targets, out, depth + 1));
        } else if (effect instanceof AllOf.LocationBasedEffects all) {
            all.effects().forEach(child -> location(child, level, source, targets, out, depth + 1));
        } else if (effect instanceof EnchantmentAttributeEffect attribute) {
            var id = attribute.attribute().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
            var axis = NativeCapabilityReader.attributeAxis(id);
            if (axis != null) add(axis, NativeCapabilityReader.levelValue(attribute.amount(), level),
                    attribute.operation() == AttributeModifier.Operation.ADD_VALUE ? "attribute_addition" : "attribute_multiplier_addition", source + "; attribute=" + id, out);
        } else if (effect instanceof Ignite ignite) {
            Double duration = NativeCapabilityReader.levelValue(ignite.duration(), level);
            if (source.startsWith("projectile_spawned")) {
                // Setting a projectile alight does not set its victim alight for the same duration.
                // The projectile's hit handler owns victim burning; no duration/DPS is inferred.
                if (duration != null && duration > 0) add(CapabilityAxis.DAMAGE_OVER_TIME, 1.0,
                        "ignited_projectile_presence", source + "; conditional=projectile_hit_required", out);
            } else add(CapabilityAxis.DAMAGE_OVER_TIME, duration, "ignite_seconds", source, out);
        } else if (effect instanceof DamageEntity damage) {
            // Damage delivered by an enchanted victim back to its attacker is defensive reflection.
            // Direct damage to the enchanted entity itself is a cost, not a positive capability.
            if (!targets.self()) add(targets.reflected() ? CapabilityAxis.REFLECTION : CapabilityAxis.BURST_DAMAGE,
                    NativeCapabilityReader.levelValue(damage.minDamage(), level), "health_points", source, out);
        } else if (effect instanceof ReplaceDisk disk) {
            // A radius is spatial utility, not extraction throughput or an asserted harvest.
            add(CapabilityAxis.CONVENIENCE, NativeCapabilityReader.levelValue(disk.radius(), level), "replacement_disk_radius", source, out);
        } else if (effect instanceof ExplodeEffect explosion) {
            if (targets.self() && explosion.damageType().isEmpty() && explosion.knockbackMultiplier().isPresent()) {
                // A non-damaging explosion centered on the enchanted participant supplies movement.
                // Its native multiplier, not its fixed area radius, carries the level curve.
                add(CapabilityAxis.VERTICAL_MOVEMENT, NativeCapabilityReader.levelValue(explosion.knockbackMultiplier().get(), level),
                        "self_explosion_knockback_multiplier", source, out);
            } else {
                // Do not turn explosion radius into invented HP/DPS. Retain actual geometric control.
                add(CapabilityAxis.CROWD_CONTROL, NativeCapabilityReader.levelValue(explosion.radius(), level), "explosion_radius", source, out);
            }
        } else if (effect instanceof SummonEntityEffect summon) {
            if (summon.entityTypes().size() > 0) add(CapabilityAxis.CONVENIENCE, 1.0, "summon_presence", source, out);
        } else if (effect instanceof ApplyMobEffect applied) {
            // Native harmful status directed at an opponent establishes control, without guessing
            // damage/healing or the random duration/amplifier. Mixed effect pools remain uncertain.
            boolean harmful = applied.toApply().size() > 0 && applied.toApply().stream()
                    .allMatch(holder -> holder.value().getCategory() == MobEffectCategory.HARMFUL);
            if (applied.toApply().size() > 0 && !(harmful && targets.self()))
                add(harmful && targets.opposing() ? CapabilityAxis.CROWD_CONTROL : CapabilityAxis.CONVENIENCE,
                        1.0, "status_application_presence", source, out);
        }
    }
    private static String condition(String source, boolean conditional) { return source + (conditional ? "; conditional=present" : ""); }
    private static void add(CapabilityAxis axis, Double magnitude, String unit, String source, List<Measurement> out) {
        if (magnitude == null || !Double.isFinite(magnitude) || magnitude <= 0) return;
        var unknown = source.contains("conditional=") ? List.of("Conditional native effect; uptime and target match frequency unmeasured") : List.<String>of();
        out.add(CompetitiveCapabilities.measurement(axis, magnitude, unit, "effect=" + source,
                CompetitiveCapabilities.equipmentScope(axis), Operation.manual(), "native_enchantments", Origin.NATIVE, unknown));
    }
}

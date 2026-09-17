package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.network.VanillaPlayerAttributeSyncService;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Updates only a handler's exact namespaced transient ID, and only when its value changes. */
public final class SkillEffectAttributes {
    private static final double EPSILON = 0.0000001;

    private SkillEffectAttributes() { }

    public static void apply(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id,
                      double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier previous = instance.getModifier(id);
        boolean changed = false;
        if (!Double.isFinite(amount) || Math.abs(amount) <= EPSILON) {
            if (previous != null) {
                instance.removeModifier(id);
                changed = true;
            }
        } else if (previous == null || previous.operation() != operation
                || Math.abs(previous.amount() - amount) > EPSILON) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
            changed = true;
        }
        if (changed && entity instanceof ServerPlayer player) {
            VanillaPlayerAttributeSyncService.syncOwner(player, attribute);
        }
    }

    public static void minimum(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double minimum) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        double subtotal = instance.getBaseValue(), baseMultiplier = 1, totalMultiplier = 1;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.id().equals(id)) continue;
            switch (modifier.operation()) {
                case ADD_VALUE -> subtotal += modifier.amount();
                case ADD_MULTIPLIED_BASE -> baseMultiplier += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> totalMultiplier *= 1 + modifier.amount();
            }
        }
        double multiplier = baseMultiplier * totalMultiplier;
        apply(entity, attribute, id, SkillEffectMath.attributeFloorAddition(subtotal, multiplier, minimum),
                AttributeModifier.Operation.ADD_VALUE);
    }

    public static void reduction(LivingEntity target, Holder<Attribute> attribute, ResourceLocation id,
                          double requested) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null) return;
        // ADD_VALUE acts on the additive subtotal, before percentage modifiers.
        // Account for all other additive modifiers (including other attackers)
        // without removing them or temporarily dirtying/synchronizing the attribute.
        double available = instance.getBaseValue();
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!modifier.id().equals(id) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                available += modifier.amount();
            }
        }
        double reduction = Math.min(SkillEffectMath.clamp(requested, 0.0, 1_000_000.0),
                SkillEffectMath.clamp(available, 0.0, 1_000_000.0));
        apply(target, attribute, id, -reduction, AttributeModifier.Operation.ADD_VALUE);
    }
}

package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/*
 * Context-aware equipment stat resolver.
 *
 * It never scans inventory. It resolves equipment that is actually active:
 * worn armor and/or the hand performing an action.
 *
 * Worn-item raw applicability and armor-coverage-weighted applicability are
 * intentionally separate APIs. A passive player-wide armor effect usually
 * wants coverage weighting; an item-local event such as durability loss can
 * query the worn item directly and receive its full applicability strength.
 */
public final class EquipmentStatResolver {

    private static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
            );

    private EquipmentStatResolver() {
    }

    /*
     * Default active-state query: worn passive effects plus main hand.
     */
    public static EquipmentStatState evaluate(LivingEntity entity) {
        return evaluateForHand(entity, InteractionHand.MAIN_HAND);
    }

    /*
     * Action-specific query. Worn passive effects and the selected hand merge
     * by MAX for the same stat so one player investment is never double-dipped
     * merely because several active contexts allow it.
     */
    public static EquipmentStatState evaluateForHand(
            LivingEntity entity,
            InteractionHand hand
    ) {
        Objects.requireNonNull(entity, "Entity cannot be null");
        Objects.requireNonNull(hand, "Interaction hand cannot be null");

        EquipmentStatState worn = evaluateWornArmor(entity);
        EquipmentStatState held = evaluateHeldOnly(entity, hand);

        return worn.mergeMax(held);
    }

    public static EquipmentStatState evaluateHeldOnly(
            LivingEntity entity,
            InteractionHand hand
    ) {
        Objects.requireNonNull(entity, "Entity cannot be null");
        Objects.requireNonNull(hand, "Interaction hand cannot be null");

        return evaluateItem(
                entity,
                entity.getItemInHand(hand),
                EquipmentActivationType.HELD
        );
    }

    /*
     * Raw WORN applicability for one actual armor slot, without coverage
     * scaling. Use this for an effect local to that ItemStack.
     */
    public static EquipmentStatState evaluateWornItem(
            LivingEntity entity,
            EquipmentSlot slot
    ) {
        Objects.requireNonNull(entity, "Entity cannot be null");
        requireArmorSlot(slot);

        return evaluateItem(
                entity,
                entity.getItemBySlot(slot),
                EquipmentActivationType.WORN
        );
    }

    /*
     * This slot's contribution to player-wide passive worn effects.
     */
    public static EquipmentStatState evaluateWornContribution(
            LivingEntity entity,
            EquipmentSlot slot
    ) {
        EquipmentStatState raw = evaluateWornItem(entity, slot);
        return EquipmentStatState.none().addScaled(
                raw.values(),
                ArmorStatWeights.weightFor(slot)
        );
    }

    /** Guard-only effects are queried explicitly; they never become generic held/worn passives. */
    public static EquipmentStatState evaluateGuardingShield(LivingEntity entity) {
        return EquipmentShieldService.isGuarding(entity)
                ? evaluateItem(entity, entity.getUseItem(), EquipmentActivationType.GUARDING)
                : EquipmentStatState.none();
    }

    /* Full passive armor state. Four qualifying pieces add their coverage shares to 1.0x. */
    public static EquipmentStatState evaluateWornArmor(LivingEntity entity) {
        Objects.requireNonNull(entity, "Entity cannot be null");

        EquipmentStatState result = EquipmentStatState.none();

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            EquipmentStatState raw = evaluateWornItem(entity, slot);
            if (raw.values().isEmpty()) {
                continue;
            }

            result = result.addScaled(
                    raw.values(),
                    ArmorStatWeights.weightFor(slot)
            );
        }

        return result;
    }

    public static EquipmentStatState evaluateItem(
            LivingEntity entity,
            ItemStack stack,
            EquipmentActivationType activation
    ) {
        Objects.requireNonNull(entity, "Entity cannot be null");
        EquipmentStatState raw = evaluateItem(stack, activation);
        if (!(entity instanceof ServerPlayer player) || !EquipmentTierData.isAscendanceEquipment(stack)) {
            return raw;
        }
        if (FracturedEquipmentData.isFractured(stack)) {
            return EquipmentStatState.none();
        }
        EquipmentTier itemTier = EquipmentTierData.tier(stack);
        if (itemTier == EquipmentTier.LATENT) {
            return EquipmentStatState.none();
        }
        PlayerEssenceData playerData = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        EquipmentTier playerTier = EquipmentTier.fromAscendanceTier(playerData.getTier());
        EquipmentTier effective = itemTier.order() <= playerTier.order() ? itemTier : playerTier;
        if (effective == playerTier) {
            return raw;
        }

        java.util.Map<net.minecraft.resources.ResourceLocation, Double> adjusted = new java.util.LinkedHashMap<>();
        for (var entry : raw.values().entrySet()) {
            var stat = EssenceStatRegistry.get(entry.getKey()).orElse(null);
            if (stat == null) continue;
            double uncapped = StatScalingService.evaluate(playerData, stat).scaledBonus();
            if (uncapped <= 0.0D) {
                adjusted.put(entry.getKey(), entry.getValue());
                continue;
            }
            double capped = StatScalingService.scaledBonusForTier(playerData, stat, effective.ascendanceTier());
            adjusted.put(entry.getKey(), entry.getValue() * Math.max(0.0D, Math.min(1.0D, capped / uncapped)));
        }
        return new EquipmentStatState(adjusted);
    }

    public static EquipmentStatState evaluateItem(
            ItemStack stack,
            EquipmentActivationType activation
    ) {
        Objects.requireNonNull(stack, "Item stack cannot be null");
        Objects.requireNonNull(activation, "Activation type cannot be null");

        if (EquipmentTierData.isAscendanceEquipment(stack)
                && (FracturedEquipmentData.isFractured(stack)
                || EquipmentTierData.tier(stack) == EquipmentTier.LATENT)) {
            return EquipmentStatState.none();
        }
        return new EquipmentStatState(
                EquipmentStatProviderRegistry
                        .evaluate(stack)
                        .strengths(activation)
        );
    }

    public static EquipmentStatProfile inspectItem(ItemStack stack) {
        return EquipmentStatProviderRegistry.evaluate(stack);
    }

    public static List<EquipmentSlot> armorSlots() {
        return ARMOR_SLOTS;
    }

    private static void requireArmorSlot(EquipmentSlot slot) {
        Objects.requireNonNull(slot, "Equipment slot cannot be null");
        if (!ARMOR_SLOTS.contains(slot)) {
            throw new IllegalArgumentException(
                    "Equipment slot is not a player armor slot: " + slot
            );
        }
    }
}

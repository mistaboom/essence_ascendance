package com.mistaboom.essence_ascendance.equipment;

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

    /*
     * Full passive armor state. Separate pieces add their configured coverage
     * shares, so a complete qualifying set naturally reaches 1.0x.
     */
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
            ItemStack stack,
            EquipmentActivationType activation
    ) {
        Objects.requireNonNull(stack, "Item stack cannot be null");
        Objects.requireNonNull(activation, "Activation type cannot be null");

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

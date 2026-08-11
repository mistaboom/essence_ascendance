package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EquipmentConduitResolver {

    private static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
            );


    private EquipmentConduitResolver() {
    }


    /*
     * ============================================================
     * DEFAULT EQUIPMENT STATE
     * ============================================================
     *
     * Default held-item context is MAIN HAND.
     *
     * Event-specific gameplay can explicitly use evaluateForHand()
     * when the actual action came from the offhand.
     */

    public static EquipmentConduitState evaluate(
            LivingEntity entity
    ) {

        return evaluateForHand(
                entity,
                InteractionHand.MAIN_HAND
        );
    }


    /*
     * ============================================================
     * ACTION/HAND-SPECIFIC STATE
     * ============================================================
     */

    public static EquipmentConduitState evaluateForHand(
            LivingEntity entity,
            InteractionHand hand
    ) {

        Objects.requireNonNull(
                entity,
                "Entity cannot be null"
        );

        Objects.requireNonNull(
                hand,
                "Interaction hand cannot be null"
        );


        Map<EquipmentConduitType, Double> strengths =
                new EnumMap<>(
                        EquipmentConduitType.class
                );


        /*
         * Armor is persistent equipment context.
         */

        double armorStrength =
                armorStrength(
                        entity
                );


        if (armorStrength > 0.0) {

            strengths.put(
                    EquipmentConduitType.ARMOR_SET,
                    armorStrength
            );
        }


        /*
         * Resolve the item used by the selected hand.
         */

        ItemStack heldStack =
                entity.getItemInHand(
                        hand
                );


        EquipmentConduitState heldState =
                evaluateItem(
                        heldStack
                );


        /*
         * ARMOR_SET capability from a held item is deliberately
         * ignored.
         *
         * Holding an armor piece in your hand must not grant armor
         * conduit strength.
         *
         * All other conduit types are valid held-item capabilities.
         */

        for (EquipmentConduitType conduit :
                EquipmentConduitType.values()) {

            if (conduit
                    == EquipmentConduitType.ARMOR_SET) {

                continue;
            }


            double strength =
                    heldState.strength(
                            conduit
                    );


            if (strength > 0.0) {

                strengths.merge(
                        conduit,
                        strength,
                        Math::max
                );
            }
        }


        return new EquipmentConduitState(
                strengths
        );
    }


    /*
     * ============================================================
     * ITEM RESOLUTION
     * ============================================================
     *
     * This is the preferred public ItemStack resolution API.
     *
     * Gameplay code should not inspect EquipmentConduitItem
     * directly.
     */

    public static EquipmentConduitState evaluateItem(
            ItemStack stack
    ) {

        return EquipmentConduitRegistry.evaluate(
                stack
        );
    }


    public static boolean isConduit(
            ItemStack stack,
            EquipmentConduitType conduit
    ) {

        Objects.requireNonNull(
                conduit,
                "Conduit cannot be null"
        );


        return evaluateItem(
                stack
        ).isActive(
                conduit
        );
    }


    /*
     * Compatibility helper for code that still expects one conduit.
     *
     * New gameplay code should use evaluateItem() because an item
     * may eventually provide multiple conduits.
     *
     * If several conduits exist, this returns the strongest one.
     */

    @Deprecated
    public static Optional<EquipmentConduitType> conduitFor(
            ItemStack stack
    ) {

        EquipmentConduitState state =
                evaluateItem(
                        stack
                );


        EquipmentConduitType strongest =
                null;

        double strongestStrength =
                0.0;


        for (EquipmentConduitType conduit :
                EquipmentConduitType.values()) {

            double strength =
                    state.strength(
                            conduit
                    );


            if (strength > strongestStrength) {

                strongest =
                        conduit;

                strongestStrength =
                        strength;
            }
        }


        return Optional.ofNullable(
                strongest
        );
    }


    /*
     * ============================================================
     * ARMOR CONDUIT
     * ============================================================
     */

    public static double armorStrength(
            LivingEntity entity
    ) {

        Objects.requireNonNull(
                entity,
                "Entity cannot be null"
        );


        double strength =
                0.0;


        for (EquipmentSlot slot :
                ARMOR_SLOTS) {

            strength +=
                    armorContribution(
                            entity,
                            slot
                    );
        }


        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        strength
                )
        );
    }


    /*
     * Returns this armor slot's final ARMOR_SET contribution.
     *
     * Example:
     *
     * Ascendance chestplate:
     *
     * provider strength = 1.0
     * chest slot weight = 0.40
     *
     * final contribution = 0.40
     *
     *
     * Future partially-attuned chestplate:
     *
     * provider strength = 0.50
     * chest slot weight = 0.40
     *
     * final contribution = 0.20
     */

    public static double armorContribution(
            LivingEntity entity,
            EquipmentSlot slot
    ) {

        Objects.requireNonNull(
                entity,
                "Entity cannot be null"
        );

        Objects.requireNonNull(
                slot,
                "Equipment slot cannot be null"
        );


        double slotWeight =
                ArmorConduitWeights.weightFor(
                        slot
                );


        if (slotWeight <= 0.0) {

            return 0.0;
        }


        ItemStack stack =
                entity.getItemBySlot(
                        slot
                );


        if (stack.isEmpty()) {

            return 0.0;
        }


        /*
         * Preserve strict slot validation for our own native armor.
         *
         * Commands can force armor pieces into incorrect slots.
         */

        if (stack.getItem()
                instanceof AscendanceArmorItem armorItem
                && armorItem.ascendanceSlot()
                != slot) {

            return 0.0;
        }


        EquipmentConduitState itemState =
                evaluateItem(
                        stack
                );


        double itemArmorStrength =
                itemState.strength(
                        EquipmentConduitType.ARMOR_SET
                );


        return slotWeight
                * itemArmorStrength;
    }


    public static boolean hasArmorConduit(
            LivingEntity entity,
            EquipmentSlot slot
    ) {

        return armorContribution(
                entity,
                slot
        ) > 0.0;
    }


    /*
     * Retained specifically for native-item diagnostics and any
     * existing callers.
     *
     * This does NOT include future enchanted/tagged third-party
     * armor.
     */

    public static boolean hasValidAscendanceArmor(
            LivingEntity entity,
            EquipmentSlot slot
    ) {

        Objects.requireNonNull(
                entity,
                "Entity cannot be null"
        );

        Objects.requireNonNull(
                slot,
                "Equipment slot cannot be null"
        );


        ItemStack stack =
                entity.getItemBySlot(
                        slot
                );


        if (!(stack.getItem()
                instanceof AscendanceArmorItem armorItem)) {

            return false;
        }


        return armorItem.ascendanceSlot()
                == slot;
    }


    public static List<EquipmentSlot> armorSlots() {

        return ARMOR_SLOTS;
    }
}
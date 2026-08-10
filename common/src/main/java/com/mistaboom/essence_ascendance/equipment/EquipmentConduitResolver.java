package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * FULL EQUIPMENT STATE
     * ============================================================
     *
     * This becomes the central equipment -> conduit resolver.
     *
     * Issue 9.5 resolves ARMOR_SET.
     *
     * Issues 9.6 and 9.7 will extend this same service for weapon
     * and tool conduits rather than creating parallel systems.
     */

    public static EquipmentConduitState evaluate(
            LivingEntity entity
    ) {

        Objects.requireNonNull(
                entity,
                "Entity cannot be null"
        );


        Map<EquipmentConduitType, Double> strengths =
                new EnumMap<>(
                        EquipmentConduitType.class
                );


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


        return new EquipmentConduitState(
                strengths
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

            if (hasValidAscendanceArmor(
                    entity,
                    slot
            )) {

                strength +=
                        ArmorConduitWeights.weightFor(
                                slot
                        );
            }
        }


        /*
         * Defensive clamp.
         *
         * The configured built-in weights currently total exactly
         * 1.0, but conduit strength should never exceed its normal
         * normalized range.
         */

        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        strength
                )
        );
    }


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


        /*
         * Commands can force items into inappropriate slots.
         *
         * Only count an Ascendance piece when it occupies the slot
         * it was actually designed for.
         */

        return armorItem
                .ascendanceSlot()
                == slot;
    }


    public static List<EquipmentSlot> armorSlots() {

        return ARMOR_SLOTS;
    }
}
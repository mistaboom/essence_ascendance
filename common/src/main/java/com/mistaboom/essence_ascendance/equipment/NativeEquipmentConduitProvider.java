package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/*
 * Built-in provider for first-party Essence Ascendance equipment.
 *
 * Native held equipment declares its conduit through
 * EquipmentConduitItem.
 *
 * Native Ascendance armor declares ARMOR_SET capability here.
 *
 * Armor slot weighting is deliberately NOT performed here.
 * That belongs to EquipmentConduitResolver because slot weighting
 * depends on equipment context.
 */
public final class NativeEquipmentConduitProvider
        implements EquipmentConduitProvider {

    public static final NativeEquipmentConduitProvider INSTANCE =
            new NativeEquipmentConduitProvider();


    private NativeEquipmentConduitProvider() {
    }


    @Override
    public EquipmentConduitState evaluate(
            ItemStack stack
    ) {

        Objects.requireNonNull(
                stack,
                "Item stack cannot be null"
        );


        if (stack.isEmpty()) {

            return EquipmentConduitState.none();
        }


        Map<EquipmentConduitType, Double> strengths =
                new EnumMap<>(
                        EquipmentConduitType.class
                );


        /*
         * Weapons and future tools.
         */

        if (stack.getItem()
                instanceof EquipmentConduitItem conduitItem) {

            strengths.put(
                    conduitItem.conduitType(),
                    1.0
            );
        }


        /*
         * Armor eligibility.
         *
         * This means:
         *
         * "This item can act as ARMOR_SET equipment."
         *
         * It does NOT mean it contributes 100% armor strength.
         *
         * EquipmentConduitResolver later multiplies this value by
         * the appropriate armor slot weight.
         */

        if (stack.getItem()
                instanceof AscendanceArmorItem) {

            strengths.put(
                    EquipmentConduitType.ARMOR_SET,
                    1.0
            );
        }


        return new EquipmentConduitState(
                strengths
        );
    }
}
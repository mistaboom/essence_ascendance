package com.mistaboom.essence_ascendance.item;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;

import java.util.Objects;

public final class AscendanceArmorItem
        extends ArmorItem {

    private final EquipmentSlot ascendanceSlot;


    public AscendanceArmorItem(
            Holder<ArmorMaterial> material,
            Type type,
            EquipmentSlot ascendanceSlot,
            Item.Properties properties
    ) {

        super(
                material,
                type,
                properties
        );


        this.ascendanceSlot =
                Objects.requireNonNull(
                        ascendanceSlot,
                        "Ascendance armor slot cannot be null"
                );
    }


    public EquipmentSlot ascendanceSlot() {

        return ascendanceSlot;
    }
}
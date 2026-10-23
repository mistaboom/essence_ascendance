package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
//? if <26.1.2
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
//? if <26.1.2
import net.minecraft.world.item.ArmorItem;
//? if <26.1.2
import net.minecraft.world.item.ArmorMaterial;
//? if >=26.1.2
/*import net.minecraft.world.item.equipment.ArmorMaterial;*/
//? if >=26.1.2
/*import net.minecraft.world.item.equipment.ArmorType;*/
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class AscendanceArmorItem
        //? if >=26.1.2
        /*extends Item*/
        //? if <26.1.2
        extends ArmorItem
        implements EquipmentProfileItem {

    private final EquipmentSlot ascendanceSlot;

    public AscendanceArmorItem(
            //? if >=26.1.2
            /*ArmorMaterial material,*/
            //? if <26.1.2
            Holder<ArmorMaterial> material,
            //? if >=26.1.2
            /*ArmorType type,*/
            //? if <26.1.2
            Type type,
            EquipmentSlot ascendanceSlot,
            Item.Properties properties
    ) {
        //? if >=26.1.2
        /*super(properties);*/
        //? if <26.1.2
        super(material, type, properties);
        this.ascendanceSlot = Objects.requireNonNull(
                ascendanceSlot,
                "Ascendance armor slot cannot be null"
        );
    }

    public EquipmentSlot ascendanceSlot() {
        return ascendanceSlot;
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.ARMOR.id();
    }



}

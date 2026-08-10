package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class AscendanceItems {

    private static final int ARMOR_DURABILITY_FACTOR =
            37;


    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(
                    EssenceAscendance.MOD_ID,
                    Registries.ITEM
            );


    /*
     * ============================================================
     * ASCENDANCE ARMOR
     * ============================================================
     */

    public static final RegistrySupplier<AscendanceArmorItem>
            ASCENDANCE_HELMET =
            registerArmor(
                    "ascendance_helmet",
                    ArmorItem.Type.HELMET,
                    EquipmentSlot.HEAD
            );


    public static final RegistrySupplier<AscendanceArmorItem>
            ASCENDANCE_CHESTPLATE =
            registerArmor(
                    "ascendance_chestplate",
                    ArmorItem.Type.CHESTPLATE,
                    EquipmentSlot.CHEST
            );


    public static final RegistrySupplier<AscendanceArmorItem>
            ASCENDANCE_LEGGINGS =
            registerArmor(
                    "ascendance_leggings",
                    ArmorItem.Type.LEGGINGS,
                    EquipmentSlot.LEGS
            );


    public static final RegistrySupplier<AscendanceArmorItem>
            ASCENDANCE_BOOTS =
            registerArmor(
                    "ascendance_boots",
                    ArmorItem.Type.BOOTS,
                    EquipmentSlot.FEET
            );


    private AscendanceItems() {
    }


    private static RegistrySupplier<AscendanceArmorItem>
    registerArmor(
            String id,
            ArmorItem.Type type,
            EquipmentSlot slot
    ) {

        return ITEMS.register(
                id,
                () ->
                        new AscendanceArmorItem(
                                AscendanceArmorMaterials.ASCENDANCE,
                                type,
                                slot,
                                new Item.Properties()
                                        .durability(
                                                type.getDurability(
                                                        ARMOR_DURABILITY_FACTOR
                                                )
                                        )
                        )
        );
    }


    public static void init() {

        ITEMS.register();


        /*
         * Put development armor in the vanilla Combat tab.
         *
         * We can move this to an Essence Ascendance creative tab
         * later if the mod grows enough to justify one.
         */

        CreativeTabRegistry.append(
                CreativeModeTabs.COMBAT,

                ASCENDANCE_HELMET,
                ASCENDANCE_CHESTPLATE,
                ASCENDANCE_LEGGINGS,
                ASCENDANCE_BOOTS
        );
    }
}
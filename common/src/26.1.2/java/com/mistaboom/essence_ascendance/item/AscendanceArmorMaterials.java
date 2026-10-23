package com.mistaboom.essence_ascendance.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;

/** Zero static defense: the shared tier service remains the sole armor-balance authority. */
public final class AscendanceArmorMaterials {
    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("essence_ascendance", "ascendance"));

    public static final ArmorMaterial ASCENDANCE = new ArmorMaterial(37,
            Map.of(ArmorType.HELMET, 0, ArmorType.CHESTPLATE, 0,
                    ArmorType.LEGGINGS, 0, ArmorType.BOOTS, 0, ArmorType.BODY, 0),
            15, SoundEvents.ARMOR_EQUIP_NETHERITE, 0.0F, 0.0F,
            TagKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath("essence_ascendance", "no_vanilla_repair")),
            ASSET);

    private AscendanceArmorMaterials() { }
    public static void init() { ASCENDANCE.assetId(); }
}

package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Persistence boundary for completed Ascendance equipment tier. */
public final class EquipmentTierData {
    private static final String ROOT_TAG = "essence_ascendance_equipment";
    private static final String TIER_TAG = "tier";

    private EquipmentTierData() {}

    public static boolean isAscendanceEquipment(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof EquipmentProfileItem;
    }

    public static EquipmentTier tier(ItemStack stack) {
        if (!isAscendanceEquipment(stack)) return EquipmentTier.LATENT;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return EquipmentTier.LATENT;
        CompoundTag outer = customData.copyTag();
        if (!outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)) return EquipmentTier.LATENT;
        return EquipmentTier.fromSerializedName(outer.getCompound(ROOT_TAG).getString(TIER_TAG));
    }

    public static void setTier(ItemStack stack, EquipmentTier tier) {
        if (!isAscendanceEquipment(stack) || tier == null) {
            throw new IllegalArgumentException("Equipment tier may only be stored on Ascendance equipment");
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> {
            CompoundTag root = outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                    ? outer.getCompound(ROOT_TAG)
                    : new CompoundTag();
            root.putString(TIER_TAG, tier.serializedName());
            outer.put(ROOT_TAG, root);
        });
        EquipmentShieldService.refreshNativeDurability(stack);
    }

    public static EquipmentTier effectiveTier(ItemStack stack, EquipmentTier playerTier) {
        EquipmentTier itemTier = tier(stack);
        return itemTier.order() <= playerTier.order() ? itemTier : playerTier;
    }
}

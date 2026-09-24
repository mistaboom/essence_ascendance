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
        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> outer.merge(tierData(tier).copyTag()));
        EquipmentShieldService.refreshNativeDurability(stack);
    }

    /** Pure component encoding shared with detached item-model presentations. */
    public static CustomData tierData(EquipmentTier tier) {
        CompoundTag root = new CompoundTag();
        root.putString(TIER_TAG, java.util.Objects.requireNonNull(tier).serializedName());
        CompoundTag outer = new CompoundTag();
        outer.put(ROOT_TAG, root);
        return CustomData.of(outer);
    }

    public static EquipmentTier effectiveTier(ItemStack stack, EquipmentTier playerTier) {
        EquipmentTier itemTier = tier(stack);
        return itemTier.order() <= playerTier.order() ? itemTier : playerTier;
    }
}

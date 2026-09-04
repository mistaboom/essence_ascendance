package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Persistent Fractured-state data for Ascendance equipment. */
public final class FracturedEquipmentData {
    private static final String ROOT_TAG = "essence_ascendance_equipment";
    private static final String FRACTURED_TAG = "fractured";

    private FracturedEquipmentData() {}

    public static boolean isFractured(ItemStack stack) {
        if (!isAscendanceArtifact(stack)) {
            return false;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        CompoundTag outer = customData.copyTag();
        if (!outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return false;
        }

        return outer.getCompound(ROOT_TAG).getBoolean(FRACTURED_TAG);
    }

    public static void markFractured(ItemStack stack) {
        requireAscendanceArtifact(stack);

        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> {
            CompoundTag root = outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                    ? outer.getCompound(ROOT_TAG)
                    : new CompoundTag();
            root.putBoolean(FRACTURED_TAG, true);
            outer.put(ROOT_TAG, root);
        });
    }

    /** Reserved for the upcoming Infuser Repair mode. */
    public static void clearFractured(ItemStack stack) {
        requireAscendanceArtifact(stack);

        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> {
            if (!outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
                return;
            }

            CompoundTag root = outer.getCompound(ROOT_TAG);
            root.remove(FRACTURED_TAG);
            outer.put(ROOT_TAG, root);
        });
    }

    /*
     * Deliberately does not use ItemStack#isEmpty(). During vanilla's break
     * path the stack count has already reached zero, but the ItemStack still
     * retains its item/components long enough for us to restore the artifact.
     */
    public static boolean isAscendanceArtifact(ItemStack stack) {
        return stack != null && stack.getItem() instanceof EquipmentProfileItem;
    }

    private static void requireAscendanceArtifact(ItemStack stack) {
        if (!isAscendanceArtifact(stack)) {
            throw new IllegalArgumentException(
                    "Fractured state may only be stored on Ascendance equipment"
            );
        }
    }
}

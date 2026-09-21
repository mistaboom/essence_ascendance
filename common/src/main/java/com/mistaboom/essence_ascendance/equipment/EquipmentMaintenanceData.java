package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Persistent hidden item-local state shared by generic equipment-maintenance skills. */
public final class EquipmentMaintenanceData {
    private static final String ROOT_TAG = "essence_ascendance_maintenance";
    private static final String OVERDURABILITY_TAG = "overdurability";
    private static final String OVERDURABILITY_CAPACITY_TAG = "overdurability_capacity";

    private EquipmentMaintenanceData() { }

    /** Unpaid sub-point wear is item state, including while efficiency is absent. */
    public static double wearDebt(ItemStack stack) {
        CompoundTag data = root(stack);
        double value = data == null ? 0 : data.getDouble("wear_debt");
        return Double.isFinite(value) && value >= 0 && value < 1 ? value : 0;
    }

    public static int resolveWear(ItemStack stack, int requested, double efficiencyPercent) {
        if (requested <= 0 || !EquipmentMaintenanceService.eligible(stack)) return requested;
        double efficiency = Double.isFinite(efficiencyPercent) ? Math.clamp(efficiencyPercent, 0, 100) : 0;
        double raw = requested * (1 - efficiency / 100) + wearDebt(stack);
        int whole = (int) Math.floor(raw + 1e-9);
        double debt = Math.max(0, raw - whole);
        CompoundTag outer = outer(stack);
        CompoundTag data = outer.contains(ROOT_TAG, Tag.TAG_COMPOUND) ? outer.getCompound(ROOT_TAG) : new CompoundTag();
        if (debt > 0) data.putDouble("wear_debt", debt); else data.remove("wear_debt");
        finish(outer, data);
        if (outer.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(outer));
        return whole;
    }

    /** Paid capacity can shrink, but a new native capacity or rank never refills it. */
    public static double clampOverdurability(ItemStack stack, double maximumFraction) {
        double capacity = Math.min(overdurabilityCapacity(stack), Math.max(0, stack.getMaxDamage() * maximumFraction));
        double remaining = Math.min(overdurability(stack), capacity);
        if (remaining != overdurability(stack) || capacity != overdurabilityCapacity(stack))
            setOverdurability(stack, remaining, capacity);
        return capacity;
    }

    public static double overdurability(ItemStack stack) {
        CompoundTag root = root(stack);
        if (root == null) return 0;
        double value = root.getDouble(OVERDURABILITY_TAG);
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    public static double overdurabilityCapacity(ItemStack stack) {
        CompoundTag root = root(stack);
        if (root == null) return 0;
        double value = root.getDouble(OVERDURABILITY_CAPACITY_TAG);
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    /** Writes the current and reference capacity together so item-bar state can never observe a mismatched pair. */
    public static void setOverdurability(ItemStack stack, double value, double capacity) {
        requireDamageable(stack);
        double resolvedCapacity = Double.isFinite(capacity) ? Math.max(0, capacity) : 0;
        double resolvedValue = Double.isFinite(value) ? Math.clamp(value, 0, resolvedCapacity) : 0;
        if (resolvedValue <= 0 || resolvedCapacity <= 0) {
            clearOverdurability(stack);
            return;
        }

        CompoundTag outer = outer(stack);
        CompoundTag root = outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                ? outer.getCompound(ROOT_TAG) : new CompoundTag();
        root.putDouble(OVERDURABILITY_TAG, resolvedValue);
        root.putDouble(OVERDURABILITY_CAPACITY_TAG, resolvedCapacity);
        outer.put(ROOT_TAG, root);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(outer));
    }

    /**
     * Removes every Masterwork durability field. If our maintenance root was the item's only custom data, the
     * CUSTOM_DATA component itself is removed so a spent item is byte-for-byte back to its ordinary component set.
     */
    public static void clearOverdurability(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;

        CompoundTag outer = customData.copyTag();
        if (!outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)) return;
        CompoundTag root = outer.getCompound(ROOT_TAG);
        root.remove(OVERDURABILITY_TAG);
        root.remove(OVERDURABILITY_CAPACITY_TAG);
        finish(outer, root);

        if (outer.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(outer));
    }

    private static CompoundTag root(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag outer = customData.copyTag();
        return outer.contains(ROOT_TAG, Tag.TAG_COMPOUND) ? outer.getCompound(ROOT_TAG) : null;
    }

    private static CompoundTag outer(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? new CompoundTag() : customData.copyTag();
    }

    private static void finish(CompoundTag outer, CompoundTag root) {
        if (root.isEmpty()) outer.remove(ROOT_TAG); else outer.put(ROOT_TAG, root);
    }

    private static void requireDamageable(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getMaxDamage() <= 0) {
            throw new IllegalArgumentException("Maintenance state may only be stored on damageable items");
        }
    }
}

package com.mistaboom.essence_ascendance.pylon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** Persistent completed tier for the single evolving Essence Focus item. */
public final class EssenceFocusData {
    private static final String ROOT_TAG = "essence_ascendance_focus";
    private static final String TIER_TAG = "tier";

    private EssenceFocusData() {
    }

    public static boolean isFocusItem(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.is(EssencePylonContent.ESSENCE_FOCUS.get());
    }

    /**
     * Returns the completed upgrade tier. {@code null} means a Focus stack is
     * still Latent; use {@link #isFocusItem(ItemStack)} to distinguish it from
     * an empty or unrelated stack.
     */
    @Nullable
    public static EssenceFocusTier tier(ItemStack stack) {
        if (!isFocusItem(stack)) {
            return null;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag outer = customData.copyTag();
        if (!outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        String serialized = outer.getCompound(ROOT_TAG).getString(TIER_TAG);
        for (EssenceFocusTier tier : EssenceFocusTier.values()) {
            if (tier.serializedName().equals(serialized)) {
                return tier;
            }
        }
        return null;
    }

    public static String displayTier(ItemStack stack) {
        EssenceFocusTier tier = tier(stack);
        return tier == null ? "Latent" : tier.displayName();
    }

    public static void setTier(ItemStack stack, EssenceFocusTier tier) {
        if (!isFocusItem(stack) || tier == null) {
            throw new IllegalArgumentException("Essence Focus tier may only be stored on an Essence Focus");
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> {
            CompoundTag root = outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                    ? outer.getCompound(ROOT_TAG)
                    : new CompoundTag();
            root.putString(TIER_TAG, tier.serializedName());
            outer.put(ROOT_TAG, root);
        });
    }

    /** Returns the evolving Focus to its pre-Dormant Latent state. */
    public static void setLatent(ItemStack stack) {
        if (!isFocusItem(stack)) {
            throw new IllegalArgumentException("Latent state may only be stored on an Essence Focus");
        }
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                outer -> outer.remove(ROOT_TAG)
        );
    }
}

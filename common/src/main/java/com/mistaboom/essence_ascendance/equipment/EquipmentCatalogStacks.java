package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Creates unbound, zero-investment tier samples for catalog UIs. */
public final class EquipmentCatalogStacks {
    private EquipmentCatalogStacks() {
    }

    public static List<ItemStack> allTiers(Item item) {
        Objects.requireNonNull(item, "Catalog item cannot be null");
        if (!(item instanceof EquipmentProfileItem)) {
            throw new IllegalArgumentException("Only Ascendance equipment may expose tier catalog stacks");
        }

        List<ItemStack> variants = new ArrayList<>(EquipmentTier.values().length);
        for (EquipmentTier tier : EquipmentTier.values()) {
            ItemStack stack = new ItemStack(item);
            // The ordinary untagged crafted stack is already Latent. Preserve
            // that canonical form while giving later samples only tier data.
            if (tier != EquipmentTier.LATENT) {
                EquipmentTierData.setTier(stack, tier);
            }
            variants.add(stack);
        }
        return List.copyOf(variants);
    }

    public static List<ItemStack> allTiers(Iterable<? extends Item> items) {
        Objects.requireNonNull(items, "Catalog items cannot be null");
        List<ItemStack> variants = new ArrayList<>();
        for (Item item : items) {
            variants.addAll(allTiers(item));
        }
        return List.copyOf(variants);
    }
}

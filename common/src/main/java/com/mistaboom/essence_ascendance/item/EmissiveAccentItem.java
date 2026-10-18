package com.mistaboom.essence_ascendance.item;

/** Layered equipment materials: 0 = base, 1 = accent, other = untinted. */
public interface EmissiveAccentItem {
    default int materialTintIndex(int modelTintIndex) { return modelTintIndex; }

    static int materialTintIndex(net.minecraft.world.item.ItemStack stack, int modelTintIndex) {
        return stack.getItem() instanceof EmissiveAccentItem item
                ? item.materialTintIndex(modelTintIndex) : modelTintIndex;
    }
}

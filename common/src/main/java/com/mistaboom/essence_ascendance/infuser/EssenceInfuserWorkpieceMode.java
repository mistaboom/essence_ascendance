package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.world.item.ItemStack;

/**
 * Client/server presentation category for the item currently being worked by
 * the Infuser. Keeping this classification separate from screen rendering
 * lets future infusion recipe families add their own workpiece presentation
 * without turning the GUI into a carrier-specific special case.
 */
public enum EssenceInfuserWorkpieceMode {
    NONE,
    ESSENTIUM,
    FOCUS;

    public static EssenceInfuserWorkpieceMode forStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return NONE;
        }
        if (EssenceInfuserBlockEntity.isLatentCarrier(stack)) {
            return ESSENTIUM;
        }
        if (EssenceInfuserBlockEntity.isFocusWorkpiece(stack)) {
            return FOCUS;
        }
        return NONE;
    }
}

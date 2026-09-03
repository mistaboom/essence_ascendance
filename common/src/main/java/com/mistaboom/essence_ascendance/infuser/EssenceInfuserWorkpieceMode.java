package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.world.item.ItemStack;

/**
 * Client/server presentation category selected by the resolved Infuser recipe.
 * Future recipe families can add a presentation mode without moving workpiece
 * recognition back into the screen.
 */
public enum EssenceInfuserWorkpieceMode {
    NONE,
    ESSENTIUM,
    FOCUS;

    public static EssenceInfuserWorkpieceMode forStack(ItemStack stack) {
        return EssenceInfuserRecipeRegistry.modeFor(stack);
    }
}

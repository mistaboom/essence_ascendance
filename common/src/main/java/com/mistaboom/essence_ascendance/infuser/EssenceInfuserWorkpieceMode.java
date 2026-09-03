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
    FOCUS,
    EQUIPMENT;

    public static EssenceInfuserWorkpieceMode forStack(ItemStack stack) {
        return EssenceInfuserRecipeRegistry.modeFor(stack);
    }

    /**
     * Presentation context for the machine. The Matrix is a deliberate
     * secondary EQUIPMENT-mode anchor so removing the individualized equipment
     * workpiece does not make its component slot/interface disappear out from
     * under a still-loaded Matrix.
     */
    public static EssenceInfuserWorkpieceMode forContext(
            ItemStack workpiece,
            ItemStack component
    ) {
        EssenceInfuserWorkpieceMode mode = forStack(workpiece);
        if (mode != NONE) {
            return mode;
        }
        return component != null
                && !component.isEmpty()
                && component.is(EssenceInfuserContent.ASCENDANCE_MATRIX.get())
                ? EQUIPMENT
                : NONE;
    }
}

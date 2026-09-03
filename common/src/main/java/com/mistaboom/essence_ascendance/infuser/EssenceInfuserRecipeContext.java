package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.pylon.EssencePylonFocusTier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable machine state exposed to a resolved Infuser recipe.
 *
 * Recipe definitions receive only the state needed to describe requirements,
 * timing, and output. World mutation remains owned by the Infuser block entity.
 */
public record EssenceInfuserRecipeContext(
        ItemStack workpiece,
        @Nullable EssenceDefinition sourceEssence,
        @Nullable EssenceDefinition targetEssence,
        @Nullable EssencePylonFocusTier installedFocusTier,
        EssenceInfuserBalance.Profile profile
) {

    public EssenceInfuserRecipeContext {
        workpiece = workpiece == null ? ItemStack.EMPTY : workpiece;
        if (profile == null) {
            throw new IllegalArgumentException("Infuser recipe profile cannot be null");
        }
    }
}

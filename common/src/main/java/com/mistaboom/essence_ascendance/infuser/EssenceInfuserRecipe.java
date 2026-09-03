package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A resolved Infuser recipe.
 *
 * The registry resolves a workpiece into one of these descriptors. The recipe
 * describes requirements and output, while the block entity remains the sole
 * authority that withdraws Essence, mutates inventories, and completes work.
 */
public interface EssenceInfuserRecipe {

    ResourceLocation id();

    EssenceInfuserWorkpieceMode workpieceMode();

    EssenceInfuserProgressModel progressModel();

    default int inputCount() {
        return 1;
    }

    default int workpieceStackLimit(ItemStack workpiece) {
        return Math.max(1, workpiece.getMaxStackSize());
    }

    /** Whether hoppers/pipes may insert this recipe's workpiece. */
    default boolean allowsAutomationInput() {
        return false;
    }

    @Nullable
    default EssenceFocusTier requiredInstalledFocusTier() {
        return null;
    }

    default boolean installedFocusAllows(EssenceInfuserRecipeContext context) {
        EssenceFocusTier required = requiredInstalledFocusTier();
        EssenceFocusTier installed = context.installedFocusTier();
        return required == null
                || (installed != null && installed.ordinal() >= required.ordinal());
    }

    EssenceInfusionRequirements essenceRequirements(EssenceInfuserRecipeContext context);

    /**
     * Amount of infusion work represented by this recipe. TIMED_ATOMIC recipes
     * use this value with the installed Focus throughput to derive duration.
     * By default, exact Essence input is also the amount of infusion work.
     */
    default long infusionWork(EssenceInfuserRecipeContext context) {
        return essenceRequirements(context).totalRequired();
    }

    /**
     * Derived processing duration for TIMED_ATOMIC recipes. Special streamed
     * recipes may override this with zero because their progress lives elsewhere.
     */
    default int processingTicks(EssenceInfuserRecipeContext context) {
        return context.profile().processingTicksForWork(infusionWork(context));
    }

    /** Creates the output for the current validated context, or EMPTY if invalid. */
    ItemStack createOutput(EssenceInfuserRecipeContext context);
}

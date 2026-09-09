package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Dynamic 1 full Essentium Ingot -> 9 matching full Essentium Nuggets recipe. */
public final class EssentiumNuggetUncompactingRecipe extends CustomRecipe {

    public EssentiumNuggetUncompactingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return fullIngot(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        EssentiumCarrierData.Value value = fullIngot(input);
        if (value == null) {
            return ItemStack.EMPTY;
        }
        if (!(EssenceInfuserContent.ESSENTIUM_NUGGET.get() instanceof EssentiumItem nugget)) {
            return ItemStack.EMPTY;
        }

        ItemStack result = EssentiumCarrierData.createFull(
                nugget,
                value.essence(),
                value.grade()
        );
        if (!result.isEmpty()) {
            result.setCount(9);
        }
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EssenceInfuserContent.ESSENTIUM_NUGGET_UNCOMPACTING_SERIALIZER.get();
    }

    private static EssentiumCarrierData.Value fullIngot(CraftingInput input) {
        ItemStack found = ItemStack.EMPTY;

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!found.isEmpty() || !stack.is(EssenceInfuserContent.ESSENTIUM_INGOT.get())) {
                return null;
            }
            found = stack;
        }

        if (found.isEmpty()) {
            return null;
        }

        EssentiumCarrierData.Value value = EssentiumCarrierData.readValidated(found)
                .orElse(null);
        if (value == null
                || value.amount() != EssentiumCarrierData.capacityFor(found, value.grade())) {
            return null;
        }

        long nuggetCapacity = EssentiumCarrierData.capacityFor(
                (EssentiumItem) EssenceInfuserContent.ESSENTIUM_NUGGET.get(),
                value.grade()
        );
        try {
            return Math.multiplyExact(nuggetCapacity, 9L) == value.amount()
                    ? value
                    : null;
        } catch (ArithmeticException overflow) {
            return null;
        }
    }
}

package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Dynamic 9 Essentium Nuggets -> 1 Essentium Ingot recipe.
 *
 * All nine nuggets must be full and carry the same Essence/grade. The recipe
 * therefore preserves the data-bearing carrier identity instead of allowing a
 * normal static crafting recipe to erase or mix carrier data.
 */
public final class EssentiumNuggetCompactingRecipe extends CustomRecipe {

    public EssentiumNuggetCompactingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return commonFullNugget(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        EssentiumCarrierData.Value value = commonFullNugget(input);
        if (value == null) {
            return ItemStack.EMPTY;
        }
        if (!(EssenceInfuserContent.ESSENTIUM_INGOT.get() instanceof EssentiumItem ingot)) {
            return ItemStack.EMPTY;
        }
        return EssentiumCarrierData.createFull(
                ingot,
                value.essence(),
                value.grade()
        );
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 9;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EssenceInfuserContent.ESSENTIUM_NUGGET_COMPACTING_SERIALIZER.get();
    }

    private static EssentiumCarrierData.Value commonFullNugget(CraftingInput input) {
        EssentiumCarrierData.Value common = null;
        int occupied = 0;

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.is(EssenceInfuserContent.ESSENTIUM_NUGGET.get())) {
                return null;
            }

            EssentiumCarrierData.Value value = EssentiumCarrierData.readValidated(stack)
                    .orElse(null);
            if (value == null
                    || value.amount() != EssentiumCarrierData.capacityFor(stack, value.grade())) {
                return null;
            }

            if (common == null) {
                common = value;
            } else if (!sameCarrier(common, value)) {
                return null;
            }
            occupied++;
        }

        if (occupied != 9 || common == null) {
            return null;
        }

        long nuggetCapacity = EssentiumCarrierData.capacityFor(
                (EssentiumItem) EssenceInfuserContent.ESSENTIUM_NUGGET.get(),
                common.grade()
        );
        long ingotCapacity = EssentiumCarrierData.capacityFor(
                (EssentiumItem) EssenceInfuserContent.ESSENTIUM_INGOT.get(),
                common.grade()
        );
        try {
            return Math.multiplyExact(nuggetCapacity, 9L) == ingotCapacity
                    ? common
                    : null;
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    private static boolean sameCarrier(
            EssentiumCarrierData.Value left,
            EssentiumCarrierData.Value right
    ) {
        return left.essence().id().equals(right.essence().id())
                && left.grade() == right.grade()
                && left.amount() == right.amount();
    }
}

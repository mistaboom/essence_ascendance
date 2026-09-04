package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Dynamic 9 full matching Essentium Ingots -> 1 matching Essentium Block recipe. */
public final class EssentiumBlockCompactingRecipe extends CustomRecipe {

    public EssentiumBlockCompactingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return commonFullIngot(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        EssentiumCarrierData.Value value = commonFullIngot(input);
        if (value == null) {
            return ItemStack.EMPTY;
        }
        if (!(EssenceInfuserContent.ESSENTIUM_BLOCK.get() instanceof EssentiumItem block)) {
            return ItemStack.EMPTY;
        }

        return EssentiumCarrierData.createFull(
                block,
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
        return EssenceInfuserContent.ESSENTIUM_BLOCK_COMPACTING_SERIALIZER.get();
    }

    private static EssentiumCarrierData.Value commonFullIngot(CraftingInput input) {
        EssentiumCarrierData.Value common = null;
        int occupied = 0;

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.is(EssenceInfuserContent.ESSENTIUM_INGOT.get())) {
                return null;
            }

            EssentiumCarrierData.Value value = EssentiumCarrierData.readValidated(stack)
                    .filter(EssentiumCarrierData::isEnabled)
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

        long ingotCapacity = EssentiumCarrierData.capacityFor(
                (EssentiumItem) EssenceInfuserContent.ESSENTIUM_INGOT.get(),
                common.grade()
        );
        long blockCapacity = EssentiumCarrierData.capacityFor(
                (EssentiumItem) EssenceInfuserContent.ESSENTIUM_BLOCK.get(),
                common.grade()
        );
        try {
            return Math.multiplyExact(ingotCapacity, 9L) == blockCapacity
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

package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Full matching carriers exchange at their saved denomination, preserving exact face value. */
public final class EssentiumBlockUncompactingRecipe extends CustomRecipe {
    public EssentiumBlockUncompactingRecipe(CraftingBookCategory category) { super(category); }

    @Override
    public boolean matches(CraftingInput input, Level level) { return !convert(input).isEmpty(); }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return convert(input); }

    private static ItemStack convert(CraftingInput input) {
        return EssentiumCarrierCrafting.convert(input, EssenceInfuserContent.ESSENTIUM_BLOCK.get(),
                EssenceInfuserContent.ESSENTIUM_INGOT.get(), false);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return width * height >= 1; }

    @Override
    public RecipeSerializer<?> getSerializer() { return EssenceInfuserContent.ESSENTIUM_BLOCK_UNCOMPACTING_SERIALIZER.get(); }
}

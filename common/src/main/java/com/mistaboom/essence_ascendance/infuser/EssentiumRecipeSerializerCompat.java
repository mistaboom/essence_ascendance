package com.mistaboom.essence_ascendance.infuser;

//? if >=26.1.2
/*import com.mojang.serialization.MapCodec;*/
//? if >=26.1.2
/*import net.minecraft.network.codec.StreamCodec;*/
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
//? if <26.1.2
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

import java.util.function.Function;

/** The four stateless MISC conversions keep their exact matching and output code. */
final class EssentiumRecipeSerializerCompat {
    private EssentiumRecipeSerializerCompat() { }

    static <T extends CustomRecipe> RecipeSerializer<T> create(Function<CraftingBookCategory, T> factory) {
        //? if >=26.1.2
        /*return new RecipeSerializer<>(MapCodec.unit(factory.apply(CraftingBookCategory.MISC)), StreamCodec.of((buffer, recipe) -> { }, buffer -> factory.apply(CraftingBookCategory.MISC)));*/
        //? if <26.1.2
        return new SimpleCraftingRecipeSerializer<>(factory::apply);
    }
}

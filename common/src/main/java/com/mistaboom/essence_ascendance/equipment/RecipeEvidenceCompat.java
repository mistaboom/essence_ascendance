package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
//? if >=26.1.2
/*import net.minecraft.world.item.crafting.CraftingInput;*/
//? if >=26.1.2
/*import net.minecraft.world.item.crafting.ShapedRecipe;*/
//? if >=26.1.2
/*import net.minecraft.world.item.crafting.ShapelessRecipe;*/
//? if >=26.1.2
/*import net.minecraft.world.item.crafting.display.SlotDisplay;*/

import java.util.List;

/** Exact recipe evidence used by salvage and tempering across the two Minecraft contracts. */
public final class RecipeEvidenceCompat {
    private RecipeEvidenceCompat() { }

    /** Null means a displayed noncrafting result could not be resolved and must fail closed. */
    public static ItemStack output(Recipe<?> recipe, HolderLookup.Provider registries) {
        //? if >=26.1.2
        /*if (recipe instanceof ShapedRecipe shaped) return shaped.assemble(CraftingInput.EMPTY);*/
        //? if >=26.1.2
        /*if (recipe instanceof ShapelessRecipe shapeless) return shapeless.assemble(CraftingInput.EMPTY);*/
        //? if >=26.1.2
        /*ItemStack result = null;*/
        //? if >=26.1.2
        /*for (var display : recipe.display()) {*/
            //? if >=26.1.2
            /*SlotDisplay slot = display.result();*/
            //? if >=26.1.2
            /*ItemStack candidate = slot instanceof SlotDisplay.ItemStackSlotDisplay stack ? stack.stack().create() : slot instanceof SlotDisplay.ItemSlotDisplay item ? new ItemStack(item.item()) : null;*/
            //? if >=26.1.2
            /*if (candidate == null || result != null && (result.getCount() != candidate.getCount() || !ItemStack.isSameItemSameComponents(result, candidate))) return null;*/
            //? if >=26.1.2
            /*result = candidate;*/
        //? if >=26.1.2
        /*}*/
        //? if >=26.1.2
        /*return result == null ? ItemStack.EMPTY : result;*/
        //? if <26.1.2
        return recipe.getResultItem(registries);
    }

    public static List<Ingredient> ingredients(Recipe<?> recipe) {
        //? if >=26.1.2
        /*return recipe.placementInfo().ingredients();*/
        //? if <26.1.2
        return recipe.getIngredients();
    }

    public static boolean empty(Ingredient ingredient) {
        //? if >=26.1.2
        /*return ingredient == null || ingredient.isEmpty();*/
        //? if <26.1.2
        return ingredient == null || ingredient == Ingredient.EMPTY;
    }

    public static List<ItemStack> alternatives(Ingredient ingredient) {
        //? if >=26.1.2
        /*return ingredient.items().map(ItemStack::new).toList();*/
        //? if <26.1.2
        return java.util.Arrays.asList(ingredient.getItems());
    }

    public static boolean hasCraftingRemainder(Item item) {
        //? if >=26.1.2
        /*return item.getCraftingRemainder() != null;*/
        //? if <26.1.2
        return item.hasCraftingRemainingItem();
    }
}

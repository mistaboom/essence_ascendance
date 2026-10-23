package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven construction decomposition shared by salvage-style mechanics.
 * Only materials guaranteed consumed by every observed construction route can be recovered.
 * Ingredient alternatives and opaque recipe callbacks are not proof of a particular paid material.
 */
public final class ConstructionMaterialResolver {
    private static final Map<Item, List<MaterialAmount>> CACHE = new HashMap<>();
    private static RecipeManager observedRecipeManager;

    private ConstructionMaterialResolver() { }

    public static List<MaterialAmount> resolve(ItemStack target, MinecraftServer server) {
        if (!EquipmentMaintenanceService.eligible(target) || server == null) return List.of();
        refreshForReload(server);
        return CACHE.computeIfAbsent(target.getItem(), ignored -> resolveRecipes(target.getItem(),
                server.getRecipeManager().getRecipes(), server.registryAccess()));
    }

    public static void clearCache() {
        CACHE.clear();
        observedRecipeManager = null;
    }

    private static void refreshForReload(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        if (manager == observedRecipeManager) return;
        CACHE.clear();
        observedRecipeManager = manager;
    }

    /** Pure recipe evidence boundary, also exercised by the native item regression fixture. */
    public static List<MaterialAmount> resolveRecipes(Item target, java.util.Collection<RecipeHolder<?>> recipes,
                                                     net.minecraft.core.HolderLookup.Provider registries) {
        Map<Item, Double> guaranteed = null;
        for (RecipeHolder<?> holder : recipes) {
            Recipe<?> recipe = holder.value();
            ItemStack output;
            try {
                output = RecipeEvidenceCompat.output(recipe, registries);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (output == null) return List.of();
            if (output.isEmpty() || output.is(Items.AIR)
                    || output.getItem() != target) continue;
            boolean nativeCrafting = recipe.getClass() == ShapedRecipe.class || recipe.getClass() == ShapelessRecipe.class;
            if (nativeCrafting) {
                int consumedTarget = requiredTargetCount(recipe, target);
                if (consumedTarget >= output.getCount()) continue;
                if (consumedTarget > 0) return List.of();
            }
            // Generic getIngredients() is not a bill for machine/custom recipes: counts and
            // reusable catalysts may be hidden. A known alternative without a proven bill
            // prevents material recovery; it must not cause an expensive route to win.
            Map<Item, Double> bill = nativeCrafting ? recipeMaterials(recipe, target, output.getCount()) : Map.of();
            if (guaranteed == null) guaranteed = new LinkedHashMap<>(bill);
            else {
                for (Item material : java.util.List.copyOf(guaranteed.keySet())) {
                    double amount = Math.min(guaranteed.get(material), bill.getOrDefault(material, 0.0));
                    if (amount <= 0) guaranteed.remove(material); else guaranteed.put(material, amount);
                }
            }
            if (guaranteed.isEmpty()) return List.of();
        }
        // Repeatable construction must lose material; even first-party equipment uses its
        // loaded crafting bill, so added cheaper routes cannot bypass this proof.
        if (guaranteed == null || guaranteed.values().stream().mapToDouble(Double::doubleValue).sum() <= 1)
            return List.of();
        return guaranteed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())))
                .map(entry -> new MaterialAmount(entry.getKey(), entry.getValue())).toList();
    }

    private static Map<Item, Double> recipeMaterials(Recipe<?> recipe, Item target, int outputCount) {
        Map<Item, Double> amounts = new LinkedHashMap<>();
        double perOutput = 1.0D / Math.max(1, outputCount);
        for (Ingredient ingredient : RecipeEvidenceCompat.ingredients(recipe)) {
            Item candidate = guaranteedConsumedMaterial(ingredient);
            if (candidate != null && candidate != target) amounts.merge(candidate, perOutput, Double::sum);
        }
        return amounts;
    }

    private static Item guaranteedConsumedMaterial(Ingredient ingredient) {
        if (RecipeEvidenceCompat.empty(ingredient) || !completeItemAlternatives(ingredient)) return null;
        Item guaranteed = null;
        for (ItemStack stack : RecipeEvidenceCompat.alternatives(ingredient)) {
            if (stack == null || stack.isEmpty() || stack.is(Items.AIR)) continue;
            Item candidate = stack.getItem();
            // A returned container or reusable catalyst was not consumed as a recoverable unit.
            if (RecipeEvidenceCompat.hasCraftingRemainder(candidate) || !knownRemainderContract(candidate)
                    || (guaranteed != null && guaranteed != candidate)) return null;
            guaranteed = candidate;
        }
        return guaranteed;
    }

    /** A custom stack-sensitive remainder can return the consumed item for some NBT states.
     * Sampling its default stack cannot establish consumption for every accepted input. */
    private static boolean knownRemainderContract(Item item) {
        for (String method : List.of("getCraftingRemainingItem", "hasCraftingRemainingItem", "getRecipeRemainder")) {
            try {
                var baseline = Item.class.getMethod(method, ItemStack.class);
                if (item.getClass().getMethod(method, ItemStack.class).getDeclaringClass() != baseline.getDeclaringClass())
                    return false;
            } catch (NoSuchMethodException absentLoaderHook) {
                // A similarly named custom method is also unproved, even in a plain common fixture.
                try { item.getClass().getMethod(method, ItemStack.class); return false; }
                catch (NoSuchMethodException absent) { /* Vanilla has only the ordinary item remainder. */ }
            } catch (RuntimeException unreadable) { return false; }
        }
        return true;
    }

    /** Both loader APIs permit complex custom ingredients to expose display-only stacks.
     * Their simple/no-testing contract is the explicit exception: those item alternatives
     * must be complete. Unknown subclasses or unreadable contracts supply no paid bill. */
    private static boolean completeItemAlternatives(Ingredient ingredient) {
        try {
            Object custom = ingredient.getClass().getMethod("getCustomIngredient").invoke(ingredient);
            if (custom == null) return ingredient.getClass() == Ingredient.class;
            try {
                return Boolean.TRUE.equals(ingredient.getClass().getMethod("isSimple").invoke(ingredient));
            } catch (NoSuchMethodException fabric) {
                try {
                    return Boolean.FALSE.equals(ingredient.getClass().getMethod("requiresTesting").invoke(ingredient));
                } catch (NoSuchMethodException unknownContract) { return false; }
            }
        } catch (NoSuchMethodException vanilla) {
            return ingredient.getClass() == Ingredient.class;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private static int requiredTargetCount(Recipe<?> recipe, Item target) {
        int count = 0;
        for (Ingredient ingredient : RecipeEvidenceCompat.ingredients(recipe)) {
            if (RecipeEvidenceCompat.empty(ingredient) || !completeItemAlternatives(ingredient)) continue;
            boolean required = false;
            for (ItemStack candidate : RecipeEvidenceCompat.alternatives(ingredient)) {
                if (candidate == null || candidate.isEmpty()) continue;
                if (candidate.getItem() != target) { required = false; break; }
                required = true;
            }
            if (required) count++;
        }
        return count;
    }

    public record MaterialAmount(Item item, double amount) {
        public MaterialAmount {
            if (item == null || !Double.isFinite(amount) || amount <= 0.0D)
                throw new IllegalArgumentException("Construction material rewards require a positive finite amount");
        }
    }
}

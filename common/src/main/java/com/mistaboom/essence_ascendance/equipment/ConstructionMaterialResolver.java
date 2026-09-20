package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven construction decomposition shared by salvage-style mechanics.
 * The loaded crafting recipe is authoritative when present; native repair material is a conservative fallback.
 */
public final class ConstructionMaterialResolver {
    private static final Map<Item, List<MaterialAmount>> CACHE = new HashMap<>();
    private static RecipeManager observedRecipeManager;

    private ConstructionMaterialResolver() { }

    public static List<MaterialAmount> resolve(ItemStack target, MinecraftServer server) {
        if (!EquipmentMaintenanceService.eligible(target) || server == null) return List.of();
        refreshForReload(server);
        if (EquipmentTierData.isAscendanceEquipment(target)) {
            return List.of(new MaterialAmount(EssenceInfuserContent.LATENT_INGOT.get(), 1.0D));
        }
        return CACHE.computeIfAbsent(target.getItem(), ignored -> build(target, server));
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

    private static List<MaterialAmount> build(ItemStack target, MinecraftServer server) {
        Recipe<?> fallback = null;
        int fallbackOutputCount = 1;
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack output;
            try {
                output = recipe.getResultItem(server.registryAccess());
            } catch (RuntimeException ignored) {
                continue;
            }
            if (output == null || output.isEmpty() || output.is(Items.AIR)
                    || output.getItem() != target.getItem() || recipe.getIngredients().isEmpty()
                    || consumesTarget(recipe, target.getItem())) continue;
            if (recipe.getType() == RecipeType.CRAFTING) return recipeMaterials(target, recipe, output.getCount());
            if (fallback == null) {
                fallback = recipe;
                fallbackOutputCount = output.getCount();
            }
        }
        if (fallback != null) return recipeMaterials(target, fallback, fallbackOutputCount);

        Item repair = nativeRepairMaterial(target);
        return repair == null ? List.of() : List.of(new MaterialAmount(repair, 1.0D));
    }

    private static List<MaterialAmount> recipeMaterials(ItemStack target, Recipe<?> recipe, int outputCount) {
        Map<Item, Double> amounts = new LinkedHashMap<>();
        double perOutput = 1.0D / Math.max(1, outputCount);
        for (Ingredient ingredient : recipe.getIngredients()) {
            Item candidate = representative(target, ingredient);
            if (candidate != null) amounts.merge(candidate, perOutput, Double::sum);
        }
        return amounts.entrySet().stream()
                .map(entry -> new MaterialAmount(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** Prefer the target's factual native repair material, then use a stable alternative from the recipe ingredient. */
    private static Item representative(ItemStack target, Ingredient ingredient) {
        if (ingredient == null || ingredient == Ingredient.EMPTY) return null;
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (stack != null && !stack.isEmpty() && !stack.is(Items.AIR)) candidates.add(stack);
        }
        if (candidates.isEmpty()) return null;
        Comparator<ItemStack> byId = Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        return candidates.stream().filter(stack -> target.getItem().isValidRepairItem(target, stack))
                .min(byId).orElseGet(() -> candidates.stream().min(byId).orElseThrow()).getItem();
    }

    private static Item nativeRepairMaterial(ItemStack target) {
        Item best = null;
        String bestId = null;
        for (Item candidate : BuiltInRegistries.ITEM) {
            ItemStack stack = candidate.getDefaultInstance();
            if (stack.isEmpty() || !target.getItem().isValidRepairItem(target, stack)) continue;
            String id = BuiltInRegistries.ITEM.getKey(candidate).toString();
            if (best == null || id.compareTo(bestId) < 0) {
                best = candidate;
                bestId = id;
            }
        }
        return best;
    }

    private static boolean consumesTarget(Recipe<?> recipe, Item target) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient == Ingredient.EMPTY) continue;
            for (ItemStack candidate : ingredient.getItems()) {
                if (candidate != null && !candidate.isEmpty() && candidate.getItem() == target) return true;
            }
        }
        return false;
    }

    public record MaterialAmount(Item item, double amount) {
        public MaterialAmount {
            if (item == null || !Double.isFinite(amount) || amount <= 0.0D)
                throw new IllegalArgumentException("Construction material rewards require a positive finite amount");
        }
    }
}

package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves materials that can structurally reinforce a damageable item.
 *
 * <p>Resolution intentionally follows a layered policy rather than a vanilla-item table:</p>
 * <ol>
 *     <li>Ascendance equipment exposes its explicit Latent construction family without becoming anvil-repairable.</li>
 *     <li>Items with a native repair ingredient use that ingredient and conventional nugget/unit/block equivalents.</li>
 *     <li>Items with no native repair ingredient fall back to their loaded construction recipe, allowing equipment
 *     such as bows (stick/string) and modded recipe-defined gear to participate without item-specific code.</li>
 * </ol>
 *
 * <p>The recipe fallback rejects recipes that already consume the output item, so modification/decoration/repair
 * recipes cannot accidentally become construction-material definitions.</p>
 */
public final class RepairMaterialResolver {
    private static final Map<Item, RepairProfile> NATIVE_CACHE = new HashMap<>();
    private static final Map<Item, ConstructionProfile> CONSTRUCTION_CACHE = new HashMap<>();
    private static RecipeManager observedRecipeManager;

    private RepairMaterialResolver() { }

    public static Match resolve(ItemStack target, ItemStack ingredient, MinecraftServer server) {
        if (!EquipmentMaintenanceService.eligible(target) || ingredient == null || ingredient.isEmpty()) {
            return Match.INVALID;
        }

        refreshCachesForReload(server);

        // Ascendance equipment intentionally owns its construction family instead of inheriting vanilla repair
        // behavior. Do not fall through to recipes: that would make arbitrary Infuser ingredients tempering inputs.
        if (EquipmentTierData.isAscendanceEquipment(target)) {
            return resolveAscendanceConstructionMaterial(ingredient);
        }

        RepairProfile nativeProfile = NATIVE_CACHE.computeIfAbsent(target.getItem(), ignored -> buildNativeProfile(target));
        Match nativeFamily = matchFamilies(nativeProfile.baseForms(), ingredient);
        if (nativeFamily.valid()) return nativeFamily;

        if (target.getItem().isValidRepairItem(target, ingredient)) {
            return new Match(true, 1.0, "native");
        }

        // A real native repair definition is authoritative. Recipe ingredients such as a sword's stick handle
        // should not become tempering materials merely because they are part of the crafting recipe.
        if (nativeProfile.hasNativeRepairIngredient()) return Match.INVALID;

        ConstructionProfile construction = constructionProfile(target.getItem(), server);
        Double exactUnits = construction.exactUnits().get(ingredient.getItem());
        if (exactUnits != null && exactUnits > 0) {
            return new Match(true, exactUnits, "recipe:" + BuiltInRegistries.ITEM.getKey(ingredient.getItem()));
        }

        return matchFamilies(construction.baseForms(), ingredient);
    }

    public static void clearCache() {
        NATIVE_CACHE.clear();
        CONSTRUCTION_CACHE.clear();
        observedRecipeManager = null;
    }

    private static Match resolveAscendanceConstructionMaterial(ItemStack ingredient) {
        if (ingredient.is(EssenceInfuserContent.LATENT_NUGGET.get())) {
            return new Match(true, (double) Form.NUGGET.units / Form.UNIT.units, "latent");
        }
        if (ingredient.is(EssenceInfuserContent.LATENT_INGOT.get())) {
            return new Match(true, 1.0, "latent");
        }
        if (ingredient.is(EssenceInfuserContent.LATENT_BLOCK_ITEM.get())) {
            return new Match(true, (double) Form.BLOCK.units / Form.UNIT.units, "latent");
        }
        return Match.INVALID;
    }

    private static Match matchFamilies(Map<String, Form> baseForms, ItemStack ingredient) {
        Match best = Match.INVALID;
        for (MaterialTag actual : materialTags(ingredient).values()) {
            Form base = baseForms.get(actual.family());
            if (base == null) continue;
            double units = (double) actual.form().units / base.units;
            if (!best.valid() || units > best.materialUnits()) best = new Match(true, units, actual.family());
        }
        return best;
    }

    private static RepairProfile buildNativeProfile(ItemStack target) {
        Map<String, Form> families = new LinkedHashMap<>();
        boolean hasNative = false;
        for (Item candidate : BuiltInRegistries.ITEM) {
            ItemStack sample = candidate.getDefaultInstance();
            if (sample.isEmpty() || !target.getItem().isValidRepairItem(target, sample)) continue;
            hasNative = true;
            for (MaterialTag tag : materialTags(sample).values()) {
                families.merge(tag.family(), tag.form(), RepairMaterialResolver::preferUnitForm);
            }
        }
        return new RepairProfile(Map.copyOf(families), hasNative);
    }

    private static void refreshCachesForReload(MinecraftServer server) {
        if (server == null) return;
        RecipeManager manager = server.getRecipeManager();
        if (manager == observedRecipeManager) return;
        // Recipe/tag reloads arrive together. Native repair definitions often reference tags too, so refresh both
        // caches at the same boundary rather than keeping stale material-family observations.
        NATIVE_CACHE.clear();
        CONSTRUCTION_CACHE.clear();
        observedRecipeManager = manager;
    }

    private static ConstructionProfile constructionProfile(Item target, MinecraftServer server) {
        if (server == null) return ConstructionProfile.EMPTY;
        return CONSTRUCTION_CACHE.computeIfAbsent(target, ignored -> buildConstructionProfile(target, server));
    }

    private static ConstructionProfile buildConstructionProfile(Item target, MinecraftServer server) {
        Map<Item, Double> craftingExact = new LinkedHashMap<>();
        Map<String, Form> craftingFamilies = new LinkedHashMap<>();
        Map<Item, Double> fallbackExact = new LinkedHashMap<>();
        Map<String, Form> fallbackFamilies = new LinkedHashMap<>();

        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack output;
            try {
                output = recipe.getResultItem(server.registryAccess());
            } catch (RuntimeException ignored) {
                continue;
            }
            if (output == null || output.isEmpty() || output.is(Items.AIR) || output.getItem() != target) continue;
            if (recipe.getIngredients().isEmpty() || consumesTarget(recipe, target)) continue;

            boolean crafting = recipe.getType() == RecipeType.CRAFTING;
            Map<Item, Double> exact = crafting ? craftingExact : fallbackExact;
            Map<String, Form> families = crafting ? craftingFamilies : fallbackFamilies;
            addRecipeIngredients(recipe, exact, families, server);
        }

        // Prefer actual crafting definitions over transformations/custom recipes. If a modded item has no crafting
        // recipe at all, the loaded-recipe fallback still gives data-driven support rather than a hard-coded list.
        if (!craftingExact.isEmpty() || !craftingFamilies.isEmpty()) {
            return new ConstructionProfile(Map.copyOf(craftingExact), Map.copyOf(craftingFamilies));
        }
        if (!fallbackExact.isEmpty() || !fallbackFamilies.isEmpty()) {
            return new ConstructionProfile(Map.copyOf(fallbackExact), Map.copyOf(fallbackFamilies));
        }
        return ConstructionProfile.EMPTY;
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

    private static void addRecipeIngredients(Recipe<?> recipe, Map<Item, Double> exact,
                                             Map<String, Form> families, MinecraftServer server) {
        Map<Item, Double> direct = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient == Ingredient.EMPTY) continue;
            for (ItemStack candidate : ingredient.getItems()) {
                if (candidate == null || candidate.isEmpty() || candidate.is(Items.AIR)) continue;
                direct.putIfAbsent(candidate.getItem(), 1.0);
                exact.putIfAbsent(candidate.getItem(), 1.0);
                for (MaterialTag tag : materialTags(candidate).values()) {
                    families.merge(tag.family(), tag.form(), RepairMaterialResolver::preferUnitForm);
                }
            }
        }

        // One level of homogeneous material conversion lets recipe-only equipment resolve through simple base
        // components without hard-coding vanilla cases. Example: bow -> stick -> planks. Complex assembled
        // components are not flattened: every occupied ingredient slot in the source recipe must accept the same
        // alternatives, which keeps things such as tripwire hooks/templates from exploding into unrelated inputs.
        for (Item directItem : direct.keySet()) {
            ItemStack directStack = directItem.getDefaultInstance();
            if (!materialTags(directStack).isEmpty()) continue;
            addHomogeneousSourceMaterials(directItem, exact, families, server);
        }
    }

    private static void addHomogeneousSourceMaterials(Item component, Map<Item, Double> exact,
                                                       Map<String, Form> families, MinecraftServer server) {
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (recipe.getType() != RecipeType.CRAFTING || recipe.getIngredients().isEmpty()
                    || consumesTarget(recipe, component)) continue;

            ItemStack output;
            try {
                output = recipe.getResultItem(server.registryAccess());
            } catch (RuntimeException ignored) {
                continue;
            }
            if (output == null || output.isEmpty() || output.is(Items.AIR) || output.getItem() != component) continue;

            Map<Item, Boolean> accepted = null;
            int occupiedSlots = 0;
            boolean homogeneous = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient == null || ingredient == Ingredient.EMPTY) continue;
                Map<Item, Boolean> alternatives = new LinkedHashMap<>();
                for (ItemStack candidate : ingredient.getItems()) {
                    if (candidate != null && !candidate.isEmpty() && !candidate.is(Items.AIR)) {
                        alternatives.put(candidate.getItem(), Boolean.TRUE);
                    }
                }
                if (alternatives.isEmpty()) {
                    homogeneous = false;
                    break;
                }
                occupiedSlots++;
                if (accepted == null) accepted = alternatives;
                else if (!accepted.keySet().equals(alternatives.keySet())) {
                    homogeneous = false;
                    break;
                }
            }
            if (!homogeneous || accepted == null || occupiedSlots <= 0) continue;

            double units = (double) Math.max(1, output.getCount()) / occupiedSlots;
            if (!Double.isFinite(units) || units <= 0) continue;
            for (Item source : accepted.keySet()) {
                exact.merge(source, units, Math::max);
                ItemStack sourceStack = source.getDefaultInstance();
                for (MaterialTag tag : materialTags(sourceStack).values()) {
                    families.merge(tag.family(), tag.form(), RepairMaterialResolver::preferUnitForm);
                }
            }
        }
    }

    private static Form preferUnitForm(Form a, Form b) {
        return Math.abs(a.units - Form.UNIT.units) <= Math.abs(b.units - Form.UNIT.units) ? a : b;
    }

    private static Map<String, MaterialTag> materialTags(ItemStack stack) {
        Map<String, MaterialTag> result = new LinkedHashMap<>();
        stack.getTags().map(TagKey::location).forEach(id -> {
            MaterialTag tag = parse(id.getPath());
            if (tag != null) result.putIfAbsent(tag.family(), tag);
        });
        return result;
    }

    private static MaterialTag parse(String path) {
        if (path.startsWith("nuggets/")) return new MaterialTag(path.substring("nuggets/".length()), Form.NUGGET);
        if (path.startsWith("ingots/")) return new MaterialTag(path.substring("ingots/".length()), Form.UNIT);
        if (path.startsWith("gems/")) return new MaterialTag(path.substring("gems/".length()), Form.UNIT);
        if (path.startsWith("storage_blocks/")) return new MaterialTag(path.substring("storage_blocks/".length()), Form.BLOCK);
        return null;
    }

    public record Match(boolean valid, double materialUnits, String family) {
        private static final Match INVALID = new Match(false, 0, "");
    }

    private record RepairProfile(Map<String, Form> baseForms, boolean hasNativeRepairIngredient) { }

    private record ConstructionProfile(Map<Item, Double> exactUnits, Map<String, Form> baseForms) {
        private static final ConstructionProfile EMPTY = new ConstructionProfile(Map.of(), Map.of());
    }

    private record MaterialTag(String family, Form form) { }

    private enum Form {
        NUGGET(1), UNIT(9), BLOCK(81);
        final int units;
        Form(int units) { this.units = units; }
    }
}

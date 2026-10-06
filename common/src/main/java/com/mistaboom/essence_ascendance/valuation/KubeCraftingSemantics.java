package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonObject;
import net.minecraft.world.item.crafting.Recipe;
import java.util.List;
import java.util.Set;

/** Audited published crafting behavior. Reads configuration getters; never runs assembly or scripts. */
final class KubeCraftingSemantics {
    private static final String AUDITED_VERSION = "2101.7.2-build.374";
    private static final Set<String> CLASSES = Set.of(
            "dev.latvian.mods.kubejs.recipe.special.ShapedKubeJSRecipe",
            "dev.latvian.mods.kubejs.recipe.special.ShapelessKubeJSRecipe");
    record Observation(boolean audited, boolean nativeBehavior, int ingredientActions, boolean modifyResult) { }
    private KubeCraftingSemantics() { }

    static Observation inspect(Recipe<?> recipe, String version) {
        return inspect(recipe.getClass().getName(), version, recipe);
    }
    static void annotate(JsonObject definition, Observation behavior, String version) {
        if (behavior.audited()) {
            definition.addProperty("behavior_adapter", "kubejs:published_crafting_hooks");
            definition.addProperty("behavior_api_version", version);
            definition.addProperty("ingredient_action_count", behavior.ingredientActions());
            definition.addProperty("modify_result_present", behavior.modifyResult());
        }
        if (!behavior.nativeBehavior()) definition.addProperty("custom_behavior_unresolved", true);
    }
    /** Object seam keeps the optional library absent from core dependencies and permits protocol fixtures. */
    static Observation inspect(String runtimeClass, String version, Object published) {
        if (!CLASSES.contains(runtimeClass) || !AUDITED_VERSION.equals(version))
            return new Observation(false, false, -1, false);
        try {
            Object actions = published.getClass().getMethod("kjs$getIngredientActions").invoke(published);
            Object modify = published.getClass().getMethod("kjs$getModifyResult").invoke(published);
            if (!(actions instanceof List<?> list) || !(modify instanceof String callback))
                throw new IllegalStateException("Published KubeJS crafting behavior getters changed");
            return new Observation(true, list.isEmpty() && callback.isEmpty(), list.size(), !callback.isEmpty());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Audited KubeJS crafting behavior API changed", failure);
        }
    }
}

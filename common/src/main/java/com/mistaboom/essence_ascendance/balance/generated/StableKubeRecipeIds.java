package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Modifier;
import java.util.TreeMap;

/** Stabilizes only an audited optional mod's fallback auto-ID input, before its native hash runs. */
public final class StableKubeRecipeIds {
    private static final String KUBE_RECIPE = "dev.latvian.mods.kubejs.recipe.KubeRecipe";
    private static final String TYPE_FUNCTION = "dev.latvian.mods.kubejs.recipe.RecipeTypeFunction";
    private StableKubeRecipeIds() { }

    public static JsonElement fallbackInput(Object recipe, JsonElement json, String kubeVersion, String miVersion) {
        if (!supported(kubeVersion, miVersion)) return json;
        try {
            // The call site is reached only for a missing explicit ID and an absent
            // schema unique ID. Read the same serialization-type function as KubeJS;
            // a JSON property alone is not sufficient proof of recipe ownership.
            var method = recipe.getClass().getMethod("getSerializationTypeFunction");
            if (!method.getDeclaringClass().getName().equals(KUBE_RECIPE)
                    || !method.getReturnType().getName().equals(TYPE_FUNCTION)) return json;
            Object type = method.invoke(recipe);
            if (type == null || !type.getClass().getName().equals(TYPE_FUNCTION)) return json;
            var field = type.getClass().getField("id");
            if (field.getType() != ResourceLocation.class || !Modifier.isFinal(field.getModifiers())) return json;
            ResourceLocation id = (ResourceLocation) field.get(type);
            if (id == null) return json;
            return canonicalFallbackInput(json, id.getNamespace(), kubeVersion, miVersion);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            // Optional or changed contracts retain their native recipe ID generation.
            return json;
        }
    }

    static boolean supported(String kubeVersion, String miVersion) {
        return "2101.7.2-build.374".equals(kubeVersion) && "2.5.8".equals(miVersion);
    }

    static JsonElement canonicalFallbackInput(JsonElement json, String namespace, String kubeVersion, String miVersion) {
        if (!supported(kubeVersion, miVersion) || !"modern_industrialization".equals(namespace)) return json;
        return orderedObjects(json);
    }

    private static JsonElement orderedObjects(JsonElement json) {
        if (json.isJsonObject()) {
            var entries = new TreeMap<String, JsonElement>();
            json.getAsJsonObject().entrySet().forEach(entry -> entries.put(entry.getKey(), entry.getValue()));
            var result = new JsonObject();
            entries.forEach((key, value) -> result.add(key, orderedObjects(value)));
            return result;
        }
        if (json.isJsonArray()) {
            var result = new JsonArray();
            for (var value : json.getAsJsonArray()) result.add(orderedObjects(value));
            return result;
        }
        // Gson primitive/null values are immutable. Retain their original number
        // representation: no numeric round-trip, value coercion, or list sorting.
        return json;
    }
}

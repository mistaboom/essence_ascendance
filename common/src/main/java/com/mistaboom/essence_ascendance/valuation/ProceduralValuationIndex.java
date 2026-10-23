package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.*;

/** Recipe-only index: no world, loot, trade, quest or acquisition scan. */
final class ProceduralValuationIndex {
    private final Map<Item, List<RecipeModel>> byOutput;
    private final Summary summary;
    private final ProductionGraph production;
    private ProceduralValuationIndex(Map<Item, List<RecipeModel>> output, Summary summary, ProductionGraph production) {
        byOutput = freeze(output); this.summary = summary; this.production = production;
    }
    static ProceduralValuationIndex build(MinecraftServer server, GenerationDataSnapshot data) {
        Map<Item, List<RecipeModel>> output = new IdentityHashMap<>();
        var graph = data.production(); int skipped = 0, count = 0, links = 0;
        for (var process : graph.processes()) {
            ResourceLocation id = ResourceLocation.tryParse(process.family());
            RecipeType<?> type = id != null && BuiltInRegistries.RECIPE_TYPE.containsKey(id) ? BuiltInRegistries.RECIPE_TYPE.get(id) : null;
            var models = productionModels(process, type);
            if (models.isEmpty()) skipped++;
            for (var model : models) {
                output.computeIfAbsent(model.outputItem(), ignored -> new ArrayList<>()).add(model); count++;
                for (var ingredient : model.ingredients()) for (Item item : ingredient.alternatives()) {
                    links++;
                }
            }
        }
        return new ProceduralValuationIndex(output, new Summary(count, skipped, output.size(), links), graph);
    }
    ProductionGraph production() { return production; }
    List<RecipeModel> recipesProducing(Item item) { return byOutput.getOrDefault(item, List.of()); }
    Summary summary() { return summary; }
    static List<RecipeModel> productionModels(ProductionGraph.Process process, RecipeType<?> type) {
        List<IngredientChoice> inputs = new ArrayList<>();
        for (var input : process.inputs()) {
            List<Item> alternatives = input.alternatives().stream().map(ResourceLocation::tryParse).filter(Objects::nonNull)
                    .filter(BuiltInRegistries.ITEM::containsKey)
                    //? if >=26.1.2
                    /*.map(BuiltInRegistries.ITEM::getValue).filter(Objects::nonNull)*/
                    //? if <26.1.2
                    .map(BuiltInRegistries.ITEM::get)
                    .filter(item -> item != Items.AIR && ValuationGenerationInputs.itemAllowed(item)).toList();
            if (alternatives.size() != input.alternatives().size()) return List.of();
            inputs.add(new IngredientChoice(alternatives, input.count(), input.consumed()));
        }
        List<RecipeModel> result = new ArrayList<>();
        for (var output : process.outputs()) {
            var id = ResourceLocation.tryParse(output.itemId());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR || !ValuationGenerationInputs.itemAllowed(item)) continue;
            result.add(new RecipeModel(ResourceLocation.parse(process.id()), type, item,
                    (int) Math.min(Integer.MAX_VALUE, output.count()), inputs, process, output.expectedCount()));
        }
        return List.copyOf(result);
    }
    private static <T> Map<Item, List<T>> freeze(Map<Item, List<T>> values) {
        Map<Item, List<T>> copy = new IdentityHashMap<>(); values.forEach((key, value) -> copy.put(key, List.copyOf(value))); return Collections.unmodifiableMap(copy);
    }
    record RecipeModel(ResourceLocation id, RecipeType<?> type, Item outputItem, int outputCount,
                       List<IngredientChoice> ingredients, ProductionGraph.Process production, double expectedOutputCount) {
        RecipeModel { ingredients = List.copyOf(ingredients); }
    }
    record IngredientChoice(List<Item> alternatives, double count, boolean consumed) {
        IngredientChoice { alternatives = List.copyOf(alternatives); }
    }
    record Summary(int recipeCount, int skippedRecipeCount, int outputItemCount, int ingredientLinkCount) { }
}

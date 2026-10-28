package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import com.mistaboom.essence_ascendance.equipment.RecipeEvidenceCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Shared published recipe view. Custom behavior is an estimate, never a hard conservation claim. */
public final class EffectiveProduction {
    private EffectiveProduction(){}
    static ProductionGraph collect(GenerationDataSnapshot data,HolderLookup.Provider registries){
        return collect(data.recipes(),registries,data::unsupportedRecipe);
    }
    static ProductionGraph collect(List<RecipeHolder<?>> recipes,HolderLookup.Provider registries,
            java.util.function.BiConsumer<String,String> unsupported){
        var processes=new ArrayList<ProductionGraph.Process>();var warnings=new ArrayList<String>();
        // These caches live only for this published recipe snapshot. Large common tags
        // recur across recipes and shaped slots; keep their immutable facts once without
        // interning strings globally or retaining native ingredients after generation.
        Map<Item,String> itemIds=new IdentityHashMap<>();
        Map<Item,Boolean> customRemainders=new IdentityHashMap<>();
        Map<List<String>,ProductionGraph.Input> sharedInputs=new HashMap<>();
        int failed=0;
        for(var holder:recipes){
            BalancePerformance.increment("production_recipes_inspected");
            try{
                var recipe=holder.value();var output=RecipeEvidenceCompat.output(recipe,registries);
                if(output==null||output.isEmpty()){unsupported.accept("generic_recipe","No unambiguous static output");continue;}
                boolean complete=nativeStatic(recipe);
                var inputs=new ArrayList<ProductionGraph.Input>();var returns=new TreeMap<String,Double>();
                for(var ingredient:staticIngredients(recipe,registries)){
                    if(RecipeEvidenceCompat.empty(ingredient))continue;
                    var choices=RecipeEvidenceCompat.alternatives(ingredient);if(choices.isEmpty()){complete=false;continue;}
                    complete &= simpleIngredient(ingredient);
                    var alternatives=new TreeSet<String>();var remainders=new TreeSet<String>();
                    for(var stack:choices){
                        if(stack.isEmpty())continue;
                        alternatives.add(itemId(itemIds,stack.getItem()));
                        //? if >=26.1.2
                        /*var returned=recipe instanceof CraftingRecipe && stack.getItem().getCraftingRemainder()!=null ? stack.getItem().getCraftingRemainder().item().value() : null;*/
                        //? if <26.1.2
                        var returned=recipe instanceof CraftingRecipe?stack.getItem().getCraftingRemainingItem():null;
                        remainders.add(returned==null?"":itemId(itemIds,returned));
                        if(recipe instanceof CraftingRecipe)complete &= !customRemainders.computeIfAbsent(stack.getItem(),EffectiveProduction::customRemainder);
                    }
                    if(alternatives.isEmpty()){complete=false;continue;}
                    List<String> identities=List.copyOf(alternatives);
                    ProductionGraph.Input normalized=sharedInputs.get(identities);
                    if(normalized==null){
                        normalized=new ProductionGraph.Input(identities,1,true);
                        sharedInputs.put(normalized.alternatives(),normalized);
                    }
                    // Repeated slots remain separate entries, so their consumed counts add.
                    inputs.add(normalized);
                    if(remainders.size()==1&&!remainders.first().isEmpty())returns.merge(remainders.first(),1.0,Double::sum);
                    else if(remainders.size()>1)complete=false;
                }
                if(inputs.isEmpty()){unsupported.accept("generic_recipe","No static consumed item inputs");continue;}
                var outputs=new ArrayList<ProductionGraph.Output>();
                outputs.add(new ProductionGraph.Output(itemId(itemIds,output.getItem()),output.getCount(),1,false));
                returns.forEach((id,n)->outputs.add(new ProductionGraph.Output(id,n,1,true)));
                //? if >=26.1.2
                /*double ticks=recipe instanceof AbstractCookingRecipe cooking?cooking.cookingTime():0;*/
                //? if <26.1.2
                double ticks=recipe instanceof AbstractCookingRecipe cooking?cooking.getCookingTime():0;
                var metadata=new TreeMap<String,String>();
                metadata.put("adapter","shared_recipe_interfaces");metadata.put("conservation_complete",Boolean.toString(complete));
                metadata.put("acquisition_complete","false");metadata.put("duration_known",Boolean.toString(recipe instanceof AbstractCookingRecipe));
                metadata.put("value_evidence",complete?"static_material_recipe":"advisory_recipe_estimate");
                metadata.put("source_access","not_inferred");
                processes.add(new ProductionGraph.Process(
                        //? if >=26.1.2
                        /*holder.id().identifier().toString(),*/
                        //? if <26.1.2
                        holder.id().toString(),
                        BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString(),
                        inputs,outputs,ticks,0,"shared_recipe_interfaces",complete?.9:.4,metadata));
            }catch(RuntimeException|LinkageError failure){
                unsupported.accept("generic_recipe",failure.getClass().getSimpleName());
                if(failed++<8)warnings.add("Recipe "+holder.id()+" could not expose a static estimate: "+failure.getClass().getSimpleName());
            }
        }
        warnings.add("Only complete static material recipes constrain payouts. Other published recipe views are estimates; custom operations, source access, fuel and throughput are not inferred.");
        BalancePerformance.count("normalized_production_nodes",processes.size());
        BalancePerformance.count("normalized_production_item_identities",itemIds.size());
        BalancePerformance.count("normalized_production_input_choices",sharedInputs.size());
        return new ProductionGraph(processes,warnings);
    }
    private static String itemId(Map<Item,String> identities,Item item){
        return identities.computeIfAbsent(item,value->BuiltInRegistries.ITEM.getKey(value).toString());
    }
    private static boolean nativeStatic(Recipe<?> recipe){
        var type=recipe.getClass();
        return type==ShapedRecipe.class||type==ShapelessRecipe.class||type==SmeltingRecipe.class||type==BlastingRecipe.class
                ||type==SmokingRecipe.class||type==CampfireCookingRecipe.class||type==StonecutterRecipe.class||type==SmithingTransformRecipe.class;
    }
    private static List<Ingredient> staticIngredients(Recipe<?> recipe,HolderLookup.Provider registries){
        //? if >=26.1.2
        /*return RecipeEvidenceCompat.ingredients(recipe);*/
        //? if <26.1.2
        // Smithing's public generic getter is empty. Its native codec exposes the three exact inputs.
        //? if <26.1.2
        if(recipe.getClass()==SmithingTransformRecipe.class){
            //? if <26.1.2
            var ops=registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            //? if <26.1.2
            var json=RecipeSerializer.SMITHING_TRANSFORM.codec().codec().encodeStart(ops,(SmithingTransformRecipe)recipe)
                    //? if <26.1.2
                    .getOrThrow().getAsJsonObject();
            //? if <26.1.2
            var result=new ArrayList<Ingredient>(3);
            //? if <26.1.2
            for(String key:List.of("template","base","addition"))
                //? if <26.1.2
                result.add(Ingredient.CODEC_NONEMPTY.parse(ops,json.get(key)).getOrThrow());
            //? if <26.1.2
            return result;
        //? if <26.1.2
        }
        //? if <26.1.2
        return recipe.getIngredients();
    }
    private static boolean customRemainder(Item item){
        //? if >=26.1.2
        /*return false;*/
        //? if <26.1.2
        // Compare with the loaded platform's Item contract, including inherited loader defaults.
        //? if <26.1.2
        for(String name:List.of("getCraftingRemainingItem","hasCraftingRemainingItem"))try{
            //? if <26.1.2
            if(item.getClass().getMethod(name,ItemStack.class).getDeclaringClass()
                    //? if <26.1.2
                    !=Item.class.getMethod(name,ItemStack.class).getDeclaringClass())return true;
        //? if <26.1.2
        }catch(NoSuchMethodException absent){ /* The other loader exposes only static native properties. */ }
        //? if <26.1.2
        try{return item.getClass().getMethod("hasCraftingRemainingItem").getDeclaringClass()!=Item.class;}
        //? if <26.1.2
        catch(NoSuchMethodException absent){return true;}
    }
    private static boolean simpleIngredient(Ingredient ingredient){
        try{
            Object custom=ingredient.getClass().getMethod("getCustomIngredient").invoke(ingredient);
            if(custom==null)return ingredient.getClass()==Ingredient.class;
            try{return Boolean.TRUE.equals(ingredient.getClass().getMethod("isSimple").invoke(ingredient));}
            catch(NoSuchMethodException absent){return Boolean.FALSE.equals(ingredient.getClass().getMethod("requiresTesting").invoke(ingredient));}
        }catch(NoSuchMethodException absent){return ingredient.getClass()==Ingredient.class;}
        catch(ReflectiveOperationException failure){return false;}
    }
}

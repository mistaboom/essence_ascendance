package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Loaded standard recipe contracts; all namespaces use the same public interfaces. */
public final class EffectiveProductionTest {
    private static int checks;
    private static HolderLookup.Provider registries;
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((t,f)->f.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); registries=VanillaRegistries.createLookup();
        namespacesAndReplacement(); countsAndRemainders(); opaqueAndFailure();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("EffectiveProductionTest: "+checks+" generic recipe checks PASS");
    }
    private static RecipeHolder<?> shapeless(String id, Item output, int count, Ingredient... inputs) {
        return new RecipeHolder<>(ResourceLocation.parse(id),new ShapelessRecipe("",CraftingBookCategory.MISC,new ItemStack(output,count),NonNullList.of(Ingredient.EMPTY,inputs)));
    }
    private static ProductionGraph collect(RecipeHolder<?>... recipes) {
        return EffectiveProduction.collect(List.of(recipes),registries,(family,reason)->{});
    }
    private static void namespacesAndReplacement() {
        for(String namespace:List.of("minecraft","datapack","arbitrary_mod")) {
            var r=collect(shapeless(namespace+":recipe",Items.GOLD_INGOT,3,Ingredient.of(Items.STONE))).processes().getFirst();
            check(r.outputs().getFirst().count()==3&&r.inputs().getFirst().count()==1,"Native counts are independent of recipe namespace");
            check(r.conservationComplete()&&!r.acquisitionComplete(),"Static recipe proves transformation, never ingredient access");
        }
        var original=collect(shapeless("fixture:same",Items.GOLD_INGOT,2,Ingredient.of(Items.STONE)));
        var replacement=collect(shapeless("fixture:same",Items.DIAMOND,5,Ingredient.of(Items.STONE)));
        check(!original.equals(replacement)&&replacement.processes().getFirst().outputs().getFirst().itemId().equals("minecraft:diamond"),"Published same-ID replacement replaces the original result");
        check(collect().processes().isEmpty(),"Removed recipe cannot be resurrected from old files");
    }
    private static void countsAndRemainders() {
        var shaped=new RecipeHolder<>(ResourceLocation.parse("fixture:shaped"),new ShapedRecipe("",CraftingBookCategory.MISC,
                ShapedRecipePattern.of(Map.of('X',Ingredient.of(Items.COPPER_INGOT)),"XXX","XXX","XXX"),new ItemStack(Items.COPPER_BLOCK)));
        var packed=collect(shaped).processes().getFirst();
        check(packed.inputs().size()==9&&packed.outputs().getFirst().count()==1,"Repeated shaped slots retain nine material units");
        var returned=collect(shapeless("fixture:container",Items.BREAD,2,Ingredient.of(Items.MILK_BUCKET))).processes().getFirst();
        check(returned.conservationComplete(),"Loaded platform default remainder methods remain complete");
        check(returned.outputs().stream().anyMatch(x->x.itemId().equals("minecraft:bucket")&&x.count()==1&&x.byproduct()),"Crafting container remainder shares the output bill");
        var alternatives=collect(shapeless("fixture:alternatives",Items.BREAD,1,Ingredient.of(Items.STONE,Items.DIRT))).processes().getFirst();
        check(alternatives.inputs().getFirst().alternatives().equals(List.of("minecraft:dirt","minecraft:stone")),"Every ordinary alternative is retained deterministically");
        var cooking=new RecipeHolder<>(ResourceLocation.parse("fixture:cooking"),new SmeltingRecipe("",CookingBookCategory.MISC,Ingredient.of(Items.STONE),new ItemStack(Items.DIAMOND,2),.1F,200));
        var cooked=collect(cooking).processes().getFirst();
        check(cooked.conservationComplete()&&cooked.outputs().getFirst().count()==2&&cooked.duration()==200,"Native cooking count and duration retained without invented fuel value");
        var milkCooking=new RecipeHolder<>(ResourceLocation.parse("fixture:cook_container"),new SmeltingRecipe("",CookingBookCategory.MISC,Ingredient.of(Items.MILK_BUCKET),new ItemStack(Items.DIAMOND),.1F,200));
        check(collect(milkCooking).processes().getFirst().outputs().size()==1,"Cooking consumes its ingredient without inventing a crafting-container return");
        var stonecutting=new RecipeHolder<>(ResourceLocation.parse("fixture:cut"),new StonecutterRecipe("",Ingredient.of(Items.STONE),new ItemStack(Items.STONE_SLAB,2)));
        check(collect(stonecutting).processes().getFirst().outputs().getFirst().count()==2,"Stonecutting actual output count retained");
        var smithing=new RecipeHolder<>(ResourceLocation.parse("fixture:smith"),new SmithingTransformRecipe(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),Ingredient.of(Items.DIAMOND_SWORD),Ingredient.of(Items.NETHERITE_INGOT),new ItemStack(Items.NETHERITE_SWORD)));
        var smith=collect(smithing).processes().getFirst();
        check(smith.inputs().size()==3&&smith.conservationComplete(),"Native smithing template, base and addition all pay material cost");
    }
    private static void opaqueAndFailure() {
        var custom=new ShapelessRecipe("",CraftingBookCategory.MISC,new ItemStack(Items.GOLD_INGOT,4),NonNullList.of(Ingredient.EMPTY,Ingredient.of(Items.STONE))) { };
        var graph=collect(new RecipeHolder<>(ResourceLocation.parse("anything:custom"),custom));
        check(graph.processes().size()==1&&!graph.processes().getFirst().conservationComplete(),"Custom subclass is a nominal estimate, not a native behavior proof");
        Recipe<RecipeInput> empty=new Recipe<>() {
            public boolean matches(RecipeInput input,net.minecraft.world.level.Level world){throw new AssertionError("must not simulate recipe");}
            public ItemStack assemble(RecipeInput input,HolderLookup.Provider registry){throw new AssertionError("must not execute recipe");}
            public boolean canCraftInDimensions(int a,int b){return true;}
            public ItemStack getResultItem(HolderLookup.Provider registry){return new ItemStack(Items.DIAMOND);}
            public RecipeSerializer<?> getSerializer(){return RecipeSerializer.SHAPELESS_RECIPE;}
            public RecipeType<?> getType(){return RecipeType.CRAFTING;}
        };
        check(collect(new RecipeHolder<>(ResourceLocation.parse("fixture:opaque"),empty)).processes().isEmpty(),"Opaque empty inputs cannot create a free-source constraint");
        var failure=new ShapelessRecipe("",CraftingBookCategory.MISC,new ItemStack(Items.GOLD_INGOT),NonNullList.of(Ingredient.EMPTY,Ingredient.of(Items.STONE))) {
            @Override public ItemStack getResultItem(HolderLookup.Provider registry){throw new IllegalStateException("unavailable getter");}
        };
        var safe=collect(new RecipeHolder<>(ResourceLocation.parse("fixture:failed"),failure),shapeless("fixture:valid",Items.BREAD,1,Ingredient.of(Items.WHEAT)));
        check(safe.processes().size()==1&&safe.warnings().stream().anyMatch(x->x.contains("failed")),"Failed nominal getter cannot suppress independent standard recipes");
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}

package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShulkerBoxColoring;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.WeatheringCopper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only acquisition relationships for standard interactions that do not
 * publish ordinary recipes. Adds edges to the SAME recipe graph, not a second
 * value system. No item prices, world changes, or optional mod dependencies.
 * Unknown/custom mechanics are left unresolved. Reflection is bounded to known
 * vanilla classes and field TYPES, so it does not depend on obfuscated names.
 */
final class ProceduralInteractionRecipes {
    private ProceduralInteractionRecipes() { }

    static List<ShadowValuationIndex.RecipeModel> discover(MinecraftServer server) {
        Map<ResourceLocation, ShadowValuationIndex.RecipeModel> result = new LinkedHashMap<>();
        readStrippingMap(result);
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof WeatheringCopper) {
                WeatheringCopper.getNext(block).ifPresent(next -> add(result, "weathering",
                        block.asItem(), next.asItem()));
            }
            if (block instanceof ConcretePowderBlock powder) {
                // 1.21.1 stores the hardened Block, not a BlockState.
                for (Field field : ConcretePowderBlock.class.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType() != Block.class) continue;
                    try {
                        if (field.trySetAccessible() && field.get(powder) instanceof Block concrete)
                            add(result, "water_hardening", powder.asItem(), concrete.asItem());
                    } catch (ReflectiveOperationException | RuntimeException exception) {
                        EssenceAscendance.LOGGER.debug("Valuation concrete relationship unavailable: {}", exception.toString());
                    }
                }
            }
        }
        discoverColoring(server, result);
        return result.values().stream().sorted(Comparator.comparing(model -> model.id().toString())).toList();
    }

    private static void readStrippingMap(Map<ResourceLocation, ShadowValuationIndex.RecipeModel> result) {
        for (Field field : AxeItem.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) continue;
            try {
                if (!field.trySetAccessible() || !(field.get(null) instanceof Map<?, ?> map)) continue;
                // Only accept the concrete Block -> Block relationship table.
                if (map.isEmpty() || map.entrySet().stream().anyMatch(entry ->
                        !(entry.getKey() instanceof Block) || !(entry.getValue() instanceof Block))) continue;
                for (Map.Entry<?, ?> entry : map.entrySet())
                    add(result, "axe_stripping", ((Block) entry.getKey()).asItem(), ((Block) entry.getValue()).asItem());
            } catch (ReflectiveOperationException | RuntimeException exception) {
                EssenceAscendance.LOGGER.debug("Valuation stripping relationship unavailable: {}", exception.toString());
            }
        }
    }

    private static void discoverColoring(MinecraftServer server,
                                         Map<ResourceLocation, ShadowValuationIndex.RecipeModel> result) {
        List<Item> boxes = BuiltInRegistries.ITEM.stream().filter(item -> item instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock).toList();
        List<Item> dyes = BuiltInRegistries.ITEM.stream().filter(item -> item instanceof DyeItem).toList();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            // Probe only the known stateless vanilla recipe implementation. Arbitrary
            // custom assemble() callbacks must NOT run for every possible pair.
            if (holder.value().getClass() != ShulkerBoxColoring.class) continue;
            CraftingRecipe recipe = (CraftingRecipe) holder.value();
            for (Item dye : dyes) {
                Map<Item, List<Item>> validInputs = new LinkedHashMap<>();
                for (Item box : boxes) {
                    try {
                        CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(box), new ItemStack(dye)));
                        if (!recipe.matches(input, server.overworld())) continue;
                        ItemStack output = recipe.assemble(input, server.registryAccess());
                        if (output.isEmpty() || output.getCount() != 1 || output.getItem() == box) continue;
                        validInputs.computeIfAbsent(output.getItem(), ignored -> new ArrayList<>()).add(box);
                    } catch (RuntimeException exception) {
                        EssenceAscendance.LOGGER.debug("Valuation special recipe {} skipped: {}", holder.id(), exception.toString());
                    }
                }
                for (Map.Entry<Item, List<Item>> entry : validInputs.entrySet()) {
                    ResourceLocation id = id("special_coloring", dye, entry.getKey());
                    result.put(id, new ShadowValuationIndex.RecipeModel(id, RecipeType.CRAFTING, entry.getKey(), 1,
                            List.of(new ShadowValuationIndex.IngredientChoice(entry.getValue()),
                                    new ShadowValuationIndex.IngredientChoice(List.of(dye)))));
                }
            }
        }
    }

    private static void add(Map<ResourceLocation, ShadowValuationIndex.RecipeModel> result,
                            String mechanic, Item from, Item to) {
        if (from == Items.AIR || to == Items.AIR || from == to) return;
        ResourceLocation id = id(mechanic, from, to);
        result.put(id, new ShadowValuationIndex.RecipeModel(id, RecipeType.CRAFTING, to, 1,
                List.of(new ShadowValuationIndex.IngredientChoice(List.of(from)))));
    }

    private static ResourceLocation id(String mechanic, Item from, Item to) {
        ResourceLocation a = BuiltInRegistries.ITEM.getKey(from), b = BuiltInRegistries.ITEM.getKey(to);
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID,
                "valuation/interaction/" + mechanic + "/" + a.getNamespace() + "/" + a.getPath()
                        + "/to/" + b.getNamespace() + "/" + b.getPath());
    }
}

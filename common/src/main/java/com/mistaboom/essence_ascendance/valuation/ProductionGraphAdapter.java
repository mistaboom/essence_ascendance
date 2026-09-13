package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Shares the already-collected recipe index, including runtime interactions. */
public final class ProductionGraphAdapter {
    // Vanilla's stock reset is a production constraint, not a guessed farm rate.
    private static final int VILLAGER_RESTOCKS_PER_DAY = 2;
    private ProductionGraphAdapter() { }

    public static ProductionGraph collect(MinecraftServer server) {
        ProceduralValuationIndex index = ProceduralValuationEngine.generationIndex(server);
        Map<String, ProductionGraph.Process> processes = new TreeMap<>();
        List<String> warnings = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            for (ProceduralValuationIndex.RecipeModel recipe : index.recipesProducing(item)) {
                String id = recipe.id().toString();
                if (processes.containsKey(id)) continue;
                String family = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.type()).toString();
                List<ProductionGraph.Input> inputs = new ArrayList<>();
                List<ProductionGraph.Output> outputs = new ArrayList<>();
                outputs.add(new ProductionGraph.Output(BuiltInRegistries.ITEM.getKey(recipe.outputItem()).toString(),
                        recipe.outputCount(), 1, false));
                boolean known = recipe.type() == RecipeType.CRAFTING || recipe.type() == RecipeType.SMITHING
                        || recipe.type() == RecipeType.STONECUTTING || recipe.type() == RecipeType.SMELTING
                        || recipe.type() == RecipeType.BLASTING || recipe.type() == RecipeType.SMOKING
                        || recipe.type() == RecipeType.CAMPFIRE_COOKING;
                for (ProceduralValuationIndex.IngredientChoice ingredient : recipe.ingredients()) {
                    boolean reusable = recipe.type() == RecipeType.CRAFTING && ingredient.alternatives().stream()
                            .allMatch(candidate -> candidate.getCraftingRemainingItem() == candidate);
                    inputs.add(new ProductionGraph.Input(ingredient.alternatives().stream()
                            .map(candidate -> BuiltInRegistries.ITEM.getKey(candidate).toString()).toList(), 1, !reusable));
                    if (recipe.type() == RecipeType.CRAFTING && !reusable) {
                        // Reserve each possible returned container conservatively.
                        // A typed adapter can describe conditional outputs exactly.
                        ingredient.alternatives().stream().map(Item::getCraftingRemainingItem)
                                .filter(java.util.Objects::nonNull).distinct()
                                .forEach(remainder -> outputs.add(new ProductionGraph.Output(
                                        BuiltInRegistries.ITEM.getKey(remainder).toString(), 1, 1, true)));
                    }
                }
                if (!known) warnings.add("Recipe family " + family
                        + " exposes only generic inputs/result; custom counts, catalysts, energy, and byproducts require a production provider.");
                processes.put(id, new ProductionGraph.Process(id, family, inputs, outputs, 0, 0,
                        "essence_ascendance:loaded_recipe", known ? .9 : .35, Map.of("time_energy", "unobserved")));
            }
            for (ProceduralTradeIndex.TradeSource trade : index.tradeSources(item)) {
                ProductionGraph.Process process = tradeProcess(trade);
                processes.put(process.id(), process);
            }
        }
        if (processes.values().stream().anyMatch(process -> process.family().equals("minecraft:trading")))
            warnings.add("Trade paths retain observed prices and sampled stock. Restocking villagers are bounded production sources; only cost A has a conservative discount floor. Wandering traders have finite lifetime stock and no assumed restock rate.");
        return new ProductionGraph(List.copyOf(processes.values()), warnings);
    }

    /** Preserve actual quantities separately from discount-safe material credit and stock production. */
    static ProductionGraph.Process tradeProcess(ProceduralTradeIndex.TradeSource trade) {
        List<ProductionGraph.Input> inputs = new ArrayList<>();
        for (net.minecraft.world.item.ItemStack cost : List.of(trade.costA(), trade.costB())) {
            if (!cost.isEmpty()) inputs.add(new ProductionGraph.Input(
                    List.of(BuiltInRegistries.ITEM.getKey(cost.getItem()).toString()), cost.getCount(), true));
        }
        Map<String, String> metadata = new TreeMap<>();
        metadata.put("trader", trade.traderId().toString());
        metadata.put("level", Integer.toString(trade.level()));
        metadata.put("observed_offer", trade.identityKey());
        metadata.put("listing_class", trade.listingClass());
        metadata.put("stock_uses", Integer.toString(trade.maxUses()));
        metadata.put("production_constraint", "finite_trade_stock");
        metadata.put("conservation_policy", "observed_prices_and_bounded_stock_production");
        if (trade.wandering()) {
            metadata.put("stock_kind", "finite_wandering_trader");
            metadata.put("stock_provenance", "sampled MerchantOffer maxUses; no restocking or replacement frequency assumed");
        } else {
            metadata.put("stock_kind", "restocking_villager");
            metadata.put("restocks_per_day", Integer.toString(VILLAGER_RESTOCKS_PER_DAY));
            metadata.put("restock_period_ticks", Integer.toString(SharedConstants.TICKS_PER_GAME_DAY));
            metadata.put("stock_provenance", "sampled MerchantOffer maxUses; vanilla Villager restock/day rule requires access to a workstation");
            if (!trade.costA().isEmpty()) {
                metadata.put("discount_cost_a_index", "0");
                metadata.put("discount_cost_a_minimum", "1");
                metadata.put("discount_provenance", "MerchantOffer clamps modified cost A to at least one; cost B is not discounted");
            }
        }
        return new ProductionGraph.Process("essence_ascendance:trade/" + digest(trade.identityKey()),
                "minecraft:trading", inputs, List.of(new ProductionGraph.Output(
                BuiltInRegistries.ITEM.getKey(trade.outputItem()).toString(), trade.outputCount(), 1, false)),
                0, 0, "essence_ascendance:loaded_trade", .6, metadata);
    }

    private static String digest(String identity) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

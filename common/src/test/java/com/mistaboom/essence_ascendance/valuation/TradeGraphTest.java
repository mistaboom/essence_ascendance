package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Optional;

/** Actual offer discounts and finite stock, plus the graph boundary used by generation. */
public final class TradeGraphTest {
    private static int checks;
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) ->
                failure.printStackTrace(new PrintStream(new FileOutputStream(FileDescriptor.err))));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var offer = new MerchantOffer(new ItemCost(Items.EMERALD, 20),
                Optional.of(new ItemCost(Items.DIAMOND, 8)), new ItemStack(Items.DIAMOND_CHESTPLATE), 12, 10, .05f);
        var source = source(offer, false);
        var graph = ProductionGraphAdapter.tradeProcess(source);
        require(graph.inputs().get(0).count() == 20 && graph.inputs().get(1).count() == 8,
                "Observed prices must survive graph collection for both cost slots");
        require(graph.metadata().get("stock_uses").equals("12"), "Sampled maximum uses remain authoritative");
        var moreStock = new ProceduralTradeIndex.TradeSource(source.outputItem(), source.outputCount(),
                source.costA(), source.costB(), source.traderId(), source.level(), source.wandering(),
                source.listingPoolSize(), source.maxUses() * 2, source.listingClass());
        require(!source.identityKey().equals(moreStock.identityKey()),
                "Equal prices with different stock capacities must not collapse by registration order");
        require(graph.metadata().get("discount_cost_a_index").equals("0"), "Only cost A gets a discount floor");
        offer.setSpecialPriceDiff(-1000);
        require(offer.getCostA().getCount() == 1, "Actual MerchantOffer enforces the declared cost-A floor");
        require(offer.getCostB().getCount() == 8, "Actual MerchantOffer never discounts cost B");
        for (int i = 0; i < offer.getMaxUses(); i++) {
            require(!offer.isOutOfStock(), "Trade available only while stock remains");
            offer.increaseUses();
        }
        require(offer.isOutOfStock(), "Actual offer refuses more trades after sampled capacity");
        offer.resetUses();
        require(!offer.isOutOfStock() && offer.getUses() == 0, "Restocking is the separate renewable service");
        var wandering = ProductionGraphAdapter.tradeProcess(source(offer, true));
        require(wandering.metadata().get("stock_kind").equals("finite_wandering_trader"), "Wandering stock is finite");
        require(!wandering.metadata().containsKey("restocks_per_day")
                && !wandering.metadata().containsKey("discount_cost_a_minimum"),
                "No invented wandering restock or villager reputation discount");
        var secondOnly = new ProceduralTradeIndex.TradeSource(Items.EMERALD, 2, ItemStack.EMPTY,
                new ItemStack(Items.IRON_INGOT, 4), ResourceLocation.parse("test:trader"), 1, false, 1, 3, "fixture");
        var secondGraph = ProductionGraphAdapter.tradeProcess(secondOnly);
        require(secondGraph.inputs().getFirst().count() == 4
                && !secondGraph.metadata().containsKey("discount_cost_a_index"),
                "Empty A must not accidentally label B as discountable");
        require(secondGraph.outputs().getFirst().count() == 2, "Multi-item trade results retain their count");
        try {
            new ProceduralTradeIndex.TradeSource(Items.EMERALD, 1, new ItemStack(Items.STONE), ItemStack.EMPTY,
                    ResourceLocation.parse("test:empty"), 1, false, 1, 0, "fixture");
            throw new AssertionError("Zero stock cannot become fictitious production");
        } catch (IllegalArgumentException expected) { checks++; }
        new PrintStream(new FileOutputStream(FileDescriptor.out)).println("TradeGraphTest: " + checks
                + " actual-price, discount-slot, finite-stock and graph checks passed");
    }
    private static ProceduralTradeIndex.TradeSource source(MerchantOffer offer, boolean wandering) {
        return new ProceduralTradeIndex.TradeSource(offer.getResult().getItem(), offer.getResult().getCount(),
                offer.getCostA(), offer.getCostB(), ResourceLocation.parse("test:sampled_trader"), 5,
                wandering, 1, offer.getMaxUses(), "actual MerchantOffer fixture");
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

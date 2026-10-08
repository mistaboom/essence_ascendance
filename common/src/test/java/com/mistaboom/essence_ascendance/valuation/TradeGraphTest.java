package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.entity.npc.VillagerTrades;

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
        exactOfferComponents();
        var worldDependent = new VillagerTrades.TreasureMapForEmeralds(12, null, "fixture.map", null, 1, 1) {
            @Override
            public MerchantOffer getOffer(net.minecraft.world.entity.Entity trader, net.minecraft.util.RandomSource random) {
                throw new AssertionError("Balance analysis invoked a world-dependent structure/map operation");
            }
        };
        var skipped = new java.util.TreeMap<String, Integer>();
        var skippedOutput = new java.util.HashMap<net.minecraft.world.item.Item, java.util.List<ProceduralTradeIndex.TradeSource>>();
        require(ProceduralTradeIndex.sampleListing(skippedOutput, worldDependent, null, ResourceLocation.parse("test:cartographer"),
                1, 1, false, 0, skipped) == 0, "World-dependent map listing must not be invoked or invent acquisition");
        require(skippedOutput.isEmpty() && skipped.values().stream().mapToInt(Integer::intValue).sum() == 1,
                "Skipped world-dependent listing must be diagnosed without manufacturing a resource source");
        var wrapped = new VillagerTrades.TypeSpecificTrade(java.util.Map.of(net.minecraft.world.entity.npc.VillagerType.PLAINS, worldDependent));
        require(ProceduralTradeIndex.sampleListing(skippedOutput, wrapped, null, ResourceLocation.parse("test:cartographer"),
                1, 1, false, 0, skipped) == 0 && skipped.values().stream().mapToInt(Integer::intValue).sum() == 2,
                "Vanilla trade-rebalance wrappers must not bypass the world-dependent listing guard");
        for (VillagerTrades.ItemListing moddedMap : java.util.List.of(
                new net.mehvahdjukaar.supplementaries.common.entities.trades.RandomAdventurerMapListing(),
                new net.mehvahdjukaar.supplementaries.common.entities.trades.StructureMapListing())) {
            require(ProceduralTradeIndex.sampleListing(skippedOutput, moddedMap, null,
                    ResourceLocation.parse("test:cartographer"), 1, 1, false, 0, skipped) == 0,
                    "Optional map factory must be skipped before any structure search or saved-map write");
            var wrappedModdedMap = new VillagerTrades.TypeSpecificTrade(java.util.Map.of(
                    net.minecraft.world.entity.npc.VillagerType.PLAINS, moddedMap));
            require(ProceduralTradeIndex.sampleListing(skippedOutput, wrappedModdedMap, null,
                    ResourceLocation.parse("test:cartographer"), 1, 1, false, 0, skipped) == 0,
                    "Wrapped optional map factory must also be skipped before invocation");
        }
        require(skippedOutput.isEmpty() && skipped.values().stream().mapToInt(Integer::intValue).sum() == 6,
                "Every excluded factory is diagnosed without inventing an acquisition source");
        int[] ordinaryCalls = {0};
        require(TradeSamplingScope.isolatedLootRandom(null) == null, "Normal loot retains native world RNG selection");
        var explicitRandom = net.minecraft.util.RandomSource.create(9);
        long sampled = TradeSamplingScope.sample(42, () -> {
            require(TradeSamplingScope.isolatedLootRandom(explicitRandom) == explicitRandom,
                    "A factory's explicit loot seed remains authoritative");
            return TradeSamplingScope.isolatedLootRandom(null).nextLong();
        });
        require(sampled == TradeSamplingScope.sample(42, () -> TradeSamplingScope.isolatedLootRandom(null).nextLong()),
                "Nested unseeded loot samples are repeatable without advancing a world RNG");
        try { TradeSamplingScope.sample(42, () -> { throw new IllegalArgumentException("fixture"); }); }
        catch (IllegalArgumentException expected) { }
        require(TradeSamplingScope.isolatedLootRandom(null) == null, "Failed optional trade reads cannot leak their RNG scope");
        require(TradeSamplingScope.entityRandom(() -> explicitRandom) == explicitRandom, "Gameplay entity RNG creation remains native");
        long entityRoll = TradeSamplingScope.sample(45, () -> TradeSamplingScope.entityRandom(() -> { throw new AssertionError(); }).nextLong());
        require(entityRoll == TradeSamplingScope.sample(45, () -> TradeSamplingScope.entityRandom(() -> { throw new AssertionError(); }).nextLong()),
                "Hypothetical constructor/reward randomness must reproduce its native sample");
        TradeSamplingScope.sample(45, () -> {
            long first = TradeSamplingScope.entityRandom(() -> { throw new AssertionError(); }).nextLong();
            TradeSamplingScope.sample(5, () -> TradeSamplingScope.entityRandom(() -> explicitRandom));
            long second = TradeSamplingScope.entityRandom(() -> { throw new AssertionError(); }).nextLong();
            require(first == entityRoll && first != second, "Nested sampling restores the independent constructor seed stream");
            return null;
        });
        VillagerTrades.ItemListing ordinaryModdedTrade = (trader, random) -> {
            ordinaryCalls[0]++;
            return new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.BREAD), 7, 1, .05f);
        };
        require(ProceduralTradeIndex.sampleListing(skippedOutput, ordinaryModdedTrade, null,
                ResourceLocation.parse("test:ordinary_modded_trader"), 1, 1, false, 0, skipped) == 1
                        && ordinaryCalls[0] == 4 && skippedOutput.containsKey(Items.BREAD),
                "Ordinary custom factories retain deterministic sampling and deduplication");
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
    private static void exactOfferComponents() {
        var first = new ItemStack(Items.ENCHANTED_BOOK);
        first.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("configuration A"));
        var second = first.copy(); second.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("configuration B"));
        int[] calls = {0};
        VillagerTrades.ItemListing listing = (trader, random) -> new MerchantOffer(
                new ItemCost(Items.EMERALD, 12).withComponents(b -> b.expect(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal("required input"))),
                Optional.of(new ItemCost(Items.BOOK)), calls[0]++ % 2 == 0 ? first : second, 8, 7, .2f);
        var output = new java.util.HashMap<net.minecraft.world.item.Item, java.util.List<ProceduralTradeIndex.TradeSource>>();
        require(ProceduralTradeIndex.sampleListing(output, listing, null, ResourceLocation.parse("test:librarian"),
                1, 1, false, 0, new java.util.TreeMap<>()) == 2, "Different output components must survive equal item/count/price deduplication");
        var sources = output.get(Items.ENCHANTED_BOOK);
        for (var source : sources) {
            var encoded = com.google.gson.JsonParser.parseString(source.offerDefinition());
            var restored = MerchantOffer.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();
            require(restored.getResult().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME), "Exact result component lost");
            require(!restored.satisfiedBy(new ItemStack(Items.EMERALD, 12), new ItemStack(Items.BOOK)), "Partial input component predicate discarded");
            require(restored.getXp() == 7 && restored.getMaxUses() == 8 && restored.getCostB().is(Items.BOOK), "Villager XP, stock or second input lost");
            var graph = ProductionGraphAdapter.tradeProcess(source);
            require(graph.metadata().get("observed_offer_definition").equals(source.offerDefinition())
                    && !graph.metadata().containsKey("access_proven"), "Retaining an offer must not certify trader/setup access");
        }
        require(!ProductionGraphAdapter.tradeProcess(sources.get(0)).id().equals(ProductionGraphAdapter.tradeProcess(sources.get(1)).id()),
                "Production snapshot collapsed distinct configured offers");
        first.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("mutated"));
        require(sources.stream().noneMatch(s -> s.offerDefinition().contains("mutated")), "Captured offer aliases mutable source stack");
        var invalid = new ItemStack(Items.IRON_PICKAXE); invalid.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, -1);
        var broken = new java.util.HashMap<net.minecraft.world.item.Item, java.util.List<ProceduralTradeIndex.TradeSource>>();
        require(ProceduralTradeIndex.sampleListing(broken, (trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD), invalid, 1, 1, .05f),
                null, ResourceLocation.parse("test:broken"), 1, 1, false, 0, new java.util.TreeMap<>()) == 1,
                "Failed component codec must retain the historical advisory item/price observation");
        var excluded = broken.get(Items.IRON_PICKAXE).getFirst();
        require(excluded.offerDefinition().isEmpty() && !excluded.offerFailure().isEmpty()
                && ProductionGraphAdapter.tradeProcess(excluded).metadata().containsKey("offer_configuration_unresolved"),
                "Failed exact offer must stay explicit and cannot establish configured acquisition");
        require(ProceduralTradeIndex.sampleListing(new java.util.HashMap<>(), (trader, random) -> { throw new NoClassDefFoundError("fixture optional incompatibility"); },
                null, ResourceLocation.parse("test:incompatible"), 1, 1, false, 0, new java.util.TreeMap<>()) == 0,
                "One incompatible offer factory must not abort unrelated trade evidence");
        for (var profession : VillagerTrades.TRADES.keySet()) {
            require(ProceduralTradeIndex.effectiveTrades(false).get(profession) == VillagerTrades.TRADES.get(profession), "Disabled trade rebalance used experimental offers");
            require(ProceduralTradeIndex.effectiveTrades(true).get(profession) == VillagerTrades.EXPERIMENTAL_TRADES.getOrDefault(profession, VillagerTrades.TRADES.get(profession)),
                    "Effective native trade-rebalance override/fallback differs from Villager.updateTrades");
        }
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

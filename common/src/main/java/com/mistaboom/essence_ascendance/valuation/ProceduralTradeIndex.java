package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader-neutral snapshot of the villager/wandering-trader offer factories that
 * are visible through vanilla's final trade tables at procedural-index build time.
 *
 * <p>Fabric's trade helper mutates these vanilla tables, so Fabric-added trades
 * are naturally visible here. NeoForge rebuilds trade lists during tag reload;
 * vanilla-visible results are still indexed, while loader-specific event-only
 * additions remain best-effort until a later optional adapter can feed them
 * into this same model. Nothing in this class is authoritative gameplay logic;
 * it only supplies additional procedural acquisition paths.</p>
 */
final class ProceduralTradeIndex {

    private static final int SAMPLES_PER_LISTING = 4;

    // Optional Supplementaries 1.21.1 / 3.9.9 map factories implement Moonlight's
    // ModItemListing, not vanilla TreasureMapForEmeralds. Their getOffer paths
    // locate structures and call MapItem.create/renderBiomePreviewMap. Keep
    // exact binary names here so neither optional mod is loaded or required.
    private static final java.util.Set<String> WORLD_DEPENDENT_MOD_LISTINGS = java.util.Set.of(
            "net.mehvahdjukaar.supplementaries.common.entities.trades.RandomAdventurerMapListing",
            "net.mehvahdjukaar.supplementaries.common.entities.trades.StructureMapListing");
    private static final String IE_REROLLING_LISTING = "blusunrize.immersiveengineering.common.world.Villages$RerollingItemListing";
    private static final String IE_GROUPED_LISTING = "blusunrize.immersiveengineering.common.world.Villages$GroupedListing";

    private final Map<Item, List<TradeSource>> sourcesByOutput;
    private final int professionTableCount;
    private final int listingCount;
    private final int offerCount;
    private final Map<String, Integer> excludedFactories;

    private ProceduralTradeIndex(
            Map<Item, List<TradeSource>> sourcesByOutput,
            int professionTableCount,
            int listingCount,
            int offerCount, Map<String, Integer> excludedFactories
    ) {
        Map<Item, List<TradeSource>> frozen = new IdentityHashMap<>();
        sourcesByOutput.forEach((item, sources) -> frozen.put(item, List.copyOf(sources)));
        this.sourcesByOutput = Map.copyOf(frozen);
        this.professionTableCount = professionTableCount;
        this.listingCount = listingCount;
        this.offerCount = offerCount;
        this.excludedFactories = Map.copyOf(excludedFactories);
    }

    static ProceduralTradeIndex build(MinecraftServer server) {
        Map<Item, List<TradeSource>> output = new IdentityHashMap<>();
        Map<String, Integer> worldDependentListings = new java.util.TreeMap<>();
        ServerLevel level = server.overworld();
        int professionTables = 0;
        int listings = 0;
        int offers = 0;

        try {
            Villager villager = new Villager(EntityType.VILLAGER, level, VillagerType.PLAINS);

            for (Map.Entry<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> professionEntry
                    : effectiveTrades(level.enabledFeatures().contains(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE)).entrySet()) {
                VillagerProfession profession = professionEntry.getKey();
                ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
                if (professionId == null) {
                    continue;
                }

                for (Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> levelEntry
                        : professionEntry.getValue().int2ObjectEntrySet()) {
                    int villagerLevel = levelEntry.getIntKey();
                    VillagerTrades.ItemListing[] factories = levelEntry.getValue();
                    if (factories == null || factories.length == 0) {
                        continue;
                    }

                    villager.setVillagerData(new VillagerData(
                            VillagerType.PLAINS,
                            profession,
                            Math.max(1, villagerLevel)
                    ));

                    int visibleFactoryCount = factories.length;
                    for (int listingIndex = 0; listingIndex < factories.length; listingIndex++) {
                        VillagerTrades.ItemListing factory = factories[listingIndex];
                        if (factory == null) {
                            continue;
                        }
                        listings++;
                        offers += sampleListing(
                                output,
                                factory,
                                villager,
                                professionId,
                                villagerLevel,
                                visibleFactoryCount,
                                false,
                                listingIndex,
                                worldDependentListings
                        );
                    }
                    professionTables++;
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            EssenceAscendance.LOGGER.debug(
                    "Procedural valuation could not fully index villager trades: {}",
                    exception.getMessage()
            );
        }

        try {
            WanderingTrader trader = new WanderingTrader(EntityType.WANDERING_TRADER, level);
            for (Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> levelEntry
                    : VillagerTrades.WANDERING_TRADER_TRADES.int2ObjectEntrySet()) {
                int tradeTier = Math.max(1, levelEntry.getIntKey());
                VillagerTrades.ItemListing[] factories = levelEntry.getValue();
                if (factories == null || factories.length == 0) {
                    continue;
                }
                int visibleFactoryCount = factories.length;
                for (int listingIndex = 0; listingIndex < factories.length; listingIndex++) {
                    VillagerTrades.ItemListing factory = factories[listingIndex];
                    if (factory == null) {
                        continue;
                    }
                    listings++;
                    offers += sampleListing(
                            output,
                            factory,
                            trader,
                            ResourceLocation.fromNamespaceAndPath("minecraft", "wandering_trader"),
                            tradeTier,
                            visibleFactoryCount,
                            true,
                            listingIndex,
                            worldDependentListings
                    );
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            EssenceAscendance.LOGGER.debug(
                    "Procedural valuation could not fully index wandering-trader offers: {}",
                    exception.getMessage()
            );
        }

        output.values().forEach(sources -> sources.sort(
                Comparator.comparing((TradeSource source) -> source.traderId().toString())
                        .thenComparingInt(TradeSource::level)
                        .thenComparing(source -> source.costA().getItem().toString())
                        .thenComparingInt(source -> source.costA().getCount())
                        .thenComparing(source -> source.costB().isEmpty() ? "" : source.costB().getItem().toString())
                        .thenComparingInt(source -> source.costB().getCount())
                        .thenComparingInt(TradeSource::maxUses)
        ));

        if (!worldDependentListings.isEmpty()) EssenceAscendance.LOGGER.info(
                "Procedural trade evidence excluded unsupported factories {}: no acquisition or absence inferred",
                worldDependentListings);
        EssenceAscendance.LOGGER.info(
                "Procedural trade index built: {} villager level tables, {} listing factories, {} sampled offer variants, {} output items",
                professionTables,
                listings,
                offers,
                output.size()
        );

        return new ProceduralTradeIndex(output, professionTables, listings, offers, worldDependentListings);
    }

    List<String> limitations() {
        return excludedFactories.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> "Trade factory evidence excluded (" + e.getValue() + " listings): " + e.getKey()
                        + "; access remains unproven, not absent; other recipe/loot evidence is retained").toList();
    }

    List<TradeSource> sources(Item item) {
        return sourcesByOutput.getOrDefault(item, List.of());
    }

    /** Native Villager.updateTrades uses a profession's experimental override when
     * enabled, with an ordinary-table fallback for professions without an override. */
    static Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> effectiveTrades(boolean rebalance) {
        var result = new LinkedHashMap<>(VillagerTrades.TRADES);
        if (rebalance) result.putAll(VillagerTrades.EXPERIMENTAL_TRADES);
        return result;
    }

    int professionTableCount() {
        return professionTableCount;
    }

    int listingCount() {
        return listingCount;
    }

    int offerCount() {
        return offerCount;
    }

    static int sampleListing(
            Map<Item, List<TradeSource>> output,
            VillagerTrades.ItemListing factory,
            Entity trader,
            ResourceLocation traderId,
            int level,
            int listingPoolSize,
            boolean wandering,
            int listingIndex,
            Map<String, Integer> worldDependentListings
    ) {
        Map<String, TradeSource> unique = new LinkedHashMap<>();
        java.util.Set<String> exclusions = new java.util.TreeSet<>();

        for (int sample = 0; sample < SAMPLES_PER_LISTING; sample++) {
            try {
                long seed = 0x5EED5EEDL
                        ^ ((long) listingIndex << 32)
                        ^ ((long) level << 20)
                        ^ (long) sample * 0x9E3779B97F4A7C15L;
                MerchantOffer offer = TradeSamplingScope.sample(seed, () -> {
                    RandomSource random = RandomSource.create(seed);
                    VillagerType type = trader instanceof net.minecraft.world.entity.npc.VillagerDataHolder holder
                            ? holder.getVillagerData().getType() : null;
                    ListingSelection selected = selectSampleListing(factory, type, random, 0);
                    if (selected.exclusion() != null) exclusions.add(selected.exclusion());
                    return selected.listing() == null ? null : selected.listing().getOffer(trader, random);
                });
                if (offer == null || offer.getMaxUses() <= 0) {
                    continue;
                }

                ItemStack result = offer.getResult();
                if (result == null || result.isEmpty() || result.is(Items.AIR)) {
                    continue;
                }

                ItemStack costA = offer.getCostA();
                ItemStack costB = offer.getCostB();
                if ((costA == null || costA.isEmpty()) && (costB == null || costB.isEmpty())) {
                    continue;
                }

                ItemStack safeA = costA == null ? ItemStack.EMPTY : costA.copy();
                ItemStack safeB = costB == null ? ItemStack.EMPTY : costB.copy();
                String definition = "", failure = "";
                try {
                    com.mojang.serialization.DynamicOps<com.google.gson.JsonElement> ops = trader == null
                            ? com.mojang.serialization.JsonOps.INSTANCE
                            : net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, trader.registryAccess());
                    definition = encodeOffer(offer, ops);
                } catch (RuntimeException | LinkageError unsupported) {
                    failure = unsupported.getClass().getSimpleName() + ": " + unsupported.getMessage();
                    if (failure.length() > 500) failure = failure.substring(0, 500);
                    com.mistaboom.essence_ascendance.balance.generated.BalancePerformance.increment("trade_configuration_read_failures");
                    // The observed item/price row remains advisory; this failure cannot prove configured access.
                }
                TradeSource source = new TradeSource(
                        result.getItem(),
                        Math.max(1, result.getCount()),
                        safeA,
                        safeB,
                        traderId,
                        Math.max(1, level),
                        wandering,
                        Math.max(1, listingPoolSize),
                        offer.getMaxUses(),
                        stableClass(factory.getClass()), definition, failure, seed,
                        trader instanceof Villager villager ? BuiltInRegistries.VILLAGER_TYPE.getKey(villager.getVillagerData().getType()).toString() : "not_villager"
                );
                unique.putIfAbsent(source.identityKey(), source);
            } catch (RuntimeException | LinkageError exception) {
                // Some custom listings intentionally reject a trader type or
                // require world state not available during a deterministic
                // probe. Skipping that one sample is safer than inventing it.
                com.mistaboom.essence_ascendance.balance.generated.BalancePerformance.increment("trade_offer_sample_failures");
                EssenceAscendance.LOGGER.debug("Trade sample excluded for {} seed index {}: {}", factory.getClass().getName(), sample, exception.toString());
            }
        }

        // Count affected registered listings once per reason, not once per roll.
        exclusions.forEach(reason -> worldDependentListings.merge(reason, 1, Integer::sum));
        for (TradeSource source : unique.values()) {
            output.computeIfAbsent(source.outputItem(), ignored -> new ArrayList<>())
                    .add(source);
        }
        return unique.size();
    }

    static String stableClass(Class<?> type) {
        return type.isHidden() ? type.getNestHost().getName() + "$$Lambda" : type.getName();
    }

    private static String uncontrolledRandomness(VillagerTrades.ItemListing listing) {
        String name = stableClass(listing.getClass());
        // Installed bytecode uses process-global Math.random or a static Random
        // behind arbitrary suppliers. Four observed rolls cannot certify their
        // price/output distribution. Do not run, reseed, freeze, or fabricate it.
        if (name.equals("cy.jdkdigital.productivefarming.event.EventHandler$$Lambda"))
            return name + ": Math.random output quantity; complete loaded distribution adapter unavailable";
        if (name.equals("com.hollingsworth.arsnouveau.common.event.EventHandler$$Lambda"))
            return name + ": static DungeonLootTables RNG and supplier outputs; complete loaded distribution adapter unavailable";
        return null;
    }

    static String encodeOffer(MerchantOffer offer, com.mojang.serialization.DynamicOps<com.google.gson.JsonElement> ops) {
        String encoded = com.mistaboom.essence_ascendance.balance.generated.BalanceDocument.canonical(
                MerchantOffer.CODEC.encodeStart(ops, offer).getOrThrow()).toString();
        if (encoded.length() > 65_536) throw new IllegalArgumentException("Configured offer exceeds bounded capture size");
        return encoded;
    }

    /** Select only the branch native wrappers would call, consuming exactly their RNG draws.
     * The original wrapper remains the source identity and pool entry; children are never
     * flattened into additional stock or claimed to be independently available. */
    static ListingSelection selectSampleListing(VillagerTrades.ItemListing listing, VillagerType type,
                                                 RandomSource random, int depth) {
        if (listing == null) return new ListingSelection(null, "Unresolved null trade factory");
        String name = stableClass(listing.getClass());
        if (depth > 16) return new ListingSelection(null, name + ": unresolved nested trade factory depth");
        if (listing instanceof VillagerTrades.TreasureMapForEmeralds || WORLD_DEPENDENT_MOD_LISTINGS.contains(name))
            return new ListingSelection(null, name + ": world-dependent map creation/structure search");
        // Installed IE 1.21.1/12.4.2 calls getOffers (which may generate other trades),
        // writes persistent randomized_offers, then invokes a registered callback.
        // Both orevein_map and revolver_piece use this stateful wrapper.
        if (IE_REROLLING_LISTING.equals(name)) return new ListingSelection(null,
                name + ": stateful offer regeneration/persistent trader data and registered callback");
        if (IE_GROUPED_LISTING.equals(name)) {
            VillagerTrades.ItemListing[] values;
            try {
                var children = listing.getClass().getDeclaredField("listings");
                if (!children.trySetAccessible()) return new ListingSelection(null, name + ": unreadable optional wrapper field listings");
                if (!(children.get(listing) instanceof VillagerTrades.ItemListing[] childrenArray) || childrenArray.length == 0)
                    return new ListingSelection(null, name + ": invalid optional wrapper field listings (null, empty or incompatible array)");
                values = childrenArray;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError changedContract) {
                return new ListingSelection(null, name + ": unreadable optional wrapper field listings ("
                        + changedContract.getClass().getSimpleName() + ")");
            }
            int index = random.nextInt(values.length);
            ListingSelection selected = selectSampleListing(values[index], type, random, depth + 1);
            return selected.withPath(name + " selected child " + index);
        }
        if (listing instanceof VillagerTrades.TypeSpecificTrade typed) {
            if (type == null) return new ListingSelection(null, name + ": villager type context unavailable");
            VillagerTrades.ItemListing child = typed.trades().get(type);
            // Native TypeSpecificTrade returns no offer for an absent biome entry.
            return child == null ? new ListingSelection(null, null)
                    : selectSampleListing(child, type, random, depth + 1).withPath(name + " selected villager type " + type);
        }
        String uncontrolled = uncontrolledRandomness(listing);
        return uncontrolled == null ? new ListingSelection(listing, null) : new ListingSelection(null, uncontrolled);
    }

    record ListingSelection(VillagerTrades.ItemListing listing, String exclusion) {
        ListingSelection withPath(String path) {
            return exclusion == null ? this : new ListingSelection(null, path + " -> " + exclusion);
        }
    }

    record TradeSource(
            Item outputItem,
            int outputCount,
            ItemStack costA,
            ItemStack costB,
            ResourceLocation traderId,
            int level,
            boolean wandering,
            int listingPoolSize,
            int maxUses,
            String listingClass,
            String offerDefinition,
            String offerFailure,
            long sampleSeed,
            String villagerType
    ) {
        TradeSource(Item outputItem, int outputCount, ItemStack costA, ItemStack costB, ResourceLocation traderId,
                    int level, boolean wandering, int listingPoolSize, int maxUses, String listingClass) {
            this(outputItem, outputCount, costA, costB, traderId, level, wandering, listingPoolSize, maxUses, listingClass,
                    "", "Exact offer not captured by item-only caller", 0, "unknown");
        }
        TradeSource {
            if (maxUses <= 0) throw new IllegalArgumentException("A sampled trade must have positive finite stock");
            costA = costA == null ? ItemStack.EMPTY : costA.copy();
            costB = costB == null ? ItemStack.EMPTY : costB.copy();
        }

        String identityKey() {
            return BuiltInRegistries.ITEM.getKey(outputItem)
                    + "|" + outputCount
                    + "|" + stackKey(costA)
                    + "|" + stackKey(costB)
                    + "|" + traderId
                    + "|" + level
                    + "|" + wandering
                    + "|stock=" + maxUses
                    + (offerDefinition.isEmpty() ? "" : "|configuration="
                        + com.mistaboom.essence_ascendance.balance.generated.BalanceDocument.hash(offerDefinition));
        }

        private static String stackKey(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return "empty";
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id + "x" + stack.getCount();
        }
    }
}

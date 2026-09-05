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
 * are visible through vanilla's final trade tables at shadow-index build time.
 *
 * <p>Fabric's trade helper mutates these vanilla tables, so Fabric-added trades
 * are naturally visible here. NeoForge rebuilds trade lists during tag reload;
 * vanilla-visible results are still indexed, while loader-specific event-only
 * additions remain best-effort until a later optional adapter can feed them
 * into this same model. Nothing in this class is authoritative gameplay logic;
 * it only supplies additional shadow acquisition paths.</p>
 */
final class ShadowTradeIndex {

    private static final int SAMPLES_PER_LISTING = 4;

    private final Map<Item, List<TradeSource>> sourcesByOutput;
    private final int professionTableCount;
    private final int listingCount;
    private final int offerCount;

    private ShadowTradeIndex(
            Map<Item, List<TradeSource>> sourcesByOutput,
            int professionTableCount,
            int listingCount,
            int offerCount
    ) {
        Map<Item, List<TradeSource>> frozen = new IdentityHashMap<>();
        sourcesByOutput.forEach((item, sources) -> frozen.put(item, List.copyOf(sources)));
        this.sourcesByOutput = Map.copyOf(frozen);
        this.professionTableCount = professionTableCount;
        this.listingCount = listingCount;
        this.offerCount = offerCount;
    }

    static ShadowTradeIndex build(MinecraftServer server) {
        Map<Item, List<TradeSource>> output = new IdentityHashMap<>();
        ServerLevel level = server.overworld();
        int professionTables = 0;
        int listings = 0;
        int offers = 0;

        try {
            Villager villager = new Villager(EntityType.VILLAGER, level, VillagerType.PLAINS);

            for (Map.Entry<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> professionEntry
                    : VillagerTrades.TRADES.entrySet()) {
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
                                listingIndex
                        );
                    }
                    professionTables++;
                }
            }
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.debug(
                    "Shadow valuation could not fully index villager trades: {}",
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
                            listingIndex
                    );
                }
            }
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.debug(
                    "Shadow valuation could not fully index wandering-trader offers: {}",
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
        ));

        EssenceAscendance.LOGGER.info(
                "Shadow trade index built: {} villager level tables, {} listing factories, {} sampled offer variants, {} output items",
                professionTables,
                listings,
                offers,
                output.size()
        );

        return new ShadowTradeIndex(output, professionTables, listings, offers);
    }

    List<TradeSource> sources(Item item) {
        return sourcesByOutput.getOrDefault(item, List.of());
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

    private static int sampleListing(
            Map<Item, List<TradeSource>> output,
            VillagerTrades.ItemListing factory,
            Entity trader,
            ResourceLocation traderId,
            int level,
            int listingPoolSize,
            boolean wandering,
            int listingIndex
    ) {
        Map<String, TradeSource> unique = new LinkedHashMap<>();

        for (int sample = 0; sample < SAMPLES_PER_LISTING; sample++) {
            try {
                long seed = 0x5EED5EEDL
                        ^ ((long) listingIndex << 32)
                        ^ ((long) level << 20)
                        ^ (long) sample * 0x9E3779B97F4A7C15L;
                MerchantOffer offer = factory.getOffer(trader, RandomSource.create(seed));
                if (offer == null) {
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
                TradeSource source = new TradeSource(
                        result.getItem(),
                        Math.max(1, result.getCount()),
                        safeA,
                        safeB,
                        traderId,
                        Math.max(1, level),
                        wandering,
                        Math.max(1, listingPoolSize),
                        Math.max(1, offer.getMaxUses()),
                        factory.getClass().getName()
                );
                unique.putIfAbsent(source.identityKey(), source);
            } catch (RuntimeException exception) {
                // Some custom listings intentionally reject a trader type or
                // require world state not available during a deterministic
                // probe. Skipping that one sample is safer than inventing it.
            }
        }

        for (TradeSource source : unique.values()) {
            output.computeIfAbsent(source.outputItem(), ignored -> new ArrayList<>())
                    .add(source);
        }
        return unique.size();
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
            String listingClass
    ) {
        TradeSource {
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
                    + "|" + wandering;
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

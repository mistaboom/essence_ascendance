package net.mehvahdjukaar.supplementaries.common.entities.trades;

/** Optional-mod linkage fixture: invoking this factory would mutate a world. */
public final class RandomAdventurerMapListing implements net.minecraft.world.entity.npc.VillagerTrades.ItemListing {
    @Override
    public net.minecraft.world.item.trading.MerchantOffer getOffer(
            net.minecraft.world.entity.Entity trader, net.minecraft.util.RandomSource random) {
        throw new AssertionError("Random map factory invoked during read-only balance analysis");
    }
}

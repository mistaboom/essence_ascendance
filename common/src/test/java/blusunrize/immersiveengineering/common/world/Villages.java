package blusunrize.immersiveengineering.common.world;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;

/** Binary-shape fixture only; optional IE is neither loaded nor required. */
public final class Villages {
    private Villages() { }
    public record RerollingItemListing(String functionKey) implements VillagerTrades.ItemListing {
        @Override public MerchantOffer getOffer(Entity trader, RandomSource random) {
            throw new AssertionError("World-dependent IE reroll callback was invoked during valuation");
        }
    }
    public static VillagerTrades.ItemListing group(VillagerTrades.ItemListing... listings) {
        return GroupedListing.of(listings);
    }
    private record GroupedListing(VillagerTrades.ItemListing[] listings) implements VillagerTrades.ItemListing {
        private GroupedListing(VillagerTrades.ItemListing[] listings) { this.listings = listings; }
        static GroupedListing of(VillagerTrades.ItemListing... listings) { return new GroupedListing(listings); }
        @Override public MerchantOffer getOffer(Entity trader, RandomSource random) {
            return listings[random.nextInt(listings.length)].getOffer(trader, random);
        }
    }
}

package com.mistaboom.essence_ascendance.utility;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import com.mistaboom.essence_ascendance.network.MicroVisualFeedback;
import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.Merchant;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

/** Shared villager pricing and local exhausted-trade recovery behavior. */
public final class VillagePatronService {
    private static final Map<Villager, RestockClock> RESTOCK_CLOCKS = new WeakHashMap<>();
    /** Offer identity is stable while its merchant menu is open; weak keys avoid extending its lifetime. */
    private static final Map<MerchantOffer, UUID> DISCOUNTED_OFFERS = new WeakHashMap<>();

    private VillagePatronService() { }

    /** Runs after vanilla reputation/Hero pricing so the skill composes with native price rules. */
    public static void applyTradeDiscount(Villager villager, Player customer) {
        if (!(customer instanceof ServerPlayer player) || !player.isAlive() || player.isRemoved()) return;
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        if (!context.isEffective(SkillIds.VILLAGE_PATRON)) return;
        double fraction = context.settings().utility().villagePatron().discountFraction();
        if (!Double.isFinite(fraction) || fraction <= 0) return;
        for (MerchantOffer offer : villager.getOffers()) {
            int displayed = offer.getCostA().getCount();
            if (displayed <= 1) continue;
            int discount = Math.min(displayed - 1, (int) Math.ceil(displayed * fraction));
            if (discount > 0) {
                offer.addToSpecialPriceDiff(-discount);
                DISCOUNTED_OFFERS.put(offer, player.getUUID());
            }
        }
        // Pricing is prepared before the trade screen is visible. Feedback is deferred until the
        // player actually completes one of the offers marked above.
    }

    public static void completedDiscountedTrade(Player customer, Merchant merchant, MerchantOffer offer) {
        if (!(customer instanceof ServerPlayer player) || !(merchant instanceof Villager villager)
                || offer == null || !player.getUUID().equals(DISCOUNTED_OFFERS.get(offer))) return;
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        if (!context.isEffective(SkillIds.VILLAGE_PATRON)) return;
        long seed = villager.getId() * 31L ^ player.level().getGameTime();
        MicroVisualFeedback.guiSlot(player, TransientVisualIds.GUI_TRADE_ACKNOWLEDGE, 2,
                SemanticVisualColor.UTILITY, seed);
        MicroVisualFeedback.utility(player.serverLevel(), villager.getBoundingBox().getCenter(), seed);
    }

    public static void tick(SkillEffectRuntime.Context context) {
        var tuning = context.settings().utility().villagePatron();
        if (tuning.restockRadiusBlocks() <= 0) {
            context.discardState(SkillIds.VILLAGE_PATRON);
            return;
        }
        ServerPlayer player = context.player();
        double radiusSquared = tuning.restockRadiusBlocks() * tuning.restockRadiusBlocks();
        List<Villager> villagers = new java.util.ArrayList<>();
        player.serverLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Villager.class),
                player.getBoundingBox().inflate(tuning.restockRadiusBlocks()),
                villager -> villager.isAlive() && !villager.isRemoved() && player.distanceToSqr(villager) <= radiusSquared,
                villagers, 256);

        int exhausted = 0;
        int refreshed = 0;
        int visualEvents = 0;
        long nextRestockAt = Long.MAX_VALUE;
        for (Villager villager : villagers) {
            if (!SharedTargetWork.visit(villager.level(), context.now())) break;
            var match = UtilityAuraService.strongest(player.serverLevel(), villager.position(), SkillIds.VILLAGE_PATRON,
                    c -> c.settings().utility().villagePatron().restockRadiusBlocks(),
                    c -> 1.0 / c.settings().utility().villagePatron().restockIntervalTicks());
            if (match == null || match.player() != player
                    || !SharedTargetWork.claim(villager.level(), villager, "restock", context.now(), 1)) continue;
            int exhaustedHere = exhaustedOffers(villager);
            if (exhaustedHere <= 0) {
                RESTOCK_CLOCKS.remove(villager);
                continue;
            }
            exhausted += exhaustedHere;
            RestockClock clock = RESTOCK_CLOCKS.get(villager);
            if (clock == null || context.now() < clock.lastObservedAt || context.now() - clock.lastObservedAt > 1) {
                clock = new RestockClock(context.now(), context.now());
                RESTOCK_CLOCKS.put(villager, clock);
            } else clock.lastObservedAt = context.now();
            long due = clock.startedAt + tuning.restockIntervalTicks();
            nextRestockAt = Math.min(nextRestockAt, due);
            if (villager.getTradingPlayer() != null || context.now() < due) continue;
            int refreshedHere = 0;
            for (MerchantOffer offer : villager.getOffers()) {
                if (!offer.isOutOfStock()) continue;
                offer.updateDemand();
                offer.resetUses();
                refreshed++;
                refreshedHere++;
            }
            if (refreshedHere > 0 && visualEvents++ < 4) {
                MicroVisualFeedback.utility(player.serverLevel(), villager.getBoundingBox().getCenter(),
                        villager.getId() * 31L ^ context.now());
            }
            RESTOCK_CLOCKS.remove(villager);
        }

        State state = context.state(SkillIds.VILLAGE_PATRON, State::new);
        state.nearbyVillagers = villagers.size();
        state.exhaustedOffers = exhausted;
        state.refreshedOffers = refreshed;
        state.nextRestockAt = nextRestockAt == Long.MAX_VALUE ? 0 : Math.max(context.now(), nextRestockAt);
        state.lastAt = context.now();
    }

    public static Snapshot snapshot(SkillEffectRuntime.Context context) {
        State state = context.existingState(SkillIds.VILLAGE_PATRON);
        if (state == null || state.lastAt != context.now()) return new Snapshot(0, 0, 0, 0);
        return new Snapshot(state.nearbyVillagers, state.exhaustedOffers, state.refreshedOffers, state.nextRestockAt);
    }

    private static int exhaustedOffers(Villager villager) {
        int exhausted = 0;
        for (MerchantOffer offer : villager.getOffers()) if (offer.isOutOfStock()) exhausted++;
        return exhausted;
    }

    public record Snapshot(int nearbyVillagers, int exhaustedOffers, int refreshedOffers, long nextRestockAt) { }

    private static final class RestockClock {
        final long startedAt;
        long lastObservedAt;
        RestockClock(long startedAt, long lastObservedAt) {
            this.startedAt = startedAt;
            this.lastObservedAt = lastObservedAt;
        }
    }

    private static final class State implements SkillEffectState {
        int nearbyVillagers;
        int exhaustedOffers;
        int refreshedOffers;
        long nextRestockAt;
        long lastAt = Long.MIN_VALUE;
        @Override public void clear() { nearbyVillagers = exhaustedOffers = refreshedOffers = 0; nextRestockAt = 0; lastAt = Long.MIN_VALUE; }
    }
}

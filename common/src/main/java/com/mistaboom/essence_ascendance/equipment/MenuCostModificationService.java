package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Common server authority used by the thin vanilla-menu mixin hooks. */
public final class MenuCostModificationService {

    public static final ResourceLocation ANVIL_EXPERIENCE = id("anvil_experience");
    public static final ResourceLocation ANVIL_MATERIALS = id("anvil_materials");
    public static final ResourceLocation ENCHANTING_EXPERIENCE = id("enchanting_experience");
    public static final ResourceLocation ENCHANTING_LAPIS = id("enchanting_lapis");

    private static final Map<UUID, LastMenuCost> LAST_MENU_COST =
            new ConcurrentHashMap<>();

    private MenuCostModificationService() {
    }

    public static ResourceCostEfficiencyService.CostQuote quoteAnvilExperience(
            ServerPlayer player,
            int baseCost
    ) {
        return quoteWorn(
                player,
                EssenceStats.ANVIL_EFFICIENCY,
                ANVIL_EXPERIENCE,
                baseCost,
                0
        );
    }

    public static ResourceCostEfficiencyService.CostQuote quoteAnvilMaterials(
            ServerPlayer player,
            int baseCost
    ) {
        return quoteWorn(
                player,
                EssenceStats.ANVIL_EFFICIENCY,
                ANVIL_MATERIALS,
                baseCost,
                0
        );
    }

    public static ResourceCostEfficiencyService.CostQuote quoteEnchantingExperience(
            ServerPlayer player,
            int baseCost
    ) {
        return quoteWorn(
                player,
                EssenceStats.ENCHANTING_EFFICIENCY,
                ENCHANTING_EXPERIENCE,
                baseCost,
                0
        );
    }

    public static ResourceCostEfficiencyService.CostQuote quoteEnchantingLapis(
            ServerPlayer player,
            int baseCost
    ) {
        return quoteWorn(
                player,
                EssenceStats.ENCHANTING_EFFICIENCY,
                ENCHANTING_LAPIS,
                baseCost,
                0
        );
    }

    public static int consumeEnchantingExperience(
            ServerPlayer player,
            int baseCost
    ) {
        return commitAndRecord(player, quoteEnchantingExperience(player, baseCost));
    }

    public static int consumeEnchantingLapis(
            ServerPlayer player,
            int baseCost
    ) {
        return commitAndRecord(player, quoteEnchantingLapis(player, baseCost));
    }

    public static void commitAnvilQuote(
            ServerPlayer player,
            ResourceCostEfficiencyService.CostQuote quote
    ) {
        if (quote == null) {
            return;
        }
        ResourceCostEfficiencyService.commit(player, quote);
        record(player, quote);
    }

    public static double anvilEfficiencyPercent(ServerPlayer player) {
        return percent(player, EssenceStats.ANVIL_EFFICIENCY);
    }

    public static double enchantingEfficiencyPercent(ServerPlayer player) {
        return percent(player, EssenceStats.ENCHANTING_EFFICIENCY);
    }

    public static Optional<LastMenuCost> lastMenuCost(ServerPlayer player) {
        return Optional.ofNullable(LAST_MENU_COST.get(player.getUUID()));
    }

    public static void forget(ServerPlayer player) {
        LAST_MENU_COST.remove(player.getUUID());
    }

    private static int commitAndRecord(
            ServerPlayer player,
            ResourceCostEfficiencyService.CostQuote quote
    ) {
        ResourceCostEfficiencyService.commit(player, quote);
        record(player, quote);
        return quote.resolvedCost();
    }

    private static ResourceCostEfficiencyService.CostQuote quoteWorn(
            ServerPlayer player,
            com.mistaboom.essence_ascendance.stat.StatDefinition stat,
            ResourceLocation channel,
            int baseCost,
            int minimumCost
    ) {
        EquipmentStatState worn = EquipmentStatResolver.evaluateWornArmor(player);
        return ResourceCostEfficiencyService.quote(
                player,
                stat,
                worn.strength(stat),
                channel,
                baseCost,
                minimumCost
        );
    }

    private static double percent(
            ServerPlayer player,
            com.mistaboom.essence_ascendance.stat.StatDefinition stat
    ) {
        ResourceCostEfficiencyService.CostQuote quote = quoteWorn(
                player,
                stat,
                id("diagnostic_" + stat.id().getPath()),
                0,
                0
        );
        return quote.efficiencyPercent();
    }

    private static void record(
            ServerPlayer player,
            ResourceCostEfficiencyService.CostQuote quote
    ) {
        LAST_MENU_COST.put(
                player.getUUID(),
                new LastMenuCost(
                        quote.channelId(),
                        quote.baseCost(),
                        quote.resolvedCost(),
                        quote.efficiencyPercent(),
                        quote.nextCarry()
                )
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                path
        );
    }

    public record LastMenuCost(
            ResourceLocation channelId,
            int baseCost,
            int resolvedCost,
            double efficiencyPercent,
            double fractionalCarry
    ) {
    }
}

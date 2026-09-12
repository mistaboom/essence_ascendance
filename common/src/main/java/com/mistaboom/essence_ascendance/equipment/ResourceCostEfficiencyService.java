package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Reusable percentage reduction for small integer resource costs.
 *
 * <p>The quote is side-effect free so a menu may synchronize the exact cost
 * before the player commits. Committing stores a fractional carry, preserving
 * reductions smaller than one item or level across transactions without free
 * rerolls from reopening or recalculating a menu.</p>
 */
public final class ResourceCostEfficiencyService {

    private static final double EPSILON = 1.0E-9D;

    private ResourceCostEfficiencyService() {
    }

    public static CostQuote quote(
            ServerPlayer player,
            StatDefinition efficiencyStat,
            double applicability,
            ResourceLocation channelId,
            int baseCost,
            int minimumCost
    ) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(efficiencyStat, "Efficiency stat cannot be null");
        Objects.requireNonNull(channelId, "Cost channel ID cannot be null");
        if (baseCost < 0 || minimumCost < 0 || minimumCost > baseCost) {
            throw new IllegalArgumentException("Invalid resource cost bounds");
        }

        PlayerEssenceData data = EssenceSavedData
                .get(player.server)
                .getPlayerData(player.getUUID());
        double percent = EquipmentValueService.scaledBonus(
                data,
                efficiencyStat,
                applicability
        );
        percent = Math.max(0.0D, Math.min(100.0D, percent));

        double previousCarry = data.getFractionalResourceCostCarry(channelId);
        if (baseCost == 0) {
            return new CostQuote(
                    channelId,
                    0,
                    0,
                    percent,
                    previousCarry,
                    previousCarry
            );
        }

        FractionalIntegerCostService.Resolution resolution =
                FractionalIntegerCostService.resolve(
                        baseCost,
                        percent,
                        previousCarry,
                        minimumCost
                );

        return new CostQuote(
                channelId,
                baseCost,
                resolution.resolvedCost(),
                percent,
                previousCarry,
                resolution.nextCarry()
        );
    }

    public static boolean commit(
            ServerPlayer player,
            CostQuote quote
    ) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(quote, "Cost quote cannot be null");
        if (quote.baseCost() <= 0 || quote.efficiencyPercent() <= 0.0D) {
            return false;
        }

        EssenceSavedData savedData = EssenceSavedData.get(player.server);
        PlayerEssenceData data = savedData.getPlayerData(player.getUUID());
        double currentCarry = data.getFractionalResourceCostCarry(quote.channelId());
        if (Math.abs(currentCarry - quote.previousCarry()) > EPSILON) {
            return false;
        }

        return savedData.setFractionalResourceCostCarry(
                player.getUUID(),
                quote.channelId(),
                quote.nextCarry()
        );
    }

    public record CostQuote(
            ResourceLocation channelId,
            int baseCost,
            int resolvedCost,
            double efficiencyPercent,
            double previousCarry,
            double nextCarry
    ) {
        public int saved() {
            return baseCost - resolvedCost;
        }
    }
}

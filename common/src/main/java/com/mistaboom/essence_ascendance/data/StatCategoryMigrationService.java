package com.mistaboom.essence_ascendance.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Reusable save-schema operation for a registered stat changing categories.
 *
 * <p>A stat investment is paid in the Essence associated with its category.
 * Keeping that investment after a category move would let a later slider
 * refund convert the old Essence into the new category's Essence. This helper
 * clears the old investment, refunds only its original Essence, increments the
 * player's Nexus revision, and saturates safely if a balance is corrupted or
 * already at {@link Long#MAX_VALUE}.</p>
 */
public final class StatCategoryMigrationService {

    private static final String PLAYERS_TAG = "players";
    private static final String INVESTED_TAG = "invested";
    private static final String AVAILABLE_TAG = "available";
    private static final String NEXUS_REVISION_TAG = "nexus_revision";

    private StatCategoryMigrationService() {
    }

    public static RefundResult refundInvestmentToOriginalEssence(
            CompoundTag root,
            ResourceLocation statId,
            ResourceLocation originalEssenceId
    ) {
        Objects.requireNonNull(root, "Save root cannot be null");
        Objects.requireNonNull(statId, "Stat ID cannot be null");
        Objects.requireNonNull(
                originalEssenceId,
                "Original Essence ID cannot be null"
        );

        if (!root.contains(PLAYERS_TAG, Tag.TAG_COMPOUND)) {
            return RefundResult.EMPTY;
        }

        CompoundTag players = root.getCompound(PLAYERS_TAG);
        String statKey = statId.toString();
        String essenceKey = originalEssenceId.toString();
        int affectedPlayers = 0;
        long totalRefunded = 0L;
        long totalUnrefunded = 0L;

        for (String playerId : players.getAllKeys()) {
            if (!players.contains(playerId, Tag.TAG_COMPOUND)) {
                continue;
            }

            CompoundTag player = players.getCompound(playerId);
            CompoundTag invested = player.getCompound(INVESTED_TAG);
            if (!invested.contains(statKey, Tag.TAG_LONG)) {
                continue;
            }

            long recordedInvestment = invested.getLong(statKey);
            invested.remove(statKey);
            player.put(INVESTED_TAG, invested);

            long refundable = Math.max(0L, recordedInvestment);
            CompoundTag available = player.getCompound(AVAILABLE_TAG);
            long currentBalance = Math.max(0L, available.getLong(essenceKey));
            long refund = Math.min(refundable, Long.MAX_VALUE - currentBalance);
            available.putLong(essenceKey, currentBalance + refund);
            player.put(AVAILABLE_TAG, available);

            long revision = Math.max(0L, player.getLong(NEXUS_REVISION_TAG));
            player.putLong(
                    NEXUS_REVISION_TAG,
                    revision < Long.MAX_VALUE ? revision + 1L : revision
            );
            players.put(playerId, player);

            affectedPlayers++;
            totalRefunded = saturatingAdd(totalRefunded, refund);
            totalUnrefunded = saturatingAdd(
                    totalUnrefunded,
                    refundable - refund
            );
        }

        root.put(PLAYERS_TAG, players);
        return new RefundResult(
                affectedPlayers,
                totalRefunded,
                totalUnrefunded
        );
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    public record RefundResult(
            int affectedPlayers,
            long totalRefunded,
            long totalUnrefunded
    ) {
        private static final RefundResult EMPTY = new RefundResult(0, 0L, 0L);
    }
}

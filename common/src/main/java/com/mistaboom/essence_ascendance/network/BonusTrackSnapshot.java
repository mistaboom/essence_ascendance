package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Server-resolved presentation facts. Compatibility analysis and provenance stay in diagnostics. */
public record BonusTrackSnapshot(
        StatUnit unit,
        ResourceLocation startTier,
        ResourceLocation completionTier,
        List<BonusTrackDefinition.Checkpoint> checkpoints,
        List<Double> tierPositions,
        double investmentExponent,
        BonusTrackDefinition.PurchaseStyle purchaseStyle,
        List<Double> snapPoints,
        BonusTrackDefinition.Applicability applicability
) {
    private static final int MAX_CHECKPOINTS = 32;
    private static final int MAX_SNAPS = 128;

    public BonusTrackSnapshot {
        Objects.requireNonNull(unit);
        Objects.requireNonNull(startTier);
        Objects.requireNonNull(completionTier);
        Objects.requireNonNull(purchaseStyle);
        Objects.requireNonNull(applicability);
        requireId(startTier);
        requireId(completionTier);
        checkpoints = List.copyOf(checkpoints);
        tierPositions = List.copyOf(tierPositions);
        snapPoints = List.copyOf(snapPoints);
        if (checkpoints.isEmpty() || checkpoints.size() > MAX_CHECKPOINTS
                || tierPositions.size() != checkpoints.size() || snapPoints.size() > MAX_SNAPS
                || !Double.isFinite(investmentExponent) || investmentExponent <= 0 || investmentExponent > 1)
            throw new IllegalArgumentException("Invalid resolved Bonus track dimensions or exponent");
        var tiers = new HashSet<ResourceLocation>();
        long previousCap = 0;
        double previousEffect = 0;
        double previousPosition = 0;
        ResourceLocation firstPurchaseTier = null;
        ResourceLocation firstCompleteTier = null;
        for (int i = 0; i < checkpoints.size(); i++) {
            var checkpoint = checkpoints.get(i);
            requireId(checkpoint.tierId());
            double position = tierPositions.get(i);
            if (!tiers.add(checkpoint.tierId()) || checkpoint.cumulativeCap() < previousCap
                    || checkpoint.segmentCost() != checkpoint.cumulativeCap() - previousCap
                    || !Double.isFinite(checkpoint.effectFraction()) || checkpoint.effectFraction() < previousEffect
                    || checkpoint.effectFraction() > 1 || !Double.isFinite(position)
                    || position < 0 || (i > 0 && position <= previousPosition) || position > 1)
                throw new IllegalArgumentException("Invalid resolved Bonus tier checkpoint");
            if ((checkpoint.segmentCost() > 0) != (checkpoint.effectFraction() > previousEffect)
                    || checkpoint.purchasable() != (checkpoint.available() && checkpoint.segmentCost() > 0)
                    || (!checkpoint.available() && (checkpoint.cumulativeCap() != 0 || checkpoint.effectFraction() != 0)))
                throw new IllegalArgumentException("Inconsistent resolved Bonus purchase capacity");
            if (applicability == BonusTrackDefinition.Applicability.UNAVAILABLE
                    && (checkpoint.cumulativeCap() != 0 || checkpoint.effectFraction() != 0
                    || checkpoint.available() || checkpoint.purchasable()))
                throw new IllegalArgumentException("Unavailable Bonus has purchase capacity");
            if (firstPurchaseTier == null && checkpoint.segmentCost() > 0) firstPurchaseTier = checkpoint.tierId();
            if (firstCompleteTier == null && checkpoint.effectFraction() == 1) firstCompleteTier = checkpoint.tierId();
            previousCap = checkpoint.cumulativeCap();
            previousEffect = checkpoint.effectFraction();
            previousPosition = position;
        }
        if (!tiers.contains(startTier) || !tiers.contains(completionTier))
            throw new IllegalArgumentException("Unknown Bonus start/completion checkpoint");
        if (previousPosition != 1 || (applicability == BonusTrackDefinition.Applicability.AVAILABLE && previousEffect != 1))
            throw new IllegalArgumentException("Incomplete resolved Bonus endpoints");
        if (applicability == BonusTrackDefinition.Applicability.AVAILABLE
                && (!startTier.equals(firstPurchaseTier) || !completionTier.equals(firstCompleteTier)))
            throw new IllegalArgumentException("Bonus span disagrees with purchase checkpoints");
        double previousSnap = -1;
        for (double snap : snapPoints) {
            if (!Double.isFinite(snap) || snap < 0 || snap > 1 || snap <= previousSnap)
                throw new IllegalArgumentException("Invalid resolved Bonus snap point");
            previousSnap = snap;
        }
        if (purchaseStyle == BonusTrackDefinition.PurchaseStyle.THRESHOLD && applicability == BonusTrackDefinition.Applicability.AVAILABLE
                && (snapPoints.isEmpty() || snapPoints.getFirst() != 0 || snapPoints.getLast() != 1))
            throw new IllegalArgumentException("Missing threshold purchase endpoints");
    }

    public boolean available() { return applicability == BonusTrackDefinition.Applicability.AVAILABLE; }

    /** Called on the server, using its resolved profile; the client never reconstructs policy. */
    public static BonusTrackSnapshot from(BonusTrackDefinition track, BalanceProfileDefinition profile) {
        Objects.requireNonNull(track, "Missing resolved Bonus track; explicitly rebuild balance");
        var tiers = AscendanceTierRegistry.powerTiers().stream()
                .sorted(Comparator.comparingInt(tier -> tier.order())).toList();
        List<Double> positions = new ArrayList<>();
        for (var checkpoint : track.checkpoints()) {
            int index = -1;
            for (int i = 0; i < tiers.size(); i++) if (tiers.get(i).id().equals(checkpoint.tierId())) index = i;
            if (index < 0 && AscendanceTierRegistry.get(checkpoint.tierId()).filter(tier -> !tier.grantsPower()).isPresent()) {
                positions.add(0.0);
                continue;
            }
            if (index < 0) throw new IllegalArgumentException("Unknown resolved Bonus tier");
            // Presentation reserves an equal band for every powered tier. Economic/effect
            // fractions remain on the checkpoint and must never compress a tier's rail.
            positions.add((index + 1.0) / tiers.size());
        }
        return new BonusTrackSnapshot(track.unit(), track.startTier(), track.completionTier(), track.checkpoints(),
                positions, track.investmentExponent(), track.purchaseStyle(), track.snapPoints(), track.applicability());
    }

    static void write(RegistryFriendlyByteBuf buffer, BonusTrackSnapshot track) {
        buffer.writeEnum(track.unit());
        buffer.writeUtf(track.startTier().toString(), PlayerEssenceSyncPayload.MAX_ID_LENGTH);
        buffer.writeUtf(track.completionTier().toString(), PlayerEssenceSyncPayload.MAX_ID_LENGTH);
        buffer.writeDouble(track.investmentExponent());
        buffer.writeEnum(track.purchaseStyle());
        buffer.writeEnum(track.applicability());
        buffer.writeVarInt(track.checkpoints().size());
        for (int i = 0; i < track.checkpoints().size(); i++) {
            var checkpoint = track.checkpoints().get(i);
            buffer.writeUtf(checkpoint.tierId().toString(), PlayerEssenceSyncPayload.MAX_ID_LENGTH);
            buffer.writeLong(checkpoint.cumulativeCap());
            buffer.writeLong(checkpoint.segmentCost());
            buffer.writeDouble(checkpoint.effectFraction());
            buffer.writeBoolean(checkpoint.available());
            buffer.writeBoolean(checkpoint.purchasable());
            buffer.writeDouble(track.tierPositions().get(i));
        }
        buffer.writeVarInt(track.snapPoints().size());
        track.snapPoints().forEach(buffer::writeDouble);
    }

    static BonusTrackSnapshot read(RegistryFriendlyByteBuf buffer) {
        var unit = buffer.readEnum(StatUnit.class);
        var start = ResourceLocation.parse(buffer.readUtf(PlayerEssenceSyncPayload.MAX_ID_LENGTH));
        var completion = ResourceLocation.parse(buffer.readUtf(PlayerEssenceSyncPayload.MAX_ID_LENGTH));
        double exponent = buffer.readDouble();
        var style = buffer.readEnum(BonusTrackDefinition.PurchaseStyle.class);
        var applicability = buffer.readEnum(BonusTrackDefinition.Applicability.class);
        int count = boundedCount(buffer, MAX_CHECKPOINTS);
        List<BonusTrackDefinition.Checkpoint> checkpoints = new ArrayList<>();
        List<Double> positions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            checkpoints.add(new BonusTrackDefinition.Checkpoint(ResourceLocation.parse(buffer.readUtf(PlayerEssenceSyncPayload.MAX_ID_LENGTH)), buffer.readLong(),
                    buffer.readLong(), buffer.readDouble(), buffer.readBoolean(), buffer.readBoolean()));
            positions.add(buffer.readDouble());
        }
        int snaps = boundedCount(buffer, MAX_SNAPS);
        List<Double> snapPoints = new ArrayList<>();
        for (int i = 0; i < snaps; i++) snapPoints.add(buffer.readDouble());
        return new BonusTrackSnapshot(unit, start, completion, checkpoints, positions, exponent, style, snapPoints, applicability);
    }

    private static int boundedCount(RegistryFriendlyByteBuf buffer, int maximum) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid Bonus snapshot list size");
        return count;
    }

    private static void requireId(ResourceLocation id) {
        if (id.toString().length() > PlayerEssenceSyncPayload.MAX_ID_LENGTH)
            throw new IllegalArgumentException("Bonus tier ID exceeds transport bound");
    }
}

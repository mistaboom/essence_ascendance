package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


public record EssenceCrucibleStatePayload(
        int schemaVersion,
        int menuId,
        long blockPos,
        String ownerName,
        String accessMode,
        boolean allowed,
        boolean channeling,
        boolean dissolving,
        int channelCount,
        java.math.BigInteger offense,
        java.math.BigInteger defense,
        java.math.BigInteger vitality,
        java.math.BigInteger mobility,
        java.math.BigInteger gathering,
        java.math.BigInteger utility,
        java.math.BigInteger total,
        java.math.BigInteger reservoirCapacity,
        long transferRatePerSecond,
        double transferRange,
        int dissolutionTicksPerItem,
        int processingTicks,
        String dissolutionMode,
        int activePylonCount,
        int maxActivePylons,
        double pylonRadius,
        int visualTransferStreams,
        int simultaneousItemProcesses,
        int channelSelection, boolean channelingForViewer, boolean channelingPaused, boolean channelingAllowed, long selectionRevision, int consumingChannels, long allocatedBatch, long actualBatch, String channelReason, boolean configureAllowed, boolean manageAllowed, String machineAccess
) implements CustomPacketPayload {

    public static final int CURRENT_SCHEMA_VERSION = 14;
    private static final int MAX_TEXT = 128;

    public static final Type<EssenceCrucibleStatePayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "essence_crucible_state"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, EssenceCrucibleStatePayload> CODEC =
            StreamCodec.of(
                    EssenceCrucibleStatePayload::write,
                    EssenceCrucibleStatePayload::read
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public java.math.BigInteger[] exactEssenceAmounts() {
        return new java.math.BigInteger[]{offense, defense, vitality, mobility, gathering, utility};
    }
    public java.math.BigInteger allocatedSamplePerSecond() { return com.mistaboom.essence_ascendance.crucible.ConcurrentChannelScheduler.sampledPerSecond(allocatedBatch); }
    public java.math.BigInteger actualSamplePerSecond() { return com.mistaboom.essence_ascendance.crucible.ConcurrentChannelScheduler.sampledPerSecond(actualBatch); }
    /** Bounded compatibility projection for positive/empty button state; never used for money. */
    public long[] essenceAmounts() {
        return java.util.Arrays.stream(exactEssenceAmounts()).mapToLong(v -> v.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact()).toArray();
    }

    private static void write(
            RegistryFriendlyByteBuf buffer,
            EssenceCrucibleStatePayload payload
    ) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarInt(payload.menuId);
        buffer.writeLong(payload.blockPos);
        buffer.writeUtf(payload.ownerName, MAX_TEXT);
        buffer.writeUtf(payload.accessMode, MAX_TEXT);
        buffer.writeBoolean(payload.allowed);
        buffer.writeBoolean(payload.channeling);
        buffer.writeBoolean(payload.dissolving);
        buffer.writeVarInt(payload.channelCount);
        buffer.writeUtf(payload.offense.toString(), MAX_TEXT);
        buffer.writeUtf(payload.defense.toString(), MAX_TEXT);
        buffer.writeUtf(payload.vitality.toString(), MAX_TEXT);
        buffer.writeUtf(payload.mobility.toString(), MAX_TEXT);
        buffer.writeUtf(payload.gathering.toString(), MAX_TEXT);
        buffer.writeUtf(payload.utility.toString(), MAX_TEXT);
        buffer.writeUtf(payload.total.toString(), MAX_TEXT);
        buffer.writeUtf(payload.reservoirCapacity.toString(), MAX_TEXT);
        buffer.writeLong(payload.transferRatePerSecond);
        buffer.writeDouble(payload.transferRange);
        buffer.writeVarInt(payload.dissolutionTicksPerItem);
        buffer.writeVarInt(payload.processingTicks);
        buffer.writeUtf(payload.dissolutionMode, MAX_TEXT);
        buffer.writeVarInt(payload.activePylonCount);
        buffer.writeVarInt(payload.maxActivePylons);
        buffer.writeDouble(payload.pylonRadius);
        buffer.writeVarInt(payload.visualTransferStreams);
        buffer.writeVarInt(payload.simultaneousItemProcesses);
        buffer.writeVarInt(payload.channelSelection);
        buffer.writeBoolean(payload.channelingForViewer);
        buffer.writeBoolean(payload.channelingPaused);
        buffer.writeBoolean(payload.channelingAllowed);
        buffer.writeVarLong(payload.selectionRevision); buffer.writeVarInt(payload.consumingChannels);
        buffer.writeVarLong(payload.allocatedBatch); buffer.writeVarLong(payload.actualBatch); buffer.writeUtf(payload.channelReason, MAX_TEXT); buffer.writeBoolean(payload.configureAllowed); buffer.writeBoolean(payload.manageAllowed); buffer.writeUtf(payload.machineAccess, MAX_TEXT);
    }

    private static EssenceCrucibleStatePayload read(
            RegistryFriendlyByteBuf buffer
    ) {
        return new EssenceCrucibleStatePayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readUtf(MAX_TEXT),
                buffer.readUtf(MAX_TEXT),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                new java.math.BigInteger(buffer.readUtf(MAX_TEXT)),
                new java.math.BigInteger(buffer.readUtf(MAX_TEXT)),
                new java.math.BigInteger(buffer.readUtf(MAX_TEXT)),
                new java.math.BigInteger(buffer.readUtf(MAX_TEXT)),
                new java.math.BigInteger(buffer.readUtf(MAX_TEXT)),
                new java.math.BigInteger(buffer.readUtf(MAX_TEXT)),
                new java.math.BigInteger(buffer.readUtf(MAX_TEXT)),
                new java.math.BigInteger(buffer.readUtf(MAX_TEXT)),
                buffer.readLong(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(MAX_TEXT),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readVarLong(), buffer.readVarInt(), buffer.readVarLong(), buffer.readVarLong(), buffer.readUtf(MAX_TEXT), buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(MAX_TEXT)
        );
    }
}

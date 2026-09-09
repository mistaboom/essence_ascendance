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
        String channelingPlayer,
        long offense,
        long defense,
        long vitality,
        long mobility,
        long gathering,
        long utility,
        long total,
        long reservoirCapacity,
        long transferRatePerSecond,
        double transferRange,
        int dissolutionTicksPerItem,
        int processingTicks,
        String dissolutionMode,
        int activePylonCount,
        int maxActivePylons,
        double pylonRadius,
        int visualTransferStreams,
        int simultaneousItemProcesses
) implements CustomPacketPayload {

    public static final int CURRENT_SCHEMA_VERSION = 8;
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

    public long[] essenceAmounts() {
        return new long[]{
                offense,
                defense,
                vitality,
                mobility,
                gathering,
                utility
        };
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
        buffer.writeUtf(payload.channelingPlayer, MAX_TEXT);
        buffer.writeLong(payload.offense);
        buffer.writeLong(payload.defense);
        buffer.writeLong(payload.vitality);
        buffer.writeLong(payload.mobility);
        buffer.writeLong(payload.gathering);
        buffer.writeLong(payload.utility);
        buffer.writeLong(payload.total);
        buffer.writeLong(payload.reservoirCapacity);
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
                buffer.readUtf(MAX_TEXT),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(MAX_TEXT),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }
}

package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EssencePylonStatePayload(
        int schemaVersion,
        int menuId,
        long blockPos,
        String ownerName,
        boolean allowed,
        boolean linked,
        long linkedCruciblePos,
        boolean active,
        boolean focusInstalled,
        String focusTierName,
        double pylonRadius,
        int maxActivePylons,
        long reservoirCapacityBonus,
        double transferRangeBonus,
        long transferRatePerSecondBonus,
        double dissolutionSpeedBonus,
        int simultaneousItemProcessesBonus,
        int linkedActivePylons,
        long linkedReservoirCapacity,
        double linkedTransferRange,
        long linkedTransferRatePerSecond,
        int linkedDissolutionTicksPerItem,
        int linkedSimultaneousItemProcesses
) implements CustomPacketPayload {

    public static final int CURRENT_SCHEMA_VERSION = 2;
    private static final int MAX_TEXT = 128;

    public static final Type<EssencePylonStatePayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "essence_pylon_state"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, EssencePylonStatePayload> CODEC =
            StreamCodec.of(
                    EssencePylonStatePayload::write,
                    EssencePylonStatePayload::read
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(
            RegistryFriendlyByteBuf buffer,
            EssencePylonStatePayload payload
    ) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarInt(payload.menuId);
        buffer.writeLong(payload.blockPos);
        buffer.writeUtf(payload.ownerName, MAX_TEXT);
        buffer.writeBoolean(payload.allowed);
        buffer.writeBoolean(payload.linked);
        buffer.writeLong(payload.linkedCruciblePos);
        buffer.writeBoolean(payload.active);
        buffer.writeBoolean(payload.focusInstalled);
        buffer.writeUtf(payload.focusTierName, MAX_TEXT);
        buffer.writeDouble(payload.pylonRadius);
        buffer.writeVarInt(payload.maxActivePylons);
        buffer.writeLong(payload.reservoirCapacityBonus);
        buffer.writeDouble(payload.transferRangeBonus);
        buffer.writeLong(payload.transferRatePerSecondBonus);
        buffer.writeDouble(payload.dissolutionSpeedBonus);
        buffer.writeVarInt(payload.simultaneousItemProcessesBonus);
        buffer.writeVarInt(payload.linkedActivePylons);
        buffer.writeLong(payload.linkedReservoirCapacity);
        buffer.writeDouble(payload.linkedTransferRange);
        buffer.writeLong(payload.linkedTransferRatePerSecond);
        buffer.writeVarInt(payload.linkedDissolutionTicksPerItem);
        buffer.writeVarInt(payload.linkedSimultaneousItemProcesses);
    }

    private static EssencePylonStatePayload read(RegistryFriendlyByteBuf buffer) {
        return new EssencePylonStatePayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readUtf(MAX_TEXT),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(MAX_TEXT),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readDouble(),
                buffer.readLong(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readDouble(),
                buffer.readLong(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }
}

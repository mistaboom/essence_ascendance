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
        String channelingPlayer,
        boolean skillEssencesEnabled,
        long offense,
        long defense,
        long vitality,
        long mobility,
        long gathering,
        long utility,
        long pyre,
        long flow,
        long terra,
        long gale,
        long body,
        long mind,
        long spirit,
        long radiance,
        long voidEssence,
        long total,
        long reservoirCapacity,
        long transferRatePerSecond,
        double transferRange,
        int dissolutionTicksPerItem,
        int processingTicks,
        int activePylonCount
) implements CustomPacketPayload {

    public static final int CURRENT_SCHEMA_VERSION = 2;
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

    public long[] skillEssenceAmounts() {
        return new long[]{
                pyre,
                flow,
                terra,
                gale,
                body,
                mind,
                spirit,
                radiance,
                voidEssence
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
        buffer.writeUtf(payload.channelingPlayer, MAX_TEXT);
        buffer.writeBoolean(payload.skillEssencesEnabled);
        buffer.writeLong(payload.offense);
        buffer.writeLong(payload.defense);
        buffer.writeLong(payload.vitality);
        buffer.writeLong(payload.mobility);
        buffer.writeLong(payload.gathering);
        buffer.writeLong(payload.utility);
        buffer.writeLong(payload.pyre);
        buffer.writeLong(payload.flow);
        buffer.writeLong(payload.terra);
        buffer.writeLong(payload.gale);
        buffer.writeLong(payload.body);
        buffer.writeLong(payload.mind);
        buffer.writeLong(payload.spirit);
        buffer.writeLong(payload.radiance);
        buffer.writeLong(payload.voidEssence);
        buffer.writeLong(payload.total);
        buffer.writeLong(payload.reservoirCapacity);
        buffer.writeLong(payload.transferRatePerSecond);
        buffer.writeDouble(payload.transferRange);
        buffer.writeVarInt(payload.dissolutionTicksPerItem);
        buffer.writeVarInt(payload.processingTicks);
        buffer.writeVarInt(payload.activePylonCount);
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
                buffer.readUtf(MAX_TEXT),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
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
                buffer.readVarInt()
        );
    }
}

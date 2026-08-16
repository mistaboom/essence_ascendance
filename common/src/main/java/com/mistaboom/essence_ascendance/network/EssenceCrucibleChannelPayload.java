package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EssenceCrucibleChannelPayload(
        int menuId,
        boolean active
) implements CustomPacketPayload {

    public static final Type<EssenceCrucibleChannelPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "essence_crucible_channel"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, EssenceCrucibleChannelPayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.menuId());
                        buffer.writeBoolean(payload.active());
                    },
                    buffer -> new EssenceCrucibleChannelPayload(
                            buffer.readVarInt(),
                            buffer.readBoolean()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

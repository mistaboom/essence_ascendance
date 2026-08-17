package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EssencePylonStateRequestPayload(
        int menuId
) implements CustomPacketPayload {

    public static final Type<EssencePylonStateRequestPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "essence_pylon_state_request"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, EssencePylonStateRequestPayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeVarInt(payload.menuId()),
                    buffer -> new EssencePylonStateRequestPayload(buffer.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

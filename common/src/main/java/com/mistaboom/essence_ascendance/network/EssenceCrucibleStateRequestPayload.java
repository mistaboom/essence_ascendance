package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client request for a fresh snapshot of the server's currently-open
 * Crucible menu. The request contains no BlockPos; the server resolves the
 * block exclusively from the player's authoritative open menu.
 */
public record EssenceCrucibleStateRequestPayload(
        int menuId
) implements CustomPacketPayload {

    public static final Type<EssenceCrucibleStateRequestPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "essence_crucible_state_request"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, EssenceCrucibleStateRequestPayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeVarInt(payload.menuId()),
                    buffer -> new EssenceCrucibleStateRequestPayload(
                            buffer.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

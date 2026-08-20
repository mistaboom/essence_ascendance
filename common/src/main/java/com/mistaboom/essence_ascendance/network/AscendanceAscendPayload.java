package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One explicit Nexus request to advance exactly one Ascendance tier. */
public record AscendanceAscendPayload(
        int menuId,
        String baseTierId
) implements CustomPacketPayload {

    private static final int MAX_ID_LENGTH = 128;

    public static final Type<AscendanceAscendPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "ascendance_ascend"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, AscendanceAscendPayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.menuId());
                        buffer.writeUtf(payload.baseTierId(), MAX_ID_LENGTH);
                    },
                    buffer ->
                            new AscendanceAscendPayload(
                                    buffer.readVarInt(),
                                    buffer.readUtf(MAX_ID_LENGTH)
                            )
            );

    public AscendanceAscendPayload {
        if (baseTierId == null || baseTierId.isBlank()) {
            throw new IllegalArgumentException("Base tier ID cannot be blank");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

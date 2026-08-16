package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative request to permanently vent one enabled Essence. */
public record EssenceCrucibleVentPayload(
        int menuId,
        String essenceId
) implements CustomPacketPayload {

    private static final int MAX_ESSENCE_ID = 128;

    public static final Type<EssenceCrucibleVentPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "essence_crucible_vent"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, EssenceCrucibleVentPayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.menuId());
                        buffer.writeUtf(payload.essenceId(), MAX_ESSENCE_ID);
                    },
                    buffer -> new EssenceCrucibleVentPayload(
                            buffer.readVarInt(),
                            buffer.readUtf(MAX_ESSENCE_ID)
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

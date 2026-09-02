package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative request to cycle/set a Crucible's dissolution scheduler. */
public record EssenceCrucibleDissolutionModePayload(
        int menuId,
        String mode
) implements CustomPacketPayload {

    private static final int MAX_MODE_TEXT = 64;

    public static final Type<EssenceCrucibleDissolutionModePayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            EssenceAscendance.MOD_ID,
                            "essence_crucible_dissolution_mode"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, EssenceCrucibleDissolutionModePayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.menuId);
                        buffer.writeUtf(payload.mode, MAX_MODE_TEXT);
                    },
                    buffer -> new EssenceCrucibleDissolutionModePayload(
                            buffer.readVarInt(),
                            buffer.readUtf(MAX_MODE_TEXT)
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

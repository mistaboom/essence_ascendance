package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Toggle one type, All or None; actor and station come only from the current server menu. */
public record EssenceCrucibleSelectionPayload(int menuId, int action, long expectedRevision) implements CustomPacketPayload {
    public static final Type<EssenceCrucibleSelectionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            EssenceAscendance.MOD_ID, "essence_crucible_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EssenceCrucibleSelectionPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeVarInt(payload.menuId); buffer.writeVarInt(payload.action); buffer.writeVarLong(payload.expectedRevision); },
            buffer -> new EssenceCrucibleSelectionPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

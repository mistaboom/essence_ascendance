package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Actor, machine, source, team and member targets are resolved from server-held choices only. */
public record TeamInterfaceActionPayload(int menuId, UUID receipt, int action, int view, int page, String input) implements CustomPacketPayload {
    public static final int OPEN = -1, REQUEST = -2, CLOSE = -4;
    public static final Type<TeamInterfaceActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "team_interface_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamInterfaceActionPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.menuId); b.writeUUID(p.receipt); b.writeVarInt(p.action); b.writeVarInt(p.view); b.writeVarInt(p.page); b.writeUtf(p.input, 80); },
            b -> new TeamInterfaceActionPayload(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(80)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

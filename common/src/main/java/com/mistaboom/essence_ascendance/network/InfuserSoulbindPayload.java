package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** One-use confirmation receipt. Actor, recipient and workpiece are always resolved on the server. */
public record InfuserSoulbindPayload(int menuId, UUID receipt) implements CustomPacketPayload {
    public static final Type<InfuserSoulbindPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "infuser_soulbind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InfuserSoulbindPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.menuId()); b.writeUUID(p.receipt()); },
            b -> new InfuserSoulbindPayload(b.readVarInt(), b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

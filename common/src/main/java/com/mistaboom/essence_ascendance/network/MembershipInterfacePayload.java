package com.mistaboom.essence_ascendance.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Connection-scoped presentation hint. All membership actions remain server-authorized. */
public record MembershipInterfacePayload(boolean nativeAvailable) implements CustomPacketPayload {
    public static final Type<MembershipInterfacePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("essence_ascendance", "membership_interface"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MembershipInterfacePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBoolean(payload.nativeAvailable()),
            buffer -> new MembershipInterfacePayload(buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

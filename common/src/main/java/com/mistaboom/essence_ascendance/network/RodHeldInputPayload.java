package com.mistaboom.essence_ascendance.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A heartbeat and client visibility scale for an existing hook. Launch bounds remain server-owned. */
public record RodHeldInputPayload(int hookId, boolean held, double entityViewScale) implements CustomPacketPayload {
    public RodHeldInputPayload(int hookId, boolean held) { this(hookId, held, 1); }
    public RodHeldInputPayload {
        if (!Double.isFinite(entityViewScale) || entityViewScale < .25 || entityViewScale > 12.5)
            throw new IllegalArgumentException("Invalid entity visibility scale");
    }
    public static final Type<RodHeldInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("essence_ascendance", "rod_held_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RodHeldInputPayload> CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeVarInt(value.hookId()); buffer.writeBoolean(value.held()); buffer.writeDouble(value.entityViewScale()); },
            buffer -> new RodHeldInputPayload(buffer.readVarInt(), buffer.readBoolean(), buffer.readDouble()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void init() {
        dev.architectury.networking.NetworkManager.registerReceiver(dev.architectury.networking.NetworkManager.Side.C2S, TYPE, CODEC,
                (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)
                        com.mistaboom.essence_ascendance.gathering.AscendanceFishingService.heldInput(player, payload.hookId(), payload.held(), payload.entityViewScale());
                }));
    }
}

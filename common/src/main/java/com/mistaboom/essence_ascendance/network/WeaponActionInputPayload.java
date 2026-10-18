package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.equipment.WeaponActionRuntime;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** A single press, with no client compatibility claim or target and no queued/replayed action. */
public record WeaponActionInputPayload(boolean stab, long sequence, long sentAt, long policyRevision) implements CustomPacketPayload {
    public static final Type<WeaponActionInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("essence_ascendance", "weapon_action_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponActionInputPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeBoolean(p.stab()); b.writeVarLong(p.sequence()); b.writeVarLong(p.sentAt()); b.writeVarLong(p.policyRevision()); },
            b -> new WeaponActionInputPayload(b.readBoolean(),b.readVarLong(),b.readVarLong(),b.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, TYPE, CODEC, (p, c) -> c.queue(() -> {
            if (c.getPlayer() instanceof ServerPlayer player) WeaponActionRuntime.input(player,p.stab(),p.sequence(),p.sentAt(),p.policyRevision());
        }));
    }
}

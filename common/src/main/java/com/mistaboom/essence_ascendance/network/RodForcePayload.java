package com.mistaboom.essence_ascendance.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import dev.architectury.networking.NetworkManager;

/** Server-computed incremental force. Preserve the local player's native gravity/drag/input velocity;
 * replacing it every tick with a positional sample would repeatedly re-add gravity compensation. */
public record RodForcePayload(ResourceLocation dimension, int hookId, int ownerId, Vec3 force) implements CustomPacketPayload {
    public static final Type<RodForcePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("essence_ascendance", "rod_force"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RodForcePayload> CODEC = StreamCodec.of(
            (buffer,p) -> { buffer.writeResourceLocation(p.dimension()); buffer.writeVarInt(p.hookId()); buffer.writeVarInt(p.ownerId()); buffer.writeDouble(p.force().x); buffer.writeDouble(p.force().y); buffer.writeDouble(p.force().z); },
            buffer -> new RodForcePayload(buffer.readResourceLocation(),buffer.readVarInt(),buffer.readVarInt(),new Vec3(buffer.readDouble(),buffer.readDouble(),buffer.readDouble())));
    public RodForcePayload { if(!Double.isFinite(force.lengthSqr()) || force.length()>1_000_000) throw new IllegalArgumentException("Invalid rod motion packet"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void init(){
        if(dev.architectury.platform.Platform.getEnvironment()==dev.architectury.utils.Env.SERVER)NetworkManager.registerS2CPayloadType(TYPE,CODEC);
    }
    public static void send(ServerPlayer player, int hookId, int ownerId, Vec3 force) {
        if (!player.hurtMarked && NetworkManager.canPlayerReceive(player,TYPE))
            NetworkManager.sendToPlayer(player,new RodForcePayload(player.level().dimension().location(),hookId,ownerId,force));
        else player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
    }
}

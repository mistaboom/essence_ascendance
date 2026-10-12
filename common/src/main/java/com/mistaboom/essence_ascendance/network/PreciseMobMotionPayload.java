package com.mistaboom.essence_ascendance.network;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Native velocity packets clamp each axis to 3.9; personalized mob launches can exceed that representation. */
public record PreciseMobMotionPayload(ResourceLocation dimension, int entityId, UUID entityUuid, Vec3 velocity)
        implements CustomPacketPayload {
    public static final Type<PreciseMobMotionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("essence_ascendance", "precise_mob_motion"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PreciseMobMotionPayload> CODEC = StreamCodec.of(
            (buffer, p) -> {
                buffer.writeResourceLocation(p.dimension()); buffer.writeVarInt(p.entityId()); buffer.writeUUID(p.entityUuid());
                buffer.writeDouble(p.velocity().x); buffer.writeDouble(p.velocity().y); buffer.writeDouble(p.velocity().z);
            }, buffer -> new PreciseMobMotionPayload(buffer.readResourceLocation(), buffer.readVarInt(), buffer.readUUID(),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())));
    public PreciseMobMotionPayload {
        if (!Double.isFinite(velocity.lengthSqr()) || velocity.length() > 1_000_000)
            throw new IllegalArgumentException("Invalid precise mob motion");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void init() {
        if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.SERVER)
            NetworkManager.registerS2CPayloadType(TYPE, CODEC);
    }
}

package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.VisualIntensity;
import com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A one-shot recipe invocation, never a stream of client geometry. */
public record WorldVisualEventPayload(WorldVisualEvent event) implements CustomPacketPayload {
    public static final Type<WorldVisualEventPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "world_visual_event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldVisualEventPayload> CODEC =
            StreamCodec.of(WorldVisualEventPayload::write, WorldVisualEventPayload::read);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buffer, WorldVisualEventPayload payload) {
        WorldVisualEvent event = payload.event();
        buffer.writeResourceLocation(event.recipeId());
        buffer.writeResourceLocation(event.dimension());
        writeVec3(buffer, event.position());
        buffer.writeVarInt(event.sourceEntityId() + 1);
        buffer.writeVarInt(event.targetEntityId() + 1);
        buffer.writeBoolean(event.secondEndpoint() != null);
        if (event.secondEndpoint() != null) writeVec3(buffer, event.secondEndpoint());
        buffer.writeFloat((float) event.direction().x);
        buffer.writeFloat((float) event.direction().y);
        buffer.writeFloat((float) event.direction().z);
        buffer.writeFloat(event.scale());
        buffer.writeFloat(event.intensity());
        buffer.writeEnum(event.presentation());
        buffer.writeEnum(event.color());
        buffer.writeVarInt(event.lifetimeTicks());
        buffer.writeLong(event.seed());
        buffer.writeFloat(event.parameterA());
        buffer.writeFloat(event.parameterB());
    }

    private static WorldVisualEventPayload read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation recipe = buffer.readResourceLocation();
        ResourceLocation dimension = buffer.readResourceLocation();
        Vec3 position = readVec3(buffer);
        int source = buffer.readVarInt() - 1;
        int target = buffer.readVarInt() - 1;
        Vec3 second = buffer.readBoolean() ? readVec3(buffer) : null;
        Vec3 direction = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        return new WorldVisualEventPayload(new WorldVisualEvent(
                recipe, dimension, position, source, target, second, direction,
                buffer.readFloat(), buffer.readFloat(), buffer.readEnum(VisualIntensity.class),
                buffer.readEnum(SemanticVisualColor.class),
                buffer.readVarInt(), buffer.readLong(), buffer.readFloat(), buffer.readFloat()));
    }

    private static void writeVec3(RegistryFriendlyByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x);
        buffer.writeDouble(value.y);
        buffer.writeDouble(value.z);
    }

    private static Vec3 readVec3(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}

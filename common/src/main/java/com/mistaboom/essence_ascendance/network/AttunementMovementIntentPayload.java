package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Input intent only. Carries no position, distance, mode, activity, tier, or contribution claim. */
public record AttunementMovementIntentPayload(boolean active) implements CustomPacketPayload {
    public static final Type<AttunementMovementIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "attunement_movement_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AttunementMovementIntentPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBoolean(payload.active()), buffer -> new AttunementMovementIntentPayload(buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

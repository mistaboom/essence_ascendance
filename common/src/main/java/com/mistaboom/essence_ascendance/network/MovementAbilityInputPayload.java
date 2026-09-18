package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.movement.MovementAbilityInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One bounded key/routing bitset; no client timestamps, coordinates, charge or magnitudes. */
public record MovementAbilityInputPayload(MovementAbilityInput input) implements CustomPacketPayload {
    public static final Type<MovementAbilityInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "movement_ability_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MovementAbilityInputPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeByte(payload.input().flags()),
            buffer -> new MovementAbilityInputPayload(new MovementAbilityInput(buffer.readUnsignedByte())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

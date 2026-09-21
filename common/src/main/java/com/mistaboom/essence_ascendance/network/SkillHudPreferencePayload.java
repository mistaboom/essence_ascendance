package com.mistaboom.essence_ascendance.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A separate presentation-only command: cannot carry any progression operation. */
public record SkillHudPreferencePayload(int menuId, ResourceLocation skill, boolean enabled) implements CustomPacketPayload {
    public static final Type<SkillHudPreferencePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("essence_ascendance", "skill_hud_preference"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkillHudPreferencePayload> CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeVarInt(value.menuId()); buffer.writeResourceLocation(value.skill()); buffer.writeBoolean(value.enabled()); },
            buffer -> new SkillHudPreferencePayload(buffer.readVarInt(), buffer.readResourceLocation(), buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

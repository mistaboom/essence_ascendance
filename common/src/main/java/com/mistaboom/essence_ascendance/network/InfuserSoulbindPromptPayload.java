package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Server prompt and client confirmation use distinct IDs on both native loaders. */
public record InfuserSoulbindPromptPayload(int menuId, UUID receipt) implements CustomPacketPayload {
    public static final Type<InfuserSoulbindPromptPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "infuser_soulbind_prompt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InfuserSoulbindPromptPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.menuId()); b.writeUUID(p.receipt()); },
            b -> new InfuserSoulbindPromptPayload(b.readVarInt(), b.readUUID()));
    public InfuserSoulbindPayload confirmation() { return new InfuserSoulbindPayload(menuId, receipt); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

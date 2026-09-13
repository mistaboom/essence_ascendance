package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;

/** Only resolved runtime values, without the expensive evidence/resource database. */
public record RuntimeBalancePayload(String runtimeJson) implements CustomPacketPayload {
    public static final int MAX_BYTES=524288;
    public static final Type<RuntimeBalancePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID,"runtime_balance"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RuntimeBalancePayload> CODEC=StreamCodec.of(
            (buffer,payload)->buffer.writeByteArray(payload.runtimeJson.getBytes(StandardCharsets.UTF_8)),
            buffer->new RuntimeBalancePayload(new String(buffer.readByteArray(MAX_BYTES),StandardCharsets.UTF_8)));
    public RuntimeBalancePayload {
        if(runtimeJson==null||runtimeJson.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IllegalArgumentException("Resolved runtime balance payload exceeds its limit");
    }
    public Type<? extends CustomPacketPayload> type() {return TYPE;}
}

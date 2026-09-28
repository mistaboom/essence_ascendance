package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Resolved runtime only. Bound wire bytes and inflated bytes independently for large packs. */
public final class RuntimeBalancePayload implements CustomPacketPayload {
    public static final int MAX_BYTES = 524288;
    public static final int MAX_JSON_BYTES = 16 * 1024 * 1024;
    // New wire format; do not negotiate the old uncompressed channel with an old client.
    public static final Type<RuntimeBalancePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            EssenceAscendance.MOD_ID, "runtime_balance_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RuntimeBalancePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeByteArray(payload.compressed),
            buffer -> decode(buffer.readByteArray(MAX_BYTES)));

    private final String runtimeJson;
    private final byte[] compressed;

    public RuntimeBalancePayload(String runtimeJson) {
        byte[] json = jsonBytes(runtimeJson);
        this.runtimeJson = runtimeJson;
        try {
            var bytes = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(json); }
            compressed = bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot compress resolved runtime balance", error);
        }
        if (compressed.length > MAX_BYTES)
            throw new IllegalArgumentException("Compressed runtime balance is " + compressed.length
                    + " bytes; transport limit is " + MAX_BYTES);
    }

    private RuntimeBalancePayload(String runtimeJson, byte[] compressed) {
        this.runtimeJson = runtimeJson;
        this.compressed = compressed;
    }

    public String runtimeJson() { return runtimeJson; }
    public int encodedBytes() { return compressed.length; }
    public Type<RuntimeBalancePayload> type() { return TYPE; }

    public static void validateJsonSize(String json) { jsonBytes(json); }

    private static byte[] jsonBytes(String json) {
        if (json == null || json.length() > MAX_JSON_BYTES)
            throw new IllegalArgumentException("Resolved runtime balance exceeds " + MAX_JSON_BYTES + " JSON bytes");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_JSON_BYTES)
            throw new IllegalArgumentException("Resolved runtime balance is " + bytes.length
                    + " JSON bytes; limit is " + MAX_JSON_BYTES);
        return bytes;
    }

    private static RuntimeBalancePayload decode(byte[] compressed) {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            // Reject highly compressible oversized input before materializing
            // an unbounded String or passing anything to Gson.
            byte[] bytes = gzip.readNBytes(MAX_JSON_BYTES + 1);
            if (bytes.length > MAX_JSON_BYTES)
                throw new IllegalArgumentException("Inflated runtime balance exceeds " + MAX_JSON_BYTES + " bytes");
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return new RuntimeBalancePayload(json, compressed);
        } catch (IOException error) {
            throw new IllegalArgumentException("Malformed compressed runtime balance", error);
        }
    }
}

package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Real ByteBuf codec, large valid runtime, corruption and both allocation bounds. */
public final class RuntimeBalancePayloadTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        var json = RuntimeBalanceDefinition.bootstrap().toJson();
        var references = json.getAsJsonObject("attunement").getAsJsonObject("references");
        for (int n = 0; n < 24000; n++) references.addProperty("fixture:large_pack_reference_" + n, n + 1);
        String text = json.toString();
        check(text.getBytes(StandardCharsets.UTF_8).length > RuntimeBalancePayload.MAX_BYTES,
                "Fixture exceeds the old raw-JSON cap that crashed pack world creation");
        var runtime = RuntimeBalanceDefinition.fromJson(json);
        var payload = runtime.networkPayload();
        check(payload == runtime.networkPayload(), "Immutable runtime reuses encoded payload for later joins");
        check(payload.encodedBytes() < RuntimeBalancePayload.MAX_BYTES, "Large pack stays inside the bounded wire limit");
        var wire = buffer();
        RuntimeBalancePayload.CODEC.encode(wire, payload);
        var decoded = RuntimeBalancePayload.CODEC.decode(wire);
        check(wire.readableBytes() == 0 && decoded.runtimeJson().equals(payload.runtimeJson()), "Exact compressed round trip");
        wire.release();
        check(RuntimeBalanceDefinition.fromJson(com.google.gson.JsonParser.parseString(decoded.runtimeJson()).getAsJsonObject())
                .toJson().equals(json), "Client retains all runtime fields and validates the large profile");
        var unicode = new RuntimeBalancePayload("{\"text\":\"Essence \u2728 \u6f22\u5b57\"}");
        wire = buffer(); RuntimeBalancePayload.CODEC.encode(wire, unicode);
        check(RuntimeBalancePayload.CODEC.decode(wire).runtimeJson().equals(unicode.runtimeJson()), "UTF-8 round trip"); wire.release();
        rejects(() -> new RuntimeBalancePayload("x".repeat(RuntimeBalancePayload.MAX_JSON_BYTES + 1)));
        byte[] random = new byte[800000]; new Random(7391).nextBytes(random);
        rejects(() -> new RuntimeBalancePayload(Base64.getEncoder().encodeToString(random)));
        rejectWire(new byte[RuntimeBalancePayload.MAX_BYTES + 1]);
        rejectWire(new byte[]{1, 2, 3});
        byte[] valid = gzip("small".getBytes(StandardCharsets.UTF_8));
        rejectWire(Arrays.copyOf(valid, valid.length - 1));
        rejectWire(gzip(new byte[]{(byte) 0xc3, 0x28}));
        rejectWire(gzip("x".repeat(RuntimeBalancePayload.MAX_JSON_BYTES + 1).getBytes(StandardCharsets.UTF_8)));
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "RuntimeBalancePayloadTest: " + checks + " checks PASS; valid runtime JSON="
                        + text.getBytes(StandardCharsets.UTF_8).length + ", compressed=" + payload.encodedBytes());
    }
    private static byte[] gzip(byte[] bytes) throws Exception {
        var result = new ByteArrayOutputStream(); try (var gzip = new GZIPOutputStream(result)) { gzip.write(bytes); }
        return result.toByteArray();
    }
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }
    private static void rejectWire(byte[] bytes) {
        var wire = buffer(); try { wire.writeByteArray(bytes); rejects(() -> RuntimeBalancePayload.CODEC.decode(wire)); }
        finally { wire.release(); }
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError("Oversized or malformed runtime payload accepted");
    }
    private static void check(boolean value, String detail) { checks++; if (!value) throw new AssertionError(detail); }
}

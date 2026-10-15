package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import dev.architectury.platform.Platform;
import java.util.UUID;

/** Real loader/API linkage check. Exits before a world; does not claim actual manager/roster gameplay. */
public final class MembershipNativeHookTest {
    private MembershipNativeHookTest() { }
    public static void run() {
        registeredInterfacePackets();
        try { com.mistaboom.essence_ascendance.projectile.NativeMachineBindingTest.run();
            com.mistaboom.essence_ascendance.projectile.NativeAutomatedItemOwnershipTest.run();
            com.mistaboom.essence_ascendance.projectile.NativeInfuserStartTest.run(); }
        catch (Exception failure) { throw new AssertionError("Actual machine fixtures failed", failure); }
        var statuses = new java.util.EnumMap<MembershipAuthority, ProviderReadiness>(MembershipAuthority.class);
        for (var entry : MembershipProviders.entries()) {
            boolean expected = Boolean.getBoolean("essence.membership.expectFtb");
            if (Platform.isModLoaded(entry.modId()) != expected) throw new AssertionError("Unexpected provider runtime " + entry.modId());
            var installed = MembershipRuntime.provider(entry.authority());
            statuses.put(entry.authority(), installed.readiness());
            if (expected) {
            if (installed.provider() == null) throw new AssertionError("Optional adapter failed loading: " + installed.readiness());
            MembershipProvider provider = installed.provider();
            var unready = provider.capture(null);
            if (unready.readiness().status() != ProviderReadiness.Status.NOT_READY || !unready.teams().isEmpty())
                throw new AssertionError("No-world manager must be unready, not authoritative empty");
            if (provider.isCurrent(null, new UUID(0, 1), null)) throw new AssertionError("Unready provider must deny access");
            try {
                Class.forName("com.mistaboom.essence_ascendance.team.compat.FtbTeamsApiBoundaryTest").getMethod("run").invoke(null);
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Actual API boundary fixtures failed", failure); }
        } else {
            if (installed.provider() != null || installed.readiness().status() != ProviderReadiness.Status.ABSENT)
                throw new AssertionError("Absent provider was linked/activated");
            try { Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI"); throw new AssertionError("Optional jar leaked onto absent runtime"); }
            catch (ClassNotFoundException absent) { /* Expected genuine absent classpath. */ }
        }
        }
        statuses.put(MembershipAuthority.NATIVE, NativeMembershipService.READY);
        MembershipAuthority expectedAuthority = Boolean.getBoolean("essence.membership.expectFtb") ? MembershipAuthority.FTB_TEAMS
                : MembershipAuthority.NATIVE;
        if (MembershipAuthority.select(MembershipAuthority.Selection.AUTO, null, statuses).authority() != expectedAuthority)
            throw new AssertionError("Runtime AUTO priority mismatch");
        System.out.println("MembershipNativeHookTest: PASS; actual loader initialization; no world/manager opened");
    }
    public static void runAndExit() {
        try { run(); System.exit(0); } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    /** Exercise the real registered transport after ordinary mod startup, not standalone CODEC constants.
     * This must not initialize the service itself: that would hide missing startup wiring. */
    private static void registeredInterfacePackets() {
        var receipt = new UUID(0, 1);
        var c2s = java.util.List.of(
                new com.mistaboom.essence_ascendance.network.TeamInterfaceActionPayload(73, receipt, -2, 0, 0, ""),
                new com.mistaboom.essence_ascendance.network.TeamInterfaceActionPayload(73, receipt, -1, 0, 0, ""),
                new com.mistaboom.essence_ascendance.network.TeamInterfaceActionPayload(73, receipt, 0, 0, 0, "Team"),
                new com.mistaboom.essence_ascendance.network.EssenceCrucibleSelectionPayload(73, 7, 0),
                new com.mistaboom.essence_ascendance.network.InfuserSoulbindPayload(73, receipt));
        var s2c = java.util.List.of(
                new com.mistaboom.essence_ascendance.network.TeamInterfacePayload(73, receipt, 0, 0, 1,
                        java.util.List.of(), java.util.List.of(), 0, 0),
                new com.mistaboom.essence_ascendance.network.ChannelSessionsPayload(1,
                        net.minecraft.resources.ResourceLocation.parse("minecraft:overworld"), 0, 0, java.util.List.of()),
                new com.mistaboom.essence_ascendance.network.InfuserSoulbindPromptPayload(73, receipt));
        for (var request : c2s) for (var response : s2c)
            if (request.type().id().equals(response.type().id()))
                throw new AssertionError("Directional payload IDs collide in NeoForge's registration: " + request.type().id());
        if (!new com.mistaboom.essence_ascendance.network.InfuserSoulbindPromptPayload(73, receipt).confirmation()
                .equals(new com.mistaboom.essence_ascendance.network.InfuserSoulbindPayload(73, receipt)))
            throw new AssertionError("Soulbind confirmation changed the server receipt");
        for (var side : dev.architectury.networking.NetworkManager.Side.values()) {
            var payloads = side == dev.architectury.networking.NetworkManager.Side.C2S ? c2s : s2c;
            for (var payload : payloads) {
                if (side == dev.architectury.networking.NetworkManager.Side.C2S) {
                    // Fabric's C2S packet wrapper loads ClientPlayNetworking and cannot run on a
                    // dedicated server. Test-only, read-only access to Architectury 13's actual
                    // registered codec/receiver maps exercises the same pre-wrapper encoding seam.
                    var id = payload.type().id();
                    @SuppressWarnings("unchecked")
                    var codec = (net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, net.minecraft.network.protocol.common.custom.CustomPacketPayload>)
                            dev.architectury.impl.NetworkAggregator.C2S_CODECS.get(id);
                    if (codec == null || !dev.architectury.impl.NetworkAggregator.C2S_RECEIVER.containsKey(id))
                        throw new AssertionError("Missing startup C2S codec/receiver: " + id);
                    var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
                    try {
                        codec.encode(buffer, payload);
                        if (!codec.decode(buffer).equals(payload)) throw new AssertionError("Registered C2S codec changed " + id);
                    } finally { buffer.release(); }
                    continue;
                }
                var packets = new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
                dev.architectury.networking.NetworkManager.collectPackets(packets::add, side, payload, net.minecraft.core.RegistryAccess.EMPTY);
                if (packets.isEmpty()) throw new AssertionError("Startup transport did not construct " + side + " " + payload.type().id());
            }
        }
        System.out.println("Stage 8 registered transport: 5 C2S codec/receiver and 3 S2C packet construction checks passed");
    }
}

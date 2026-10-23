package com.mistaboom.essence_ascendance.membershipfixture;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Registry-bound 26.1.2 probe; its early stop is not a normal server-session pass. */
@Mod("essence_membership_test")
public final class MembershipNativeMod2612 {
    public MembershipNativeMod2612(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::stopped);
    }

    private void started(ServerStartedEvent event) {
        try {
            Class.forName("com.mistaboom.essence_ascendance.team.MembershipNativeHookTest")
                    .getMethod("run").invoke(null);
            event.getServer().halt(false);
        } catch (Throwable failure) {
            failure.printStackTrace();
            Runtime.getRuntime().halt(1);
        }
    }

    private void stopped(ServerStoppedEvent event) {
        // The isolated probe exits only after the native server has saved its world.
        System.out.println("MembershipNativeMod2612: saved world and stopped");
        Runtime.getRuntime().halt(0);
    }
}

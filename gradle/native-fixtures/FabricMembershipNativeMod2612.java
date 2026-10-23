package com.mistaboom.essence_ascendance.membershipfixture;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Fabric lifecycle boundary for the shared registry-bound membership probe. */
public final class FabricMembershipNativeMod2612 implements ModInitializer {
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                Class.forName("com.mistaboom.essence_ascendance.team.MembershipNativeHookTest")
                        .getMethod("run").invoke(null);
                server.halt(false);
            } catch (Throwable failure) {
                failure.printStackTrace();
                Runtime.getRuntime().halt(1);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            System.out.println("FabricMembershipNativeMod2612: saved world and stopped");
            Runtime.getRuntime().halt(0);
        });
    }
}

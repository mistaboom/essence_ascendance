package com.mistaboom.essence_ascendance.nativefixture2612;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.fml.common.Mod;

/** Explicit registry-bound fixture; its early exit is never a world/server-session pass. */
@Mod("essence_transfer_native_test")
public final class Neo2612TransferNativeMod {
    public Neo2612TransferNativeMod(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::serverStarted);
    }

    private void serverStarted(ServerStartedEvent event) {
        try {
            Class.forName("com.mistaboom.essence_ascendance.neoforge.MachineTransfer2612Test")
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            System.out.println("Neo2612TransferNativeMod: registry-bound transfer fixture PASS; stopping isolated server");
            event.getServer().halt(false);
        } catch (Throwable failure) {
            failure.printStackTrace();
            Runtime.getRuntime().halt(1);
        }
    }
}

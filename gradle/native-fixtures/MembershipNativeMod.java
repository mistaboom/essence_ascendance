package com.mistaboom.essence_ascendance.membershipfixture;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

/** Explicit dedicated-server no-world fixture, never part of a release. */
@Mod("essence_membership_test")
public final class MembershipNativeMod {
    public MembershipNativeMod(IEventBus bus) {
        bus.addListener((FMLLoadCompleteEvent event) -> event.enqueueWork(() -> {
            try {
                Class.forName("com.mistaboom.essence_ascendance.team.MembershipNativeHookTest").getMethod("runAndExit").invoke(null);
            } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        }));
    }
}

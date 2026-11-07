package com.mistaboom.essence_ascendance.achievementfixture;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Disposable world lifecycle; this adapter is never packaged in production. */
public final class FabricAchievementNativeMod implements ModInitializer {
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                Class.forName("com.mistaboom.essence_ascendance.achievement.AchievementNativeFixture")
                        .getMethod("run", net.minecraft.server.level.ServerLevel.class).invoke(null, server.overworld());
                server.halt(false);
            } catch (Throwable failure) {
                failure.printStackTrace();
                Runtime.getRuntime().halt(1);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            System.out.println("AchievementNativeFixture: disposable world saved and stopped");
            Runtime.getRuntime().halt(0);
        });
    }
}

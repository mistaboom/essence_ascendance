package com.mistaboom.essence_ascendance.achievementfixture;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Disposable world lifecycle; this adapter is never packaged in production. */
@Mod("essence_achievement_test")
public final class NeoForgeAchievementNativeMod {
    public NeoForgeAchievementNativeMod(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::stopped);
    }

    private void started(ServerStartedEvent event) {
        try {
            Class.forName("com.mistaboom.essence_ascendance.achievement.AchievementNativeFixture")
                    .getMethod("run", net.minecraft.server.level.ServerLevel.class).invoke(null, event.getServer().overworld());
            event.getServer().halt(false);
        } catch (Throwable failure) {
            failure.printStackTrace();
            Runtime.getRuntime().halt(1);
        }
    }

    private void stopped(ServerStoppedEvent event) {
        System.out.println("AchievementNativeFixture: disposable world saved and stopped");
        Runtime.getRuntime().halt(0);
    }
}

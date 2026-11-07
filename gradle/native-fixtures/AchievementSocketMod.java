package com.mistaboom.essence_ascendance.achievementfixture;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
@Mod("essence_achievement_socket_test")
public final class AchievementSocketMod {
    public AchievementSocketMod(IEventBus bus) {
        bus.addListener((FMLLoadCompleteEvent event) -> event.enqueueWork(() -> {
            if (Boolean.getBoolean("essence.achievement.socketClient")) AchievementSocketFixture.client();
            else AchievementSocketFixture.server();
        }));
    }
}

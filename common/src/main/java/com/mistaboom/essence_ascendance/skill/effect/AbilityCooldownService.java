package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Persistent server-authoritative cooldown channels. Choice groups can share one channel without per-skill special cases. */
public final class AbilityCooldownService {
    private AbilityCooldownService() { }

    public static long readyAt(ServerPlayer player, ResourceLocation channel, long now) {
        var saved = EssenceSavedData.get(player.server);
        var data = saved.getPlayerData(player.getUUID());
        long readyAt = data.getAbilityCooldownUntil(channel);
        if (readyAt > 0 && readyAt <= now && data.setAbilityCooldownUntil(channel, 0)) {
            saved.setDirty();
            return 0;
        }
        return readyAt;
    }

    public static long remaining(ServerPlayer player, ResourceLocation channel, long now) {
        return Math.max(0, readyAt(player, channel, now) - now);
    }

    public static boolean ready(ServerPlayer player, ResourceLocation channel, long now) {
        return remaining(player, channel, now) == 0;
    }

    public static boolean trigger(ServerPlayer player, ResourceLocation channel, long now, int cooldownTicks) {
        if (cooldownTicks < 1 || !ready(player, channel, now)) return false;
        long readyAt = SkillEffectMath.expiresAt(now, cooldownTicks);
        var saved = EssenceSavedData.get(player.server);
        if (saved.getPlayerData(player.getUUID()).setAbilityCooldownUntil(channel, readyAt)) saved.setDirty();
        return true;
    }
}

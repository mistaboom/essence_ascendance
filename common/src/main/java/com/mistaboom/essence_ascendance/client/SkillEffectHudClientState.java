package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.SkillEffectHudPayload;
import com.mistaboom.essence_ascendance.skill.effect.EffectHudVisibility;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudSnapshot;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Skill-agnostic presentation cache; server omissions hide disabled/suspended effects immediately. */
public final class SkillEffectHudClientState {
    private static final EffectHudVisibility<ResourceLocation> VISIBILITY = new EffectHudVisibility<>(20L);
    private static SkillEffectHudSnapshot snapshot;
    private static LocalPlayer receiptPlayer;
    private static ClientLevel receiptLevel;
    private static long receiptTime;
    private static boolean initialized;

    private SkillEffectHudClientState() { }

    public static void init() {
        if (initialized) return;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, SkillEffectHudPayload.TYPE, SkillEffectHudPayload.CODEC,
                (payload, context) -> ClientPacketDispatch.queue(context, () -> accept(payload)));
        initialized = true;
    }

    public static void clear() {
        snapshot = null;
        receiptPlayer = null;
        receiptLevel = null;
        receiptTime = 0L;
        VISIBILITY.clear();
    }

    public static List<SkillEffectHudEntry> visibleEntries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (receiptPlayer != minecraft.player || receiptLevel != minecraft.level) clear();
        if (snapshot == null || minecraft.player == null || minecraft.level == null
                || !minecraft.player.isAlive()) return List.of();
        long now = minecraft.level.getGameTime();
        return snapshot.entries().stream().filter(entry -> VISIBILITY.visible(entry.id(), now)).toList();
    }

    public static long estimatedServerGameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || receiptPlayer != minecraft.player || receiptLevel != minecraft.level) return 0L;
        return snapshot.serverGameTime() + Math.max(0L, receiptLevel.getGameTime() - receiptTime);
    }

    private static void accept(SkillEffectHudPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || payload.snapshot().playerEntityId() != minecraft.player.getId()
                || !payload.snapshot().dimension().equals(minecraft.level.dimension().location())) return;
        if (receiptPlayer != minecraft.player || receiptLevel != minecraft.level) clear();
        long now = minecraft.level.getGameTime();
        Map<ResourceLocation, Boolean> entries = new LinkedHashMap<>();
        for (SkillEffectHudEntry entry : payload.snapshot().entries()) entries.put(entry.id(), entry.active());
        VISIBILITY.replace(entries, now);
        snapshot = payload.snapshot();
        receiptPlayer = minecraft.player;
        receiptLevel = minecraft.level;
        receiptTime = now;
    }
}

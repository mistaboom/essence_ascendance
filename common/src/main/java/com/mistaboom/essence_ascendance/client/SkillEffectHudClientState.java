package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.SkillEffectHudPayload;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudPresentation;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudSnapshot;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Skill-agnostic presentation cache; server omissions hide disabled/suspended effects immediately. */
public final class SkillEffectHudClientState {
    private static final SkillEffectHudPresentation PRESENTATION = new SkillEffectHudPresentation();
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
        PRESENTATION.clear();
    }

    public static List<SkillEffectHudEntry> visibleEntries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (receiptPlayer != minecraft.player || receiptLevel != minecraft.level) clear();
        if (snapshot == null || minecraft.player == null || minecraft.level == null
                || !minecraft.player.isAlive()) return List.of();
        long now = minecraft.level.getGameTime();
        return PRESENTATION.visibleEntries(now);
    }

    /** Latest authoritative entry without presentation grace/freeze; useful for responsive client VFX. */
    public static SkillEffectHudEntry currentEntry(ResourceLocation id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || receiptPlayer != minecraft.player || receiptLevel != minecraft.level
                || minecraft.player == null || !minecraft.player.isAlive()) return null;
        for (SkillEffectHudEntry entry : snapshot.entries()) if (entry.id().equals(id)) return entry;
        return null;
    }

    public static long estimatedServerGameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || receiptPlayer != minecraft.player || receiptLevel != minecraft.level) return 0L;
        return snapshot.serverGameTime() + Math.max(0L, receiptLevel.getGameTime() - receiptTime);
    }

    /** Typed presentation-only reach for native crosshair picking; server validates every attack again. */
    public static double primaryMeleeBonusReach() {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || minecraft.player != receiptPlayer || minecraft.level != receiptLevel
                || minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.isSpectator()
                || estimatedServerGameTime() >= snapshot.primaryMeleeReachExpiresAt()) return 0;
        boolean functional = com.mistaboom.essence_ascendance.equipment.EquipmentShieldService.canGuard(minecraft.player, minecraft.player.getMainHandItem())
                || com.mistaboom.essence_ascendance.equipment.EquipmentShieldService.canGuard(minecraft.player, minecraft.player.getOffhandItem());
        return functional ? snapshot.primaryMeleeBonusReach() : 0;
    }

    private static void accept(SkillEffectHudPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || payload.snapshot().playerEntityId() != minecraft.player.getId()
                || !payload.snapshot().dimension().equals(minecraft.level.dimension().location())) return;
        if (receiptPlayer != minecraft.player || receiptLevel != minecraft.level) clear();
        long now = minecraft.level.getGameTime();
        PRESENTATION.replace(payload.snapshot().entries(), now);
        snapshot = payload.snapshot();
        receiptPlayer = minecraft.player;
        receiptLevel = minecraft.level;
        receiptTime = now;
    }
}

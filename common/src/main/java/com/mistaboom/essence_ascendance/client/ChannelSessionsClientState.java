package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.ChannelSessionsPayload;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudLayout;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Incoming sessions use the existing HUD card renderer/layout, without inventing a skill identity. */
public final class ChannelSessionsClientState {
    private static ChannelSessionsPayload snapshot;
    private static Object receiptPlayer, receiptLevel;
    private static long receiptTick;
    private static boolean initialized;
    public static void init() {
        if (initialized) return; initialized = true;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ChannelSessionsPayload.TYPE, ChannelSessionsPayload.CODEC,
                (p, c) -> ClientPacketDispatch.queue(c, () -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player == null || mc.level == null || p.playerEntityId() != mc.player.getId() || !p.dimension().equals(mc.level.dimension().location())) return;
                    snapshot = p; receiptPlayer = mc.player; receiptLevel = mc.level; receiptTick = mc.level.getGameTime();
                }));
    }
    public static void clear() { snapshot = null; receiptPlayer = receiptLevel = null; }
    public static void render(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        if (mc.player != receiptPlayer || mc.level != receiptLevel || mc.player == null || !mc.player.isAlive()
                || mc.level.getGameTime() - receiptTick > 20) { clear(); return; }
        if (snapshot == null || mc.options.hideGui || mc.screen != null || graphics.guiWidth() < SkillEffectHudLayout.WIDTH * 2 + 24) return;
        int y = 6, shown = 0;
        for (var link : snapshot.links()) {
            int height = SkillEffectHudLayout.height(2);
            if (y + height > graphics.guiHeight() - 70) break;
            SkillEffectHudOverlay.renderPresentationCard(graphics, new SkillEffectHudOverlay.CardContent(0xFFBFAAE8,
                    SkillEffectHudEntry.Text.translated("gui.essence_ascendance.team.hud_source", BlockPos.of(link.source()).toShortString()),
                    SkillEffectHudEntry.Text.translated("gui.essence_ascendance.channel.reason." + link.reason().toLowerCase(Locale.ROOT)),
                    List.of(SkillEffectHudEntry.Text.translated("gui.essence_ascendance.team.hud_selection", Integer.toString(Integer.bitCount(snapshot.selection()))),
                            SkillEffectHudEntry.Text.translated("gui.essence_ascendance.team.hud_batch", com.mistaboom.essence_ascendance.text.EssenceText.wholeAmount(link.allocated()), com.mistaboom.essence_ascendance.text.EssenceText.wholeAmount(link.actual()))), SkillEffectHudEntry.Meter.none()), 6, y, height, mc.level.getGameTime());
            y += height + SkillEffectHudLayout.GAP; shown++;
        }
        if (shown < snapshot.total()) graphics.drawString(mc.font, Component.translatable("gui.essence_ascendance.team.hud_more", snapshot.total() - shown), 6, y, MachineScreenUi.MUTED, false);
    }
}

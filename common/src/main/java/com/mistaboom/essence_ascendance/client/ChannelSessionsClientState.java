package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.ChannelSessionsPayload;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudLayout;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Incoming sessions use the existing HUD card renderer/layout, without inventing a skill identity. */
public final class ChannelSessionsClientState {
    private static ChannelSessionsPayload snapshot;
    private static Object receiptPlayer, receiptLevel;
    private static long receiptTick;
    private static final com.mistaboom.essence_ascendance.audio.FeedbackStateCursor ORDER=new com.mistaboom.essence_ascendance.audio.FeedbackStateCursor();
    private static boolean initialized;
    public static void init() {
        if (initialized) return; initialized = true;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ChannelSessionsPayload.TYPE, ChannelSessionsPayload.CODEC,
                (p, c) -> ClientPacketDispatch.queueWorld(c, () -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player == null || mc.level == null || p.playerEntityId() != mc.player.getId() || !p.dimension().equals(mc.level.dimension().location())) return;
                    if (!com.mistaboom.essence_ascendance.audio.FeedbackTime.fresh(p.originatingTick(),mc.level.getGameTime())
                            || mc.level.getGameTime()-p.originatingTick()>20
                            || snapshot!=null && p.originatingTick()<snapshot.originatingTick()) return;
                    long next=p.links().isEmpty()?0:p.links().getFirst().generation();
                    if(!ORDER.accept(mc.player,mc.level,p.originatingTick(),next,!p.links().isEmpty())) return;
                    snapshot = p; receiptPlayer = mc.player; receiptLevel = mc.level; receiptTick = p.originatingTick();
                }));
    }
    public static void clear() {
        ORDER.clear();
        expire();
    }
    private static void expire() {
        snapshot = null; receiptPlayer = receiptLevel = null;
    }
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (snapshot != null && (mc.player != receiptPlayer || mc.level != receiptLevel || mc.player == null || !mc.player.isAlive()
                || mc.level.getGameTime() < receiptTick)) clear();
        else if(snapshot!=null && mc.level.getGameTime()-receiptTick>20) expire();
    }
    //? if >=26.1.2
    /*public static void render(GuiGraphicsExtractor graphics) {*/
    //? if <26.1.2
    public static void render(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
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
        //? if >=26.1.2
        /*if (shown < snapshot.total()) graphics.text(mc.font, Component.translatable("gui.essence_ascendance.team.hud_more", snapshot.total() - shown), 6, y, MachineScreenUi.MUTED, false);*/
        //? if <26.1.2
        if (shown < snapshot.total()) graphics.drawString(mc.font, Component.translatable("gui.essence_ascendance.team.hud_more", snapshot.total() - shown), 6, y, MachineScreenUi.MUTED, false);
    }
}

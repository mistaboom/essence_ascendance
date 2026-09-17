package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudLayout;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One data-driven renderer/layout for every current or future effect card. No skill-specific branches. */
public final class SkillEffectHudOverlay {
    private static final int WIDTH = SkillEffectHudLayout.WIDTH;
    private static final int GAP = 3;
    private static final int BACKGROUND = 0xCC11131A;
    private static final int TEXT = 0xFFF4F1E8;
    private static final int MUTED = 0xFFB4BAC7;

    private SkillEffectHudOverlay() { }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        List<SkillEffectHudEntry> entries = SkillEffectHudClientState.visibleEntries();
        if (entries.isEmpty()) return;
        int bottom = graphics.guiHeight() - 59;
        int availableHeight = bottom - 6;
        if (availableHeight < 30 || graphics.guiWidth() < WIDTH + 12) return;
        // Keep the original bottom-right placement. Additional cards flow into columns to the left.
        int columns = Math.max(1, (graphics.guiWidth() / 2) / (WIDTH + GAP));
        int cursor = 0;
        long now = SkillEffectHudClientState.estimatedServerGameTime();
        entries = com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudPriority.order(entries, now);
        for (int column = 0; column < columns && cursor < entries.size(); column++) {
            List<SkillEffectHudEntry> group = new ArrayList<>();
            int totalHeight = 0;
            while (cursor < entries.size()) {
                SkillEffectHudEntry entry = entries.get(cursor);
                int added = height(entry) + (group.isEmpty() ? 0 : GAP);
                if (totalHeight + added > availableHeight) break;
                group.add(entry);
                totalHeight += added;
                cursor++;
            }
            if (group.isEmpty()) break;
            int x = graphics.guiWidth() - WIDTH - 6 - column * (WIDTH + GAP);
            int y = bottom - totalHeight;
            for (SkillEffectHudEntry entry : group) {
                renderCard(graphics, entry, x, y, now);
                y += height(entry) + GAP;
            }
        }
        // Explicit overflow instead of drawing off-screen or silently hiding additional active effects.
        if (cursor < entries.size()) {
            String more = Component.translatable("hud.essence_ascendance.more_effects", entries.size() - cursor).getString();
            graphics.drawString(minecraft.font, fit(more, WIDTH - 14), graphics.guiWidth() - WIDTH + 1,
                    bottom + GAP, MUTED, false);
        }
    }

    private static int height(SkillEffectHudEntry entry) { return SkillEffectHudLayout.height(entry.lines().size()); }

    private static void renderCard(GuiGraphics graphics, SkillEffectHudEntry entry, int x, int y, long now) {
        Font font = Minecraft.getInstance().font;
        graphics.fill(x, y, x + WIDTH, y + height(entry), BACKGROUND);
        graphics.fill(x, y, x + 3, y + height(entry), entry.accent());
        String titleText = resolve(entry.title()).toUpperCase(Locale.ROOT);
        String badgeText = resolve(entry.badge());
        var header = SkillEffectHudLayout.header(font.width(titleText), font.width(badgeText));
        String badge = fit(badgeText, header.badgeLimit());
        String title = fit(titleText, header.titleLimit());
        graphics.pose().pushPose();
        graphics.pose().translate(x + 7, y + 4, 0);
        graphics.pose().scale(header.scale(), header.scale(), 1);
        graphics.drawString(font, title, 0, 0, TEXT, false);
        graphics.drawString(font, badge, header.rightEdge() - font.width(badge), 0, entry.accent(), false);
        graphics.pose().popPose();
        for (int line = 0; line < entry.lines().size(); line++) {
            graphics.drawString(font, fit(resolve(entry.lines().get(line)), WIDTH - 14),
                    x + 7, y + 14 + 9 * line, MUTED, false);
        }
        int footer = y + height(entry) - 10;
        switch (entry.meter().kind()) {
            case NONE -> { }
            case TIMER -> {
                long remaining = Math.max(0L, entry.meter().expiresAt() - now);
                String value = entry.meter().expiresAt() == 0L ? " —"
                        : " " + String.format(Locale.ROOT, "%.2f", remaining / 20.0) + "s";
                graphics.drawString(font, fit(resolve(entry.meter().label()) + value, WIDTH - 14),
                        x + 7, footer, remaining > 0L ? TEXT : MUTED, false);
            }
            case PROGRESS -> {
                int fill = (int) Math.round(entry.meter().fraction() * (WIDTH - 14));
                graphics.fill(x + 7, footer, x + WIDTH - 7, footer + 4, 0xFF381C22);
                graphics.fill(x + 7, footer, x + 7 + fill, footer + 4, entry.accent());
            }
        }
    }

    private static String resolve(Text text) {
        return text.translationKey().isEmpty() ? text.literal()
                : Component.translatable(text.translationKey(), text.arguments().toArray()).getString();
    }

    private static String fit(String text, int pixels) {
        Font font = Minecraft.getInstance().font;
        if (pixels <= 0) return "";
        if (font.width(text) <= pixels) return text;
        String ellipsis = "…";
        if (font.width(ellipsis) > pixels) return "";
        return font.plainSubstrByWidth(text, pixels - font.width(ellipsis)) + ellipsis;
    }
}

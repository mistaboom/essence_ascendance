package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudLayout;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** One data-driven renderer/layout for every current or future effect card. No skill-specific branches. */
public final class SkillEffectHudOverlay {
    private static final int WIDTH = SkillEffectHudLayout.WIDTH;
    private static final int GAP = SkillEffectHudLayout.GAP;
    private static final int TEXT = 0xFFF4F1E8;
    private static final int MUTED = 0xFFB4BAC7;

    /**
     * Presentation-only stack motion. Cards never swap through one another. A new card opens its slot from
     * zero height, while a removed card leaves an invisible slot that closes behind it. Because the stack is
     * bottom anchored, cards above an insertion move up and cards above a removal settle down without crossing.
     */
    private static final double SLOT_OPEN_PIXELS_PER_SECOND = 520.0;
    private static final double SLOT_CLOSE_PIXELS_PER_SECOND = 360.0;
    private static final double MOTION_EPSILON = 0.01;
    private static final Map<ResourceLocation, AnimatedCard> CARDS = new LinkedHashMap<>();
    private static final List<ResourceLocation> STACK_ORDER = new ArrayList<>();
    private static long lastFrameNanos;

    private SkillEffectHudOverlay() { }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            clearMotion();
            return;
        }
        List<SkillEffectHudEntry> entries = SkillEffectHudClientState.visibleEntries();
        if (entries.isEmpty()) {
            clearMotion();
            return;
        }
        int bottom = graphics.guiHeight() - 59;
        int availableHeight = bottom - 6;
        if (availableHeight < 30 || graphics.guiWidth() < WIDTH + 12) return;
        int columns = Math.max(1, (graphics.guiWidth() / 2) / (WIDTH + GAP));
        long now = SkillEffectHudClientState.estimatedServerGameTime();
        List<SkillEffectHudEntry> desired = com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudPriority.order(entries, now);

        long frameNanos = System.nanoTime();
        double deltaSeconds = lastFrameNanos == 0L ? 0.0
                : Math.clamp((frameNanos - lastFrameNanos) / 1_000_000_000.0, 0.0, 0.1);
        lastFrameNanos = frameNanos;

        synchronizeCards(desired);
        animateSlots(deltaSeconds);
        pruneClosedCards();

        List<AnimatedCard> ordered = orderedCards();
        List<PositionedCard> positioned = new ArrayList<>();
        int cursor = 0;
        for (int column = 0; column < columns && cursor < ordered.size(); column++) {
            List<AnimatedCard> group = new ArrayList<>();
            double totalHeight = 0;
            while (cursor < ordered.size()) {
                AnimatedCard card = ordered.get(cursor);
                double added = card.occupiedHeight + (group.isEmpty() ? 0 : scaledGap(card));
                if (!group.isEmpty() && totalHeight + added > availableHeight) break;
                group.add(card);
                totalHeight += added;
                cursor++;
            }
            if (group.isEmpty()) break;
            int x = graphics.guiWidth() - WIDTH - 6 - column * (WIDTH + GAP);
            double y = bottom - totalHeight;
            for (int index = 0; index < group.size(); index++) {
                AnimatedCard card = group.get(index);
                if (index > 0) y += scaledGap(card);
                positioned.add(new PositionedCard(card, x, y));
                y += card.occupiedHeight;
            }
        }

        for (PositionedCard positionedCard : positioned) {
            AnimatedCard card = positionedCard.card();
            if (!card.present || card.occupiedHeight < 1.0) continue;
            renderCard(graphics, card.entry, positionedCard.x(), (int) Math.round(positionedCard.y()),
                    Math.max(1, (int) Math.ceil(card.occupiedHeight)), now);
        }

        long presentOverflow = ordered.subList(Math.min(cursor, ordered.size()), ordered.size()).stream()
                .filter(card -> card.present).count();
        if (presentOverflow > 0) {
            String more = Component.translatable("hud.essence_ascendance.more_effects", presentOverflow).getString();
            graphics.drawString(minecraft.font, fit(more, WIDTH - 14), graphics.guiWidth() - WIDTH + 1,
                    bottom + GAP, MUTED, false);
        }
    }

    /**
     * Existing cards retain their relative visual order until they disappear. Priority still determines where a
     * newly appearing card is inserted, but a timer/bucket change cannot force two live cards to swap positions.
     */
    private static void synchronizeCards(List<SkillEffectHudEntry> desired) {
        Map<ResourceLocation, SkillEffectHudEntry> incoming = desired.stream().collect(Collectors.toMap(
                SkillEffectHudEntry::id, entry -> entry, (left, right) -> right, LinkedHashMap::new));
        Set<ResourceLocation> incomingIds = incoming.keySet();

        for (AnimatedCard card : CARDS.values()) {
            card.present = incomingIds.contains(card.id);
            SkillEffectHudEntry replacement = incoming.get(card.id);
            if (replacement != null) card.entry = replacement;
        }

        List<ResourceLocation> desiredOrder = desired.stream().map(SkillEffectHudEntry::id).toList();
        for (ResourceLocation id : desiredOrder) {
            if (CARDS.containsKey(id)) continue;
            SkillEffectHudEntry entry = incoming.get(id);
            AnimatedCard card = new AnimatedCard(id, entry);
            CARDS.put(id, card);
            insertWithoutReorderingSurvivors(id, desiredOrder);
        }
    }

    private static void insertWithoutReorderingSurvivors(ResourceLocation id, List<ResourceLocation> desiredOrder) {
        int desiredIndex = desiredOrder.indexOf(id);

        // Prefer the nearest desired successor so an insertion can open space directly below the card it follows.
        for (int index = desiredIndex + 1; index < desiredOrder.size(); index++) {
            int successor = STACK_ORDER.indexOf(desiredOrder.get(index));
            if (successor >= 0) {
                STACK_ORDER.add(successor, id);
                return;
            }
        }
        // Otherwise place it after the nearest desired predecessor. Existing survivors are never reordered.
        for (int index = desiredIndex - 1; index >= 0; index--) {
            int predecessor = STACK_ORDER.indexOf(desiredOrder.get(index));
            if (predecessor >= 0) {
                STACK_ORDER.add(predecessor + 1, id);
                return;
            }
        }
        STACK_ORDER.add(id);
    }

    private static void animateSlots(double deltaSeconds) {
        if (deltaSeconds <= 0.0) return;
        for (AnimatedCard card : CARDS.values()) {
            double target = card.present ? height(card.entry) : 0.0;
            double speed = target >= card.occupiedHeight
                    ? SLOT_OPEN_PIXELS_PER_SECOND : SLOT_CLOSE_PIXELS_PER_SECOND;
            card.occupiedHeight = approach(card.occupiedHeight, target, speed * deltaSeconds);
        }
    }

    private static void pruneClosedCards() {
        List<ResourceLocation> closed = new ArrayList<>();
        for (ResourceLocation id : STACK_ORDER) {
            AnimatedCard card = CARDS.get(id);
            if (card != null && !card.present && card.occupiedHeight <= MOTION_EPSILON) closed.add(id);
        }
        if (closed.isEmpty()) return;
        STACK_ORDER.removeAll(closed);
        closed.forEach(CARDS::remove);
    }

    private static List<AnimatedCard> orderedCards() {
        List<AnimatedCard> ordered = new ArrayList<>(STACK_ORDER.size());
        for (ResourceLocation id : STACK_ORDER) {
            AnimatedCard card = CARDS.get(id);
            if (card != null && (card.present || card.occupiedHeight > MOTION_EPSILON)) ordered.add(card);
        }
        return ordered;
    }

    private static double scaledGap(AnimatedCard card) {
        int fullHeight = height(card.entry);
        if (fullHeight <= 0 || card.occupiedHeight <= 0) return 0;
        return GAP * Math.clamp(card.occupiedHeight / fullHeight, 0.0, 1.0);
    }

    private static double approach(double current, double target, double maximumStep) {
        if (Math.abs(target - current) <= MOTION_EPSILON || maximumStep <= 0) return target;
        if (current < target) return Math.min(target, current + maximumStep);
        return Math.max(target, current - maximumStep);
    }

    private static void clearMotion() {
        CARDS.clear();
        STACK_ORDER.clear();
        lastFrameNanos = 0L;
    }

    private static int height(SkillEffectHudEntry entry) { return SkillEffectHudLayout.height(entry.lines().size()); }

    private static void renderCard(GuiGraphics graphics, SkillEffectHudEntry entry, int x, int y,
                                   int visibleHeight, long now) {
        int fullHeight = height(entry);
        int clippedHeight = Math.clamp(visibleHeight, 1, fullHeight);
        graphics.fill(x, y, x + WIDTH, y + clippedHeight, SkillEffectHudLayout.BACKGROUND);
        int fillWidth = SkillEffectHudLayout.progressWidth(entry.meter());
        int fillColor = SkillEffectHudLayout.fillColor(entry.accent());
        if (fillWidth > 0) graphics.fill(x + SkillEffectHudLayout.ACCENT_WIDTH, y,
                x + SkillEffectHudLayout.ACCENT_WIDTH + fillWidth, y + clippedHeight, fillColor);
        graphics.fill(x, y, x + SkillEffectHudLayout.ACCENT_WIDTH, y + clippedHeight, entry.accent());
        // Use the possible fill color even at zero progress, avoiding text-color flicker on first charge.
        int textBackground = entry.meter().kind() == SkillEffectHudEntry.MeterKind.PROGRESS
                ? fillColor : SkillEffectHudLayout.BACKGROUND;
        int primary = SkillEffectHudLayout.textColor(TEXT, textBackground);
        int secondary = SkillEffectHudLayout.textColor(MUTED, textBackground);
        int status = SkillEffectHudLayout.textColor(entry.accent(), textBackground);
        graphics.enableScissor(x, y, x + WIDTH, y + clippedHeight);
        try {
            Font font = Minecraft.getInstance().font;
            // Four rows: full title, status plus indicator, then two full-width details.
            drawRow(graphics, resolve(entry.title()), x, y + SkillEffectHudLayout.TITLE_Y, primary);
            String badge = resolve(entry.badge());
            drawRow(graphics, badge, x, y + SkillEffectHudLayout.BADGE_Y, status);
            int statusY = y + SkillEffectHudLayout.BADGE_Y;
            switch (entry.meter().kind()) {
                case NONE, PROGRESS -> { }
                case TIMER -> {
                    long remaining = Math.max(0L, entry.meter().expiresAt() - now);
                    String value = com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudCards.compact(remaining / 20.0) + "s";
                    String timer = SkillEffectHudLayout.timerText(badge, resolve(entry.meter().label()), value, font::width);
                    int timerWidth = font.width(timer);
                    // Exceptionally long translated statuses can use spare title space for the countdown.
                    int timerY = font.width(badge) + SkillEffectHudLayout.STATUS_GAP + timerWidth <= SkillEffectHudLayout.CONTENT_WIDTH
                            ? statusY : y + SkillEffectHudLayout.TITLE_Y;
                    if (timerY == statusY || font.width(resolve(entry.title())) + SkillEffectHudLayout.STATUS_GAP + timerWidth <= SkillEffectHudLayout.CONTENT_WIDTH)
                        graphics.drawString(font, timer, x + WIDTH - SkillEffectHudLayout.PADDING - timerWidth,
                                timerY, remaining > 0L ? primary : secondary, true);
                }
            }
            int row = 0;
            // Numeric/state details outrank free-form names and other contextual prose.
            List<Text> details = new ArrayList<>(entry.lines());
            details.sort(java.util.Comparator.comparingInt(text -> text.translationKey().isEmpty() ? 1
                    : text.arguments().isEmpty() ? 1 : 0));
            for (Text detail : details) {
                String text = resolve(detail);
                if (font.width(text) > SkillEffectHudLayout.CONTENT_WIDTH) continue;
                drawRow(graphics, text, x, y + SkillEffectHudLayout.DETAIL_Y + SkillEffectHudLayout.ROW_HEIGHT * row++, secondary);
                if (row == SkillEffectHudLayout.DETAIL_ROWS) break;
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static String resolve(Text text) {
        String resolved = text.translationKey().isEmpty() ? text.literal()
                : Component.translatable(text.translationKey(), text.arguments().toArray()).getString();
        return com.mistaboom.essence_ascendance.skill.effect.SkillHudVocabulary.compact(resolved,
                key -> Component.translatable(key).getString());
    }

    private static void drawRow(GuiGraphics graphics, String text, int x, int y, int color) {
        if (Minecraft.getInstance().font.width(text) <= SkillEffectHudLayout.CONTENT_WIDTH)
            graphics.drawString(Minecraft.getInstance().font, text, x + SkillEffectHudLayout.PADDING, y, color, true);
    }

    private static String fit(String text, int pixels) {
        return Minecraft.getInstance().font.width(text) <= pixels ? text : "";
    }

    private static final class AnimatedCard {
        private final ResourceLocation id;
        private SkillEffectHudEntry entry;
        private boolean present = true;
        private double occupiedHeight;

        private AnimatedCard(ResourceLocation id, SkillEffectHudEntry entry) {
            this.id = id;
            this.entry = entry;
        }
    }

    private record PositionedCard(AnimatedCard card, int x, double y) { }
}

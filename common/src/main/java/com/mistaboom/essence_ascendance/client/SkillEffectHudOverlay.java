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
    private static final int GAP = 3;
    private static final int BACKGROUND = 0xCC11131A;
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
        graphics.fill(x, y, x + WIDTH, y + clippedHeight, BACKGROUND);
        graphics.fill(x, y, x + 3, y + clippedHeight, entry.accent());
        graphics.enableScissor(x, y, x + WIDTH, y + clippedHeight);
        try {
            Font font = Minecraft.getInstance().font;
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
            int footer = y + fullHeight - 10;
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
        } finally {
            graphics.disableScissor();
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

package com.mistaboom.essence_ascendance.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Component-preserving measurement, fitting and wrapping through Minecraft's text facilities. */
public final class StyledTextLayout {
    private static final FormattedText ELLIPSIS = FormattedText.of("\u2026");

    private StyledTextLayout() {
    }

    public record Run(String text, Style style) {
    }

    public record LinkRegion(UiBounds bounds, String target) { }

    public static boolean hasLink(FormattedCharSequence line) {
        boolean[] linked = {false};
        line.accept((index, style, codePoint) -> {
            linked[0] |= style.getClickEvent() != null && style.getClickEvent().getAction() == ClickEvent.Action.CHANGE_PAGE;
            return !linked[0];
        });
        return linked[0];
    }

    /** Internal semantic navigation metadata, never a URL or a chat command. */
    public static Component link(Component label, String target) {
        return label.copy().withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, target)));
    }

    /** Visual-order glyph measurement makes wrapped, translated and bidi hit regions match drawing. */
    public static List<LinkRegion> links(Font font, List<FormattedCharSequence> lines, int x, int y, int lineStep) {
        List<LinkRegion> result = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            if (!hasLink(lines.get(lineIndex))) continue;
            int[] cursor = {x};
            int lineY = y + lineIndex * lineStep;
            lines.get(lineIndex).accept((index, style, codePoint) -> {
                int width = font.width(FormattedCharSequence.codepoint(codePoint, style));
                ClickEvent event = style.getClickEvent();
                if (event != null && event.getAction() == ClickEvent.Action.CHANGE_PAGE) {
                    UiBounds bounds = new UiBounds(cursor[0], lineY, width, font.lineHeight);
                    if (!result.isEmpty() && result.getLast().target().equals(event.getValue())
                            && result.getLast().bounds().y() == lineY && result.getLast().bounds().right() == cursor[0]) {
                        LinkRegion previous = result.removeLast();
                        bounds = new UiBounds(previous.bounds().x(), lineY, previous.bounds().width() + width, font.lineHeight);
                    }
                    result.add(new LinkRegion(bounds, event.getValue()));
                }
                cursor[0] += width;
                return true;
            });
        }
        return List.copyOf(result);
    }

    /** Exposes logical styled runs for interaction mapping and invariant tests. */
    public static List<Run> runs(FormattedText text) {
        if (text == null) {
            return List.of();
        }
        List<Run> runs = new ArrayList<>();
        text.visit((style, content) -> {
            if (!content.isEmpty()) {
                runs.add(new Run(content, style));
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return List.copyOf(runs);
    }

    public static List<FormattedCharSequence> wrap(Font font, Component text, int maximumWidth) {
        if (text == null || maximumWidth <= 0) {
            return List.of();
        }
        return List.copyOf(font.split(text, maximumWidth));
    }

    /**
     * Fits one styled visual line. The prefix retains click/hover/color styles;
     * only the synthetic ellipsis uses the default draw color.
     */
    public static FormattedCharSequence fit(Font font, Component text, int maximumWidth) {
        if (text == null || maximumWidth <= 0) {
            return FormattedCharSequence.EMPTY;
        }
        if (font.width(text) <= maximumWidth) {
            return text.getVisualOrderText();
        }
        int ellipsisWidth = font.width(ELLIPSIS);
        if (ellipsisWidth > maximumWidth) {
            return FormattedCharSequence.EMPTY;
        }
        int prefixWidth = maximumWidth - ellipsisWidth;
        FormattedText prefix = font.substrByWidth(text, prefixWidth);
        return Language.getInstance().getVisualOrder(FormattedText.composite(prefix, ELLIPSIS));
    }

    /** Compatibility fitting for native canvas labels that are already plain text. */
    public static String fitPlain(Font font,
            String text,
            int maximumWidth
    ) {
        if (maximumWidth <= 0) {
            return "";
        }

        if (font.width(text) <= maximumWidth) {
            return text;
        }

        String ellipsis = "…";
        if (font.width(ellipsis) > maximumWidth) return "";
        int allowed =
                Math.max(1, maximumWidth - font.width(ellipsis));

        return font.plainSubstrByWidth(
                text,
                allowed
        ) + ellipsis;
    }

    public static String[] wrapTwoPlainLines(Font font,
            String text,
            int maximumWidth
    ) {
        String normalized =
                text == null ? "" : text.trim();

        if (maximumWidth <= 0
                || normalized.isEmpty()) {
            return new String[]{"", ""};
        }

        if (font.width(normalized) <= maximumWidth) {
            return new String[]{normalized, ""};
        }

        /*
         * Wrap first, then ellipsize only the final visible line. The old helper
         * required BOTH candidate lines to fit completely; if the second line
         * was a little too long it abandoned wrapping entirely and produced a
         * single "Melee Atta…" line.
         *
         * Instead, use the furthest word boundary that still fits on line one,
         * then fit only the final visible line.
         */
        int split = -1;
        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.charAt(i) != ' ') {
                continue;
            }

            String firstCandidate =
                    normalized.substring(0, i).trim();
            if (!firstCandidate.isEmpty()
                    && font.width(firstCandidate) <= maximumWidth) {
                split = i;
            } else if (!firstCandidate.isEmpty()) {
                break;
            }
        }

        if (split >= 0) {
            String first =
                    normalized.substring(0, split).trim();
            String second =
                    normalized.substring(split + 1).trim();

            return new String[]{
                    first,
                    fitPlain(font, second, maximumWidth)
            };
        }

        /*
         * A single word can itself exceed the column. Split by rendered width so
         * the second line is still available instead of discarding it.
         */
        String first =
                font.plainSubstrByWidth(
                        normalized,
                        maximumWidth
                );
        if (first.isEmpty()) {
            return new String[]{
                    fitPlain(font, normalized, maximumWidth),
                    ""
            };
        }

        String second =
                normalized.substring(
                        Math.min(first.length(), normalized.length())
                ).stripLeading();

        return new String[]{
                first,
                fitPlain(font, second, maximumWidth)
        };
    }
}

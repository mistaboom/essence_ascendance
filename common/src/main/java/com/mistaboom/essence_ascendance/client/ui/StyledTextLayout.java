package com.mistaboom.essence_ascendance.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
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

    /** Recolors every visual run while retaining emphasis and interaction metadata. */
    public static FormattedCharSequence recolor(FormattedCharSequence line, int rgb) {
        if (line == null) return FormattedCharSequence.EMPTY;
        return sink -> line.accept((index, style, codePoint) ->
                sink.accept(index, style.withColor(rgb), codePoint));
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
}

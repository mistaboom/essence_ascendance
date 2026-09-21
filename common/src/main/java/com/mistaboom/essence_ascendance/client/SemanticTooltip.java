package com.mistaboom.essence_ascendance.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Semantic tooltip styles: callers supply data and state, not per-skill formatting. */
public final class SemanticTooltip {
    public enum State {
        MET(ChatFormatting.GREEN), STAGED(ChatFormatting.AQUA),
        MISSING(ChatFormatting.RED), UNAVAILABLE(ChatFormatting.DARK_GRAY);
        private final ChatFormatting color;
        State(ChatFormatting color) { this.color = color; }
        public ChatFormatting color() { return color; }
    }

    public record Line(Component content, int indentation) { }
    private final List<Line> lines = new ArrayList<>();

    public SemanticTooltip title(Component text, int accent) {
        return add(text.copy().withStyle(style -> style.withColor(accent & 0xFFFFFF).withBold(true)), 0);
    }
    public SemanticTooltip description(Component text) { return add(text.copy().withStyle(ChatFormatting.GRAY), 0); }
    public SemanticTooltip field(Component text) { return add(text.copy().withStyle(ChatFormatting.GRAY), 0); }
    public SemanticTooltip detail(Component text) { return add(text.copy().withStyle(ChatFormatting.GRAY), 1); }
    public SemanticTooltip section(Component text) {
        return add(text.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD), 0);
    }
    public SemanticTooltip requirement(Component text, State state) {
        return add(text.copy().withStyle(state.color()), 1);
    }
    public SemanticTooltip hint(Component text) {
        return add(text.copy().withStyle(ChatFormatting.GRAY), 0);
    }
    public SemanticTooltip gap() {
        if (!lines.isEmpty() && !lines.getLast().content().getString().isEmpty()) add(Component.empty(), 0);
        return this;
    }
    public List<Line> lines() { return List.copyOf(lines); }

    private SemanticTooltip add(Component text, int indentation) {
        lines.add(new Line(text, indentation));
        return this;
    }

    public List<FormattedCharSequence> wrap(Font font, int maximumWidth) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Line line : lines) {
            if (line.content().getString().isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
                continue;
            }
            Component indent = Component.literal("  ".repeat(line.indentation()));
            int available = Math.max(1, maximumWidth - font.width(indent));
            for (FormattedCharSequence part : font.split(line.content(), available)) {
                // Indentation applies to every continuation line, not only the first one.
                wrapped.add(FormattedCharSequence.composite(indent.getVisualOrderText(), part));
            }
        }
        return List.copyOf(wrapped);
    }

    public static Component value(Object text) {
        return (text instanceof Component component ? component.copy() : Component.literal(String.valueOf(text)))
                .withStyle(ChatFormatting.WHITE);
    }

    public static Component status(Component text, boolean current, boolean projected) {
        return text.copy().withStyle(current ? State.MET.color()
                : projected ? State.STAGED.color() : State.MISSING.color());
    }
}

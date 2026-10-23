package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.UiOverlayStack;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Style;

import java.util.List;

/** A rendered machine overlay and the input contract for the exact same bounds. */
public record MachineOverlay(
        String id,
        UiBounds bounds,
        int zOrder,
        UiOverlayStack.PointerPolicy pointerPolicy,
        UiOverlayStack.TooltipPolicy tooltipPolicy,
        boolean ownsKeyboard,
        int maximumScroll,
        int scrollStep,
        List<AbstractWidget> controls,
        Renderer renderer,
        StyleResolver styleResolver
) {
    public MachineOverlay {
        controls = List.copyOf(controls);
        maximumScroll = Math.max(0, maximumScroll);
        scrollStep = Math.max(1, scrollStep);
    }

    @FunctionalInterface
    public interface Renderer {
        //? if >=26.1.2
        /*void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int scrollOffset);*/
        //? if <26.1.2
        void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int scrollOffset);
    }

    @FunctionalInterface
    public interface StyleResolver {
        Style styleAt(double mouseX, double mouseY, int scrollOffset);
    }

    public UiOverlayStack.Layer layer() {
        return new UiOverlayStack.Layer(
                id,
                bounds,
                zOrder,
                pointerPolicy,
                tooltipPolicy,
                ownsKeyboard
        );
    }

    public Style styleAt(double mouseX, double mouseY, int scrollOffset) {
        return styleResolver == null ? null : styleResolver.styleAt(mouseX, mouseY, scrollOffset);
    }
}

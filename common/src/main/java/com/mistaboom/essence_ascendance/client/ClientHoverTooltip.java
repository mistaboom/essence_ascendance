package com.mistaboom.essence_ascendance.client;

//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
//? if >=26.1.2
/*import net.minecraft.network.chat.HoverEvent;*/

/** Keeps rich text, item and entity hover content intact across GUI extractors. */
public final class ClientHoverTooltip {
    private ClientHoverTooltip() { }

    //? if >=26.1.2
    /*public static void show(GuiGraphicsExtractor graphics, Font font, Style style, int x, int y) {*/
    //? if <26.1.2
    public static void show(GuiGraphics graphics, Font font, Style style, int x, int y) {
        if (style == null || style.getHoverEvent() == null) return;
        //? if >=26.1.2
        /*if (style.getHoverEvent() instanceof HoverEvent.ShowText text) graphics.setTooltipForNextFrame(font, text.value(), x, y); else if (style.getHoverEvent() instanceof HoverEvent.ShowItem item) graphics.setTooltipForNextFrame(font, item.item().create(), x, y); else if (style.getHoverEvent() instanceof HoverEvent.ShowEntity entity) graphics.setComponentTooltipForNextFrame(font, entity.entity().getTooltipLines(), x, y);*/
        //? if <26.1.2
        graphics.renderComponentHoverEffect(font, style, x, y);
    }
}

package com.mistaboom.essence_ascendance.client.ui.content;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenControls;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenViewport;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Sizing, fitting, caption, clipping and render-state boundary for document illustrations. */
public final class IllustrationView {
    @FunctionalInterface
    public interface Renderer {
        /** Render only inside figureBounds; the caller owns clipping, pose and flush boundaries. */
        void render(GuiGraphics graphics, SemanticDocument.Illustration illustration,
                    UiBounds figureBounds, float partialTick);
    }

    public record Layout(UiBounds bounds, UiBounds figure, UiBounds caption, List<FormattedCharSequence> captionLines) { }

    private IllustrationView() { }

    public static Layout layout(Font font, SemanticDocument.Illustration illustration, UiBounds available) {
        int width = Math.min(available.width(), illustration.preferredWidth());
        int figureHeight = Math.min(Math.max(0, available.height() - font.lineHeight - 10), illustration.preferredHeight());
        int x = available.x() + Math.max(0, (available.width() - width) / 2);
        UiBounds figure = new UiBounds(x, available.y() + 4, width, figureHeight);
        List<FormattedCharSequence> lines = font.split(illustration.caption(), Math.max(1, width - 8));
        int captionHeight = Math.max(font.lineHeight + 4, lines.size() * (font.lineHeight + 1) + 4);
        UiBounds caption = new UiBounds(x, figure.bottom() + 2, width, captionHeight);
        return new Layout(new UiBounds(x, available.y(), width, figure.height() + caption.height() + 6),
                figure, caption, List.copyOf(lines));
    }

    public static void render(GuiGraphics graphics, Font font, SemanticDocument.Illustration illustration,
                              Layout layout, float partialTick, Renderer renderer) {
        FullscreenControls.panel(graphics, layout.bounds(),
                AscendanceUiPalette.argb(AscendanceUiPalette.RAISED_SURFACE),
                AscendanceUiPalette.argb(AscendanceUiPalette.DIVIDER));
        graphics.flush();
        graphics.pose().pushPose();
        try {
            FullscreenViewport.withClip(graphics, layout.figure(),
                    () -> renderer.render(graphics, illustration, layout.figure(), partialTick));
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
        int y = layout.caption().y() + 2;
        for (FormattedCharSequence line : layout.captionLines()) {
            graphics.drawCenteredString(font, line, layout.caption().x() + layout.caption().width() / 2, y,
                    AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT));
            y += font.lineHeight + 1;
        }
    }
}

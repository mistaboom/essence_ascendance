package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import com.mistaboom.essence_ascendance.client.procedural.GuiProceduralGeometry;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.UiViewport;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/** A reusable two-dimensional viewport with pixel clipping and fractional pan/scroll offsets. */
public final class FullscreenViewport {
    public record Point(double x, double y) { }

    private UiBounds bounds = new UiBounds(0, 0, 0, 0);
    private UiViewport horizontal = UiViewport.create(0, 0, 0);
    private UiViewport vertical = UiViewport.create(0, 0, 0);
    private double x;
    private double y;
    private boolean configured;
    private boolean panning;
    private double previousPanX;
    private double previousPanY;

    public void configure(UiBounds bounds, int contentWidth, int contentHeight) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        horizontal = UiViewport.create(contentWidth, bounds.width(), 0);
        vertical = UiViewport.create(contentHeight, bounds.height(), 0);
        configured = true;
        restore(x, y);
    }

    /** Restore can precede the first layout without losing a remembered offset. */
    public void restore(double x, double y) {
        requireFinite(x, y);
        this.x = configured ? Math.clamp(x, 0.0, maximumX()) : Math.max(0.0, x);
        this.y = configured ? Math.clamp(y, 0.0, maximumY()) : Math.max(0.0, y);
        if (configured) {
            horizontal = UiViewport.create(horizontal.contentSize(), horizontal.viewportSize(), (int) this.x);
            vertical = UiViewport.create(vertical.contentSize(), vertical.viewportSize(), (int) this.y);
        }
    }

    public double x() { return x; }
    public double y() { return y; }
    public double maximumX() { return horizontal.maximumOffset(); }
    public double maximumY() { return vertical.maximumOffset(); }
    public UiBounds bounds() { return bounds; }
    public int contentWidth() { return horizontal.contentSize(); }
    public int contentHeight() { return vertical.contentSize(); }

    public double originX(boolean center) {
        return bounds.x() + (center ? Math.max(0, bounds.width() - contentWidth()) / 2.0 : 0.0) - x;
    }

    public double originY(boolean center) {
        return bounds.y() + (center ? Math.max(0, bounds.height() - contentHeight()) / 2.0 : 0.0) - y;
    }

    /** Pixel-aligned content retains integer centering and rounds the scroll before subtraction. */
    public int pixelOriginX(boolean center) {
        return bounds.x() + (center ? Math.max(0, bounds.width() - contentWidth()) / 2 : 0) - (int) Math.round(x);
    }

    public int pixelOriginY(boolean center) {
        return bounds.y() + (center ? Math.max(0, bounds.height() - contentHeight()) / 2 : 0) - (int) Math.round(y);
    }

    public Point screenToContent(double screenX, double screenY, boolean centerX, boolean centerY) {
        return new Point(screenX - originX(centerX), screenY - originY(centerY));
    }

    public Point screenToContent(double screenX, double screenY) {
        return screenToContent(screenX, screenY, false, false);
    }

    public Point contentToScreen(double contentX, double contentY, boolean centerX, boolean centerY) {
        return new Point(contentX + originX(centerX), contentY + originY(centerY));
    }

    public Point contentToScreen(double contentX, double contentY) {
        return contentToScreen(contentX, contentY, false, false);
    }

    /** Wheel deltas use Minecraft's sign: a positive delta moves toward the start. */
    public boolean scroll(double dx, double dy, double step, boolean shift, boolean horizontalFallback) {
        requireFinite(dx, dy);
        if (!Double.isFinite(step) || step < 0.0) throw new IllegalArgumentException("Scroll step must be finite and nonnegative");
        if (!configured) return false;
        if (shift || (horizontalFallback && maximumY() == 0.0 && maximumX() > 0.0)) {
            dx += dy;
            dy = 0.0;
        }
        return moveTo(x - dx * step, y - dy * step);
    }

    public void beginPan(double screenX, double screenY) {
        requireFinite(screenX, screenY);
        previousPanX = screenX;
        previousPanY = screenY;
        panning = true;
    }

    public boolean pan(double screenX, double screenY) {
        requireFinite(screenX, screenY);
        if (!panning || !configured) return false;
        boolean changed = moveTo(x + previousPanX - screenX, y + previousPanY - screenY);
        previousPanX = screenX;
        previousPanY = screenY;
        return changed;
    }

    public void endPan() { panning = false; }
    public boolean isPanning() { return panning; }

    /** Arrow keys move by step; Shift directs page/home/end keys to the horizontal axis. */
    public boolean keyboard(int key, boolean shift, int step) {
        if (!configured) return false;
        int amount = Math.max(1, step);
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT -> moveTo(x - amount, y);
            case GLFW.GLFW_KEY_RIGHT -> moveTo(x + amount, y);
            case GLFW.GLFW_KEY_UP -> moveTo(x, y - amount);
            case GLFW.GLFW_KEY_DOWN -> moveTo(x, y + amount);
            case GLFW.GLFW_KEY_PAGE_UP -> shift
                    ? moveTo(horizontal.pageBy(-1).offset(), y)
                    : moveTo(x, vertical.pageBy(-1).offset());
            case GLFW.GLFW_KEY_PAGE_DOWN -> shift
                    ? moveTo(horizontal.pageBy(1).offset(), y)
                    : moveTo(x, vertical.pageBy(1).offset());
            case GLFW.GLFW_KEY_HOME -> shift ? moveTo(0.0, y) : moveTo(x, 0.0);
            case GLFW.GLFW_KEY_END -> shift ? moveTo(maximumX(), y) : moveTo(x, maximumY());
            default -> false;
        };
    }

    /** Scissors are stacked by GuiGraphics and always restored, including after renderer failure. */
    public static void withClip(GuiGraphics graphics, UiBounds bounds, Runnable render) {
        GuiProceduralGeometry.clipped(graphics, bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), render);
    }

    public void renderContent(GuiGraphics graphics, boolean centerX, boolean centerY, Runnable render) {
        withClip(graphics, bounds, () -> {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(originX(centerX), originY(centerY), 0.0);
                render.run();
            } finally {
                graphics.pose().popPose();
            }
        });
    }

    public void renderContent(GuiGraphics graphics, Runnable render) {
        renderContent(graphics, false, false, render);
    }

    private boolean moveTo(double requestedX, double requestedY) {
        double previousX = x;
        double previousY = y;
        restore(requestedX, requestedY);
        return x != previousX || y != previousY;
    }

    private static void requireFinite(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Viewport coordinates must be finite");
        }
    }
}

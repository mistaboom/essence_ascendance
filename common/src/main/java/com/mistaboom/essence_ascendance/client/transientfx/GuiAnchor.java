package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.mixin.AbstractContainerScreenPositionAccess;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;

import java.util.Objects;

/** Screen-agnostic anchors for points, rectangles and container slots. */
@FunctionalInterface
public interface GuiAnchor {
    Rect resolve(Screen screen, int guiWidth, int guiHeight);

    static GuiAnchor point(int x, int y) { return (screen, width, height) -> Rect.point(x, y); }
    static GuiAnchor rectangle(int left, int top, int right, int bottom) {
        Rect rect = new Rect(left, top, right, bottom);
        return (screen, width, height) -> rect;
    }
    static GuiAnchor centered(int offsetX, int offsetY) {
        return (screen, width, height) -> Rect.point(width / 2 + offsetX, height / 2 + offsetY);
    }
    static GuiAnchor slot(int menuSlot) {
        return (screen, width, height) -> slotRect(screen, menuSlot, false);
    }
    static GuiAnchor resultSlot() {
        return (screen, width, height) -> slotRect(screen, -1, true);
    }
    static GuiAnchor dynamic(Resolver resolver) { return Objects.requireNonNull(resolver); }

    private static Rect slotRect(Screen screen, int index, boolean result) {
        if (!(screen instanceof AbstractContainerScreen<?> container)
                || !(container instanceof AbstractContainerScreenPositionAccess origin)) return null;
        Slot selected = null;
        if (result) {
            for (Slot slot : container.getMenu().slots) {
                if (slot instanceof ResultSlot) { selected = slot; break; }
            }
        } else if (index >= 0 && index < container.getMenu().slots.size()) {
            selected = container.getMenu().slots.get(index);
        }
        if (selected == null) return null;
        int left = origin.essenceAscendance$getLeftPos() + selected.x;
        int top = origin.essenceAscendance$getTopPos() + selected.y;
        return new Rect(left, top, left + 16, top + 16);
    }

    @FunctionalInterface
    interface Resolver extends GuiAnchor { }

    record Rect(int left, int top, int right, int bottom) {
        public Rect {
            if (right < left || bottom < top) throw new IllegalArgumentException("Invalid GUI anchor rectangle");
        }
        public static Rect point(int x, int y) { return new Rect(x, y, x, y); }
        public int centerX() { return (left + right) / 2; }
        public int centerY() { return (top + bottom) / 2; }
        public int width() { return right - left; }
        public int height() { return bottom - top; }
    }
}

package com.mistaboom.essence_ascendance.client.ui;

import net.minecraft.client.renderer.Rect2i;

/**
 * Immutable screen-space geometry shared by layout, rendering and input.
 * Width and height may be zero, but are never negative.
 */
public record UiBounds(int x, int y, int width, int height) {

    public UiBounds {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("UI bounds cannot have a negative size");
        }
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < right()
                && pointY >= y && pointY < bottom();
    }

    public boolean intersects(UiBounds other) {
        return x < other.right() && right() > other.x
                && y < other.bottom() && bottom() > other.y;
    }

    public UiBounds intersection(UiBounds other) {
        int left = Math.max(x, other.x), top = Math.max(y, other.y);
        return new UiBounds(left, top, Math.max(0, Math.min(right(), other.right()) - left),
                Math.max(0, Math.min(bottom(), other.bottom()) - top));
    }

    public UiBounds translate(int deltaX, int deltaY) {
        return new UiBounds(x + deltaX, y + deltaY, width, height);
    }

    public UiBounds inset(int amount) {
        int inset = Math.max(0, amount);
        return new UiBounds(
                x + Math.min(inset, width / 2),
                y + Math.min(inset, height / 2),
                Math.max(0, width - inset * 2),
                Math.max(0, height - inset * 2)
        );
    }

    /** Clamp the complete rectangle into a container whenever its size permits it. */
    public UiBounds clampInside(UiBounds container) {
        int maximumX = Math.max(container.x, container.right() - width);
        int maximumY = Math.max(container.y, container.bottom() - height);
        return new UiBounds(
                Math.clamp(x, container.x, maximumX),
                Math.clamp(y, container.y, maximumY),
                width,
                height
        );
    }

    public Rect2i toMinecraftRect() {
        return new Rect2i(x, y, width, height);
    }
}

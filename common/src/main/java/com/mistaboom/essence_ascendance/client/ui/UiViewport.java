package com.mistaboom.essence_ascendance.client.ui;

/** A clamped one-dimensional viewport used by panels and future full-screen pages. */
public record UiViewport(int contentSize, int viewportSize, int offset) {

    public UiViewport {
        if (contentSize < 0 || viewportSize < 0) {
            throw new IllegalArgumentException("Viewport sizes cannot be negative");
        }
        offset = Math.clamp(offset, 0, maximumOffset(contentSize, viewportSize));
    }

    public static UiViewport create(int contentSize, int viewportSize, int requestedOffset) {
        return new UiViewport(contentSize, viewportSize, requestedOffset);
    }

    public int maximumOffset() {
        return maximumOffset(contentSize, viewportSize);
    }

    public boolean scrollable() {
        return maximumOffset() > 0;
    }

    public UiViewport scrollBy(int pixels) {
        return new UiViewport(contentSize, viewportSize, offset + pixels);
    }

    public UiViewport pageBy(int direction) {
        int page = Math.max(1, viewportSize - 10);
        return scrollBy(Integer.signum(direction) * page);
    }

    public UiViewport start() {
        return new UiViewport(contentSize, viewportSize, 0);
    }

    public UiViewport end() {
        return new UiViewport(contentSize, viewportSize, maximumOffset());
    }

    private static int maximumOffset(int contentSize, int viewportSize) {
        return Math.max(0, contentSize - viewportSize);
    }
}

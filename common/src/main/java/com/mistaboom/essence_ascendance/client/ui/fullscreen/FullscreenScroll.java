package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import com.mistaboom.essence_ascendance.client.ui.UiViewport;

/**
 * Measured one-dimensional scrolling in caller-defined units, such as text rows.
 * Restored offsets remain intact until content is measured; a synchronization
 * wait therefore cannot overwrite navigation with an artificial empty viewport.
 */
public final class FullscreenScroll {
    private UiViewport viewport = UiViewport.create(0, 0, 0);
    private int restoredOffset;
    private boolean measured;

    public void configure(int contentSize, int visibleSize) {
        viewport = UiViewport.create(contentSize, visibleSize, offset());
        measured = true;
    }

    public void restore(int offset) {
        restoredOffset = Math.max(0, offset);
        measured = false;
    }

    public int offset() {
        return measured ? viewport.offset() : restoredOffset;
    }

    public int maximumOffset() {
        return measured ? viewport.maximumOffset() : 0;
    }

    /** Reveal an item in the same units as configure, without discarding measurement. */
    public void ensureVisible(int start, int size) {
        if (!measured || viewport.viewportSize() == 0) return;
        int target = viewport.offset();
        if (start < target) target = start;
        else if ((long) start + size > (long) target + viewport.viewportSize())
            target = start + Math.min(Math.max(1, size), viewport.viewportSize()) - viewport.viewportSize();
        viewport = UiViewport.create(viewport.contentSize(), viewport.viewportSize(), target);
    }

    public void wheel(double amount, int step) {
        if (measured) viewport = viewport.scrollBy(-(int) Math.signum(amount) * Math.max(1, step));
    }

    /** Standard arrows, Page Up/Down and Home/End; units and page size belong to the content policy. */
    public boolean key(int key, int step, int pageStep) {
        if (!measured) return false;
        switch (key) {
            case 264 -> viewport = viewport.scrollBy(Math.max(1, step));
            case 265 -> viewport = viewport.scrollBy(-Math.max(1, step));
            case 267 -> viewport = viewport.scrollBy(Math.max(1, pageStep));
            case 266 -> viewport = viewport.scrollBy(-Math.max(1, pageStep));
            case 268 -> viewport = viewport.start();
            case 269 -> viewport = viewport.end();
            default -> { return false; }
        }
        return true;
    }
}

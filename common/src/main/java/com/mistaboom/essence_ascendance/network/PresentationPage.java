package com.mistaboom.essence_ascendance.network;

/** Shared paging arithmetic; hostile offsets cannot overflow and presentation never caps actual consumers. */
public final class PresentationPage {
    private PresentationPage() {}
    public static int start(int total, int page, int size) {
        if (total < 0 || page < 0 || size < 1) throw new IllegalArgumentException("Invalid page");
        return (int) Math.min(total, (long) page * size);
    }
    public static int pages(int total, int size) {
        if (total < 0 || size < 1) throw new IllegalArgumentException("Invalid page");
        return (int) Math.max(1, ((long) total + size - 1) / size);
    }
}

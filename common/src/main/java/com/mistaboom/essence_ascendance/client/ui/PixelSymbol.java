package com.mistaboom.essence_ascendance.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;

/** Small status marks use whole GUI pixels, independent of Unicode fallback fonts. */
public enum PixelSymbol {
    CHECK(".......", "......#", ".....##", "#...##.", "##.##..", ".###...", "..#...."),
    REPLACED("..###..", ".#...#.", "#....##", "#...###", "#......", ".#...#.", "..###..");

    public static final int SIZE = 7;
    private final List<UiBounds> pixels;

    PixelSymbol(String... rows) {
        List<UiBounds> points = new ArrayList<>();
        for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++)
            if (rows[y].charAt(x) == '#') points.add(new UiBounds(x, y, 1, 1));
        pixels = List.copyOf(points);
    }

    public List<UiBounds> pixels() { return pixels; }

    public void draw(GuiGraphics graphics, int x, int y, int color) {
        for (UiBounds pixel : pixels) graphics.fill(x + pixel.x(), y + pixel.y(),
                x + pixel.right(), y + pixel.bottom(), color);
    }
}

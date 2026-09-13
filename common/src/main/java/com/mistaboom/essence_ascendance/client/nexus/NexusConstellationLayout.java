package com.mistaboom.essence_ascendance.client.nexus;

import java.util.ArrayList;
import java.util.List;

/** Registry-sized elliptical layout, in scaled GUI coordinates. No category owns a position. */
public record NexusConstellationLayout(int centerX, int centerY, int medallionRadius,
                                      List<Node> nodes) {
    public NexusConstellationLayout { nodes = List.copyOf(nodes); }

    public static NexusConstellationLayout create(int left, int top, int width, int height, int count) {
        if (width < 1 || height < 1 || count < 0 || count > 256)
            throw new IllegalArgumentException("Invalid constellation bounds");
        int cx = left + width / 2;
        int cy = top + height / 2;
        double rx = Math.max(1, width / 2.0 - Math.min(58, width / 5.0));
        double ry = Math.max(1, height / 2.0 - Math.min(30, height / 5.0));
        double spacing = count < 2 ? 1 : Math.sin(Math.PI / count);
        int radius = Math.max(1, (int) Math.min(Math.min(20, height / 12.0), Math.min(rx, ry) * spacing * 0.62));
        ry = Math.max(radius + 2, height / 2.0 - radius - 33);
        double nearest = Double.MAX_VALUE;
        List<Node> nodes = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double angle = -Math.PI / 2 + (count % 2 == 0 ? Math.PI / Math.max(1, count) : 0)
                    + Math.PI * 2 * index / Math.max(1, count);
            nodes.add(new Node(index, cx + (int) Math.round(Math.cos(angle) * rx),
                    cy + (int) Math.round(Math.sin(angle) * ry), radius, angle));
            nearest = Math.min(nearest, Math.hypot(Math.cos(angle) * rx, Math.sin(angle) * ry));
        }
        int centerRadius = Math.max(1, (int) Math.min(Math.min(29, height / 4.0), nearest - radius - 5));
        return new NexusConstellationLayout(cx, cy, centerRadius, nodes);
    }

    public boolean medallionContains(double x, double y) {
        return Math.hypot(x - centerX, y - centerY) <= medallionRadius + 4;
    }

    public record Node(int index, int x, int y, int radius, double angle) {
        public boolean contains(double px, double py) {
            return Math.hypot(px - x, py - y) <= radius + 5;
        }
    }
}

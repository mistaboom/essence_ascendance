package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;

/** Opt-in via traversalBatchInvariants; no world, graphics context or automatic build dependency. */
public final class SingleSidedFluidVertexConsumerTest {
    private static int checks;
    private static final float[][] TOP = {{0, .8F, 0}, {0, .8F, 1}, {1, .8F, 1}, {1, .8F, 0}};
    private static final float[][] SLOPE = {{0, .1F, 0}, {0, .2F, 1}, {1, .3F, 1}, {1, .2F, 0}};

    public static void main(String[] args) {
        checks = 0;
        Collector empty = new Collector();
        var filter = new SingleSidedFluidVertexConsumer(empty);
        filter.finish(); filter.finish();
        check(empty.vertices.isEmpty(), "Empty native tessellations emit nothing");
        boolean rejected = false;
        try { filter.setColor(1, 2, 3, 4); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "Attributes cannot attach to an absent position");

        for (float[][] shape : new float[][][]{TOP, SLOPE}) {
            for (int start = 0; start < 4; start++) {
                Collector output = new Collector();
                var wrapped = new SingleSidedFluidVertexConsumer(output);
                quad(wrapped, shape, 0, 1);
                quad(wrapped, shape, start, -1);
                wrapped.finish();
                check(output.vertices.size() == 4, "Any rotated backward copy is omitted, including sloped/shallow top faces");
                for (int i = 0; i < 4; i++) attributes(output.vertices.get(i), shape[i], i);

                Collector same = new Collector();
                wrapped = new SingleSidedFluidVertexConsumer(same);
                quad(wrapped, shape, 0, 1);
                quad(wrapped, shape, start, 1);
                wrapped.finish();
                check(same.vertices.size() == 8, "Same-winding faces are not removed");
            }
        }

        // A native block: outward top, optional reverse top, outward bottom, then four
        // outward sides each followed by a reversed copy. Bottom faces have no duplicate.
        float[][] bottom = {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}};
        float[][] north = {{0, .8F, 0}, {1, .8F, 0}, {1, 0, 0}, {0, 0, 0}};
        float[][] south = {{1, .8F, 1}, {0, .8F, 1}, {0, 0, 1}, {1, 0, 1}};
        float[][] west = {{0, .8F, 1}, {0, .8F, 0}, {0, 0, 0}, {0, 0, 1}};
        float[][] east = {{1, .8F, 0}, {1, .8F, 1}, {1, 0, 1}, {1, 0, 0}};
        float[][][] faces = {TOP, bottom, north, south, west, east};
        Collector box = new Collector();
        filter = new SingleSidedFluidVertexConsumer(box);
        for (int face = 0; face < faces.length; face++) {
            quad(filter, faces[face], 0, 1);
            if (face != 1) quad(filter, faces[face], face == 0 ? 0 : 3, -1);
        }
        filter.finish();
        check(box.vertices.size() == 24, "Exactly six exterior faces remain, not an invisible lava block");
        for (int face = 0; face < faces.length; face++) {
            for (int vertex = 0; vertex < 4; vertex++) attributes(box.vertices.get(face * 4 + vertex), faces[face][vertex], vertex);
            check(!facesPoint(box, face * 4, .5F, .4F, .5F), "Inside camera sees no front-facing opaque lava boundary");
        }
        check(facesPoint(box, 0, .5F, 1.5F, .5F), "Exterior lava surface remains visible from above");
        check(facesPoint(box, 8, .5F, .4F, -1), "Exterior lava wall remains visible from outside");

        Collector distinct = new Collector();
        filter = new SingleSidedFluidVertexConsumer(distinct);
        quad(filter, TOP, 0, 1); quad(filter, SLOPE, 0, -1);
        filter.finish();
        check(distinct.vertices.size() == 8, "Different geometry is not removed just for facing inward");

        Collector last = new Collector();
        filter = new SingleSidedFluidVertexConsumer(last);
        quad(filter, TOP, 0, 1);
        check(last.vertices.isEmpty(), "A quad is buffered until all of its attributes are available");
        filter.finish(); filter.finish();
        check(last.vertices.size() == 4, "The final quad flushes once even without a following addVertex");

        // Reuse of the four mutable slots must not leak fields from a previous vertex.
        Collector sparse = new Collector();
        filter = new SingleSidedFluidVertexConsumer(sparse);
        quad(filter, TOP, 0, 1);
        for (int i = 0; i < 4; i++) filter.addVertex(i, 2, 0);
        filter.finish();
        for (int i = 4; i < 8; i++) check(sparse.vertices.get(i).fields == 0, "Unset attributes are not invented or copied from older vertices");

        Collector partial = new Collector();
        filter = new SingleSidedFluidVertexConsumer(partial);
        filter.addVertex(1, 2, 3).setColor(4, 5, 6, 7);
        filter.finish();
        check(partial.vertices.size() == 1 && partial.vertices.getFirst().alpha == 7, "Unexpected partial tail is forwarded, not silently discarded");
        System.out.println("SingleSidedFluidVertexConsumerTest: " + checks + " checks passed");
    }

    private static void quad(VertexConsumer out, float[][] shape, int start, int step) {
        for (int i = 0; i < 4; i++) {
            int at = Math.floorMod(start + step * i, 4);
            float[] p = shape[at];
            out.addVertex(p[0], p[1], p[2]).setColor(21 + at, 41 + at, 61 + at, 81 + at)
                    .setUv(at * .1F, at * .2F).setUv1(101 + at, 121 + at)
                    .setUv2(141 + at, 161 + at).setNormal(0, 1, 0);
        }
    }

    private static void attributes(Vertex actual, float[] pos, int at) {
        check(actual.x == pos[0] && actual.y == pos[1] && actual.z == pos[2], "Native positions/winding are preserved exactly");
        check(actual.fields == 31, "Every supplied vertex attribute reaches the native consumer");
        check(actual.red == 21 + at && actual.green == 41 + at && actual.blue == 61 + at && actual.alpha == 81 + at, "Tint and alpha are unchanged");
        check(actual.u == at * .1F && actual.v == at * .2F, "Resource-pack UVs are unchanged");
        check(actual.overlayU == 101 + at && actual.overlayV == 121 + at && actual.lightU == 141 + at && actual.lightV == 161 + at, "Overlay and light coordinates are unchanged");
        check(actual.nx == 0 && actual.ny == 1 && actual.nz == 0, "Native fluid normals are preserved, not mistaken for face direction");
    }

    private static boolean facesPoint(Collector output, int offset, float x, float y, float z) {
        Vertex a = output.vertices.get(offset), b = output.vertices.get(offset + 1), c = output.vertices.get(offset + 2);
        float ux = b.x - a.x, uy = b.y - a.y, uz = b.z - a.z;
        float vx = c.x - a.x, vy = c.y - a.y, vz = c.z - a.z;
        return (uy * vz - uz * vy) * (x - a.x) + (uz * vx - ux * vz) * (y - a.y)
                + (ux * vy - uy * vx) * (z - a.z) > 0;
    }

    private static final class Collector implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        private Vertex current;
        @Override public VertexConsumer addVertex(float x, float y, float z) { current = new Vertex(); current.x = x; current.y = y; current.z = z; vertices.add(current); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { current.red = r; current.green = g; current.blue = b; current.alpha = a; current.fields |= 1; return this; }
        @Override public VertexConsumer setUv(float u, float v) { current.u = u; current.v = v; current.fields |= 2; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { current.overlayU = u; current.overlayV = v; current.fields |= 4; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { current.lightU = u; current.lightV = v; current.fields |= 8; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { current.nx = x; current.ny = y; current.nz = z; current.fields |= 16; return this; }
    }

    private static final class Vertex {
        private float x, y, z, u, v, nx, ny, nz;
        private int red, green, blue, alpha, overlayU, overlayV, lightU, lightV, fields;
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}

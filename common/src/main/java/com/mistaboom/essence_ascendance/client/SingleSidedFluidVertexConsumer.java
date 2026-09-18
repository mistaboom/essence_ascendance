package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.Objects;

/** Filters only the immediately repeated, reversed copy of a fluid quad. The native liquid
 * renderer emits each outward face first and then its inward-facing copy. Keeping the first
 * preserves the exterior texture, geometry, tint, lighting and resource-pack UVs. This is not
 * alpha-zero rendering: omitted faces write neither color nor depth, so glass behind them
 * remains visible. Solid blocks, water and distinct/nonduplicated faces are never filtered.
 *
 * One instance is local to one native tessellation call. No shared mutable mesh state, loader-
 * specific private vertex signature, magic vertex ordinal, texture replacement or shader is
 * required. Buffering is lazy because enclosed fluid blocks frequently emit no vertices.
 * Call finish() after tessellation; the last vertex has no endVertex() callback in 1.21.1. */
public final class SingleSidedFluidVertexConsumer implements VertexConsumer {
    private static final int QUAD_SIZE = 4;
    private static final int POSITION_SIZE = 3;
    private static final int COLOR = 1, UV = 2, OVERLAY = 4, LIGHT = 8, NORMAL = 16;
    private final VertexConsumer delegate;
    private Vertex[] quad;
    private float[] previousPositions;
    private int count;
    private boolean hasPrevious;
    private Vertex current;

    public SingleSidedFluidVertexConsumer(VertexConsumer delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (count == QUAD_SIZE) flush();
        if (quad == null) {
            quad = new Vertex[QUAD_SIZE];
            for (int i = 0; i < QUAD_SIZE; i++) quad[i] = new Vertex();
            previousPositions = new float[QUAD_SIZE * POSITION_SIZE];
        }
        current = quad[count++];
        current.x = x; current.y = y; current.z = z;
        current.fields = 0;
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        Vertex vertex = current();
        vertex.red = red; vertex.green = green; vertex.blue = blue; vertex.alpha = alpha;
        vertex.fields |= COLOR;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        Vertex vertex = current();
        vertex.u = u; vertex.v = v; vertex.fields |= UV;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        Vertex vertex = current();
        vertex.overlayU = u; vertex.overlayV = v; vertex.fields |= OVERLAY;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        Vertex vertex = current();
        vertex.lightU = u; vertex.lightV = v; vertex.fields |= LIGHT;
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        Vertex vertex = current();
        vertex.normalX = x; vertex.normalY = y; vertex.normalZ = z; vertex.fields |= NORMAL;
        return this;
    }

    public void finish() {
        flush();
        hasPrevious = false;
    }

    private Vertex current() {
        if (current == null) throw new IllegalStateException("Fluid vertex attributes require a position first");
        return current;
    }

    private void flush() {
        if (count == 0) return;
        boolean complete = count == QUAD_SIZE;
        if (!complete || !hasPrevious || !reversedCopy()) {
            for (int i = 0; i < count; i++) emit(quad[i]);
            if (complete) {
                for (int i = 0; i < QUAD_SIZE; i++) {
                    int offset = i * POSITION_SIZE;
                    previousPositions[offset] = quad[i].x;
                    previousPositions[offset + 1] = quad[i].y;
                    previousPositions[offset + 2] = quad[i].z;
                }
                hasPrevious = true;
            }
        }
        // A partial tail is forwarded unchanged, not guessed into a complete quad. The native
        // renderer always emits complete quads; this also avoids eating unexpected mod geometry.
        count = 0;
        current = null;
    }

    private boolean reversedCopy() {
        // Same-winding duplicates remain intact, even for a degenerate quad. Reverse order may
        // start at any corner: native top and side backfaces use different starting vertices.
        for (int start = 0; start < QUAD_SIZE; start++) {
            if (matches(start, 1)) return false;
        }
        for (int start = 0; start < QUAD_SIZE; start++) {
            if (matches(start, -1)) return true;
        }
        return false;
    }

    private boolean matches(int start, int step) {
        for (int i = 0; i < QUAD_SIZE; i++) {
            int offset = Math.floorMod(start + step * i, QUAD_SIZE) * POSITION_SIZE;
            Vertex vertex = quad[i];
            if (vertex.x != previousPositions[offset] || vertex.y != previousPositions[offset + 1]
                    || vertex.z != previousPositions[offset + 2]) return false;
        }
        return true;
    }

    private void emit(Vertex vertex) {
        VertexConsumer out = delegate.addVertex(vertex.x, vertex.y, vertex.z);
        if ((vertex.fields & COLOR) != 0) out = out.setColor(vertex.red, vertex.green, vertex.blue, vertex.alpha);
        if ((vertex.fields & UV) != 0) out = out.setUv(vertex.u, vertex.v);
        if ((vertex.fields & OVERLAY) != 0) out = out.setUv1(vertex.overlayU, vertex.overlayV);
        if ((vertex.fields & LIGHT) != 0) out = out.setUv2(vertex.lightU, vertex.lightV);
        if ((vertex.fields & NORMAL) != 0) out.setNormal(vertex.normalX, vertex.normalY, vertex.normalZ);
    }

    private static final class Vertex {
        private float x, y, z, u, v, normalX, normalY, normalZ;
        private int red, green, blue, alpha, overlayU, overlayV, lightU, lightV, fields;
    }
}

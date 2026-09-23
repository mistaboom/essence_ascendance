package com.mistaboom.essence_ascendance.client.transientfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Per-frame resolved anchors and animation values supplied to a world recipe. */
public record WorldVisualRenderContext(
        WorldVisualEvent event,
        ClientLevel level,
        PoseStack pose,
        VertexConsumer planes,
        VertexConsumer lines,
        Vec3 cameraPosition,
        float partialTick,
        double progress
) {
    public double easedProgress() { return ProceduralMotion.smoothStep(progress); }
    public double envelope() { return ProceduralMotion.fadeEnvelope(progress, 7.0, 5.0); }
    public int rgb() { return event.color().rgb(); }
    public Vec3 position() { return event.position().subtract(cameraPosition); }
    public Vec3 secondEndpoint() {
        return event.secondEndpoint() == null ? position() : event.secondEndpoint().subtract(cameraPosition);
    }
    public Vec3 source() { return resolve(event.sourceEntityId(), event.position()); }
    public Vec3 target() { return resolve(event.targetEntityId(), event.secondEndpoint() == null
            ? event.position() : event.secondEndpoint()); }

    /** Entity anchors interpolate render position and follow the center of the current bounds. */
    private Vec3 resolve(int entityId, Vec3 fallback) {
        if (entityId == WorldVisualEvent.NO_ENTITY) return fallback.subtract(cameraPosition);
        Entity entity = level.getEntity(entityId);
        if (entity == null || entity.isRemoved()) return fallback.subtract(cameraPosition);
        double x = entity.xo + (entity.getX() - entity.xo) * partialTick;
        double y = entity.yo + (entity.getY() - entity.yo) * partialTick
                + entity.getBbHeight() * 0.5;
        double z = entity.zo + (entity.getZ() - entity.zo) * partialTick;
        return new Vec3(x, y, z).subtract(cameraPosition);
    }

    /** Deterministic unit interval variation; no render-frame randomness. */
    public double variation(int lane) {
        long value = event.seed() + 0x9E3779B97F4A7C15L * (lane + 1L);
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}

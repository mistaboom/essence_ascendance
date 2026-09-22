package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.utility.UtilitySenseService;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.ProceduralColors;
import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry.billboardRect;
import static com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry.diamond;
import static com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry.diamondRing;
import static com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry.line;

/** Through-terrain presentation for Threat Sense, Hunter's Ledger and Waylight. */
public final class UtilitySenseRenderer {
    private static final ProceduralColors.Colors WISP_COLORS = ProceduralColors.canonicalWisp();
    private static final int[] SIDES = {-1, 1};
    private static final Vec3 X_AXIS = new Vec3(1, 0, 0);
    private static final Vec3 Y_AXIS = new Vec3(0, 1, 0);
    private static final Vec3 Z_AXIS = new Vec3(0, 0, 1);
    private static final RenderType SEE_THROUGH_LINES = ProceduralRenderTypes.PERCEPTION_LINES;
    private static final RenderType SEE_THROUGH_FILLS = ProceduralRenderTypes.PERCEPTION_PLANES;

    private UtilitySenseRenderer() { }

    public static void render(Camera camera, Matrix4f positionMatrix) {
        UtilitySenseService.Mode mode = UtilitySenseClientState.mode();
        if (mode == UtilitySenseService.Mode.NONE) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        PoseStack pose = new PoseStack();
        pose.mulPose(positionMatrix);
        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        if (mode == UtilitySenseService.Mode.THREAT) {
            VertexConsumer lines = buffers.getBuffer(SEE_THROUGH_LINES);
            renderThreatSenseLines(minecraft, cameraPos, pose, lines);
            buffers.endBatch(SEE_THROUGH_LINES);

            if (UtilitySenseClientState.ledger()) {
                VertexConsumer fills = buffers.getBuffer(SEE_THROUGH_FILLS);
                renderLedgerReadouts(minecraft, camera, cameraPos, pose, fills);
                buffers.endBatch(SEE_THROUGH_FILLS);
            }
        } else if (mode == UtilitySenseService.Mode.WAYLIGHT) {
            VertexConsumer fills = buffers.getBuffer(SEE_THROUGH_FILLS);
            renderWaylightFills(camera, cameraPos, pose, fills);
            buffers.endBatch(SEE_THROUGH_FILLS);

            VertexConsumer lines = buffers.getBuffer(SEE_THROUGH_LINES);
            renderWaylightLines(camera, cameraPos, pose, lines);
            buffers.endBatch(SEE_THROUGH_LINES);
        }
    }

    private static void renderThreatSenseLines(Minecraft minecraft, Vec3 cameraPos, PoseStack pose,
                                               VertexConsumer lines) {
        int utility = AscendancePalette.UTILITY;
        int memory = AscendancePalette.TRANSCENDENT.metalRgb();

        for (UtilitySenseService.Threat threat : UtilitySenseClientState.threats()) {
            Entity entity = minecraft.level.getEntity(threat.entityId());
            if (entity == null || entity.isRemoved()) continue;
            float alpha = threat.active() ? 0.96F : 0.50F;
            AABB box = entity.getBoundingBox().inflate(0.035).move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            cornerBrackets(pose, lines, box, threat.active() ? utility : memory, alpha);
        }

        for (UtilitySenseService.ProjectilePath path : UtilitySenseClientState.projectilePaths()) {
            for (int i = 1; i < path.points().size(); i++) {
                boolean observed = i < path.historyPoints();
                int rgb = observed ? memory : utility;
                float alpha = observed ? 0.34F : 0.95F;
                line(pose, lines, path.points().get(i - 1).subtract(cameraPos),
                        path.points().get(i).subtract(cameraPos), rgb, alpha);
            }
            if (path.historyPoints() < path.points().size()) {
                Vec3 end = path.points().get(path.points().size() - 1).subtract(cameraPos);
                crossMarker(pose, lines, end, 0.10, utility, 0.78F);
            }
        }

        for (UtilitySenseService.ExplosionDanger explosion : UtilitySenseClientState.explosions()) {
            renderSphere(pose, lines, explosion.center().subtract(cameraPos), explosion.radius(), utility, 0.62F);
        }

    }

    private static void renderWaylightFills(Camera camera, Vec3 cameraPos, PoseStack pose,
                                            VertexConsumer fills) {
        Vec3 wispWorld = UtilitySenseClientState.wispPosition(camera.getPartialTickTime());
        if (wispWorld != null) {
            renderWispFills(camera, pose, fills, wispWorld.subtract(cameraPos));
        }
    }

    private static void renderWaylightLines(Camera camera, Vec3 cameraPos, PoseStack pose,
                                            VertexConsumer lines) {
        UtilitySenseService.WaylightMarker marker = UtilitySenseClientState.waylight();
        if (marker != null) {
            Vec3 center = new Vec3(marker.feet().getX() + 0.5, marker.feet().getY() + 0.025,
                    marker.feet().getZ() + 0.5).subtract(cameraPos);
            spawnFootingMarker(pose, lines, center);
        }

        Vec3 wispWorld = UtilitySenseClientState.wispPosition(camera.getPartialTickTime());
        if (wispWorld != null) {
            renderWispLines(camera, pose, lines, wispWorld.subtract(cameraPos));
        }
    }

    private static void renderLedgerReadouts(Minecraft minecraft, Camera camera, Vec3 cameraPos,
                                             PoseStack pose, VertexConsumer fills) {
        Vec3 right = cameraRight(camera);
        Vec3 up = cameraUp(camera);
        for (UtilitySenseService.Threat threat : UtilitySenseClientState.threats()) {
            Entity entity = minecraft.level.getEntity(threat.entityId());
            if (entity == null || entity.isRemoved()) continue;

            float visibility = threat.active() ? 1.0F : 0.62F;
            Vec3 center = entity.position().add(0, entity.getBbHeight() + 0.36, 0).subtract(cameraPos);
            renderStatusRibbon(pose, fills, center, right, up, threat, visibility);
        }
    }

    /**
     * Compact Hunter's Ledger readout. Health and armor are adjacent, non-overlapping
     * billboard geometry rather than stacked translucent quads, avoiding angle-dependent
     * translucent sorting while keeping the readout light enough to scan in combat.
     */
    private static void renderStatusRibbon(PoseStack pose, VertexConsumer fills, Vec3 center, Vec3 right, Vec3 up,
                                           UtilitySenseService.Threat threat, float visibility) {
        int accent = threat.active() ? AscendancePalette.UTILITY : AscendancePalette.TRANSCENDENT.metalRgb();
        int background = AscendancePalette.TRANSCENDENT.primaryRgb();

        double healthFraction = threat.maxHealth() <= 0 ? 0.0
                : Math.clamp(threat.health() / threat.maxHealth(), 0.0F, 1.0F);
        renderSplitRibbon(pose, fills, center, right, up,
                0.74, 0.052, healthFraction,
                AscendancePalette.VITALITY, background, 0.94F * visibility);
        renderRibbonCaps(pose, fills, center, right, up, 0.74, 0.052,
                accent, 0.82F * visibility);

        Vec3 armorCenter = center.add(up.scale(-0.075));
        double armorFraction = Math.clamp(threat.armor() / 20.0, 0.0, 1.0);
        renderSplitRibbon(pose, fills, armorCenter, right, up,
                0.54, 0.024, armorFraction,
                AscendancePalette.DEFENSE, background, 0.88F * visibility);
        renderRibbonCaps(pose, fills, armorCenter, right, up, 0.54, 0.024,
                accent, 0.68F * visibility);
    }

    private static void renderSplitRibbon(PoseStack pose, VertexConsumer fills, Vec3 center, Vec3 right, Vec3 up,
                                          double width, double height, double fraction,
                                          int filledRgb, int emptyRgb, float alpha) {
        double resolved = Math.clamp(fraction, 0.0, 1.0);
        double filledWidth = width * resolved;
        double emptyWidth = width - filledWidth;

        if (filledWidth > 0.001) {
            Vec3 filledCenter = center.add(right.scale((filledWidth - width) * 0.5));
            billboardRect(pose, fills, filledCenter, right, up, filledWidth, height, filledRgb, alpha);
        }
        if (emptyWidth > 0.001) {
            Vec3 emptyCenter = center.add(right.scale((width - emptyWidth) * 0.5));
            billboardRect(pose, fills, emptyCenter, right, up, emptyWidth, height, emptyRgb, alpha * 0.72F);
        }
    }

    private static void renderRibbonCaps(PoseStack pose, VertexConsumer fills, Vec3 center, Vec3 right, Vec3 up,
                                         double width, double height, int rgb, float alpha) {
        double capWidth = Math.max(0.012, height * 0.30);
        double offset = width * 0.5 + capWidth * 0.8;
        billboardRect(pose, fills, center.add(right.scale(-offset)), right, up,
                capWidth, height * 1.12, rgb, alpha);
        billboardRect(pose, fills, center.add(right.scale(offset)), right, up,
                capWidth, height * 1.12, rgb, alpha);
    }

    private static void renderWispFills(Camera camera, PoseStack pose, VertexConsumer fills, Vec3 center) {
        Vec3 right = cameraRight(camera);
        Vec3 up = cameraUp(camera);
        long age = UtilitySenseClientState.wispAge();
        double pulse = 1.0 + ProceduralMotion.oscillate(age * 0.22, 0.10);
        double flap = ProceduralMotion.oscillate(age * 0.34, 0.035);
        int utility = WISP_COLORS.shell();
        int core = WISP_COLORS.core();

        // Concentric, non-overlapping diamond bands preserve the layered glow without
        // stacking coplanar translucent faces. The old stacked diamonds could flicker
        // between layers as the camera angle changed.
        double outerHalfWidth = 0.27 * pulse;
        double outerHalfHeight = 0.31 * pulse;
        double bodyHalfWidth = 0.15 * pulse;
        double bodyHalfHeight = 0.19 * pulse;
        double coreHalfWidth = 0.075 * pulse;
        double coreHalfHeight = 0.095 * pulse;
        diamondRing(pose, fills, center, right, up,
                outerHalfWidth, outerHalfHeight, bodyHalfWidth, bodyHalfHeight, utility, 0.12F);
        diamondRing(pose, fills, center, right, up,
                bodyHalfWidth, bodyHalfHeight, coreHalfWidth, coreHalfHeight, utility, 0.72F);
        diamond(pose, fills, center, right, up,
                coreHalfWidth, coreHalfHeight, core, 0.98F);

        Vec3 leftWing = center.add(right.scale(-0.17 - flap)).add(up.scale(0.015));
        Vec3 rightWing = center.add(right.scale(0.17 + flap)).add(up.scale(0.015));
        diamond(pose, fills, leftWing, right, up, 0.095, 0.13 + Math.abs(flap), utility, 0.48F);
        diamond(pose, fills, rightWing, right, up, 0.095, 0.13 + Math.abs(flap), utility, 0.48F);

        Vec3 velocity = UtilitySenseClientState.wispVelocity();
        Vec3 trail = velocity.lengthSqr() > 1.0E-5 ? velocity.normalize().scale(-1) : up.scale(-1);
        for (int i = 1; i <= 3; i++) {
            Vec3 mote = center.add(trail.scale(0.12 * i)).add(up.scale(-0.035 * i));
            double size = 0.055 - i * 0.009;
            diamond(pose, fills, mote, right, up, size, size * 1.2, utility, 0.40F / i);
        }
    }

    private static void renderWispLines(Camera camera, PoseStack pose, VertexConsumer lines, Vec3 center) {
        Vec3 right = cameraRight(camera);
        Vec3 up = cameraUp(camera);
        int core = AscendancePalette.TRANSCENDENT.metalRgb();

        // Tiny antennae give the procedural glow a creature-like silhouette.
        Vec3 crown = center.add(up.scale(0.12));
        line(pose, lines, crown, crown.add(up.scale(0.11)).add(right.scale(-0.055)), core, 0.86F);
        line(pose, lines, crown, crown.add(up.scale(0.11)).add(right.scale(0.055)), core, 0.86F);
    }

    private static void spawnFootingMarker(PoseStack pose, VertexConsumer lines, Vec3 center) {
        int utility = AscendancePalette.UTILITY;
        int core = AscendancePalette.TRANSCENDENT.metalRgb();
        double half = 0.43;
        double corner = 0.18;
        for (int sx : SIDES) {
            for (int sz : SIDES) {
                Vec3 cornerPoint = center.add(sx * half, 0, sz * half);
                line(pose, lines, cornerPoint, cornerPoint.add(-sx * corner, 0, 0), utility, 0.88F);
                line(pose, lines, cornerPoint, cornerPoint.add(0, 0, -sz * corner), utility, 0.88F);
            }
        }
        crossMarker(pose, lines, center.add(0, 0.015, 0), 0.09, core, 0.92F);
    }

    private static void cornerBrackets(PoseStack pose, VertexConsumer lines, AABB box, int rgb, float alpha) {
        double length = Math.min(0.24, Math.max(0.09,
                Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())) * 0.28));
        for (int ix = 0; ix < 2; ix++) {
            for (int iy = 0; iy < 2; iy++) {
                for (int iz = 0; iz < 2; iz++) {
                    double x = ix == 0 ? box.minX : box.maxX;
                    double y = iy == 0 ? box.minY : box.maxY;
                    double z = iz == 0 ? box.minZ : box.maxZ;
                    double sx = ix == 0 ? 1 : -1;
                    double sy = iy == 0 ? 1 : -1;
                    double sz = iz == 0 ? 1 : -1;
                    Vec3 corner = new Vec3(x, y, z);
                    line(pose, lines, corner, corner.add(sx * length, 0, 0), rgb, alpha);
                    line(pose, lines, corner, corner.add(0, sy * length, 0), rgb, alpha);
                    line(pose, lines, corner, corner.add(0, 0, sz * length), rgb, alpha);
                }
            }
        }
    }

    private static void renderSphere(PoseStack pose, VertexConsumer lines, Vec3 center,
                                     double radius, int rgb, float alpha) {
        ProceduralGeometry.ring(pose, lines, center, X_AXIS, Y_AXIS, radius, 24, rgb, alpha);
        ProceduralGeometry.ring(pose, lines, center, X_AXIS, Z_AXIS, radius, 24, rgb, alpha);
        ProceduralGeometry.ring(pose, lines, center, Y_AXIS, Z_AXIS, radius, 24, rgb, alpha);
    }

    private static void crossMarker(PoseStack pose, VertexConsumer lines, Vec3 center,
                                    double radius, int rgb, float alpha) {
        line(pose, lines, center.add(-radius, 0, 0), center.add(radius, 0, 0), rgb, alpha);
        line(pose, lines, center.add(0, -radius, 0), center.add(0, radius, 0), rgb, alpha);
        line(pose, lines, center.add(0, 0, -radius), center.add(0, 0, radius), rgb, alpha);
    }

    private static Vec3 cameraRight(Camera camera) {
        Vector3f left = camera.getLeftVector();
        return new Vec3(-left.x(), -left.y(), -left.z());
    }

    private static Vec3 cameraUp(Camera camera) {
        Vector3f up = camera.getUpVector();
        return new Vec3(up.x(), up.y(), up.z());
    }

}

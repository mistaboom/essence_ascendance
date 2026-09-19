package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mistaboom.essence_ascendance.utility.UtilitySenseService;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Through-terrain presentation for Threat Sense, Hunter's Ledger and Waylight. */
public final class UtilitySenseRenderer {
    private static final RenderType SEE_THROUGH_LINES = new RenderType(
            "essence_ascendance_utility_sense_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            RenderType.TRANSIENT_BUFFER_SIZE,
            false,
            true,
            () -> {
                RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableDepthTest();
                RenderSystem.disableCull();
                RenderSystem.depthMask(false);
            },
            () -> {
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
            }
    ) { };

    private static final RenderType SEE_THROUGH_FILLS = new RenderType(
            "essence_ascendance_utility_sense_fills",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            RenderType.TRANSIENT_BUFFER_SIZE,
            false,
            true,
            () -> {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableDepthTest();
                RenderSystem.disableCull();
                RenderSystem.depthMask(false);
            },
            () -> {
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
            }
    ) { };

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
        double pulse = 1.0 + Math.sin(age * 0.22) * 0.10;
        double flap = Math.sin(age * 0.34) * 0.035;
        int utility = AscendancePalette.UTILITY;
        int core = AscendancePalette.TRANSCENDENT.metalRgb();

        // Concentric, non-overlapping diamond bands preserve the layered glow without
        // stacking coplanar translucent faces. The old stacked diamonds could flicker
        // between layers as the camera angle changed.
        double outerHalfWidth = 0.27 * pulse;
        double outerHalfHeight = 0.31 * pulse;
        double bodyHalfWidth = 0.15 * pulse;
        double bodyHalfHeight = 0.19 * pulse;
        double coreHalfWidth = 0.075 * pulse;
        double coreHalfHeight = 0.095 * pulse;
        billboardDiamondRing(pose, fills, center, right, up,
                outerHalfWidth, outerHalfHeight, bodyHalfWidth, bodyHalfHeight, utility, 0.12F);
        billboardDiamondRing(pose, fills, center, right, up,
                bodyHalfWidth, bodyHalfHeight, coreHalfWidth, coreHalfHeight, utility, 0.72F);
        billboardDiamond(pose, fills, center, right, up,
                coreHalfWidth, coreHalfHeight, core, 0.98F);

        Vec3 leftWing = center.add(right.scale(-0.17 - flap)).add(up.scale(0.015));
        Vec3 rightWing = center.add(right.scale(0.17 + flap)).add(up.scale(0.015));
        billboardDiamond(pose, fills, leftWing, right, up, 0.095, 0.13 + Math.abs(flap), utility, 0.48F);
        billboardDiamond(pose, fills, rightWing, right, up, 0.095, 0.13 + Math.abs(flap), utility, 0.48F);

        Vec3 velocity = UtilitySenseClientState.wispVelocity();
        Vec3 trail = velocity.lengthSqr() > 1.0E-5 ? velocity.normalize().scale(-1) : up.scale(-1);
        for (int i = 1; i <= 3; i++) {
            Vec3 mote = center.add(trail.scale(0.12 * i)).add(up.scale(-0.035 * i));
            double size = 0.055 - i * 0.009;
            billboardDiamond(pose, fills, mote, right, up, size, size * 1.2, utility, 0.40F / i);
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
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
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
        int segments = 24;
        for (int plane = 0; plane < 3; plane++) {
            Vec3 previous = circlePoint(center, radius, plane, 0);
            for (int i = 1; i <= segments; i++) {
                double angle = Math.PI * 2.0 * i / segments;
                Vec3 next = circlePoint(center, radius, plane, angle);
                line(pose, lines, previous, next, rgb, alpha);
                previous = next;
            }
        }
    }

    private static Vec3 circlePoint(Vec3 center, double radius, int plane, double angle) {
        double a = Math.cos(angle) * radius;
        double b = Math.sin(angle) * radius;
        return switch (plane) {
            case 0 -> center.add(a, b, 0);
            case 1 -> center.add(a, 0, b);
            default -> center.add(0, a, b);
        };
    }

    private static void crossMarker(PoseStack pose, VertexConsumer lines, Vec3 center,
                                    double radius, int rgb, float alpha) {
        line(pose, lines, center.add(-radius, 0, 0), center.add(radius, 0, 0), rgb, alpha);
        line(pose, lines, center.add(0, -radius, 0), center.add(0, radius, 0), rgb, alpha);
        line(pose, lines, center.add(0, 0, -radius), center.add(0, 0, radius), rgb, alpha);
    }

    private static void billboardRect(PoseStack pose, VertexConsumer fills, Vec3 center, Vec3 right, Vec3 up,
                                      double width, double height, int rgb, float alpha) {
        Vec3 horizontal = right.scale(width * 0.5);
        Vec3 vertical = up.scale(height * 0.5);
        quad(pose, fills,
                center.subtract(horizontal).subtract(vertical),
                center.add(horizontal).subtract(vertical),
                center.add(horizontal).add(vertical),
                center.subtract(horizontal).add(vertical), rgb, alpha);
    }

    private static void billboardDiamond(PoseStack pose, VertexConsumer fills, Vec3 center, Vec3 right, Vec3 up,
                                         double halfWidth, double halfHeight, int rgb, float alpha) {
        quad(pose, fills,
                center.add(up.scale(halfHeight)),
                center.add(right.scale(halfWidth)),
                center.add(up.scale(-halfHeight)),
                center.add(right.scale(-halfWidth)), rgb, alpha);
    }

    private static void billboardDiamondRing(PoseStack pose, VertexConsumer fills, Vec3 center, Vec3 right, Vec3 up,
                                             double outerHalfWidth, double outerHalfHeight,
                                             double innerHalfWidth, double innerHalfHeight,
                                             int rgb, float alpha) {
        Vec3 outerTop = center.add(up.scale(outerHalfHeight));
        Vec3 outerRight = center.add(right.scale(outerHalfWidth));
        Vec3 outerBottom = center.add(up.scale(-outerHalfHeight));
        Vec3 outerLeft = center.add(right.scale(-outerHalfWidth));
        Vec3 innerTop = center.add(up.scale(innerHalfHeight));
        Vec3 innerRight = center.add(right.scale(innerHalfWidth));
        Vec3 innerBottom = center.add(up.scale(-innerHalfHeight));
        Vec3 innerLeft = center.add(right.scale(-innerHalfWidth));

        quad(pose, fills, outerTop, outerRight, innerRight, innerTop, rgb, alpha);
        quad(pose, fills, outerRight, outerBottom, innerBottom, innerRight, rgb, alpha);
        quad(pose, fills, outerBottom, outerLeft, innerLeft, innerBottom, rgb, alpha);
        quad(pose, fills, outerLeft, outerTop, innerTop, innerLeft, rgb, alpha);
    }

    private static void quad(PoseStack pose, VertexConsumer fills, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             int rgb, float alpha) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int opacity = Math.clamp(Math.round(alpha * 255.0F), 0, 255);
        fills.addVertex(pose.last().pose(), (float) a.x, (float) a.y, (float) a.z).setColor(red, green, blue, opacity);
        fills.addVertex(pose.last().pose(), (float) b.x, (float) b.y, (float) b.z).setColor(red, green, blue, opacity);
        fills.addVertex(pose.last().pose(), (float) c.x, (float) c.y, (float) c.z).setColor(red, green, blue, opacity);
        fills.addVertex(pose.last().pose(), (float) d.x, (float) d.y, (float) d.z).setColor(red, green, blue, opacity);
    }

    private static Vec3 cameraRight(Camera camera) {
        Vector3f left = camera.getLeftVector();
        return new Vec3(-left.x(), -left.y(), -left.z());
    }

    private static Vec3 cameraUp(Camera camera) {
        Vector3f up = camera.getUpVector();
        return new Vec3(up.x(), up.y(), up.z());
    }

    private static void line(PoseStack pose, VertexConsumer lines, Vec3 start, Vec3 end, int rgb, float alpha) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() <= Math.ulp(1.0)) return;
        Vec3 normal = direction.normalize();
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int a = Math.clamp(Math.round(alpha * 255.0F), 0, 255);
        lines.addVertex(pose.last().pose(), (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, a)
                .setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
        lines.addVertex(pose.last().pose(), (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, a)
                .setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }

}

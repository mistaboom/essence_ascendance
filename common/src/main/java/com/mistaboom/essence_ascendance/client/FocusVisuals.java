package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.pylon.PylonLocalFrame;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** One physical Focus mesh and one context-driven presentation for all hosts. */
public final class FocusVisuals {
    private static final BlockbenchStaticMesh GEM = new BlockbenchStaticMesh(
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "meshes/focus.eamesh"),
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "textures/item/focus.png"));
    private static final Vec3 TILTED = new Vec3(0, 0.56, 0.83);
    private static final double TAU = Math.PI * 2;
    private static final Ornament LATENT_ORNAMENT = new Ornament(0.58, 0, 0, false, 0);
    private static final Ornament DORMANT_ORNAMENT = new Ornament(0.43, 0, 0, false, 1);
    private static final Ornament AWAKENED_ORNAMENT = new Ornament(0.31, TAU * 0.24, 2, false, 2);
    private static final Ornament RESONANT_ORNAMENT = new Ornament(0.20, TAU * 0.48, 4, true, 3);
    private static final Ornament ASCENDANT_ORNAMENT = new Ornament(0.10, TAU * 0.74, 6, true, 4);
    private static final Ornament TRANSCENDENT_ORNAMENT = new Ornament(0.012, TAU, 8, true, 5);

    private FocusVisuals() { }

    public enum Host { GENERIC, PYLON, INFUSER }

    /** Anchor is block-local for installed Foci; yaw is in world degrees. */
    public record Context(Host host, boolean installed, boolean energized, EssenceFocusTier tier,
                          boolean active, long ratePerSecond,
                          double x, double y, double z, float yawDegrees,
                          Direction axisDirection) {
        public static Context generic(EssenceFocusTier tier) {
            return new Context(Host.GENERIC, true, true, tier, false, 0,
                    0.5, 1.5, 0.5, 0, Direction.UP);
        }

        public static Context installed(MachineVisualState.Focus focus, boolean linked) {
            return installed(focus, linked, Direction.UP);
        }

        public static Context installed(MachineVisualState.Focus focus, boolean linked,
                                        Direction axisDirection) {
            Host host = focus.host() == MachineVisualState.Host.PYLON ? Host.PYLON : Host.INFUSER;
            return new Context(host, focus.installed(), linked, focus.tier(), focus.active(), focus.ratePerSecond(),
                    0.5, 1.5, 0.5, 0, axisDirection);
        }

        public PylonLocalFrame frame() {
            return PylonLocalFrame.of(axisDirection);
        }
    }

    public static int color(EssenceFocusTier tier) {
        if (tier == null) return AscendancePalette.LATENT.metalRgb();
        return switch (tier) {
            case DORMANT -> AscendancePalette.DORMANT.metalRgb();
            case AWAKENED -> AscendancePalette.AWAKENED.metalRgb();
            case RESONANT -> AscendancePalette.RESONANT.metalRgb();
            case ASCENDANT -> AscendancePalette.ASCENDANT.metalRgb();
            case TRANSCENDENT -> AscendancePalette.TRANSCENDENT.metalRgb();
        };
    }

    public static void renderItem(ItemStack stack, ItemDisplayContext displayContext,
                                  PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!EssenceFocusData.isFocusItem(stack)) return;
        Context context = Context.generic(EssenceFocusData.tier(stack));
        pose.pushPose();
        // The asset uses its installed block-space center at y=1.5.
        pose.translate(0, -1, 0);
        renderGem(context, pose, buffers, light, overlay, 0);
        pose.popPose();
    }

    public static void renderInstalled(Context context, Level level, BlockPos pos, float partialTick,
                                       PoseStack pose, MultiBufferSource buffers, int overlay) {
        if (!context.installed()) return;
        double age = level.getGameTime() + partialTick;
        int light = LevelRenderer.getLightColor(level, pos.relative(context.axisDirection()));
        renderGem(context, pose, buffers, light, overlay, age);
        // An installed gem remains a visible physical component, but its magical
        // ornament does not energize until the host has a machine link.
        if (!context.energized()) return;

        // MachineWorldVisualRenderer submits this after the opaque world so normal
        // depth testing can resolve the halo without transparent depth writes.
        double activity = context.active() ? Math.min(1.0, Math.log1p(context.ratePerSecond()) / 16.0) : 0.0;
        int tierLevel = context.tier() == null ? 0 : context.tier().ordinal() + 1;
        double refinement = tierLevel / 5.0;
        double pulse = ProceduralMotion.oscillate(age * (0.07 + activity * 0.06), 0.018);
        double radius = 0.27 + refinement * 0.07 + pulse;
        PylonLocalFrame frame = context.frame();
        Vec3 localAxis = frame.axis();
        Vec3 localRight = frame.right();
        Vec3 localForward = frame.forward();
        Vec3 center = center(context, age).add(localAxis.scale(-0.015));
        int rgb = color(context.tier());
        float opacity = (float) (0.18 + refinement * 0.28 + (context.active() ? 0.18 : 0.0));
        Ornament ornament = ornament(context.tier());
        double direction = context.host() == Host.INFUSER ? -1 : 1;
        double phase = age * direction * 0.018 + Math.toRadians(context.yawDegrees());
        renderLuminousShapes(context, pose, buffers, center, age, phase, ornament, rgb);

        VertexConsumer lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        ProceduralGeometry.brokenRing(pose, lines,
                center, localRight, localForward, radius,
                phase, TAU, 3, 13, ornament.gap(), rgb, opacity);

        if (ornament.secondarySweep() > 0) {
            ProceduralGeometry.arc(pose, lines, center, localRight,
                    frame.localVectorToWorld(TILTED),
                    radius * 0.91, radius * 0.91, -phase * 0.8,
                    ornament.secondarySweep(), 27, rgb, opacity * 0.52F);
        }
        if (ornament.ticks() > 0) {
            for (int index = 0; index < ornament.ticks(); index++) {
                double angle = ProceduralMotion.orbitAngle(index, ornament.ticks(), phase * 0.35);
                Vec3 inner = ProceduralGeometry.orbitPoint(center, localRight, localForward,
                        radius + 0.014, radius + 0.014, angle);
                Vec3 outer = ProceduralGeometry.orbitPoint(center, localRight, localForward,
                        radius + 0.037, radius + 0.037, angle);
                ProceduralGeometry.line(pose, lines, inner, outer, rgb, opacity * 0.72F);
            }
        }
        if (ornament.satellite() || context.active() && tierLevel >= 2) {
            double angle = phase - age * 0.042;
            Vec3 orbit = ProceduralGeometry.orbitPoint(
                    center, localRight, localForward, radius, radius, angle);
            double s = 0.018 + (context.tier() == null ? 0 : context.tier().ordinal() * 0.002);
            ProceduralGeometry.line(pose, lines, orbit.subtract(localRight.scale(s)),
                    orbit.add(localRight.scale(s)), rgb, 0.78F);
            ProceduralGeometry.line(pose, lines, orbit.subtract(localAxis.scale(s)),
                    orbit.add(localAxis.scale(s)), rgb, 0.78F);
        }
    }

    /** Increasing completeness is the main tier cue; new structure is introduced sparingly. */
    private static Ornament ornament(EssenceFocusTier tier) {
        if (tier == null) return LATENT_ORNAMENT;
        return switch (tier) {
            case DORMANT -> DORMANT_ORNAMENT;
            case AWAKENED -> AWAKENED_ORNAMENT;
            case RESONANT -> RESONANT_ORNAMENT;
            case ASCENDANT -> ASCENDANT_ORNAMENT;
            case TRANSCENDENT -> TRANSCENDENT_ORNAMENT;
        };
    }

    private record Ornament(double gap, double secondarySweep, int ticks,
                            boolean satellite, int shards) { }

    /** Broad readable forms surround the solid gem; linework remains a close-view detail. */
    private static void renderLuminousShapes(Context context, PoseStack pose, MultiBufferSource buffers,
                                             Vec3 center, double age, double phase,
                                             Ornament ornament, int rgb) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f cameraUp = camera.getUpVector();
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z());
        Vec3 up = new Vec3(cameraUp.x(), cameraUp.y(), cameraUp.z());
        VertexConsumer planes = buffers.getBuffer(ProceduralRenderTypes.WORLD_PLANES);
        int luminous = luminousColor(rgb);
        double pulse = 1.0 + ProceduralMotion.oscillate(age * 0.09, context.active() ? 0.035 : 0.018);
        int tier = context.tier() == null ? 0 : context.tier().ordinal() + 1;
        PylonLocalFrame frame = context.frame();
        double refinement = tier / 5.0;
        double outerWidth = (0.33 + refinement * 0.10) * pulse;
        double outerHeight = (0.39 + refinement * 0.10) * pulse;
        double bodyWidth = (0.28 + refinement * 0.06) * pulse;
        double bodyHeight = (0.34 + refinement * 0.07) * pulse;
        double innerWidth = (0.245 + refinement * 0.025) * pulse;
        double innerHeight = (0.31 + refinement * 0.035) * pulse;
        float innerOpacity = (float) (0.23 + refinement * 0.32
                + (context.active() ? 0.10 : 0.0));

        // Non-overlapping bands leave the mesh visible in the center.
        if (tier >= 2) {
            ProceduralGeometry.diamondRing(pose, planes, center, right, up,
                    outerWidth, outerHeight, bodyWidth, bodyHeight, rgb,
                    (float) (0.08 + refinement * 0.18 + (context.active() ? 0.05 : 0.0)));
        }
        ProceduralGeometry.diamondRing(pose, planes, center, right, up,
                bodyWidth, bodyHeight, innerWidth, innerHeight, luminous,
                innerOpacity);

        for (int index = 0; index < ornament.shards(); index++) {
            double angle = ProceduralMotion.orbitAngle(index, ornament.shards(), phase + age * 0.012);
            Vec3 shard = ProceduralGeometry.orbitPoint(center, frame.right(), frame.forward(), outerWidth + 0.055,
                    outerWidth + 0.055, angle)
                    .add(frame.axis().scale(
                            ProceduralMotion.oscillate(age * 0.12 + index * 2.0, 0.045)));
            ProceduralGeometry.diamondRing(pose, planes, shard, right, up,
                    0.070, 0.105, 0.044, 0.071, rgb,
                    (float) (0.12 + refinement * 0.18));
            ProceduralGeometry.diamond(pose, planes, shard, right, up,
                    0.044, 0.071, luminous,
                    (float) (0.42 + refinement * 0.38 + (context.active() ? 0.10 : 0.0)));
        }
    }

    private static int luminousColor(int rgb) {
        int r = ((rgb >> 16 & 255) * 3 + 255) / 4;
        int g = ((rgb >> 8 & 255) * 3 + 255) / 4;
        int b = ((rgb & 255) * 3 + 255) / 4;
        return r << 16 | g << 8 | b;
    }

    private static void renderGem(Context context, PoseStack pose, MultiBufferSource buffers,
                                  int light, int overlay, double age) {
        pose.pushPose();
        Vec3 center = center(context, age);
        pose.translate(center.x, center.y, center.z);
        PylonRenderTransform.applyRotation(pose, context.axisDirection());
        float rotation = context.yawDegrees() + (float) (age * (context.host() == Host.INFUSER
                ? (context.active() ? -2.4 : -1.4) : (context.active() ? 2.2 : 1.25)));
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        double breath = context.host() == Host.GENERIC ? 1.0
                : 1.0 + ProceduralMotion.oscillate(age * 0.09, context.active() ? 0.035 : 0.018);
        float scale = (float) ((1.0 + (context.tier() == null ? 0 : context.tier().ordinal() * 0.012)) * breath);
        pose.scale(scale, scale, scale);
        pose.translate(-0.5, -1.5, -0.5);
        int rgb = color(context.tier());
        GEM.render(pose, buffers, luminousLight(light), overlay, rgb, 255, false);
        // The lit base keeps normal-based facets; this brighter color is a contained glow.
        int emission = context.tier() == null ? 66 : 76 + context.tier().ordinal() * 9;
        if (context.active()) emission += 12;
        GEM.render(pose, buffers, 0xF000F0, overlay, luminousColor(rgb), emission, true);
        pose.popPose();
    }

    private static int luminousLight(int packedLight) {
        int block = Math.max(packedLight & 0xFF, 12 << 4);
        int sky = Math.max((packedLight >>> 16) & 0xFF, 9 << 4);
        return (packedLight & 0xFF00FF00) | block | sky << 16;
    }

    /** Actual animated gem center in host-local coordinates, shared with attached beams. */
    public static Vec3 center(Context context, double age) {
        return context.frame().localToBlock(
                new Vec3(context.x(), context.y() + hover(context, age), context.z()));
    }

    private static double hover(Context context, double age) {
        if (context.host() == Host.GENERIC) return 0;
        return ProceduralMotion.oscillate(age * (context.host() == Host.INFUSER ? 0.11 : 0.085),
                context.active() ? 0.038 : 0.022);
    }
}

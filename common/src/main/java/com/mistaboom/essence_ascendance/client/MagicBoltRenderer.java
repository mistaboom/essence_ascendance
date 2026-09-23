package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.projectile.MagicBoltEntity;
import com.mistaboom.essence_ascendance.projectile.ProjectileFlightTraceAccess;
import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Depth-tested, textureless Caster bolt with a strong core and distance-gated fine structure. */
public final class MagicBoltRenderer extends EntityRenderer<MagicBoltEntity> {
    private static final int SHELL = SemanticVisualColor.MAGIC.rgb();
    private static final int CORE = 0xFFF4CB;
    private static final double DETAIL_DISTANCE_SQUARED = 28.0 * 28.0;

    public MagicBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
        shadowStrength = 0.0F;
    }

    @Override protected int getBlockLightLevel(MagicBoltEntity entity, BlockPos position) {
        return 15;
    }

    @Override public void render(MagicBoltEntity bolt, float yaw, float partialTick,
                                 PoseStack pose, MultiBufferSource buffers, int packedLight) {
        Vec3 velocity = bolt.getDeltaMovement();
        Vec3 forward = velocity.lengthSqr() > 1.0E-8 ? velocity.normalize() : new Vec3(0, 0, 1);
        Basis basis = Basis.around(forward);
        double time = bolt.tickCount + partialTick;
        double pulse = 1.0 + Math.sin(time * 0.72) * 0.07;
        Vec3 center = Vec3.ZERO;

        var planes = buffers.getBuffer(ProceduralRenderTypes.WORLD_PLANES);
        ProceduralGeometry.diamond(pose, planes, center, basis.a(), forward,
                0.115 * pulse, 0.245 * pulse, SHELL, 0.72F);
        ProceduralGeometry.diamond(pose, planes, center, basis.b(), forward,
                0.095 * pulse, 0.225 * pulse, brighten(SHELL, 0.34), 0.58F);
        ProceduralGeometry.diamond(pose, planes, center, basis.a(), forward,
                0.052 * pulse, 0.155 * pulse, CORE, 0.96F);
        ProceduralGeometry.diamondRing(pose, planes, center, basis.a(), basis.b(),
                0.165 * pulse, 0.165 * pulse, 0.105 * pulse, 0.105 * pulse,
                brighten(SHELL, 0.52), 0.42F);

        double trailLength = Math.clamp(0.42 + velocity.length() * 0.12, 0.48, 0.92);
        Vec3 tail = forward.scale(-trailLength);
        ProceduralGeometry.taperedPlane(pose, planes, tail, forward.scale(-0.07), basis.a(),
                0.012, 0.050, SHELL, 0.34F);
        ProceduralGeometry.taperedPlane(pose, planes, tail.scale(0.82), forward.scale(-0.04), basis.b(),
                0.008, 0.034, brighten(SHELL, 0.36), 0.25F);

        var lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        ProceduralGeometry.line(pose, lines, forward.scale(-0.24), forward.scale(0.25), CORE, 0.90F);
        ProceduralGeometry.brokenRing(pose, lines, center, basis.a(), basis.b(),
                0.18, time * 0.19, Math.PI * 2.0, 6, 2, 0.32,
                brighten(SHELL, 0.48), 0.54F);

        if (entityRenderDispatcher.distanceToSqr(bolt) <= DETAIL_DISTANCE_SQUARED) {
            ProceduralGeometry.arc(pose, lines, center, basis.a(), basis.b(),
                    0.22, 0.12, -time * 0.25, Math.PI * 1.55, 10,
                    brighten(SHELL, 0.68), 0.28F);
            Vec3 tilted = basis.b().scale(0.78).add(forward.scale(0.62)).normalize();
            ProceduralGeometry.arc(pose, lines, center, basis.a(), tilted,
                    0.145, 0.145, time * 0.31, Math.PI * 1.34, 8,
                    brighten(SHELL, 0.76), 0.22F);
            ProceduralGeometry.helix(pose, lines, tail, forward.scale(trailLength * 0.88),
                    basis.a(), basis.b(), 0.025, 1.35, 12,
                    brighten(SHELL, 0.62), 0.22F);
            renderFlightTrace(bolt, partialTick, pose, lines, basis);
        }

        super.render(bolt, yaw, partialTick, pose, buffers, packedLight);
    }

    private static void renderFlightTrace(MagicBoltEntity bolt, float partialTick, PoseStack pose,
                                          com.mojang.blaze3d.vertex.VertexConsumer lines, Basis basis) {
        List<Vec3> trace = ((ProjectileFlightTraceAccess) (Object) bolt).essenceAscendance$flightTrace();
        if (trace.size() < 2) return;
        Vec3 origin = new Vec3(
                bolt.xo + (bolt.getX() - bolt.xo) * partialTick,
                bolt.yo + (bolt.getY() - bolt.yo) * partialTick,
                bolt.zo + (bolt.getZ() - bolt.zo) * partialTick);
        int first = Math.max(0, trace.size() - 10);
        List<Vec3> local = new ArrayList<>(trace.size() - first + 1);
        for (int i = first; i < trace.size(); i++) local.add(trace.get(i).subtract(origin));
        local.add(Vec3.ZERO);
        ProceduralGeometry.ribbon(pose, lines, local, brighten(SHELL, 0.34), 0.24F);
        if (local.size() >= 3) {
            Vec3 offset = basis.a().scale(0.022);
            ProceduralGeometry.line(pose, lines, local.get(local.size() - 3).add(offset), offset,
                    brighten(SHELL, 0.72), 0.18F);
            ProceduralGeometry.line(pose, lines, local.get(local.size() - 2).subtract(offset), offset.scale(-1),
                    brighten(SHELL, 0.72), 0.16F);
        }
    }

    @Override public ResourceLocation getTextureLocation(MagicBoltEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private static int brighten(int rgb, double amount) {
        int red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
        red += (int) Math.round((255 - red) * amount);
        green += (int) Math.round((255 - green) * amount);
        blue += (int) Math.round((255 - blue) * amount);
        return red << 16 | green << 8 | blue;
    }

    private record Basis(Vec3 a, Vec3 b) {
        private static Basis around(Vec3 direction) {
            Vec3 n = direction.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : direction.normalize();
            Vec3 reference = Math.abs(n.y) < 0.88 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 a = n.cross(reference).normalize();
            return new Basis(a, n.cross(a).normalize());
        }
    }
}

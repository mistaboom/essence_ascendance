package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Closed recipe identities with an extension point, not a serialized visual program. */
public final class WorldVisualRecipes {
    public static final ResourceLocation IMPACT_PULSE = TransientVisualIds.WORLD_IMPACT_PULSE;
    public static final ResourceLocation GATHERING_SPRITZ = TransientVisualIds.WORLD_GATHERING_SPRITZ;
    public static final ResourceLocation UTILITY_ACKNOWLEDGE = TransientVisualIds.WORLD_UTILITY_ACKNOWLEDGE;
    public static final ResourceLocation PROCESSOR_COMPLETE = TransientVisualIds.WORLD_PROCESSOR_COMPLETE;
    private static final Map<ResourceLocation, WorldVisualRecipe> RECIPES = new LinkedHashMap<>();

    static {
        register(IMPACT_PULSE, new ImpactPulse());
        register(GATHERING_SPRITZ, new GatheringSpritz());
        register(UTILITY_ACKNOWLEDGE, new UtilityAcknowledge());
        register(PROCESSOR_COMPLETE, new ProcessorComplete());
        register(TransientVisualIds.WORLD_ASCENDANCE_CEREMONY, new AscendanceCeremonyRecipe());
        CombatWorldVisualRecipes.registerAll();
        DefenseWorldVisualRecipes.registerAll();
        MobilityVitalityWorldVisualRecipes.registerAll();
        UtilityWorldVisualRecipes.registerAll();
    }

    private WorldVisualRecipes() { }

    public static void register(ResourceLocation id, WorldVisualRecipe recipe) {
        if (RECIPES.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(recipe)) != null)
            throw new IllegalArgumentException("Duplicate world visual recipe: " + id);
    }

    public static WorldVisualRecipe get(ResourceLocation id) { return RECIPES.get(id); }

    /** Small reusable impact acknowledgement: broad readable pulse plus optional fine registration. */
    private static final class ImpactPulse implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext context) {
            Basis basis = Basis.around(context.event().direction());
            Vec3 center = context.position();
            double scale = context.event().scale();
            float alpha = (float) (context.envelope() * (0.28 + context.event().intensity() * 0.16));
            double diamondRadius = scale * (0.12 + 0.12 * (1.0 - context.progress()));
            ProceduralGeometry.diamondRing(context.pose(), context.planes(), center,
                    basis.a(), basis.b(), diamondRadius, diamondRadius,
                    diamondRadius * 0.52, diamondRadius * 0.52,
                    context.rgb(), alpha * 0.72F);

            Vec3 target = context.target();
            if (target.distanceToSqr(center) > 0.01) {
                Vec3 lateral = basis.a();
                ProceduralGeometry.beam(context.pose(), context.planes(), center, target, lateral,
                        Math.max(0.008, scale * 0.012), context.rgb(), alpha * 0.36F);
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            Basis basis = Basis.around(context.event().direction());
            double radius = context.event().scale() * (0.18 + context.easedProgress() * 0.82);
            float alpha = (float) (context.envelope() * (0.28 + context.event().intensity() * 0.16));
            double rotation = context.variation(0) * Math.PI * 2.0 + context.progress() * 0.8;
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), context.position(),
                    basis.a(), basis.b(), radius, rotation, Math.PI * 2.0,
                    4, 4, 0.16, context.rgb(), alpha);
        }

        @Override public void renderDetailLines(WorldVisualRenderContext context) {
            Basis basis = Basis.around(context.event().direction());
            Vec3 center = context.position();
            double scale = context.event().scale();
            double radius = scale * (0.24 + context.easedProgress() * 0.58);
            double rotation = context.variation(1) * Math.PI * 2.0 - context.progress() * 1.3;
            float alpha = (float) (context.envelope() * 0.24);
            ProceduralGeometry.ring(context.pose(), context.lines(), center, basis.a(), basis.b(),
                    radius * 0.72, 12, context.rgb(), alpha);
            ProceduralGeometry.spokes(context.pose(), context.lines(), center, basis.a(), basis.b(),
                    radius * 0.82, radius, 8, rotation, context.rgb(), alpha * 0.75F);
        }
    }

    /** Tiny upward faceted burst used only when Gathering actually changes a payout or growth state. */
    private static final class GatheringSpritz implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext context) {
            Vec3 center = context.position();
            double scale = context.event().scale();
            double p = context.easedProgress();
            float alpha = (float) (context.envelope() * (0.45 + context.event().intensity() * 0.12));
            Vec3 right = new Vec3(1, 0, 0), up = new Vec3(0, 1, 0);
            for (int lane = 0; lane < 3; lane++) {
                double angle = context.variation(lane) * Math.PI * 2.0;
                double spread = scale * (0.08 + lane * 0.035);
                Vec3 shard = center.add(Math.cos(angle) * spread * p,
                        scale * (0.05 + (0.22 + lane * 0.035) * p), Math.sin(angle) * spread * p);
                double size = scale * (0.035 + lane * 0.006) * (1.0 - p * 0.35);
                ProceduralGeometry.diamond(context.pose(), context.planes(), shard, right, up,
                        size, size * 1.35, context.rgb(), alpha);
            }
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            Vec3 center = context.position();
            double scale = context.event().scale();
            double p = context.easedProgress();
            float alpha = (float) (context.envelope() * (0.45 + context.event().intensity() * 0.12));
            double radius = scale * (0.08 + 0.22 * p);
            ProceduralGeometry.arc(context.pose(), context.lines(), center,
                    new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius, radius,
                    context.variation(4) * Math.PI * 2.0, Math.PI * 1.45, 7,
                    context.rgb(), alpha * 0.55F);
        }
    }

    /** Compact convergence pulse for observer-visible Utility transactions. */
    private static final class UtilityAcknowledge implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext context) {
            Vec3 center = context.position();
            double scale = context.event().scale();
            float alpha = (float) (context.envelope() * (0.36 + context.event().intensity() * 0.12));
            ProceduralGeometry.diamondRing(context.pose(), context.planes(), center,
                    new Vec3(1, 0, 0), new Vec3(0, 1, 0), scale * 0.07, scale * 0.09,
                    scale * 0.035, scale * 0.045, context.rgb(), alpha * 0.62F);
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            double scale = context.event().scale();
            double radius = scale * (0.26 - 0.16 * context.easedProgress());
            float alpha = (float) (context.envelope() * (0.36 + context.event().intensity() * 0.12));
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), context.position(),
                    new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius,
                    context.variation(0) * Math.PI * 2.0 - context.progress(), Math.PI * 2.0,
                    4, 2, 0.28, context.rgb(), alpha);
        }
    }

    /** A compact upward transfer: one readable product diamond and one fine angular registration. */
    private static final class ProcessorComplete implements WorldVisualRecipe {
        @Override public void renderPrimaryPlanes(WorldVisualRenderContext context) {
            double p = context.easedProgress();
            Vec3 center = context.position().add(0, context.event().scale() * (0.06 + 0.22 * p), 0);
            double size = context.event().scale() * (0.075 - 0.018 * p);
            float alpha = (float) (context.envelope() * 0.48);
            ProceduralGeometry.diamond(context.pose(), context.planes(), center,
                    new Vec3(1, 0, 0), new Vec3(0, 1, 0), size, size * 1.25,
                    context.rgb(), alpha);
        }

        @Override public void renderPrimaryLines(WorldVisualRenderContext context) {
            double p = context.easedProgress();
            double radius = context.event().scale() * (0.22 - 0.10 * p);
            ProceduralGeometry.brokenRing(context.pose(), context.lines(), context.position(),
                    new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius,
                    Math.PI * 0.25 + p, Math.PI * 2.0, 4, 2, 0.24,
                    context.rgb(), (float) (context.envelope() * 0.40));
        }

        @Override public void renderDetailLines(WorldVisualRenderContext context) {
            double height = context.event().scale() * (0.10 + 0.20 * context.easedProgress());
            Vec3 center = context.position();
            ProceduralGeometry.beam(context.pose(), context.lines(), center.add(-0.08, 0, 0),
                    center.add(0.08, height, 0), new Vec3(0, 0, 1), 0.006,
                    context.rgb(), (float) (context.envelope() * 0.20));
        }
    }

    private record Basis(Vec3 a, Vec3 b) {
        private static Basis around(Vec3 normal) {
            Vec3 n = normal.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : normal.normalize();
            Vec3 reference = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 a = n.cross(reference).normalize();
            return new Basis(a, n.cross(a).normalize());
        }
    }
}

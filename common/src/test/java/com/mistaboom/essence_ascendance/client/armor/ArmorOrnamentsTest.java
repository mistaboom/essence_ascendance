package com.mistaboom.essence_ascendance.client.armor;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.visual.ArmorVisualStyle;
import com.mistaboom.essence_ascendance.visual.ArmorVisualStyle.Motif;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Executes real plane/line generation, with no GL context, against explicit spatial and cost budgets. */
public final class ArmorOrnamentsTest {
    public static void main(String[] args) throws Exception {
        checkEmissionRenderState();
        int[] emission = {0, 22, 45, 67, 90, 112};
        int previous = -1, fullSetVertices = 0;
        for (EquipmentTier tier : EquipmentTier.values()) {
            int alpha = ArmorVisualStyle.tier(tier).emission();
            check(alpha == emission[tier.ordinal()] && alpha > previous, "Exact monotonic emission endpoints/curve");
            previous = alpha;
        }
        for (Motif motif : Motif.values()) {
            check(ArmorVisualStyle.motif(motif.mesh) == motif, "Every mesh resolves its motif");
            int previousVertices = -1;
            for (EquipmentTier tier : EquipmentTier.values()) {
                Recording near = draw(motif, tier, 0, 1);
                Recording far = draw(motif, tier, 0, 0);
                check(near.xyz.size() >= previousVertices, "Nondecreasing actual vertex cost: " + motif + tier);
                check(far.xyz.size() <= near.xyz.size(), "Distance removes detail before broad shapes");
                if (ArmorVisualStyle.enabled(motif, tier)) check(!far.xyz.isEmpty(), "Broad motif survives detail cull");
                previousVertices = near.xyz.size();
                if (tier == EquipmentTier.TRANSCENDENT) fullSetVertices += near.xyz.size();
                var bounds = motif.bounds();
                for (int step = 0; step < 80; step++) {
                    Recording actual = draw(motif, tier, step * 157.13, 1);
                    check(actual.xyz.size() == near.xyz.size(), "Animation cannot grow the vertex budget");
                    for (float[] v : actual.xyz) {
                        check(v[0] >= bounds.minX() && v[0] <= bounds.maxX()
                                && v[1] >= bounds.minY() && v[1] <= bounds.maxY()
                                && v[2] >= bounds.minZ() && v[2] <= bounds.maxZ(), "Actual animated vertex exceeds flight bound");
                        check(v[2] < ArmorVisualStyle.FLIGHT_REAR_BOUNDARY, "Resting motif must leave all rear flight space clear");
                    }
                    if (tier == EquipmentTier.LATENT) same(near, actual, "Latent has no orbit/motion");
                }
                same(near, draw(motif, tier, 0, 1), "Deterministic animation");
            }
            check(ArmorOrnaments.clearsFlight(motif, new Matrix4f()), "Standing ornament clears flight");
            check(!ArmorOrnaments.clearsFlight(motif, new Matrix4f().translate(0, 0, 2)), "Rear intersection suppressed");
            // Representative walking, crouching and swimming limb rotations: every accepted bound's
            // actual vertices must still stay in front of the reserved plane in torso coordinates.
            for (int step = 0; step <= 32; step++) {
                Matrix4f transform = new Matrix4f().rotateX((float) (-Math.PI + step * Math.PI / 16));
                if (!ArmorOrnaments.clearsFlight(motif, transform)) continue;
                for (float[] v : draw(motif, EquipmentTier.TRANSCENDENT, step * 21, 1).xyz) {
                    var point = transform.transformPosition(new org.joml.Vector3f(v));
                    check(point.z < ArmorVisualStyle.FLIGHT_REAR_BOUNDARY, "Posed geometry must clear flight");
                }
            }
        }
        check(ArmorOrnaments.detail(12) == 1 && ArmorOrnaments.detail(24) == 0, "Fine-detail distance window");
        check(ArmorOrnaments.visibility(24) == 1 && ArmorOrnaments.visibility(48) == 0, "Broad shape distance window");
        check(fullSetVertices <= 2600, "Full set must fit the explicit 2600-vertex ornament budget");
        System.out.println("ArmorOrnamentsTest: emission, deterministic tiers, all animated bounds, flight culling, LOD PASS; full set "
                + fullSetVertices + " ornament vertices");
    }

    private static void checkEmissionRenderState() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var texture = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("essence_ascendance",
                "textures/armor/ascendance/generated/helmet_emission.png");
        var armor = net.minecraft.client.renderer.RenderType.armorCutoutNoCull(texture);
        var focus = net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(texture);
        var emission = ArmorRenderTypes.emission(texture);
        // Read actual render-state shards without invoking OpenGL. This catches the subtle mismatch
        // where a generic entity emissive pass sits behind vanilla's view-offset armor surface.
        check(stateField(emission, "layeringState") == stateField(armor, "layeringState"), "Emission depth layering must match normal armor");
        check(stateField(emission, "depthTestState") == stateField(armor, "depthTestState"), "Emission must keep normal armor depth testing");
        for (String field : List.of("shaderState", "transparencyState", "writeMaskState", "overlayState", "cullState"))
            check(stateField(emission, field) == stateField(focus, field), "Preserve Focus emissive semantics: " + field);
        check(stateField(emission, "writeMaskState") != stateField(armor, "writeMaskState"), "Emission must not write depth");
    }

    private static Object stateField(net.minecraft.client.renderer.RenderType type, String name) throws Exception {
        var state = type.getClass().getDeclaredField("state");
        state.setAccessible(true);
        Object composite = state.get(type);
        var field = composite.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(composite);
    }

    private static Recording draw(Motif motif, EquipmentTier tier, double age, float detail) {
        Recording result = new Recording();
        ArmorOrnaments.render(motif, tier, age, 1.234, EquipmentTierVisuals.armorAccentRgb(tier),
                detail, 1, new PoseStack(), result, result);
        return result;
    }
    private static void same(Recording a, Recording b, String message) {
        check(a.xyz.size() == b.xyz.size(), message);
        for (int i = 0; i < a.xyz.size(); i++) check(java.util.Arrays.equals(a.xyz.get(i), b.xyz.get(i)), message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static final class Recording implements VertexConsumer {
        final List<float[]> xyz = new ArrayList<>();
        public VertexConsumer addVertex(float x, float y, float z) {
            check(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z), "Finite positions");
            xyz.add(new float[]{x, y, z}); return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            check(a > 0 && a <= 255, "Visible, bounded opacity"); return this;
        }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}

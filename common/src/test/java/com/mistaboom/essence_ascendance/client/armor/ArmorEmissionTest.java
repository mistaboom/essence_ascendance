package com.mistaboom.essence_ascendance.client.armor;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
//? if >=26.1.2
/*import net.minecraft.client.renderer.rendertype.RenderType;*/
//? if >=26.1.2
/*import net.minecraft.client.renderer.rendertype.RenderSetup;*/
//? if >=26.1.2
/*import net.minecraft.client.renderer.rendertype.LayeringTransform;*/
//? if >=26.1.2
/*import com.mojang.blaze3d.pipeline.BlendFunction;*/
//? if >=26.1.2
/*import com.mojang.blaze3d.pipeline.ColorTargetState;*/
//? if >=26.1.2
/*import com.mojang.blaze3d.platform.CompareOp;*/
//? if <26.1.2
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Actual render-state and tier contracts, without invoking OpenGL or controlling a client. */
public final class ArmorEmissionTest {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        int[] expected = {0, 36, 72, 108, 144, 180};
        int previous = -1;
        for (EquipmentTier tier : EquipmentTier.values()) {
            int alpha = ArmorEmission.alpha(tier);
            check(alpha == expected[tier.ordinal()] && alpha > previous && alpha <= 255,
                    "Zero Latent, strictly increasing scaled emission, brighter Transcendent endpoint");
            previous = alpha;
        }
        check(ArmorEmission.alpha(EquipmentTier.TRANSCENDENT) == 180, "Transcendent endpoint matches the tuned maximum");
        check(ArmorEmission.luminousColor(0x804020) == 0x9f6f57, "Retain Focus's 3:1 accent/white tint");
        var texture = ResourceLocation.fromNamespaceAndPath("essence_ascendance",
                "textures/armor/ascendance/generated/chestplate_emission.png");
        var emission = ArmorRenderTypes.emission(texture);
        //? if >=26.1.2
        /*var pipeline = emission.pipeline();*/
        //? if >=26.1.2
        /*check(pipeline.getShaderDefines().flags().containsAll(List.of("EMISSIVE", "NO_OVERLAY", "NO_CARDINAL_LIGHTING")), "Unlit emission retains the emissive and no-cardinal-lighting shader flags");*/
        //? if >=26.1.2
        /*check(pipeline.getDepthStencilState().depthTest() == CompareOp.LESS_THAN_OR_EQUAL && !pipeline.getDepthStencilState().writeDepth(), "Emission tests physical armor depth without writing transparent pixels");*/
        //? if >=26.1.2
        /*check(pipeline.getColorTargetState().blendFunction().orElseThrow().equals(BlendFunction.TRANSLUCENT) && pipeline.getColorTargetState().writeMask() == ColorTargetState.WRITE_ALL, "Emission retains scaled alpha blending and full color writes");*/
        //? if >=26.1.2
        /*check(!pipeline.isCull() && emission.sortOnUpload(), "Emission keeps both armor sides visible and sorts transparent quads");*/
        //? if >=26.1.2
        /*var state = RenderType.class.getDeclaredField("state");*/
        //? if >=26.1.2
        /*state.setAccessible(true);*/
        //? if >=26.1.2
        /*var layering = RenderSetup.class.getDeclaredField("layeringTransform");*/
        //? if >=26.1.2
        /*layering.setAccessible(true);*/
        //? if >=26.1.2
        /*check(layering.get(state.get(emission)) == LayeringTransform.VIEW_OFFSET_Z_LAYERING, "Emission shares the armor view-depth offset");*/
        //? if <26.1.2
        var armor = RenderType.armorCutoutNoCull(texture);
        //? if <26.1.2
        var focus = RenderType.entityTranslucentEmissive(texture);
        //? if <26.1.2
        var eyes = RenderType.eyes(texture);
        //? if <26.1.2
        check(stateField(emission, "layeringState") == stateField(armor, "layeringState"), "Emission must align with normal armor depth");
        //? if <26.1.2
        check(stateField(emission, "depthTestState") == stateField(armor, "depthTestState"), "Emission must test physical depth");
        //? if <26.1.2
        check(stateField(emission, "shaderState") == stateField(eyes, "shaderState"), "Use unlit shader: normals cannot dim side-facing sleeves");
        //? if <26.1.2
        check(stateField(emission, "shaderState") != stateField(focus, "shaderState"), "Do not restore directional shading to accent emission");
        //? if <26.1.2
        for (String field : List.of("transparencyState", "writeMaskState", "cullState")) check(stateField(emission, field) == stateField(focus, field), "Preserve masked alpha blend/color-only semantics: " + field);
        //? if <26.1.2
        check(stateField(emission, "transparencyState") != stateField(eyes, "transparencyState"), "Use scaled alpha, not additive eye blending");
        //? if <26.1.2
        check(stateField(emission, "writeMaskState") != stateField(armor, "writeMaskState"), "Transparent accent pixels cannot write depth");
        System.out.println("ArmorEmissionTest: scaled 0..180 emission, luminous tint, unlit shader, armor depth and masked alpha blending PASS");
    }

    //? if <26.1.2
    private static Object stateField(RenderType type, String name) throws Exception {
        //? if <26.1.2
        var state = type.getClass().getDeclaredField("state");
        //? if <26.1.2
        state.setAccessible(true);
        //? if <26.1.2
        Object composite = state.get(type);
        //? if <26.1.2
        var field = composite.getClass().getDeclaredField(name);
        //? if <26.1.2
        field.setAccessible(true);
        //? if <26.1.2
        return field.get(composite);
    //? if <26.1.2
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

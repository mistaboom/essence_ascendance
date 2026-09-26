package com.mistaboom.essence_ascendance.client.armor;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
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
        var armor = RenderType.armorCutoutNoCull(texture);
        var focus = RenderType.entityTranslucentEmissive(texture);
        var eyes = RenderType.eyes(texture);
        var emission = ArmorRenderTypes.emission(texture);
        check(stateField(emission, "layeringState") == stateField(armor, "layeringState"), "Emission must align with normal armor depth");
        check(stateField(emission, "depthTestState") == stateField(armor, "depthTestState"), "Emission must test physical depth");
        check(stateField(emission, "shaderState") == stateField(eyes, "shaderState"), "Use unlit shader: normals cannot dim side-facing sleeves");
        check(stateField(emission, "shaderState") != stateField(focus, "shaderState"), "Do not restore directional shading to accent emission");
        for (String field : List.of("transparencyState", "writeMaskState", "cullState"))
            check(stateField(emission, field) == stateField(focus, field), "Preserve masked alpha blend/color-only semantics: " + field);
        check(stateField(emission, "transparencyState") != stateField(eyes, "transparencyState"), "Use scaled alpha, not additive eye blending");
        check(stateField(emission, "writeMaskState") != stateField(armor, "writeMaskState"), "Transparent accent pixels cannot write depth");
        System.out.println("ArmorEmissionTest: scaled 0..180 emission, luminous tint, unlit shader, armor depth and masked alpha blending PASS");
    }

    private static Object stateField(RenderType type, String name) throws Exception {
        var state = type.getClass().getDeclaredField("state");
        state.setAccessible(true);
        Object composite = state.get(type);
        var field = composite.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(composite);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.armor.ArmorRenderTypes;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** The authored shield uses the armor's stitched tier textures and exact tuned accent emission. */
public final class AscendanceShieldRenderer {
    private static final BlockbenchStaticMesh MESH = new BlockbenchStaticMesh(
            resource("meshes/item/ascendance_shield.eamesh"), texture("latent"));
    private static final RenderType[] BODY = createBodyTypes();
    private static final RenderType EMISSION = ArmorRenderTypes.emission(texture("emission"));

    private AscendanceShieldRenderer() { }

    public static void invalidateResources() {
        MESH.invalidate();
    }

    public static void render(ItemStack stack, PoseStack pose, MultiBufferSource buffers,
                              int packedLight, int packedOverlay) {
        EquipmentTier tier = EquipmentTierData.tier(stack);
        pose.pushPose();
        try {
            // Exported in vanilla ShieldModel coordinates: centered grip, -Z front, +Z handle.
            // Item-model JSON already supplies both hands, blocking, GUI, ground and frame poses.
            pose.scale(1.0F, -1.0F, -1.0F);
            MESH.render(pose, ItemRenderer.getFoilBufferDirect(
                            buffers, BODY[tier.ordinal()], true, stack.hasFoil()),
                    packedLight, packedOverlay, 0xFFFFFF, 255);

            int alpha = ArmorEmission.alpha(tier);
            if (alpha > 0) {
                int rgb = ArmorEmission.luminousColor(EquipmentTierVisuals.armorAccentRgb(tier));
                MESH.render(pose, buffers.getBuffer(EMISSION), LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, rgb, alpha);
            }
        } finally {
            pose.popPose();
        }
    }

    private static RenderType[] createBodyTypes() {
        EquipmentTier[] tiers = EquipmentTier.values();
        RenderType[] types = new RenderType[tiers.length];
        for (EquipmentTier tier : tiers) {
            types[tier.ordinal()] = RenderType.entityCutoutNoCull(texture(tier.serializedName()));
        }
        return types;
    }

    private static ResourceLocation texture(String name) {
        return resource("textures/item/ascendance_shield/" + name + ".png");
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }
}

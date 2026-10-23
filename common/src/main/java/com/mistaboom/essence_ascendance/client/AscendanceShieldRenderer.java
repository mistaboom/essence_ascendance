package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.armor.ArmorRenderTypes;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
import com.mojang.blaze3d.vertex.PoseStack;
//? if <26.1.2
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
//? if >=26.1.2
/*import net.minecraft.client.renderer.rendertype.RenderType;*/
//? if >=26.1.2
/*import net.minecraft.client.renderer.rendertype.RenderTypes;*/
//? if <26.1.2
import net.minecraft.client.renderer.RenderType;
//? if <26.1.2
import net.minecraft.client.renderer.entity.ItemRenderer;
//? if >=26.1.2
/*import com.mojang.blaze3d.vertex.VertexMultiConsumer;*/
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
            MESH.render(pose,
                    //? if >=26.1.2
                    /*stack.hasFoil() ? VertexMultiConsumer.create(buffers.getBuffer(RenderTypes.entityGlint()), buffers.getBuffer(BODY[tier.ordinal()])) : buffers.getBuffer(BODY[tier.ordinal()]),*/
                    //? if <26.1.2
                    ItemRenderer.getFoilBufferDirect(buffers, BODY[tier.ordinal()], true, stack.hasFoil()),
                    packedLight, packedOverlay, 0xFFFFFF, 255);

            int alpha = ArmorEmission.alpha(tier);
            if (alpha > 0) {
                int rgb = ArmorEmission.luminousColor(EquipmentTierVisuals.armorAccentRgb(tier));
                MESH.render(pose, buffers.getBuffer(EMISSION),
                        //? if >=26.1.2
                        /*ClientLightCompat.FULL_BRIGHT,*/
                        //? if <26.1.2
                        LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, rgb, alpha);
            }
        } finally {
            pose.popPose();
        }
    }

    //? if >=26.1.2 {
    /*public static void submit(ItemStack stack, PoseStack pose,
                              net.minecraft.client.renderer.SubmitNodeCollector collector,
                              int packedLight, int packedOverlay, boolean foil) {
        EquipmentTier tier = EquipmentTierData.tier(stack);
        pose.pushPose();
        try {
            pose.scale(1.0F, -1.0F, -1.0F);
            MESH.submit(pose, collector, BODY[tier.ordinal()], packedLight,
                    packedOverlay, 0xFFFFFF, 255);
            if (foil) MESH.submit(pose, collector, RenderTypes.entityGlint(),
                    ClientLightCompat.FULL_BRIGHT, packedOverlay, 0xFFFFFF, 255);
            int alpha = ArmorEmission.alpha(tier);
            if (alpha > 0) {
                int rgb = ArmorEmission.luminousColor(EquipmentTierVisuals.armorAccentRgb(tier));
                MESH.submit(pose, collector, EMISSION, ClientLightCompat.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, rgb, alpha);
            }
        } finally { pose.popPose(); }
    }

    public static void visitExtents(java.util.function.Consumer<org.joml.Vector3fc> consumer) {
        MESH.visitExtents(consumer);
    }
    *///? }

    private static RenderType[] createBodyTypes() {
        EquipmentTier[] tiers = EquipmentTier.values();
        RenderType[] types = new RenderType[tiers.length];
        for (EquipmentTier tier : tiers) {
            //? if >=26.1.2
            /*types[tier.ordinal()] = RenderTypes.entityCutout(texture(tier.serializedName()));*/
            //? if <26.1.2
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

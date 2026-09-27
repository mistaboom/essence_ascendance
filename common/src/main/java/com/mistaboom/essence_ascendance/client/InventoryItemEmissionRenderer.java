package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.armor.ArmorRenderTypes;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.item.EmissiveAccentItem;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Accent-only post-item pass shared by generated Ascendance weapons and tools in every display context. */
public final class InventoryItemEmissionRenderer {
    static final int ACCENT_TINT_INDEX = 1;
    private static final long MODEL_SEED = 42L;
    private static final RenderType EMISSION = ArmorRenderTypes.emission(TextureAtlas.LOCATION_BLOCKS);

    private InventoryItemEmissionRenderer() {
    }

    public static void render(ItemStack stack, BakedModel model, PoseStack pose, MultiBufferSource buffers) {
        if (!(stack.getItem() instanceof EmissiveAccentItem)) return;

        var tier = EquipmentTierData.tier(stack);
        int alpha = ArmorEmission.alpha(tier);
        if (alpha == 0) return;

        int rgb = ArmorEmission.luminousColor(EquipmentTierVisuals.armorAccentRgb(tier));
        float red = (rgb >> 16 & 255) / 255.0F;
        float green = (rgb >> 8 & 255) / 255.0F;
        float blue = (rgb & 255) / 255.0F;
        float opacity = alpha / 255.0F;
        // Generated items use a fixed atlas buffer, unlike the shield's immediate body pass.
        // BufferSource drains shared buffers before fixed buffers: without this flush the
        // normal sprite draws AFTER our color-only emission and completely covers its glow.
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(Sheets.translucentCullBlockSheet());
        }
        VertexConsumer consumer = buffers.getBuffer(EMISSION);
        RandomSource random = RandomSource.create();

        for (Direction direction : Direction.values()) {
            random.setSeed(MODEL_SEED);
            renderAccent(model.getQuads(null, direction, random), pose, consumer,
                    red, green, blue, opacity);
        }
        random.setSeed(MODEL_SEED);
        renderAccent(model.getQuads(null, null, random), pose, consumer,
                red, green, blue, opacity);
    }

    private static void renderAccent(List<BakedQuad> quads, PoseStack pose, VertexConsumer consumer,
                                     float red, float green, float blue, float alpha) {
        PoseStack.Pose currentPose = pose.last();
        for (BakedQuad quad : quads) {
            if (!quad.isTinted() || quad.getTintIndex() != ACCENT_TINT_INDEX) continue;
            consumer.putBulkData(currentPose, quad, red, green, blue, alpha,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }
}

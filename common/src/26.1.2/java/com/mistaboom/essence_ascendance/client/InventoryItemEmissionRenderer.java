package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.armor.ArmorRenderTypes;
import com.mistaboom.essence_ascendance.equipment.EquipmentAppearance;
import com.mistaboom.essence_ascendance.item.EmissiveAccentItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Adds only the authored accent quads after the native item submission in every display context. */
public final class InventoryItemEmissionRenderer {
    private static final Map<ItemStackRenderState, ItemStack> STACKS = new WeakHashMap<>();
    private InventoryItemEmissionRenderer() { }

    public static void clear(ItemStackRenderState state) { STACKS.remove(state); }

    public static void capture(ItemStackRenderState state, ItemStack stack) {
        if (stack.getItem() instanceof EmissiveAccentItem) STACKS.put(state, stack.copy());
    }

    public static void submit(ItemStackRenderState state, List<BakedQuad> quads, PoseStack pose,
            SubmitNodeCollector collector, int overlay) {
        ItemStack stack = STACKS.get(state);
        if (stack == null || quads.isEmpty()) return;
        var appearance = EquipmentAppearance.resolve(stack, PersonalizedEquipmentClient.active(stack));
        int alpha = appearance.emissionAlpha();
        if (alpha == 0) return;
        List<BakedQuad> accents = quads.stream().filter(quad -> quad.materialInfo().isTinted()
                && EmissiveAccentItem.materialTintIndex(stack, quad.materialInfo().tintIndex()) == 1).toList();
        if (accents.isEmpty()) return;
        int color = alpha << 24 | appearance.luminousRgb();
        collector.submitCustomGeometry(pose, ArmorRenderTypes.emission(TextureAtlas.LOCATION_ITEMS),
                (submittedPose, consumer) -> {
                    QuadInstance instance = new QuadInstance();
                    instance.setColor(color);
                    instance.setLightCoords(0xF000F0);
                    instance.setOverlayCoords(overlay);
                    for (BakedQuad quad : accents) consumer.putBakedQuad(submittedPose, quad, instance);
                });
    }
}

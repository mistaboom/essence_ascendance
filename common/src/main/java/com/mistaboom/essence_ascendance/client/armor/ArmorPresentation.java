package com.mistaboom.essence_ascendance.client.armor;

import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumMap;

/** Shared accent-only post-armor pass for both loaders and every humanoid armor wearer. */
public final class ArmorPresentation<T extends LivingEntity> {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final RenderType[] EMISSION = {
            texture("helmet"), texture("chestplate"), texture("leggings"), texture("boots")
    };
    private final EnumMap<EquipmentSlot, AscendanceArmorModel<T>> models = new EnumMap<>(EquipmentSlot.class);

    private static RenderType texture(String piece) {
        return ArmorRenderTypes.emission(ResourceLocation.fromNamespaceAndPath("essence_ascendance",
                "textures/armor/ascendance/generated/" + piece + "_emission.png"));
    }

    public void render(HumanoidModel<T> parent, T entity, PoseStack pose, MultiBufferSource buffers) {
        if (entity.isInvisible() || entity.isSpectator()) return;
        for (int index = 0; index < SLOTS.length; index++) {
            EquipmentSlot slot = SLOTS[index];
            var stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof AscendanceArmorItem armor) || armor.ascendanceSlot() != slot) continue;
            var tier = EquipmentTierData.tier(stack);
            int emission = ArmorEmission.alpha(tier);
            if (emission == 0) continue;
            var model = models.computeIfAbsent(slot, AscendanceArmorModel::new);
            prepareModel(parent, model, slot);
            int rgb = ArmorEmission.luminousColor(EquipmentTierVisuals.armorAccentRgb(tier));
            // Exact normal-pass mesh/UVs and view-depth layering; only authored accent pixels blend.
            model.renderToBuffer(pose, buffers.getBuffer(EMISSION[index]),
                    0xF000F0, OverlayTexture.NO_OVERLAY, emission << 24 | rgb);
        }
    }

    /** Armor visibility belongs to its slot, not the wearer's skin/wooden limb visibility. */
    static <T extends LivingEntity> void prepareModel(HumanoidModel<T> parent,
                                                      AscendanceArmorModel<T> model, EquipmentSlot slot) {
        parent.copyPropertiesTo(model);
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> model.head.visible = true;
            case CHEST -> {
                model.body.visible = true;
                model.leftArm.visible = true;
                model.rightArm.visible = true;
            }
            case LEGS, FEET -> {
                model.leftLeg.visible = true;
                model.rightLeg.visible = true;
                // Leggings' body-attached belt has no accent mask and never gets an emission pass.
            }
            default -> throw new IllegalArgumentException("Unsupported armor emission slot: " + slot);
        }
    }
}

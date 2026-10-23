package com.mistaboom.essence_ascendance.client.armor;

import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Accent-mask pass attached to native armor submission for humanoids and remote players. */
public final class ArmorPresentation {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final RenderType[] EMISSION = {
            texture("helmet"), texture("chestplate"), texture("leggings"), texture("boots")
    };

    private ArmorPresentation() { }

    private static RenderType texture(String piece) {
        return ArmorRenderTypes.emission(Identifier.fromNamespaceAndPath("essence_ascendance",
                "textures/armor/ascendance/generated/" + piece + "_emission.png"));
    }

    public static void submit(HumanoidModel<?> parent, HumanoidRenderState state,
                              PoseStack pose, SubmitNodeCollector collector) {
        if (state.isInvisible || state instanceof AvatarRenderState avatar && avatar.isSpectator) return;
        for (int index = 0; index < SLOTS.length; index++) {
            EquipmentSlot slot = SLOTS[index];
            ItemStack stack = switch (slot) {
                case HEAD -> state.headEquipment;
                case CHEST -> state.chestEquipment;
                case LEGS -> state.legsEquipment;
                case FEET -> state.feetEquipment;
                default -> ItemStack.EMPTY;
            };
            if (!(stack.getItem() instanceof AscendanceArmorItem armor)
                    || armor.ascendanceSlot() != slot) continue;
            var tier = EquipmentTierData.tier(stack);
            int alpha = ArmorEmission.alpha(tier);
            if (alpha == 0) continue;
            int color = alpha << 24 | ArmorEmission.luminousColor(
                    EquipmentTierVisuals.armorAccentRgb(tier));
            // The feature collector draws later, so each submission owns an independent bone pose.
            AscendanceArmorModel<HumanoidRenderState> model = new AscendanceArmorModel<>(slot);
            prepareModel(parent, model, slot);
            RenderType layer = EMISSION[index];
            collector.submitCustomGeometry(pose, layer, (submittedPose, vertices) -> {
                PoseStack geometry = new PoseStack();
                geometry.mulPose(submittedPose.pose());
                model.renderToBuffer(geometry, vertices, 0xF000F0, OverlayTexture.NO_OVERLAY, color);
            });
        }
    }

    static void prepareModel(HumanoidModel<?> parent, AscendanceArmorModel<?> model, EquipmentSlot slot) {
        model.head.loadPose(parent.head.storePose());
        model.hat.loadPose(parent.hat.storePose());
        model.body.loadPose(parent.body.storePose());
        model.rightArm.loadPose(parent.rightArm.storePose());
        model.leftArm.loadPose(parent.leftArm.storePose());
        model.rightLeg.loadPose(parent.rightLeg.storePose());
        model.leftLeg.loadPose(parent.leftLeg.storePose());
        model.head.visible = slot == EquipmentSlot.HEAD;
        model.hat.visible = false;
        model.body.visible = slot == EquipmentSlot.CHEST;
        model.rightArm.visible = slot == EquipmentSlot.CHEST;
        model.leftArm.visible = slot == EquipmentSlot.CHEST;
        model.rightLeg.visible = slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
        model.leftLeg.visible = slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }
}

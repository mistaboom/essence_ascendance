package com.mistaboom.essence_ascendance.client.armor;

import com.mistaboom.essence_ascendance.client.FlightVisualRenderer;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralWorldQueue;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import com.mistaboom.essence_ascendance.visual.ArmorVisualStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import java.util.EnumMap;

/** Shared post-armor pass for both loaders and every humanoid armor wearer. */
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

    public void render(HumanoidModel<T> parent, T entity, float partialTick,
                       PoseStack pose, MultiBufferSource buffers) {
        if (entity.isInvisible() || entity.isSpectator()) return;
        double distanceSquared = entity.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        double distance = Math.sqrt(distanceSquared);
        float detail = ArmorOrnaments.detail(distance), visibility = ArmorOrnaments.visibility(distance);
        double age = entity.tickCount + partialTick;
        boolean flight = entity instanceof AbstractClientPlayer player && FlightVisualRenderer.active(player);
        Matrix4f toBody = null;
        if (flight) {
            pose.pushPose();
            parent.body.translateAndRotate(pose);
            toBody = new Matrix4f(pose.last().pose()).invert();
            pose.popPose();
        }
        final Matrix4f bodyInverse = toBody;
        Frame frame = null;
        for (int index = 0; index < SLOTS.length; index++) {
            EquipmentSlot slot = SLOTS[index];
            var stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof AscendanceArmorItem armor) || armor.ascendanceSlot() != slot) continue;
            var model = models.computeIfAbsent(slot, AscendanceArmorModel::new);
            parent.copyPropertiesTo(model);
            // Empty bones carry no mesh or motif; copying visibility also respects hidden limbs.
            model.head.visible = parent.head.visible;
            model.body.visible = parent.body.visible;
            model.leftArm.visible = parent.leftArm.visible;
            model.rightArm.visible = parent.rightArm.visible;
            model.leftLeg.visible = parent.leftLeg.visible;
            model.rightLeg.visible = parent.rightLeg.visible;
            var tier = EquipmentTierData.tier(stack);
            int rgb = EquipmentTierVisuals.armorAccentRgb(tier);
            int emission = ArmorVisualStyle.tier(tier).emission();
            if (emission > 0) {
                boolean bodyVisible = model.body.visible;
                if (slot == EquipmentSlot.LEGS) model.body.visible = false; // Never submit the belt overlay.
                // Identical vertices/UVs, LEQUAL depth and color-only emissive blending: no inflated mesh,
                // polygon offset, filtered mask expansion or separate ordinary base/accent surfaces.
                // ArmorRenderTypes matches the vanilla armor pass's VIEW_OFFSET_Z_LAYERING.
                model.renderToBuffer(pose, buffers.getBuffer(EMISSION[index]),
                        0xF000F0, OverlayTexture.NO_OVERLAY, emission << 24 | ArmorVisualStyle.luminousColor(rgb));
                model.body.visible = bodyVisible;
            }
            if (visibility <= 0) continue;
            if (frame == null) frame = new Frame(age, detail, visibility);
            Frame capturedFrame = frame;
            double seed = (entity.getUUID().hashCode() & 65535) * .001 + index * 1.7;
            model.visitAttachments(pose, (mesh, bonePose) -> {
                var motif = ArmorVisualStyle.motif(mesh);
                if (!ArmorVisualStyle.enabled(motif, tier)) return;
                if (bodyInverse != null && !ArmorOrnaments.clearsFlight(motif,
                        new Matrix4f(bodyInverse).mul(bonePose.pose()))) return;
                PoseStack attached = new PoseStack();
                attached.last().pose().set(bonePose.pose());
                attached.last().normal().set(bonePose.normal());
                capturedFrame.attachments[capturedFrame.count++] = new Attachment(motif, tier, seed, rgb, attached);
            });
        }
        if (frame != null && frame.count > 0) {
            // A single queue entry and two shared ornament batches for the entire set.
            // Attachments already include the entity transform; enqueue only the world camera transform.
            if (ProceduralWorldQueue.inWorldFrame())
                ProceduralWorldQueue.enqueueEntity(new PoseStack(), distanceSquared, frame::render);
            else frame.render(new PoseStack(), buffers);
        }
    }

    private record Attachment(ArmorVisualStyle.Motif motif, EquipmentTier tier, double seed, int rgb, PoseStack pose) { }

    private static final class Frame {
        private final Attachment[] attachments = new Attachment[9];
        private int count;
        private final double age;
        private final float detail, visibility;
        private Frame(double age, float detail, float visibility) {
            this.age = age; this.detail = detail; this.visibility = visibility;
        }
        private void render(PoseStack world, MultiBufferSource buffers) {
            PoseStack transformed = new PoseStack();
            for (int pass = 0; pass < (detail > 0 ? 2 : 1); pass++) {
                var consumer = buffers.getBuffer(pass == 0 ? ProceduralRenderTypes.WORLD_PLANES
                        : ProceduralRenderTypes.WORLD_DEPTH_LINES);
                for (int i = 0; i < count; i++) {
                    Attachment a = attachments[i];
                    transformed.last().pose().set(world.last().pose()).mul(a.pose.last().pose());
                    transformed.last().normal().set(world.last().normal()).mul(a.pose.last().normal());
                    ArmorOrnaments.render(a.motif, a.tier, age, a.seed, a.rgb, detail, visibility, transformed,
                            pass == 0 ? consumer : null, pass == 1 ? consumer : null);
                }
            }
        }
    }
}

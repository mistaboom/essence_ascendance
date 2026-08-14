package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import com.mistaboom.essence_ascendance.item.AscendanceRangedWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/*
 * Fabric client bridge for the SECOND half of vanilla bow animation.
 *
 * Item model predicates control which bow model is shown, but
 * ItemInHandRenderer independently moves/scales the held bow while it is
 * being drawn. Vanilla divides that first-person transform by a hard-coded
 * 20-tick full-draw duration.
 *
 * Ascendance already synchronizes its resolved fullDrawTicks onto the held
 * ItemStack. Replacing only vanilla's 20.0F divisor for our bow lets the
 * entire vanilla first-person transform run unchanged, just on the same
 * accelerated timeline as gameplay and the model predicates.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @ModifyConstant(
            method = "renderArmWithItem",
            constant = @Constant(floatValue = 20.0F),
            require = 1,
            allow = 1
    )
    private float essenceAscendance$useResolvedBowDrawDuration(
            float vanillaFullDrawTicks,
            AbstractClientPlayer player,
            float partialTicks,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equippedProgress,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int combinedLight
    ) {
        if (!(stack.getItem() instanceof AscendanceRangedWeaponItem)) {
            return vanillaFullDrawTicks;
        }

        return Math.max(
                1.0F,
                EquipmentWeaponService.syncedRangedFullDrawTicks(stack)
        );
    }
}

package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import com.mistaboom.essence_ascendance.item.AscendanceRangedWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/*
 * Fabric client bridge for first-person equipment animation timing.
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

    /*
     * A successful use resets the selected hand height to zero. Vanilla then
     * raises it by at most 0.4 each client tick, independently of isBlocking.
     * Replace only that upward step for the actively used Ascendance Shield so
     * its native equip animation completes on the synchronized guard-ready tick.
     */
    @ModifyArg(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;clamp(FFF)F",
                    ordinal = 2
            ),
            index = 2,
            require = 1,
            allow = 1
    )
    private float essenceAscendance$useMainHandShieldRaiseStep(float vanillaMaximumStep) {
        return essenceAscendance$shieldRaiseStep(vanillaMaximumStep, InteractionHand.MAIN_HAND);
    }

    @ModifyArg(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;clamp(FFF)F",
                    ordinal = 3
            ),
            index = 2,
            require = 1,
            allow = 1
    )
    private float essenceAscendance$useOffHandShieldRaiseStep(float vanillaMaximumStep) {
        return essenceAscendance$shieldRaiseStep(vanillaMaximumStep, InteractionHand.OFF_HAND);
    }

    private float essenceAscendance$shieldRaiseStep(float vanillaMaximumStep, InteractionHand hand) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null
                || !EquipmentShieldService.isUsingShield(player)
                || player.getUsedItemHand() != hand) {
            return vanillaMaximumStep;
        }

        int raiseDelayTicks = EquipmentShieldService.raiseDelayTicks(player, player.getUseItem());
        return 1.0F / Math.max(1, raiseDelayTicks);
    }

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

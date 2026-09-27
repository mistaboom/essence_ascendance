package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.client.AscendanceShieldRenderer;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only Ascendance shield geometry; item display/blocking transforms remain vanilla. */
@Mixin(BlockEntityWithoutLevelRenderer.class)
public abstract class ShieldItemRendererMixin {
    @Inject(method = "renderByItem", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$renderShield(ItemStack stack, ItemDisplayContext displayContext,
                                               PoseStack pose, MultiBufferSource buffers,
                                               int packedLight, int packedOverlay, CallbackInfo ci) {
        if (EquipmentShieldService.isShield(stack)) {
            AscendanceShieldRenderer.render(stack, pose, buffers, packedLight, packedOverlay);
            ci.cancel();
        }
    }
}

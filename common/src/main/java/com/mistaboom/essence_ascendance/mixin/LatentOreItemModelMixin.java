package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
abstract class LatentOreItemModelMixin {
    @Inject(method = "getModel", at = @At("RETURN"), cancellable = true)
    private void essence$hostItem(ItemStack stack, Level level, LivingEntity entity, int seed, CallbackInfoReturnable<BakedModel> cir) {
        if (LatentOreModels.isOre(stack)) cir.setReturnValue(LatentOreModels.forItem(stack, cir.getReturnValue()));
    }
}

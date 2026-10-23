package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.InventoryItemEmissionRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep each resolved item state associated with its stack for the authored accent pass. */
@Mixin(ItemModelResolver.class)
public abstract class ItemRendererEmissionMixin {
    @Inject(method = "updateForTopItem", at = @At("HEAD"))
    private void essenceAscendance$clear(ItemStackRenderState state, ItemStack stack,
            ItemDisplayContext display, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        InventoryItemEmissionRenderer.clear(state);
    }

    @Inject(method = "appendItemLayers", at = @At("TAIL"))
    private void essenceAscendance$capture(ItemStackRenderState state, ItemStack stack,
            ItemDisplayContext display, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        InventoryItemEmissionRenderer.capture(state, stack);
    }
}

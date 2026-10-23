package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Collection;
import java.util.List;

/** Substitute only the host geometry; the authored item model retains transforms, tint and foil. */
@Mixin(CuboidItemModelWrapper.class)
abstract class LatentOreItemModelMixin {
    @ModifyArg(method = "update", at = @At(value = "INVOKE",
            target = "Ljava/util/List;addAll(Ljava/util/Collection;)Z"), index = 0, require = 1)
    private Collection<BakedQuad> essence$hostItem(Collection<BakedQuad> original,
            @Local(argsOnly = true) ItemStack stack,
            @Local(argsOnly = true) ItemStackRenderState renderState) {
        List<BakedQuad> host = LatentOreModels.itemQuads(stack, renderState);
        return host == null ? original : host;
    }
}

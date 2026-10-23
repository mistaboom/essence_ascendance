package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.ore.LatentOreItems;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keep each alias inventory key, but bake its geometry from the authored adaptive cube. */
@Mixin(ModelBakery.class)
abstract class LatentOreModelBakeryMixin {
    @ModifyArg(method = "loadItemModelAndDependencies", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/ModelBakery;getModel(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/resources/model/UnbakedModel;"),
            index = 0, require = 1)
    private ResourceLocation essence$aliasModel(ResourceLocation modelResource) {
        if (!modelResource.getNamespace().equals(EssenceAscendance.MOD_ID)
                || !modelResource.getPath().startsWith("item/")) return modelResource;
        ResourceLocation itemId = modelResource.withPath(modelResource.getPath().substring("item/".length()));
        return LatentOreItems.isRegisteredAlias(itemId)
                ? ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "item/adaptive_latent_ore")
                : modelResource;
    }
}

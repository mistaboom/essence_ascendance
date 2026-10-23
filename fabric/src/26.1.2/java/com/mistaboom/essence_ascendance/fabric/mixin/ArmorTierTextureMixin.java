package com.mistaboom.essence_ascendance.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Replace only the armor base atlas; native foil and trim submission still run. */
@Mixin(EquipmentLayerRenderer.class)
public abstract class ArmorTierTextureMixin {
    @ModifyArg(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;"
                    + "armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;"), index = 0)
    private Identifier essenceAscendance$tierAtlas(Identifier original,
            @Local(argsOnly = true) EquipmentClientInfo.LayerType layerType,
            @Local(argsOnly = true) ItemStack stack) {
        Identifier replacement = EquipmentTierVisuals.armorTexture(
                stack, layerType == EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS);
        return replacement == null ? original : replacement;
    }
}

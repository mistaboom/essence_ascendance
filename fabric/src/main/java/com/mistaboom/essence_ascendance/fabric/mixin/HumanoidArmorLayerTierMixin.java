package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric keeps vanilla's ArmorMaterial.Layer texture lookup intact. Capture the
 * stack being rendered, then swap only that texture lookup for Ascendance
 * armor.  The rest of vanilla rendering (including trims and real enchantment
 * glint) remains untouched.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerTierMixin {

    @Unique
    private static final ThreadLocal<ItemStack> ESSENCE_ASCENDANCE$ARMOR_STACK =
            new ThreadLocal<>();

    @Redirect(
            method = "renderArmorPiece",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack essenceAscendance$captureArmorStack(
            LivingEntity entity,
            EquipmentSlot slot
    ) {
        ItemStack stack = entity.getItemBySlot(slot);
        ESSENCE_ASCENDANCE$ARMOR_STACK.set(stack);
        return stack;
    }

    @Redirect(
            method = "renderArmorPiece",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ArmorMaterial$Layer;texture(Z)Lnet/minecraft/resources/ResourceLocation;"
            )
    )
    private ResourceLocation essenceAscendance$useTierArmorTexture(
            ArmorMaterial.Layer layer,
            boolean innerLayer
    ) {
        ItemStack stack = ESSENCE_ASCENDANCE$ARMOR_STACK.get();
        ResourceLocation replacement =
                EquipmentTierVisuals.armorTexture(stack, innerLayer);
        return replacement == null
                ? layer.texture(innerLayer)
                : replacement;
    }

    @Inject(
            method = "renderArmorPiece",
            at = @At("RETURN")
    )
    private void essenceAscendance$clearArmorStack(CallbackInfo ci) {
        ESSENCE_ASCENDANCE$ARMOR_STACK.remove();
    }
}

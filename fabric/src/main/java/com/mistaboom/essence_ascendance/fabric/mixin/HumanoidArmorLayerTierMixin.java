package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.model.HumanoidModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mistaboom.essence_ascendance.client.armor.AscendanceArmorModel;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

import java.util.EnumMap;
import java.util.Map;

/**
 * Select the slot's mesh model before vanilla copies the humanoid pose, then
 * select its tier atlas. Vanilla still owns visibility, baby scaling and foil.
 * Trim passes remain vanilla; these custom UVs do not provide a trim layout.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerTierMixin {

    @Unique
    private static final ThreadLocal<ItemStack> ESSENCE_ASCENDANCE$ARMOR_STACK =
            new ThreadLocal<>();

    @Unique
    private final Map<EquipmentSlot, HumanoidModel<LivingEntity>> essenceAscendance$armorModels =
            new EnumMap<>(EquipmentSlot.class);

    @ModifyVariable(method = "renderArmorPiece", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private HumanoidModel<LivingEntity> essenceAscendance$armorModel(
            HumanoidModel<LivingEntity> original, PoseStack pose, MultiBufferSource buffers,
            LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel<LivingEntity> model) {
        if (!(entity.getItemBySlot(slot).getItem() instanceof AscendanceArmorItem armor)
                || armor.ascendanceSlot() != slot) return original;
        return essenceAscendance$armorModels.computeIfAbsent(slot, AscendanceArmorModel::new);
    }

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

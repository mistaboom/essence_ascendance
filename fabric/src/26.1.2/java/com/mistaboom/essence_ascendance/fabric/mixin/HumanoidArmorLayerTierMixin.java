package com.mistaboom.essence_ascendance.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mistaboom.essence_ascendance.client.armor.AscendanceArmorModel;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.EnumMap;
import java.util.Map;

/** Keep the authored slot mesh inside vanilla's armor, trim, foil and baby render path. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerTierMixin {
    @Unique private final Map<EquipmentSlot, AscendanceArmorModel<HumanoidRenderState>>
            essenceAscendance$models = new EnumMap<>(EquipmentSlot.class);

    @ModifyVariable(method = "renderArmorPiece", at = @At("STORE"), ordinal = 0)
    private HumanoidModel<?> essenceAscendance$armorModel(HumanoidModel<?> original,
            @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) EquipmentSlot slot) {
        if (!(stack.getItem() instanceof AscendanceArmorItem armor)
                || armor.ascendanceSlot() != slot) return original;
        return essenceAscendance$models.computeIfAbsent(slot, AscendanceArmorModel::new);
    }
}

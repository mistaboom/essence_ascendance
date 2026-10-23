package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IItemExtension;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NeoForge exposes a stack-sensitive armor texture extension directly on Item.
 * Use that hook instead of replacing the armor renderer so trims and genuine
 * enchantment glint remain fully vanilla/NeoForge controlled.
 */
@Mixin(AscendanceArmorItem.class)
public abstract class AscendanceArmorItemClientMixin implements IItemExtension {

    @Override
    public ResourceLocation getArmorTexture(
            ItemStack stack,
            Entity entity,
            EquipmentSlot slot,
            ArmorMaterial.Layer layer,
            boolean innerModel
    ) {
        return EquipmentTierVisuals.armorTexture(stack, innerModel);
    }
}

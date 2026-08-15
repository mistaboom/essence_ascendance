package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.client.EquipmentTooltipClientState;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import java.util.Objects;

public final class AscendanceArmorItem
        extends ArmorItem
        implements EquipmentProfileItem {

    private final EquipmentSlot ascendanceSlot;

    public AscendanceArmorItem(
            Holder<ArmorMaterial> material,
            Type type,
            EquipmentSlot ascendanceSlot,
            Item.Properties properties
    ) {
        super(material, type, properties);
        this.ascendanceSlot = Objects.requireNonNull(
                ascendanceSlot,
                "Ascendance armor slot cannot be null"
        );
    }

    public EquipmentSlot ascendanceSlot() {
        return ascendanceSlot;
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.ARMOR.id();
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        super.appendHoverText(
                stack,
                context,
                tooltipComponents,
                tooltipFlag
        );
        EquipmentTooltipClientState.append(
                stack,
                tooltipComponents
        );
    }

}

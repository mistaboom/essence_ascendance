package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.client.EquipmentTooltipClientState;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

/*
 * Vanilla SwordItem supplies interaction semantics only.
 *
 * The Tier constructor values are bootstrap requirements. Actual intended
 * melee balance comes from:
 *
 * Ascendance tier -> EquipmentBaselineService -> melee profile -> invested
 * melee_damage / melee_attack_speed bonuses.
 */
public final class AscendanceMeleeWeaponItem
        extends SwordItem
        implements EquipmentProfileItem {

    public AscendanceMeleeWeaponItem(
            Tier tier,
            Item.Properties properties
    ) {
        super(tier, properties);
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.MELEE_WEAPON.id();
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

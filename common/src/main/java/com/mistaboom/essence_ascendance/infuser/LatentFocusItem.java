package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Craftable inert precursor that becomes the first Dormant Focus in an Infuser. */
public final class LatentFocusItem extends Item {

    public LatentFocusItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        FocusInfusionData.appendTooltip(stack, tooltipComponents);
    }
}

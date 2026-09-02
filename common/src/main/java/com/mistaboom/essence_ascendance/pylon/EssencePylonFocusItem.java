package com.mistaboom.essence_ascendance.pylon;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.infuser.FocusInfusionData;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

public final class EssencePylonFocusItem extends Item {

    private final EssencePylonFocusTier tier;

    public EssencePylonFocusItem(
            EssencePylonFocusTier tier,
            Properties properties
    ) {
        super(properties);
        this.tier = tier;
    }

    public EssencePylonFocusTier tier() {
        return tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Object blockEntity =
                context.getLevel().getBlockEntity(context.getClickedPos());
        if (!(blockEntity instanceof EssencePylonBlockEntity)
                && !(blockEntity instanceof EssenceInfuserBlockEntity)) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (blockEntity instanceof EssencePylonBlockEntity pylon) {
            return pylon.installOrSwapFocus(player, context.getHand());
        }

        EssenceInfuserBlockEntity infuser =
                (EssenceInfuserBlockEntity) blockEntity;
        return infuser.installOrSwapFocus(player, context.getHand())
                ? InteractionResult.CONSUME
                : InteractionResult.FAIL;
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

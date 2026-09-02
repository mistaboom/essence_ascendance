package com.mistaboom.essence_ascendance.pylon;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

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
}

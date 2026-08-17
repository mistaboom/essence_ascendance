package com.mistaboom.essence_ascendance.pylon;

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
        if (!(context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof EssencePylonBlockEntity pylon)) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        return pylon.installOrSwapFocus(player, context.getHand());
    }
}

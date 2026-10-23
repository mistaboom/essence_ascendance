package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.archive.ArchiveClientBridge;
import net.minecraft.world.InteractionHand;
//? if >=26.1.2
/*import net.minecraft.world.InteractionResult;*/
//? if <26.1.2
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Portable Archive access; it needs no machine, menu, world VFX or server-side screen class. */
public final class AscendanceArchiveItem extends Item {
    public AscendanceArchiveItem(Properties properties) { super(properties); }

    @Override
    //? if >=26.1.2
    /*public InteractionResult use(Level level, Player player, InteractionHand hand) {*/
    //? if <26.1.2
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) ArchiveClientBridge.open();
        //? if >=26.1.2
        /*return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;*/
        //? if <26.1.2
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}

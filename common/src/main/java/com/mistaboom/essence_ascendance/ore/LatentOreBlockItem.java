package com.mistaboom.essence_ascendance.ore;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LatentOreBlockItem extends BlockItem {
    public LatentOreBlockItem(Block block, Properties properties) { super(block, properties); }

    /** Intentional representative stack for recipe viewers and Archive item lists. */
    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        LatentOreHost.write(stack, Blocks.STONE.defaultBlockState());
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return LatentOreHost.read(stack)
                .map(host -> (Component) Component.translatable("item.essence_ascendance.latent_ore.host", host.getBlock().getName()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext context) {
        return LatentOreHost.read(context.getItemInHand()).isPresent() ? super.getPlacementState(context) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (LatentOreHost.read(stack).isEmpty())
            tooltip.add(Component.translatable("item.essence_ascendance.latent_ore.invalid_host").withStyle(ChatFormatting.RED));
    }
}

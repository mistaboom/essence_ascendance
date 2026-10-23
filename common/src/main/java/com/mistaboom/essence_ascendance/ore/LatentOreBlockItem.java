package com.mistaboom.essence_ascendance.ore;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=26.1.2
/*import net.minecraft.world.item.component.TooltipDisplay;*/
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
//? if >=26.1.2
/*import java.util.function.Consumer;*/

public final class LatentOreBlockItem extends BlockItem {
    @Nullable private final BlockState fixedHost;

    public LatentOreBlockItem(Block block, Properties properties) { this(block, properties, null); }

    public LatentOreBlockItem(Block block, Properties properties, @Nullable BlockState fixedHost) {
        super(block, properties);
        this.fixedHost = fixedHost;
    }

    @Nullable public BlockState fixedHost() { return fixedHost; }

    /** Aliases place the adaptive block, but must not replace its canonical Block.asItem mapping. */
    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
        if (fixedHost == null) super.registerBlocks(blockToItemMap, item);
    }

    /** Intentional representative stack for recipe viewers and Archive item lists. */
    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        LatentOreHost.write(stack, fixedHost == null ? Blocks.STONE.defaultBlockState() : fixedHost);
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

    //? if <26.1.2
    @Override
    //? if <26.1.2
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        //? if <26.1.2
        super.appendHoverText(stack, context, tooltip, flag);
        //? if <26.1.2
        if (LatentOreHost.read(stack).isEmpty())
            //? if <26.1.2
            tooltip.add(Component.translatable("item.essence_ascendance.latent_ore.invalid_host").withStyle(ChatFormatting.RED));
    //? if <26.1.2
    }

    //? if >=26.1.2
    /*@Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) { super.appendHoverText(stack, context, display, tooltip, flag); if (LatentOreHost.read(stack).isEmpty()) tooltip.accept(Component.translatable("item.essence_ascendance.latent_ore.invalid_host").withStyle(ChatFormatting.RED)); }*/
}

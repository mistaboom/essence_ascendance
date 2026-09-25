package com.mistaboom.essence_ascendance.ore;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

/** A single ordinary chunk-meshed ore block; its host contributes appearance, never special behavior. */
public final class LatentOreBlock extends Block implements EntityBlock {
    public LatentOreBlock(Properties properties) { super(properties); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LatentOreBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack) {
        super.setPlacedBy(level, pos, state, entity, stack);
        if (level.getBlockEntity(pos) instanceof LatentOreBlockEntity ore)
            LatentOreHost.read(stack).ifPresent(ore::setHost);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return LatentOreHost.host(level, pos).map(LatentOreHost::stack).orElseGet(() -> new ItemStack(this));
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder loot) {
        List<ItemStack> drops = super.getDrops(state, loot);
        if (loot.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof LatentOreBlockEntity ore) {
            ore.hostState().ifPresent(host -> drops.forEach(stack -> {
                if (stack.is(asItem())) LatentOreHost.write(stack, host);
            }));
        }
        return drops;
    }
}

package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;

/** World representation for a data-bearing full Essentium block carrier. */
public final class EssentiumBlock extends Block implements EntityBlock {

    /** Canonical EssenceTypes.ORDERED slot: offense, defense, vitality, mobility, gathering, utility. */
    public static final IntegerProperty ESSENCE = IntegerProperty.create("essence", 0, 5);
    /** Canonical EssenceFocusTier slot: dormant through transcendent. */
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 4);

    public EssentiumBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ESSENCE, 0)
                .setValue(TIER, 0));
    }

    public static InteractionResult placeCarrier(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Optional<EssentiumCarrierData.Value> value = EssentiumCarrierData.readValidated(stack);
        if (value.isEmpty()) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        BlockPlaceContext placement = new BlockPlaceContext(context);
        if (!placement.canPlace()) {
            return InteractionResult.FAIL;
        }

        BlockPos pos = placement.getClickedPos();
        if (context.getPlayer() != null
                && !level.mayInteract(context.getPlayer(), pos)) {
            return InteractionResult.FAIL;
        }
        EssentiumBlock block = EssenceInfuserContent.ESSENTIUM_BLOCK_BLOCK.get();
        BlockState state = block.stateFor(value.get());
        if (!state.canSurvive(level, pos) || !level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return InteractionResult.FAIL;
        }

        if (!(level.getBlockEntity(pos) instanceof EssentiumBlockEntity blockEntity)) {
            level.removeBlock(pos, false);
            return InteractionResult.FAIL;
        }
        blockEntity.setCarrier(stack);

        if (!level.isClientSide) {
            level.playSound(
                    null,
                    pos,
                    SoundEvents.STONE_PLACE,
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F
            );
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        //? if >=26.1.2
        /*return InteractionResult.SUCCESS;*/
        //? if <26.1.2
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private BlockState stateFor(EssentiumCarrierData.Value value) {
        return defaultBlockState()
                .setValue(ESSENCE, essenceIndex(value.essence()))
                .setValue(TIER, value.grade().ordinal());
    }

    private static int essenceIndex(EssenceDefinition essence) {
        int index = 0;
        for (EssenceDefinition candidate : EssenceRegistry.values()) {
            if (candidate.equals(essence)) {
                return index;
            }
            index++;
        }
        return 0;
    }

    public static int tint(BlockState state, int tintIndex) {
        if (tintIndex == 0) {
            return AscendancePalette.opaque(tierPrimaryRgb(tierFor(state.getValue(TIER))));
        }
        if (tintIndex == 1) {
            return AscendancePalette.opaque(
                    AscendancePalette.categoryRgb(essenceFor(state.getValue(ESSENCE)))
            );
        }
        return 0xFFFFFFFF;
    }

    //? if <26.1.2
    public static int tint(
            //? if <26.1.2
            BlockState state,
            //? if <26.1.2
            net.minecraft.world.level.BlockAndTintGetter level,
            //? if <26.1.2
            BlockPos pos,
            //? if <26.1.2
            int tintIndex
    //? if <26.1.2
    ) {
        //? if <26.1.2
        return tint(state, tintIndex);
    //? if <26.1.2
    }

    private static int tierPrimaryRgb(EssenceFocusTier tier) {
        return switch (tier) {
            case DORMANT -> AscendancePalette.DORMANT.primaryRgb();
            case AWAKENED -> AscendancePalette.AWAKENED.primaryRgb();
            case RESONANT -> AscendancePalette.RESONANT.primaryRgb();
            case ASCENDANT -> AscendancePalette.ASCENDANT.primaryRgb();
            case TRANSCENDENT -> AscendancePalette.TRANSCENDENT.primaryRgb();
        };
    }

    private static EssenceDefinition essenceFor(int index) {
        int current = 0;
        for (EssenceDefinition essence : EssenceRegistry.values()) {
            if (current++ == index) {
                return essence;
            }
        }
        return EssenceRegistry.values().iterator().next();
    }

    private static EssenceFocusTier tierFor(int index) {
        EssenceFocusTier[] tiers = EssenceFocusTier.values();
        return tiers[Math.max(0, Math.min(index, tiers.length - 1))];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssentiumBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType
    ) {
        return null;
    }

    @Override
    public BlockState playerWillDestroy(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player
    ) {
        if (player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof EssentiumBlockEntity blockEntity) {
            blockEntity.suppressDrop();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    //? if <26.1.2
    @Override
    //? if <26.1.2
    protected void onRemove(
            //? if <26.1.2
            BlockState state,
            //? if <26.1.2
            Level level,
            //? if <26.1.2
            BlockPos pos,
            //? if <26.1.2
            BlockState newState,
            //? if <26.1.2
            boolean movedByPiston
    //? if <26.1.2
    ) {
        //? if <26.1.2
        if (!state.is(newState.getBlock())
                //? if <26.1.2
                && !level.isClientSide
                //? if <26.1.2
                && level.getBlockEntity(pos) instanceof EssentiumBlockEntity blockEntity
                //? if <26.1.2
                && !blockEntity.isDropSuppressed()) {
            //? if <26.1.2
            ItemStack carrier = blockEntity.carrierStack();
            //? if <26.1.2
            if (!carrier.isEmpty()) {
                //? if <26.1.2
                popResource(level, pos, carrier);
            //? if <26.1.2
            }
        //? if <26.1.2
        }
        //? if <26.1.2
        super.onRemove(state, level, pos, newState, movedByPiston);
    //? if <26.1.2
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ESSENCE, TIER);
    }
}

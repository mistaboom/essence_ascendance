package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.gathering.*;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Vanilla cast/wait/bite/click fishing, with a separate held attachment press. */
public final class AscendanceFishingRodItem extends FishingRodItem implements EquipmentProfileItem {
    public AscendanceFishingRodItem(Properties properties) { super(properties); }
    @Override public ResourceLocation equipmentProfileId() { return EquipmentProfiles.FISHING_ROD.id(); }
    //? if >=26.1.2
    /*@Override public InteractionResult use(Level level, Player player, InteractionHand hand) {*/
    //? if <26.1.2
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) EquipmentFishingRodService.refreshNativeDurability(stack);
        //? if >=26.1.2
        /*if (!EquipmentFishingRodService.functional(stack)) return InteractionResult.FAIL;*/
        //? if <26.1.2
        if (!EquipmentFishingRodService.functional(stack)) return InteractionResultHolder.fail(stack);
        //? if >=26.1.2
        /*if (player instanceof ServerPlayer && !EssenceConfigManager.authoritativeReady()) return InteractionResult.FAIL;*/
        //? if <26.1.2
        if (player instanceof ServerPlayer && !EssenceConfigManager.authoritativeReady()) return InteractionResultHolder.fail(stack);
        // A native active-use latch stops Minecraft from repeating casts/retrievals during one held press.
        if (player.fishing instanceof RodHookAccess hook && hook.essenceAscendance$rodHand() != 0) {
            if (player instanceof ServerPlayer server && !AscendanceFishingService.valid(server, player.fishing)) {
                player.fishing.discard();
                //? if >=26.1.2
                /*return InteractionResult.FAIL;*/
                //? if <26.1.2
                return InteractionResultHolder.fail(stack);
            }
            //? if >=26.1.2
            /*if (player instanceof ServerPlayer && hook.essenceAscendance$rodState().hand != hand) return InteractionResult.FAIL;*/
            //? if <26.1.2
            if (player instanceof ServerPlayer && hook.essenceAscendance$rodState().hand != hand) return InteractionResultHolder.fail(stack);
            if (hook.essenceAscendance$attachment() != 0) {
                if (player instanceof ServerPlayer && !hook.essenceAscendance$rodState().press())
                    //? if >=26.1.2
                    /*return InteractionResult.FAIL;*/
                    //? if <26.1.2
                    return InteractionResultHolder.fail(stack);
                if (player instanceof ServerPlayer) hook.essenceAscendance$rodState().lastHeldInputTick = level.getGameTime();
                player.startUsingItem(hand);
                //? if >=26.1.2
                /*return InteractionResult.CONSUME;*/
                //? if <26.1.2
                return InteractionResultHolder.consume(stack);
            }
        }
        var result = super.use(level, player, hand);
        player.startUsingItem(hand);
        return result;
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return Integer.MAX_VALUE; }
    //? if >=26.1.2
    /*@Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.NONE; }*/
    //? if <26.1.2
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    //? if >=26.1.2
    /*@Override public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {*/
    //? if <26.1.2
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer player && player.fishing instanceof RodHookAccess hook
                && hook.essenceAscendance$rodHand() != 0 && hook.essenceAscendance$rodState().castStack == stack
                && hook.essenceAscendance$rodState().release()) player.fishing.discard();
        //? if >=26.1.2
        /*return false;*/
    }
    //? if >=26.1.2
    /*@Override public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, Entity entity, EquipmentSlot slot) { super.inventoryTick(stack, level, entity, slot); EquipmentFishingRodService.refreshNativeDurability(stack); }*/
    //? if <26.1.2
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        //? if <26.1.2
        super.inventoryTick(stack, level, entity, slot, selected);
        //? if <26.1.2
        if (!level.isClientSide) EquipmentFishingRodService.refreshNativeDurability(stack);
    //? if <26.1.2
    }
}

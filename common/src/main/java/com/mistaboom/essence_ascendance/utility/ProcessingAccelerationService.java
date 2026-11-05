package com.mistaboom.essence_ascendance.utility;

import com.mistaboom.essence_ascendance.network.MicroVisualFeedback;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Shared fractional native-tick accelerator. Eligibility is data-driven so
 * modded processors can opt into the block tag without a code adapter.
 */
public final class ProcessingAccelerationService {
    public static final TagKey<Block> INDUSTRIOUS_PROCESSORS = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("essence_ascendance", "industrious_processors"));
    private static final Map<BlockEntity, Double> FRACTIONAL_TICKS = new WeakHashMap<>();

    private ProcessingAccelerationService() { }

    public record Plan(ServerPlayer owner, int extraTicks, double multiplier) {
        private static final Plan NONE = new Plan(null, 0, 1.0);
        public boolean active() { return owner != null && multiplier > 1.0; }
    }

    public static Plan plan(BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved() || !blockEntity.getBlockState().is(INDUSTRIOUS_PROCESSORS)
                || !(blockEntity.getLevel() instanceof ServerLevel level)) {
            if (blockEntity != null) FRACTIONAL_TICKS.remove(blockEntity);
            return Plan.NONE;
        }

        Vec3 center = Vec3.atCenterOf(blockEntity.getBlockPos());
        UtilityAuraService.Match match = UtilityAuraService.strongest(level, center, SkillIds.INDUSTRIOUS_PRESENCE,
                context -> context.settings().utility().industriousPresence().radiusBlocks(),
                context -> context.settings().utility().industriousPresence().processingSpeedMultiplier());
        if (match == null || match.strength() <= 1.0) {
            FRACTIONAL_TICKS.remove(blockEntity);
            return Plan.NONE;
        }

        if (!SharedTargetWork.claim(level, blockEntity, "processor", level.getGameTime(), 1)) return Plan.NONE;

        double accrued = FRACTIONAL_TICKS.getOrDefault(blockEntity, 0.0) + (match.strength() - 1.0);
        int requestedTicks = Math.min(64, (int) Math.floor(accrued));
        int extraTicks = 0;
        while (extraTicks < requestedTicks && SharedTargetWork.visit(level, level.getGameTime())) extraTicks++;
        FRACTIONAL_TICKS.put(blockEntity, accrued - Math.floor(accrued));
        if (extraTicks > 0) com.mistaboom.essence_ascendance.skill.effect.SkillHudEvents.record(match.player(), SkillIds.INDUSTRIOUS_PRESENCE, "processing", match.strength());
        return new Plan(match.player(), extraTicks, match.strength());
    }

    public static List<ItemStack> snapshot(BlockEntity blockEntity) {
        List<ItemStack> stacks = new ArrayList<>();
        if (blockEntity instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) stacks.add(container.getItem(slot).copy());
        } else if (blockEntity instanceof CampfireBlockEntity campfire) {
            for (ItemStack stack : campfire.getItems()) stacks.add(stack.copy());
        }
        return stacks;
    }

    public static void acknowledgeChangedOutput(BlockEntity blockEntity, Plan plan, List<ItemStack> before) {
        if (plan.extraTicks() <= 0 || !completedOutput(blockEntity,before,snapshot(blockEntity))
                || !(blockEntity.getLevel() instanceof ServerLevel level)) return;
        long seed = blockEntity.getBlockPos().asLong() ^ level.getGameTime();
        MicroVisualFeedback.processorComplete(level,Vec3.atCenterOf(blockEntity.getBlockPos()).add(0,.56,0),
                blockEntity instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity,seed);

        int guiSlot = visibleOutputSlot(plan.owner(), blockEntity);
        if (guiSlot >= 0) MicroVisualFeedback.guiSlot(plan.owner(), TransientVisualIds.GUI_PROCESSOR_COMPLETE,
                guiSlot, SemanticVisualColor.UTILITY, seed);
    }

    private static int visibleOutputSlot(ServerPlayer player, BlockEntity blockEntity) {
        if (player.containerMenu instanceof AbstractFurnaceMenu
                && player.containerMenu.slots.size() > 2
                && player.containerMenu.slots.get(2).container == blockEntity) return 2;
        if (player.containerMenu instanceof BrewingStandMenu
                && player.containerMenu.slots.size() > 1
                && player.containerMenu.slots.get(1).container == blockEntity) return 1;
        for (int slot = 0; slot < player.containerMenu.slots.size(); slot++) {
            var candidate = player.containerMenu.slots.get(slot);
            if (candidate.container == blockEntity && candidate instanceof ResultSlot) return slot;
        }
        return -1;
    }

    /** Native output facts, not any inventory mutation (fuel/input consumption is not completion). */
    public static boolean completedOutput(BlockEntity source,List<ItemStack> before,List<ItemStack> after) {
        if(before.size()!=after.size()) return false;
        if(source instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)
            return after.size()>2 && increased(before.get(2),after.get(2));
        if(source instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity) {
            for(int slot=0;slot<Math.min(3,after.size());slot++)
                if(!after.get(slot).isEmpty() && !ItemStack.isSameItemSameComponents(before.get(slot),after.get(slot))) return true;
        }
        if(source instanceof CampfireBlockEntity) {
            for(int slot=0;slot<after.size();slot++) if(!before.get(slot).isEmpty() && after.get(slot).isEmpty()) return true;
        }
        // Tagged modded processors need an output adapter before claiming semantic completion.
        return false;
    }
    private static boolean increased(ItemStack before,ItemStack after) {
        return !after.isEmpty() && (before.isEmpty() || !ItemStack.isSameItemSameComponents(before,after) || after.getCount()>before.getCount());
    }
}

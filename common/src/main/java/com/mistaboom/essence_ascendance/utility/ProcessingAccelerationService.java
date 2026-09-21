package com.mistaboom.essence_ascendance.utility;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
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
}

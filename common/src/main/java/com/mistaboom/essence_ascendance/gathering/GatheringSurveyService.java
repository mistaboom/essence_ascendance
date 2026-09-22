package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared server-authoritative block survey used by Gathering information skills.
 * The scan never force-loads chunks. Skill potency (range) comes from generated balance;
 * refresh cadence and packet bounds are infrastructure safeguards only.
 */
public final class GatheringSurveyService {
    private static final int REFRESH_TICKS = 10;
    public static final int MAX_TARGETS = 8_192;
    private static final int[][] CARDINAL_NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private GatheringSurveyService() { }

    public static void tickOreSight(SkillEffectRuntime.Context context) {
        refresh(context, SkillIds.ORE_SIGHT, Mode.ORE,
                context.settings().gathering().oreSight());
    }

    public static void tickTreasureSense(SkillEffectRuntime.Context context) {
        refresh(context, SkillIds.TREASURE_SENSE, Mode.TREASURE,
                context.settings().gathering().treasureSense());
    }

    public static Snapshot snapshot(ServerPlayer player) {
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        if (context.isEffective(SkillIds.ORE_SIGHT)) {
            SurveyState state = context.existingState(SkillIds.ORE_SIGHT);
            return state == null ? Snapshot.empty(player, Mode.ORE) : state.snapshot(player, Mode.ORE);
        }
        if (context.isEffective(SkillIds.TREASURE_SENSE)) {
            SurveyState state = context.existingState(SkillIds.TREASURE_SENSE);
            return state == null ? Snapshot.empty(player, Mode.TREASURE) : state.snapshot(player, Mode.TREASURE);
        }
        return Snapshot.empty(player, Mode.NONE);
    }

    public static int targetCount(SkillEffectRuntime.Context context, net.minecraft.resources.ResourceLocation skill) {
        SurveyState state = context.existingState(skill);
        return state == null ? 0 : state.targets.size();
    }

    private static void refresh(SkillEffectRuntime.Context context, net.minecraft.resources.ResourceLocation skill,
                                Mode mode, GatheringBalanceSettings.Survey tuning) {
        SurveyState state = context.state(skill, SurveyState::new);
        ServerPlayer player = context.player();
        BlockPos center = player.blockPosition();
        boolean enabled = mode != Mode.ORE || holdingPickaxe(player);
        int radius = Math.max(0, (int) Math.ceil(tuning.rangeBlocks()));

        if (!enabled || radius <= 0) {
            state.replace(center, context.now(), List.of());
            return;
        }
        if (state.scannedAt != Long.MIN_VALUE
                && context.now() - state.scannedAt < REFRESH_TICKS) return;

        List<Target> targets = switch (mode) {
            case ORE -> scanOres(player, center, radius, tuning.rangeBlocks());
            case TREASURE -> scanTreasure(player.serverLevel(), center, radius, tuning.rangeBlocks());
            case NONE -> List.of();
        };
        state.replace(center, context.now(), targets);
    }

    private static boolean holdingPickaxe(ServerPlayer player) {
        return player.getMainHandItem().is(ItemTags.PICKAXES)
                || player.getOffhandItem().is(ItemTags.PICKAXES);
    }

    private static List<Target> scanOres(ServerPlayer player, BlockPos center, int radius, double rangeBlocks) {
        ServerLevel level = player.serverLevel();
        Map<Block, Long> values = NaturalOreDropService.surveyOreValues(player);
        if (values.isEmpty()) return List.of();
        Map<Long, OreNode> nodes = new HashMap<>();

        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (nodes.size() >= MAX_TARGETS) break;
            if (!withinRange(center, cursor, rangeBlocks) || !level.hasChunkAt(cursor)) continue;
            Block block = level.getBlockState(cursor).getBlock();
            Long value = values.get(block);
            if (value != null) nodes.put(cursor.asLong(), new OreNode(cursor.immutable(), Math.max(1L, value)));
        }
        if (nodes.isEmpty()) return List.of();

        Set<Long> unvisited = new HashSet<>(nodes.keySet());
        Set<Long> emphasized = Set.of();
        long bestValue = Long.MIN_VALUE;
        long bestAnchor = Long.MAX_VALUE;
        while (!unvisited.isEmpty()) {
            long seed = unvisited.iterator().next();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            Set<Long> deposit = new HashSet<>();
            queue.add(seed);
            unvisited.remove(seed);
            long total = 0L;
            long anchor = seed;
            while (!queue.isEmpty()) {
                long packed = queue.removeFirst();
                OreNode node = nodes.get(packed);
                if (node == null) continue;
                deposit.add(packed);
                total = saturatedAdd(total, node.value());
                anchor = Math.min(anchor, packed);
                BlockPos pos = node.pos();
                for (int[] step : CARDINAL_NEIGHBORS) {
                    long neighbor = pos.offset(step[0], step[1], step[2]).asLong();
                    if (unvisited.remove(neighbor)) queue.addLast(neighbor);
                }
            }
            if (total > bestValue || (total == bestValue && anchor < bestAnchor)) {
                bestValue = total;
                bestAnchor = anchor;
                emphasized = Set.copyOf(deposit);
            }
        }

        Set<Long> prime = emphasized;
        return nodes.values().stream()
                .sorted(Comparator.comparingLong(node -> distanceSquared(center, node.pos())))
                .map(node -> new Target(node.pos(), prime.contains(node.pos().asLong())))
                .toList();
    }

    private static List<Target> scanTreasure(ServerLevel level, BlockPos center, int radius, double rangeBlocks) {
        List<Target> result = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (result.size() >= MAX_TARGETS) break;
            if (!withinRange(center, cursor, rangeBlocks) || !level.hasChunkAt(cursor)) continue;
            BlockEntity entity = level.getBlockEntity(cursor);
            if (entity instanceof RandomizableContainerBlockEntity container && container.getLootTable() != null) {
                result.add(new Target(cursor.immutable(), false));
            }
        }
        result.sort(Comparator.comparingLong(target -> distanceSquared(center, target.pos())));
        return List.copyOf(result);
    }

    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dy = a.getY() - b.getY();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /** The scan box is rounded up for traversal, but detection uses the exact generated radius. */
    public static boolean withinRange(BlockPos center, BlockPos target, double rangeBlocks) {
        return rangeBlocks >= 0 && distanceSquared(center, target) <= rangeBlocks * rangeBlocks;
    }

    private static long saturatedAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }

    public enum Mode { NONE, ORE, TREASURE }

    public record Target(BlockPos pos, boolean emphasized) { }

    public record Snapshot(net.minecraft.resources.ResourceLocation dimension, Mode mode, List<Target> targets) {
        public Snapshot { targets = List.copyOf(targets); }
        static Snapshot empty(ServerPlayer player, Mode mode) {
            return new Snapshot(player.serverLevel().dimension().location(), mode, List.of());
        }
    }

    private record OreNode(BlockPos pos, long value) { }

    private static final class SurveyState implements SkillEffectState {
        private BlockPos center;
        private long scannedAt = Long.MIN_VALUE;
        private List<Target> targets = List.of();

        void replace(BlockPos center, long now, List<Target> targets) {
            this.center = center == null ? null : center.immutable();
            this.scannedAt = now;
            this.targets = List.copyOf(targets);
        }

        Snapshot snapshot(ServerPlayer player, Mode mode) {
            return new Snapshot(player.serverLevel().dimension().location(), mode, targets);
        }

        @Override public void clear() {
            center = null;
            scannedAt = Long.MIN_VALUE;
            targets = List.of();
        }
    }
}

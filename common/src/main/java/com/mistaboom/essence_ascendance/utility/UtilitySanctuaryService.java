package com.mistaboom.essence_ascendance.utility;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.RecentHostileCombat;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Natural-spawn suppression and persistent post-disengagement pacification for Sanctuary. */
public final class UtilitySanctuaryService {
    private UtilitySanctuaryService() { }

    public record Snapshot(int nearbyHostiles, int pacifiedHostiles, long nextDisengageAt) { }

    public static boolean suppressesNaturalMonsterSpawn(ServerLevel level, BlockPos position) {
        if (level == null || position == null) return false;
        return !UtilityAuraService.matches(level, Vec3.atCenterOf(position), SkillIds.SANCTUARY,
                context -> context.settings().utility().sanctuary().radiusBlocks()).isEmpty();
    }

    /** Called from Mob#setTarget so a mob that Sanctuary already disengaged cannot reacquire the same
     * player one AI tick later. Player-initiated hostile damage temporarily suspends this block. */
    public static boolean blocksTargeting(Mob mob, LivingEntity proposedTarget) {
        if (!(proposedTarget instanceof ServerPlayer player) || mob == null || !mob.isAlive() || mob.isRemoved()
                || mob.level() != player.level() || mob.getType().getCategory() != MobCategory.MONSTER) return false;
        var context = SkillEffectRuntime.context(player);
        if (!context.isEffective(SkillIds.SANCTUARY)) return false;
        var tuning = context.settings().utility().sanctuary();
        if (tuning.radiusBlocks() <= 0 || mob.distanceToSqr(player) > tuning.radiusBlocks() * tuning.radiusBlocks()
                || RecentHostileCombat.outgoingRemaining(context, tuning.disengageDelayTicks()) > 0) return false;
        State state = context.existingState(SkillIds.SANCTUARY);
        return state != null && state.pacified.contains(mob.getUUID());
    }

    public static void tick(SkillEffectRuntime.Context context) {
        var tuning = context.settings().utility().sanctuary();
        State state = context.state(SkillIds.SANCTUARY, State::new);
        state.refresh(context, tuning.radiusBlocks(), tuning.disengageDelayTicks());
    }

    public static Snapshot snapshot(SkillEffectRuntime.Context context) {
        State state = context.existingState(SkillIds.SANCTUARY);
        return state == null ? new Snapshot(0, 0, Long.MIN_VALUE) : state.snapshot();
    }

    private static final class State implements SkillEffectState {
        private final Map<UUID, Long> targetingSince = new HashMap<>();
        private final Set<UUID> pacified = new HashSet<>();
        private int nearbyHostiles;
        private int pacifiedHostiles;
        private long nextDisengageAt = Long.MIN_VALUE;

        void refresh(SkillEffectRuntime.Context context, double radiusBlocks, int disengageDelayTicks) {
            nearbyHostiles = 0;
            pacifiedHostiles = 0;
            nextDisengageAt = Long.MIN_VALUE;
            if (radiusBlocks <= 0) {
                targetingSince.clear();
                pacified.clear();
                return;
            }

            var player = context.player();
            ServerLevel level = player.serverLevel();
            double radiusSquared = radiusBlocks * radiusBlocks;
            long now = context.now();
            var mobs = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radiusBlocks),
                    mob -> mob.isAlive() && !mob.isRemoved()
                            && mob.getType().getCategory() == MobCategory.MONSTER
                            && mob.distanceToSqr(player) <= radiusSquared);
            Set<UUID> nearbyIds = new HashSet<>();
            for (Mob mob : mobs) nearbyIds.add(mob.getUUID());
            targetingSince.keySet().retainAll(nearbyIds);
            pacified.retainAll(nearbyIds);

            int aggressionRemaining = RecentHostileCombat.outgoingRemaining(context, disengageDelayTicks);
            for (Mob mob : mobs) {
                UUID id = mob.getUUID();
                boolean targeting = mob.getTarget() == player;

                if (pacified.contains(id)) {
                    if (aggressionRemaining <= 0) {
                        pacifiedHostiles++;
                        targetingSince.remove(id);
                        if (targeting) clearTarget(mob, player);
                    } else if (targeting) {
                        nearbyHostiles++;
                        rememberNext(now + aggressionRemaining);
                    }
                    continue;
                }

                if (!targeting) {
                    targetingSince.remove(id);
                    continue;
                }

                long since = targetingSince.computeIfAbsent(id, ignored -> now);
                long eligibleAt = Math.max(since + disengageDelayTicks, now + aggressionRemaining);
                if (aggressionRemaining <= 0 && now >= since + disengageDelayTicks) {
                    pacified.add(id);
                    pacifiedHostiles++;
                    targetingSince.remove(id);
                    clearTarget(mob, player);
                } else {
                    nearbyHostiles++;
                    rememberNext(eligibleAt);
                }
            }
        }

        private void rememberNext(long eligibleAt) {
            if (nextDisengageAt == Long.MIN_VALUE || eligibleAt < nextDisengageAt) nextDisengageAt = eligibleAt;
        }

        private static void clearTarget(Mob mob, ServerPlayer player) {
            if (mob.getTarget() != player) return;
            mob.setTarget(null);
            mob.getNavigation().stop();
        }

        Snapshot snapshot() {
            return new Snapshot(nearbyHostiles, pacifiedHostiles, nextDisengageAt);
        }

        @Override public void clear() {
            targetingSince.clear();
            pacified.clear();
            nearbyHostiles = 0;
            pacifiedHostiles = 0;
            nextDisengageAt = Long.MIN_VALUE;
        }
    }
}

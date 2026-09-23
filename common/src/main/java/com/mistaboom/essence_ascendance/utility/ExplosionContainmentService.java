package com.mistaboom.essence_ascendance.utility;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import com.mistaboom.essence_ascendance.network.MicroVisualFeedback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Shared field resolution and event state for Containment Field explosions. */
public final class ExplosionContainmentService {
    private ExplosionContainmentService() { }

    public record Snapshot(long lastContainedAt, int containedThisTick) { }

    public static List<UtilityAuraService.Match> matches(ServerLevel level, Vec3 center) {
        return UtilityAuraService.matches(level, center, SkillIds.CONTAINMENT_FIELD,
                context -> context.settings().utility().containmentField().radiusBlocks());
    }

    public static void record(List<UtilityAuraService.Match> matches, Vec3 center) {
        for (UtilityAuraService.Match match : matches) {
            State state = match.context().state(SkillIds.CONTAINMENT_FIELD, State::new);
            state.record(match.context().now());
        }
        if (!matches.isEmpty()) {
            UtilityAuraService.Match first = matches.getFirst();
            MicroVisualFeedback.utility(first.player().serverLevel(), center,
                    Double.doubleToLongBits(center.x + center.y * 31.0 + center.z * 961.0)
                            ^ first.context().now());
        }
    }

    public static Snapshot snapshot(SkillEffectRuntime.Context context) {
        State state = context.existingState(SkillIds.CONTAINMENT_FIELD);
        return state == null ? new Snapshot(Long.MIN_VALUE, 0) : state.snapshot();
    }

    private static final class State implements SkillEffectState {
        private long lastContainedAt = Long.MIN_VALUE;
        private int containedThisTick;

        void record(long now) {
            if (lastContainedAt != now) containedThisTick = 0;
            lastContainedAt = now;
            containedThisTick++;
        }

        Snapshot snapshot() { return new Snapshot(lastContainedAt, containedThisTick); }

        @Override public void clear() {
            lastContainedAt = Long.MIN_VALUE;
            containedThisTick = 0;
        }
    }
}

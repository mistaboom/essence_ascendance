package com.mistaboom.essence_ascendance.movement;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.config.PostureBalanceSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared native movement evidence for movement skills. Input alone and forced displacement never earn power. */
public final class PlayerMotionTracker {
    /** Network batching allowance, not an effect duration. Missing samples may hold, never build, momentum. */
    public static final int SAMPLE_GRACE_TICKS = 2;
    public record Sample(boolean discontinuity, boolean intentional, boolean forced, boolean moving,
                         boolean recentMovement, double distance, double turnDegrees, String reason, double spatialDistance) {
        public Sample(boolean discontinuity, boolean intentional, boolean forced, boolean moving,
                      boolean recentMovement, double distance, double turnDegrees, String reason) {
            this(discontinuity, intentional, forced, moving, recentMovement, distance, turnDegrees, reason, distance);
        }
        public boolean qualifiedSpatialMovement(double minimum) {
            return !discontinuity && !forced && Double.isFinite(spatialDistance)
                    && (spatialDistance >= minimum || recentMovement);
        }
    }
    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();
    private static final class State {
        final MotionSampleAccumulator pending = new MotionSampleAccumulator();
        final String dimension;
        PostureBalanceSettings.Movement policy;
        Vec3 position, acceptedVelocity;
        long velocityAt = Long.MIN_VALUE;
        float yaw;
        long lastTick = Long.MIN_VALUE, forcedUntil;
        Sample sample;
        State(ServerPlayer player, PostureBalanceSettings.Movement policy) {
            this.policy = policy; dimension = dimension(player); position = player.position(); yaw = player.getYRot();
        }
    }
    private PlayerMotionTracker() { }
    public static void track(ServerPlayer player, PostureBalanceSettings.Movement policy) {
        State state = STATES.computeIfAbsent(player, ignored -> new State(player, policy));
        state.policy = policy;
    }
    public static Sample sample(ServerPlayer player) {
        State state = STATES.get(player);
        if (state == null) return new Sample(true, false, false, false, false, 0, 0, "untracked");
        long now = player.level().getGameTime();
        if (state.lastTick == now && state.sample != null) return state.sample;
        var policy = state.policy;
        var packet = state.pending.consume(now, SAMPLE_GRACE_TICKS);
        double actual = player.position().distanceTo(state.position);
        double turn = Math.max(packet.turnDegrees(), MotionSampleAccumulator.angle(state.yaw, player.getYRot()));
        boolean discontinuity = !state.dimension.equals(dimension(player)) || packet.discontinuity()
                || !Double.isFinite(actual) || !Double.isFinite(turn) || actual > policy.maximumDisplacement()
                || state.lastTick != Long.MIN_VALUE && (now < state.lastTick || now - state.lastTick > 1);
        boolean intentional = AttunementGameplay.movementIntent(player, policy.intentTimeoutTicks());
        boolean unknown = actual > policy.stillExitDisplacement() && packet.spatialDistance() < policy.minimumDisplacement();
        boolean forced = unknown || now < state.forcedUntil;
        boolean moving = packet.horizontalDistance() >= policy.minimumDisplacement();
        String reason = discontinuity ? "movement_discontinuity" : forced ? "forced_or_uncorroborated_movement"
                : !intentional ? "no_movement_input" : moving ? "native_player_movement"
                : packet.recentMovement() ? "waiting_for_movement_sample" : "movement_stopped";
        state.sample = new Sample(discontinuity, intentional, forced, moving, packet.recentMovement(),
                packet.horizontalDistance(), turn, reason, packet.spatialDistance());
        state.lastTick = now; state.position = player.position(); state.yaw = player.getYRot();
        return state.sample;
    }
    /** Called from the existing accepted native movement adapter, never from the intent packet. */
    public static void moved(ServerPlayer player, Vec3 before, String dimension, float yaw) {
        State state = STATES.get(player); if (state == null) return;
        Vec3 delta = player.position().subtract(before);
        if (dimension.equals(dimension(player)) && Double.isFinite(delta.lengthSqr())
                && delta.length() <= state.policy.maximumDisplacement()
                && delta.length() >= state.policy.minimumDisplacement()) {
            state.acceptedVelocity = delta;
            state.velocityAt = player.level().getGameTime();
        }
        state.pending.record(player.level().getGameTime(), delta.horizontalDistance(),
                dimension.equals(dimension(player)) ? delta.length() : Double.NaN,
                MotionSampleAccumulator.angle(yaw, player.getYRot()),
                state.policy.minimumDisplacement(), state.policy.maximumDisplacement());
    }
    /** Most recent accepted native positional step, not an input-packet claim. Rotation-only packets
     * do not erase an airborne velocity; stale samples fall back to the native entity motion. */
    public static Vec3 velocity(ServerPlayer player) {
        State state = STATES.get(player);
        long now = player.level().getGameTime();
        if (state != null && state.acceptedVelocity != null && now >= state.velocityAt
                && now - state.velocityAt <= SAMPLE_GRACE_TICKS) return state.acceptedVelocity;
        return player.getDeltaMovement();
    }
    public static void forced(ServerPlayer player) {
        State state = STATES.get(player); if (state == null) return;
        state.forcedUntil = player.level().getGameTime() + state.policy.forcedMotionQuietTicks();
    }
    public static void externalMove(Entity entity, MoverType type, Vec3 before) {
        if (!(entity instanceof ServerPlayer player) || type == MoverType.PLAYER) return;
        State state = STATES.get(player);
        if (state != null && player.position().distanceTo(before) > state.policy.stillExitDisplacement()) forced(player);
    }
    public static void velocity(Entity entity, Vec3 proposed) {
        if (!(entity instanceof ServerPlayer player)) return;
        State state = STATES.get(player); if (state == null) return;
        Vec3 current = player.getDeltaMovement(); double epsilon = state.policy.minimumDisplacement();
        // Ignore ordinary gravity/drag. Native jump impulses are safe but cannot themselves generate momentum.
        if (!Double.isFinite(proposed.lengthSqr()) || proposed.horizontalDistance() > current.horizontalDistance() + epsilon
                || current.horizontalDistance() > epsilon && proposed.horizontalDistance() > epsilon
                && current.x * proposed.x + current.z * proposed.z < 0) forced(player);
    }
    public static void forget(ServerPlayer player) { STATES.remove(player); }
    private static String dimension(ServerPlayer player) { return player.level().dimension().location().toString(); }
}

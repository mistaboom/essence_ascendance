package com.mistaboom.essence_ascendance.utility;

import com.mistaboom.essence_ascendance.mixin.CreeperExplosionAccess;
import com.mistaboom.essence_ascendance.projectile.ProjectileFlightTraceAccess;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared server-authoritative sensing service for Utility's Sense Focus branch.
 * Skill potency comes only from generated settings. Refresh cadence, trace sizes,
 * and packet caps are implementation safeguards and never enter balance calculations.
 */
public final class UtilitySenseService {
    private static final int REFRESH_TICKS = 5;
    public static final int MAX_THREATS = 256;
    public static final int MAX_PROJECTILE_PATHS = 64;
    public static final int MAX_PROJECTILE_PATH_POINTS = 64;
    public static final int MAX_EXPLOSIONS = 64;
    // Vanilla 1.21.1 PrimedTnt has a fixed explosion radius and exposes no runtime field.
    // This mirrors native identity; it is not Essence Ascendance balance tuning.
    private static final double VANILLA_TNT_EXPLOSION_POWER = 4.0;

    private UtilitySenseService() { }

    public static void tickThreatSense(SkillEffectRuntime.Context context) {
        ThreatState state = context.state(SkillIds.THREAT_SENSE, ThreatState::new);
        boolean ledger = context.isEffective(SkillIds.HUNTERS_LEDGER);
        if (state.scannedAt != Long.MIN_VALUE
                && context.now() - state.scannedAt < REFRESH_TICKS
                && state.ledger == ledger) return;

        ServerPlayer player = context.player();
        double range = context.settings().utility().threatSense().rangeBlocks();
        int memoryTicks = context.settings().utility().huntersLedger().memoryTicks();
        state.refresh(player, context.now(), range, ledger, memoryTicks);
    }

    public static void tickWaylight(SkillEffectRuntime.Context context) {
        WaylightState state = context.state(SkillIds.WAYLIGHT, WaylightState::new);
        if (state.scannedAt != Long.MIN_VALUE && context.now() - state.scannedAt < REFRESH_TICKS) return;
        state.refresh(context.player(), context.now(), context.settings().utility().waylight().searchRadiusBlocks());
    }

    public static Snapshot snapshot(ServerPlayer player) {
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        if (context.isEffective(SkillIds.THREAT_SENSE)) {
            ThreatState state = context.existingState(SkillIds.THREAT_SENSE);
            return state == null ? Snapshot.empty(player, Mode.THREAT) : state.snapshot(player);
        }
        if (context.isEffective(SkillIds.WAYLIGHT)) {
            WaylightState state = context.existingState(SkillIds.WAYLIGHT);
            return state == null ? Snapshot.empty(player, Mode.WAYLIGHT) : state.snapshot(player);
        }
        return Snapshot.empty(player, Mode.NONE);
    }

    public static ThreatCounts threatCounts(SkillEffectRuntime.Context context) {
        ThreatState state = context.existingState(SkillIds.THREAT_SENSE);
        if (state == null) return new ThreatCounts(0, 0, 0, 0);
        int active = 0;
        int remembered = 0;
        for (Threat threat : state.threats) {
            if (threat.active()) active++; else remembered++;
        }
        return new ThreatCounts(active, remembered, state.projectilePaths.size(), state.explosions.size());
    }

    public static WaylightMarker waylightMarker(SkillEffectRuntime.Context context) {
        WaylightState state = context.existingState(SkillIds.WAYLIGHT);
        return state == null ? null : state.marker;
    }

    public enum Mode { NONE, THREAT, WAYLIGHT }

    public record Threat(int entityId, boolean active, float health, float maxHealth, int armor) { }

    /**
     * History points are observed positions from the projectile itself. Points from
     * historyPoints - 1 onward are the generic native-gravity projection.
     */
    public record ProjectilePath(List<Vec3> points, int historyPoints) {
        public ProjectilePath {
            points = List.copyOf(points);
            if (points.size() < 2 || points.size() > MAX_PROJECTILE_PATH_POINTS)
                throw new IllegalArgumentException("Utility projectile path point count out of bounds");
            if (historyPoints < 1 || historyPoints > points.size())
                throw new IllegalArgumentException("Utility projectile history count out of bounds");
        }
    }

    public record ExplosionDanger(Vec3 center, double radius) { }
    public record WaylightMarker(BlockPos feet) { }
    public record ThreatCounts(int activeThreats, int rememberedThreats, int projectilePaths, int explosions) { }

    public record Snapshot(ResourceLocation dimension, Mode mode, boolean ledger,
                           List<Threat> threats, List<ProjectilePath> projectilePaths,
                           List<ExplosionDanger> explosions, WaylightMarker waylight) {
        public Snapshot {
            threats = List.copyOf(threats);
            projectilePaths = List.copyOf(projectilePaths);
            explosions = List.copyOf(explosions);
        }

        static Snapshot empty(ServerPlayer player, Mode mode) {
            return new Snapshot(player.serverLevel().dimension().location(), mode, false,
                    List.of(), List.of(), List.of(), null);
        }
    }

    private static final class ThreatState implements SkillEffectState {
        private final Map<UUID, ObservedThreat> observed = new HashMap<>();
        private long scannedAt = Long.MIN_VALUE;
        private boolean ledger;
        private List<Threat> threats = List.of();
        private List<ProjectilePath> projectilePaths = List.of();
        private List<ExplosionDanger> explosions = List.of();

        void refresh(ServerPlayer player, long now, double range, boolean ledger, int memoryTicks) {
            this.scannedAt = now;
            this.ledger = ledger;
            if (range <= 0) {
                observed.clear();
                threats = List.of();
                projectilePaths = List.of();
                explosions = List.of();
                return;
            }

            ServerLevel level = player.serverLevel();
            AABB search = player.getBoundingBox().inflate(range);
            double rangeSq = range * range;
            List<Mob> active = level.getEntitiesOfClass(Mob.class, search,
                    mob -> mob.isAlive() && !mob.isRemoved() && mob.getTarget() == player
                            && mob.distanceToSqr(player) <= rangeSq);
            for (Mob mob : active) {
                observed.put(mob.getUUID(), new ObservedThreat(new WeakReference<>(mob), now));
            }

            observed.entrySet().removeIf(entry -> {
                LivingEntity entity = entry.getValue().entity.get();
                if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.level() != level
                        || entity.distanceToSqr(player) > rangeSq) return true;
                return !ledger && entry.getValue().lastActive < now;
            });
            if (ledger) {
                observed.entrySet().removeIf(entry -> now - entry.getValue().lastActive > memoryTicks);
            }

            List<Threat> resolved = new ArrayList<>(Math.min(MAX_THREATS, observed.size()));
            observed.values().stream()
                    .sorted(Comparator.comparingDouble(value -> {
                        LivingEntity entity = value.entity.get();
                        return entity == null ? Double.MAX_VALUE : entity.distanceToSqr(player);
                    }))
                    .limit(MAX_THREATS)
                    .forEach(value -> {
                        LivingEntity entity = value.entity.get();
                        if (entity != null) resolved.add(new Threat(entity.getId(), value.lastActive == now,
                                entity.getHealth(), entity.getMaxHealth(), entity.getArmorValue()));
                    });
            threats = List.copyOf(resolved);
            projectilePaths = dangerousProjectiles(player, search, range);
            explosions = primedExplosions(player, search);
        }

        Snapshot snapshot(ServerPlayer player) {
            return new Snapshot(player.serverLevel().dimension().location(), Mode.THREAT, ledger,
                    threats, projectilePaths, explosions, null);
        }

        @Override public void clear() {
            observed.clear();
            scannedAt = Long.MIN_VALUE;
            ledger = false;
            threats = List.of();
            projectilePaths = List.of();
            explosions = List.of();
        }
    }

    private static final class WaylightState implements SkillEffectState {
        private long scannedAt = Long.MIN_VALUE;
        private WaylightMarker marker;

        void refresh(ServerPlayer player, long now, double radius) {
            scannedAt = now;
            marker = radius <= 0 ? null : findWaylight(player.serverLevel(), player.blockPosition(), (int) Math.ceil(radius));
        }

        Snapshot snapshot(ServerPlayer player) {
            return new Snapshot(player.serverLevel().dimension().location(), Mode.WAYLIGHT, false,
                    List.of(), List.of(), List.of(), marker);
        }

        @Override public void clear() {
            scannedAt = Long.MIN_VALUE;
            marker = null;
        }
    }

    private static final class ObservedThreat {
        private final WeakReference<LivingEntity> entity;
        private final long lastActive;
        private ObservedThreat(WeakReference<LivingEntity> entity, long lastActive) {
            this.entity = entity;
            this.lastActive = lastActive;
        }
    }

    private static List<ProjectilePath> dangerousProjectiles(ServerPlayer player, AABB search, double range) {
        List<ProjectilePath> result = new ArrayList<>();
        ServerLevel level = player.serverLevel();
        AABB playerVolume = player.getBoundingBox();
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, search,
                entity -> entity.isAlive() && !entity.isRemoved() && entity.getOwner() != player)) {
            if (result.size() >= MAX_PROJECTILE_PATHS) break;
            ProjectilePath path = projectedProjectilePath(level, player, playerVolume, projectile, range);
            if (path != null) result.add(path);
        }
        return List.copyOf(result);
    }

    /**
     * Generic projectile preview: actual observed flight from launch/current history plus a
     * forward simulation using the projectile's live velocity and native gravity. This keeps
     * the path useful for vanilla and modded Projectile subclasses without naming weapon mobs.
     */
    private static ProjectilePath projectedProjectilePath(ServerLevel level, ServerPlayer player, AABB playerVolume,
                                                          Projectile projectile, double range) {
        Vec3 velocity = projectile.getDeltaMovement();
        if (velocity.lengthSqr() <= 1.0E-8) return null;

        List<Vec3> points = new ArrayList<>(MAX_PROJECTILE_PATH_POINTS);
        if (projectile instanceof ProjectileFlightTraceAccess traced) {
            List<Vec3> history = traced.essenceAscendance$flightTrace();
            int start = Math.max(0, history.size() - (MAX_PROJECTILE_PATH_POINTS / 2));
            for (int i = start; i < history.size(); i++) appendDistinct(points, history.get(i));
        }
        appendDistinct(points, projectile.position());
        int historyPoints = Math.max(1, points.size());

        Vec3 current = projectile.position();
        double gravity = projectile.getGravity();
        double drag = estimatedDrag(points, velocity, gravity);
        double projectilePadding = Math.max(projectile.getBbWidth(), projectile.getBbHeight()) * 0.5;
        AABB dangerVolume = playerVolume.inflate(projectilePadding);
        boolean intersectsPlayer = false;
        Vec3 playerCenter = playerVolume.getCenter();
        double currentDistanceSq = projectile.position().distanceToSqr(playerCenter);
        double closestFutureDistanceSq = currentDistanceSq;
        double traveled = 0.0;

        while (points.size() < MAX_PROJECTILE_PATH_POINTS && traveled < range && velocity.lengthSqr() > 1.0E-8) {
            Vec3 next = current.add(velocity);
            HitResult block = level.clip(new ClipContext(current, next,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
            if (block.getType() != HitResult.Type.MISS) next = block.getLocation();

            if (dangerVolume.clip(current, next).isPresent()) intersectsPlayer = true;
            closestFutureDistanceSq = Math.min(closestFutureDistanceSq, next.distanceToSqr(playerCenter));
            appendDistinct(points, next);
            traveled += current.distanceTo(next);
            if (block.getType() != HitResult.Type.MISS) break;

            current = next;
            velocity = velocity.scale(drag).add(0.0, -gravity, 0.0);
        }

        // A hostile near-miss remains useful while it is still closing on the player, but it
        // vanishes on the next sense refresh once its closest approach is behind it. This is
        // trajectory-based rather than projectile- or mob-specific, so modded Projectile
        // subclasses receive the same behavior automatically.
        boolean aimedByHostile = projectile.getOwner() instanceof Mob mob && mob.getTarget() == player;
        boolean stillClosing = closestFutureDistanceSq + 1.0E-6 < currentDistanceSq;
        if ((!intersectsPlayer && !(aimedByHostile && stillClosing)) || points.size() < 2) return null;
        return new ProjectilePath(points, Math.min(historyPoints, points.size()));
    }

    private static double estimatedDrag(List<Vec3> points, Vec3 currentVelocity, double gravity) {
        if (points.size() < 2) return 1.0;
        Vec3 previousVelocity = points.get(points.size() - 1).subtract(points.get(points.size() - 2));
        double denominator = previousVelocity.lengthSqr();
        if (denominator <= 1.0E-8) return 1.0;
        // For the common native model: nextVelocity = previousVelocity * drag - gravityY.
        Vec3 beforeGravity = currentVelocity.add(0.0, gravity, 0.0);
        double estimate = beforeGravity.dot(previousVelocity) / denominator;
        return Double.isFinite(estimate) && estimate >= 0.5 && estimate <= 1.2 ? estimate : 1.0;
    }

    private static void appendDistinct(List<Vec3> points, Vec3 point) {
        if (points.isEmpty() || points.get(points.size() - 1).distanceToSqr(point) > 1.0E-8) points.add(point);
    }

    private static List<ExplosionDanger> primedExplosions(ServerPlayer player, AABB search) {
        List<ExplosionDanger> result = new ArrayList<>();
        ServerLevel level = player.serverLevel();
        for (PrimedTnt tnt : level.getEntitiesOfClass(PrimedTnt.class, search,
                entity -> entity.isAlive() && !entity.isRemoved())) {
            if (result.size() >= MAX_EXPLOSIONS) break;
            result.add(new ExplosionDanger(tnt.position(), dangerRadiusFromBlastPower(VANILLA_TNT_EXPLOSION_POWER)));
        }
        if (result.size() < MAX_EXPLOSIONS) {
            for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, search,
                    entity -> entity.isAlive() && !entity.isRemoved() && entity.getSwelling(1.0F) > 0)) {
                if (result.size() >= MAX_EXPLOSIONS) break;
                double blastPower = ((CreeperExplosionAccess) creeper).essenceAscendance$getExplosionRadius();
                if (creeper.isPowered()) blastPower *= 2.0; // native charged-creeper multiplier
                result.add(new ExplosionDanger(creeper.position(), dangerRadiusFromBlastPower(blastPower)));
            }
        }
        return List.copyOf(result);
    }

    /** Vanilla explosion entity-damage candidate range is twice the blast-power argument. */
    private static double dangerRadiusFromBlastPower(double blastPower) {
        return blastPower * 2.0;
    }

    private static WaylightMarker findWaylight(ServerLevel level, BlockPos center, int radius) {
        long radiusSq = (long) radius * radius;
        Candidate best = null;
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            long dx = cursor.getX() - center.getX();
            long dy = cursor.getY() - center.getY();
            long dz = cursor.getZ() - center.getZ();
            long distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq > radiusSq || !level.hasChunkAt(cursor) || !spawnableHostileFooting(level, cursor)) continue;

            int localBrightness = currentLocalBrightness(level, cursor);
            Candidate next = new Candidate(cursor.immutable(), localBrightness, distanceSq);
            if (best == null || next.compareTo(best) < 0) best = next;
        }
        return best == null ? null : new WaylightMarker(best.pos);
    }

    /**
     * Deterministic natural-ground-monster footing check. Zombie supplies the ordinary
     * ON_GROUND monster footprint/placement identity; light limits come from the dimension.
     * We intentionally omit player-distance and mob-cap gates because Waylight is a spawn-
     * proofing indicator for the block itself, not a prediction of the next spawn attempt.
     */
    private static boolean spawnableHostileFooting(ServerLevel level, BlockPos feet) {
        BlockState body = level.getBlockState(feet);
        if (!SpawnPlacements.isSpawnPositionOk(EntityType.ZOMBIE, level, feet)) return false;
        if (!NaturalSpawner.isValidEmptySpawnBlock(level, feet, body, body.getFluidState(), EntityType.ZOMBIE)) return false;
        if (!level.noCollision(EntityType.ZOMBIE.getSpawnAABB(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5))) return false;

        if (level.getBrightness(LightLayer.BLOCK, feet) > level.dimensionType().monsterSpawnBlockLightLimit()) return false;
        return currentLocalBrightness(level, feet) <= level.dimensionType().monsterSpawnLightTest().getMaxValue();
    }

    private static int currentLocalBrightness(ServerLevel level, BlockPos pos) {
        return level.isThundering() ? level.getMaxLocalRawBrightness(pos, 10) : level.getMaxLocalRawBrightness(pos);
    }

    private record Candidate(BlockPos pos, int localBrightness, long distanceSq)
            implements Comparable<Candidate> {
        @Override public int compareTo(Candidate other) {
            // Once a block is genuinely spawnable, proximity is the useful signal. Darkness
            // only breaks equal-distance ties so time-of-night does not make the wisp wander.
            int distance = Long.compare(distanceSq, other.distanceSq);
            if (distance != 0) return distance;
            int darkness = Integer.compare(localBrightness, other.localBrightness);
            if (darkness != 0) return darkness;
            return Long.compare(pos.asLong(), other.pos.asLong());
        }
    }
}

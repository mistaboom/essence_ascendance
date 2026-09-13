package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.mixin.ProjectileNativeAccess;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.effect.OffenseProjectileEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-thread pipeline: snapshot -> steering -> sweep -> native damage -> continuation -> payload -> cleanup. */
public final class ProjectileRuntime {
    private static boolean stopping;
    private static final ThreadLocal<ImpactProbe> IMPACT = new ThreadLocal<>();
    private static final java.util.Map<ServerPlayer, java.util.LinkedHashMap<java.util.UUID, String>> RECENT = new java.util.WeakHashMap<>();
    private static final double EPSILON = 0.000001;
    private ProjectileRuntime() { }

    public static ProjectileState state(Projectile projectile) {
        return ((ProjectileStateAccess) projectile).essenceAscendance$state();
    }
    public static boolean secondary(Projectile projectile) {
        return ((ProjectileStateAccess) projectile).essenceAscendance$secondary();
    }
    public static boolean managed(Projectile projectile) {
        ProjectileState state = state(projectile);
        return state != null && state.managed() && !state.ended;
    }

    /** Called by Projectile.shoot at launch, after owner assignment. Unknown launch contracts fail closed. */
    public static void launch(Projectile projectile) {
        if (projectile.level().isClientSide) return;
        ProjectileStateAccess access = (ProjectileStateAccess) projectile;
        if (access.essenceAscendance$launchChecked()) return;
        access.essenceAscendance$launchChecked(true);
        access.essenceAscendance$secondary(EquipmentDamageService.isSecondarySkillDamage() || EquipmentDamageService.isReflectionInProgress());
        ProjectileSource source = ProjectileAdapters.source(projectile);
        if (source == ProjectileSource.UNSUPPORTED || access.essenceAscendance$secondary()
                || !(projectile.getOwner() instanceof ServerPlayer owner)
                || !validOwner(owner, projectile)) return;
        var effective = CommittedSkillService.effectiveIds(owner);
        var state = new ProjectileState(source, OffenseProjectileEffects.selectedPath(effective), owner.getUUID(),
                com.mistaboom.essence_ascendance.data.EssenceSavedData.get(owner.server).getPlayerData(owner.getUUID()).projectileLife(),
                owner.level().dimension().location(), owner.level().getGameTime(),
                com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime.resolvedSettings(owner).projectiles(),
                projectile instanceof AbstractArrow arrow ? Math.max(0, arrow.getPierceLevel()) : 0);
        state.payload = ProjectileImpactEffects.snapshot(owner, effective);
        access.essenceAscendance$state(state);
        if (state.managed()) remember(projectile, state);
        if (state.path == ProjectilePath.HOMING) {
            LivingEntity target = ProjectileTargeting.acquire(projectile, owner, owner.getEyePosition(),
                    owner.getLookAngle(), state.profile.acquisitionRange(), state.profile.acquisitionConeDegrees(), state.visited);
            if (target != null) state.target = target.getUUID();
        }
    }

    private static boolean validOwner(ServerPlayer owner, Projectile projectile) {
        return owner.isAlive() && !owner.isRemoved() && !owner.isSpectator() && owner.level() == projectile.level()
                && owner.server.getPlayerList().getPlayer(owner.getUUID()) == owner;
    }
    private static ServerPlayer owner(Projectile projectile, ProjectileState state) {
        return projectile.getOwner() instanceof ServerPlayer player && player.getUUID().equals(state.owner)
                && state.dimension.equals(projectile.level().dimension().location()) && validOwner(player, projectile)
                && state.ownerLife.equals(com.mistaboom.essence_ascendance.data.EssenceSavedData.get(player.server)
                        .getPlayerData(player.getUUID()).projectileLife()) ? player : null;
    }
    private static boolean finite(Vec3 value) { return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }

    /** Neither this service nor the adapter recreates the projectile. Each segment consumes the original distance budget. */
    public static void move(Projectile projectile) {
        ProjectileState state = state(projectile);
        if (projectile.level().isClientSide || state == null || state.ended) return;
        ServerPlayer owner = owner(projectile, state);
        long age = projectile.level().getGameTime() - state.launchedAt;
        if (owner == null || age < 0 || age >= state.profile.lifetimeTicks() || state.remainingTicks-- <= 0
                || ProjectileAdapters.source(projectile) != state.source || !finite(projectile.position())) {
            stop(projectile, state); return;
        }
        if (state.path == ProjectilePath.HOMING) ProjectileTargeting.steer(projectile, owner, state);
        double speed = projectile.getDeltaMovement().length();
        if (!finite(projectile.getDeltaMovement()) || speed < EPSILON || speed > state.maximumSpeed) {
            stop(projectile, state); return;
        }
        double tickDistance = Math.min(speed, state.remainingRange);
        int segments = 0;
        while (!projectile.isRemoved() && !state.ended && tickDistance > EPSILON) {
            if (++segments > state.maximumImpacts + 1 || owner(projectile, state) == null) { stop(projectile, state); break; }
            Vec3 start = projectile.position();
            Vec3 end = start.add(projectile.getDeltaMovement().normalize().scale(tickDistance));
            if (!loadedSegment(projectile, start, end)) { stop(projectile, state); break; }
            BlockHitResult block = projectile.level().clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
            Vec3 clipped = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            EntityHitResult entity = ProjectileCollision.firstEntity(projectile, start, clipped,
                    candidate -> !state.visited.contains(candidate.getUUID())
                            && ((ProjectileNativeAccess) projectile).essenceAscendance$canHit(candidate));
            HitResult hit = entity != null && (block.getType() == HitResult.Type.MISS
                    || start.distanceToSqr(entity.getLocation()) < start.distanceToSqr(clipped)) ? entity : block;
            Vec3 destination = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
            double distance = Math.min(tickDistance, start.distanceTo(destination));
            state.remainingRange = Math.max(0, state.remainingRange - distance); tickDistance -= distance;

            if (hit.getType() == HitResult.Type.MISS) { projectile.setPos(destination); break; }
            if (!ProjectilePlatform.allowImpact(projectile, hit)) {
                // A canceled impact never becomes a free penetration or a payload activation.
                projectile.setPos(destination); stop(projectile, state); break;
            }
            if (hit.getType() == HitResult.Type.BLOCK) {
                // Native arrows derive their embedding vector from the pre-impact position.
                ((ProjectileNativeAccess) projectile).essenceAscendance$impact(hit);
                state.ended = true;
                ProjectileImpactEffects.impact(state, new ProjectileImpactEffects.Impact(projectile, owner, hit, null, 0, false));
                break;
            }
            projectile.setPos(destination);
            Entity victim = ((EntityHitResult) hit).getEntity();
            if (!(victim instanceof LivingEntity living) || !ProjectileTargeting.canHarm(owner, victim)
                    || !state.visit(victim.getUUID())) { stop(projectile, state); break; }
            ImpactProbe previous = IMPACT.get();
            ImpactProbe probe = new ImpactProbe(projectile, living, state);
            IMPACT.set(probe);
            ProjectileDeflection deflection;
            try { deflection = ((ProjectileNativeAccess) projectile).essenceAscendance$impact(hit); }
            finally { if (previous == null) IMPACT.remove(); else IMPACT.set(previous); }
            boolean success = probe.accepted && probe.confirmedDamage > 0;
            boolean continuing = false;
            if (success && deflection == ProjectileDeflection.NONE && !projectile.isRemoved()
                    && owner(projectile, state) != null && state.canContinue()) {
                if (state.ricochets > 0) {
                    LivingEntity next = ProjectileTargeting.retarget(projectile, owner, state);
                    if (next != null && state.ricochet()) {
                        projectile.setDeltaMovement(next.getBoundingBox().getCenter().subtract(destination).normalize().scale(speed));
                        projectile.hasImpulse = true;
                        continuing = true;
                    }
                }
                if (!continuing) continuing = state.penetrate();
            }
            if (success) ProjectileImpactEffects.impact(state,
                    new ProjectileImpactEffects.Impact(projectile, owner, hit, living, probe.confirmedDamage, continuing));
            if (!continuing) stop(projectile, state);
        }
        if (!state.ended && state.remainingRange <= EPSILON) stop(projectile, state);
        if (!projectile.isRemoved()) ProjectileUtil.rotateTowardsMovement(projectile, 1);
        remember(projectile, state);
    }

    private static boolean loadedSegment(Projectile projectile, Vec3 start, Vec3 end) {
        // Never load chunks to acquire a target or continue a shot.
        int steps = (int) Math.ceil(start.distanceTo(end));
        for (int i = 0; i <= steps; i++) {
            if (!projectile.level().hasChunkAt(BlockPos.containing(start.lerp(end, i / (double) Math.max(1, steps))))) return false;
        }
        return true;
    }
    private static void stop(Projectile projectile, ProjectileState state) {
        state.ended = true; remember(projectile, state); projectile.discard();
    }

    @FunctionalInterface public interface DamageCall { boolean hurt(float amount); }
    public static boolean damage(Entity target, DamageSource source, float amount, DamageCall nativeDamage) {
        ImpactProbe probe = IMPACT.get();
        if (probe == null || source.getDirectEntity() != probe.projectile || target != probe.victim) return nativeDamage.hurt(amount);
        if (probe.damageAttempted) return false;
        probe.damageAttempted = true;
        // Resolve native shielding before exposing the bypass scope; otherwise detection would query itself.
        var defenses = ProjectileDefenseService.resolve(
                new ProjectileDefenseService.Context(probe.projectile, probe.victim, source), probe.state);
        float scaled = (float) (amount * probe.state.damageMultiplier * defenses.damageMultiplier());
        if (!Float.isFinite(scaled) || scaled <= 0) return false;
        probe.defenses = defenses;
        probe.defenseSource = source;
        try {
            boolean accepted = nativeDamage.hurt(scaled);
            probe.accepted = accepted;
            return accepted;
        } finally {
            probe.defenses = ProjectileDefenseService.Resolution.NONE;
            probe.defenseSource = null;
        }
    }
    static boolean bypassesDefense(ResourceLocation defense, LivingEntity target, DamageSource source) {
        ImpactProbe probe = IMPACT.get();
        return probe != null && probe.victim == target && probe.defenseSource == source
                && probe.state.path == ProjectilePath.PIERCING && probe.defenses.bypasses(defense)
                && source.getDirectEntity() == probe.projectile && owner(probe.projectile, probe.state) != null
                && EquipmentDamageService.skillDamagePlayer(target, source) != null;
    }
    /** Called by the existing measured-health hook; nested procs never qualify. */
    public static void confirmedDamage(LivingEntity target, DamageSource source, double damage) {
        ImpactProbe probe = IMPACT.get();
        if (probe != null && probe.victim == target && source.getDirectEntity() == probe.projectile) probe.confirmedDamage += damage;
    }
    public static boolean nativeImpact(Projectile projectile) {
        ImpactProbe probe = IMPACT.get(); return probe != null && probe.projectile == projectile;
    }
    public static boolean canDischarge(DamageSource source) {
        return !(source.getDirectEntity() instanceof Projectile projectile) || state(projectile) == null || !state(projectile).chargeDischarged;
    }
    public static void discharged(DamageSource source) {
        if (source.getDirectEntity() instanceof Projectile projectile && state(projectile) != null) state(projectile).chargeDischarged = true;
    }
    public static void serverStarting() { stopping = false; }
    public static void serverStopping() { stopping = true; }
    public static void forgetOwner(ServerPlayer owner) {
        // Clean shutdown saves valid entity snapshots. An ordinary logout ends this owner's projectile life.
        if (stopping) return;
        var saved = com.mistaboom.essence_ascendance.data.EssenceSavedData.get(owner.server);
        saved.getPlayerData(owner.getUUID()).invalidateProjectiles(); saved.setDirty();
        for (var level : owner.server.getAllLevels()) {
            List<Projectile> remove = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Projectile projectile && state(projectile) != null
                        && state(projectile).managed() && state(projectile).owner.equals(owner.getUUID())) remove.add(projectile);
            }
            for (Projectile projectile : remove) stop(projectile, state(projectile));
        }
        RECENT.remove(owner);
    }

    private static void remember(Projectile projectile, ProjectileState state) {
        if (!(projectile.getOwner() instanceof ServerPlayer player)) return;
        var recent = RECENT.computeIfAbsent(player, ignored -> new java.util.LinkedHashMap<>());
        recent.put(projectile.getUUID(), "#" + projectile.getId() + " " + state.source + " / " + state.path + " | range="
                + String.format(java.util.Locale.ROOT, "%.2f", state.remainingRange) + " | ticks=" + state.remainingTicks
                + " | visited=" + state.visited.size() + " | ricochets=" + state.ricochets
                + " | penetrations=" + state.nativePenetrations + "+" + state.skillPenetrations
                + " | damage x" + state.damageMultiplier + " | ended=" + state.ended);
        while (recent.size() > 8) recent.remove(recent.keySet().iterator().next());
    }
    public static List<String> diagnostics(ServerPlayer player) {
        var recent = RECENT.get(player);
        return recent == null ? List.of() : List.copyOf(recent.values());
    }
    public static void clearDiagnostics() { RECENT.clear(); }
    private static final class ImpactProbe {
        final Projectile projectile; final LivingEntity victim; final ProjectileState state;
        boolean damageAttempted, accepted; double confirmedDamage;
        DamageSource defenseSource;
        ProjectileDefenseService.Resolution defenses = ProjectileDefenseService.Resolution.NONE;
        ImpactProbe(Projectile projectile, LivingEntity victim, ProjectileState state) {
            this.projectile = projectile; this.victim = victim; this.state = state;
        }
    }
}

package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Effective player spatial scans issue short leases; each projectile resolves the strongest field once per tick. */
public final class ProjectileControlService {
    private record Field(WeakReference<ServerPlayer> owner, long expires, ProjectileBalanceSettings.Control tuning) { }
    private static final Map<Projectile, Map<UUID, Field>> FIELDS = new WeakHashMap<>();
    private static final Map<ServerPlayer, LinkedHashMap<String, String>> RECENT = new WeakHashMap<>();
    private ProjectileControlService() { }
    public static ProjectileControlState state(Projectile projectile) {
        var access = (ProjectileStateAccess) projectile;
        if (access.essenceAscendance$control() == null) access.essenceAscendance$control(new ProjectileControlState());
        return access.essenceAscendance$control();
    }
    public static void scan(SkillEffectRuntime.Context context) {
        ServerPlayer player = context.player();
        var tuning = context.settings().projectiles().control();
        if (!ProjectileOwnership.validDefender(player) || Math.floorMod(context.now() + player.getId(), tuning.scanCadenceTicks()) != 0) return;
        double outer = tuning.outerRadius();
        // Spatial broad phase only, followed by deterministic nearest-first bounded effect processing.
        var nearby = player.serverLevel().getEntitiesOfClass(Projectile.class,
                new AABB(player.position(), player.position()).inflate(outer), ProjectileAdapters::inFlight);
        nearby.sort(Comparator.<Projectile>comparingDouble(p -> p.distanceToSqr(player)).thenComparing(Projectile::getUUID));
        int examined = 0, active = 0;
        for (var projectile : nearby) {
            if (++examined > context.settings().projectiles().maximumImpacts()) break;
            var ownership = ProjectileOwnership.resolve(projectile, player);
            if (!ownership.hostile()) continue;
            var claims = FIELDS.computeIfAbsent(projectile, ignored -> new LinkedHashMap<>());
            claims.put(player.getUUID(), new Field(new WeakReference<>(player), context.now() + tuning.scanCadenceTicks() + 1, tuning));
            active++;
            // A claim is consumed on the projectile's next movement tick, independent of entity iteration order.
            state(projectile);
        }
        record(player, "drag", "Drag fields: active=" + active + " examined=" + Math.min(examined, context.settings().projectiles().maximumImpacts())
                + " cadence=" + tuning.scanCadenceTicks() + " overlap=strongest; exit=remove field factor; no prevention credit");
    }
    public static void beforeFlight(Projectile projectile) {
        if (projectile.level().isClientSide) {
            var access = (ProjectileStateAccess) projectile;
            float factor = access.essenceAscendance$flightScale();
            if (factor != 1 || access.essenceAscendance$control() != null) {
                var presentation = state(projectile);
                presentation.dragFactor = factor;
                // Motion packets already contain scaled velocity. Only predict subsequent physics here.
                presentation.physicsInput = factor < 1 ? projectile.getDeltaMovement() : null;
            }
            return;
        }
        var state = ((ProjectileStateAccess) projectile).essenceAscendance$control();
        if (state == null) return;
        long now = projectile.level().getGameTime();
        if (state.lastFlightTick == now) return;
        state.lastFlightTick = now;
        double factor = 1;
        var claims = FIELDS.get(projectile);
        if (claims != null) {
            var it = claims.values().iterator();
            while (it.hasNext()) {
                var field = it.next();
                var player = field.owner().get();
                if (player == null || field.expires() < now || !ProjectileOwnership.resolve(projectile, player).hostile()
                        || !CommittedSkillService.effectiveIds(player).contains(SkillIds.PROJECTILE_DRAG_FIELD)) { it.remove(); continue; }
                Vec3 center = player.getBoundingBox().getCenter();
                Vec3 toward = center.subtract(projectile.position());
                factor = Math.min(factor, ProjectileControlMath.factor(toward.length(), field.tuning()));
            }
            if (claims.isEmpty()) FIELDS.remove(projectile);
        }
        // Use exactly the scale sent to clients. All directions stay viscous until the shot leaves the pocket.
        factor = (float) factor;
        ((ProjectileStateAccess) projectile).essenceAscendance$flightScale((float) factor);
        // Unloaded/reloaded leases are deliberately absent. A persisted factor is undone once on resumption.
        if (state.dragFactor != 1 || factor != 1) {
            var launch = ProjectileRuntime.state(projectile);
            double maximum = launch == null ? com.mistaboom.essence_ascendance.config.EssenceConfigManager.skillEffects().projectiles().maximumSpeed()
                    : launch.maximumSpeed;
            boolean entered = state.dragFactor == 1 && factor < 0.999;
            Vec3 velocity = ProjectileControlMath.velocity(projectile.getDeltaMovement(), state.dragFactor, factor, maximum);
            if (!ProjectileControlMath.finite(velocity)) { projectile.discard(); return; }
            projectile.setDeltaMovement(velocity); projectile.hasImpulse = true; state.dragFactor = factor;
            state.physicsInput = factor < 1 ? velocity : null;
            if (factor < 0.999 && projectile.level() instanceof net.minecraft.server.level.ServerLevel level
                    && (entered || Math.floorMod(now + projectile.getId(), 8) == 0))
                com.mistaboom.essence_ascendance.network.CombatVisualFeedback.projectileDrag(level, projectile, factor);
            if (entered) projectile.level().playSound(null, projectile.blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE,
                    SoundSource.PLAYERS, 0.35F, 0.8F);
            if (claims != null) for (var field : claims.values()) {
                var player = field.owner().get();
                if (player != null && factor < 1) com.mistaboom.essence_ascendance.skill.effect.SkillHudEvents.record(player, SkillIds.PROJECTILE_DRAG_FIELD, "slowed", (1 - factor) * 100);
                if (player != null) record(player, "drag_sample", "Projectile #" + projectile.getId()
                        + " owner=" + (projectile.getOwner() == null ? "ownerless damaging" : projectile.getOwner().getUUID())
                        + " hostile=" + ProjectileOwnership.resolve(projectile, player).decision()
                        + " fields=" + claims.size() + " resolved factor=" + String.format(java.util.Locale.ROOT, "%.3f", factor)
                        + " speed=" + String.format(java.util.Locale.ROOT, "%.3f", velocity.length()));
            }
        }
    }
    /** Called after an arrow applies native inertia/gravity so downward acceleration enters the same jello response. */
    public static void afterNativePhysics(Projectile projectile, Vec3 input, double inertia) {
        var control = ((ProjectileStateAccess) projectile).essenceAscendance$control();
        if (control == null) return;
        control.physicsInput = null;
        if (projectile.isRemoved() || !projectile.isAlive()) return;
        if (control.dragFactor == 1) return;
        var launch = ProjectileRuntime.state(projectile);
        double maximum = projectile.level().isClientSide ? Double.MAX_VALUE
                : launch == null ? com.mistaboom.essence_ascendance.config.EssenceConfigManager.skillEffects().projectiles().maximumSpeed()
                : launch.maximumSpeed;
        Vec3 velocity = ProjectileControlMath.afterPhysics(input, projectile.getDeltaMovement(), inertia, control.dragFactor, maximum);
        if (!ProjectileControlMath.finite(velocity) || velocity.lengthSqr() < 0.000000000001) { projectile.discard(); return; }
        projectile.setDeltaMovement(velocity); projectile.hasImpulse = true;
    }
    /** Native arrows take their ordinary tick path; the pre-movement input was captured by beforeFlight. */
    public static void afterNativeArrowFlight(Projectile projectile, double inertia) {
        var control = ((ProjectileStateAccess) projectile).essenceAscendance$control();
        if (control != null && control.physicsInput != null) afterNativePhysics(projectile, control.physicsInput, inertia);
    }
    public static void release(Projectile projectile) { FIELDS.remove(projectile); }
    public static void record(ServerPlayer player, String key, String decision) {
        var recent = RECENT.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        recent.put(key, decision);
        while (recent.size() > 8) recent.remove(recent.keySet().iterator().next());
    }
    public static List<String> diagnostics(ServerPlayer player) {
        var result = new ArrayList<String>(); var recent = RECENT.get(player);
        if (recent != null) result.addAll(recent.values());
        return List.copyOf(result);
    }
    public static void forget(ServerPlayer player) { RECENT.remove(player); ProjectileInterceptionService.forget(player); }
    public static void clear() { FIELDS.clear(); RECENT.clear(); ProjectileInterceptionService.clear(); }
}

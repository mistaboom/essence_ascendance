package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.mixin.ProjectileNativeAccess;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.network.CombatVisualFeedback;
import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import com.mistaboom.essence_ascendance.visual.transientfx.VisualIntensity;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Only actual server attack/animation hooks enter this service; proximity scanning cannot intercept. */
public final class ProjectileInterceptionService {
    public enum Destination { AIMED, SOURCE, DESTROY }
    private static final Map<ServerPlayer, ProjectileSwingLedger> SWINGS = new WeakHashMap<>();
    private ProjectileInterceptionService() { }
    public static Destination destination(boolean crosshair, boolean source) {
        return crosshair ? Destination.AIMED : source ? Destination.SOURCE : Destination.DESTROY;
    }
    public static boolean attack(ServerPlayer player) {
        if (!eligible(player)) return false;
        var ledger = SWINGS.computeIfAbsent(player, ignored -> new ProjectileSwingLedger());
        ledger.attackAnimation(player.level().getGameTime());
        return intercept(player, ledger, false);
    }
    public static void swing(ServerPlayer player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !eligible(player)) return;
        if (((com.mistaboom.essence_ascendance.mixin.ProjectileMiningAccess) player.gameMode).essenceAscendance$destroyingBlock()) return;
        var ledger = SWINGS.computeIfAbsent(player, ignored -> new ProjectileSwingLedger());
        if (ledger.animate(player.level().getGameTime())) intercept(player, ledger, true);
    }
    /** Arrows are not vanilla pick targets: the first left click can target a block behind the intended shot. */
    public static boolean startBlockAttack(ServerPlayer player, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action action) {
        if (!eligible(player)) return false;
        var ledger = SWINGS.computeIfAbsent(player, ignored -> new ProjectileSwingLedger());
        return ledger.startBlockAttack(player.level().getGameTime(), action,
                ((com.mistaboom.essence_ascendance.mixin.ProjectileMiningAccess) player.gameMode).essenceAscendance$destroyingBlock(),
                () -> intercept(player, ledger, true));
    }
    /** Vanilla uses the same animation packet for some right-click actions, so remember their accepted server route. */
    public static void used(ServerPlayer player) {
        if (eligible(player)) SWINGS.computeIfAbsent(player, ignored -> new ProjectileSwingLedger()).used(player.level().getGameTime());
    }
    private static boolean eligible(ServerPlayer player) {
        return ProjectileOwnership.validDefender(player) && !player.isUsingItem()
                && CommittedSkillService.effectiveIds(player).contains(SkillIds.INTERCEPTOR);
    }
    private static boolean intercept(ServerPlayer player, ProjectileSwingLedger ledger, boolean airSwing) {
        var settings = SkillEffectRuntime.resolvedSettings(player).projectiles();
        var tuning = settings.control();
        double readiness = player.getAttackStrengthScale(0);
        if (!ledger.process(player.level().getGameTime(), readiness, tuning.readinessThreshold())) {
            ProjectileControlService.record(player, "swing", "Interception rejected: attack readiness or duplicate swing; actual="
                    + String.format(java.util.Locale.ROOT, "%.3f", readiness) + " required=" + tuning.readinessThreshold()); return false;
        }
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        double range = Math.min(tuning.swingRange(), player.entityInteractionRange());
        List<Vec3> rays = ProjectileControlMath.fan(look, range, tuning.swingRadius(), tuning.swingHalfAngleDegrees(), settings.maximumImpacts())
                .stream().map(offset -> {
                    var block = player.level().clip(new ClipContext(eye, eye.add(offset), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                    return block.getType() == HitResult.Type.MISS ? offset : block.getLocation().subtract(eye);
                }).toList();
        var nearby = player.serverLevel().getEntitiesOfClass(Projectile.class, new AABB(eye, eye).inflate(range + tuning.swingRadius()),
                projectile -> ProjectileAdapters.inFlight(projectile)
                        && ProjectileControlMath.intersects(projectile.getBoundingBox(), eye, look, rays, tuning.swingRadius())
                        && ProjectileTargeting.visible(projectile, eye, projectile.getBoundingBox().getCenter()));
        nearby.sort(Comparator.<Projectile>comparingDouble(p -> p.position().distanceToSqr(eye)).thenComparing(Entity::getUUID));
        boolean theft = CommittedSkillService.effectiveIds(player).contains(SkillIds.TRAJECTORY_THEFT);
        int successes = 0, examined = 0;
        for (var projectile : nearby) {
            if (++examined > settings.maximumImpacts() || successes >= tuning.swingBudget()) break;
            var ownership = ProjectileOwnership.resolve(projectile, player);
            if (!ownership.hostile()) {
                ProjectileControlService.record(player, "ownership", "Projectile #" + projectile.getId() + " rejected: " + ownership.decision()); continue;
            }
            String result = theft ? steal(projectile, player, ownership, tuning) : "Interceptor: destroyed";
            if (!theft || result.startsWith("Destroyed")) projectile.discard();
            ProjectileControlService.release(projectile);
            ProjectileControlService.record(player, "theft", "Projectile #" + projectile.getId() + " " + ownership.decision() + " -> " + result);
            CombatVisualFeedback.at(player.serverLevel(), TransientVisualIds.WORLD_PROJECTILE_INTERCEPT,
                    projectile.position(), projectile.isRemoved() ? look : projectile.getDeltaMovement(), 1.0F, 1.0F,
                    VisualIntensity.STANDARD, SemanticVisualColor.DEFENSE, 16,
                    player.level().getGameTime() ^ projectile.getUUID().getLeastSignificantBits(),
                    0.0F, 0.0F);
            successes++;
        }
        if (successes > 0) {
            com.mistaboom.essence_ascendance.skill.effect.SkillHudEvents.record(player, SkillIds.INTERCEPTOR, "targets", successes);
            if (airSwing) player.resetAttackStrengthTicker();
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.45F, 1.2F);
        }
        ProjectileControlService.record(player, "swing", "Interception: intersected=" + nearby.size() + " accepted=" + successes
                + " budget=" + tuning.swingBudget() + " readiness=" + tuning.readinessThreshold());
        return successes > 0;
    }
    private static String steal(Projectile projectile, ServerPlayer defender, ProjectileOwnership.Resolution ownership,
                                ProjectileBalanceSettings.Control tuning) {
        var previous = ProjectileRuntime.state(projectile);
        if (previous != null && previous.redirected) return "Destroyed: this projectile was already stolen";
        if (previous == null || previous.ended || previous.remainingTicks <= 0 || previous.remainingRange <= 0
                || projectile.level().getGameTime() - previous.launchedAt >= previous.profile.lifetimeTicks())
            return "Destroyed: missing or exhausted launch snapshot";
        var control = ProjectileControlService.state(projectile);
        LivingEntity aimed = ProjectileTargeting.crosshair(projectile, defender, tuning.theftTargetRange(), tuning.theftAimConeDegrees(), previous.visited);
        LivingEntity source = ownership.source();
        if (source != null && (!ProjectileTargeting.canHarm(defender, source) || defender.isAlliedTo(source)
                || previous.visited.contains(source.getUUID()) || source.distanceToSqr(defender) > tuning.theftTargetRange() * tuning.theftTargetRange()
                || !ProjectileTargeting.visible(projectile, projectile.position(), source.getBoundingBox().getCenter()))) source = null;
        var decision = destination(aimed != null, source != null);
        if (decision == Destination.DESTROY) return "Destroyed: no eligible aimed creature or responsible source";
        LivingEntity target = decision == Destination.AIMED ? aimed : source;
        Vec3 delta = target.getBoundingBox().getCenter().subtract(projectile.position());
        double speed = ProjectileControlMath.redirectSpeed(projectile.getDeltaMovement(), control.dragFactor,
                previous.profile.speed(), tuning.theftSpeedMultiplier(), previous.maximumSpeed);
        if (!Double.isFinite(speed) || speed < 0.000001 || delta.lengthSqr() < 0.000001) return "Destroyed: invalid redirect velocity";
        if (!control.redirect(projectile.getOwner() == null ? null : projectile.getOwner().getUUID(), defender.getUUID(), tuning.redirectBudget()))
            return "Destroyed: redirect budget exhausted or owner loop";
        var next = previous.transferTo(defender.getUUID(), EssenceSavedData.get(defender.server).getPlayerData(defender.getUUID()).projectileLife(),
                tuning.theftTurnDegreesPerTick());
        if (next == null) return "Destroyed: invalid transfer snapshot";
        if (!previous.managed()) {
            // Native piercing records earlier victims by live entity ID. Fold them into the persisted UUID ledger once.
            for (int id : ((ProjectileStateAccess) projectile).essenceAscendance$nativeHitIds()) {
                Entity victim = projectile.level().getEntity(id);
                if (victim != null) next.visited.add(victim.getUUID());
            }
        }
        if (next.visited.contains(target.getUUID())) return "Destroyed: destination was already hit";
        next.target = target.getUUID();
        ((ProjectileStateAccess) projectile).essenceAscendance$state(next);
        ProjectileOwnership.transferNative(projectile, defender);
        // Re-evaluate departure from the new owner; source immunity belongs to the defender after transfer.
        ((ProjectileNativeAccess) projectile).essenceAscendance$leftOwner(false);
        projectile.setDeltaMovement(delta.normalize().scale(speed)); projectile.hasImpulse = true;
        control.dragFactor = 1;
        ((ProjectileStateAccess) projectile).essenceAscendance$flightScale(1);
        control.physicsInput = null;
        com.mistaboom.essence_ascendance.skill.effect.SkillHudEvents.record(defender, SkillIds.TRAJECTORY_THEFT, "redirected", 1);
        CombatVisualFeedback.link(defender.serverLevel(), TransientVisualIds.WORLD_PROJECTILE_REDIRECT,
                projectile.position(), null, null, target.getBoundingBox().getCenter(), projectile.getDeltaMovement(),
                1.10F, 1.10F, VisualIntensity.MAJOR, SemanticVisualColor.MAGIC, 20,
                defender.level().getGameTime() ^ projectile.getUUID().getMostSignificantBits(),
                control.remainingRedirects, 1.0F);
        return "Trajectory Theft " + decision + " target=" + target.getUUID() + " speed="
                + String.format(java.util.Locale.ROOT, "%.2f", speed) + " homing=" + next.profile.turnDegreesPerTick()
                + "deg/tick remaining redirects=" + control.remainingRedirects + "; native damage/physics retained; path/payload cleared";
    }
    public static void forget(ServerPlayer player) { SWINGS.remove(player); }
    public static void clear() { SWINGS.clear(); }
}

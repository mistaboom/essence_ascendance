package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

/** Exercises production classification, physical response, swing geometry/ledger, transfer and persistence contracts. */
public final class ProjectileControlTest {
    private static int assertions;
    private static final UUID SHOOTER = new UUID(1, 1), DEFENDER = new UUID(1, 2), LIFE = new UUID(1, 3);
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        classification(); drag(); swings(); transfer(); nativeOwnership();
        System.out.println("Projectile control contracts passed: " + assertions);
    }
    private static void classification() {
        check(ProjectileAdapters.classify(EntityType.ARROW, Arrow.class, false, false) == ProjectileSource.RANGED_PHYSICAL,
                "Vanilla/Ascendance Bow arrows use the same exact native contract");
        check(ProjectileAdapters.classify(EntityType.SPECTRAL_ARROW, SpectralArrow.class, false, false) == ProjectileSource.RANGED_PHYSICAL,
                "Spectral arrow adapter");
        check(ProjectileAdapters.classify(null, MagicBoltEntity.class, false, false) == ProjectileSource.ASCENDANCE_MAGIC,
                "Real final Caster entity uses the shared contract");
        check(ProjectileAdapters.classify(EntityType.ARROW, Object.class, false, false) == ProjectileSource.UNSUPPORTED,
                "An unregistered custom arrow class cannot inherit opt-in");
        check(ProjectileAdapters.classify(EntityType.SNOWBALL, Snowball.class, false, false) == ProjectileSource.UNSUPPORTED
                && ProjectileAdapters.classify(EntityType.ENDER_PEARL, ThrownEnderpearl.class, false, false) == ProjectileSource.UNSUPPORTED,
                "Utility projectiles excluded");
        check(ProjectileAdapters.classify(EntityType.ARROW, Arrow.class, true, false) == ProjectileSource.UNSUPPORTED
                && ProjectileAdapters.classify(EntityType.ARROW, Arrow.class, false, true) == ProjectileSource.SECONDARY,
                "No-physics and secondary sources excluded");
        check(decision(true, true, true, false, false, false, true) == ProjectileOwnership.Decision.HOSTILE, "Hostile shooter");
        check(decision(true, true, true, true, false, false, true) == ProjectileOwnership.Decision.OWNERLESS_DAMAGING, "Dispenser shot deliberately hostile");
        check(decision(true, true, false, false, false, false, true) == ProjectileOwnership.Decision.INVALID_OWNER, "Invalid ownership never becomes ownerless");
        check(decision(true, true, true, false, true, false, true) == ProjectileOwnership.Decision.OWN, "Own shots untouched");
        check(decision(true, true, true, false, false, true, true) == ProjectileOwnership.Decision.ALLIED, "Allies untouched even when friendly fire enabled");
        check(decision(true, true, true, false, false, false, false) == ProjectileOwnership.Decision.PVP_DISABLED, "PvP policy enforced");
        check(decision(false, true, true, false, false, false, true) == ProjectileOwnership.Decision.UNSUPPORTED
                && decision(true, false, true, false, false, false, true) == ProjectileOwnership.Decision.INACTIVE, "Unsupported/embedded rejected");
    }
    private static ProjectileOwnership.Decision decision(boolean supported, boolean active, boolean validOwner, boolean ownerless,
                                                           boolean self, boolean allied, boolean pvp) {
        return ProjectileOwnership.classify(supported, active, validOwner, ownerless, self, allied, pvp);
    }
    private static void drag() {
        var settings = ProjectileBalanceSettings.defaults(); var control = settings.control();
        check(near(ProjectileControlMath.factor(control.outerRadius(), control), 1), "No outer-edge impulse");
        check(near(ProjectileControlMath.factor(control.innerRadius(), control), control.minimumSpeedFactor()), "Inner minimum factor");
        double previous = 1;
        for (double distance = control.outerRadius(); distance >= 0; distance -= 0.025) {
            double factor = ProjectileControlMath.factor(distance, control);
            check(Double.isFinite(factor) && factor <= previous + 0.000001 && factor >= control.minimumSpeedFactor(), "Smooth bounded proximity response"); previous = factor;
        }
        Vec3 initial = new Vec3(1, -0.2, 3);
        Vec3 slowed = ProjectileControlMath.velocity(initial, 1, 0.25, settings.maximumSpeed());
        for (int tick = 0; tick < 10000; tick++) slowed = ProjectileControlMath.velocity(slowed, 0.25, 0.25, settings.maximumSpeed());
        check(near(slowed.length(), initial.length() * 0.25), "Ten thousand samples do not compound slowdown");
        check(near(slowed.normalize().dot(initial.normalize()), 1), "Direction preserved");
        Vec3 overlap = ProjectileControlMath.velocity(slowed, 0.25, Math.min(0.25, 0.5), settings.maximumSpeed());
        check(near(overlap.length(), slowed.length()), "Strongest overlap does not multiply fields");
        Vec3 exit = ProjectileControlMath.velocity(overlap, 0.25, 1, settings.maximumSpeed());
        check(near(exit.length(), initial.length()), "Exit or invalid field owner undoes only the field response");
        Vec3 physicsInput = new Vec3(0.5, 0.1, 0.25);
        Vec3 nativeOutput = physicsInput.scale(0.99).add(0, -0.05, 0);
        Vec3 physics = ProjectileControlMath.afterPhysics(physicsInput, nativeOutput, 0.99, 0.25, settings.maximumSpeed());
        Vec3 expected = physicsInput.scale(Math.pow(0.99, 0.25)).add(0, -0.05 * 0.25 * 0.25, 0);
        check(physics.distanceTo(expected) < 0.000001,
                "Local time scales inertia fractionally and gravity quadratically to preserve flight over distance");
        Vec3 viscousFall = new Vec3(0.375, 0, 0);
        for (int tick = 0; tick < 20; tick++)
            viscousFall = ProjectileControlMath.afterPhysics(viscousFall,
                    viscousFall.scale(0.99).add(0, -0.05, 0), 0.99, 0.125, settings.maximumSpeed());
        check(viscousFall.x > 0 && viscousFall.y < 0 && viscousFall.y > -0.2,
                "Twenty field ticks retain nonzero horizontal and downward motion while slowing accumulated gravity");
        check(ProjectileControlMath.redirectSpeed(new Vec3(0.1, -0.05, 0), 0.125, 3, 1.25, 16) == 3.75,
                "Melee return restores full nominal launch speed and adds the configured impulse");
        check(ProjectileTargeting.aimDot(new Vec3(0, 1.5, 0), new Vec3(0, 0, 1), new Vec3(2, 1.5, 2))
                >= Math.cos(Math.toRadians(control.theftAimConeDegrees())) - 0.000001,
                "Enemy two blocks lateral to a projectile struck two blocks ahead fits aim assistance");
        Vec3 returned = new Vec3(0, 0, 3.75), desired = new Vec3(2, 0.4, 2);
        Vec3 steered = ProjectileTargeting.turn(returned, desired, control.theftTurnDegreesPerTick());
        check(near(steered.length(), returned.length())
                        && steered.normalize().dot(desired.normalize()) > returned.normalize().dot(desired.normalize()),
                "Theft homing turns toward an assisted target without losing the melee relaunch speed");
        check(ProjectileControlMath.velocity(new Vec3(Double.NaN, 0, 0), 1, 0.25, 16).equals(Vec3.ZERO), "Nonfinite rejected");
        check(ProjectileControlMath.velocity(Vec3.ZERO, 1, 0.25, 16).equals(Vec3.ZERO), "Zero vector remains finite");
        check(ProjectileControlMath.velocity(new Vec3(100, 0, 0), 0.25, 1, 16).length() <= 16, "Exit bounded by launch maximum");
        var persisted = new ProjectileControlState(); persisted.dragFactor = 0.25;
        var loaded = ProjectileControlState.load(persisted.save());
        check(loaded != null && loaded.dragFactor == 0.25
                && near(ProjectileControlMath.velocity(slowed, loaded.dragFactor, 1, 16).length(), initial.length()), "Reload without leases safely releases saved slowdown");
        // Reproduce a level skeleton shot across the full radial pocket, including its extra real time near the player.
        Vec3 position = new Vec3(12, 1.5, 0), velocity = new Vec3(-1.6, 0.12, 0);
        double previousFactor = 1;
        int reachableTicks = 0;
        for (int tick = 0; tick < 100 && position.x > 0.5; tick++) {
            double factor = (float) ProjectileControlMath.factor(position.distanceTo(new Vec3(0, 0.9, 0)), control);
            velocity = ProjectileControlMath.velocity(velocity, previousFactor, factor, 16);
            previousFactor = factor;
            if (position.x <= 3) {
                check(position.y > 1.0, "Slowed skeleton arrow stays above leggings throughout the reachable flight");
                reachableTicks++;
            }
            position = position.add(velocity);
            velocity = ProjectileControlMath.afterPhysics(velocity, velocity.scale(0.99F).add(0, -0.05, 0), 0.99F, factor, 16);
        }
        check(position.x <= 0.5 && position.y > 1.0 && reachableTicks >= 12,
                "Radial pocket gives at least 0.6 seconds of reachable flight without dropping the shot to the floor");
        // Eight times the wall time should trace nearly the same ballistic arc at an eighth of the speed.
        Vec3 normal = Vec3.ZERO, slow = Vec3.ZERO, normalV = new Vec3(1.6, 0.12, 0), slowV = normalV.scale(0.125);
        for (int tick = 0; tick < 6; tick++) { normal = normal.add(normalV); normalV = normalV.scale(0.99F).add(0, -0.05, 0); }
        for (int tick = 0; tick < 48; tick++) {
            slow = slow.add(slowV);
            slowV = ProjectileControlMath.afterPhysics(slowV, slowV.scale(0.99F).add(0, -0.05, 0), 0.99F, 0.125, 16);
        }
        check(normal.distanceTo(slow) < 0.17 && slowV.x > 0 && slowV.y < 0,
                "Eightfold slow motion preserves the ballistic arc and keeps moving downward");
    }
    private static void swings() {
        var settings = ProjectileBalanceSettings.defaults(); var c = settings.control();
        Vec3 eye = new Vec3(0, 1.5, 0), look = new Vec3(0, 0, 1);
        var fan = ProjectileControlMath.fan(look, c.swingRange(), c.swingRadius(), c.swingHalfAngleDegrees(), settings.maximumImpacts());
        check(fan.size() <= settings.maximumImpacts() + 1, "Weapon fan CPU bounded");
        var minimumFan = ProjectileControlMath.fan(look, c.swingRange(), c.swingRadius(), c.swingHalfAngleDegrees(), 1);
        check(minimumFan.size() == 1 && ProjectileControlMath.intersects(box(0, 1.5, 2), eye, look, minimumFan, c.swingRadius()),
                "Minimum processing budget still accepts the central ordinary weapon hit");
        check(ProjectileControlMath.intersects(box(0, 1.5, 2), eye, look, fan, c.swingRadius()), "Front hit");
        check(ProjectileControlMath.intersects(box(1, 1.5, 2), eye, look, fan, c.swingRadius()), "Front-side arc hit");
        check(!ProjectileControlMath.intersects(box(0, 1.5, -0.1), eye, look, fan, c.swingRadius()), "Hilt inflation never hits behind");
        check(!ProjectileControlMath.intersects(box(3, 1.5, 0.2), eye, look, fan, c.swingRadius()), "Outside arc miss");
        check(!ProjectileControlMath.intersects(box(0, 1.5, 4), eye, look, fan, c.swingRadius()), "Range-edge miss");
        check(!ProjectileControlMath.intersects(box(0, 3, 2), eye, look, fan, c.swingRadius()), "Above weapon miss");
        check(!ProjectileControlMath.intersects(box(0, 1.5, 2), eye, look, java.util.List.of(look.scale(1)), c.swingRadius()), "Terrain-clipped fan cannot reach behind wall");
        var ledger = new ProjectileSwingLedger();
        check(!ledger.process(10, 0.5, c.readinessThreshold()) && ledger.process(10, 1, c.readinessThreshold()), "Readiness required before accepting swing");
        check(!ledger.process(10, 1, c.readinessThreshold()), "One processing pass per tick");
        ledger.attackAnimation(11);
        check(ledger.process(11, 1, c.readinessThreshold()) && !ledger.animate(12), "Attack+later animation deduplicated");
        ledger.used(20);
        check(!ledger.animate(20) && !ledger.animate(22) && ledger.animate(23), "Right-click animations suppressed for bounded matching window");
        ledger.attackAnimation(30);
        check(ledger.animate(33), "Stale expected animation cannot swallow later ordinary swing");
        check(!ledger.process(35, Double.NaN, 0), "Invalid readiness rejected");
        var blockClick = new ProjectileSwingLedger();
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var start = net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK;
        check(blockClick.startBlockAttack(40, start, false, () -> {
            attempts.incrementAndGet();
            return blockClick.process(40, 1, c.readinessThreshold())
                    && ProjectileControlMath.intersects(box(0, 1.3, 2), eye, look, fan, c.swingRadius());
        }), "First left click against a block behind an arrow intercepts before mining starts");
        blockClick.used(40); // Native block-action RETURN hook still runs after canceling block destruction.
        check(!blockClick.animate(40) && !blockClick.animate(41) && attempts.get() == 1,
                "Block-click attack consumes its following animation exactly once");
        for (var action : net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.values()) {
            if (action == start) continue;
            check(!blockClick.startBlockAttack(50, action, false, () -> { throw new AssertionError("Not a fresh attack"); }),
                    "Non-start block/use action never enters interception: " + action);
        }
        check(!blockClick.startBlockAttack(50, start, true, () -> { throw new AssertionError("Held mining"); }),
                "Continued mining cannot repeatedly bat projectiles");
        check(!blockClick.startBlockAttack(50, start, false, () -> false), "A missed interception leaves native block breaking available");
        check(ProjectileInterceptionService.destination(true, true) == ProjectileInterceptionService.Destination.AIMED
                && ProjectileInterceptionService.destination(false, true) == ProjectileInterceptionService.Destination.SOURCE
                && ProjectileInterceptionService.destination(false, false) == ProjectileInterceptionService.Destination.DESTROY, "Aim priority, source fallback, safe no-target fallback");
    }
    private static void transfer() {
        var control = new ProjectileControlState();
        check(control.redirect(SHOOTER, DEFENDER, 1) && control.remainingRedirects == 0, "Exactly one redirect");
        check(!control.redirect(DEFENDER, SHOOTER, 1) && !control.redirect(DEFENDER, new UUID(3, 3), 1), "No sender loop or third-player ping-pong");
        var roundTrip = ProjectileControlState.load(control.save());
        check(roundTrip != null && roundTrip.owners.equals(Set.of(SHOOTER, DEFENDER))
                && !roundTrip.redirect(DEFENDER, new UUID(3, 3), 1), "Restart preserves exhausted theft budget and owner history");
        var bad = control.save(); bad.putDouble("DragFactor", Double.NaN);
        check(ProjectileControlState.load(bad) == null, "Corrupt control persistence rejected");
        bad = control.save(); bad.putInt("Redirects", 2);
        check(ProjectileControlState.load(bad) == null, "Corrupt enlarged redirect budget rejected");
        bad = control.save(); bad.putInt("Redirects", 1);
        check(ProjectileControlState.load(bad) == null, "A spent owner history cannot reopen a transfer on reload");
        for (var source : new ProjectileSource[] {ProjectileSource.RANGED_PHYSICAL, ProjectileSource.ASCENDANCE_MAGIC}) {
            for (var path : ProjectilePath.values()) {
                var original = new ProjectileState(source, path, SHOOTER, LIFE, ResourceLocation.parse("minecraft:overworld"),
                        120, ProjectileBalanceSettings.defaults(), 2);
                original.remainingRange = 8; original.remainingTicks = 9; original.damageMultiplier = 0.64;
                original.target = new UUID(8, 8); original.visit(new UUID(8, 9));
                var redirected = original.transferTo(DEFENDER, new UUID(9, 9), 18);
                check(redirected != null && redirected.managed() && redirected.redirected && redirected.path == ProjectilePath.NONE
                        && redirected.payload == null && redirected.target == null && redirected.ricochets == 0 && redirected.skillPenetrations == 0
                        && redirected.chargeDischarged, source + "/" + path + " transfer clears every offensive selection/discharge");
                check(redirected != null && redirected.source == source
                        && redirected.profile.range() == original.profile.range() && redirected.profile.speed() == original.profile.speed()
                        && redirected.profile.lifetimeTicks() == original.profile.lifetimeTicks()
                        && redirected.profile.acquisitionRange() == original.profile.acquisitionRange()
                        && redirected.profile.acquisitionConeDegrees() == original.profile.acquisitionConeDegrees()
                        && redirected.profile.turnDegreesPerTick() == Math.max(original.profile.turnDegreesPerTick(), 18)
                        && redirected.launchedAt == original.launchedAt && redirected.remainingRange == 8 && redirected.remainingTicks == 9
                        && redirected.damageMultiplier == 0.64 && redirected.nativePenetrations == 2 && redirected.visited.equals(original.visited)
                        && redirected.owner.equals(DEFENDER), source + "/" + path + " ownership transfer retains native physics/range/life/damage/visited ledger");
                redirected.target = new UUID(7, 7);
                var redirectedReloaded = ProjectileState.load(redirected.save());
                check(redirectedReloaded != null && redirectedReloaded.target.equals(new UUID(7, 7))
                                && redirectedReloaded.profile.turnDegreesPerTick() >= 18,
                        source + "/" + path + " redirected homing target and turn rate survive reload");
            }
        }
    }
    private static void nativeOwnership() {
        // Constructor-free allocation is exclusively a no-world fixture for vanilla's field-only methods.
        // This executes the real mapped override rather than simulating setOwner or constructing a game world.
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var field = unsafeClass.getDeclaredField("theUnsafe"); field.setAccessible(true);
            Object allocator = field.get(null);
            var allocate = unsafeClass.getMethod("allocateInstance", Class.class);
            Arrow arrow = (Arrow) allocate.invoke(allocator, Arrow.class);
            var typeField = net.minecraft.world.entity.Entity.class.getDeclaredField("type"); typeField.setAccessible(true);
            typeField.set(arrow, EntityType.ARROW);
            check(!arrow.isPickable(), "Real vanilla arrows are absent from entity crosshair picking, exposing the block-click route");
            ServerPlayer source = (ServerPlayer) allocate.invoke(allocator, ServerPlayer.class);
            ServerPlayer defender = (ServerPlayer) allocate.invoke(allocator, ServerPlayer.class);
            var attributes = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attributes"); attributes.setAccessible(true);
            attributes.set(defender, new net.minecraft.world.entity.ai.attributes.AttributeMap(
                    net.minecraft.world.entity.player.Player.createAttributes().build()));
            var effects = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("activeEffects"); effects.setAccessible(true);
            effects.set(defender, new java.util.HashMap<>());
            var ticker = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker"); ticker.setAccessible(true);
            ticker.setInt(defender, 20);
            // Keep the inherited animation active so this field-only fixture needs no world broadcast.
            defender.swinging = true; defender.swingTime = 0;
            check(defender.getAttackStrengthScale(0) == 1, "Native server player begins with fully ready attack");
            defender.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            check(defender.getAttackStrengthScale(0) == 0,
                    "ServerPlayer's actual swing override resets readiness; interception must precede it");
            source.setUUID(SHOOTER); defender.setUUID(DEFENDER);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setOwner(source);
            check(arrow.pickup == AbstractArrow.Pickup.ALLOWED, "Real vanilla setOwner reproduces mob-ammunition pickup conversion");
            for (var pickup : AbstractArrow.Pickup.values()) {
                arrow.pickup = pickup; arrow.setBaseDamage(3.75);
                Vec3 velocity = new Vec3(0.3, -0.2, 2.5); arrow.setDeltaMovement(velocity);
                ProjectileOwnership.transferNative(arrow, defender);
                check(arrow.pickup == pickup && arrow.getOwner() == defender && arrow.getOwner().getUUID().equals(DEFENDER),
                        "Native ownership transfer preserves " + pickup + " while updating responsible owner");
                check(arrow.getBaseDamage() == 3.75 && arrow.getDeltaMovement().equals(velocity),
                        "Native ownership transfer retains intrinsic damage and velocity for " + pickup);
            }
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot execute mapped native ownership regression", failure); }
    }
    private static AABB box(double x, double y, double z) { return new AABB(x - 0.05, y - 0.05, z - 0.05, x + 0.05, y + 0.05, z + 0.05); }
    private static boolean near(double first, double second) { return Math.abs(first - second) < 0.000001; }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}

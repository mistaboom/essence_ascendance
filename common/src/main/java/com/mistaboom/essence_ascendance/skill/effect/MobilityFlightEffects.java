package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.mixin.MovementAbilityNativeAccess;
import com.mistaboom.essence_ascendance.movement.FlightAbilityRules;
import com.mistaboom.essence_ascendance.movement.FlightAbilityState;
import com.mistaboom.essence_ascendance.movement.MovementAbilityInput;
import com.mistaboom.essence_ascendance.movement.MovementAbilityRules;
import com.mistaboom.essence_ascendance.movement.MovementImpulseMath;
import com.mistaboom.essence_ascendance.network.ProgressionVisualFeedback;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudCards.compact;

/** Shared flight lifecycle: one ordinary-Jump input stream, normalized resources and native-relative movement. */
public final class MobilityFlightEffects {
    private MobilityFlightEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new Wings(), new FreeFlight(SkillIds.FATIGUE_FLIGHT),
                new VectorBoost(), new FreeFlight(SkillIds.UNTETHERED_FLIGHT));
    }

    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.mobility." + key, args);
    }

    private static int timeout(SkillEffectRuntime.Context context) {
        return context.settings().posture().movement().intentTimeoutTicks();
    }

    static SkillEffectHudEntry wingsCard(boolean owned, boolean gliding) {
        boolean active = owned && gliding;
        return SkillEffectHudEntry.skill(SkillIds.ESSENCE_WINGS, active, AscendancePalette.MOBILITY,
                text(active ? "wings_gliding" : "wings_ready"), List.of(), SkillEffectHudEntry.Meter.none());
    }

    static SkillEffectHudEntry flightCard(ResourceLocation id, boolean flying, boolean permission, double stamina) {
        if (id.equals(SkillIds.UNTETHERED_FLIGHT))
            return SkillEffectHudEntry.skill(id, flying, AscendancePalette.MOBILITY,
                    text("untethered_flying"), List.of(), SkillEffectHudEntry.Meter.none());
        return SkillEffectHudCards.progress(id, flying || permission || stamina < 1, AscendancePalette.MOBILITY,
                text(flying ? "flight_thrusting" : "flight_stamina", compact(stamina * 100)),
                flying ? List.of() : List.of(text("flight_ground_recharge")), stamina);
    }

    static SkillEffectHudEntry boostCard(boolean gliding, double charge) {
        Text badge = charge >= 1 ? text("vector_boost_ready") : text("vector_boost_recharge", compact(charge * 100));
        return SkillEffectHudCards.progress(SkillIds.VECTOR_BOOST, gliding || charge < 1, AscendancePalette.MOBILITY,
                badge, gliding && charge >= 1 ? List.of(text("vector_boost_hint")) : List.of(), charge);
    }

    private static void stopOwnedWings(SkillEffectRuntime.Context context, FlightAbilityState state) {
        if (state == null || !state.wingsActive()) return;
        if (context.player().isFallFlying()) context.player().stopFallFlying();
        state.stopWings();
    }

    /** One authoritative velocity write path for all skill-owned flight impulses. */
    private static boolean applyVelocity(ServerPlayer player, MovementImpulseMath.Velocity next) {
        Vec3 before = player.getDeltaMovement();
        if (!finite(before) || !Double.isFinite(next.x()) || !Double.isFinite(next.y()) || !Double.isFinite(next.z())) return false;
        if (Double.compare(before.x, next.x()) == 0 && Double.compare(before.y, next.y()) == 0
                && Double.compare(before.z, next.z()) == 0) return false;
        player.setDeltaMovement(new Vec3(next.x(), next.y(), next.z()));
        player.fallDistance = MovementImpulseMath.fallDistance(player.fallDistance, before.y, next.y());
        player.hasImpulse = true;
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        return true;
    }

    private static final class Wings implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.ESSENCE_WINGS; }

        @Override public void tick(SkillEffectRuntime.Context context) {
            ServerPlayer player = context.player();
            FlightAbilityState state = context.state(id(), FlightAbilityState::new);
            state.expireInput(context.now(), timeout(context));
            boolean supported = MovementAbilityRules.supported(player);
            if (state.wingsActive()) {
                if (!player.isFallFlying() || !FlightAbilityRules.wingsAllowed(player) || supported) {
                    stopOwnedWings(context, state);
                    return;
                }
                if (player.isFallFlying()) {
                    var tuning = context.settings().mobility().essenceWings();
                    applyVelocity(player, MovementImpulseMath.essenceWingsGlide(
                            velocity(player.getDeltaMovement()), tuning.horizontalDragCompensation()));
                }
                return;
            }
            if (player.isFallFlying() || supported || !FlightAbilityRules.wingsAllowed(player)) return;
            // Apex is a physical sign change, not a balance number. Holding Jump from takeoff is intentionally valid.
            if (state.jumpHeldFor(context.now(), timeout(context), MovementAbilityInput.HEARTBEAT_TICKS)
                    && player.getDeltaMovement().y <= 0) {
                player.startFallFlying();
                state.startWings(context.now());
            }
        }

        @Override public void deactivate(SkillEffectRuntime.Context context) {
            FlightAbilityState state = context.existingState(id());
            stopOwnedWings(context, state);
            context.discardState(id());
        }

        @Override public void movementDiscontinuity(SkillEffectRuntime.Context context) { deactivate(context); }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            FlightAbilityState state = context.existingState(id());
            return wingsCard(state != null && state.wingsActive() && FlightAbilityRules.wingsAllowed(context.player()),
                    context.player().isFallFlying());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            FlightAbilityState state = context.existingState(id());
            return List.of("Owned equipment-free glide=" + (state != null && state.wingsActive())
                            + "; fall-flying=" + context.player().isFallFlying(),
                    "Generated horizontal Elytra-drag compensation="
                            + context.settings().mobility().essenceWings().horizontalDragCompensation(),
                    "Hold ordinary Jump at/after the physical apex. Native pitch/lift remain intact; generated horizontal drag compensation makes Essence Wings carry momentum farther without inventing a second flight-speed stat.");
        }
    }

    private record FreeFlight(ResourceLocation id) implements SkillEffectHudHandler {
        @Override public void tick(SkillEffectRuntime.Context context) {
            ServerPlayer player = context.player();
            FlightAbilityState state = context.state(id, FlightAbilityState::new);
            int timeout = timeout(context);
            state.expireInput(context.now(), timeout);
            var base = context.settings().mobility().fatigueFlight();
            boolean untethered = id.equals(SkillIds.UNTETHERED_FLIGHT);
            boolean allowed = FlightAbilityRules.freeFlightAllowed(player);
            boolean supported = MovementAbilityRules.supported(player);

            if (!untethered) {
                tickJetpack(context, state, base, allowed, supported);
                return;
            }

            state.setThrusting(false);
            if (!allowed) {
                state.releaseFlight(player);
                state.updateStamina(context.now(), base.enduranceTicks(), base.groundRechargeTicks(), false, false);
                return;
            }

            if (supported) {
                state.releaseFlight(player);
            } else if (!state.flightPermission()
                    && state.jumpHeldFor(context.now(), timeout, MovementAbilityInput.HEARTBEAT_TICKS)) {
                state.activateFlight(player);
            } else if (state.flightPermission()) {
                state.maintainFlightPermission(player);
            }

            boolean draining = !supported && state.flightPermission() && player.getAbilities().flying;
            int rechargeTicks = supported ? base.groundRechargeTicks()
                    : context.settings().mobility().untetheredFlight().airRechargeTicks();
            state.updateStamina(context.now(), base.enduranceTicks(), rechargeTicks, draining, true);
        }

        private static void tickJetpack(SkillEffectRuntime.Context context, FlightAbilityState state,
                                        com.mistaboom.essence_ascendance.config.MobilityBalanceSettings.FatigueFlight tuning,
                                        boolean allowed, boolean supported) {
            ServerPlayer player = context.player();
            // Remove any skill-owned mayfly/flying lease left by an older runtime build. Fatigue now owns velocity only.
            if (state.flightPermission()) state.releaseFlight(player);
            boolean thrusting = allowed && !supported && state.stamina() > 0
                    && state.jumpHeldFreshFor(context.now(), MovementAbilityInput.HEARTBEAT_TICKS);
            state.setThrusting(thrusting);
            if (thrusting) {
                float nativeJump = ((MovementAbilityNativeAccess) player).essenceAscendance$jumpPower();
                double gravity = player.getAttributeValue(Attributes.GRAVITY);
                float flightSpeed = player.getAbilities().getFlyingSpeed();
                if (Float.isFinite(nativeJump) && nativeJump > 0 && Double.isFinite(gravity) && gravity >= 0
                        && Float.isFinite(flightSpeed) && flightSpeed >= 0) {
                    double yaw = Math.toRadians(player.getYRot());
                    double directionX = state.leftInput() * Math.cos(yaw) - state.forwardInput() * Math.sin(yaw);
                    double directionZ = state.forwardInput() * Math.cos(yaw) + state.leftInput() * Math.sin(yaw);
                    applyVelocity(player, MovementImpulseMath.jetpackThrust(velocity(player.getDeltaMovement()), gravity,
                            nativeJump, tuning.thrustGravityMultiplier(), directionX, directionZ, flightSpeed));
                } else state.setThrusting(false);
            }
            state.updateStamina(context.now(), tuning.enduranceTicks(), tuning.groundRechargeTicks(),
                    state.thrusting(), supported);
            if (state.stamina() <= 0) state.setThrusting(false);
        }

        @Override public void deactivate(SkillEffectRuntime.Context context) {
            FlightAbilityState state = context.existingState(id);
            if (state != null) state.releaseFlight(context.player());
            if (id.equals(SkillIds.FATIGUE_FLIGHT)) {
                if (state != null) state.suspendThrust();
            } else context.discardState(id);
        }

        @Override public void movementDiscontinuity(SkillEffectRuntime.Context context) { deactivate(context); }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            FlightAbilityState state = context.existingState(id);
            double stamina = state == null ? 1 : state.stamina();
            boolean untethered = id.equals(SkillIds.UNTETHERED_FLIGHT);
            boolean flying = untethered
                    ? state != null && state.flightPermission() && context.player().getAbilities().flying
                    : state != null && state.thrusting();
            return flightCard(id, flying, state != null && state.flightPermission(), stamina);
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            FlightAbilityState state = context.existingState(id);
            var fatigue = context.settings().mobility().fatigueFlight();
            boolean untethered = id.equals(SkillIds.UNTETHERED_FLIGHT);
            return List.of("Stamina=" + (state == null ? 1 : state.stamina()) + "; permission="
                            + (state != null && state.flightPermission()) + "; thrusting=" + (state != null && state.thrusting())
                            + "; native flying=" + context.player().getAbilities().flying,
                    "Endurance ticks=" + fatigue.enduranceTicks() + "; ground recharge ticks=" + fatigue.groundRechargeTicks()
                            + "; thrust/gravity=" + fatigue.thrustGravityMultiplier()
                            + "; resolved lateral flight speed=" + context.player().getAbilities().getFlyingSpeed()
                            + (untethered ? "; air recharge ticks=" + context.settings().mobility().untetheredFlight().airRechargeTicks() : ""),
                    untethered
                            ? "Ordinary Jump admits native creative-style flight; stamina recovers airborne while flight speed remains the equipment Flight Speed route. Zero stamina never revokes flight."
                            : "Hold ordinary Jump airborne for generated jetpack acceleration. Fresh heartbeats are required to continue thrust; release/stale input stops thrust. WASD adds yaw-relative horizontal acceleration using resolved Abilities#flyingSpeed, while vertical acceleration is added to existing descent instead of zeroing Y first.");
        }
    }

    private static final class VectorBoost implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.VECTOR_BOOST; }

        @Override public void tick(SkillEffectRuntime.Context context) {
            FlightAbilityState state = context.state(id(), FlightAbilityState::new);
            var tuning = context.settings().mobility().vectorBoost();
            state.updateBoost(context.now(), tuning.rechargeTicks());
            long pressTick = state.consumeJumpPressTick(context.now(), timeout(context));
            FlightAbilityState wings = context.existingState(SkillIds.ESSENCE_WINGS);
            if (pressTick == Long.MIN_VALUE || state.boostCharge() < 1 || wings == null || !wings.wingsActive()
                    || wings.wingsStartedTick() >= pressTick || !context.player().isFallFlying()
                    || !FlightAbilityRules.wingsAllowed(context.player())) return;
            if (boost(context, tuning.rocketSpeedBonus())) state.spendBoost();
        }

        private static boolean boost(SkillEffectRuntime.Context context, double rocketSpeedBonus) {
            ServerPlayer player = context.player();
            Vec3 before = player.getDeltaMovement();
            Vec3 look = player.getLookAngle();
            if (!finite(before) || !finite(look)) return false;
            MovementImpulseMath.Velocity resolved = MovementImpulseMath.vectorBoost(
                    velocity(before), velocity(look), rocketSpeedBonus);
            if (!applyVelocity(player, resolved)) return false;
            Vec3 after = new Vec3(resolved.x(), resolved.y(), resolved.z());
            ProgressionVisualFeedback.mobilityLaunch(player, player.position().add(0, 0.20, 0),
                    after.subtract(before), (float) Math.min(1.5, after.subtract(before).length()), 3);
            return true;
        }

        @Override public void deactivate(SkillEffectRuntime.Context context) {
            FlightAbilityState state = context.existingState(id());
            if (state != null) state.suspendBoost();
        }
        @Override public void movementDiscontinuity(SkillEffectRuntime.Context context) { deactivate(context); }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            FlightAbilityState state = context.existingState(id());
            double charge = state == null ? 1 : state.boostCharge();
            FlightAbilityState wings = context.existingState(SkillIds.ESSENCE_WINGS);
            boolean gliding = context.isEffective(SkillIds.ESSENCE_WINGS) && wings != null && wings.wingsActive()
                    && context.player().isFallFlying() && FlightAbilityRules.wingsAllowed(context.player());
            return boostCard(gliding, charge);
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            FlightAbilityState state = context.existingState(id());
            var tuning = context.settings().mobility().vectorBoost();
            return List.of("Boost charge=" + (state == null ? 1 : state.boostCharge()) + "; gliding=" + context.player().isFallFlying(),
                    "Recharge ticks=" + tuning.rechargeTicks() + "; native-firework cruise multiplier="
                            + Math.sqrt(1 + tuning.rocketSpeedBonus()),
                    "One fresh ordinary Jump press during effective Wings fall-flying fills the missing look-axis velocity to a generated native-firework-relative cruise target. Future ranks shorten recharge instead of making the burst harder to control.");
        }
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private static MovementImpulseMath.Velocity velocity(Vec3 value) {
        return new MovementImpulseMath.Velocity(value.x, value.y, value.z);
    }
}

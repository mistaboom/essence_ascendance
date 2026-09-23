package com.mistaboom.essence_ascendance.movement;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.mixin.MovementAbilityNativeAccess;
import com.mistaboom.essence_ascendance.network.ProgressionVisualFeedback;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Reusable authoritative ordinary-input launch path. No position, strength, charge or air-credit claims arrive from clients. */
public final class MovementAbilityService {
    private MovementAbilityService() { }
    public static void observe(SkillEffectRuntime.Context context, ResourceLocation id) {
        PlayerMotionTracker.track(context.player(), context.settings().posture().movement());
        var state = context.state(id, MovementAbilityState::new);
        state.observe(context.now(), MovementAbilityRules.allowed(context.player()),
                MovementAbilityRules.supported(context.player()), timeout(context));
    }
    public static void input(ServerPlayer player, MovementAbilityInput input) {
        if (!EssenceConfigManager.authoritativeReady()) return;
        ResourceLocation id = MovementAbilityRules.style(player);
        if (id == null) return;
        var context = SkillEffectRuntime.context(player);
        if (!context.isEffective(id)) return;
        var state = context.state(id, MovementAbilityState::new);
        var decision = state.input(context.now(), input, MovementAbilityRules.mode(id),
                MovementAbilityRules.allowed(player), MovementAbilityRules.supported(player),
                context.settings().mobility().chargedJump().chargeTicks(), timeout(context));
        // Native ground/unused-air presses are routed by the client, not launched again here.
        if (decision.action() != MovementAbilityState.Action.CHARGED_RELEASE
                && decision.action() != MovementAbilityState.Action.AIR_JUMP) return;
        launch(context, id, input, decision, state);
    }
    private static int timeout(SkillEffectRuntime.Context context) {
        return context.settings().posture().movement().intentTimeoutTicks();
    }
    private static void launch(SkillEffectRuntime.Context context, ResourceLocation id,
                               MovementAbilityInput input, MovementAbilityState.Decision decision,
                               MovementAbilityState state) {
        ServerPlayer player = context.player();
        Vec3 priorNative = player.getDeltaMovement();
        Vec3 before = PlayerMotionTracker.velocity(player);
        float nativePower = ((MovementAbilityNativeAccess) player).essenceAscendance$jumpPower();
        if (!finite(before) || !finite(priorNative) || !Float.isFinite(nativePower) || nativePower <= 0) return;
        float beforeFall = player.fallDistance;
        // Retain native jump callbacks, attribute/block/potion behavior, sprint impulse, statistics and exertion.
        player.jumpFromGround();
        Vec3 nativeMotion = player.getDeltaMovement();
        if (!finite(nativeMotion)) { player.setDeltaMovement(priorNative); return; }
        var tuning = context.settings().mobility();
        MovementImpulseMath.Velocity next;
        if (id.equals(SkillIds.VECTOR_JUMP)) {
            next = MovementImpulseMath.vectorJump(velocity(before), velocity(player.getLookAngle()),
                    nativePower, tuning.vectorJump().impulseBonus(), tuning.vectorJump().brakeFraction());
        } else {
            double yaw = Math.toRadians(player.getYRot());
            double dx = input.left() * Math.cos(yaw) - input.forward() * Math.sin(yaw);
            double dz = input.forward() * Math.cos(yaw) + input.left() * Math.sin(yaw);
            boolean charged = id.equals(SkillIds.CHARGED_JUMP);
            // Do not multiply existing upward momentum again when an air jump occurs during ascent.
            var baseline = new MovementImpulseMath.Velocity(before.x + nativeMotion.x - priorNative.x,
                    nativePower, before.z + nativeMotion.z - priorNative.z);
            next = MovementImpulseMath.directionalJump(baseline, dx, dz,
                    player.getAttributeValue(Attributes.MOVEMENT_SPEED),
                    charged ? tuning.chargedJump().heightBonus() : tuning.doubleJump().heightBonus(),
                    charged ? tuning.chargedJump().steeringBonus() : tuning.doubleJump().steeringBonus(), decision.charge());
        }
        player.setDeltaMovement(new Vec3(next.x(), next.y(), next.z()));
        player.fallDistance = MovementImpulseMath.fallDistance(beforeFall, before.y, next.y());
        player.setOnGround(false);
        player.hasImpulse = true;
        player.hurtMarked = true;
        state.launched(context.now(), decision.action() == MovementAbilityState.Action.CHARGED_RELEASE);
        Vec3 applied = new Vec3(next.x(), next.y(), next.z());
        int style = id.equals(SkillIds.CHARGED_JUMP) ? 0 : id.equals(SkillIds.VECTOR_JUMP) ? 2 : 1;
        ProgressionVisualFeedback.mobilityLaunch(player, player.position().add(0, 0.08, 0),
                applied.subtract(before), (float) Math.min(1.5, applied.subtract(before).length()), style);
        // The local player is not physically predicted twice; only this accepted native motion is applied.
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private static MovementImpulseMath.Velocity velocity(Vec3 value) {
        return new MovementImpulseMath.Velocity(value.x, value.y, value.z);
    }
}

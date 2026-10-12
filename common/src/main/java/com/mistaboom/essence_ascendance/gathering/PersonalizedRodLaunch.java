package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.config.FishingRodBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService;
import com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Rod-specific interpretation of an item-independent gift perk. Vanilla fishing and grappling do not enter it. */
public final class PersonalizedRodLaunch {
    private PersonalizedRodLaunch() { }
    public static double multiplier(ServerPlayer owner, ItemStack stack, Entity target) {
        if (!(target instanceof Mob)) return 1;
        return PersonalizedEquipmentService.active(owner, stack)
                .map(profile -> Math.clamp(profile.perk(PersonalizedEquipmentService.MOB_OPENING_LAUNCH, 1), 1, 100)).orElse(1.0);
    }

    public static Vec3 force(ServerPlayer owner, ItemStack stack, Entity target, Vec3 toward, Vec3 velocity,
            EquipmentFishingRodService.Context context, int poweredTicks, FishingRodBalanceSettings policy, double resistance, double entityViewScale) {
        return force(owner, stack, target, toward, velocity, context, poweredTicks, policy, resistance, entityViewScale,
                new RodPullMath.OpeningFrame(toward, velocity));
    }
    public static Vec3 force(ServerPlayer owner, ItemStack stack, Entity target, Vec3 toward, Vec3 velocity,
            EquipmentFishingRodService.Context context, int poweredTicks, FishingRodBalanceSettings policy, double resistance,
            double entityViewScale, RodPullMath.OpeningFrame frame) {
        double multiplier = multiplier(owner, stack, target);
        double steering = multiplier > 1 ? PersonalizedEquipmentService.active(owner, stack)
                .map(profile -> Math.clamp(profile.perk(PersonalizedEquipmentService.MOB_LAUNCH_STEERING, 1), 1, 100)).orElse(1.0) : 1;
        Vec3 ordinary = RodPullMath.entityForce(toward, velocity, owner.getLookAngle(), context.baseline(), poweredTicks,
                policy, context.targetForceMultiplier(), resistance, 1, 1, frame);
        if (multiplier <= 1 || poweredTicks >= policy.forceRampTicks()) return ordinary;
        var mob = (Mob) target;
        double nativeDistance = Math.min(mob.getType().getCategory().getDespawnDistance(), mob.getType().clientTrackingRange() * 16.0);
        // Keep one chunk of room at a simulation/view boundary; small-distance servers get a smaller launch.
        var players = owner.server.getPlayerList();
        int viewDistance = Math.min(players.getViewDistance(), owner.requestedViewDistance());
        nativeDistance = Math.min(nativeDistance, Math.max(0, Math.min(viewDistance, players.getSimulationDistance()) - 1) * 16.0);
        // Native entity rendering also has a size-scaled cutoff. The client can request a
        // smaller visual budget; it cannot raise the server's despawn/tracking/simulation limit.
        nativeDistance = Math.min(nativeDistance, mob.getBoundingBox().getSize() * 64 * entityViewScale);
        Vec3 boosted = RodPullMath.entityLaunchForce(toward, velocity, owner.getLookAngle(), context.baseline(), poweredTicks,
                policy, context.targetForceMultiplier(), resistance, multiplier, steering, frame);
        Vec3 offset = mob.position().subtract(owner.position());
        double radius = nativeDistance * MobLaunchEnvelope.DISTANCE_FRACTION;
        double gravity = mob.getAttributeValue(Attributes.GRAVITY);
        // Reserve settling room, apply close capture after launch limiting, then validate the
        // final combined motion. Scaling the complete force first would also erase its brake.
        Vec3 limited = MobLaunchEnvelope.limit(offset, velocity, boosted, gravity, mob.isNoGravity(), radius - policy.settlingDistance());
        Vec3 captured = RodPullMath.captureEntityForce(toward, velocity, owner.getLookAngle(), context.baseline(), poweredTicks,
                policy, context.targetForceMultiplier(), resistance, multiplier, frame, limited);
        return MobLaunchEnvelope.limit(offset, velocity, captured, gravity, mob.isNoGravity(), radius);
    }
}

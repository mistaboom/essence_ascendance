package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.equipment.personalized.*;
import com.mistaboom.essence_ascendance.gathering.*;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.network.PreciseMobMotionPayload;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import java.io.StringReader;
import java.util.*;
import io.netty.buffer.Unpooled;

public final class PersonalizedEquipmentTest {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        com.mistaboom.essence_ascendance.essence.EssenceTypes.init();
        com.mistaboom.essence_ascendance.tier.AscendanceTiers.init();
        com.mistaboom.essence_ascendance.stat.EssenceStats.init();
        com.mistaboom.essence_ascendance.progression.MilestoneProviders.init();
        com.mistaboom.essence_ascendance.progression.Milestones.init();
        com.mistaboom.essence_ascendance.skill.Skills.init();
        EquipmentProfiles.init();
        var gift = PersonalizedEquipmentService.definitions().getFirst();
        check(gift.playerId().equals(UUID.fromString("422890f3-c52e-4cdd-ad9c-a18c17b469fa"))
                && gift.name().equals("Vance's Yeet Stick") && gift.tier().equals("transcendent"), "Exact gift assignment");
        check(gift.baseRgb() == 0xFF1493 && gift.accentRgb() == 0x7FFFD4 && gift.perk("mob_opening_launch", 1) == 10,
                "Gift appearance and behavior share a definition");
        check(gift.perk("mob_launch_steering", 1) == 4, "Vance has a separate, owner-gated steering perk");
        var glaive = PersonalizedEquipmentDefinition.read(new StringReader("""
                {"schema_version":1,"profiles":[{"id":"future_glaive","player_uuid":"00000000-0000-0000-0000-000000000001",
                "item":"essence_ascendance:ascendance_glaive","tier":"transcendent","name":"Future Glaive",
                "base_color":"#123456","accent_color":"#ABCDEF","perks":{"future_glaive_perk":2}}]}
                """));
        check(glaive.getFirst().itemId().endsWith("ascendance_glaive") && glaive.getFirst().perk("mob_opening_launch", 1) == 1,
                "Definitions support other equipment types and independent perks");
        try { PersonalizedEquipmentDefinition.read(new StringReader("{\"schema_version\":2,\"profiles\":[]}")); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }
        var policy = RuntimeBalanceDefinition.bootstrap().config().fishingRodBalance();
        for (var tier : EquipmentTier.values()) {
            var baseline = policy.tier(tier);
            Vec3 toward = new Vec3(0, 0, -8), aim = new Vec3(0, 1, 0);
            var ordinary = RodPullMath.force(toward, Vec3.ZERO, aim, baseline, 0, policy, 1, 0);
            var boosted = RodPullMath.force(toward, Vec3.ZERO, aim, baseline, 0, policy, 1, 0, 10);
            check(boosted.distanceTo(ordinary.scale(10)) < 1e-8, "Unconstrained opening kick is tenfold");
            var steady = RodPullMath.force(toward, Vec3.ZERO, aim, baseline, policy.forceRampTicks(), policy, 1, 0);
            check(steady.equals(RodPullMath.force(toward, Vec3.ZERO, aim, baseline, policy.forceRampTicks(), policy, 1, 0, 10)),
                    "Gift hands off to exactly ordinary steady reeling");
            check(RodPullMath.force(toward, Vec3.ZERO, aim, baseline, 0, policy, 1, 1, 10).lengthSqr() == 0,
                    "Gift does not bypass knockback resistance");
        }
        var baseline = policy.tier(EquipmentTier.TRANSCENDENT);
        entityControl(policy);
        for (double radius : new double[]{57.6, 72, 100.8, 115.2}) for (double gravity : new double[]{0, .04, .08, .16})
            for (Vec3 direction : List.of(new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(1, 1, 0).normalize())) {
                Vec3 offset = new Vec3(0, 0, 8), force = direction.scale(15);
                var limited = MobLaunchEnvelope.limit(offset, Vec3.ZERO, force, gravity, gravity == 0, radius);
                double distance = MobLaunchEnvelope.maximumDistance(offset, limited, gravity, gravity == 0 ? .98 : .91);
                check(distance <= radius + 1e-7 && distance > radius - .02, "Flight budget approaches its limit without crossing it");
                check(limited.length() <= force.length() && limited.dot(force) > 0, "Envelope scales the kick without changing aim");
            }
        check(MobLaunchEnvelope.limit(Vec3.ZERO, new Vec3(30, 0, 0), new Vec3(10, 0, 0), .08, false, 115.2).equals(Vec3.ZERO),
                "Existing excessive momentum is retained without added outward boost");
        for (Vec3 aim : List.of(new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(1, 1, 0).normalize())) {
            Vec3 position = new Vec3(0, 0, 8), velocity = Vec3.ZERO;
            var frame = new RodPullMath.OpeningFrame(position.scale(-1), velocity);
            double maximum = 0;
            for (int tick = 0; tick < 330; tick++) {
                if (tick < policy.forceRampTicks()) {
                    var requested = RodPullMath.entityLaunchForce(position.scale(-1), velocity, aim, baseline, tick, policy, 1, 0, 10, 4, frame);
                    var limited = MobLaunchEnvelope.limit(position, velocity, requested, .08, false, 115.2 - policy.settlingDistance());
                    var captured = RodPullMath.captureEntityForce(position.scale(-1), velocity, aim, baseline, tick, policy, 1, 0, 10, frame, limited);
                    velocity = velocity.add(MobLaunchEnvelope.limit(position, velocity, captured, .08, false, 115.2));
                }
                position = position.add(velocity);
                if (position.y < 0) { position = new Vec3(position.x, 0, position.z); velocity = new Vec3(velocity.x, 0, velocity.z); }
                velocity = new Vec3(velocity.x * .91, (velocity.y - .08) * .98, velocity.z * .91);
                maximum = Math.max(maximum, position.length());
            }
            check(maximum <= 115.2001, "The entire held opening burst plus released flight respects the distance budget");
        }
        var packet = new PreciseMobMotionPayload(ResourceLocation.parse("minecraft:overworld"), 3, UUID.randomUUID(), new Vec3(10, 6, -8));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PreciseMobMotionPayload.CODEC.encode(buffer, packet);
            check(packet.equals(PreciseMobMotionPayload.CODEC.decode(buffer)), "Network codec retains full launch velocity and entity identity");
            buffer.clear();
            var input = new com.mistaboom.essence_ascendance.network.RodHeldInputPayload(3, true, 2.5);
            com.mistaboom.essence_ascendance.network.RodHeldInputPayload.CODEC.encode(buffer, input);
            check(input.equals(com.mistaboom.essence_ascendance.network.RodHeldInputPayload.CODEC.decode(buffer)),
                    "Client entity visibility reaches the server without losing its actual scale");
        } finally { buffer.release(); }
        System.out.println("PersonalizedEquipmentTest: " + checks + " profile, opening-only physics, flight envelope and network checks PASS");
    }
    private static void entityControl(com.mistaboom.essence_ascendance.config.FishingRodBalanceSettings policy) {
        for (var tier : EquipmentTier.values()) for (double multiplier : new double[]{1, 10})
            for (double distance : new double[]{1, 2, 6}) for (Vec3 aim : List.of(new Vec3(0, 1, 0), new Vec3(0, 0, 1))) {
                var baseline = policy.tier(tier);
                Vec3 position = new Vec3(0, .075, distance), velocity = Vec3.ZERO;
                var frame = new RodPullMath.OpeningFrame(position.scale(-1), velocity);
                double minimum = distance;
                for (int tick = 0; tick < 240; tick++) {
                    Vec3 force = RodPullMath.entityForce(position.scale(-1), velocity, aim, baseline, tick, policy,
                            1, 0, multiplier, multiplier > 1 ? 4 : 1, frame);
                    if (multiplier > 1 && tick < policy.forceRampTicks()) {
                        var requested = RodPullMath.entityLaunchForce(position.scale(-1), velocity, aim, baseline, tick, policy, 1, 0, multiplier, 4, frame);
                        var limited = MobLaunchEnvelope.limit(position, velocity, requested, .08, false, 115.2 - policy.settlingDistance());
                        force = RodPullMath.captureEntityForce(position.scale(-1), velocity, aim, baseline, tick, policy, 1, 0, multiplier, frame, limited);
                        force = MobLaunchEnvelope.limit(position, velocity, force, .08, false, 115.2);
                    }
                    if (aim.y > 0 && tick < policy.forceRampTicks()) check(force.y >= -1e-7,
                            "An upward opening cannot flip downward when the target moves above the holder");
                    velocity = velocity.add(force); position = position.add(velocity);
                    if (position.y < .075) { position = new Vec3(position.x, .075, position.z); velocity = new Vec3(velocity.x, 0, velocity.z); }
                    velocity = new Vec3(velocity.x * .91, (velocity.y - .08) * .98, velocity.z * .91);
                    minimum = Math.min(minimum, position.z);
                }
                check(minimum >= policy.settlingDistance() - .01, "Cold target approaches the caster without crossing and rebounding behind them: "
                        + tier + ", multiplier=" + multiplier + ", distance=" + distance + ", aim=" + aim + ", minimum=" + minimum);
                check(position.z < policy.settlingDistance() + .1, "Holding still reels the target into a stable close capture");
            }
        var row = policy.tier(EquipmentTier.TRANSCENDENT);
        Vec3 toward = new Vec3(0, 0, -100), look = new Vec3(0, .5, Math.sqrt(.75));
        var frame = new RodPullMath.OpeningFrame(toward, Vec3.ZERO);
        var weak = RodPullMath.entityForce(toward, Vec3.ZERO, look, row, 0, policy, 1, 0, 10, 1, frame);
        var strong = RodPullMath.entityForce(toward, Vec3.ZERO, look, row, 0, policy, 1, 0, 10, 4, frame);
        check(strong.y / -strong.z > weak.y / -weak.z * 3.99, "Vance's steering gives fourfold weight to angled look input");
        for (Vec3 side : List.of(new Vec3(-1, 0, 0), new Vec3(1, 0, 0), new Vec3(0, -1, 0), new Vec3(0, 1, 0))) {
            var force = RodPullMath.entityForce(toward, Vec3.ZERO, side, row, 0, policy, 1, 0, 10, 4, frame);
            check(force.dot(side) > -force.z * 10, "Enhanced steering covers left, right, up and down");
        }
        Vec3 initial = new Vec3(0, 0, -5), close = new Vec3(0, 0, -1);
        var protectedFrame = new RodPullMath.OpeningFrame(close, initial);
        check(RodPullMath.entityForce(close, initial, new Vec3(0, 1, 0), row, 0, policy, 1, 0, 10, 4, protectedFrame).z <= 1e-8,
                "Close capture does not erase inward momentum present before the pull");
        check(RodPullMath.entityForce(close, initial, new Vec3(0, 1, 0), row, 0, policy, 1, 1, 10, 4, protectedFrame).lengthSqr() == 0,
                "Resistance also blocks capture braking");
        check(RodPullMath.entityForce(toward, Vec3.ZERO, look, row, 30, policy, 1, 0, 10, 4, frame)
                .equals(RodPullMath.force(toward, Vec3.ZERO, null, row, 30, policy, 1, 0)),
                "Outside close capture, sustained reeling keeps the ordinary generated force");
    }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); checks++; }
}

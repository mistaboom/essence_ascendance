package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.mistaboom.essence_ascendance.equipment.WeaponActionCollision.*;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;

/** Bounded long-range probes. The visible pose still extends to the full accepted range. */
public final class WeaponActionFastCollision {
    private static final double PROBE_MOTION = .4;
    private static final int MAX_PROBES_PER_SEGMENT = 48;
    private static final double BLADE_RADIUS = BLADE_WIDTH / 2 + THICKNESS;

    private WeaponActionFastCollision() { }

    public static Trace stab(Pose from, Pose to, ServerPlayer player) {
        int probes = probes(from, to);
        var context = CollisionContext.of(player);
        for (int i = 0; i <= probes; i++) {
            double fraction = (double) i / probes;
            Pose pose = interpolate(from, to, fraction);
            Hit hit = nearest(player, context, pose, false);
            if (hit == null) continue;
            Pose shortened = pose.withDistance(hit.distance);
            return new Trace(List.of(new Knot(0, from), new Knot(fraction, pose),
                    new Knot(fraction, shortened)), new Contact(fraction, shortened, hit.candidate));
        }
        return new Trace(List.of(new Knot(0, from), new Knot(1, to)), null);
    }

    public static Sweep swipe(Pose from, Pose to, ServerPlayer player, Predicate<Contact> visitor) {
        int probes = probes(from, to);
        var context = CollisionContext.of(player);
        var knots = new ArrayList<Knot>(probes + 2);
        knots.add(new Knot(0, from));
        for (int i = 1; i <= probes; i++) {
            double fraction = (double) i / probes;
            Pose pose = interpolate(from, to, fraction);
            Hit hit = nearest(player, context, pose, true);
            Pose solved = hit == null ? pose : pose.withDistance(hit.distance);
            knots.add(new Knot(fraction, solved));
            if (hit != null && !visitor.test(new Contact(fraction, solved, hit.candidate)))
                return new Sweep(List.copyOf(knots), false);
        }
        return new Sweep(List.copyOf(knots), true);
    }

    private static int probes(Pose from, Pose to) {
        return Math.clamp((int) Math.ceil(motionBound(from, to) / PROBE_MOTION), 1, MAX_PROBES_PER_SEGMENT);
    }

    private record Hit(double distance, Candidate candidate) { }

    private static Hit nearest(ServerPlayer player, CollisionContext context, Pose pose, boolean radial) {
        var level = player.serverLevel();
        Vec3 start = pose.pivot(), end = pose.tip();
        Hit terrain = BlockGetter.traverseBlocks(start, end, player, (ignored, pos) -> {
            var unit = new AABB(pos);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || level.isOutsideBuildHeight(pos)) {
                Vec3 point = unit.clip(start, end).orElse(start);
                return new Hit(start.distanceTo(point), new Candidate(unit, Kind.UNKNOWN_TERRAIN, null, true));
            }
            var state = level.getBlockState(pos);
            if (state.isAir()) return null;
            Hit closest = null;
            for (AABB box : state.getCollisionShape(level, pos, context).toAabbs()) {
                AABB bounds = box.move(pos);
                Vec3 point = bounds.inflate(BLADE_RADIUS).clip(start, end).orElse(null);
                if (point == null) continue;
                double distance = start.distanceTo(point);
                if (closest == null || distance < closest.distance)
                    closest = new Hit(distance, new Candidate(bounds, Kind.BLOCK, null, true));
            }
            return closest;
        }, ignored -> null);

        double minimum = radial ? 0 : Math.max(0, pose.tipDistance() - BLADE_LENGTH);
        Vec3 bladeStart = start.add(pose.forward().scale(minimum));
        Hit closest = terrain;
        for (Entity entity : level.getEntities(player, new AABB(bladeStart, end).inflate(BLADE_RADIUS),
                e -> e.isAlive() && !e.isSpectator() && e.isPickable())) {
            AABB bounds = entity.getBoundingBox();
            Vec3 point = bounds.inflate(BLADE_RADIUS).clip(bladeStart, end).orElse(null);
            if (point == null) continue;
            double distance = start.distanceTo(point);
            if (closest == null || distance < closest.distance - 1e-9
                    || Math.abs(distance - closest.distance) <= 1e-9 && closest.candidate.kind() == Kind.ENTITY
                    && entity.getUUID().compareTo(closest.candidate.entity().getUUID()) < 0)
                closest = new Hit(distance, new Candidate(bounds, Kind.ENTITY, entity, false));
        }
        return closest;
    }
}

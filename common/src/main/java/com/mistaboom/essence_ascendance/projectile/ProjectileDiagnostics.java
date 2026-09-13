package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.OffenseProjectileEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure checks included in /essence test skills effects. These do not claim live collision or loader acceptance. */
public final class ProjectileDiagnostics {
    private ProjectileDiagnostics() { }
    public static List<String> validate() {
        List<String> errors = new ArrayList<>();
        var defaults = ProjectileBalanceSettings.defaults();
        validateSweptRow(errors);
        validateShieldBypass(errors);
        check(errors, defaults.caster().range() == 20 && defaults.caster().speed() == 3, "Caster launch defaults");
        var piercing = state(ProjectilePath.PIERCING, 0);
        UUID first = new UUID(1, 1), second = new UUID(1, 2), third = new UUID(1, 3);
        check(errors, piercing.visit(first) && !piercing.visit(first), "Duplicate victim rejected");
        check(errors, piercing.penetrate() && near(piercing.damageMultiplier, 0.8), "First skill penetration falloff");
        check(errors, piercing.visit(second) && piercing.penetrate() && near(piercing.damageMultiplier, 0.64), "Second skill penetration falloff");
        check(errors, piercing.visit(third) && !piercing.penetrate(), "Default skill stops after three victims");

        var composed = state(ProjectilePath.PIERCING, 4);
        composed.remainingRange = 17; composed.remainingTicks = 70; composed.chargeDischarged = true;
        for (int i = 0; i < 4; i++) {
            composed.visit(new UUID(2, i));
            check(errors, composed.penetrate() && composed.damageMultiplier == 1, "Native piercing damage preserved " + i);
        }
        check(errors, composed.penetrate() && near(composed.damageMultiplier, 0.8), "Skill follows native budget");
        var restored = ProjectileState.load(composed.save());
        check(errors, restored != null && restored.path == ProjectilePath.PIERCING && restored.source == ProjectileSource.ASCENDANCE_MAGIC
                && restored.visited.equals(composed.visited) && restored.remainingRange == 17 && restored.remainingTicks == 70
                && restored.nativePenetrations == 0 && restored.skillPenetrations == 1 && restored.chargeDischarged
                && restored.ownerLife.equals(composed.ownerLife) && restored.profile.equals(composed.profile)
                && near(restored.piercingShieldMultiplier, composed.piercingShieldMultiplier)
                && near(restored.damageMultiplier, 0.8), "Complete launch/state persistence");
        if (restored != null) check(errors, !restored.visit(new UUID(2, 0)), "Reload cannot repeat a victim");
        var ricochet = state(ProjectilePath.RICOCHET, 0);
        ricochet.remainingRange = 3; ricochet.remainingTicks = 2;
        check(errors, ricochet.ricochet() && near(ricochet.damageMultiplier, 0.75) && !ricochet.ricochet()
                && ricochet.remainingRange == 3 && ricochet.remainingTicks == 2, "One ricochet preserves distance/lifetime");
        var capped = state(ProjectilePath.PIERCING, 127);
        check(errors, capped.maximumImpacts >= 128, "Safety cap never reduces native piercing");
        for (int i = 0; i < capped.maximumImpacts; i++) capped.visit(new UUID(3, i));
        check(errors, !capped.visit(new UUID(4, 0)) && !capped.penetrate(), "Impact budget bounds stacked penetration");
        var zero = state(ProjectilePath.PIERCING, 0); zero.damageMultiplier = 0;
        check(errors, !zero.penetrate(), "Zero damage cannot continue");
        var corrupt = composed.save(); corrupt.putDouble("RemainingRange", Double.NaN);
        check(errors, ProjectileState.load(corrupt) == null, "Reject NaN persistence");
        corrupt = composed.save(); corrupt.putString("Path", "unknown_future_path");
        check(errors, ProjectileState.load(corrupt) == null, "Unknown saved path fails closed");
        corrupt = composed.save(); corrupt.putDouble("RemainingRange", 21);
        check(errors, ProjectileState.load(corrupt) == null, "Reload cannot enlarge travel budget");

        Vec3 velocity = new Vec3(0, 0, 3), desired = new Vec3(3, 0, 0);
        Vec3 arrow = ProjectileTargeting.turn(velocity, desired, defaults.arrow().turnDegreesPerTick());
        Vec3 bolt = ProjectileTargeting.turn(velocity, desired, defaults.caster().turnDegreesPerTick());
        check(errors, near(arrow.length(), 3) && near(bolt.length(), 3), "Steering preserves speed");
        check(errors, near(Math.toDegrees(Math.acos(arrow.normalize().dot(velocity.normalize()))), 2)
                && near(Math.toDegrees(Math.acos(bolt.normalize().dot(velocity.normalize()))), 12), "Profile-driven angular bounds");
        check(errors, ProjectileTargeting.turn(velocity, desired, 0).equals(velocity)
                && ProjectileTargeting.turn(Vec3.ZERO, desired, 12).equals(Vec3.ZERO), "Steering degenerate inputs");
        check(errors, OffenseProjectileEffects.selectedPath(Set.of(SkillIds.HOMING_PROJECTILE)) == ProjectilePath.HOMING
                && OffenseProjectileEffects.selectedPath(Set.of(SkillIds.RICOCHET)) == ProjectilePath.RICOCHET
                && OffenseProjectileEffects.selectedPath(Set.of(SkillIds.PIERCING_PROJECTILE)) == ProjectilePath.PIERCING,
                "Effective catalog path bindings");
        check(errors, OffenseProjectileEffects.selectedPath(Set.of()) == ProjectilePath.NONE
                && OffenseProjectileEffects.selectedPath(Set.of(SkillIds.HOMING_PROJECTILE, SkillIds.RICOCHET)) == ProjectilePath.NONE,
                "Missing or invalid multiple selection fails closed");
        check(errors, OffenseProjectileEffects.selectedPath(Set.of(SkillIds.EXPLOSIVE_PAYLOAD)) == ProjectilePath.NONE,
                "Payload selection never selects a path");
        return List.copyOf(errors);
    }

    private static void validateShieldBypass(List<String> errors) {
        var piercing = state(ProjectilePath.PIERCING, 0);
        var shield = ProjectileDefenseService.VANILLA_SHIELD;
        var barrier = ResourceLocation.fromNamespaceAndPath("essence_ascendance", "diagnostic_barrier");
        var supported = new ProjectileDefenseService.Resolution(Set.of(shield), piercing.piercingShieldMultiplier);
        check(errors, near(supported.damageMultiplier(), 0.8) && supported.bypasses(shield)
                && !supported.bypasses(barrier), "Shield bypass is explicit and retains 80% damage by default");
        var layered = new ProjectileDefenseService.Resolution(Set.of(shield, barrier), piercing.piercingShieldMultiplier);
        check(errors, near(layered.damageMultiplier(), 0.8) && layered.bypasses(barrier),
                "Overlapping supported shields apply one impact penalty");
        var unshielded = new ProjectileDefenseService.Resolution(Set.of(), piercing.piercingShieldMultiplier);
        check(errors, unshielded.damageMultiplier() == 1 && !unshielded.bypasses(shield),
                "Unshielded/native-bypassed hits have no skill shield penalty");
        check(errors, !ProjectileDefenseService.bypasses(shield, null, null), "No shield permission outside an impact scope");
        for (ProjectilePath path : List.of(ProjectilePath.NONE, ProjectilePath.HOMING, ProjectilePath.RICOCHET)) {
            check(errors, ProjectileDefenseService.resolve(null, state(path, 0)) == ProjectileDefenseService.Resolution.NONE,
                    path + " never queries or bypasses shields");
        }
        piercing.visit(new UUID(30, 1)); piercing.penetrate();
        check(errors, near(piercing.damageMultiplier * supported.damageMultiplier(), 0.64)
                && piercing.skillPenetrations == 1, "Shield penalty composes with falloff without spending extra penetrations");
        var tag = piercing.save();
        tag.putDouble("PiercingShieldMultiplier", 0.5);
        var restored = ProjectileState.load(tag);
        check(errors, restored != null && near(restored.piercingShieldMultiplier, 0.5),
                "Shield penalty is a saved launch snapshot");
        tag.remove("PiercingShieldMultiplier");
        restored = ProjectileState.load(tag);
        check(errors, restored != null && ProjectileDefenseService.resolve(null, restored) == ProjectileDefenseService.Resolution.NONE,
                "A snapshot without shield permission cannot acquire it after reload");
        tag.putDouble("PiercingShieldMultiplier", Double.NaN);
        check(errors, ProjectileState.load(tag) == null, "Invalid saved shield multiplier fails closed");
    }

    private static void validateSweptRow(List<String> errors) {
        // Reproduce the reported row at body height, with empty space between targets.
        // Contacts must stay on the flight segment, including when continuation crosses ticks.
        List<AABB> row = List.of(new AABB(-0.3, 0, 2.7, 0.3, 1.95, 3.3),
                new AABB(-0.3, 0, 4.7, 0.3, 1.95, 5.3),
                new AABB(-0.3, 0, 6.7, 0.3, 1.95, 7.3));
        for (ProjectileSource source : List.of(ProjectileSource.RANGED_PHYSICAL, ProjectileSource.ASCENDANCE_MAGIC)) {
            var state = new ProjectileState(source, ProjectilePath.PIERCING, new UUID(0, 1), new UUID(0, 2),
                    ResourceLocation.parse("minecraft:overworld"), 100, ProjectileBalanceSettings.defaults(), 0);
            Vec3 position = new Vec3(0, 1.5, 0);
            double startingRange = state.remainingRange;
            boolean ended = false;
            for (int tick = 0; tick < 4 && !ended; tick++) {
                double remainingStep = 3;
                while (remainingStep > 0.000001 && !ended) {
                    Vec3 end = position.add(0, 0, remainingStep);
                    int victim = -1;
                    Vec3 contact = end;
                    for (int i = 0; i < row.size(); i++) {
                        if (state.visited.contains(new UUID(10, i))) continue;
                        var candidate = ProjectileCollision.contact(row.get(i).inflate(0.3F), position, end);
                        if (candidate.isPresent() && (victim < 0
                                || position.distanceToSqr(candidate.get()) < position.distanceToSqr(contact))) {
                            victim = i; contact = candidate.get();
                        }
                    }
                    double travelled = position.distanceTo(contact);
                    remainingStep -= travelled; state.remainingRange -= travelled; position = contact;
                    if (victim < 0) break;
                    check(errors, near(position.y, 1.5), source + " contact remains at body height");
                    check(errors, state.visit(new UUID(10, victim)), source + " row impact is unique");
                    ended = !state.penetrate();
                }
            }
            check(errors, state.visited.size() == 3 && ended && near(state.damageMultiplier, 0.64),
                    source + " piercing reaches all three separated targets");
            check(errors, near(startingRange - state.remainingRange, position.z),
                    source + " row continuation consumes exact travel distance");
        }
        Vec3 start = new Vec3(0, 1.5, 0), beforeWall = new Vec3(0, 1.5, 2);
        check(errors, ProjectileCollision.contact(row.getFirst().inflate(0.3F), start, beforeWall).isEmpty(),
                "A block-clipped segment cannot hit the target behind it");
        Vec3 inside = new Vec3(0, 1.5, 3);
        check(errors, ProjectileCollision.contact(row.getFirst(), inside, inside.add(0, 0, 3)).orElseThrow().equals(inside),
                "Overlapping hitbox contacts at the segment start");
    }
    private static ProjectileState state(ProjectilePath path, int nativePiercing) {
        return new ProjectileState(ProjectileSource.ASCENDANCE_MAGIC, path, new UUID(0, 1), new UUID(0, 2),
                ResourceLocation.parse("minecraft:overworld"), 100, ProjectileBalanceSettings.defaults(), nativePiercing);
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.000001; }
    private static void check(List<String> errors, boolean condition, String message) { if (!condition) errors.add("Projectile: " + message); }
}

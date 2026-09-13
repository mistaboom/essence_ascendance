package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exercises the real catalog/evaluator and complete gameplay diagnostics without constructing a world. */
public final class ProjectileSkillBatchTest {
    private static int assertions;
    private static final List<ResourceLocation> PATHS = List.of(SkillIds.HOMING_PROJECTILE,
            SkillIds.RICOCHET, SkillIds.PIERCING_PROJECTILE);
    private static final List<ResourceLocation> PAYLOADS = List.of(SkillIds.EXPLOSIVE_PAYLOAD, SkillIds.ROOTING_PAYLOAD);

    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init();
        catalogAndComposition();
        prerequisiteChain();
        lifecycle();
        crossControlInteractions();
        for (var kind : SkillProcDamageService.DamageKind.values())
            check(kind.reflectedOutcome() == (kind == SkillProcDamageService.DamageKind.REDIRECTED_PROJECTILE),
                    "Only an actual returned-damage outcome participates in Defense reflection: " + kind);
        var failures = SkillEffectRuntime.validateInvariants();
        check(failures.isEmpty(), "Full gameplay diagnostics: " + failures);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "ProjectileSkillBatchTest: " + assertions + " catalog/composition/diagnostic assertions passed");
    }

    private static void catalogAndComposition() {
        check(SkillRegistry.values().size() == 90, "The curated catalog retains 90 skills");
        check(SkillEffectRegistry.implementedIds().size() == 18, "15 Offense and three Defense effects implemented");
        for (var id : List.of(SkillIds.EXPLOSIVE_PAYLOAD, SkillIds.ROOTING_PAYLOAD,
                SkillIds.PROJECTILE_DRAG_FIELD, SkillIds.INTERCEPTOR, SkillIds.TRAJECTORY_THEFT)) {
            check(SkillEffectRegistry.isImplemented(id), "Batch registered: " + id);
        }
        for (var id : List.of(SkillIds.GUARDED_ADVANCE, SkillIds.SHIELD_RAM, SkillIds.REFLEXIVE_WARD,
                SkillIds.EVASIVE_CURRENT, SkillIds.STATUS_MIRROR)) {
            check(!SkillEffectRegistry.isImplemented(id), "Future branches remain unimplemented: " + id);
        }
        check(!SkillGroups.OFFENSE_PROJECTILE_PATH.equals(SkillGroups.OFFENSE_PROJECTILE_PAYLOAD),
                "Path and payload retain separate choice groups");
        check(Set.copyOf(SkillRegistry.choiceGroup(SkillGroups.OFFENSE_PROJECTILE_PAYLOAD).orElseThrow().memberIds())
                .equals(Set.copyOf(PAYLOADS)), "Payload exclusivity matches the workbook");
        Map<ResourceLocation, Integer> owned = new HashMap<>();
        PATHS.forEach(id -> owned.put(id, 1)); PAYLOADS.forEach(id -> owned.put(id, 1));
        for (var path : PATHS) for (var payload : PAYLOADS) {
            var result = evaluate(owned, Map.of(SkillGroups.OFFENSE_PROJECTILE_PATH, path,
                    SkillGroups.OFFENSE_PROJECTILE_PAYLOAD, payload));
            for (var candidate : PATHS) check(result.get(candidate).effective() == candidate.equals(path),
                    "Exactly selected path effective for " + path + "/" + payload);
            for (var candidate : PAYLOADS) check(result.get(candidate).effective() == candidate.equals(payload),
                    "Exactly selected payload effective for " + path + "/" + payload);
        }
        for (var payload : PAYLOADS) {
            var result = evaluate(Map.of(payload, 1), Map.of(SkillGroups.OFFENSE_PROJECTILE_PAYLOAD, payload));
            check(result.get(payload).effective(), "Payload works without owning a path");
            check(PATHS.stream().noneMatch(id -> result.get(id).effective()), "Payload cannot activate an unowned path");
        }
    }

    private static void prerequisiteChain() {
        check(SkillRegistry.require(SkillIds.INTERCEPTOR).prerequisites().equals(List.of(SkillIds.PROJECTILE_DRAG_FIELD)),
                "Interceptor requires Drag Field exactly as curated");
        check(SkillRegistry.require(SkillIds.TRAJECTORY_THEFT).prerequisites().equals(List.of(SkillIds.INTERCEPTOR)),
                "Theft requires Interceptor exactly as curated");
        var owned = Map.of(SkillIds.PROJECTILE_DRAG_FIELD, 1, SkillIds.INTERCEPTOR, 1, SkillIds.TRAJECTORY_THEFT, 1);
        var ready = evaluate(owned, Map.of());
        owned.keySet().forEach(id -> check(ready.get(id).effective(), "Automatic defense chain effective: " + id));
        var missing = evaluate(Map.of(SkillIds.INTERCEPTOR, 1, SkillIds.TRAJECTORY_THEFT, 1), Map.of());
        check(!missing.get(SkillIds.INTERCEPTOR).effective() && !missing.get(SkillIds.TRAJECTORY_THEFT).effective(),
                "An absent Drag Field disables both descendants");
        var suppressed = evaluate(owned, Map.of(SkillIds.INTERCEPTOR, SkillIds.INTERCEPTOR));
        check(suppressed.get(SkillIds.PROJECTILE_DRAG_FIELD).effective()
                && !suppressed.get(SkillIds.INTERCEPTOR).effective() && !suppressed.get(SkillIds.TRAJECTORY_THEFT).effective(),
                "Opting out of Interceptor retains Drag Field and disables Theft");
        var onlyTheftOff = evaluate(owned, Map.of(SkillIds.TRAJECTORY_THEFT, SkillIds.TRAJECTORY_THEFT));
        check(onlyTheftOff.get(SkillIds.INTERCEPTOR).effective() && !onlyTheftOff.get(SkillIds.TRAJECTORY_THEFT).effective(),
                "Opting out of Theft retains ordinary Interceptor");
    }

    private static Map<ResourceLocation, SkillEvaluationResult> evaluate(Map<ResourceLocation, Integer> owned,
                                                                        Map<ResourceLocation, ResourceLocation> selected) {
        return SkillStateEvaluator.evaluateAll(SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(),
                owned, selected, Set.of(), Set.of(), Map.of()));
    }

    private static void lifecycle() {
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var otherDimension = ResourceLocation.withDefaultNamespace("the_nether");
        var owner = new java.util.UUID(0, 1);
        var life = new java.util.UUID(0, 2);
        for (var source : List.of(com.mistaboom.essence_ascendance.projectile.ProjectileSource.RANGED_PHYSICAL,
                com.mistaboom.essence_ascendance.projectile.ProjectileSource.ASCENDANCE_MAGIC)) {
            var state = new com.mistaboom.essence_ascendance.projectile.ProjectileState(source,
                    com.mistaboom.essence_ascendance.projectile.ProjectilePath.HOMING, owner, life, dimension, 100,
                    com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings.defaults(), 0);
            check(com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.ownerMatches(state, owner, life, dimension, true),
                    "Live owner identity matches");
            check(!com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.ownerMatches(state, owner,
                    new java.util.UUID(0, 3), dimension, true), "Death/respawn or logout invalidates the old life");
            check(!com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.ownerMatches(state,
                    new java.util.UUID(0, 4), life, dimension, true), "Unapproved ownership change fails closed");
            check(com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.accepts(state, dimension, 100, source, true),
                    "Launch tick is valid");
            check(com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.accepts(state, dimension,
                    100 + state.profile.lifetimeTicks() - 1, source, true), "Last live tick is valid");
            check(!com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.accepts(state, dimension,
                    100 + state.profile.lifetimeTicks(), source, true), "Exact lifetime boundary expires");
            check(!com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.accepts(state, dimension, 99, source, true),
                    "Clock rollback fails closed");
            check(!com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.accepts(state, otherDimension, 101, source, true),
                    "Dimension change invalidates flight");
            check(!com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.accepts(state, dimension, 101, source, false),
                    "Offline/dead/invalid owner cannot keep effects alive");
            var loaded = com.mistaboom.essence_ascendance.projectile.ProjectileState.load(state.save());
            check(loaded != null && !com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.accepts(loaded, dimension,
                    100 + state.profile.lifetimeTicks(), source, true), "Unload/reload never renews real age");
            state.remainingTicks = 0;
            check(!com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.accepts(state, dimension, 101, source, true),
                    "Spent flight tick budget cannot renew");
            state.remainingTicks = 1; state.remainingRange = 0;
            check(!com.mistaboom.essence_ascendance.projectile.ProjectileLifecycle.accepts(state, dimension, 101, source, true),
                    "Spent range cannot renew");
        }
    }

    private static void crossControlInteractions() {
        var tuning = com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings.defaults();
        var initialVelocity = new net.minecraft.world.phys.Vec3(0, 0, 3);
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var owner = new java.util.UUID(1, 1); var defender = new java.util.UUID(2, 1);
        for (var source : List.of(com.mistaboom.essence_ascendance.projectile.ProjectileSource.RANGED_PHYSICAL,
                com.mistaboom.essence_ascendance.projectile.ProjectileSource.ASCENDANCE_MAGIC))
            for (var path : com.mistaboom.essence_ascendance.projectile.ProjectilePath.values())
                for (var payload : PAYLOADS) {
                    var data = new net.minecraft.nbt.CompoundTag();
                    data.putInt("TriggerBudget", 3); data.putInt("Particles", 8);
                    if (payload.equals(SkillIds.EXPLOSIVE_PAYLOAD)) {
                        data.putDouble("Radius", 4); data.putDouble("DamageScale", .25); data.putInt("TargetLimit", 6);
                    } else {
                        data.putInt("Duration", 40); data.putInt("MaximumDuration", 80); data.putDouble("MovementTolerance", .0625);
                    }
                    var shot = new com.mistaboom.essence_ascendance.projectile.ProjectileState(source, path, owner, owner,
                            dimension, 50, tuning, 1);
                    shot.payload = new com.mistaboom.essence_ascendance.projectile.ProjectileImpactEffects.Snapshot(payload, data);
                    shot.remainingRange = 8; shot.remainingTicks = 5;
                    var savedBeforeDrag = shot.save();
                    var slowed = com.mistaboom.essence_ascendance.projectile.ProjectileControlMath.velocity(initialVelocity,
                            1, tuning.control().minimumSpeedFactor(), tuning.maximumSpeed());
                    check(shot.save().equals(savedBeforeDrag), "Drag preserves all launch-saved path/payload state");
                    check(slowed.length() > 0 && slowed.length() < initialVelocity.length(), "Every payload/path source slows finitely");
                    var control = new com.mistaboom.essence_ascendance.projectile.ProjectileControlState();
                    control.dragFactor = tuning.control().minimumSpeedFactor();
                    check(control.redirect(owner, defender, tuning.control().redirectBudget()), "Slowed composition can be stolen once");
                    var returned = shot.transferTo(defender, defender, tuning.control().theftTurnDegreesPerTick());
                    check(returned != null && returned.payload == null && returned.path == com.mistaboom.essence_ascendance.projectile.ProjectilePath.NONE
                            && returned.redirected && returned.remainingRange == 8 && returned.remainingTicks == 5,
                            "Theft strips original effects while preserving remaining flight");
                    var restoredControl = com.mistaboom.essence_ascendance.projectile.ProjectileControlState.load(control.save());
                    check(restoredControl != null && !restoredControl.redirect(defender, owner, 1),
                            "Slowed path/payload combinations cannot ping-pong after reload");
                    check(!returned.claimPayloadImpact(new java.util.UUID(3, 1), 10), "Returned damage cannot trigger an inherited payload");
                }
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}

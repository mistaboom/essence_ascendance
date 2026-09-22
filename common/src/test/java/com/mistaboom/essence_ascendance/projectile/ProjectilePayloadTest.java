package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.ImmobilizationController;
import com.mistaboom.essence_ascendance.skill.effect.OffenseProjectileEffects;
import com.mistaboom.essence_ascendance.skill.effect.PropagationBudget;
import com.mistaboom.essence_ascendance.skill.effect.SafeCreatureAreaService;
import com.mistaboom.essence_ascendance.skill.effect.SkillProcDamageService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Real NBT, profile and service contracts; native entities/multiplayer remain explicit world acceptance. */
public final class ProjectilePayloadTest {
    private static int checks;
    private static final UUID OWNER = new UUID(1, 1), LIFE = new UUID(1, 2), VICTIM = new UUID(2, 1);
    public static void main(String[] args) {
        choices(); continuationReceipts(); snapshotsAndTransfers(); malformedSnapshots(); roots(); area();
        com.mistaboom.essence_ascendance.skill.effect.SkillProcAttributionTest.verify();
        check(ProjectileDiagnostics.validate().isEmpty(), "Existing path/shield/swept collision regressions: " + ProjectileDiagnostics.validate());
        System.out.println("ProjectilePayloadTest: " + checks + " checks passed");
    }
    private static void choices() {
        var pathIds = List.of(SkillIds.HOMING_PROJECTILE, SkillIds.RICOCHET, SkillIds.PIERCING_PROJECTILE);
        var paths = List.of(ProjectilePath.HOMING, ProjectilePath.RICOCHET, ProjectilePath.PIERCING);
        for (int i = 0; i < pathIds.size(); i++) for (var payload : List.of(SkillIds.EXPLOSIVE_PAYLOAD, SkillIds.ROOTING_PAYLOAD)) {
            var selected = Set.of(pathIds.get(i), payload);
            check(OffenseProjectileEffects.selectedPath(selected) == paths.get(i), "Payload must not change selected path");
            check(payload.equals(ProjectileImpactEffects.selectedPayload(selected)), "Path must not change selected payload");
        }
        check(ProjectileImpactEffects.selectedPayload(Set.of(SkillIds.EXPLOSIVE_PAYLOAD, SkillIds.ROOTING_PAYLOAD)) == null,
                "Invalid dual payload selection fails closed");
        check(ProjectileImpactEffects.selectedPayload(Set.of(SkillIds.HOMING_PROJECTILE)) == null, "Path alone grants no payload");
    }
    private static ProjectileState state(ProjectileSource source, ProjectilePath path, ResourceLocation payload) {
        ProjectileState state = new ProjectileState(source, path, OWNER, LIFE,
                ResourceLocation.withDefaultNamespace("overworld"), 50, ProjectileBalanceSettings.defaults(), 2);
        if (payload != null) state.payload = snapshot(payload);
        return state;
    }
    private static ProjectileImpactEffects.Snapshot snapshot(ResourceLocation id) {
        CompoundTag data = new CompoundTag(); data.putInt("TriggerBudget", 3); data.putInt("Particles", 8);
        if (id.equals(SkillIds.EXPLOSIVE_PAYLOAD)) {
            data.putDouble("Radius", 4); data.putDouble("DamageScale", .25); data.putInt("TargetLimit", 6);
        } else {
            data.putInt("Duration", 40); data.putInt("MaximumDuration", 80); data.putDouble("MovementTolerance", .0625);
        }
        return new ProjectileImpactEffects.Snapshot(id, data);
    }
    private static void continuationReceipts() {
        ProjectileState ricochet = state(ProjectileSource.RANGED_PHYSICAL, ProjectilePath.RICOCHET, null);
        int bounces = ricochet.ricochets;
        check(!ProjectileRuntime.usedSkillContinuation(ricochet, ProjectilePath.RICOCHET, bounces),
                "A direct hit without a rebound has no Ricochet receipt");
        check(ricochet.ricochet() && ProjectileRuntime.usedSkillContinuation(ricochet, ProjectilePath.RICOCHET, bounces),
                "A spent rebound has a Ricochet receipt");

        ProjectileState piercing = state(ProjectileSource.RANGED_PHYSICAL, ProjectilePath.PIERCING, null);
        int penetrations = piercing.skillPenetrations;
        check(!ProjectileRuntime.usedSkillContinuation(piercing, ProjectilePath.PIERCING, penetrations),
                "A direct hit without continuation has no Piercing receipt");
        check(piercing.penetrate() && !ProjectileRuntime.usedSkillContinuation(piercing, ProjectilePath.PIERCING, penetrations),
                "Native penetration does not claim a skill continuation");
        piercing.nativePenetrations = 0;
        check(piercing.penetrate() && ProjectileRuntime.usedSkillContinuation(piercing, ProjectilePath.PIERCING, penetrations),
                "Spent skill penetration has a Piercing receipt");
        check(!ProjectileRuntime.usedSkillContinuation(piercing, ProjectilePath.RICOCHET, bounces),
                "One projectile path cannot claim another path's receipt");
    }
    private static void snapshotsAndTransfers() {
        for (var source : List.of(ProjectileSource.RANGED_PHYSICAL, ProjectileSource.ASCENDANCE_MAGIC))
            for (var path : ProjectilePath.values()) for (var payload : List.of(SkillIds.EXPLOSIVE_PAYLOAD, SkillIds.ROOTING_PAYLOAD)) {
                ProjectileState state = state(source, path, payload);
                check(state.managed(), "Payload-only vanilla shots enter shared collision routing");
                state.payload.data().putInt("TriggerBudget", 500);
                check(state.remainingPayloadTriggers() == 3, "Snapshot accessor must not expose mutable launch data");
                state.visit(VICTIM);
                check(!state.claimPayloadImpact(VICTIM, 0) && !state.claimPayloadImpact(VICTIM, Double.NaN), "Only finite confirmed damage activates");
                check(state.claimPayloadImpact(VICTIM, 8) && !state.claimPayloadImpact(VICTIM, 8), "Distinct victim once");
                state.remainingRange = 12; state.remainingTicks = 9; state.damageMultiplier = .64;
                if (path == ProjectilePath.HOMING) state.target = new UUID(3, 1);
                ProjectileState loaded = ProjectileState.load(state.save());
                check(loaded != null && loaded.payload.id().equals(payload) && loaded.path == path && loaded.source == source,
                        "Every supported source/path/payload snapshot survives NBT");
                check(loaded.remainingPayloadTriggers() == 2 && loaded.owner.equals(OWNER) && loaded.ownerLife.equals(LIFE)
                        && loaded.dimension.equals(state.dimension) && loaded.launchedAt == 50, "Lifecycle identity and used payload budget persist");
                check(loaded.profile.equals(state.profile) && loaded.payload.data().equals(state.payload.data()),
                        "Load never consults a current configuration or selected loadout");
                for (int n = 0; n < 4; n++) {
                    UUID next = new UUID(2, n + 2); loaded.visit(next);
                    check(loaded.claimPayloadImpact(next, 8) == (n < 2), "Continuation respects finite snapshot budget");
                }
                var redirected = loaded.transferTo(new UUID(9, 1), new UUID(9, 2), 18);
                check(redirected != null && redirected.redirected && redirected.managed() && redirected.path == ProjectilePath.NONE
                        && redirected.payload == null && redirected.target == null && redirected.chargeDischarged,
                        "Theft clears both offensive selections and charged-proc permission");
                check(redirected.remainingRange == 12 && redirected.remainingTicks == 9 && redirected.launchedAt == 50
                        && redirected.profile.range() == loaded.profile.range()
                        && redirected.profile.speed() == loaded.profile.speed()
                        && redirected.profile.turnDegreesPerTick() == Math.max(loaded.profile.turnDegreesPerTick(), 18)
                        && redirected.damageMultiplier == .64
                        && redirected.nativePenetrations == 2 && redirected.visited.equals(loaded.visited),
                        "Theft preserves native damage, spent travel/lifetime and hit history");
                check(redirected.ricochets == 0 && redirected.skillPenetrations == 0 && redirected.remainingPayloadTriggers() == 0,
                        "No original owner path or payload continuation survives transfer");
                check(ProjectileState.load(redirected.save()) != null, "Sanitized theft survives save/load");
            }
    }
    private static void malformedSnapshots() {
        ProjectileState state = state(ProjectileSource.RANGED_PHYSICAL, ProjectilePath.PIERCING, SkillIds.ROOTING_PAYLOAD);
        state.visit(VICTIM); state.claimPayloadImpact(VICTIM, 1);
        for (String key : List.of("Range", "Speed", "AcquireRange", "Cone", "Turn", "MaximumSpeed", "RemainingRange", "Multiplier")) {
            CompoundTag data = state.save(); data.putDouble(key, Double.NaN);
            check(ProjectileState.load(data) == null, "Corrupt floating snapshot fails closed: " + key);
        }
        for (String key : List.of("Lifetime", "MaximumImpacts", "RemainingTicks", "NativePenetrations")) {
            CompoundTag data = state.save(); data.putInt(key, -1);
            check(ProjectileState.load(data) == null, "Corrupt integer snapshot fails closed: " + key);
        }
        CompoundTag tag = state.save();
        ListTag duplicate = new ListTag(); duplicate.add(StringTag.valueOf(VICTIM.toString())); duplicate.add(StringTag.valueOf(VICTIM.toString()));
        tag.put("Visited", duplicate); check(ProjectileState.load(tag) == null, "Repeated saved hits cannot reopen payload budget");
        tag = state.save(); tag.put("Visited", new ListTag());
        check(ProjectileState.load(tag) == null, "A payload victim must have a confirmed routed projectile contact");
        tag = state.save(); tag.putBoolean("Redirected", true);
        check(ProjectileState.load(tag) == null, "Corrupt stolen snapshot cannot regain original skills");
        tag = state.save(); tag.getCompound("Payload").getCompound("Data").putInt("MaximumDuration", Integer.MAX_VALUE);
        check(ProjectileState.load(tag) == null, "Corrupt root cannot bind forever");
        tag = state.save(); tag.getCompound("Payload").putString("Id", "example:unknown");
        check(ProjectileState.load(tag) == null, "Unknown restored payload requires a registered contract");
        tag = state.save(); tag.putDouble("RemainingRange", state.profile.range() + 1);
        check(ProjectileState.load(tag) == null, "Restored projectile cannot extend original range");
        tag = state.save(); tag.putInt("RemainingTicks", state.profile.lifetimeTicks() + 1);
        check(ProjectileState.load(tag) == null, "Restored projectile cannot extend original lifetime");
        for (var source : List.of(ProjectileSource.SECONDARY, ProjectileSource.UNSUPPORTED)) {
            tag = state.save(); tag.putString("Source", source.name());
            check(ProjectileState.load(tag) == null, "Unsupported/secondary projectile cannot restore payload state");
        }
    }
    private static void roots() {
        var window = new ImmobilizationController.Window(100, 40, 80);
        check(window.active(100) && window.active(139) && !window.active(140), "Exact initial duration boundary");
        for (int now = 101; now < 180; now++) {
            check(window.refresh(now), "Independent confirmed impacts may refresh during active window");
            check(window.expiresAt() <= 180, "Repeated hits cannot shift first-application cap");
        }
        check(!window.active(180) && !window.refresh(180), "Expiry cannot refresh an exhausted window");
        check(!window.recovered(219) && window.recovered(220), "Guaranteed recovery gap prevents permanent repeated roots");
        check(!window.active(99) && window.recovered(99), "Clock rollback fails open for immobilization");
        var falling = ImmobilizationController.constrain(new Vec3(4, -.3, -7));
        check(falling.equals(new Vec3(0, -.3, 0)), "Actual horizontal translation/knockback blocked while gravity settles");
        check(ImmobilizationController.constrain(new Vec3(4, .8, 3)).equals(Vec3.ZERO), "Jump and upward knockback blocked");
        check(ImmobilizationController.constrain(new Vec3(Double.NaN, Double.NaN, Double.POSITIVE_INFINITY)).equals(Vec3.ZERO),
                "Bad velocity cannot create permanent invalid motion");
        check(!SkillProcDamageService.DamageKind.EXPLOSIVE_PAYLOAD.reflectedOutcome()
                        && SkillProcDamageService.DamageKind.REDIRECTED_PROJECTILE.reflectedOutcome(),
                "Actual redirected damage exposes generic defense-outcome metadata");
    }
    private static void area() {
        List<SafeCreatureAreaService.Candidate> candidates = new ArrayList<>();
        candidates.add(new SafeCreatureAreaService.Candidate(new UUID(0, 2), new Vec3(1, 0, 0), true));
        candidates.add(new SafeCreatureAreaService.Candidate(new UUID(0, 1), new Vec3(-1, 0, 0), true));
        candidates.add(new SafeCreatureAreaService.Candidate(OWNER, Vec3.ZERO, false));
        candidates.add(new SafeCreatureAreaService.Candidate(VICTIM, Vec3.ZERO, true));
        candidates.add(new SafeCreatureAreaService.Candidate(new UUID(0, 3), new Vec3(4, 0, 0), true));
        candidates.add(new SafeCreatureAreaService.Candidate(new UUID(0, 4), new Vec3(4.001, 0, 0), true));
        candidates.add(new SafeCreatureAreaService.Candidate(new UUID(0, 5), new Vec3(1, 1, 1), false));
        var selected = SafeCreatureAreaService.select(Vec3.ZERO, 4, 6, Set.of(VICTIM), candidates);
        check(selected.equals(List.of(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3))),
                "Area selects eligible nearby creatures only, excludes direct victim/owner/allies, exact sphere not box corners");
        java.util.Collections.reverse(candidates);
        check(selected.equals(SafeCreatureAreaService.select(Vec3.ZERO, 4, 6, Set.of(VICTIM), candidates)),
                "Equal-distance target selection is deterministic independent of world enumeration");
        check(SafeCreatureAreaService.select(Vec3.ZERO, 4, 1, Set.of(VICTIM), candidates).size() == 1, "Shared target budget enforced");
        var budget = new PropagationBudget(0, 6); budget.seed(VICTIM);
        check(!budget.tryVisit(VICTIM, 0), "Explosion never duplicates the direct impact target");
        check(budget.tryVisit(new UUID(0, 1), 0) && !budget.tryVisit(new UUID(0, 1), 0), "Area damages a victim once per burst");
        check(!budget.canContinue(1) && !budget.tryVisit(new UUID(0, 2), 1), "Payload burst cannot recursively propagate secondary kills");
        check(SafeCreatureAreaService.select(Vec3.ZERO, Double.NaN, 6, Set.of(), candidates).isEmpty(), "Invalid area fails closed");
    }
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}

package com.mistaboom.essence_ascendance.machine;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.team.*;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import java.math.BigInteger;
import java.util.*;

/** Normalized offline rosters exercise the exact production components with no optional provider. */
public final class MachineBindingTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), D = new UUID(0, 4);
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); AscendanceTiers.init();
        for (MembershipAuthority authority : MembershipAuthority.values()) {
            bindingContract(authority); operatorCreditContract(authority); dissolution(authority); pendingPlacement(authority); capacity(authority);
        }
        freshNativeFallback(); nativeFallback(); fallbackOwnershipHistory(); links(); pylonPacket();
        System.out.println("MachineBindingTest: " + checks + " assertions passed");
    }
    private static void bindingContract(MembershipAuthority authority) {
        var saved = new EssenceSavedData(); saved.establishMembershipAuthority(authority);
        var personal = new MachineBinding(); check(personal.initialize(saved, A), "Initial solo placement");
        var personalId = personal.pool(); var donated = new MachineBinding(); donated.initialize(saved, A);
        var team = roster(authority, 1, List.of(A, B), Set.of(B), B);
        var neighbor = roster(authority, 2, List.of(C, D), Set.of(C), C);
        publish(saved, team, neighbor);
        check(personal.refresh(saved) && personal.pool().equals(team.pool()), "Existing personal machine joins its owner's team automatically");
        check(personal.operational(saved) && personal.fundingActor(saved) != null, "Joined machine can use the team reservoir without adoption");
        check(personal.allows(saved, A, MachineAction.INVENTORY) && personal.allows(saved, B, MachineAction.CONFIGURE), "All current teammates can open and configure donated infrastructure");
        check(!personalId.equals(personal.pool()) && personal.personalOwner() == null && personal.placer().equals(A), "Automatic donation changes ownership while retaining placement provenance");
        CompoundTag unloadedPersonal = new CompoundTag(); donated.save(unloadedPersonal);
        check(saved.resolvePool(A).requireReady().members().equals(List.of(A, B)), "Machine donation never creates a second membership");
        var station = new MachineBinding(); var infuser = new MachineBinding(); var pylon = new MachineBinding();
        check(station.initialize(saved, B) && infuser.initialize(saved, A) && pylon.initialize(saved, B), "All newly placed machines inherit current membership");
        var stolen = new MachineBinding(); stolen.initialize(saved, A); stolen.start(saved, A, A);
        check(stolen.place(saved, C) && stolen.pool().equals(neighbor.pool()) && stolen.placer().equals(C)
                && stolen.operator() == null && stolen.recipient() == null, "Stolen physical machine placement creates fresh ownership and clears work grants");
        check(station.samePool(saved, infuser) && station.samePool(saved, pylon), "Different placers share infrastructure pool");
        check(!station.allows(saved, C, MachineAction.USE), "Neighboring team cannot use infrastructure");
        check(infuser.start(saved, B, B, C) && infuser.recipient().equals(C), "Existing soulbinding determines equipment recipient independently of operator and pool");
        check(infuser.start(saved, B, B), "Operator and recipient are explicit, independent of placer");
        long revision = infuser.revision();
        team = roster(authority, 1, List.of(B, D), Set.of(B), B);
        neighbor = roster(authority, 2, List.of(C), Set.of(C), C); publish(saved, team, neighbor);
        check(infuser.pool().equals(team.pool()) && infuser.placer().equals(A), "Placer departure never transfers team infrastructure");
        var unloaded = new MachineBinding(); unloaded.load(unloadedPersonal);
        check(unloaded.refresh(saved) && unloaded.pool().equals(team.pool()) && !unloaded.allows(saved, A, MachineAction.USE),
                "Machine unloaded for both join and departure still belongs to the first team joined");
        var newlyPersonal = new MachineBinding(); newlyPersonal.initialize(saved, A);
        check(!newlyPersonal.refresh(saved) && newlyPersonal.personalOwner().equals(A), "A new solo placement does not replay older team joins");
        check(infuser.operatorValid(saved) && infuser.revision() == revision, "Same-team roster change does not disarm ownership/job grant");
        check(!infuser.allows(saved, A, MachineAction.USE), "Departed placer loses access, including stale menus");
        var paused = new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "fixture", "Unsettled roster");
        saved.reservoirs().pause(paused);
        check(!infuser.operational(saved) && !infuser.refresh(saved) && infuser.operator().equals(B), "Unavailable membership pauses without changing ownership or operator");
        publish(saved, team, neighbor);
        check(infuser.operatorValid(saved), "Same identity and grants resume after settlement");
        var grants = new HashMap<>(team.grants()); grants.put(B, Set.of(PoolCapability.USE, PoolCapability.MANAGE));
        team = new MembershipRoster(team.pool(), team.members(), grants, B); publish(saved, team, neighbor);
        check(!infuser.operatorValid(saved), "Revoked spending is rechecked without a membership revision change");
        CompoundTag nbt = new CompoundTag(); infuser.save(nbt); var restored = new MachineBinding(); restored.load(nbt);
        check(restored.pool().equals(infuser.pool()) && restored.operator().equals(B) && restored.revision() == revision, "Binding and distinct roles persist");
        check(!restored.operatorValid(saved), "Unloaded machine revalidates saved operator grants");
    }
    private static void operatorCreditContract(MembershipAuthority authority) {
        var saved = new EssenceSavedData(); saved.establishMembershipAuthority(authority);
        var team = roster(authority, 30, List.of(A, B), Set.of(A), A); publish(saved, team);
        var machine = new MachineBinding(); machine.place(saved, A);
        check(machine.operator() == null && !machine.operatorValid(saved),
                "Working placed infrastructure has no inferred human operator");
        check(machine.operational(saved) && machine.fundingActor(saved).equals(A),
                "Automatic reservoir funding can exist independently of operation credit");
        check(machine.start(saved, B, A) && machine.operator().equals(B) && machine.operatorValid(saved),
                "Authorized member explicitly becomes operator independently of owner and recipient");
        check(machine.fundingActor(saved).equals(A) && machine.recipient().equals(A) && machine.placer().equals(A),
                "Operation credit does not rewrite funding, item recipient, or placement provenance");
        long revision = machine.revision(); var pool = machine.pool(); boolean paused = machine.paused();
        check(!machine.start(saved, C, null) && machine.operator().equals(B),
                "A rejected outsider request cannot steal attribution from the real operator");
        CompoundTag snapshot = new CompoundTag(); machine.save(snapshot);
        var restored = new MachineBinding(); restored.load(snapshot);
        check(restored.operator().equals(B) && restored.operatorValid(saved),
                "An explicit operator survives the existing machine-binding save contract");
        CompoundTag oldWorld = snapshot.copy(); oldWorld.getCompound("machine_binding").remove("operator");
        var withoutOperator = new MachineBinding(); withoutOperator.load(oldWorld);
        check(withoutOperator.operator() == null && !withoutOperator.operatorValid(saved)
                        && withoutOperator.fundingActor(saved).equals(A),
                "Old-world absence of an operator remains unattributed rather than borrowing the funding actor");
        machine.clearOperator();
        check(machine.operator() == null && !machine.operatorValid(saved),
                "Ineligible manual work can clear operation credit immediately");
        check(machine.pool().equals(pool) && machine.recipient().equals(A) && machine.revision() == revision
                        && machine.paused() == paused && machine.operational(saved),
                "Clearing operation credit leaves infrastructure, workpiece, revision, and processing availability intact");
        CompoundTag cleared = new CompoundTag(); machine.save(cleared); restored.load(cleared);
        check(restored.operator() == null && !restored.operatorValid(saved),
                "Cleared operation attribution cannot return after save/reload");
        check(machine.start(saved, B, null), "Fresh authorized operation re-establishes credit after clearing");
        publish(saved, roster(authority, 30, List.of(A), Set.of(A), A));
        check(!machine.operatorValid(saved) && machine.fundingActor(saved).equals(A),
                "A departed operator cannot use another current member's funding authority to retain credit");
        machine.place(saved, A);
        check(machine.operator() == null && machine.recipient() == null,
                "Fresh physical placement clears every previous operation claim");
    }

    private static void dissolution(MembershipAuthority authority) {
        var saved = new EssenceSavedData(); saved.establishMembershipAuthority(authority);
        var team = roster(authority, 8, List.of(A, B), Set.of(A, B), B); publish(saved, team);
        var machine = new MachineBinding(); machine.initialize(saved, A); machine.start(saved, A, A);
        CompoundTag unloadedMachine = new CompoundTag(); machine.save(unloadedMachine);
        publish(saved);
        check(saved.reservoirs().isDissolved(team.pool()) && saved.reservoirs().dissolutionRecipient(team.pool()).equals(B), "Confirmed deletion remembers actual leader, not arbitrary officer");
        check(machine.refresh(saved) && machine.personalOwner().equals(B) && !machine.paused() && machine.operational(saved),
                "Final disband returns usable personal infrastructure to the owner");
        var nextTeam = roster(authority, 9, List.of(B, C), Set.of(B), B); publish(saved, nextTeam);
        CompoundTag incomplete = unloadedMachine.copy();
        incomplete.getCompound("machine_binding").remove("ownership_roster_revision");
        rejects(() -> new MachineBinding().load(incomplete), "Missing ownership cursor is rejected without guessing current membership");
        CompoundTag missingJoins = saved.save(new CompoundTag(), null);
        missingJoins.getCompound("reservoir_settlement").remove("machine_joins");
        rejects(() -> EssenceSavedData.load(missingJoins, null), "Missing persisted machine join history is rejected");
        CompoundTag malformedJoins = saved.save(new CompoundTag(), null);
        malformedJoins.getCompound("reservoir_settlement").putString("machine_joins", "invalid");
        rejects(() -> EssenceSavedData.load(malformedJoins, null), "Malformed machine join history is rejected");
        CompoundTag missingDeletion = saved.save(new CompoundTag(), null);
        com.mistaboom.essence_ascendance.data.NbtVersionCompat.getList(
                missingDeletion.getCompound("reservoir_settlement"), "dissolved", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).remove("roster_revision");
        rejects(() -> EssenceSavedData.load(missingDeletion, null), "Missing dissolution cursor is rejected");
        CompoundTag invalidDeletion = saved.save(new CompoundTag(), null);
        com.mistaboom.essence_ascendance.data.NbtVersionCompat.getList(
                invalidDeletion.getCompound("reservoir_settlement"), "dissolved", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).putLong("roster_revision", 0);
        rejects(() -> EssenceSavedData.load(invalidDeletion, null), "Zero dissolution cursor is rejected");
        var restarted = EssenceSavedData.load(saved.save(new CompoundTag(), null), null); publish(restarted, nextTeam);
        var restored = new MachineBinding(); restored.load(unloadedMachine);
        check(restored.refresh(restarted), "Unloaded disband resolves from persisted metadata after restart");
        check(restored.personalOwner() == null && restored.pool().equals(nextTeam.pool()) && !restored.paused(), "Unloaded infrastructure replays disband followed by the owner's next team join");
        check(restored.operational(restarted), "Automatic membership lifecycle leaves ordinary machine operation available");
        check(restored.operator() == null && restored.recipient() == null && !restored.refresh(restarted), "Funding identity changes clear roles once");
        var ambiguous = roster(authority, 10, List.of(A, D), Set.of(A, D), null); publish(restarted, nextTeam, ambiguous);
        var locked = new MachineBinding(); locked.initialize(restarted, D); publish(restarted, nextTeam);
        check(!locked.refresh(restarted) && !locked.operational(restarted) && locked.pool().equals(ambiguous.pool()), "Unknown leader stays locked for administrator recovery");
        var singleton = roster(authority, 11, List.of(A), Set.of(A), A); publish(restarted, nextTeam, singleton);
        var sole = new MachineBinding(); sole.initialize(restarted, A); publish(restarted, nextTeam);
        check(sole.refresh(restarted) && sole.personalOwner().equals(A), "Last-member departure resolves to authoritative sole member");
        var coalesced = new EssenceSavedData(); coalesced.establishMembershipAuthority(authority);
        var oldParty = roster(authority, 12, List.of(A, B), Set.of(A), A); publish(coalesced, oldParty);
        var moved = new MachineBinding(); moved.initialize(coalesced, A);
        var newParty = roster(authority, 13, List.of(A, C), Set.of(A), A); publish(coalesced, newParty);
        check(moved.refresh(coalesced) && moved.pool().equals(newParty.pool()) && moved.operational(coalesced),
                "One provider publication can dissolve the old pool and join its successor to another team");
    }
    private static void pendingPlacement(MembershipAuthority authority) {
        var saved = new EssenceSavedData(); saved.establishMembershipAuthority(authority);
        var stolen = new MachineBinding(); stolen.initialize(saved, A); stolen.start(saved, A, A);
        saved.reservoirs().pause(new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "fixture", "Roster loading"));
        check(stolen.place(saved, B) && stolen.pool() == null && stolen.placer().equals(B)
                && stolen.operator() == null && !stolen.allows(saved, A, MachineAction.USE) && !stolen.operational(saved),
                "Physical placement during unresolved membership immediately removes former ownership and funding");
        CompoundTag placed = new CompoundTag(); stolen.save(placed);
        var restored = new MachineBinding(); restored.load(placed);
        check(!restored.refresh(saved), "Pending placement stays unavailable until authority settles");
        var team = roster(authority, 15, List.of(B, C), Set.of(B), B); publish(saved, team);
        check(restored.refresh(saved) && restored.pool().equals(team.pool()) && restored.placer().equals(B)
                && restored.allows(saved, C, MachineAction.USE) && !restored.allows(saved, A, MachineAction.USE),
                "Saved trusted placement resolves to the new placer's ready membership");
        var legacy = new MachineBinding(); CompoundTag provenanceOnly = new CompoundTag(); provenanceOnly.putUUID("owner", A); legacy.load(provenanceOnly);
        check(!legacy.refresh(saved) && legacy.pool() == null, "Old provenance never becomes a pending placement grant");
    }
    private static void capacity(MembershipAuthority authority) {
        var saved = new EssenceSavedData(); saved.establishMembershipAuthority(authority);
        var team = roster(authority, 3, List.of(B, A, B), Set.of(A), A); publish(saved, team);
        var small = MachineCapacity.limit(100, saved.resolvePool(A).requireReady());
        var large = MachineCapacity.limit(1000, saved.resolvePool(B).requireReady());
        check(small.equals(BigInteger.valueOf(200)) && large.equals(BigInteger.valueOf(2000)), "Unique offline effective members multiply each local structure's capacity");
        BigInteger balance = BigInteger.valueOf(400).multiply(ReservoirService.SCALE);
        check(!MachineCapacity.accepts(balance, ReservoirService.SCALE, small) && MachineCapacity.accepts(balance, ReservoirService.SCALE, large), "Unequal stations use one shared balance and different addition limits");
        saved.reservoirs().credit(A, Map.of(EssenceTypes.OFFENSE.id(), balance), ReservoirService.NONE);
        team = roster(authority, 3, List.of(A), Set.of(A), A); publish(saved, team);
        check(saved.reservoirs().read(A).whole(EssenceTypes.OFFENSE.id()).equals(BigInteger.valueOf(200)), "Over-capacity departure preserves the detached and remaining shares");
        check(saved.reservoirs().channel(A, Map.of(EssenceTypes.OFFENSE.id(), 150L)), "Over-capacity withdrawal remains possible");
        check(saved.getPlayerData(B).getCrucibleStored(EssenceTypes.OFFENSE) == 200, "Departed share never gets individually clamped");
        var wide = MachineCapacity.limit(Long.MAX_VALUE, new MembershipSnapshot(team.pool(), List.of(A,B), MembershipPolicy.grants(List.of(A,B),Set.of(A)),1,1));
        check(wide.equals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO)), "Fill limit multiplication is exact beyond signed long");
        check(MachineCapacity.visualFill(BigInteger.valueOf(100).multiply(ReservoirService.SCALE),BigInteger.valueOf(200)) == 5000, "Visual fill uses aggregate capacity without dividing by six");
        check(!MachineCapacity.accepts(BigInteger.valueOf(200).multiply(ReservoirService.SCALE), BigInteger.ONE, small), "Even a fractional addition is rejected before input consumption");
    }
    private static void freshNativeFallback() {
        var saved = new EssenceSavedData(); saved.establishMembershipAuthority(MembershipAuthority.FTB_TEAMS);
        saved.reservoirs().pause(new ProviderReadiness(ProviderReadiness.Status.FAILED, "future-api",
                "Actual API failure before the first complete roster"));
        var machine = new MachineBinding(); machine.place(saved, A);
        CompoundTag pending = new CompoundTag(); machine.save(pending);
        CompoundTag before = saved.save(new CompoundTag(), null);
        check(machine.pool() == null && !machine.operational(saved)
                        && before.getCompound("pool_membership").getCompound("solos").isEmpty()
                        && saved.reservoirs().settledTeams().isEmpty(),
                "First placement records only its trusted placer when the external API fails before any membership exists");
        check(MembershipFallback.activate(saved, "Actual API failed before any membership was confirmed"),
                "A fresh failed external authority can fall back without inventing a previous team or solo record");
        CompoundTag fallback = saved.save(new CompoundTag(), null);
        var membership = fallback.getCompound("pool_membership");
        check(membership.getCompound("solos").isEmpty()
                        && NbtVersionCompat.getList(membership, "fallback_pools", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty()
                        && fallback.getCompound("players").equals(before.getCompound("players")),
                "Fresh fallback persists an empty identity mapping without creating accounts or sharing money");
        var restarted = EssenceSavedData.load(fallback, null);
        check(restarted.membershipAuthority() == MembershipAuthority.NATIVE && restarted.hasMembershipFallback()
                        && restarted.membershipFallbackReason().equals(saved.membershipFallbackReason()),
                "Empty fallback metadata and its failure explanation survive restart before the first player resolves");
        var restored = new MachineBinding(); restored.load(pending);
        check(restored.refresh(restarted) && restored.personalOwner().equals(A) && restored.operational(restarted)
                        && restored.allows(restarted, A, MachineAction.USE) && !restored.allows(restarted, B, MachineAction.USE),
                "The originally placed device becomes usable only by its placer without creating a team or replacing it");
        check(restarted.resolvePool(A).requireReady().isSoloFor(A) && restarted.reservoirs().settledTeams().isEmpty(),
                "Fresh fallback grants ordinary personal access and never manufactures a party");
        var fresh = new MachineBinding(); fresh.place(restarted, A);
        check(fresh.operational(restarted) && fresh.pool().equals(restored.pool()),
                "Subsequent placements use the same ready personal identity");
        var reloaded = EssenceSavedData.load(restarted.save(new CompoundTag(), null), null);
        check(reloaded.resolvePool(A).requireReady().pool().equals(restored.pool()),
                "The newly resolved native solo identity persists after the empty-fallback recovery");
    }
    private static void nativeFallback() {
        var saved = new EssenceSavedData(); saved.establishMembershipAuthority(MembershipAuthority.FTB_TEAMS);
        publish(saved);
        var personalBeforeJoin = new MachineBinding(); personalBeforeJoin.place(saved, A);
        CompoundTag beforeJoin = new CompoundTag(); personalBeforeJoin.save(beforeJoin);
        var team = roster(MembershipAuthority.FTB_TEAMS, 60, List.of(A, B), Set.of(A, B), A); publish(saved, team);
        saved.reservoirs().credit(A, Map.of(EssenceTypes.OFFENSE.id(), ReservoirService.SCALE.multiply(BigInteger.valueOf(101)).add(BigInteger.ONE)), ReservoirService.NONE);
        saved.getPlayerData(A).setAvailable(EssenceTypes.OFFENSE, 13);
        saved.getPlayerData(B).setAvailable(EssenceTypes.OFFENSE, 7);
        var loadedMachine = new MachineBinding(); loadedMachine.place(saved, A); loadedMachine.start(saved, A, A);
        CompoundTag unloadedMachine = new CompoundTag(); loadedMachine.save(unloadedMachine);
        var personal = new MachineBinding(); personal.place(saved, C); PoolId priorSolo = personal.pool();
        BigInteger total = saved.reservoirs().read(A).units(EssenceTypes.OFFENSE.id());
        long financialRevision = saved.reservoirs().revision(), rosterRevision = saved.reservoirs().rosterRevision();
        CompoundTag before = saved.save(new CompoundTag(), null);
        saved.reservoirs().pause(new ProviderReadiness(ProviderReadiness.Status.FAILED, "future", "Actual missing FTB API"));
        var pending = new MachineBinding(); pending.place(saved, D);
        check(MembershipFallback.activate(saved, "Actual missing FTB API") && saved.membershipAuthority() == MembershipAuthority.NATIVE,
                "An actual provider failure can activate built-in teams without losing the last confirmed roster");
        PoolId nativeTeam = saved.remapFallbackPool(team.pool()), nativeSolo = saved.remapFallbackPool(priorSolo);
        check(nativeTeam.authority() == MembershipAuthority.NATIVE && !nativeTeam.equals(team.pool())
                        && nativeSolo.authority() == MembershipAuthority.NATIVE && !nativeSolo.equals(priorSolo),
                "Fallback records exact native replacement identities for known teams and solo pools");
        check(saved.resolvePool(A).requireReady().pool().equals(nativeTeam)
                        && saved.resolvePool(B).requireReady().pool().equals(nativeTeam)
                        && saved.resolvePool(C).requireReady().isSoloFor(C),
                "Last-confirmed teammates stay together and unrelated solo players stay separate");
        check(saved.save(new CompoundTag(), null).getCompound("players").equals(before.getCompound("players"))
                        && saved.reservoirs().read(A).units(EssenceTypes.OFFENSE.id()).equals(total),
                "Fallback preserves every player record, whole amount, fractional carry and personal Available balance");
        check(saved.resolvePool(A).requireReady().allows(A, PoolCapability.MANAGE)
                        && !saved.resolvePool(B).requireReady().allows(B, PoolCapability.MANAGE)
                        && saved.resolvePool(B).requireReady().allows(B, PoolCapability.USE)
                        && saved.resolvePool(B).requireReady().allows(B, PoolCapability.SPEND),
                "The recorded owner becomes native manager while former external officers retain normal member access");
        check(saved.reservoirs().revision() == financialRevision + 1 && saved.reservoirs().rosterRevision() == rosterRevision + 1
                        && cursorPositions(before, team.pool()).equals(cursorPositions(saved.save(new CompoundTag(), null), nativeTeam)),
                "Fallback invalidates old transaction and roster receipts once, retaining remainder rotation without equalizing again");
        check(loadedMachine.refresh(saved) && loadedMachine.pool().equals(nativeTeam)
                        && loadedMachine.allows(saved, B, MachineAction.USE) && !loadedMachine.allows(saved, C, MachineAction.USE),
                "Loaded team machinery follows its exact preserved roster without outsider access");
        check(personal.refresh(saved) && personal.pool().equals(nativeSolo) && personal.allows(saved, C, MachineAction.USE),
                "Personal machinery remains usable by its owner after fallback");
        check(pending.refresh(saved) && pending.personalOwner().equals(D) && pending.allows(saved, D, MachineAction.USE),
                "A device placed during a failed provider becomes usable through native solo membership");
        var restarted = EssenceSavedData.load(saved.save(new CompoundTag(), null), null);
        var unloaded = new MachineBinding(); unloaded.load(unloadedMachine);
        check(unloaded.refresh(restarted) && unloaded.pool().equals(nativeTeam) && unloaded.allows(restarted, B, MachineAction.USE),
                "Saved fallback aliases recover machinery that was unloaded during the failure and server restart");
        var beforeParty = new MachineBinding(); beforeParty.load(beforeJoin);
        check(beforeParty.refresh(restarted) && beforeParty.pool().equals(nativeTeam),
                "An unloaded personal machine replays its confirmed team join after fallback");
        var unknown = new PoolId(MembershipAuthority.FTB_TEAMS, PoolId.Kind.TEAM, new UUID(99, 99));
        CompoundTag foreign = unloadedMachine.copy(); foreign.getCompound("machine_binding").put("pool", unknown.save());
        var orphan = new MachineBinding(); orphan.load(foreign);
        check(restarted.remapFallbackPool(unknown).equals(unknown) && !orphan.refresh(restarted)
                        && !orphan.operational(restarted) && !orphan.allows(restarted, A, MachineAction.USE),
                "Unknown old team ownership is never guessed from the placer or granted to a current native team");
        CompoundTag once = restarted.save(new CompoundTag(), null);
        check(!MembershipFallback.activate(restarted, "Repeated same API error") && restarted.save(new CompoundTag(), null).equals(once),
                "Repeated fallback does not regenerate IDs, metadata receipts or player money");
        restarted.nativeMembership().leave(B);
        check(!unloaded.allows(restarted, B, MachineAction.USE) && unloaded.allows(restarted, A, MachineAction.USE),
                "Built-in team controls work after fallback and revoke a departing member's device access");
    }
    private static CompoundTag cursorPositions(CompoundTag world, PoolId pool) {
        var entries = NbtVersionCompat.getList(world.getCompound("reservoir_settlement"), "cursors", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            if (PoolId.load(entry.getCompound("pool")).equals(pool)) return entry.getCompound("positions").copy();
        }
        return new CompoundTag();
    }
    private static void fallbackOwnershipHistory() {
        for (boolean dissolved : List.of(false, true)) {
            var saved = new EssenceSavedData(); saved.establishMembershipAuthority(MembershipAuthority.FTB_TEAMS); publish(saved);
            var machine = new MachineBinding(); machine.place(saved, A);
            CompoundTag beforeJoin = new CompoundTag(); machine.save(beforeJoin);
            var oldTeam = roster(MembershipAuthority.FTB_TEAMS, 70, List.of(A, B), Set.of(B), B); publish(saved, oldTeam);
            var retained = roster(MembershipAuthority.FTB_TEAMS, 70, List.of(B), Set.of(B), B);
            if (dissolved) publish(saved); else publish(saved, retained);
            var newTeam = roster(MembershipAuthority.FTB_TEAMS, 71, List.of(A, C), Set.of(A), A);
            if (dissolved) publish(saved, newTeam); else publish(saved, retained, newTeam);
            MembershipFallback.activate(saved, "API failed after recorded ownership transitions");
            var restarted = EssenceSavedData.load(saved.save(new CompoundTag(), null), null);
            var unloaded = new MachineBinding(); unloaded.load(beforeJoin);
            check(unloaded.refresh(restarted) && unloaded.allows(restarted, B, MachineAction.USE)
                            && !unloaded.allows(restarted, A, MachineAction.USE),
                    dissolved ? "Fallback replays a confirmed dissolution to its recovery owner, not the former placer's new team"
                            : "Fallback preserves join then departure history so unloaded machinery remains with the original team");
        }
    }
    private static void pylonPacket() {
        var wide = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        var payload = new com.mistaboom.essence_ascendance.network.EssencePylonStatePayload(
                3, 1, 0, "team", true, false, 4, false, true, "dormant", 16, 8, 100, 2, 10, 0.1, 1,
                0, wide, 16, 20, 20, 1, "LOCKED", "UNAVAILABLE", "minecraft:overworld");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            com.mistaboom.essence_ascendance.network.EssencePylonStatePayload.CODEC.encode(buffer, payload);
            check(com.mistaboom.essence_ascendance.network.EssencePylonStatePayload.CODEC.decode(buffer).equals(payload),
                    "Pylon exact local capacity and unloaded/Locked dimension-aware target state survive real packet codec");
        } finally { buffer.release(); }
    }
    private static void links() {
        ResourceLocation dimension = ResourceLocation.parse("minecraft:overworld");
        var first = new MachineLink.Source(dimension, new BlockPos(4,0,0));
        var closer = new MachineLink.Source(dimension, new BlockPos(1,0,0));
        for (String caller : List.of("Infuser", "Pylon")) {
            var link = new MachineLink();
            check(link.refresh(MachineLink.State.INVALID, () -> first), caller + " Automatic initially selects an eligible source");
            check(!link.refresh(MachineLink.State.VALID, () -> { throw new AssertionError("Stable link rescanned"); }), "Closer placement preserves valid link");
            check(!link.refresh(MachineLink.State.UNAVAILABLE, () -> { throw new AssertionError("Unloaded target rerouted"); }) && link.target().equals(first), "Unloaded target pauses and retains identity");
            link.configure(MachineLink.Mode.LOCKED, first);
            check(!link.refresh(MachineLink.State.INVALID, () -> { throw new AssertionError("Locked fallback"); }), "Locked never falls back after removal or authorization loss");
            link.configure(MachineLink.Mode.AUTOMATIC, first);
            check(link.refresh(MachineLink.State.INVALID, () -> closer) && link.target().equals(closer), "Automatic replaces confirmed invalid target");
            link.configure(MachineLink.Mode.DISCONNECTED, null);
            check(link.target() == null && !link.refresh(MachineLink.State.DISCONNECTED, () -> { throw new AssertionError("Disconnected scanned"); }), "Disconnected has no source");
            CompoundTag saved = new CompoundTag(); link.save(saved); var loaded = new MachineLink(); loaded.load(saved);
            check(loaded.mode() == MachineLink.Mode.DISCONNECTED && loaded.target() == null, "Link modes persist");
            saved.getCompound("machine_link").remove("version");
            rejects(() -> loaded.load(saved), "Missing link schema is rejected without changing the saved mode");
            link.configure(MachineLink.Mode.LOCKED, first); link.save(saved); loaded.load(saved);
            check(loaded.mode() == MachineLink.Mode.LOCKED && loaded.target().equals(first), "Current Locked link roundtrips exactly");
            saved.getCompound("machine_link").putInt("version", 1);
            rejects(() -> loaded.load(saved), "Unsupported link schema is rejected");
        }
        var negative = new BlockPos(-1,0,0); var positive = new BlockPos(1,0,0);
        var tie = MachineLink.nearest(dimension, BlockPos.ZERO, List.of(positive, negative));
        check(tie.equals(MachineLink.nearest(dimension, BlockPos.ZERO, List.of(negative, positive))), "Deterministic ties ignore iteration order");
        check(!first.equals(new MachineLink.Source(ResourceLocation.parse("minecraft:the_nether"), first.pos())), "Source identity includes dimension");
    }
    private static MembershipRoster roster(MembershipAuthority authority, long id, List<UUID> members, Set<UUID> managers, UUID leader) {
        return MembershipRoster.party(authority, new UUID(1,id), members, managers, leader);
    }
    private static void publish(EssenceSavedData saved, MembershipRoster... teams) { MembershipPublication.publish(saved, NativeMembershipService.READY, List.of(teams)); }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalStateException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}

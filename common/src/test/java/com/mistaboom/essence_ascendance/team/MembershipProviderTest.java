package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.machine.MachinePoolAccess;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import java.math.BigInteger;
import java.util.*;

/** Shared production-boundary fixtures, not proof of a real FTB server/multiplayer interaction. */
public final class MembershipProviderTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), D = new UUID(0, 4);
    private static final UUID TEAM = new UUID(1, 1);
    private static final ProviderReadiness READY = NativeMembershipService.READY;
    private static int assertions;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); AscendanceTiers.init();
        nativeOperations(); nativeFailures(); personalAccessRequests(); approvalNaming(); pendingRequestLifecycle(); machineOwnershipReceipts();
        for (MembershipAuthority authority : MembershipAuthority.values()) providerContract(authority);
        for (MembershipAuthority authority : List.of(MembershipAuthority.FTB_TEAMS)) externalLifecycle(authority);
        versionAndAbsence(); discoveryFailures(); selectionMatrix(); runtimeFailureRecovery(); activationBoundary(); activationReviewWindow(); nativeActivation(); captureSchedule();
        System.out.println("MembershipProviderTest: " + assertions + " assertions passed");
    }
    private static void nativeOperations() {
        EssenceSavedData saved = new EssenceSavedData(); seed(saved, A, 900); seed(saved, B, 300);
        var nativeTeams = saved.nativeMembership(); PoolId pool = nativeTeams.create(A, "First team");
        check(pool.kind() == PoolId.Kind.TEAM && !pool.id().equals(A), "Native team has independent persistent identity");
        var request = nativeTeams.request(B, pool, true);
        share(saved, A, 900); share(saved, B, 300);
        check(saved.resolvePool(A).requireReady().members().equals(List.of(A)), "Pending applicant never participates");
        EssenceSavedData loaded = EssenceSavedData.load(save(saved), null);
        check(loaded.nativeMembership().requests(B).getFirst().equals(request), "Applicant acknowledgment/request survives restart");
        long receipt = loaded.reservoirs().rosterRevision(); loaded.nativeMembership().decide(A, request.token(), true);
        check(loaded.reservoirs().rosterRevision() == receipt + 1, "Approval performs one complete reconciliation");
        share(loaded, A, 600); share(loaded, B, 600);
        rejectUnchanged(loaded, () -> loaded.nativeMembership().decide(A, request.token(), true));
        seed(loaded, C, 0);
        var another = loaded.nativeMembership().request(C, pool, true);
        rejectUnchanged(loaded, () -> loaded.nativeMembership().decide(B, another.token(), true));
        check(loaded.nativeMembership().requests(B).isEmpty(), "Ordinary members do not see requests for owner approval");
        loaded.nativeMembership().decide(A, another.token(), true);
        share(loaded, A, 400); share(loaded, B, 400); share(loaded, C, 400);
        check(!loaded.resolvePool(B).requireReady().allows(B, PoolCapability.MANAGE), "Ordinary member cannot manage or approve membership");
        var beforeMoney = accounts(loaded);
        loaded.nativeMembership().transfer(A, B);
        check(accounts(loaded).equals(beforeMoney) && loaded.resolvePool(B).requireReady().pool().equals(pool), "Management transfer preserves money and pool identity");
        rejectUnchanged(loaded, () -> loaded.nativeMembership().disband(A));
        loaded.nativeMembership().leave(A); share(loaded, A, 400);
        loaded.nativeMembership().remove(B, C); share(loaded, C, 400);
        check(loaded.resolvePool(A).requireReady().isSoloFor(A) && loaded.resolvePool(C).requireReady().isSoloFor(C), "Leave and kick have identical financial detachment");
        loaded.nativeMembership().disband(B); share(loaded, B, 400);
        check(loaded.resolvePool(B).requireReady().isSoloFor(B), "Disband detaches final share with no payout");
        check(EssenceSavedData.load(save(loaded), null).resolvePool(B).requireReady().isSoloFor(B), "Disband persists");
    }
    private static void nativeFailures() {
        EssenceSavedData saved = new EssenceSavedData(); seed(saved, A, 7); seed(saved, B, 5); seed(saved, C, 2); seed(saved, D, 1);
        var service = saved.nativeMembership(); PoolId target = service.create(A, "Target");
        rejectUnchanged(saved, () -> service.request(B, target, false));
        var pending = service.request(B, target, true);
        rejectUnchanged(saved, () -> service.cancel(C, pending.token()));
        rejectUnchanged(saved, () -> service.decide(C, pending.token(), true));
        rejectUnchanged(saved, () -> service.request(B, target, true));
        service.cancel(B, pending.token()); rejectUnchanged(saved, () -> service.decide(A, pending.token(), true));
        var declined = service.request(B, target, true); var money = accounts(saved);
        service.decide(A, declined.token(), false); check(accounts(saved).equals(money), "Decline changes no balances");
        var stale = service.request(B, target, true); var accepted = service.request(C, target, true);
        service.decide(A, accepted.token(), true);
        check(service.requests(B).size() == 1 && service.requests(B).getFirst().targetRevision() == saved.resolvePool(A).requireReady().membershipRevision(), "Remaining requests follow the same team after another applicant joins");
        service.cancel(B, stale.token());
        rejectUnchanged(saved, () -> service.create(C, "Implicit merge"));
        rejectUnchanged(saved, () -> service.request(C, target, true));
        rejectUnchanged(saved, () -> service.remove(C, A));
        rejectUnchanged(saved, () -> service.transfer(C, A));
        rejectUnchanged(saved, () -> service.transfer(A, D));
        rejectUnchanged(saved, () -> service.remove(A, A));
        rejectUnchanged(saved, () -> service.leave(A));
        var waiting = service.request(B, target, true); rejectUnchanged(saved, () -> service.disband(A)); service.remove(A, C); service.disband(A);
        rejectUnchanged(saved, () -> service.decide(A, waiting.token(), true));
        check(service.requests(B).isEmpty(), "Disband cancels target requests");
        PoolId one = service.create(D, "Solo party"); rejectUnchanged(saved, () -> service.leave(D)); service.disband(D);
        check(saved.resolvePool(D).requireReady().isSoloFor(D), "Final owner explicitly disbands");
        rejectUnchanged(saved, () -> service.request(B, one, true));
    }
    private static void personalAccessRequests() {
        EssenceSavedData saved = new EssenceSavedData(); seed(saved, A, 900); seed(saved, B, 300);
        saved.getPlayerData(A).setAvailable(EssenceTypes.OFFENSE, 17);
        saved.getPlayerData(B).setAvailable(EssenceTypes.OFFENSE, 23);
        var service = saved.nativeMembership();
        rejectUnchanged(saved, () -> service.requestPersonal(B, A, false));
        var request = service.requestPersonal(B, A, true);
        check(saved.resolvePool(A).requireReady().isSoloFor(A) && saved.resolvePool(B).requireReady().isSoloFor(B), "Access requests do not force either solo player into a team");
        share(saved, A, 900); share(saved, B, 300);
        check(service.requests(A).equals(List.of(request)), "Only personal owner receives personal-machine request");
        rejectUnchanged(saved, () -> service.decide(C, request.token(), true));
        var loaded = EssenceSavedData.load(save(saved), null);
        loaded.nativeMembership().decide(A, request.token(), true, "Shared workshop");
        var team = loaded.resolvePool(A).requireReady();
        check(team.pool().kind() == PoolId.Kind.TEAM && team.members().equals(List.of(A, B))
                && team.allows(A, PoolCapability.MANAGE) && !team.allows(B, PoolCapability.MANAGE), "Explicit personal-owner approval creates a shared team with that owner");
        share(loaded, A, 600); share(loaded, B, 600);
        check(loaded.getPlayerData(A).getAvailable(EssenceTypes.OFFENSE) == 17
                && loaded.getPlayerData(B).getAvailable(EssenceTypes.OFFENSE) == 23, "Joining shares stored essence only; channeled Available remains personal");
        loaded.reservoirs().debit(A, Map.of(EssenceTypes.OFFENSE.id(), 100L), ReservoirService.NONE);
        loaded.nativeMembership().leave(B); share(loaded, A, 550); share(loaded, B, 550);
        check(loaded.getPlayerData(B).getAvailable(EssenceTypes.OFFENSE) == 23, "Departure keeps personal Available and exactly half the remaining stored balance");
        loaded.nativeMembership().disband(A);
    }
    private static void approvalNaming() {
        EssenceSavedData saved = new EssenceSavedData(); seed(saved, A, 7); seed(saved, B, 5); seed(saved, C, 3);
        var service = saved.nativeMembership(); var personal = service.requestPersonal(B, A, true);
        rejectUnchanged(saved, () -> service.decide(A, personal.token(), true));
        for (String invalid : List.of("", "   ", "x".repeat(81)))
            rejectUnchanged(saved, () -> service.decide(A, personal.token(), true, invalid));
        check(service.requests(A).equals(List.of(personal)), "Invalid/missing approval names preserve the pending request and all funds");
        service.decide(A, personal.token(), true, "  Workshop  ");
        PoolId pool = saved.resolvePool(A).requireReady().pool();
        check(service.name(pool).equals("Workshop"), "Personal approval creates the explicitly named team");
        var existing = service.request(C, pool, true);
        service.decide(A, existing.token(), true, "Must not rename");
        check(service.name(pool).equals("Workshop") && saved.resolvePool(C).requireReady().pool().equals(pool),
                "Existing-team approval preserves its name even if a name is supplied");
    }
    private static void pendingRequestLifecycle() {
        EssenceSavedData saved = new EssenceSavedData();
        for (UUID id : List.of(A, B, C, D)) seed(saved, id, 10);
        var service = saved.nativeMembership();
        var toOwner = service.requestPersonal(B, A, true);
        var ownersOutgoing = service.requestPersonal(A, C, true);
        check(service.requests(A).size() == 2, "Solo owner can have both a sent request and an incoming request");
        service.decide(A, toOwner.token(), true, "Workshop");
        check(service.requests(A).isEmpty() && service.requests(C).isEmpty(), "Creating a team through approval removes the owner's obsolete sent request");
        rejectUnchanged(saved, () -> service.decide(C, ownersOutgoing.token(), true));
        PoolId team = saved.resolvePool(A).requireReady().pool();
        var first = service.request(C, team, true);
        var second = service.request(D, team, true);
        service.decide(A, first.token(), true);
        check(service.requests(A).size() == 1 && service.requests(D).getFirst().token().equals(second.token()), "Approving one applicant preserves the other applicant's request");
        service.transfer(A, B);
        check(service.requests(A).isEmpty() && service.requests(B).size() == 1, "Pending requests follow current team ownership");
        rejectUnchanged(saved, () -> service.decide(A, second.token(), true));
        service.decide(B, second.token(), true);
        check(saved.resolvePool(D).requireReady().pool().equals(team) && service.requests(B).isEmpty(), "New owner can approve remaining applicant after roster and ownership changes");

        EssenceSavedData soloSaved = new EssenceSavedData();
        for (UUID id : List.of(A, B, C)) seed(soloSaved, id, 10);
        var soloService = soloSaved.nativeMembership();
        var personal = soloService.requestPersonal(B, A, true);
        soloService.create(A, "Changed plans");
        check(soloService.requests(B).isEmpty(), "Personal request is removed when its destination independently creates a team");
        rejectUnchanged(soloSaved, () -> soloService.decide(A, personal.token(), true));
        soloService.requestPersonal(B, C, true);
        soloService.create(B, "My workshop");
        check(soloService.requests(C).isEmpty(), "Creating one's own team clears sent requests without enrolling the other solo owner");
        check(soloSaved.resolvePool(C).requireReady().isSoloFor(C), "Obsolete requests never create implicit memberships");
    }
    private static void machineOwnershipReceipts() {
        EssenceSavedData saved = new EssenceSavedData(); seed(saved, A, 7); seed(saved, B, 5);
        long placed = saved.reservoirs().rosterRevision();
        var service = saved.nativeMembership(); PoolId first = service.create(A, "First workshop");
        var join = saved.reservoirs().machineJoin(A, placed);
        check(join != null && join.pool().equals(first) && join.revision() > placed, "Creating a team records the ownership transition for unloaded personal machines");
        var request = service.request(B, first, true); service.decide(A, request.token(), true);
        long joined = saved.reservoirs().machineJoin(B, 0).revision();
        service.leave(B); long newPlacement = saved.reservoirs().rosterRevision();
        PoolId second = service.create(B, "Second workshop");
        check(saved.reservoirs().machineJoin(B, 0).pool().equals(first)
                && saved.reservoirs().machineJoin(B, newPlacement).pool().equals(second), "First later join retains old team infrastructure while new solo placements follow their own later join");
        service.disband(A); long dissolved = saved.reservoirs().dissolutionRevision(first);
        check(dissolved > joined && saved.reservoirs().dissolutionRecipient(first).equals(A), "Final disband records its owner and ordered transition");
        var secondRequest = service.request(A, second, true); service.decide(B, secondRequest.token(), true);
        var loaded = EssenceSavedData.load(save(saved), null);
        check(loaded.reservoirs().machineJoin(A, placed).equals(join)
                && loaded.reservoirs().machineJoin(A, dissolved).pool().equals(second)
                && loaded.reservoirs().dissolutionRevision(first) == dissolved, "Unloaded ownership chain persists through save/restart without touching balance accounting");
        var before = save(loaded); MembershipPublication.publish(loaded, READY, loaded.reservoirs().settledTeams().stream().map(MembershipRoster::from).toList());
        check(save(loaded).equals(before), "Duplicate membership publications create no extra machine transitions");
    }
    private static MembershipRoster roster(MembershipAuthority authority, List<UUID> members, Set<UUID> managers) {
        return MembershipRoster.party(authority, TEAM, members, managers);
    }
    private static void providerContract(MembershipAuthority authority) { providerContract(authority, (members, managers) -> roster(authority, members, managers)); }
    public static void providerContract(MembershipAuthority authority, java.util.function.BiFunction<List<UUID>, Set<UUID>, MembershipRoster> adapter) {
        EssenceSavedData saved = new EssenceSavedData(); saved.establishMembershipAuthority(authority);
        seed(saved, A, 900); seed(saved, B, 300);
        MembershipRoster initial = adapter.apply(List.of(B, A, B), Set.of(A));
        check(MembershipPublication.publish(saved, READY, List.of(initial)), authority + " complete offline roster publication");
        share(saved, A, 600); share(saved, B, 600);
        check(saved.resolvePool(A).requireReady().members().equals(List.of(A, B)), "Shared normalization produces full unique sorted roster");
        check(!saved.resolvePool(A).requireReady().allows(C, PoolCapability.SPEND), "Unrelated/invited/allied UUID has no access");
        check(saved.resolvePool(B).requireReady().allows(B, PoolCapability.APPROVE_REQUEST), "Common member permissions");
        check(!saved.resolvePool(B).requireReady().allows(B, PoolCapability.MANAGE), "Common management restriction");
        var snapshot = save(saved); long receipt = saved.reservoirs().rosterRevision();
        check(!MembershipPublication.publish(saved, READY, List.of(initial)) && save(saved).equals(snapshot), "Duplicate events/name-only observations are inert");
        check(saved.reservoirs().channel(B, Map.of(EssenceTypes.OFFENSE.id(), 200L)), "Same common economy handles provider spending");
        share(saved, A, 500); share(saved, B, 500);
        check(saved.getPlayerData(B).getAvailable(EssenceTypes.OFFENSE.id()) == 200, "Only channel recipient gets Available");
        var money = accounts(saved);
        MembershipRoster roles = adapter.apply(List.of(A, B), Set.of(B));
        MembershipPublication.publish(saved, READY, List.of(roles));
        check(accounts(saved).equals(money) && saved.resolvePool(B).requireReady().pool().equals(initial.pool()), "Leadership/role change keeps balances and identity");
        check(saved.resolvePool(B).requireReady().membershipRevision() == 1 && saved.resolvePool(B).requireReady().permissionRevision() == 2,
                "Permission revision changes without membership redistribution");
        rejectUnchanged(saved, () -> MembershipPublication.publish(saved, READY, List.of(roles, roles)));
        MembershipPublication.publish(saved, READY, List.of(adapter.apply(List.of(B, C), Set.of(B))));
        share(saved, A, 500); share(saved, B, 250); share(saved, C, 250);
        check(saved.getPlayerData(A).getAvailable(EssenceTypes.OFFENSE.id()) == 0, "Detachment never pays Available");
        EssenceSavedData restart = EssenceSavedData.load(save(saved), null);
        if (authority != MembershipAuthority.NATIVE) check(restart.resolvePool(B).snapshot().isEmpty(), "External restart pauses until confirmed");
        long persistedReceipt = restart.reservoirs().rosterRevision();
        MembershipPublication.publish(restart, READY, List.of(adapter.apply(List.of(B, C), Set.of(B))));
        check(restart.reservoirs().rosterRevision() == persistedReceipt, "Restart confirmation does not redistribute");
        check(restart.reservoirs().rosterRevision() > receipt, "Changed batches have durable receipts");
        MembershipPublication.publish(restart, READY, List.of());
        share(restart, A, 500); share(restart, B, 250); share(restart, C, 250);
        var solo = restart.resolvePool(B).requireReady();
        check(solo.pool().authority() == authority && solo.isSoloFor(B), "Confirmed absence/individual team is solo under selected authority");
        check(MachinePoolAccess.allowsPersonal(solo, B, B, PoolCapability.SPEND), "Common solo capability works without provider-specific machine policy");
        check(restart.soloReservoirAccount(B) == restart.getPlayerData(B), "Solo helper supports every ready authority");
    }
    private static void externalLifecycle(MembershipAuthority authority) {
        EssenceSavedData saved = new EssenceSavedData(); saved.establishMembershipAuthority(authority);
        seed(saved, A, 900); seed(saved, B, 300);
        var settle = new MembershipSettlement(saved); var initial = roster(authority, List.of(A, B), Set.of(A));
        settle.invalidate("Startup"); var money = accounts(saved);
        check(saved.resolvePool(A).snapshot().isEmpty() && saved.resolvePool(D).snapshot().isEmpty(), "Unready is never prior solo/no-team");
        rejectUnchanged(saved, () -> saved.nativeMembership().create(D, "Denied"));
        settle.observe(new MembershipProvider.Observation(READY, List.of(initial)));
        check(accounts(saved).equals(money) && saved.resolvePool(A).snapshot().isEmpty(), "First observation stays paused and financially inert");
        settle.observe(new MembershipProvider.Observation(READY, List.of(initial))); share(saved, A, 600); share(saved, B, 600);
        rejectUnchanged(saved, () -> saved.nativeMembership().create(D, "Denied ready"));
        rejectUnchanged(saved, () -> saved.nativeMembership().request(D, initial.pool(), true));
        rejectUnchanged(saved, () -> saved.nativeMembership().leave(A));
        rejectUnchanged(saved, () -> saved.nativeMembership().remove(A, B));
        rejectUnchanged(saved, () -> saved.nativeMembership().transfer(A, B));
        rejectUnchanged(saved, () -> saved.nativeMembership().disband(A));
        rejectUnchanged(saved, () -> saved.nativeMembership().cancel(A, UUID.randomUUID()));
        rejectUnchanged(saved, () -> saved.nativeMembership().decide(A, UUID.randomUUID(), true));
        settle.invalidate("Duplicate event"); settle.invalidate("Duplicate event"); money = accounts(saved);
        settle.observe(new MembershipProvider.Observation(READY, List.of(initial)));
        settle.observe(new MembershipProvider.Observation(READY, List.of(initial)));
        check(accounts(saved).equals(money), "Duplicate callbacks resume without redistribution");
        settle.invalidate("Disband callback sequence");
        settle.observe(new MembershipProvider.Observation(READY, List.of(roster(authority, List.of(A), Set.of(A)))));
        settle.observe(new MembershipProvider.Observation(READY, List.of()));
        check(accounts(saved).equals(money) && saved.resolvePool(A).snapshot().isEmpty(), "Transient one-member/disband states never publish money");
        settle.observe(new MembershipProvider.Observation(READY, List.of()));
        share(saved, A, 600); share(saved, B, 600);
        check(saved.resolvePool(A).requireReady().isSoloFor(A), "Confirmed disband detaches original shares once");
        check(settle.confirmedDeletions().equals(Set.of(initial.pool())), "Confirmed deletion is distinct from provider unavailability/ready solo");
        var empty = save(saved);
        settle.observe(new MembershipProvider.Observation(new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "fixture", "Manager destroyed"), List.of()));
        check(save(saved).equals(empty) && saved.resolvePool(A).snapshot().isEmpty(), "Provider disappearance preserves receipts/accounts and pauses");
        rejectUnchanged(saved, () -> saved.establishMembershipAuthority(MembershipAuthority.NATIVE));
        settle.observe(new MembershipProvider.Observation(READY, List.of())); settle.observe(new MembershipProvider.Observation(READY, List.of()));
        check(saved.resolvePool(A).requireReady().isSoloFor(A), "Provider recovery confirms current receipt");
        var conflicting = roster(authority, List.of(A, C), Set.of(A));
        rejectUnchanged(saved, () -> settle.observe(new MembershipProvider.Observation(READY, List.of(initial, conflicting))));
        rejectUnchanged(saved, () -> MembershipRoster.party(authority, TEAM, List.of(A), Set.of(B)));
        MembershipPublication.publish(saved, READY, List.of(initial));
        var durable = save(saved);
        for (var unavailable : List.of(ProviderReadiness.Status.ABSENT, ProviderReadiness.Status.UNSUPPORTED,
                ProviderReadiness.Status.NOT_READY, ProviderReadiness.Status.FAILED)) {
            settle.observe(new MembershipProvider.Observation(new ProviderReadiness(unavailable, "fixture", "Authority unusable"), List.of()));
            check(save(saved).equals(durable) && saved.membershipAuthority() == authority && saved.resolvePool(B).snapshot().isEmpty(),
                    "Missing/unsupported/unavailable selected authority preserves all durable state and denies operations");
            rejectUnchanged(saved, () -> saved.reservoirs().credit(B, Map.of(EssenceTypes.OFFENSE.id(), BigInteger.ONE), ReservoirService.NONE));
        }
    }
    private static void nativeActivation() {
        EssenceSavedData saved = new EssenceSavedData(); seed(saved, A, 900); seed(saved, B, 300);
        PoolId nativeTeam = saved.nativeMembership().create(A, "Original");
        var pending = saved.nativeMembership().request(B, nativeTeam, true);
        CompoundTag metadata = save(saved).getCompound("native_membership").copy();
        var external = new MembershipProvider.Observation(READY, List.of(roster(MembershipAuthority.FTB_TEAMS, List.of(A, B), Set.of(A))));
        var toExternal = MembershipActivation.prepare(saved, MembershipAuthority.FTB_TEAMS, external);
        toExternal.activate(saved, toExternal.token(), external);
        var soloNative = new MembershipProvider.Observation(READY, List.of());
        var toNative = MembershipActivation.prepare(saved, MembershipAuthority.NATIVE, soloNative);
        toNative.activate(saved, toNative.token(), soloNative);
        share(saved, A, 600); share(saved, B, 600);
        check(saved.resolvePool(A).state() == MembershipResolution.State.READY_SOLO && saved.reservoirs().settledTeams().isEmpty(), "Native activation starts with solos and never revives old teams");
        check(save(saved).getCompound("native_membership").equals(metadata), "Activation preserves native names/requests without auto deletion");
        rejectUnchanged(saved, () -> saved.nativeMembership().decide(A, pending.token(), true));
        saved.nativeMembership().cancel(B, pending.token());
        check(saved.nativeMembership().requests(B).isEmpty(), "Obsolete native request remains explicitly cancelable");
        saved.nativeMembership().create(A, "Fresh native team");
        check(saved.resolvePool(A).state() == MembershipResolution.State.READY_TEAM, "Native mutations resume only after explicit activation");
    }
    private static void versionAndAbsence() {
        for (var entry : MembershipProviders.entries()) for (String loader : List.of("fabric", "neoforge", "future-loader")) {
            for (String version : List.of("26.1.2.999", "2101.99.99", "unknown", "future-api-preview+local")) {
                var detected = MembershipProviders.support(entry, Map.of(entry.modId(), version), loader);
                check(detected.status() == ProviderReadiness.Status.NOT_READY && detected.version().equals(version),
                        "Installed versions are diagnostics, never an allowlist: the actual adapter must be attempted");
                var changedDependency = MembershipProviders.support(entry,
                        Map.of(entry.modId(), version, "ftblibrary", "newer-library-build"), loader);
                check(changedDependency.status() == ProviderReadiness.Status.NOT_READY,
                        "Library updates do not require an Essence Ascendance version-list update");
            }
            check(MembershipProviders.support(entry, Map.of(), loader).status() == ProviderReadiness.Status.ABSENT,
                    "A genuinely absent provider remains distinct from a failed API attempt");
        }
        // Loading unconditional public contracts in a fixture runtime with no FTB jars must succeed.
        try { Class.forName("com.mistaboom.essence_ascendance.team.MembershipRuntime"); check(true, "Common runtime absent-safe class loading"); }
        catch (ClassNotFoundException failure) { throw new AssertionError(failure); }
    }
    private static void discoveryFailures() {
        Map<String, String> mods = Map.of("ftbteams", "future-api-preview+local");
        int[] created = {0}, hooks = {0};
        var healthy = fixtureProvider(() -> hooks[0]++);
        var available = MembershipProviders.discover(mods, "future-loader", (server, authority) -> { }, (entry, version) -> {
            created[0]++;
            check(version.equals(mods.get(entry.modId())), "The adapter receives the installed version for diagnostics");
            return healthy;
        });
        var ftb = available.get(MembershipAuthority.FTB_TEAMS);
        check(created[0] == 1 && hooks[0] == 1 && ftb.provider() == healthy
                        && ftb.readiness().status() == ProviderReadiness.Status.NOT_READY,
                "A future version reaches real adapter construction and event registration without guessing readiness");
        var absent = MembershipProviders.discover(Map.of(), "neoforge", (server, authority) -> { }, (entry, version) -> {
            throw new AssertionError("Absent optional API must never be linked");
        });
        check(absent.get(MembershipAuthority.FTB_TEAMS).provider() == null
                        && absent.get(MembershipAuthority.FTB_TEAMS).readiness().status() == ProviderReadiness.Status.ABSENT,
                "Absent optional mods do not load an adapter");
        List<java.util.function.Supplier<MembershipProvider>> failures = List.of(
                () -> { throw new NoClassDefFoundError("fixture missing API class"); },
                () -> { throw new NoSuchMethodError("fixture changed API signature"); },
                () -> { throw new IllegalStateException("fixture API initialization failed"); });
        for (var factory : failures) {
            var failed = MembershipProviders.discover(mods, "neoforge", (server, authority) -> { }, (entry, version) -> factory.get());
            var result = failed.get(MembershipAuthority.FTB_TEAMS);
            check(result.provider() == null && result.readiness().status() == ProviderReadiness.Status.FAILED
                            && result.readiness().detail().contains("fixture")
                            && result.readiness().version().equals(mods.get("ftbteams")),
                    "An actual missing/changed/broken API is isolated and diagnosed instead of aborting startup");
            check(failed.get(MembershipAuthority.NATIVE).readiness().status() == ProviderReadiness.Status.AVAILABLE,
                    "An optional API failure does not disable native membership discovery");
        }
        var polling = fixtureProvider(() -> { throw new NoSuchMethodError("fixture event API changed"); });
        var eventsFailed = MembershipProviders.discover(mods, "neoforge", (server, authority) -> { }, (entry, version) -> polling)
                .get(MembershipAuthority.FTB_TEAMS);
        check(eventsFailed.provider() == polling && eventsFailed.readiness().status() == ProviderReadiness.Status.NOT_READY
                        && eventsFailed.readiness().detail().toLowerCase(Locale.ROOT).contains("poll"),
                "A broken optional event hook retains authoritative capture and live permission checks through polling");
        check(eventsFailed.provider().capture(null).readiness().status() == ProviderReadiness.Status.AVAILABLE,
                "Event-hook failure cannot poison an otherwise working roster API");
        boolean fatalEscaped = false;
        try { MembershipProviders.discover(mods, "neoforge", (server, authority) -> { }, (entry, version) -> {
            throw new OutOfMemoryError("fixture fatal VM failure");
        }); } catch (OutOfMemoryError expected) { fatalEscaped = true; }
        check(fatalEscaped, "Fatal VM failures are not mislabeled as optional API incompatibility");
    }
    private static MembershipProvider fixtureProvider(Runnable install) {
        return new MembershipProvider() {
            @Override public Observation capture(MinecraftServer server) { return new Observation(READY, List.of()); }
            @Override public boolean isCurrent(MinecraftServer server, UUID member, MembershipSnapshot settled) { return settled == null; }
            @Override public void installInvalidation(java.util.function.Consumer<MinecraftServer> invalidate) { install.run(); }
        };
    }
    private static void selectionMatrix() {
        ProviderReadiness absent = new ProviderReadiness(ProviderReadiness.Status.ABSENT, "none", "Absent");
        ProviderReadiness unsupported = new ProviderReadiness(ProviderReadiness.Status.UNSUPPORTED, "x", "Legacy unavailable state fixture");
        ProviderReadiness waiting = new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "x", "Starting");
        for (var ftb : List.of(absent, unsupported, waiting, READY)) {
            var statuses = Map.of(MembershipAuthority.NATIVE, READY, MembershipAuthority.FTB_TEAMS, ftb);
            var selected = MembershipAuthority.select(MembershipAuthority.Selection.AUTO, null, statuses);
            MembershipAuthority expected = ftb == absent ? MembershipAuthority.NATIVE : MembershipAuthority.FTB_TEAMS;
            check(selected.authority() == expected, "Deterministic new-world AUTO matrix");
            if (ftb == unsupported) check(!selected.usable(), "Selection reports unavailable evidence; preserved-state fallback belongs to the runtime");
            for (MembershipAuthority authority : MembershipAuthority.values()) {
                check(MembershipAuthority.select(MembershipAuthority.Selection.AUTO, authority, statuses).authority() == authority, "Persisted authority survives install/removal/replacement");
                check(MembershipAuthority.select(MembershipAuthority.selection(authority.id()), null, statuses).authority() == authority, "Stable explicit ID selection");
            }
        }
        for (ProviderReadiness readiness : List.of(absent, unsupported, waiting)) {
            var resolved = new MembershipResolution(readiness, Optional.empty());
            check(resolved.state() == (readiness == absent ? MembershipResolution.State.ABSENT : readiness == unsupported ? MembershipResolution.State.UNSUPPORTED : MembershipResolution.State.UNAVAILABLE), "Unavailable state distinctions");
        }
    }
    private static void activationBoundary() {
        for (MembershipAuthority from : MembershipAuthority.values()) for (MembershipAuthority target : MembershipAuthority.values()) {
            if (from == target) continue;
            EssenceSavedData saved = new EssenceSavedData(); saved.establishMembershipAuthority(from); seed(saved, A, 900); seed(saved, B, 300); seed(saved, C, 100);
            MembershipPublication.publish(saved, READY, List.of(roster(from, List.of(A, B), Set.of(A))));
            var before = save(saved); var previousSolo = saved.resolvePool(C).requireReady().pool(); before = save(saved);
            var evidence = new MembershipProvider.Observation(READY, List.of(roster(target, List.of(B, C), Set.of(B))));
            var receipt = MembershipActivation.prepare(saved, target, evidence);
            check(save(saved).equals(before), "Activation preview changes no durable data");
            rejectUnchanged(saved, () -> receipt.activate(saved, UUID.randomUUID(), evidence));
            rejectUnchanged(saved, () -> receipt.activate(saved, receipt.token(), new MembershipProvider.Observation(new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "x", "Unavailable"), List.of())));
            rejectUnchanged(saved, () -> receipt.activate(saved, receipt.token(), new MembershipProvider.Observation(READY, List.of())));
            receipt.activate(saved, receipt.token(), evidence);
            check(saved.membershipAuthority() == target, "Only explicit receipt activates authority"); share(saved, A, 600); share(saved, B, 350); share(saved, C, 350);
            check(saved.resolvePool(A).requireReady().pool().authority() == target && !saved.resolvePool(A).requireReady().pool().equals(previousSolo), "Solo ownership is explicitly reidentified");
            check(EssenceSavedData.load(save(saved), null).membershipAuthority() == target, "Activated authority persists through restart");
            rejectUnchanged(saved, () -> receipt.activate(saved, receipt.token(), evidence));
            var back = new MembershipProvider.Observation(READY, List.of(roster(from, List.of(A, B), Set.of(A))));
            var stale = MembershipActivation.prepare(saved, from, back);
            saved.reservoirs().credit(A, Map.of(EssenceTypes.OFFENSE.id(), BigInteger.ONE), ReservoirService.NONE);
            rejectUnchanged(saved, () -> stale.activate(saved, stale.token(), back));
        }
    }
    private static void captureSchedule() {
        var schedule = new MembershipCaptureSchedule(); check(schedule.due(), "Startup capture immediately due");
        schedule.captured(true, true); check(schedule.due(), "Second settling observation next tick");
        schedule.captured(true, false);
        for (int tick = 1; tick < MembershipCaptureSchedule.RECOVERY_TICKS; tick++) check(!schedule.due(), "No full capture between bounded checks");
        check(schedule.due(), "Bounded fallback detects eventless changes"); schedule.captured(false, true);
        check(!schedule.due(), "Unavailable provider is not queried every tick"); schedule.invalidate(); check(schedule.due(), "Event invalidation captures next tick");
    }
    @SuppressWarnings("unchecked")
    private static void runtimeFailureRecovery() {
        try {
            var providersField = MembershipRuntime.class.getDeclaredField("providers"); providersField.setAccessible(true);
            var sessionsField = MembershipRuntime.class.getDeclaredField("SESSIONS"); sessionsField.setAccessible(true);
            var connect = MembershipRuntime.class.getDeclaredMethod("connect", MinecraftServer.class, EssenceSavedData.class, boolean.class);
            var tick = MembershipRuntime.class.getDeclaredMethod("tick", MinecraftServer.class);
            var decision = MembershipRuntime.class.getDeclaredMethod("decision", EssenceSavedData.class);
            connect.setAccessible(true); tick.setAccessible(true); decision.setAccessible(true);
            Map<Object, Object> sessions = (Map<Object, Object>) sessionsField.get(null);
            Object previousProviders = providersField.get(null), previousSession = sessions.get(null);
            boolean hadSession = sessions.containsKey(null);
            String previousSelection = System.getProperty("essence.membershipAuthority");
            try {
                System.setProperty("essence.membershipAuthority", "auto");
                for (String mode : List.of("initialization", "removed", "capture-linkage", "capture-status", "waiting", "probe-error", "probe-change")) {
                    var ready = new ProviderReadiness(ProviderReadiness.Status.AVAILABLE, "future-api", "Complete fixture roster");
                    var waiting = new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "future-api", "Manager still starting");
                    var failed = new ProviderReadiness(ProviderReadiness.Status.FAILED, "future-api", "Actual fixture API failure");
                    var party = MembershipRoster.party(MembershipAuthority.FTB_TEAMS, TEAM, List.of(A, B), Set.of(A), A);
                    var evidence = new MembershipProvider.Observation(ready, List.of(party));
                    boolean[] probeChanged = {false};
                    MembershipProvider fake = new MembershipProvider() {
                        public Observation capture(MinecraftServer server) {
                            if (mode.equals("capture-linkage")) throw new NoSuchMethodError("Fixture roster API removed");
                            if (mode.equals("capture-status")) return new Observation(failed, List.of());
                            if (mode.equals("waiting")) return new Observation(waiting, List.of());
                            return evidence;
                        }
                        public boolean isCurrent(MinecraftServer server, UUID member, MembershipSnapshot settled) {
                            if (probeChanged[0] && mode.equals("probe-error")) throw new NoClassDefFoundError("Fixture live API removed");
                            return !probeChanged[0];
                        }
                        public void installInvalidation(java.util.function.Consumer<MinecraftServer> invalidate) { }
                    };
                    boolean disconnected = mode.equals("initialization") || mode.equals("removed");
                    var discovery = mode.equals("removed")
                            ? new ProviderReadiness(ProviderReadiness.Status.ABSENT, "absent", "Fixture mod removed")
                            : disconnected ? failed : waiting;
                    providersField.set(null, Map.of(
                            MembershipAuthority.NATIVE, new MembershipProviders.Installed(READY, null),
                            MembershipAuthority.FTB_TEAMS, new MembershipProviders.Installed(discovery, disconnected ? null : fake)));
                    var saved = new EssenceSavedData(); saved.establishMembershipAuthority(MembershipAuthority.FTB_TEAMS);
                    seed(saved, A, 120); seed(saved, B, 40);
                    MembershipPublication.publish(saved, ready, List.of(party));
                    var money = accounts(saved);
                    connect.invoke(null, null, saved, false);
                    var started = sessions.get(null).getClass().getDeclaredField("started"); started.setAccessible(true);
                    started.setBoolean(sessions.get(null), true);
                    if (mode.startsWith("probe-")) {
                        tick.invoke(null, (Object) null); tick.invoke(null, (Object) null);
                        check(saved.resolvePool(A).requireReady().pool().equals(party.pool()), "A complete working API settles before live-probe failure testing");
                        probeChanged[0] = true;
                        check(saved.resolvePool(A).snapshot().isEmpty() && saved.membershipAuthority() == MembershipAuthority.FTB_TEAMS,
                                "A failed or stale live probe denies the current operation without switching authority inside it");
                        check(accounts(saved).equals(money), "Denied live authorization changes no player money");
                        probeChanged[0] = false;
                    }
                    tick.invoke(null, (Object) null);
                    if (mode.equals("waiting") || mode.equals("probe-change")) {
                        check(saved.membershipAuthority() == MembershipAuthority.FTB_TEAMS && !saved.hasMembershipFallback(),
                                "Temporary startup and ordinary roster changes never trigger an automatic authority switch");
                        if (mode.equals("probe-change")) {
                            tick.invoke(null, (Object) null);
                            check(saved.resolvePool(A).requireReady().pool().equals(party.pool()), "A changed live probe resumes through two matching captures");
                        } else {
                            for (int count = 0; count < 210; count++) tick.invoke(null, (Object) null);
                            check(saved.membershipAuthority() == MembershipAuthority.FTB_TEAMS && saved.resolvePool(A).snapshot().isEmpty(),
                                    "Repeated not-ready observations keep saved membership protected while the manager initializes");
                        }
                    } else {
                        check(saved.membershipAuthority() == MembershipAuthority.NATIVE && saved.hasMembershipFallback(),
                                mode + " automatically establishes the built-in authority");
                        var resolved = saved.resolvePool(A).requireReady();
                        check(resolved.pool().equals(saved.remapFallbackPool(party.pool())) && resolved.members().equals(List.of(A, B))
                                        && !resolved.allows(C, PoolCapability.USE),
                                mode + " preserves the last confirmed team and excludes outsiders");
                        check(MembershipRuntime.diagnosticSnapshot(null).notice().contains("using Essence Ascendance teams"),
                                "Automatic fallback is visible in membership status");
                        var loaded = EssenceSavedData.load(save(saved), null);
                        System.setProperty("essence.membershipAuthority", "ftb_teams");
                        var next = (MembershipAuthority.Decision) decision.invoke(null, loaded);
                        check(next.authority() == MembershipAuthority.NATIVE && !next.authorityChangeRequired(),
                                "Persisted fallback survives restart even when a stale startup property requests the failed provider");
                        System.setProperty("essence.membershipAuthority", "auto");
                    }
                    check(accounts(saved).equals(money), mode + " preserves exact recorded player balances");
                }
            } finally {
                providersField.set(null, previousProviders);
                if (hadSession) sessions.put(null, previousSession); else sessions.remove(null);
                if (previousSelection == null) System.clearProperty("essence.membershipAuthority");
                else System.setProperty("essence.membershipAuthority", previousSelection);
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to exercise automatic membership fallback lifecycle", failure);
        }
    }
    @SuppressWarnings("unchecked")
    private static void activationReviewWindow() {
        // Run the real prepare/tick/status/activate lifecycle without creating a server or world.
        // Reflection is confined to fixture setup; all lifecycle operations use production methods.
        try {
            var providersField = MembershipRuntime.class.getDeclaredField("providers"); providersField.setAccessible(true);
            var sessionsField = MembershipRuntime.class.getDeclaredField("SESSIONS"); sessionsField.setAccessible(true);
            var connect = MembershipRuntime.class.getDeclaredMethod("connect", MinecraftServer.class, EssenceSavedData.class, boolean.class);
            var tick = MembershipRuntime.class.getDeclaredMethod("tick", MinecraftServer.class);
            connect.setAccessible(true); tick.setAccessible(true);
            Map<Object, Object> sessions = (Map<Object, Object>) sessionsField.get(null);
            Object previousProviders = providersField.get(null), previousSession = sessions.get(null);
            boolean hadSession = sessions.containsKey(null);
            String previousSelection = System.getProperty("essence.membershipAuthority");
            var evidence = new MembershipProvider.Observation(READY,
                    List.of(roster(MembershipAuthority.FTB_TEAMS, List.of(A, B), Set.of(A))));
            MembershipProvider fake = new MembershipProvider() {
                public Observation capture(MinecraftServer server) { return evidence; }
                public boolean isCurrent(MinecraftServer server, UUID member, MembershipSnapshot settled) { return true; }
                public void installInvalidation(java.util.function.Consumer<MinecraftServer> invalidate) { }
            };
            try {
                System.setProperty("essence.membershipAuthority", "auto");
                providersField.set(null, Map.of(
                        MembershipAuthority.NATIVE, new MembershipProviders.Installed(READY, null),
                        MembershipAuthority.FTB_TEAMS, new MembershipProviders.Installed(
                                new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "fixture", "Awaiting capture"), fake)));
                for (boolean expire : List.of(false, true)) {
                    EssenceSavedData saved = new EssenceSavedData(); seed(saved, A, 900); seed(saved, B, 300);
                    saved.establishMembershipAuthority(MembershipAuthority.NATIVE);
                    connect.invoke(null, null, saved, false);
                    var started = sessions.get(null).getClass().getDeclaredField("started"); started.setAccessible(true);
                    started.setBoolean(sessions.get(null), true);
                    CompoundTag before = save(saved);
                    MembershipRuntime.prepare(null, "ftb_teams");
                    check(MembershipRuntime.diagnosticSnapshot(null).activationToken() == null,
                            "Preparing waits for complete observations before issuing a token");
                    tick.invoke(null, (Object) null); tick.invoke(null, (Object) null);
                    var settled = MembershipRuntime.diagnosticSnapshot(null);
                    UUID token = settled.activationToken();
                    check(token != null && settled.target() == MembershipAuthority.FTB_TEAMS,
                            "Two matching runtime captures issue a reviewable target token");
                    check(settled.affectedTeams() == 1 && settled.affectedPlayers() == 2,
                            "Runtime status reports actual captured parties and members");
                    check(MembershipAuthority.ready(settled.providers().get(MembershipAuthority.FTB_TEAMS)),
                            "Runtime status replaces discovery readiness with observed target readiness");
                    int elapsed = expire ? 5999 : 80 * 20;
                    for (int count = 2; count < elapsed; count++) tick.invoke(null, (Object) null);
                    var reviewed = MembershipRuntime.diagnosticSnapshot(null);
                    check(token.equals(reviewed.activationToken()) && reviewed.activationTicksRemaining() == 6000 - elapsed,
                            "Runtime status preserves the token and counts down the actual review window");
                    check(MembershipRuntime.diagnosticSnapshot(null).equals(reviewed),
                            "Reading status neither changes the receipt nor extends the deadline");
                    check(save(saved).equals(before), "Reviewing the target changes no durable membership or funds");
                    if (!expire) {
                        MembershipRuntime.activate(null, token);
                        check(saved.membershipAuthority() == MembershipAuthority.FTB_TEAMS,
                                "Runtime activation succeeds after the reported 80-second review delay");
                        share(saved, A, 600); share(saved, B, 600);
                    } else {
                        tick.invoke(null, (Object) null);
                        var expired = MembershipRuntime.diagnosticSnapshot(null);
                        check(expired.target() == null && expired.activationToken() == null && expired.activationTicksRemaining() == 0,
                                "Runtime removes the pending activation at exactly 6,000 ticks");
                        check(saved.membershipAuthority() == MembershipAuthority.NATIVE && save(saved).equals(before),
                                "Timeout retains the established authority and all durable state and funds");
                        check(expired.notice() != null && expired.notice().contains("expired")
                                        && expired.notice().contains("/essence admin membership prepare ftb_teams"),
                                "Expired status explains the timeout and exact recovery command");
                        String failure = null;
                        try { MembershipRuntime.activate(null, token); }
                        catch (IllegalStateException expected) { failure = expected.getMessage(); }
                        check(expired.notice().equals(failure) && save(saved).equals(before),
                                "Expired activation rejects the old token with an explanation and no durable changes");
                        MembershipRuntime.prepare(null, "ftb_teams");
                        var restarted = MembershipRuntime.diagnosticSnapshot(null);
                        check(restarted.notice() == null && restarted.activationTicksRemaining() == 6000
                                        && restarted.activationToken() == null,
                                "Preparing again clears the expiration notice and starts a fresh review");
                        tick.invoke(null, (Object) null); tick.invoke(null, (Object) null);
                        check(!token.equals(MembershipRuntime.diagnosticSnapshot(null).activationToken()),
                                "Preparing again issues a new token after recapturing the target");
                    }
                }
            } finally {
                providersField.set(null, previousProviders);
                if (hadSession) sessions.put(null, previousSession); else sessions.remove(null);
                if (previousSelection == null) System.clearProperty("essence.membershipAuthority");
                else System.setProperty("essence.membershipAuthority", previousSelection);
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to exercise the no-world membership lifecycle", failure);
        }
    }
    private static void seed(EssenceSavedData saved, UUID member, long whole) {
        saved.reservoirs().credit(member, Map.of(EssenceTypes.OFFENSE.id(), BigInteger.valueOf(whole).multiply(ReservoirService.SCALE)), ReservoirService.NONE);
    }
    private static void share(EssenceSavedData saved, UUID member, long expected) {
        check(saved.getPlayerData(member).getCrucibleStored(EssenceTypes.OFFENSE.id()) == expected, "Expected recorded share " + expected);
    }
    private static CompoundTag save(EssenceSavedData saved) { return saved.save(new CompoundTag(), null); }
    private static CompoundTag accounts(EssenceSavedData saved) { return save(saved).getCompound("players").copy(); }
    private static void rejectUnchanged(EssenceSavedData saved, Runnable action) {
        CompoundTag before = save(saved); boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException | ArithmeticException expected) { rejected = true; }
        check(rejected, "Invalid operation must reject"); check(save(saved).equals(before), "Rejected operation changes no durable state/balance");
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}

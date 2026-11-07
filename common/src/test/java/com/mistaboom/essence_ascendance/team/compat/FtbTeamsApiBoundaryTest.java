package com.mistaboom.essence_ascendance.team.compat;

import com.mistaboom.essence_ascendance.team.*;
import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import dev.ftb.mods.ftbteams.api.*;
//? if >=26.1.2
/*import dev.ftb.mods.ftbteams.api.event.TeamManagerEvent;*/
import java.lang.reflect.Proxy;
import java.util.*;

/** Fixtures against the real target public interfaces. No private FTB fields or persisted data. */
public final class FtbTeamsApiBoundaryTest {
    private static int assertions;
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), D = new UUID(0, 4);
    public static void run() {
        Map<UUID, TeamRank> ranks = new HashMap<>(Map.of(A, TeamRank.OWNER, B, TeamRank.MEMBER, C, TeamRank.INVITED, D, TeamRank.ALLY));
        UUID id = new UUID(1, 1);
        Team party = team(id, A, ranks, "party");
        Team individual = team(C, C, new HashMap<>(Map.of(C, TeamRank.OWNER)), "player");
        Team administrative = team(new UUID(2, 2), D, new HashMap<>(Map.of(D, TeamRank.OWNER)), "server");
        eventInvalidation(party, individual, administrative);
        soloPlacementAndJoin();
        List<Team> all = new ArrayList<>(List.of(party, individual, administrative));
        Map<UUID, Team> effective = new HashMap<>(Map.of(A, party, B, party, C, individual));
        TeamManager manager = (TeamManager) Proxy.newProxyInstance(TeamManager.class.getClassLoader(), new Class<?>[]{TeamManager.class},
                (p, method, args) -> switch (method.getName()) {
                    case "getTeams" -> List.copyOf(all);
                    case "getTeamByID" -> all.stream().filter(team -> team.getId().equals(args[0])).findFirst();
                    case "getTeamForPlayerID" -> Optional.ofNullable(effective.get((UUID) args[0]));
                    default -> throw new AssertionError("Unexpected API call " + method);
                });
        var observation = FtbTeamsMembershipProvider.captureManager(manager);
        check(FtbTeamsMembershipProvider.teamName(manager, id).orElseThrow().equals("The Builders"), "Actual team display name is used instead of stable UUID");
        check(FtbTeamsMembershipProvider.teamName(manager, C).isEmpty(), "Individual teams are not presented as party names");
        check(FtbTeamsMembershipProvider.teamName((TeamManager) null, id).isEmpty(), "Unavailable names do not invent a team identity");
        check(observation.readiness().status() == ProviderReadiness.Status.AVAILABLE && observation.teams().size() == 1, "Only actual party groups included");
        MembershipRoster roster = observation.teams().getFirst(); var snapshot = roster.settle(null);
        check(roster.members().equals(List.of(A, B)), "Offline actual members included; allies/invitations excluded");
        check(roster.pool().id().equals(id) && roster.pool().authority() == MembershipAuthority.FTB_TEAMS, "Actual stable team identity");
        check(snapshot.allows(A, PoolCapability.MANAGE) && !snapshot.allows(B, PoolCapability.MANAGE), "Owner/member role translation");
        check(FtbTeamsMembershipProvider.current(manager, B, snapshot), "Settled live permission probe");
        ranks.put(B, TeamRank.OFFICER);
        check(!FtbTeamsMembershipProvider.current(manager, B, snapshot), "Unannounced promotion invalidates before access");
        MembershipSnapshot promoted = FtbTeamsMembershipProvider.captureManager(manager).teams().getFirst().settle(snapshot);
        check(promoted.membershipRevision() == snapshot.membershipRevision() && promoted.allows(B, PoolCapability.MANAGE), "Officer becomes common management without roster change");
        ranks.put(B, TeamRank.MEMBER);
        check(!FtbTeamsMembershipProvider.current(manager, B, promoted), "Unannounced demotion invalidates before access");
        check(FtbTeamsMembershipProvider.current(manager, C, null), "Individual/default team is solo");
        check(FtbTeamsMembershipProvider.current(manager, D, null), "No effective team is solo only with complete manager");
        check(!FtbTeamsMembershipProvider.current(null, D, null), "Missing manager is unknown, never solo");
        check(FtbTeamsMembershipProvider.captureManager(null).readiness().status() == ProviderReadiness.Status.NOT_READY, "Startup/absence evidence");
        effective.put(D, administrative);
        check(!FtbTeamsMembershipProvider.current(manager, D, null), "Unexpected effective admin grouping denies financial access");
        effective.remove(B); rejects(() -> FtbTeamsMembershipProvider.captureManager(manager)); effective.put(B, party);
        ranks.remove(B);
        check(!FtbTeamsMembershipProvider.current(manager, A, snapshot), "Roster changed even when actor retained");
        ranks.put(B, TeamRank.MEMBER); ranks.put(C, TeamRank.MEMBER); effective.put(C, party);
        check(!FtbTeamsMembershipProvider.current(manager, A, snapshot), "Newly joined actual member invalidates old snapshot");
        ranks.remove(C); effective.put(C, individual);
        all.add(party); // The common settlement rejects duplicate actual identities, never double counts.
        var duplicate = FtbTeamsMembershipProvider.captureManager(manager);
        var saved = new com.mistaboom.essence_ascendance.data.EssenceSavedData(); saved.establishMembershipAuthority(MembershipAuthority.FTB_TEAMS);
        rejects(() -> new MembershipSettlement(saved).observe(duplicate));
        MembershipProviderTest.providerContract(MembershipAuthority.FTB_TEAMS, (members, managers) -> {
            UUID owner = managers.iterator().next(); Map<UUID, TeamRank> actual = new HashMap<>();
            members.forEach(member -> actual.put(member, member.equals(owner) ? TeamRank.OWNER : managers.contains(member) ? TeamRank.OFFICER : TeamRank.MEMBER));
            Team t = team(id, owner, actual, "party");
            TeamManager m = (TeamManager) Proxy.newProxyInstance(TeamManager.class.getClassLoader(), new Class<?>[]{TeamManager.class},
                    (p, method, args) -> switch (method.getName()) {
                        case "getTeams" -> List.of(t);
                        case "getTeamForPlayerID" -> Optional.ofNullable(actual.containsKey((UUID) args[0]) ? t : null);
                        default -> throw new AssertionError("Unexpected API call " + method);
                    });
            return FtbTeamsMembershipProvider.captureManager(m).teams().getFirst();
        });
        System.out.println("FTB shared provider/accounting contract through actual API translation: PASS");
        System.out.println("FtbTeamsApiBoundaryTest: " + assertions + " assertions passed against real API interfaces with fixture rosters (not live multiplayer)");
    }
    private static void soloPlacementAndJoin() {
        String installedVersion = "future-api-preview+local";
        var saved = new com.mistaboom.essence_ascendance.data.EssenceSavedData();
        saved.establishMembershipAuthority(MembershipAuthority.FTB_TEAMS);
        saved.reservoirs().credit(A, Map.of(com.mistaboom.essence_ascendance.essence.EssenceTypes.OFFENSE.id(),
                com.mistaboom.essence_ascendance.data.ReservoirService.SCALE.multiply(java.math.BigInteger.valueOf(120))),
                com.mistaboom.essence_ascendance.data.ReservoirService.NONE);
        saved.reservoirs().credit(B, Map.of(com.mistaboom.essence_ascendance.essence.EssenceTypes.OFFENSE.id(),
                com.mistaboom.essence_ascendance.data.ReservoirService.SCALE.multiply(java.math.BigInteger.valueOf(40))),
                com.mistaboom.essence_ascendance.data.ReservoirService.NONE);
        var settlement = new MembershipSettlement(saved);
        settlement.observe(FtbTeamsMembershipProvider.captureManager(null, installedVersion));
        var placed = new com.mistaboom.essence_ascendance.machine.MachineBinding();
        placed.place(saved, A);
        var stored = new net.minecraft.nbt.CompoundTag(); placed.save(stored);
        var restored = new com.mistaboom.essence_ascendance.machine.MachineBinding(); restored.load(stored);
        check(!restored.operational(saved), "Placement waits only for an actually unready manager, not for party creation");
        List<Team> all = new ArrayList<>(); Map<UUID, Team> effective = new HashMap<>();
        TeamManager manager = (TeamManager) Proxy.newProxyInstance(TeamManager.class.getClassLoader(), new Class<?>[]{TeamManager.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTeams" -> List.copyOf(all);
                    case "getTeamForPlayerID" -> Optional.ofNullable(effective.get((UUID) args[0]));
                    default -> throw new AssertionError("Unexpected API call " + method);
                });
        var solo = FtbTeamsMembershipProvider.captureManager(manager, installedVersion);
        check(solo.readiness().status() == ProviderReadiness.Status.AVAILABLE
                        && solo.readiness().version().equals(installedVersion) && solo.teams().isEmpty(),
                "A working future-version manager with no party is authoritative ready solo, not unsupported");
        settlement.observe(solo); settlement.observe(solo);
        check(restored.refresh(saved) && restored.personalOwner().equals(A)
                        && restored.allows(saved, A, com.mistaboom.essence_ascendance.machine.MachineAction.USE)
                        && !restored.allows(saved, B, com.mistaboom.essence_ascendance.machine.MachineAction.USE),
                "Previously pending Crucible ownership becomes usable personally without creating a team or replacing it");
        var fresh = new com.mistaboom.essence_ascendance.machine.MachineBinding(); fresh.place(saved, A);
        check(fresh.operational(saved) && fresh.personalOwner().equals(A),
                "Fresh no-party placement works immediately after manager readiness");
        Team party = team(new UUID(5, 5), A, new HashMap<>(Map.of(A, TeamRank.OWNER, B, TeamRank.MEMBER)), "party");
        all.add(party); effective.put(A, party); effective.put(B, party);
        settlement.invalidate("Actual FTB party created");
        var joined = FtbTeamsMembershipProvider.captureManager(manager, installedVersion);
        settlement.observe(joined); settlement.observe(joined);
        check(restored.refresh(saved) && restored.pool().id().equals(party.getId())
                        && restored.allows(saved, B, com.mistaboom.essence_ascendance.machine.MachineAction.USE)
                        && !restored.allows(saved, C, com.mistaboom.essence_ascendance.machine.MachineAction.USE),
                "A pre-party Crucible follows the actual new team while keeping outsiders excluded");
        fresh.place(saved, A);
        check(fresh.pool().equals(restored.pool()) && fresh.operational(saved),
                "Replacing a device after joining uses the same ready team authority");
        check(saved.getPlayerData(A).getCrucibleStored(com.mistaboom.essence_ascendance.essence.EssenceTypes.OFFENSE) == 80
                        && saved.getPlayerData(B).getCrucibleStored(com.mistaboom.essence_ascendance.essence.EssenceTypes.OFFENSE) == 80,
                "Solo-to-party recovery conserves and equalizes Stored Essence exactly once");
    }
    private static void eventInvalidation(Team party, Team individual, Team administrative) {
        var saved = new com.mistaboom.essence_ascendance.data.EssenceSavedData();
        saved.establishMembershipAuthority(MembershipAuthority.FTB_TEAMS);
        var settlement = new MembershipSettlement(saved);
        var empty = new MembershipProvider.Observation(new ProviderReadiness(ProviderReadiness.Status.AVAILABLE,
                "fixture", "Complete empty party roster"), List.of());
        settlement.observe(empty); settlement.observe(empty);
        var originalMembership = saved.resolvePool(A).requireReady();
        var scheduler = new com.mistaboom.essence_ascendance.crucible.ConcurrentChannelScheduler<String, UUID>();
        Object source = new Object(), receiver = new Object();
        long[] delivered = {0}; int[] invalidations = {0};
        scheduler.source("source", source, () -> 100, () -> true);
        var consumer = new com.mistaboom.essence_ascendance.crucible.ConcurrentChannelScheduler.Consumer() {
            @Override public boolean valid() { return saved.resolvePool(A).snapshot().isPresent(); }
            @Override public long demand() { return Long.MAX_VALUE; }
            @Override public long ratePerSecond() { return -1; }
            @Override public long deliver(long grant) { delivered[0] += grant; return grant; }
        };
        var session = scheduler.register("source", A, receiver, 1, consumer);
        long tick = 0;
        for (; tick <= 4; tick++) scheduler.tick(tick);
        check(delivered[0] > 0, "Event fixture starts an actual shared scheduler transfer before observer arrival");
        Runnable invalidate = () -> {
            invalidations[0]++; settlement.invalidate("Actual adapter event policy");
        };
        var changed = FtbTeamsMembershipProvider.teamInvalidation(invalidate);
        Team clientParty = team(new UUID(3, 3), A, new HashMap<>(Map.of(A, TeamRank.OWNER)), "party", true, true);
        Team deletedParty = team(new UUID(4, 4), A, new HashMap<>(Map.of(A, TeamRank.OWNER)), "party", false, false);
        record Change(Runnable apply, String detail) { }
        var ignored = new ArrayList<>(List.of(new Change(() -> changed.accept(individual, null), "default-player creation and first-login null-to-player"),
                new Change(() -> changed.accept(individual, individual), "personal team metadata refresh"),
                new Change(() -> changed.accept(administrative, null), "excluded server grouping"),
                new Change(() -> changed.accept(clientParty, null), "client-side party properties"),
                new Change(() -> changed.accept(clientParty, clientParty), "client-side prior/current party")));
        //? if >=26.1.2 {
        /*var managerChanged = FtbTeamsMembershipProvider.managerInvalidation(server -> invalidate.run());
        TeamManager eventManager = (TeamManager) Proxy.newProxyInstance(TeamManager.class.getClassLoader(), new Class<?>[]{TeamManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getServer")) return null;
                    throw new AssertionError("Unexpected manager event API call " + method);
                });
        ignored.add(new Change(() -> managerChanged.accept(new TeamManagerEvent.Data(eventManager, TeamManagerEvent.Action.SAVED)),
                "manager save during first-login personal-team creation or world persistence"));
        *///? }
        for (var event : ignored) {
            event.apply().run(); scheduler.tick(tick++);
            check(invalidations[0] == 0 && !settlement.pending(), event.detail()+" does not pause unrelated membership");
            check(saved.resolvePool(A).requireReady().equals(originalMembership), event.detail()+" preserves exact membership revisions");
            check(scheduler.current(session), event.detail()+" preserves the original active scheduler session");
        }
        long priorDelivered = delivered[0];
        for (int i = 0; i < 4; i++) scheduler.tick(tick++);
        check(delivered[0] > priorDelivered, "Original session keeps transferring without a new Start after irrelevant FTB events");
        int revision = 2;
        var mutations = new ArrayList<>(List.of(new Change(() -> changed.accept(party, null), "party creation/property/ownership mutation"),
                new Change(() -> changed.accept(party, individual), "personal-to-party join"),
                new Change(() -> changed.accept(individual, party), "party-to-personal leave"),
                new Change(() -> changed.accept(null, party), "prior party disappearance"),
                new Change(() -> changed.accept(deletedParty, null), "already-invalid deleted party")));
        //? if >=26.1.2 {
        /*for (var action : List.of(TeamManagerEvent.Action.CREATED, TeamManagerEvent.Action.LOADED, TeamManagerEvent.Action.DESTROYED))
            mutations.add(new Change(() -> managerChanged.accept(new TeamManagerEvent.Data(eventManager, action)), "manager lifecycle " + action));
        *///? }
        for (var event : mutations) {
            settlement.observe(empty); settlement.observe(empty);
            var active = scheduler.register("source", A, receiver, revision++, consumer);
            int before = invalidations[0]; long beforeDelivery = delivered[0];
            event.apply().run();
            check(invalidations[0] == before + 1 && settlement.pending() && saved.resolvePool(A).snapshot().isEmpty(),
                    event.detail()+" immediately pauses financial and permission access");
            scheduler.tick(tick++);
            check(!scheduler.current(active) && delivered[0] == beforeDelivery,
                    event.detail()+" cannot retain or deliver through an invalid authorized session");
            settlement.observe(empty); settlement.observe(empty);
            for (int i = 0; i < 4; i++) scheduler.tick(tick++);
            check(!scheduler.current(active) && delivered[0] == beforeDelivery,
                    event.detail()+" cannot silently recreate or pay a revoked explicit session after settlement");
        }
        System.out.println("FTB event policy: irrelevant observations preserve live transfer; membership transitions remain fail-closed");
    }
    private static Team team(UUID id, UUID owner, Map<UUID, TeamRank> ranks, String kind) {
        return team(id, owner, ranks, kind, true, false);
    }
    private static Team team(UUID id, UUID owner, Map<UUID, TeamRank> ranks, String kind, boolean valid, boolean client) {
        return (Team) Proxy.newProxyInstance(Team.class.getClassLoader(), new Class<?>[]{Team.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getId", "getTeamId" -> id;
            case "getOwner" -> owner;
            case "getName" -> net.minecraft.network.chat.Component.literal("The Builders");
            case "isPartyTeam" -> kind.equals("party");
            case "isPlayerTeam" -> kind.equals("player");
            case "isServerTeam" -> kind.equals("server");
            case "isValid" -> valid;
            case "isClientTeam" -> client;
            case "getMembers" -> { Set<UUID> members = new HashSet<>(); ranks.forEach((member, rank) -> { if (rank.isMemberOrBetter()) members.add(member); }); yield Set.copyOf(members); }
            case "getRankForPlayer" -> ranks.getOrDefault((UUID) args[0], TeamRank.NONE);
            case "toString" -> "Fixture team " + id;
            default -> throw new AssertionError("Unexpected API call " + method);
        });
    }
    private static void rejects(Runnable action) {
        boolean rejected = false; try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        check(rejected, "Conflicting incomplete provider evidence must reject");
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}

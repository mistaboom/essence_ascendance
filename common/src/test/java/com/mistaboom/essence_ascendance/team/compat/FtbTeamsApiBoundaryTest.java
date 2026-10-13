package com.mistaboom.essence_ascendance.team.compat;

import com.mistaboom.essence_ascendance.team.*;
import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import dev.ftb.mods.ftbteams.api.*;
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
    private static Team team(UUID id, UUID owner, Map<UUID, TeamRank> ranks, String kind) {
        return (Team) Proxy.newProxyInstance(Team.class.getClassLoader(), new Class<?>[]{Team.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getId", "getTeamId" -> id;
            case "getOwner" -> owner;
            case "getName" -> net.minecraft.network.chat.Component.literal("The Builders");
            case "isPartyTeam" -> kind.equals("party");
            case "isPlayerTeam" -> kind.equals("player");
            case "isServerTeam" -> kind.equals("server");
            case "isValid" -> true;
            case "isClientTeam" -> false;
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

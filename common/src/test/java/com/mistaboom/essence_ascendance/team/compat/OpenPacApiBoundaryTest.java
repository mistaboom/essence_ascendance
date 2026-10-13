package com.mistaboom.essence_ascendance.team.compat;

import com.mistaboom.essence_ascendance.team.*;
import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import xaero.pac.common.server.parties.party.api.*;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import java.lang.reflect.Proxy;
import java.util.*;

/** Exact artifact public-interface fixtures; fail on any claims/bridge/invite API access. */
public final class OpenPacApiBoundaryTest {
    private static int assertions;
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);
    private static final UUID ID = new UUID(1, 1);
    private static final class Fixture {
        UUID owner;
        final Map<UUID, PartyMemberRank> ranks = new HashMap<>();
        final Map<UUID, IServerPartyAPI> effective = new HashMap<>();
        final List<IServerPartyAPI> all = new ArrayList<>();
        final IServerPartyAPI party;
        final IPartyManagerAPI manager;
        Fixture(List<UUID> members, Set<UUID> managers) {
            owner = managers.iterator().next();
            members.forEach(member -> ranks.put(member, managers.contains(member) ? PartyMemberRank.ADMIN : PartyMemberRank.MEMBER));
            party = (IServerPartyAPI) Proxy.newProxyInstance(IServerPartyAPI.class.getClassLoader(), new Class<?>[]{IServerPartyAPI.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getId" -> ID;
                        case "getDefaultName" -> "Alex's Party";
                        case "getOwner" -> member(owner);
                        case "getMemberInfoStream" -> ranks.keySet().stream().map(this::member);
                        case "getMemberCount" -> ranks.size();
                        default -> throw new AssertionError("Unexpected API call " + method);
                    });
            all.add(party); members.forEach(member -> effective.put(member, party));
            manager = (IPartyManagerAPI) Proxy.newProxyInstance(IPartyManagerAPI.class.getClassLoader(), new Class<?>[]{IPartyManagerAPI.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAllStream" -> all.stream();
                        case "getPartyByMember" -> effective.get((UUID) args[0]);
                        case "getPartyById" -> all.stream().anyMatch(p -> p == party) && ID.equals(args[0]) ? party : null;
                        default -> throw new AssertionError("Unexpected API call " + method);
                    });
        }
        IPartyMemberAPI member(UUID id) {
            return (IPartyMemberAPI) Proxy.newProxyInstance(IPartyMemberAPI.class.getClassLoader(), new Class<?>[]{IPartyMemberAPI.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUUID" -> id;
                        case "getRank" -> ranks.get(id);
                        case "isOwner" -> id.equals(owner);
                        default -> throw new AssertionError("Unexpected API call " + method);
                    });
        }
    }
    public static void run() {
        Fixture f = new Fixture(List.of(A, B), Set.of(A));
        var observation = OpenPacMembershipProvider.captureManager(f.manager);
        check(OpenPacMembershipProvider.teamName(f.manager, null, ID).orElseThrow().equals("Alex's Party"), "Default party name is readable without exposing UUID");
        var config = (xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI) Proxy.newProxyInstance(
                xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI.class.getClassLoader(),
                new Class<?>[]{xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI.class},
                (p, method, args) -> {
                    if (method.getName().equals("getEffective") && args[0] == xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions.PARTY_NAME) return "The Builders";
                    throw new AssertionError("Unexpected config call " + method);
                });
        var configs = (xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI) Proxy.newProxyInstance(
                xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI.class.getClassLoader(),
                new Class<?>[]{xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI.class},
                (p, method, args) -> {
                    if (method.getName().equals("getLoadedConfig") && args[0].equals(A)) return config;
                    throw new AssertionError("Unexpected config manager call " + method);
                });
        check(OpenPacMembershipProvider.teamName(f.manager, configs, ID).orElseThrow().equals("The Builders"), "Custom party display name comes from the owner configuration API");
        check(OpenPacMembershipProvider.teamName(null, configs, ID).isEmpty(), "Unavailable party names do not change membership");
        var roster = observation.teams().getFirst(); var snapshot = roster.settle(null);
        check(observation.readiness().status() == ProviderReadiness.Status.AVAILABLE && roster.members().equals(List.of(A, B)), "Complete offline actual roster");
        check(roster.pool().id().equals(ID) && roster.pool().authority() == MembershipAuthority.OPENPAC, "Stable party ID, independent of owner");
        check(snapshot.allows(A, PoolCapability.MANAGE) && !snapshot.allows(B, PoolCapability.MANAGE), "Owner/member roles");
        check(OpenPacMembershipProvider.current(f.manager, B, snapshot), "Settled live probe");
        check(OpenPacMembershipProvider.current(f.manager, C, null), "No actual party is ready solo");
        check(!OpenPacMembershipProvider.current(null, C, null), "Missing manager never solo");
        check(OpenPacMembershipProvider.captureManager(null).readiness().status() == ProviderReadiness.Status.NOT_READY, "No manager distinct from empty parties");
        f.ranks.put(B, PartyMemberRank.CLAIMER);
        check(OpenPacMembershipProvider.current(f.manager, B, snapshot), "Claim permission doesn't grant management");
        f.ranks.put(B, PartyMemberRank.MODERATOR);
        check(!OpenPacMembershipProvider.current(f.manager, B, snapshot), "Unannounced promotion denied before access");
        var promoted = OpenPacMembershipProvider.captureManager(f.manager).teams().getFirst().settle(snapshot);
        check(promoted.membershipRevision() == snapshot.membershipRevision() && promoted.allows(B, PoolCapability.MANAGE), "Moderator maps to shared management");
        f.ranks.put(B, PartyMemberRank.MEMBER);
        check(!OpenPacMembershipProvider.current(f.manager, B, promoted), "Unannounced demotion denied");
        f.owner = B; f.ranks.put(B, PartyMemberRank.ADMIN);
        check(OpenPacMembershipProvider.captureManager(f.manager).teams().getFirst().pool().equals(roster.pool()), "Ownership change retains financial party identity");
        f.effective.remove(A); rejects(() -> OpenPacMembershipProvider.captureManager(f.manager)); f.effective.put(A, f.party);
        f.ranks.put(C, PartyMemberRank.MEMBER); f.effective.put(C, f.party);
        check(!OpenPacMembershipProvider.current(f.manager, B, promoted), "New actual member invalidates prior settlement");
        f.all.clear();
        check(!OpenPacMembershipProvider.current(f.manager, B, promoted) && !OpenPacMembershipProvider.current(f.manager, A, null), "Spread deletion stale offline index denies old/solo access");
        var saved = new com.mistaboom.essence_ascendance.data.EssenceSavedData(); saved.establishMembershipAuthority(MembershipAuthority.OPENPAC);
        f.all.add(f.party); f.all.add(f.party);
        rejects(() -> new MembershipSettlement(saved).observe(OpenPacMembershipProvider.captureManager(f.manager)));
        MembershipProviderTest.providerContract(MembershipAuthority.OPENPAC, (members, managers) ->
                OpenPacMembershipProvider.captureManager(new Fixture(members, managers).manager).teams().getFirst());
        System.out.println("OpenPAC shared provider/accounting contract through actual API translation: PASS");
        System.out.println("OpenPacApiBoundaryTest: " + assertions + " assertions passed against actual 0.32.7 interfaces; fixture parties, no live multiplayer");
    }
    private static void rejects(Runnable action) {
        boolean rejected = false; try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        check(rejected, "Incomplete/conflicting evidence rejected");
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}

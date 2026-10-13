package com.mistaboom.essence_ascendance.team.compat;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.team.*;
import dev.ftb.mods.ftbteams.api.*;
import dev.ftb.mods.ftbteams.api.event.*;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.function.Consumer;

/** Only this conditionally constructed class links the verified FTB Teams 2101.1.11 public API. */
public final class FtbTeamsMembershipProvider implements MembershipProvider {
    private static final String VERSION = "2101.1.11";
    private static TeamManager manager(MinecraftServer server) {
        FTBTeamsAPI.API api = FTBTeamsAPI.api();
        if (api == null || !api.isManagerLoaded()) return null;
        TeamManager manager = api.getManager();
        return manager.getServer() == server ? manager : null;
    }
    private static Set<UUID> managers(Team team, Set<UUID> members) {
        Set<UUID> managers = new HashSet<>();
        for (UUID member : members) {
            TeamRank rank = team.getRankForPlayer(member);
            if (!rank.isMemberOrBetter()) throw new IllegalStateException("FTB member/rank inconsistency");
            if (rank.isOfficerOrBetter()) managers.add(member);
        }
        if (!members.contains(team.getOwner()) || !managers.contains(team.getOwner()))
            throw new IllegalStateException("FTB party owner missing from complete roster");
        return managers;
    }
    @Override public Observation capture(MinecraftServer server) {
        return captureManager(manager(server));
    }
    @Override public Optional<String> teamName(MinecraftServer server, UUID id) {
        return teamName(manager(server), id);
    }
    static Optional<String> teamName(TeamManager manager, UUID id) {
        return manager == null ? Optional.empty() : manager.getTeamByID(id)
                .filter(team -> team.isPartyTeam() && team.isValid() && !team.isClientTeam())
                .map(team -> team.getName().getString()).filter(name -> !name.isBlank());
    }
    static Observation captureManager(TeamManager manager) {
        if (manager == null) return new Observation(new ProviderReadiness(ProviderReadiness.Status.NOT_READY,
                VERSION, "FTB manager not loaded for this server"), List.of());
        List<MembershipRoster> rosters = new ArrayList<>();
        for (Team team : manager.getTeams()) {
            // Player/default teams resolve to solo; server administrative groupings are excluded.
            if (!team.isPartyTeam()) continue;
            if (!team.isValid() || team.isClientTeam()) throw new IllegalStateException("Unsettled FTB team");
            Set<UUID> members = Set.copyOf(team.getMembers());
            Set<UUID> managers = managers(team, members);
            for (UUID member : members) {
                Team effective = manager.getTeamForPlayerID(member).orElseThrow(() -> new IllegalStateException("Missing FTB effective team"));
                if (!effective.isPartyTeam() || !effective.getId().equals(team.getId()))
                    throw new IllegalStateException("FTB effective roster mismatch");
            }
            rosters.add(MembershipRoster.party(MembershipAuthority.FTB_TEAMS, team.getId(), members, managers, team.getOwner()));
        }
        // A complete party roster includes offline members. Invitations/allies never enter getMembers().
        return new Observation(new ProviderReadiness(ProviderReadiness.Status.AVAILABLE, VERSION,
                "Complete actual FTB party rosters including offline members"), rosters);
    }
    @Override public boolean isCurrent(MinecraftServer server, UUID member, MembershipSnapshot settled) {
        return current(manager(server), member, settled);
    }
    static boolean current(TeamManager manager, UUID member, MembershipSnapshot settled) {
        if (manager == null) return false;
        Team team = manager.getTeamForPlayerID(member).orElse(null);
        if (team == null || team.isPlayerTeam()) return settled == null;
        if (!team.isPartyTeam()) return false; // Unexpected effective server/unknown team is unresolved.
        if (settled == null || !team.isValid() || team.isClientTeam() || !settled.pool().id().equals(team.getId())) return false;
        Set<UUID> members = team.getMembers();
        if (!members.equals(new HashSet<>(settled.members())) || !team.getOwner().equals(settled.recoveryRecipient())) return false;
        TeamRank rank = team.getRankForPlayer(member);
        return rank.isMemberOrBetter() && settled.allows(member, PoolCapability.MANAGE) == rank.isOfficerOrBetter();
    }
    @Override public void installInvalidation(Consumer<MinecraftServer> invalidate) {
        Consumer<TeamEvent> changed = ignored -> {
            FTBTeamsAPI.API api = FTBTeamsAPI.api();
            if (api != null && api.isManagerLoaded()) invalidate.accept(api.getManager().getServer());
        };
        TeamManagerEvent.CREATED.register(e -> invalidate.accept(e.getManager().getServer()));
        TeamManagerEvent.LOADED.register(e -> invalidate.accept(e.getManager().getServer()));
        TeamManagerEvent.DESTROYED.register(e -> invalidate.accept(e.getManager().getServer()));
        TeamEvent.CREATED.register(changed::accept);
        TeamEvent.DELETED.register(changed);
        TeamEvent.PLAYER_CHANGED.register(changed::accept);
        TeamEvent.PLAYER_JOINED_PARTY.register(changed::accept);
        TeamEvent.PLAYER_LEFT_PARTY.register(changed::accept);
        TeamEvent.OWNERSHIP_TRANSFERRED.register(changed::accept);
        TeamEvent.PROPERTIES_CHANGED.register(changed::accept);
    }
}

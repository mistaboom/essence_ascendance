package com.mistaboom.essence_ascendance.team.compat;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.team.*;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.parties.party.PartyManager;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import java.util.*;
import java.util.function.Consumer;

/** Audited 0.32.7/1.21.1 public API. Reads OPAC's own parties, never its claims party-system bridges.
 * The documented API has no party listeners/load receipt. The exact artifact's public
 * PartyManager.isLoaded() is the isolated version-bound readiness specialization: disabled parties
 * must never be mistaken for confirmed deletion. No implementation fields or files are accessed.
 */
public final class OpenPacMembershipProvider implements MembershipProvider {
    private final Set<MinecraftServer> started = Collections.newSetFromMap(new IdentityHashMap<>());
    private IPartyManagerAPI manager(MinecraftServer server) {
        if (server == null || !started.contains(server)) return null;
        IPartyManagerAPI manager = OpenPACServerAPI.get(server).getPartyManager();
        if (!(manager instanceof PartyManager nativeManager))
            throw new IllegalStateException("Unsupported OpenPAC manager implementation; expected audited 0.32.7 native parties");
        return nativeManager.isLoaded() ? manager : null;
    }
    @Override public void installInvalidation(Consumer<MinecraftServer> invalidate) {
        LifecycleEvent.SERVER_STARTED.register(server -> { started.add(server); invalidate.accept(server); });
        LifecycleEvent.SERVER_STOPPING.register(server -> { started.remove(server); invalidate.accept(server); });
        LifecycleEvent.SERVER_STOPPED.register(started::remove);
    }
    @Override public Observation capture(MinecraftServer server) { return captureManager(manager(server)); }
    @Override public Optional<String> teamName(MinecraftServer server, UUID id) {
        var manager = manager(server);
        return teamName(manager, manager == null ? null : OpenPACServerAPI.get(server).getPlayerConfigManager(), id);
    }
    static Optional<String> teamName(IPartyManagerAPI manager,
            xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI configs, UUID id) {
        var party = manager == null ? null : manager.getPartyById(id);
        if (party == null) return Optional.empty();
        var config = configs == null ? null : configs.getLoadedConfig(party.getOwner().getUUID());
        String name = config == null ? "" : config.getEffective(xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions.PARTY_NAME);
        return Optional.ofNullable(name == null || name.isBlank() ? party.getDefaultName() : name).filter(value -> !value.isBlank());
    }
    static Observation captureManager(IPartyManagerAPI manager) {
        if (manager == null) return new Observation(new ProviderReadiness(ProviderReadiness.Status.NOT_READY,
                "0.32.7", "OpenPAC parties not loaded/disabled or server API unavailable"), List.of());
        try (var parties = manager.getAllStream()) {
            List<MembershipRoster> rosters = parties.map(party -> roster(manager, party)).toList();
            return new Observation(new ProviderReadiness(ProviderReadiness.Status.AVAILABLE, "0.32.7",
                    "Complete OpenPAC actual parties including offline members; claims integrations excluded"), rosters);
        }
    }
    private static MembershipRoster roster(IPartyManagerAPI manager, IServerPartyAPI party) {
        Set<UUID> members = new HashSet<>(), managers = new HashSet<>();
        try (var stream = party.getMemberInfoStream()) {
            stream.forEach(info -> {
                UUID member = Objects.requireNonNull(info.getUUID());
                if (!members.add(member)) throw new IllegalStateException("Duplicate OpenPAC actual member");
                PartyMemberRank rank = Objects.requireNonNull(info.getRank());
                if (info.isOwner() || rank == PartyMemberRank.MODERATOR || rank == PartyMemberRank.ADMIN) managers.add(member);
                IServerPartyAPI effective = manager.getPartyByMember(member);
                if (effective == null || !party.getId().equals(effective.getId()))
                    throw new IllegalStateException("OpenPAC member index is unsettled");
            });
        }
        if (members.size() != party.getMemberCount() || !members.contains(party.getOwner().getUUID())
                || !party.getOwner().isOwner() || !managers.contains(party.getOwner().getUUID())
                || manager.getPartyById(party.getId()) == null)
            throw new IllegalStateException("Incomplete OpenPAC party evidence");
        return MembershipRoster.party(MembershipAuthority.OPENPAC, party.getId(), members, managers, party.getOwner().getUUID());
    }
    @Override public boolean isCurrent(MinecraftServer server, UUID member, MembershipSnapshot settled) {
        return current(manager(server), member, settled);
    }
    static boolean current(IPartyManagerAPI manager, UUID member, MembershipSnapshot settled) {
        if (manager == null) return false;
        IServerPartyAPI party = manager.getPartyByMember(member);
        if (party == null) return settled == null;
        // Deletion is spread across ticks. Stale offline index entries cannot authorize an old party.
        if (manager.getPartyById(party.getId()) == null || settled == null
                || !settled.pool().equals(new PoolId(MembershipAuthority.OPENPAC, PoolId.Kind.TEAM, party.getId()))) return false;
        MembershipRoster current = roster(manager, party);
        return current.members().equals(settled.members()) && current.grants().equals(settled.permissions())
                && Objects.equals(current.recoveryRecipient(), settled.recoveryRecipient());
    }
}

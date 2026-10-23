package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import net.minecraft.nbt.*;
import java.util.*;

/** Applicant-originated native operations. All financial transitions use the common settled reconciler. */
public final class NativeMembershipService {
    public static final ProviderReadiness READY = new ProviderReadiness(
            ProviderReadiness.Status.AVAILABLE, "native-3", "Complete native membership");
    private final EssenceSavedData saved;
    private final Map<PoolId, String> names = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>();
    public record Request(UUID token, UUID applicant, PoolId source, PoolId target,
                          long sourceRevision, long targetRevision) { }
    public NativeMembershipService(EssenceSavedData saved) { this.saved = Objects.requireNonNull(saved); }
    private void writable() {
        if (saved.membershipAuthority() != MembershipAuthority.NATIVE
                || !MembershipAuthority.ready(saved.reservoirs().readiness()))
            throw new IllegalStateException("Native membership writes unavailable under the current authority/readiness");
    }
    public List<Request> requests(UUID actor) {
        writable();
        refreshRequests();
        MembershipSnapshot current = saved.resolvePool(actor).requireReady();
        return requests.values().stream().filter(r -> r.applicant().equals(actor)
                || current.pool().equals(r.target()) && current.allows(actor, PoolCapability.MANAGE))
                .sorted(Comparator.comparing(Request::token)).toList();
    }
    public String name(PoolId pool) { saved.reservoirs().revision(); return names.getOrDefault(pool, "Unnamed team"); }
    public PoolId create(UUID actor, String name) {
        writable(); requireSolo(actor);
        if (name == null || name.isBlank() || name.length() > 80) throw new IllegalArgumentException("Team name must contain 1..80 characters");
        PoolId id = PoolId.newNative(PoolId.Kind.TEAM);
        List<MembershipRoster> batch = batch();
        batch.add(new MembershipRoster(id, List.of(actor), MembershipPolicy.grants(List.of(actor), Set.of(actor))));
        publish(batch);
        names.put(id, name.strip()); requests.remove(actor); saved.setDirty(); return id;
    }
    public Request request(UUID applicant, PoolId target, boolean acknowledgeSharingStoredEssence) {
        writable();
        if (!acknowledgeSharingStoredEssence) throw new IllegalArgumentException("Applicant must acknowledge sharing all existing stored essence");
        MembershipSnapshot source = requireSolo(applicant), destination = team(target);
        if (requests.containsKey(applicant)) throw new IllegalStateException("Cancel the existing request first");
        Request request = new Request(UUID.randomUUID(), applicant, source.pool(), target,
                source.membershipRevision(), destination.membershipRevision());
        requests.put(applicant, request); saved.setDirty(); return request;
    }
    /** A solo owner must explicitly approve before either player is put in a new team. */
    public Request requestPersonal(UUID applicant, UUID owner, boolean acknowledgeSharingStoredEssence) {
        writable();
        if (!acknowledgeSharingStoredEssence || applicant.equals(owner)) throw new IllegalArgumentException("Explicit sharing acknowledgment required");
        MembershipSnapshot source = requireSolo(applicant), destination = requireSolo(owner);
        if (requests.containsKey(applicant)) throw new IllegalStateException("Cancel the existing request first");
        Request request = new Request(UUID.randomUUID(), applicant, source.pool(), destination.pool(),
                source.membershipRevision(), destination.membershipRevision());
        requests.put(applicant, request); saved.setDirty(); return request;
    }
    private MembershipSnapshot destination(PoolId id) {
        if (id.kind() == PoolId.Kind.TEAM) return team(id);
        UUID owner = saved.personalOwner(id);
        if (owner == null) throw new IllegalStateException("Personal owner unavailable");
        MembershipSnapshot current = requireSolo(owner);
        if (!current.pool().equals(id)) throw new IllegalStateException("Owner membership changed");
        return current;
    }
    public void cancel(UUID applicant, UUID token) {
        writable(); Request request = pending(token);
        if (!request.applicant().equals(applicant)) throw new IllegalStateException("Only the applicant may cancel");
        requests.remove(applicant); saved.setDirty();
    }
    public void decide(UUID actor, UUID token, boolean approve) { decide(actor, token, approve, null); }
    public void decide(UUID actor, UUID token, boolean approve, String createdTeamName) {
        writable(); Request request = pending(token);
        MembershipSnapshot destination = destination(request.target());
        if (!destination.allows(actor, PoolCapability.MANAGE)) throw new IllegalStateException("Only the team owner may decide");
        if (approve) {
            MembershipSnapshot source = requireSolo(request.applicant());
            if (!source.pool().equals(request.source()) || source.membershipRevision() != request.sourceRevision()
                    || destination.membershipRevision() != request.targetRevision())
                throw new IllegalStateException("Stale request; applicant must cancel and request again");
            List<UUID> members = new ArrayList<>(destination.members()); members.add(request.applicant());
            Set<UUID> managers = managers(destination);
            if (destination.pool().kind() == PoolId.Kind.SOLO) {
                if (createdTeamName == null || createdTeamName.isBlank() || createdTeamName.length() > 80)
                    throw new IllegalArgumentException("Creating a team requires a name with 1..80 characters; use /essence team approve <request> <name>");
                PoolId created = PoolId.newNative(PoolId.Kind.TEAM);
                replace(new MembershipRoster(created, members, MembershipPolicy.grants(members, managers)));
                names.put(created, createdTeamName.strip());
            } else replace(new MembershipRoster(destination.pool(), members, MembershipPolicy.grants(members, managers)));
        }
        requests.remove(request.applicant()); saved.setDirty();
    }
    public void leave(UUID actor) {
        writable(); MembershipSnapshot current = ownTeam(actor);
        if (current.allows(actor, PoolCapability.MANAGE))
            throw new IllegalStateException("The owner must transfer ownership or disband after all other members have left");
        detach(current, actor);
    }
    public void remove(UUID manager, UUID member) {
        writable(); MembershipSnapshot current = managed(manager);
        if (!current.members().contains(member) || current.allows(member, PoolCapability.MANAGE))
            throw new IllegalStateException("Removal requires a current non-management member");
        detach(current, member);
    }
    public void transfer(UUID manager, UUID successor) {
        writable(); MembershipSnapshot current = managed(manager);
        if (!current.members().contains(successor)) throw new IllegalStateException("Successor must be a current member");
        replace(new MembershipRoster(current.pool(), current.members(), MembershipPolicy.grants(current.members(), Set.of(successor))));
    }
    public void disband(UUID manager) {
        writable(); MembershipSnapshot current = managed(manager);
        if (current.members().size() != 1) throw new IllegalStateException("Other members must leave before the owner can disband");
        delete(current.pool());
    }
    private void detach(MembershipSnapshot current, UUID departing) {
        List<UUID> remaining = new ArrayList<>(current.members()); remaining.remove(departing);
        if (remaining.isEmpty()) { delete(current.pool()); return; }
        replace(new MembershipRoster(current.pool(), remaining, MembershipPolicy.grants(remaining, managers(current))));
    }
    private Set<UUID> managers(MembershipSnapshot snapshot) {
        Set<UUID> managers = new HashSet<>();
        snapshot.members().stream().filter(m -> snapshot.allows(m, PoolCapability.MANAGE)).forEach(managers::add);
        return managers;
    }
    private MembershipSnapshot requireSolo(UUID actor) {
        MembershipSnapshot current = saved.resolvePool(actor).requireReady();
        if (!current.isSoloFor(actor)) throw new IllegalStateException("Leave the existing team first; teams are never silently merged");
        return current;
    }
    private MembershipSnapshot ownTeam(UUID actor) {
        MembershipSnapshot current = saved.resolvePool(actor).requireReady();
        if (current.pool().kind() != PoolId.Kind.TEAM) throw new IllegalStateException("No current team");
        return current;
    }
    private MembershipSnapshot managed(UUID actor) {
        MembershipSnapshot current = ownTeam(actor);
        if (!current.allows(actor, PoolCapability.MANAGE)) throw new IllegalStateException("Management required");
        return current;
    }
    private MembershipSnapshot team(PoolId id) {
        return saved.reservoirs().settledTeams().stream().filter(t -> t.pool().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Target team no longer exists"));
    }
    /** Keep valid requests usable as rosters change; discard requests whose applicant or destination moved on. */
    private void refreshRequests() {
        boolean changed = false;
        var iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Request request = entry.getValue();
            var source = saved.resolvePool(request.applicant()).snapshot().orElse(null);
            MembershipSnapshot target;
            try { target = destination(request.target()); }
            catch (IllegalStateException unavailable) { target = null; }
            if (source == null || !source.isSoloFor(request.applicant()) || !source.pool().equals(request.source())
                    || source.membershipRevision() != request.sourceRevision() || target == null) {
                iterator.remove(); changed = true;
            } else if (target.membershipRevision() != request.targetRevision()) {
                entry.setValue(new Request(request.token(), request.applicant(), request.source(), request.target(),
                        request.sourceRevision(), target.membershipRevision()));
                changed = true;
            }
        }
        if (changed) saved.setDirty();
    }
    private Request pending(UUID token) {
        return requests.values().stream().filter(r -> r.token().equals(token)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Request no longer pending"));
    }
    private List<MembershipRoster> batch() {
        return new ArrayList<>(saved.reservoirs().settledTeams().stream().map(MembershipRoster::from).toList());
    }
    private void publish(List<MembershipRoster> batch) {
        saved.establishMembershipAuthority(MembershipAuthority.NATIVE);
        MembershipPublication.publish(saved, READY, batch);
        refreshRequests();
    }
    private void replace(MembershipRoster roster) {
        List<MembershipRoster> batch = batch(); batch.removeIf(r -> r.pool().equals(roster.pool())); batch.add(roster); publish(batch);
    }
    private void delete(PoolId id) {
        List<MembershipRoster> batch = batch(); batch.removeIf(r -> r.pool().equals(id)); publish(batch);
        names.remove(id); requests.values().removeIf(r -> r.target().equals(id)); saved.setDirty();
    }
    public CompoundTag save() {
        saved.reservoirs().revision();
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1);
        CompoundTag display = new CompoundTag(); names.forEach((id, name) -> display.putString(id.id().toString(), name)); tag.put("names", display);
        ListTag pending = new ListTag();
        requests.values().stream().sorted(Comparator.comparing(Request::applicant)).forEach(r -> {
            CompoundTag entry = new CompoundTag(); NbtVersionCompat.putUUID(entry, "token", r.token()); NbtVersionCompat.putUUID(entry, "applicant", r.applicant());
            entry.put("source", r.source().save()); entry.put("target", r.target().save());
            entry.putLong("source_revision", r.sourceRevision()); entry.putLong("target_revision", r.targetRevision()); pending.add(entry);
        });
        tag.put("requests", pending); return tag;
    }
    public static NativeMembershipService load(EssenceSavedData saved, CompoundTag tag) {
        if (tag.getInt("version") != 1 || !tag.contains("names", Tag.TAG_COMPOUND) || !tag.contains("requests", Tag.TAG_LIST))
            throw new IllegalStateException("Invalid native membership metadata");
        NativeMembershipService result = new NativeMembershipService(saved);
        CompoundTag display = tag.getCompound("names");
        for (String key : display.getAllKeys()) {
            PoolId id = new PoolId(MembershipAuthority.NATIVE, PoolId.Kind.TEAM, UUID.fromString(key));
            String name = display.getString(key);
            if (!display.contains(key, Tag.TAG_STRING) || name.isBlank() || name.length() > 80)
                throw new IllegalStateException("Invalid native display name");
            result.team(id); result.names.put(id, name);
        }
        Set<UUID> tokens = new HashSet<>();
        ListTag pending = tag.getList("requests", Tag.TAG_COMPOUND);
        if (!NbtVersionCompat.listHasElementType((ListTag) tag.get("requests"), Tag.TAG_COMPOUND))
            throw new IllegalStateException("Invalid request list");
        for (Tag raw : pending) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("token") || !entry.hasUUID("applicant") || !entry.contains("source_revision", Tag.TAG_LONG)
                    || !entry.contains("target_revision", Tag.TAG_LONG)) throw new IllegalStateException("Incomplete request");
            Request r = new Request(entry.getUUID("token"), entry.getUUID("applicant"), PoolId.load(entry.getCompound("source")),
                    PoolId.load(entry.getCompound("target")), entry.getLong("source_revision"), entry.getLong("target_revision"));
            if (r.source().authority() != MembershipAuthority.NATIVE || r.source().kind() != PoolId.Kind.SOLO
                    || r.target().authority() != MembershipAuthority.NATIVE
                    || r.sourceRevision() < 1 || r.targetRevision() < 1 || !tokens.add(r.token())
                    || result.requests.put(r.applicant(), r) != null) throw new IllegalStateException("Invalid/duplicate request");
        }
        return result;
    }
}

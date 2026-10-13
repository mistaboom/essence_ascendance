package com.mistaboom.essence_ascendance.team;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Complete actual roster (including offline members); no invitations, allies or claim trust. */
public record MembershipSnapshot(PoolId pool, List<UUID> members,
                                 Map<UUID, Set<PoolCapability>> permissions,
                                 long membershipRevision, long permissionRevision, UUID recoveryRecipient) {
    public MembershipSnapshot(PoolId pool, List<UUID> members, Map<UUID, Set<PoolCapability>> permissions,
                              long membershipRevision, long permissionRevision) {
        this(pool, members, permissions, membershipRevision, permissionRevision, inferRecipient(members, permissions));
    }
    public static UUID inferRecipient(Collection<UUID> members, Map<UUID, Set<PoolCapability>> permissions) {
        var unique = new TreeSet<>(members);
        if (unique.size() == 1) return unique.first();
        var managers = unique.stream().filter(m -> permissions.getOrDefault(m, Set.of()).contains(PoolCapability.MANAGE)).toList();
        return managers.size() == 1 ? managers.getFirst() : null;
    }
    public MembershipSnapshot {
        Objects.requireNonNull(pool);
        members = List.copyOf(new TreeSet<>(members));
        if (members.isEmpty() || (pool.kind() == PoolId.Kind.SOLO && members.size() != 1))
            throw new IllegalArgumentException("A pool needs members; a solo pool needs exactly one");
        if (recoveryRecipient != null && !members.contains(recoveryRecipient)) throw new IllegalArgumentException("Recovery recipient is not a member");
        if (membershipRevision < 1 || permissionRevision < 1)
            throw new IllegalArgumentException("Pool revisions must be positive");
        if (!members.containsAll(permissions.keySet()))
            throw new IllegalArgumentException("Permissions granted to a nonmember");
        Map<UUID, Set<PoolCapability>> normalized = new LinkedHashMap<>();
        for (UUID member : members) {
            Set<PoolCapability> granted = permissions.getOrDefault(member, Set.of());
            normalized.put(member, Collections.unmodifiableSet(granted.isEmpty()
                    ? EnumSet.noneOf(PoolCapability.class) : EnumSet.copyOf(granted)));
        }
        permissions = Collections.unmodifiableMap(normalized);
    }

    public boolean allows(UUID actor, PoolCapability capability) {
        Objects.requireNonNull(capability);
        return permissions.getOrDefault(actor, Set.of()).contains(capability);
    }

    public boolean isSoloFor(UUID player) {
        return pool.kind() == PoolId.Kind.SOLO && members.size() == 1 && members.getFirst().equals(player);
    }

    /** A plan only: consumers must transact account shares before publishing a changed roster. */
    public Change reconcile(Collection<UUID> roster, Map<UUID, Set<PoolCapability>> grants) {
        MembershipSnapshot normalized = new MembershipSnapshot(pool, List.copyOf(roster), grants,
                membershipRevision, permissionRevision);
        if (pool.kind() == PoolId.Kind.SOLO && !members.equals(normalized.members))
            throw new IllegalArgumentException("A solo pool cannot be reassigned to another player");
        Set<UUID> added = new TreeSet<>(normalized.members);
        added.removeAll(members);
        Set<UUID> removed = new TreeSet<>(members);
        removed.removeAll(normalized.members);
        boolean membershipChanged = !members.equals(normalized.members);
        boolean permissionsChanged = !permissions.equals(normalized.permissions);
        MembershipSnapshot after = new MembershipSnapshot(pool, normalized.members, normalized.permissions,
                membershipChanged ? Math.incrementExact(membershipRevision) : membershipRevision,
                permissionsChanged ? Math.incrementExact(permissionRevision) : permissionRevision);
        return new Change(this, after, Set.copyOf(added), Set.copyOf(removed));
    }

    public record Change(MembershipSnapshot before, MembershipSnapshot after,
                         Set<UUID> added, Set<UUID> removed) {
        public boolean membershipChanged() { return before.membershipRevision != after.membershipRevision; }
        public boolean permissionsChanged() { return before.permissionRevision != after.permissionRevision; }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("pool", pool.save());
        if (recoveryRecipient != null) tag.putUUID("recovery_recipient", recoveryRecipient);
        tag.putLong("membership_revision", membershipRevision);
        tag.putLong("permission_revision", permissionRevision);
        ListTag roster = new ListTag();
        for (UUID member : members) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", member);
            int mask = 0;
            for (PoolCapability capability : permissions.get(member)) mask |= 1 << capability.ordinal();
            entry.putInt("capabilities", mask);
            roster.add(entry);
        }
        tag.put("members", roster);
        return tag;
    }

    public static MembershipSnapshot load(CompoundTag tag) {
        if (!tag.contains("pool", Tag.TAG_COMPOUND) || !tag.contains("members", Tag.TAG_LIST)
                || !tag.contains("membership_revision", Tag.TAG_LONG)
                || !tag.contains("permission_revision", Tag.TAG_LONG))
            throw new IllegalStateException("Incomplete saved membership snapshot");
        Map<UUID, Set<PoolCapability>> grants = new LinkedHashMap<>();
        ListTag roster = tag.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < roster.size(); i++) {
            CompoundTag entry = roster.getCompound(i);
            if (!entry.hasUUID("player") || !entry.contains("capabilities", Tag.TAG_INT))
                throw new IllegalStateException("Incomplete saved member");
            UUID member = entry.getUUID("player");
            int mask = entry.getInt("capabilities");
            if (mask < 0 || mask >= 1 << PoolCapability.values().length)
                throw new IllegalStateException("Unknown saved membership capability");
            Set<PoolCapability> capabilities = EnumSet.noneOf(PoolCapability.class);
            for (PoolCapability capability : PoolCapability.values())
                if ((mask & 1 << capability.ordinal()) != 0) capabilities.add(capability);
            if (grants.put(member, capabilities) != null)
                throw new IllegalStateException("Duplicate saved member");
        }
        return new MembershipSnapshot(PoolId.load(tag.getCompound("pool")), List.copyOf(grants.keySet()),
                grants, tag.getLong("membership_revision"), tag.getLong("permission_revision"),
                tag.hasUUID("recovery_recipient") ? tag.getUUID("recovery_recipient") : null);
    }
}

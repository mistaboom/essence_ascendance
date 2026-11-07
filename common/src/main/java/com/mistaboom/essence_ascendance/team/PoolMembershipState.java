package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Metadata within EssenceSavedData, with no essence balance or contribution ledger. Server-thread confined. */
public final class PoolMembershipState {
    private static final ProviderReadiness NATIVE_READY = new ProviderReadiness(
            ProviderReadiness.Status.AVAILABLE, "stage-3", "Authoritative solo membership ready");
    private final Map<UUID, MembershipSnapshot> solos = new HashMap<>();
    /** Exact identities migrated by an automatic fallback; never a blanket provider alias. */
    private final Map<PoolId, PoolId> fallbackPools = new HashMap<>();
    private String fallbackReason = "";
    private MembershipAuthority established;

    public MembershipAuthority establishedAuthority() { return established; }
    public boolean contains(UUID player) { return solos.containsKey(player); }
    public boolean hasFallback() { return established == MembershipAuthority.NATIVE && !fallbackReason.isEmpty(); }
    public String fallbackReason() { return fallbackReason; }
    public PoolId remapFallbackPool(PoolId pool) {
        return hasFallback() && pool != null ? fallbackPools.getOrDefault(pool, pool) : pool;
    }
    public UUID personalOwner(PoolId pool) {
        return solos.entrySet().stream().filter(e -> e.getValue().pool().equals(pool)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    /** Ordinary selection cannot change identities; fallback has its own preserved-state boundary. */
    public void establish(MembershipAuthority authority) {
        Objects.requireNonNull(authority);
        if (established != null && established != authority)
            throw new IllegalStateException("Authority change requires a deliberate reconciliation boundary/new development world");
        established = authority;
    }

    /** Prepare replacement identity metadata without touching balances or the active authority. */
    public PoolMembershipState forAuthority(MembershipAuthority authority) {
        PoolMembershipState result = new PoolMembershipState(); result.establish(authority);
        for (UUID player : solos.keySet()) result.resolveSolo(player);
        return result;
    }

    /** Stage a native identity replacement without changing a single financial or ownership receipt. */
    public PoolMembershipState forNativeFallback(java.util.Collection<PoolId> knownPools, String reason) {
        if (established == null || established == MembershipAuthority.NATIVE)
            throw new IllegalStateException("Fallback requires an established external authority");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Fallback needs an actual failure reason");
        PoolMembershipState result = new PoolMembershipState(); result.establish(MembershipAuthority.NATIVE);
        result.fallbackReason = reason.length() > 2048 ? reason.substring(0, 2048) : reason;
        java.util.Set<PoolId> identities = new java.util.HashSet<>(knownPools);
        solos.values().forEach(snapshot -> identities.add(snapshot.pool()));
        for (PoolId previous : java.util.List.copyOf(identities)) if (previous.authority() == established
                && (previous.kind() == PoolId.Kind.TEAM || personalOwner(previous) != null)) {
            PoolId replacement;
            do { replacement = PoolId.newNative(previous.kind()); } while (!identities.add(replacement));
            result.fallbackPools.put(previous, replacement);
        }
        solos.forEach((player, snapshot) -> result.solos.put(player, new MembershipSnapshot(
                Objects.requireNonNull(result.fallbackPools.get(snapshot.pool())), snapshot.members(),
                snapshot.permissions(), snapshot.membershipRevision(), snapshot.permissionRevision(), snapshot.recoveryRecipient())));
        return result;
    }

    public MembershipResolution resolveSolo(UUID player) {
        Objects.requireNonNull(player);
        if (established == null) established = MembershipAuthority.NATIVE;
        MembershipSnapshot snapshot = solos.computeIfAbsent(player, member -> new MembershipSnapshot(
                established == MembershipAuthority.NATIVE ? PoolId.newNative(PoolId.Kind.SOLO)
                        : new PoolId(established, PoolId.Kind.SOLO, member), List.of(member),
                Map.of(member, EnumSet.allOf(PoolCapability.class)), 1, 1));
        return new MembershipResolution(NATIVE_READY, Optional.of(snapshot));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        if (established != null) tag.putString("authority", established.name());
        CompoundTag records = new CompoundTag();
        new TreeMap<>(solos).forEach((player, snapshot) -> records.put(player.toString(), snapshot.save()));
        tag.put("solos", records);
        if (hasFallback()) {
            tag.putString("fallback_reason", fallbackReason);
            ListTag aliases = new ListTag();
            fallbackPools.entrySet().stream().sorted(java.util.Comparator.comparing(e -> e.getKey().toString())).forEach(entry -> {
                CompoundTag alias = new CompoundTag(); alias.put("from", entry.getKey().save()); alias.put("to", entry.getValue().save()); aliases.add(alias);
            });
            tag.put("fallback_pools", aliases);
        }
        return tag;
    }

    public static PoolMembershipState load(CompoundTag tag) {
        if (tag.getInt("version") != 1 || !tag.contains("solos", Tag.TAG_COMPOUND))
            throw new IllegalStateException("Unsupported membership metadata; recreate the development world");
        PoolMembershipState result = new PoolMembershipState();
        if (tag.contains("authority")) {
            if (!tag.contains("authority", Tag.TAG_STRING)) throw new IllegalStateException("Invalid authority");
            result.established = MembershipAuthority.valueOf(tag.getString("authority"));
        }
        CompoundTag records = tag.getCompound("solos");
        HashSet<PoolId> identities = new HashSet<>();
        for (String key : records.getAllKeys()) {
            UUID player = UUID.fromString(key);
            if (!records.contains(key, Tag.TAG_COMPOUND)) throw new IllegalStateException("Invalid solo record");
            MembershipSnapshot snapshot = MembershipSnapshot.load(records.getCompound(key));
            if (snapshot.pool().authority() != result.established
                    || snapshot.pool().kind() != PoolId.Kind.SOLO || !snapshot.members().equals(List.of(player))
                    || !identities.add(snapshot.pool()) || result.solos.put(player, snapshot) != null)
                throw new IllegalStateException("Conflicting saved solo identity or membership");
        }
        if (!result.solos.isEmpty() && result.established == null)
            throw new IllegalStateException("Saved memberships have no established authority");
        if (tag.contains("fallback_reason") || tag.contains("fallback_pools")) {
            if (result.established != MembershipAuthority.NATIVE || !tag.contains("fallback_reason", Tag.TAG_STRING)
                    || !tag.contains("fallback_pools", Tag.TAG_LIST)) throw new IllegalStateException("Invalid native fallback metadata");
            result.fallbackReason = tag.getString("fallback_reason");
            if (result.fallbackReason.isBlank() || result.fallbackReason.length() > 2048)
                throw new IllegalStateException("Invalid native fallback reason");
            ListTag aliases = tag.getList("fallback_pools", Tag.TAG_COMPOUND);
            if (!com.mistaboom.essence_ascendance.data.NbtVersionCompat.listHasElementType((ListTag) tag.get("fallback_pools"), Tag.TAG_COMPOUND))
                throw new IllegalStateException("Invalid native fallback pool list");
            java.util.Set<PoolId> targets = new java.util.HashSet<>();
            for (Tag raw : aliases) {
                CompoundTag alias = (CompoundTag) raw;
                PoolId from = PoolId.load(alias.getCompound("from")), to = PoolId.load(alias.getCompound("to"));
                if (from.authority() == MembershipAuthority.NATIVE || to.authority() != MembershipAuthority.NATIVE
                        || from.kind() != to.kind() || !targets.add(to) || result.fallbackPools.put(from, to) != null)
                    throw new IllegalStateException("Conflicting native fallback pool identity");
                if (to.kind() == PoolId.Kind.SOLO && result.personalOwner(to) == null)
                    throw new IllegalStateException("Native fallback solo identity has no owner");
            }
        }
        return result;
    }
}

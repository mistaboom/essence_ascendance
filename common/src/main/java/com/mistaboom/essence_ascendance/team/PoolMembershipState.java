package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import net.minecraft.nbt.CompoundTag;
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
    private MembershipAuthority established;

    public MembershipAuthority establishedAuthority() { return established; }
    public boolean contains(UUID player) { return solos.containsKey(player); }
    public UUID personalOwner(PoolId pool) {
        return solos.entrySet().stream().filter(e -> e.getValue().pool().equals(pool)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    /** Established authorities never silently fall back or switch financial worlds. */
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
        return result;
    }
}

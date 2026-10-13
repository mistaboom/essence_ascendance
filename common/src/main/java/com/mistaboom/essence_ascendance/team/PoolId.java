package com.mistaboom.essence_ascendance.team;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.UUID;

/** Stable namespaced identity. Native IDs are allocated and saved, never derived from placers or positions. */
public record PoolId(MembershipAuthority authority, Kind kind, UUID id) {
    public enum Kind { SOLO, TEAM }

    public PoolId {
        Objects.requireNonNull(authority);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(id);
    }

    public static PoolId newNative(Kind kind) {
        return new PoolId(MembershipAuthority.NATIVE, kind, UUID.randomUUID());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("authority", authority.name());
        tag.putString("kind", kind.name());
        tag.putUUID("id", id);
        return tag;
    }

    public static PoolId load(CompoundTag tag) {
        if (!tag.contains("authority", Tag.TAG_STRING) || !tag.contains("kind", Tag.TAG_STRING)
                || !tag.hasUUID("id")) throw new IllegalStateException("Incomplete saved pool identity");
        return new PoolId(MembershipAuthority.valueOf(tag.getString("authority")),
                Kind.valueOf(tag.getString("kind")), tag.getUUID("id"));
    }
}

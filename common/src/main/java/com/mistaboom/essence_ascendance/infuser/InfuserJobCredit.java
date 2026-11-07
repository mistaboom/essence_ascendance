package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import net.minecraft.nbt.CompoundTag;
import java.util.Objects;
import java.util.UUID;

/** Current operation attribution, not item ownership or lifetime item history. */
public record InfuserJobCredit(UUID actionId, UUID actor, UUID source, boolean observedSupply, boolean mixedSupply) {
    public InfuserJobCredit { Objects.requireNonNull(actionId); }

    public static InfuserJobCredit begin(UUID actor) {
        return new InfuserJobCredit(UUID.randomUUID(), actor, null, false, false);
    }

    /** A job drawing from more than one physical supply cannot assert a single source. */
    public InfuserJobCredit suppliedBy(UUID identity) {
        if (mixedSupply) return this;
        if (identity == null || observedSupply && !identity.equals(source))
            return new InfuserJobCredit(actionId, actor, null, true, true);
        return new InfuserJobCredit(actionId, actor, identity, true, false);
    }

    public InfuserJobCredit withoutActor() {
        return new InfuserJobCredit(actionId, null, source, observedSupply, mixedSupply);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        NbtVersionCompat.putUUID(tag, "action", actionId);
        if (actor != null) NbtVersionCompat.putUUID(tag, "actor", actor);
        if (source != null) NbtVersionCompat.putUUID(tag, "source", source);
        tag.putBoolean("observed_supply", observedSupply);
        tag.putBoolean("mixed_supply", mixedSupply);
        return tag;
    }

    /** Unknown or malformed provenance suppresses credit while preserving the gameplay job. */
    public static InfuserJobCredit load(CompoundTag tag) {
        if (NbtVersionCompat.getInt(tag, "version") != 1 || !NbtVersionCompat.hasUUID(tag, "action")) return null;
        boolean observed = NbtVersionCompat.getBoolean(tag, "observed_supply");
        boolean mixed = NbtVersionCompat.getBoolean(tag, "mixed_supply");
        UUID source = NbtVersionCompat.hasUUID(tag, "source") ? NbtVersionCompat.getUUID(tag, "source") : null;
        if (mixed && source != null || !observed && (source != null || mixed) || observed && !mixed && source == null) return null;
        return new InfuserJobCredit(NbtVersionCompat.getUUID(tag, "action"),
                NbtVersionCompat.hasUUID(tag, "actor") ? NbtVersionCompat.getUUID(tag, "actor") : null,
                source, observed, mixed);
    }
}

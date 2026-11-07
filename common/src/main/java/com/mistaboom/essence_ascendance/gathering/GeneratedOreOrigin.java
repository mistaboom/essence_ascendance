package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Compact positive worldgen proof on the existing ore block entity, bound to its original address. */
public final class GeneratedOreOrigin {
    public static final String TAG = "essence_generated_origin";
    private GeneratedOreOrigin() { }
    public static CompoundTag created(String dimension, BlockPos position) {
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putString("dimension", dimension); tag.putLong("position", position.asLong()); return tag;
    }
    public static boolean matches(CompoundTag tag, String dimension, BlockPos position) {
        return tag != null && NbtVersionCompat.contains(tag, "version", Tag.TAG_INT)
                && NbtVersionCompat.getInt(tag, "version") == 1
                && NbtVersionCompat.contains(tag, "dimension", Tag.TAG_STRING)
                && dimension.equals(NbtVersionCompat.getString(tag, "dimension"))
                && NbtVersionCompat.contains(tag, "position", Tag.TAG_LONG)
                && position.asLong() == NbtVersionCompat.getLong(tag, "position");
    }
    /** Discard copied current-version proof before it can be saved and copied back to its source address.
     * A detached block entity can validate its position before its level supplies the dimension.
     * Future versions remain opaque and untrusted by matches. */
    public static CompoundTag atAddress(CompoundTag tag, String dimension, BlockPos position) {
        if (tag == null || !NbtVersionCompat.contains(tag, "version", Tag.TAG_INT)
                || NbtVersionCompat.getInt(tag, "version") != 1) return tag;
        if (!NbtVersionCompat.contains(tag, "position", Tag.TAG_LONG)
                || position.asLong() != NbtVersionCompat.getLong(tag, "position")
                || !NbtVersionCompat.contains(tag, "dimension", Tag.TAG_STRING)
                || NbtVersionCompat.getString(tag, "dimension").isBlank()
                || dimension != null && !dimension.equals(NbtVersionCompat.getString(tag, "dimension"))) return null;
        return tag;
    }
}

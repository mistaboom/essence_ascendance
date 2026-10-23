package com.mistaboom.essence_ascendance.data;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/** Narrow bridge for the typed NBT accessors removed in 26.1.2. */
public final class NbtVersionCompat {
    // Vanilla's historical TAG_ANY_NUMERIC sentinel remains 99 even though
    // the named constant was removed from Tag in 26.1.2.
    private static final int ANY_NUMERIC = 99;
    private NbtVersionCompat() { }

    public static boolean contains(CompoundTag tag, String key, int type) {
        Tag value = tag.get(key);
        return value != null && (value.getId() == type
                || type == ANY_NUMERIC && value.getId() >= Tag.TAG_BYTE && value.getId() <= Tag.TAG_DOUBLE);
    }

    public static ListTag getList(CompoundTag tag, String key, int elementType) {
        Tag value = tag.get(key);
        if (!(value instanceof ListTag list)) return new ListTag();
        return listHasElementType(list, elementType) ? list : new ListTag();
    }

    public static CompoundTag getCompound(CompoundTag tag, String key) {
        //? if >=26.1.2
        /*return tag.getCompoundOrEmpty(key);*/
        //? if <26.1.2
        return tag.getCompound(key);
    }

    public static String getString(CompoundTag tag, String key) {
        //? if >=26.1.2
        /*return tag.getStringOr(key, "");*/
        //? if <26.1.2
        return tag.getString(key);
    }

    public static int getInt(CompoundTag tag, String key) {
        //? if >=26.1.2
        /*return tag.getIntOr(key, 0);*/
        //? if <26.1.2
        return tag.getInt(key);
    }

    public static long getLong(CompoundTag tag, String key) {
        //? if >=26.1.2
        /*return tag.getLongOr(key, 0L);*/
        //? if <26.1.2
        return tag.getLong(key);
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        //? if >=26.1.2
        /*return tag.getBooleanOr(key, false);*/
        //? if <26.1.2
        return tag.getBoolean(key);
    }

    public static long[] getLongArray(CompoundTag tag, String key) {
        //? if >=26.1.2
        /*return tag.getLongArray(key).orElseGet(() -> new long[0]);*/
        //? if <26.1.2
        return tag.getLongArray(key);
    }

    public static boolean listHasElementType(ListTag list, int type) {
        // Newer ListTag permits mixed elements. The legacy typed getter and
        // strict settlement validation require the entire list to match.
        for (Tag entry : list) {
            if (entry.getId() != type) return false;
        }
        return true;
    }

    public static void putUUID(CompoundTag tag, String key, UUID value) {
        tag.put(key, new IntArrayTag(UUIDUtil.uuidToIntArray(value)));
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        Tag value = tag.get(key);
        return value instanceof IntArrayTag array && array.getAsIntArray().length == 4;
    }

    public static UUID getUUID(CompoundTag tag, String key) {
        if (!hasUUID(tag, key)) throw new IllegalArgumentException("Invalid UUID at " + key);
        return UUIDUtil.uuidFromIntArray(((IntArrayTag) tag.get(key)).getAsIntArray());
    }
}

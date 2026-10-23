package com.mistaboom.essence_ascendance.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/** Verifies the old typed-NBT contract that saved membership and machines rely on. */
public final class NbtVersionCompatTest {
    private static int checks;

    public static void main(String[] args) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("count", 7);
        tag.putString("name", "seven");
        check(NbtVersionCompat.contains(tag, "count", Tag.TAG_INT), "exact numeric type");
        check(NbtVersionCompat.contains(tag, "count", 99), "any numeric type");
        check(!NbtVersionCompat.contains(tag, "name", 99), "string is not numeric");
        check(!NbtVersionCompat.contains(tag, "count", Tag.TAG_LONG), "wrong numeric type");
        check(!NbtVersionCompat.contains(tag, "missing", Tag.TAG_INT), "missing field");

        ListTag names = new ListTag();
        names.add(StringTag.valueOf("alpha"));
        tag.put("names", names);
        check(NbtVersionCompat.getList(tag, "names", Tag.TAG_STRING).size() == 1, "matching list type");
        check(NbtVersionCompat.getList(tag, "names", Tag.TAG_COMPOUND).isEmpty(), "wrong list type");
        check(NbtVersionCompat.getList(tag, "missing", Tag.TAG_STRING).isEmpty(), "missing list");
        check(NbtVersionCompat.listHasElementType(new ListTag(), Tag.TAG_COMPOUND), "empty list has no conflicting type");
        names.add(StringTag.valueOf("beta"));
        check(NbtVersionCompat.listHasElementType(names, Tag.TAG_STRING), "every matching element accepted");
        check(NbtVersionCompat.getList(tag, "names", Tag.TAG_STRING) == names, "valid typed list keeps its contents");
        //? if >=26.1.2 {
        /*ListTag mixed = new ListTag();
        mixed.add(new CompoundTag());
        mixed.add(StringTag.valueOf("not a settlement record"));
        tag.put("mixed", mixed);
        check(!NbtVersionCompat.listHasElementType(mixed, Tag.TAG_COMPOUND), "later mixed entry fails strict validation");
        check(NbtVersionCompat.getList(tag, "mixed", Tag.TAG_COMPOUND).isEmpty(), "typed getter rejects mixed list");
        check(mixed.size() == 2, "validation leaves malformed source unchanged");
        *///? }

        UUID owner = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        NbtVersionCompat.putUUID(tag, "owner", owner);
        check(NbtVersionCompat.hasUUID(tag, "owner"), "UUID tag present");
        check(owner.equals(NbtVersionCompat.getUUID(tag, "owner")), "UUID round trip");
        tag.putIntArray("owner", new int[]{1, 2, 3});
        check(!NbtVersionCompat.hasUUID(tag, "owner"), "short UUID rejected");
        boolean rejected = false;
        try { NbtVersionCompat.getUUID(tag, "owner"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "malformed UUID read rejected");
        System.out.println("NbtVersionCompatTest: " + checks + " assertions passed");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
}

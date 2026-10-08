package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.JsonParser;
import net.minecraft.nbt.*;
import java.util.List;

public final class NativeVillagerAccessTest {
    private static int checks;
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var entity = new CompoundTag(); entity.putString("id", "minecraft:villager"); entity.putInt("Age", 0); entity.putInt("Xp", 0); entity.putFloat("Health", 20);
        var data = new CompoundTag(); data.putString("profession", "minecraft:none"); data.putString("type", "minecraft:plains"); data.putInt("level", 1); entity.put("VillagerData", data);
        check(NativeVillagerAccess.adultUntraded(entity), "Typed healthy adult unemployed villager rejected");
        for (String kind : List.of("minecraft:zombie_villager", "fixture:villager", "minecraft:armor_stand")) {
            var copy = entity.copy(); copy.putString("id", kind); check(!NativeVillagerAccess.adultUntraded(copy), "Non-villager inferred from template name");
        }
        for (int age : List.of(-1, -24000, 1)) { var copy = entity.copy(); copy.putInt("Age", age); check(!NativeVillagerAccess.adultUntraded(copy), "Baby or cooldown context admitted as fresh adult"); }
        for (String key : List.of("Age", "Xp", "Health")) { var copy = entity.copy(); copy.remove(key); check(!NativeVillagerAccess.adultUntraded(copy), "Missing required native entity state admitted"); }
        for (String key : List.of("NoAI", "NoGravity", "Invulnerable")) { var copy = entity.copy(); copy.putBoolean(key, true); check(!NativeVillagerAccess.adultUntraded(copy), "Unsupported NPC behavior admitted"); }
        var copy = entity.copy(); copy.putInt("Xp", 1); check(!NativeVillagerAccess.adultUntraded(copy), "Traded villager treated as profession-resettable");
        copy = entity.copy(); copy.put("Offers", new CompoundTag()); check(!NativeVillagerAccess.adultUntraded(copy), "Preconfigured offers replaced by sampled table");
        copy = entity.copy(); copy.getCompound("VillagerData").putString("profession", "minecraft:nitwit"); check(!NativeVillagerAccess.adultUntraded(copy), "Nitwit can be assigned a job");
        copy = entity.copy(); copy.getCompound("VillagerData").remove("type"); check(!NativeVillagerAccess.adultUntraded(copy), "Missing type inferred as plains");
        copy = entity.copy(); copy.putFloat("Health", Float.NaN); check(!NativeVillagerAccess.adultUntraded(copy), "Nonfinite health certified a surviving trader");
        var placement = JsonParser.parseString("{\"type\":\"minecraft:random_spread\",\"spacing\":32,\"separation\":8}").getAsJsonObject();
        check(NativeVillagerAccess.placement(placement), "Native positive random spread rejected");
        placement.addProperty("frequency", 0); check(!NativeVillagerAccess.placement(placement), "Disabled placement became NPC supply");
        placement.remove("frequency"); placement.add("exclusion_zone", new com.google.gson.JsonObject()); check(!NativeVillagerAccess.placement(placement), "Unresolved placement exclusion ignored");
        placement.remove("exclusion_zone"); placement.addProperty("type", "fixture:placement"); check(!NativeVillagerAccess.placement(placement), "Custom placement treated as native");
        var template = new CompoundTag(); var size = new ListTag(); size.add(IntTag.valueOf(1)); size.add(IntTag.valueOf(3)); size.add(IntTag.valueOf(1)); template.put("size", size);
        var info = new CompoundTag(); var block = new ListTag(); block.add(IntTag.valueOf(0)); block.add(IntTag.valueOf(1)); block.add(IntTag.valueOf(0)); info.put("blockPos", block);
        var pos = new ListTag(); pos.add(DoubleTag.valueOf(.5)); pos.add(DoubleTag.valueOf(1)); pos.add(DoubleTag.valueOf(.5)); info.put("pos", pos);
        check(NativeVillagerAccess.insideEntity(template, info), "Contained entity rejected");
        pos.set(0, DoubleTag.valueOf(100)); check(!NativeVillagerAccess.insideEntity(template, info), "Entity outside placed piece admitted");
        pos.set(0, DoubleTag.valueOf(Double.NaN)); check(!NativeVillagerAccess.insideEntity(template, info), "Nonfinite entity position admitted");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("NativeVillagerAccessTest: " + checks + " checks PASS");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}

package com.mistaboom.essence_ascendance.balance.quest;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Audited read-only public API calls; never constructs/loads a quest service or calls custom behavior. */
final class FtbQuestReader {
    private static final String ROOT = "dev.ftb.mods.ftbquests.quest.";
    private FtbQuestReader() { }
    static ProviderReadiness readiness(MinecraftServer server, Path folder, String version) {
        Object file = instance();
        return FtbQuestProvider.definitionReadiness(version, file == null || field(file, "server") == server,
                file != null && (boolean) call(file, "isLoading"), file != null && call(file, "getFolder") != null,
                Files.isRegularFile(folder.resolve("data.snbt")));
    }
    static QuestEvidence capture(MinecraftServer server, Path folder, String version) {
        BalancePerformance.increment("quest_definition_normalizations");
        CompoundTag data; List<CompoundTag> chapters = new ArrayList<>(), tables = new ArrayList<>(); String provenance;
        Object file = instance();
        if (file != null && call(file, "getFolder") != null) {
            if (field(file, "server") != server || (boolean) call(file, "isLoading")) throw new IllegalStateException("Quest service not ready");
            data = write(file, server.registryAccess()); data.putInt("version", 13);
            for (Object chapter : collection(call(file, "getAllChapters"))) {
                CompoundTag c = write(chapter, server.registryAccess()); ListTag quests = new ListTag();
                for (Object quest : collection(call(chapter, "getQuests"))) {
                    CompoundTag q = write(quest, server.registryAccess());
                    // Effective inherited booleans/modes are public getters, not current team state.
                    q.putBoolean("can_repeat", (boolean) call(quest, "canBeRepeated"));
                    q.putString("progression_mode", call(quest, "getProgressionMode").toString().toLowerCase(Locale.ROOT));
                    ListTag tasks = new ListTag(), rewards = new ListTag();
                    for (Object task : collection(call(quest, "getTasks"))) tasks.add(definition(task, server.registryAccess(), true));
                    for (Object reward : collection(call(quest, "getRewards"))) rewards.add(definition(reward, server.registryAccess(), false));
                    q.put("tasks", tasks); q.put("rewards", rewards); quests.add(q);
                }
                c.put("quests", quests); chapters.add(c);
            }
            for (Object table : collection(call(file, "getRewardTables"))) tables.add(table(table, server.registryAccess()));
            provenance = "FTB Quests " + version + " effective ServerQuestFile public definition API; no player/team access";
        } else {
            return captureDefinitions(folder, version);
        }
        QuestEvidence result = QuestNormalizer.normalize(data, chapters, tables, provenance);
        BalancePerformance.count("quest_definitions", result.quests().size());
        BalancePerformance.count("quest_tasks", result.quests().stream().mapToLong(q -> q.tasks().size()).sum());
        BalancePerformance.count("quest_reward_leaves", result.quests().stream().mapToLong(q -> q.rewards().size()).sum());
        return result;
    }
    private static CompoundTag definition(Object object, HolderLookup.Provider lookup, boolean task) {
        String name = object.getClass().getName();
        Map<String, String> supported = task ? Map.of(ROOT + "task.ItemTask", "item", ROOT + "task.CheckmarkTask", "checkmark")
                : Map.of(ROOT + "reward.ItemReward", "item", ROOT + "reward.ChoiceReward", "choice", ROOT + "reward.RandomReward", "random");
        CompoundTag tag;
        if (supported.containsKey(name)) {
            tag = !task && !name.endsWith("ItemReward") ? new CompoundTag() : write(object, lookup);
            tag.putString("type", supported.get(name));
            if (task && name.endsWith("ItemTask")) {
                tag.putBoolean("consume_items", (boolean) call(object, "consumesResources"));
                tag.putBoolean("only_from_crafting", (boolean) call(object, "isOnlyFromCrafting"));
            }
            if (!task) {
                tag.putBoolean("team_reward", (boolean) call(object, "isTeamReward"));
                if (!name.endsWith("ItemReward")) {
                    // getTable() clears invalid references: inspect the exact audited field
                    // rather than mutating a broken book while collecting diagnostics.
                    Object table = declaredFieldFrom(object, ROOT + "reward.RandomReward", "table");
                    if (table != null && (boolean) call(table, "isValid")) tag.put("table_data", table(table, lookup));
                }
            }
        } else { tag = new CompoundTag(); tag.putString("type", "opaque:" + name); }
        tag.putString("id", call(object, "getCodeString").toString()); return tag;
    }
    private static CompoundTag table(Object table, HolderLookup.Provider lookup) {
        // RewardTable.writeData dispatches nested custom reward writers. Do not call it.
        CompoundTag tag = new CompoundTag(); tag.putString("id", call(table, "getCodeString").toString());
        // No public loot-size getter: preserve audited scalar fields with an exact class guard.
        if (!table.getClass().getName().equals(ROOT + "loot.RewardTable")) throw new IllegalStateException("Unsupported table class");
        tag.putInt("loot_size", (int) declaredField(table, "lootSize")); ListTag rewards = new ListTag();
        for (Object weighted : collection(call(table, "getWeightedRewards"))) {
            Object reward = call(weighted, "getReward");
            // Nested table rewards are opaque here: prevents recursive getters/custom writers.
            CompoundTag r = reward.getClass().getName().equals(ROOT + "reward.ItemReward")
                    ? definition(reward, lookup, false) : opaqueReward(reward);
            r.putFloat("weight", ((Number) call(weighted, "getWeight")).floatValue()); rewards.add(r);
        }
        tag.put("rewards", rewards); return tag;
    }
    private static CompoundTag opaqueReward(Object reward) {
        CompoundTag tag = new CompoundTag(); tag.putString("id", call(reward, "getCodeString").toString());
        tag.putString("type", "opaque:" + reward.getClass().getName()); return tag;
    }
    private static CompoundTag write(Object object, HolderLookup.Provider lookup) {
        CompoundTag tag = new CompoundTag(); invoke(object, "writeData", new Class<?>[]{CompoundTag.class, HolderLookup.Provider.class}, tag, lookup);
        if (!tag.contains("id")) tag.putString("id", call(object, "getCodeString").toString()); return tag;
    }
    private static List<CompoundTag> readDirectory(Path folder) {
        if (!Files.exists(folder)) return List.of();
        try (var files = Files.list(folder)) { return files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".snbt")).sorted().map(FtbQuestReader::read).toList(); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Cannot enumerate quest definitions " + folder, failure); }
    }
    static QuestEvidence captureDefinitions(Path folder, String version) {
        var result = QuestNormalizer.normalize(read(folder.resolve("data.snbt")), readDirectory(folder.resolve("chapters")),
                readDirectory(folder.resolve("reward_tables")),
                "FTB Quests " + version + " format-13 authoritative config/ftbquests/quests load source before SERVER_STARTED; definition-only native SNBT.tryRead");
        BalancePerformance.count("quest_definitions", result.quests().size());
        BalancePerformance.count("quest_tasks", result.quests().stream().mapToLong(q -> q.tasks().size()).sum());
        BalancePerformance.count("quest_reward_leaves", result.quests().stream().mapToLong(q -> q.rewards().size()).sum());
        return result;
    }
    private static CompoundTag read(Path path) {
        try {
            Object result = Class.forName("dev.ftb.mods.ftblibrary.snbt.SNBT").getMethod("tryRead", Path.class).invoke(null, path);
            if (!(result instanceof CompoundTag tag)) throw new IllegalStateException("Missing/broken quest definition " + path);
            return tag;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot read authoritative quest definition " + path, failure); }
    }
    private static Object instance() {
        try { return Class.forName(ROOT + "ServerQuestFile").getField("INSTANCE").get(null); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Audited quest API unavailable", failure); }
    }
    private static Object call(Object target, String method) { return invoke(target, method, new Class<?>[0]); }
    private static Object invoke(Object target, String method, Class<?>[] types, Object... args) {
        try { return target.getClass().getMethod(method, types).invoke(target, args); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Audited definition API failed: " + method, failure); }
    }
    private static Object field(Object object, String name) {
        try { return object.getClass().getField(name).get(object); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Audited field " + name + " unavailable", failure); }
    }
    private static Object declaredField(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Audited scalar field " + name + " unavailable", failure); }
    }
    private static Object declaredFieldFrom(Object object, String owner, String name) {
        try { Field field = Class.forName(owner).getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Audited definition field " + owner + "/" + name + " unavailable", failure); }
    }
    private static Collection<?> collection(Object value) { return (Collection<?>) value; }
}

package com.mistaboom.essence_ascendance.balance.quest;

import net.minecraft.nbt.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Audited FTB Quests format-13 definition projection. No optional linkage. */
public final class QuestNormalizer {
    private QuestNormalizer() { }
    public static QuestEvidence normalize(CompoundTag data, List<CompoundTag> chapters,
                                          List<CompoundTag> tables, String provenance) {
        if (data.getInt("version") != 13) throw new IllegalArgumentException("Unsupported FTB quest definition format: " + data.getInt("version"));
        Map<String, CompoundTag> tableMap = new TreeMap<>();
        for (var table : tables) if (tableMap.put(id(table), table) != null) throw new IllegalArgumentException("Duplicate reward table ID");
        List<QuestEvidence.Quest> out = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (var chapter : chapters) {
            boolean consume = tri(chapter, "consume_items", data.getBoolean("default_consume_items"));
            boolean repeat = chapter.getBoolean("default_repeatable_quest");
            String mode = text(chapter, "progression_mode", text(data, "progression_mode", "linear"));
            if (mode.equals("default")) mode = text(data, "progression_mode", "linear");
            for (Tag qt : list(chapter, "quests", Tag.TAG_COMPOUND)) {
                CompoundTag q = (CompoundTag) qt; String qid = id(q);
                if (!ids.add(qid)) throw new IllegalArgumentException("Duplicate quest ID " + qid);
                List<String> deps = new ArrayList<>(), unresolved = new ArrayList<>();
                for (Tag dep : list(q, "dependencies", Tag.TAG_STRING)) deps.add(code(dep.getAsString()));
                String dependency = text(q, "dependency_requirement", "all_completed");
                int minimum = q.getInt("min_required_dependencies");
                int required = minimum > 0 ? minimum : dependency.startsWith("one_") ? 1 : deps.size();
                String effectiveMode = text(q, "progression_mode", mode);
                if (effectiveMode.equals("default")) effectiveMode = mode;
                boolean enforced = !effectiveMode.equals("flexible");
                if (!List.of("linear", "flexible").contains(effectiveMode)) unresolved.add("Unknown progression mode " + effectiveMode);
                if (enforced && !List.of("all_completed", "one_completed").contains(dependency))
                    unresolved.add("Started-state dependency semantics not yet supported: " + dependency);
                if (q.getInt("max_completable_deps") > 0) unresolved.add("Exclusive questline dependency condition");
                List<QuestEvidence.Task> tasks = new ArrayList<>();
                for (Tag tt : list(q, "tasks", Tag.TAG_COMPOUND)) {
                    CompoundTag t = (CompoundTag) tt; String type = text(t, "type", "item");
                    List<String> unknown = new ArrayList<>(); List<String> items = new ArrayList<>();
                    long count = t.contains("count") ? t.getLong("count") : 1;
                    boolean consumed = tri(t, "consume_items", consume);
                    if (type.equals("item")) {
                        String item = item(t.get("item")); if (item != null) items.add(item); else unknown.add("Missing/unregistered item or unresolved filter target");
                        if (tri(t, "only_from_crafting", false)) unknown.add("Craft-event requirement; ordinary possession does not establish it");
                        String matching = text(t, "match_components", "none");
                        if (!matching.equals("none") || hasComponents(t.get("item"))) unknown.add("Component/filter matching needs variant-specific acquisition evidence");
                        if (item != null && (item.startsWith("ftbfiltersystem:") || item.startsWith("itemfilters:"))) unknown.add("Filter semantics unresolved");
                    } else if (!type.equals("checkmark")) unknown.add("Opaque task type " + type + "; target/quantity retained in predicate");
                    if (count <= 0) unknown.add("Nonpositive task quantity");
                    if (count > 9_007_199_254_740_992L) unknown.add("Task quantity cannot be represented exactly by shared production accounting");
                    tasks.add(new QuestEvidence.Task(id(t), type, items, count, consumed, unknown.isEmpty(), t.toString(), unknown));
                }
                List<QuestEvidence.Reward> rewards = new ArrayList<>();
                if (tri(q, "require_sequential_tasks", chapter.getBoolean("require_sequential_tasks")) && tasks.stream().anyMatch(QuestEvidence.Task::consumed))
                    unresolved.add("Sequential consumed submissions need order-sensitive finite-stock accounting");
                for (Tag rt : list(q, "rewards", Tag.TAG_COMPOUND)) {
                    CompoundTag r = (CompoundTag) rt;
                    String scope = tri(r, "team_reward", tri(chapter, "team_reward", data.getBoolean("default_reward_team"))) ? "team" : "player";
                    rewards.addAll(rewards(r, tableMap, scope, id(r), "", 1, new HashSet<>(), 0));
                }
                out.add(new QuestEvidence.Quest(qid, deps, required, enforced, dependency,
                        tri(q, "can_repeat", repeat), Math.max(0, q.getInt("repeat_cooldown")), q.getBoolean("optional"), tasks, rewards, unresolved));
            }
        }
        Set<String> questIds = new HashSet<>(); out.forEach(q -> questIds.add(q.id()));
        List<QuestEvidence.Quest> validated = new ArrayList<>();
        for (var q : out) {
            List<String> unresolved = new ArrayList<>(q.unresolved());
            q.prerequisites().stream().filter(dep -> !questIds.contains(dep)).forEach(dep -> unresolved.add("Broken/non-quest prerequisite " + dep));
            validated.add(new QuestEvidence.Quest(q.id(), q.prerequisites(), q.required(), q.enforced(), q.dependencyMode(),
                    q.repeatable(), q.cooldownSeconds(), q.optional(), q.tasks(), q.rewards(), unresolved));
        }
        return new QuestEvidence(validated.stream().sorted(Comparator.comparing(QuestEvidence.Quest::id)).toList(), provenance,
                List.of("Task mentions and visual ordering are suggested progression, not global item gates",
                        "Availability is marginal opportunity; finite stock, random success, variants and unsupported conditions remain explicit",
                        "No verified external crafting/use gate is exposed by this adapter"));
    }
    private static List<QuestEvidence.Reward> rewards(CompoundTag r, Map<String, CompoundTag> tables, String scope,
            String sourceId, String group, double probability, Set<String> visiting, int depth) {
        String type = text(r, "type", "item"); List<String> unknown = new ArrayList<>();
        if (type.equals("item")) {
            String item = item(r.get("item")); long count = r.getInt("count");
            if (count == 0) count = r.get("item") instanceof CompoundTag stack && stack.contains("count") ? stack.getInt("count") : 1;
            if (item == null) unknown.add("Missing/unregistered reward item");
            if (r.getInt("random_bonus") != 0 || r.getBoolean("only_one")) unknown.add("Random bonus/only-one inventory condition");
            if (hasComponents(r.get("item"))) unknown.add("Reward component variant retained; default item capability is not proven for this variant");
            return List.of(new QuestEvidence.Reward(sourceId, group.isEmpty() ? type : group.startsWith("choice/") ? "choice" : "random",
                    item, count, probability, group, scope, r.toString(), unknown));
        }
        if (!type.equals("choice") && !type.equals("random")) unknown.add("Opaque reward type " + type);
        else if (depth >= 16) unknown.add("Nested reward table depth exceeds supported bound");
        else {
            CompoundTag table = r.contains("table_data", Tag.TAG_COMPOUND) ? r.getCompound("table_data") : tables.get(code(text(r, "table", "0")));
            String key = text(r, "table", sourceId);
            if (table == null) unknown.add("Broken reward table " + key);
            else if (!visiting.add(key)) unknown.add("Reward table cycle " + key);
            else {
                List<QuestEvidence.Reward> leaves = new ArrayList<>();
                var entries = list(table, "rewards", Tag.TAG_COMPOUND);
                double total = entries.stream().mapToDouble(t -> weight((CompoundTag) t)).filter(w -> w > 0).sum();
                int size = table.contains("loot_size") ? table.getInt("loot_size") : 1;
                String exclusive = group.isEmpty() ? type + "/" + sourceId : group;
                for (Tag entry : entries) {
                    CompoundTag child = (CompoundTag) entry; double w = weight(child);
                    // Nonpositive table entries are unconditional random rewards in the native table.
                    double p = type.equals("choice") ? probability : total <= 0 ? 0 : w <= 0 ? probability : probability * w / total;
                    for (var leaf : rewards(child, tables, scope, sourceId + "/" + id(child), exclusive, p, visiting, depth + 1)) {
                        List<String> issues = new ArrayList<>(leaf.unresolved());
                        if (type.equals("random") && size != 1) issues.add("Multiple correlated random draws; loot_size=" + size);
                        leaves.add(new QuestEvidence.Reward(leaf.id(), leaf.type(), leaf.item(), leaf.count(), leaf.probability(),
                                leaf.group(), leaf.scope(), leaf.predicate(), issues));
                    }
                }
                visiting.remove(key); if (!leaves.isEmpty()) return leaves;
                unknown.add("Empty reward table");
            }
        }
        return List.of(new QuestEvidence.Reward(sourceId, type, null, 0, 0, group, scope, r.toString(), unknown));
    }
    private static double weight(CompoundTag t) { return t.contains("weight") ? t.getDouble("weight") : 1; }
    private static ListTag list(CompoundTag tag, String key, int elementType) {
        if (!tag.contains(key)) return new ListTag();
        if (!(tag.get(key) instanceof ListTag list) || !list.isEmpty() && list.getElementType() != elementType)
            throw new IllegalArgumentException("Invalid quest definition list " + key + "; not authoritative empty data");
        return list;
    }
    private static boolean hasComponents(Tag t) { return t instanceof CompoundTag c && (c.contains("components") || c.contains("tag")); }
    private static String item(Tag tag) {
        String id = tag instanceof StringTag ? tag.getAsString() : tag instanceof CompoundTag c ? c.getString("id") : "";
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.ITEM.containsKey(key) && !id.equals("minecraft:air") ? key.toString() : null;
    }
    private static String id(CompoundTag tag) { return code(tag.getString("id")); }
    private static String code(String id) {
        if (!id.matches("[0-9a-fA-F]{1,16}")) throw new IllegalArgumentException("Invalid quest object ID " + id);
        return String.format(Locale.ROOT, "%016x", Long.parseUnsignedLong(id, 16));
    }
    private static String text(CompoundTag tag, String key, String fallback) { return tag.contains(key) ? tag.getString(key) : fallback; }
    private static boolean tri(CompoundTag tag, String key, boolean fallback) {
        if (!tag.contains(key)) return fallback;
        if (tag.get(key) instanceof StringTag) {
            String value = tag.getString(key); if (value.equals("default")) return fallback;
            if (value.equals("true")) return true; if (value.equals("false")) return false;
            throw new IllegalArgumentException("Invalid tristate " + key + "=" + value);
        }
        return tag.getBoolean(key);
    }
}

package com.mistaboom.essence_ascendance.balance.quest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import java.util.*;

/** Definition evidence only: no team, completion state, titles, icons or capability measurements. */
public record QuestEvidence(List<Quest> quests, String provenance, List<String> limitations) {
    private static final Gson JSON = new Gson();
    public static final QuestEvidence EMPTY = new QuestEvidence(List.of(), "FTB Quests absent/disabled", List.of());
    public QuestEvidence { quests = List.copyOf(quests); limitations = List.copyOf(limitations); }
    public record Task(String id, String type, List<String> items, long count, boolean consumed,
                       boolean supported, String predicate, List<String> unresolved) {
        public Task { items = List.copyOf(items); unresolved = List.copyOf(unresolved); }
    }
    public record Reward(String id, String type, String item, long count, double probability,
                         String group, String scope, String predicate, List<String> unresolved) {
        public Reward { unresolved = List.copyOf(unresolved); }
        public boolean supported() { return item != null && count > 0 && probability > 0 && unresolved.isEmpty(); }
    }
    public record Quest(String id, List<String> prerequisites, int required, boolean enforced,
                        String dependencyMode, boolean repeatable, int cooldownSeconds, boolean optional,
                        List<Task> tasks, List<Reward> rewards, List<String> unresolved) {
        public Quest { prerequisites = List.copyOf(prerequisites); tasks = List.copyOf(tasks);
            rewards = List.copyOf(rewards); unresolved = List.copyOf(unresolved); }
    }
    public JsonObject diagnostics() {
        JsonObject out = JSON.toJsonTree(this).getAsJsonObject();
        out.addProperty("model", "quest-definitions-1; availability, not capability strength");
        out.addProperty("questCount", quests.size());
        out.addProperty("taskCount", quests.stream().mapToLong(q -> q.tasks.size()).sum());
        out.addProperty("rewardCount", quests.stream().mapToLong(q -> q.rewards.size()).sum());
        out.addProperty("gateScope", "Enforced dependencies gate quest rewards only; no crafting/use gates inferred");
        out.addProperty("activity", "Player activity; no online-population multiplier or passive rate");
        return out;
    }
    /** Stable economic facts, independent of optional/default SNBT serialization.
     * Raw predicates, capture provenance and opaque definitions remain in diagnostics().
     * Unsupported classes expose no invented quantities or inferred behavior here. */
    private static JsonObject semanticDefinition(Quest quest) {
        JsonObject out = JSON.toJsonTree(quest).getAsJsonObject();
        var tasks = new com.google.gson.JsonArray();
        for (Task task : quest.tasks) {
            if (!Set.of("item", "checkmark").contains(task.type)) tasks.add(opaque(task.id));
            else {
                JsonObject value = JSON.toJsonTree(task).getAsJsonObject();
                value.remove("predicate"); tasks.add(value);
            }
        }
        var rewards = new com.google.gson.JsonArray();
        quest.rewards.forEach(reward -> rewards.add(semanticReward(reward)));
        out.add("tasks", tasks); out.add("rewards", rewards);
        out.addProperty("raw_definition", "metadata.generation.quests/" + quest.id);
        return out;
    }
    private static JsonObject semanticReward(Reward reward) {
        if (!Set.of("item", "choice", "random").contains(reward.type)) return opaque(reward.id);
        JsonObject out = JSON.toJsonTree(reward).getAsJsonObject();
        out.remove("predicate"); return out;
    }
    private static JsonObject opaque(String id) {
        JsonObject out = new JsonObject(); out.addProperty("id", id);
        out.addProperty("type", "opaque"); out.addProperty("supported", false);
        out.addProperty("limitation", "Behavior unresolved; original type/predicate retained in quest diagnostics");
        return out;
    }
    /** Shared production observations retain exact economy semantics without inventing hard constraints. */
    public List<ProductionGraph.Process> processes() {
        List<ProductionGraph.Process> out = new ArrayList<>();
        Map<String, String> definitions = new HashMap<>();
        quests.forEach(q -> definitions.put(q.id, JSON.toJson(semanticDefinition(q))));
        for (Quest q : quests) for (Reward r : q.rewards) if (r.item != null && r.count > 0 && r.probability > 0) {
            Map<String, String> m = new TreeMap<>();
            m.put("quest_id", q.id); m.put("reward_id", r.id); m.put("evidence_category", "reward_acquisition");
            m.put("reward_semantics", r.type); m.put("exclusive_group", r.group); m.put("scope", r.scope);
            m.put("prerequisites", JSON.toJson(q.prerequisites)); m.put("required_prerequisites", "" + q.required);
            m.put("dependency_mode", q.dependencyMode); m.put("dependencies_enforced", "" + q.enforced);
            m.put("repeatable", "" + q.repeatable); m.put("cooldown_seconds", "" + q.cooldownSeconds);
            m.put("renewability", q.repeatable ? "renewable" : "finite"); m.put("operation", "manual");
            m.put("player_activity", "tasks and reward claim; repeat resets task requirements; no measured rate");
            m.put("effective_definition", definitions.get(q.id)); m.put("output_predicate", JSON.toJson(semanticReward(r)));
            m.put("source_provenance", "Effective FTB format-13 definition; capture provenance/raw predicates in metadata.generation.quests"); m.put("acquisition_complete", "false");
            // Finite, exclusive and random supply is not an unrestricted conversion; the
            // conservation solver must not sum choices or erase a one-time free reward.
            m.put("conservation_complete", "false"); m.put("duration_known", "false");
            m.put("unresolved", "Finite inventory/choice correlation, joint prerequisite costs and activity/rate are not material-only conversion constraints");
            List<ProductionGraph.Input> inputs = q.tasks.stream().filter(t -> t.supported && !t.items.isEmpty())
                    .map(t -> new ProductionGraph.Input(t.items, t.count, t.consumed)).toList();
            out.add(new ProductionGraph.Process("essence_ascendance:quest/" + q.id + "/" + r.id,
                    "ftbquests:reward", inputs, List.of(new ProductionGraph.Output(r.item, r.count, r.probability, false)),
                    0, 0, "ftbquests", r.supported() ? .85 : .25, m));
        }
        for (Quest q : quests) if (q.repeatable && q.unresolved.isEmpty() && q.tasks.stream().allMatch(Task::supported)
                && !q.rewards.isEmpty() && q.rewards.stream().allMatch(r -> r.supported() && r.type.equals("item") && r.scope.equals("team"))) {
            Map<String, String> m = new TreeMap<>();
            m.put("quest_id", q.id); m.put("quest_economy_bundle", "true"); m.put("reward_semantics", "repeatable_deterministic_team_bundle");
            m.put("repeatable", "true");
            m.put("scope", "team"); m.put("operation", "manual"); m.put("renewability", "renewable");
            m.put("source_provenance", "Effective FTB format-13 definition; capture provenance/raw predicates in metadata.generation.quests"); m.put("player_activity", "Complete all reset tasks and claim the team rewards; no passive generation");
            m.put("cooldown_seconds", "" + q.cooldownSeconds); m.put("prerequisites", JSON.toJson(q.prerequisites));
            m.put("effective_definition", definitions.get(q.id)); m.put("acquisition_complete", "false");
            m.put("conservation_complete", "true"); m.put("duration_known", "false");
            List<ProductionGraph.Input> inputs = q.tasks.stream().filter(t -> !t.items.isEmpty())
                    .map(t -> new ProductionGraph.Input(t.items, t.count, t.consumed)).toList();
            if (inputs.stream().noneMatch(ProductionGraph.Input::consumed))
                m.put("production_constraint", com.mistaboom.essence_ascendance.balance.economy.BoundedProductionPolicy.NATIVE_RESOURCE_SOURCE);
            out.add(new ProductionGraph.Process("essence_ascendance:quest-economy/" + q.id, "ftbquests:repeatable_reward_bundle", inputs,
                    q.rewards.stream().map(r -> new ProductionGraph.Output(r.item, r.count, 1, false)).toList(),
                    0, 0, "ftbquests", .85, m));
        }
        return List.copyOf(out);
    }
}

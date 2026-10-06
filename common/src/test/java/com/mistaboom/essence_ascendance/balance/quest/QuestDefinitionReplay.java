package com.mistaboom.essence_ascendance.balance.quest;

import com.google.gson.GsonBuilder;
import com.mistaboom.essence_ascendance.balance.engine.AcquisitionProgressionGraph;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import java.nio.file.*;
import java.util.*;

/** Opt-in definition-only real-file replay. Registry is vanilla: this is not native pack acceptance. */
public final class QuestDefinitionReplay {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        Path source = Path.of(args[0]), output = Path.of(args[1]); Files.createDirectories(output);
        String version = args[2];
        Map<String, AcquisitionProgressionGraph.Placement> seeds = new TreeMap<>();
        BuiltInRegistries.ITEM.keySet().forEach(id -> seeds.put(id.toString(), AcquisitionProgressionGraph.Placement.ordinary(ProgressionBand.ENTRY)));
        var trials = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 3; i++) {
            long start = System.nanoTime(); QuestEvidence evidence = FtbQuestReader.captureDefinitions(source, version); long normalization = System.nanoTime() - start;
            start = System.nanoTime(); var graph = new ProductionGraph(evidence.processes(), List.of());
            var result = AcquisitionProgressionGraph.solve(seeds, graph, evidence); long progression = System.nanoTime() - start;
            long tasks = evidence.quests().stream().mapToLong(q -> q.tasks().size()).sum();
            long rewards = evidence.quests().stream().mapToLong(q -> q.rewards().size()).sum();
            trials.add(Map.of("trial", i + 1, "normalizationNanos", normalization, "projectionNanos", progression,
                    "quests", evidence.quests().size(), "tasks", tasks, "rewardLeaves", rewards, "processes", graph.processes().size(),
                    "ruleEvaluations", result.ruleEvaluations()));
            System.out.println("Definition replay " + (i + 1) + ": quests=" + evidence.quests().size() + " tasks=" + tasks + " rewardLeaves=" + rewards
                    + " normalize=" + normalization + "ns projection=" + progression + "ns");
            if (i == 0) Files.writeString(output.resolve("normalized-definitions.json"), new GsonBuilder().setPrettyPrinting().create().toJson(evidence.diagnostics()));
        }
        Files.writeString(output.resolve("timings.json"), new GsonBuilder().setPrettyPrinting().create().toJson(trials));
        // Reject a damaged authoritative source rather than returning an empty book.
        Path invalid = output.resolve("invalid-definition-fixture"); Files.createDirectories(invalid);
        Files.writeString(invalid.resolve("data.snbt"), "{ broken !!!");
        try { FtbQuestReader.captureDefinitions(invalid, version); throw new AssertionError("Broken source accepted as empty"); }
        catch (IllegalStateException expected) { System.out.println("Broken authoritative SNBT rejects generation: PASS"); }
    }
}

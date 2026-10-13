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
    /** Actual installed Library/Quests early-definition path. Isolated loader fixture only; no world or completion state. */
    public static void installedFixture() throws Exception {
        String quests = dev.architectury.platform.Platform.getMod("ftbquests").getVersion();
        String library = dev.architectury.platform.Platform.getMod("ftblibrary").getVersion();
        String teams = dev.architectury.platform.Platform.getMod("ftbteams").getVersion();
        String architectury = dev.architectury.platform.Platform.getMod("architectury").getVersion();
        if (!FtbQuestProvider.supported(quests, library, teams, architectury)) throw new AssertionError("Installed FTB quest tuple rejected");
        Path source = Path.of("quest-definition-fixture"); Files.createDirectories(source.resolve("chapters"));
        Files.writeString(source.resolve("data.snbt"), "{version:13}");
        Files.writeString(source.resolve("chapters/example.snbt"), "{id:'1',quests:[{id:'2',tasks:[{id:'3',type:'checkmark'}],rewards:[{id:'4',type:'item',item:'minecraft:diamond',count:2}]}]}");
        if (!FtbQuestReader.readiness(null, source, quests).collectable()) throw new AssertionError("Installed early quest definitions unavailable");
        QuestEvidence evidence = FtbQuestReader.capture(null, source, quests);
        if (evidence.quests().size() != 1 || evidence.quests().getFirst().tasks().size() != 1
                || evidence.quests().getFirst().rewards().getFirst().count() != 2
                || !"minecraft:diamond".equals(evidence.quests().getFirst().rewards().getFirst().item()))
            throw new AssertionError("Installed Library SNBT / quest normalization changed the definition");
        Files.writeString(source.resolve("data.snbt"), "{ broken !!!");
        try { FtbQuestReader.capture(null, source, quests); throw new AssertionError("Malformed installed source accepted"); }
        catch (IllegalStateException expected) { /* Missing or malformed definitions must not become an empty book. */ }
        System.out.println("FTB shared installed tuple: Teams=" + teams + " Library=" + library + " Quests=" + quests
                + " Architectury=" + architectury + "; early definition capture/malformed rejection PASS (no world/completion state)");
    }
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

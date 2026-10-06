package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.quest.*;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import java.util.*;

/** Joint acquisition/progression fixtures; no optional mod, player, team, world or server bootstrap. */
public final class QuestAcquisitionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        readiness(); definitions(); reachability(); economy();
        System.out.println("Quest acquisition invariants passed: " + checks);
    }
    private static void readiness() {
        var absent = new GenerationProviders(null, mod -> null);
        check(!absent.prepare("quests", new FtbQuestProvider(), false), "Absent suite skips without linking optional quest classes");
        check(absent.diagnostics().get(0).getAsJsonObject().get("status").getAsString().equals("ABSENT"), "Absent is distinct from empty");
        check(FtbQuestProvider.supported("2101.1.36", "2101.1.36", "2101.1.11", "13.0.11"), "Installed exact API supported");
        check(FtbQuestProvider.supported("2101.1.30", "2101.1.35", "2101.1.10", "13.0.11"), "Additional audited definition API tuple supported");
        check(!FtbQuestProvider.supported("2101.1.30", "2101.1.36", "2101.1.10", "13.0.11"), "Audited tuples do not imply mixed-version compatibility");
        check(!FtbQuestProvider.supported("2101.1.30", "2101.1.35", "2101.1.10", "13.0.12"), "Unaudited platform dependency rejected");
        check(!FtbQuestProvider.supported("2101.1.37", "2101.1.36", "2101.1.11", "13.0.11"), "Unaudited version rejected");
        check(!FtbQuestProvider.supported("2101.1.36", "2101.1.35", "2101.1.11", "13.0.11"), "Unaudited dependency rejected");
        var unready = FtbQuestProvider.definitionReadiness("2101.1.36", true, false, false, false);
        check(unready.status() == ProviderReadiness.Status.NOT_READY, "Constructed service is not authoritative empty book");
        var unavailable = new GenerationProviders(null, mod -> "installed");
        var provider = new GenerationProvider() {
            public String id() { return "fixture_unready_quests"; }
            public ProviderReadiness readiness(com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot inputs) { return unready; }
        };
        check(!unavailable.prepare("quests", provider, false), "Required unready optional provider is excluded without rejecting generation");
        var diagnostic = unavailable.diagnostics().get(0).getAsJsonObject();
        check(diagnostic.get("status").getAsString().equals("NOT_READY") && diagnostic.get("required").getAsBoolean(),
                "Excluded installed provider remains explicitly unready and required for evidence quality");
        check(!diagnostic.get("evidenceCollected").getAsBoolean() && diagnostic.get("facts").getAsLong() == 0
                        && diagnostic.get("sources").getAsLong() == 0,
                "Unready quest provider contributes no partial rewards, gates or sources");
        check(unavailable.warnings().stream().anyMatch(warning -> warning.contains("fixture_unready_quests") && warning.contains("NOT_READY")),
                "Excluded quest integration is retained in compatibility warnings");
        check(!FtbQuestProvider.definitionReadiness("2101.1.36", true, true, true, true).collectable(), "Loading/failed service rejects even with files");
        check(!FtbQuestProvider.definitionReadiness("2101.1.36", false, false, true, true).collectable(), "Foreign-server service rejects");
        check(FtbQuestProvider.definitionReadiness("2101.1.36", true, false, false, true).collectable(), "Verified early source allows generation before SERVER_STARTED");
        check(FtbQuestProvider.definitionReadiness("2101.1.36", true, false, true, false).collectable(), "Initialized empty service is authoritative");
    }
    private static void definitions() throws Exception {
        check(normalize("", "linear").quests().isEmpty(), "Genuinely empty format-13 book accepted");
        var q = normalize("{id:'1',tasks:[{id:'2',type:'item',item:{id:'minecraft:iron_ingot'},count:4,consume_items:true}],rewards:[{id:'3',type:'item',item:{id:'minecraft:diamond'},count:2}]}", "linear").quests().getFirst();
        check(q.tasks().getFirst().consumed() && q.tasks().getFirst().count() == 4, "Consume task counted distinctly");
        check(q.rewards().getFirst().count() == 2 && q.rewards().getFirst().scope().equals("player"), "Finite player reward quantity");
        var collect = normalize("{id:'1',tasks:[{id:'2',type:'item',item:{id:'minecraft:iron_ingot'},count:4}]}", "linear");
        check(!collect.quests().getFirst().tasks().getFirst().consumed(), "Collect task is reusable requirement");
        check(collect.processes().isEmpty(), "Task does not produce the mentioned item");
        var opaque = normalize("{id:'1',tasks:[{id:'2',type:'custom',energy:200}],rewards:[{id:'3',type:'command',command:'give @p minecraft:diamond'}]}", "linear");
        check(!opaque.quests().getFirst().tasks().getFirst().supported(), "Custom tasks unresolved");
        check(opaque.quests().getFirst().rewards().getFirst().item() == null && opaque.processes().isEmpty(), "Command reward never executed or guessed");
        check(opaque.quests().getFirst().tasks().getFirst().predicate().contains("energy"), "Opaque resource target/quantity retained");
        var broken = normalize("{id:'1',dependencies:['ff'],tasks:[{id:'2',type:'checkmark'}],rewards:[{id:'3',type:'item',item:'minecraft:diamond'}]}", "linear");
        check(!broken.quests().getFirst().unresolved().isEmpty(), "Broken prerequisite explicit");
        check(solve(broken, Map.of()).items().isEmpty(), "Broken reference cannot grant reward");
        var cyclic = normalize("{id:'1',dependencies:['2'],tasks:[{id:'3',type:'checkmark'}],rewards:[{id:'4',item:'minecraft:diamond'}]},{id:'2',dependencies:['1'],tasks:[{id:'5',type:'checkmark'}]}", "linear");
        check(solve(cyclic, Map.of()).quests().isEmpty(), "Unseeded prerequisite cycle cannot grant acquisition");
        var crafting = normalize("{id:'1',tasks:[{id:'2',type:'item',item:'minecraft:iron_ingot',only_from_crafting:true}],rewards:[{id:'3',item:'minecraft:diamond'}]}", "linear");
        check(solve(crafting, seed("minecraft:iron_ingot", ProgressionBand.ENTRY)).items().size() == 1, "Craft-only task cannot be satisfied by possession");
        var same = normalize("{id:'1',tasks:[{id:'2',type:'checkmark'}],rewards:[{id:'3',item:'minecraft:diamond'}],title:'late',icon:{id:'minecraft:netherite_sword'}}", "linear");
        check(!same.diagnostics().toString().contains("netherite_sword") && !same.diagnostics().toString().contains("late"), "Titles/icons excluded from economic truth");
        rejects(() -> QuestNormalizer.normalize(new CompoundTag(), List.of(), List.of(), "fixture"), "Unsupported definition version fails");
        var malformed = tag("{id:'f',quests:'broken list'}");
        var validData = tag("{version:13}");
        rejects(() -> QuestNormalizer.normalize(validData, List.of(malformed), List.of(), "fixture"), "Malformed definition list cannot become authoritative empty book");
    }
    private static void reachability() throws Exception {
        var early = normalize("{id:'1',tasks:[{id:'2',type:'checkmark'}],rewards:[{id:'3',item:'minecraft:netherite_sword'}]}", "linear");
        var reached = solve(early, seed("minecraft:netherite_sword", ProgressionBand.APEX));
        check(reached.items().get("minecraft:netherite_sword").stage() == ProgressionBand.ENTRY, "Early reward availability precedes late ordinary crafting");
        check(early.diagnostics().toString().indexOf("BURST_DAMAGE") < 0, "Availability adds no strength axes");
        var late = normalize("{id:'1',tasks:[{id:'2',type:'item',item:'minecraft:netherite_ingot'}]},{id:'3',dependencies:['1'],tasks:[{id:'4',type:'checkmark'}],rewards:[{id:'5',item:'minecraft:diamond'}]}", "linear");
        check(solve(late, seed("minecraft:netherite_ingot", ProgressionBand.APEX)).items().get("minecraft:diamond").stage() == ProgressionBand.APEX, "Enforced late prerequisite contributes reward stage");
        var flexible = normalize("{id:'1',tasks:[{id:'2',type:'item',item:'minecraft:netherite_ingot'}]},{id:'3',dependencies:['1'],tasks:[{id:'4',type:'checkmark'}],rewards:[{id:'5',item:'minecraft:diamond'}]}", "flexible");
        check(solve(flexible, Map.of()).items().get("minecraft:diamond").stage() == ProgressionBand.ENTRY, "Flexible ordering is suggested, not enforced");
        var optional = normalize("{id:'1',optional:true,tasks:[{id:'2',type:'item',item:'minecraft:iron_ingot'},{id:'3',type:'item',item:'minecraft:netherite_ingot'}]}", "linear");
        check(solve(optional, seed("minecraft:iron_ingot", ProgressionBand.EARLY)).items().get("minecraft:iron_ingot").stage() == ProgressionBand.EARLY, "Optional late mention cannot delay reachable item");
        var alternatives = normalize("{id:'1',tasks:[{id:'2',type:'item',item:'minecraft:netherite_ingot'}]},{id:'3',tasks:[{id:'4',type:'checkmark'}]},{id:'5',dependencies:['1','3'],dependency_requirement:'one_completed',tasks:[{id:'6',type:'checkmark'}],rewards:[{id:'7',item:'minecraft:diamond'}]}", "linear");
        check(solve(alternatives, Map.of()).items().get("minecraft:diamond").stage() == ProgressionBand.ENTRY, "Alternative completed prerequisite chooses reachable branch");
        var recipe = new ProductionGraph.Process("fixture:craft", "minecraft:crafting", List.of(new ProductionGraph.Input(List.of("minecraft:netherite_sword"), 1, true)),
                List.of(new ProductionGraph.Output("minecraft:gold_ingot", 1, 1, false)), 0, 0, "fixture", .9, Map.of());
        var joint = AcquisitionProgressionGraph.solve(Map.of(), new ProductionGraph(List.of(recipe), List.of()), early);
        check(joint.items().containsKey("minecraft:gold_ingot"), "Quest reward feeds normal shared production reachability");
        check(joint.items().get("minecraft:gold_ingot").finite(), "Finite input does not become renewable through crafting");
        var tooMuch = normalize("{id:'1',tasks:[{id:'2',type:'checkmark'}],rewards:[{id:'3',item:'minecraft:diamond',count:1}]},{id:'4',tasks:[{id:'5',type:'item',item:'minecraft:diamond',count:2}],rewards:[{id:'6',item:'minecraft:gold_ingot'}]}", "linear");
        check(!solve(tooMuch, Map.of()).items().containsKey("minecraft:gold_ingot"), "Finite quantity cannot supply oversized submission");
    }
    private static void economy() throws Exception {
        var evidence = normalize("{id:'1',can_repeat:true,repeat_cooldown:30,tasks:[{id:'2',type:'item',item:'minecraft:iron_ingot',count:4,consume_items:true}],rewards:[{id:'3',item:'minecraft:diamond',count:2,team_reward:true}]}", "linear");
        var p = evidence.processes().getFirst();
        check(p.inputs().getFirst().consumed() && p.inputs().getFirst().count() == 4, "Repeated submission material costs retained");
        check(p.metadata().get("cooldown_seconds").equals("30") && p.metadata().get("scope").equals("team"), "Cooldown/scope are definition facts");
        check(p.metadata().get("operation").equals("manual") && !p.metadata().containsKey("operations_per_second"), "Repeatable is not passive or measured throughput");
        check(!p.conservationComplete(), "Conditional reward not laundered into unrestricted conservation");
        check(evidence.processes().stream().anyMatch(process -> process.conservationComplete() && process.metadata().containsKey("quest_economy_bundle")),
                "Deterministic repeatable team rewards have one material-cost conservation bundle");
        check(solve(evidence, seed("minecraft:iron_ingot", ProgressionBand.MID)).items().get("minecraft:diamond").stage() == ProgressionBand.MID, "Repeatable retains repeat requirements");
        var table = tag("{id:'a',loot_size:1,rewards:[{id:'b',item:'minecraft:diamond',count:2,weight:1.0f},{id:'c',item:'minecraft:emerald',count:3,weight:3.0f}]}");
        var choice = normalizeWithTable("choice", table);
        check(choice.processes().size() == 2 && choice.processes().stream().allMatch(process -> process.outputs().size() == 1), "Choices are separate opportunities, never simultaneous bundle");
        check(choice.quests().getFirst().rewards().stream().allMatch(r -> r.probability() == 1 && !r.group().isEmpty()), "Mutually exclusive choice groups retained");
        var both = new ProductionGraph.Process("fixture:both", "minecraft:crafting", List.of(new ProductionGraph.Input(List.of("minecraft:diamond"), 1, true), new ProductionGraph.Input(List.of("minecraft:emerald"), 1, true)),
                List.of(new ProductionGraph.Output("minecraft:gold_ingot", 1, 1, false)), 0, 0, "fixture", .9, Map.of());
        check(!AcquisitionProgressionGraph.solve(Map.of(), new ProductionGraph(List.of(both), List.of()), choice).items().containsKey("minecraft:gold_ingot"), "Finite exclusive choices cannot jointly satisfy a recipe");
        var random = normalizeWithTable("random", table);
        check(random.quests().getFirst().rewards().getFirst().probability() == .25, "Random weights normalized once against whole supported table");
        check(random.quests().getFirst().rewards().get(1).probability() == .75, "Random outcome quantities remain independent");
        check(random.processes().stream().noneMatch(ProductionGraph.Process::conservationComplete), "Random rewards never summed in hard constraints");
        check(random.diagnostics().toString().contains("player") && !random.diagnostics().toString().contains("UUID"), "Definition-only scope, no player/team completion state");
        var finiteRepeat = normalize("{id:'1',tasks:[{id:'2',type:'checkmark'}],rewards:[{id:'3',item:'minecraft:iron_ingot',count:4}]},{id:'4',can_repeat:true,tasks:[{id:'5',type:'item',item:'minecraft:iron_ingot',count:4,consume_items:true}],rewards:[{id:'6',item:'minecraft:diamond'}]}", "linear");
        check(solve(finiteRepeat, Map.of()).items().get("minecraft:diamond").finite(), "Repeat flag cannot replenish finite consumed task resources");
        check(!solve(finiteRepeat, Map.of()).items().get("minecraft:diamond").renewable(), "Finite submitted resources do not prove recurring supply");
        var freeRepeat = normalize("{id:'1',can_repeat:true,tasks:[{id:'2',type:'item',item:'minecraft:iron_ingot',count:4}],rewards:[{id:'3',item:'minecraft:diamond',team_reward:true}]}", "linear");
        check(freeRepeat.processes().stream().anyMatch(process -> process.metadata().containsKey("production_constraint") && process.conservationComplete()),
                "Collect-only repeatable team opportunity is a manual bounded source, not a zero-credit recipe");
        var twoConsumed = normalize("{id:'1',tasks:[{id:'2',type:'checkmark'}],rewards:[{id:'3',item:'minecraft:iron_ingot',count:4}]},{id:'4',tasks:[{id:'5',type:'item',item:'minecraft:iron_ingot',count:3,consume_items:true},{id:'6',type:'item',item:'minecraft:iron_ingot',count:3,consume_items:true}],rewards:[{id:'7',item:'minecraft:diamond'}]}", "linear");
        check(!solve(twoConsumed, Map.of()).items().containsKey("minecraft:diamond"), "Separate consumed tasks cannot spend the same finite stack twice");
        var multi = new ProductionGraph.Process("fixture:bundle", "ftbquests:repeatable_reward_bundle", p.inputs(),
                List.of(new ProductionGraph.Output("minecraft:diamond", 2, 1, false), new ProductionGraph.Output("minecraft:emerald", 2, 1, false)),
                0, 0, "ftbquests", .85, Map.of());
        var proposed = Map.of("minecraft:iron_ingot", new com.mistaboom.essence_ascendance.balance.economy.DissolutionYield(2_000_000),
                "minecraft:diamond", new com.mistaboom.essence_ascendance.balance.economy.DissolutionYield(5_000_000),
                "minecraft:emerald", new com.mistaboom.essence_ascendance.balance.economy.DissolutionYield(5_000_000));
        var conserved = com.mistaboom.essence_ascendance.balance.economy.EconomyConservationSolver.solveWholeUnits(new ProductionGraph(List.of(multi), List.of()), proposed);
        check(conserved.invariants().stream().allMatch(com.mistaboom.essence_ascendance.balance.economy.EconomyConservationSolver.Invariant::passed),
                "Repeatable shared-cost bundle passes real whole-unit conservation");
    }
    private static QuestEvidence normalizeWithTable(String type, CompoundTag table) throws Exception {
        return QuestNormalizer.normalize(tag("{version:13,progression_mode:'linear'}"), List.of(tag("{id:'f',quests:[{id:'1',tasks:[{id:'2',type:'checkmark'}],rewards:[{id:'3',type:'" + type + "',table:'a'}]}]}")), List.of(table), "fixture");
    }
    private static QuestEvidence normalize(String quests, String mode) throws Exception {
        return QuestNormalizer.normalize(tag("{version:13,progression_mode:'" + mode + "'}"), List.of(tag("{id:'f',quests:[" + quests + "]}")), List.of(), "fixture");
    }
    private static CompoundTag tag(String text) throws Exception { return TagParser.parseTag(text.replace('\'', '"')); }
    private static Map<String, AcquisitionProgressionGraph.Placement> seed(String item, ProgressionBand stage) { return Map.of(item, AcquisitionProgressionGraph.Placement.ordinary(stage)); }
    private static AcquisitionProgressionGraph.Result solve(QuestEvidence q, Map<String, AcquisitionProgressionGraph.Placement> seeds) { return AcquisitionProgressionGraph.solve(seeds, new ProductionGraph(q.processes(), List.of()), q); }
    private static void check(boolean value, String detail) { checks++; if (!value) throw new AssertionError(detail); }
    private static void rejects(Runnable action, String detail) { boolean rejected = false; try { action.run(); } catch (RuntimeException expected) { rejected = true; } check(rejected, detail); }
}

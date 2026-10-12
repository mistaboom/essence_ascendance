package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mistaboom.essence_ascendance.balance.quest.QuestEvidence;
import java.util.*;

/** Pure native-witness/recipe/quest boundary checks; no server or optional mod. */
public final class AuthoritativeAcquisitionClosureTest {
    private static int checks;
    public static void main(String[] args) {
        nativeDescendants(); exclusionsAndUnknowns(); finiteLineage(); stageAndBounds(); questReplay(); chanceOutputs();
        System.out.println("AuthoritativeAcquisitionClosureTest: " + checks + " checks PASS");
    }
    private static void nativeDescendants() {
        var before = resources("seed", "ordinary", "tool", "child", "grandchild");
        before.put("ordinary", resource("ordinary", true, 4)); before.put("tool", resource("tool", true, 1000));
        var after = nativeSeed(before, SourceAvailability.Category.CONDITIONAL_RENEWABLE, true);
        var graph = graph(recipe("child", List.of(input("seed", 2, true), input("ordinary", 1, true), input("tool", 1, false)), 1),
                recipe("grandchild", List.of(input("child", 1, true)), 2));
        var previous = Map.of("ordinary", ordinary(), "tool", ordinary());
        var result = resolve(before, after, previous, graph, new EvidenceSink());
        check(result.resources().get("child").reachable(), "A newly proved native seed unlocks a complete material recipe");
        check(result.resources().get("grandchild").reachable(), "Complete descendants close transitively with no quests installed");
        check(result.progression().items().containsKey("ordinary"), "Ordinary modeled seeds survive the empty-quest projection");
        check(result.resources().get("child").economicValue() == 20, "Consumed input quantities are paid; reusable setup grants no recurring credit");
        check(result.resources().get("grandchild").economicValue() == 10, "Output batch count divides the paid bound");
        check(result.progression().items().get("grandchild").renewable(), "Renewability follows all consumed prerequisites");
        check(result.resources().get("child").warnings().stream().noneMatch(w -> w.equals("unreachable_or_excluded")), "Resolved source warning is removed");
        var missingSetup = resolve(before, after, Map.of("ordinary", ordinary()), graph, new EvidenceSink());
        check(!missingSetup.resources().get("child").reachable(), "Reusable setup still requires independent acquisition");
        var finite = nativeSeed(before, SourceAvailability.Category.FINITE_SHARED, false);
        check(!resolve(before, finite, previous, graph, new EvidenceSink()).resources().get("child").reachable(),
                "Expected finite event output is not invented guaranteed stock");
        for (var invalid : List.of(new double[]{0, 2, 1}, new double[]{.5, 0, 1}, new double[]{.5, 2, 0})) {
            var changed = nativeSeed(before, SourceAvailability.Category.CONDITIONAL_RENEWABLE, true,
                    invalid[0], invalid[1], invalid[2] == 1);
            check(!resolve(before, changed, previous, graph, new EvidenceSink()).resources().get("child").reachable(),
                    "Zero chance, zero quantity and unproved access cannot seed descendants");
        }
    }
    private static void exclusionsAndUnknowns() {
        var before = resources("seed", "child", "grandchild", "a", "b");
        var after = nativeSeed(before, SourceAvailability.Category.CONDITIONAL_RENEWABLE, true);
        var graph = graph(recipe("child", List.of(input("seed", 1, true)), 1),
                recipe("grandchild", List.of(input("child", 1, true)), 1),
                recipe("a", List.of(input("b", 1, true)), 1), recipe("b", List.of(input("a", 1, true)), 1));
        var result = resolve(before, after, Map.of(), graph, new EvidenceSink());
        check(!result.resources().get("a").reachable() && !result.resources().get("b").reachable(), "Unseeded cycles stay unresolved");
        var denied = new EvidenceSink(); override(denied, "child", "attainable", EvidenceFact.Value.flag(false));
        result = resolve(before, after, Map.of(), graph, denied);
        check(!result.resources().get("child").reachable() && !result.resources().get("grandchild").reachable(),
                "Human-disabled intermediate cannot be reached or used to reach descendants");
        var incomplete = recipe("child", List.of(input("seed", 1, true)), 1);
        incomplete = new ProductionGraph.Process(incomplete.id(), incomplete.family(), incomplete.inputs(), incomplete.outputs(),
                0, 0, "fixture", 1, Map.of("acquisition_complete", "false"));
        check(!resolve(before, after, Map.of(), graph(incomplete), new EvidenceSink()).resources().get("child").reachable(),
                "Advisory/incomplete recipes cannot establish access");
        boolean rejected = false;
        try { new ProductionGraph.Output("child", 1, 0, false); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "The production schema rejects zero-probability outputs before closure");
    }
    private static void finiteLineage() {
        var before = resources("seed", "a", "b", "child", "grandchild", "conflict", "too_many");
        before.put("a", resource("a", true, 5)); before.put("b", resource("b", true, 7));
        var after = nativeSeed(before, SourceAvailability.Category.CONDITIONAL_RENEWABLE, true);
        var a = new AcquisitionProgressionGraph.Placement(ProgressionBand.MID, Map.of("choice", "a"), 1, true, false, "reward/q/a");
        var b = new AcquisitionProgressionGraph.Placement(ProgressionBand.MID, Map.of("choice", "b"), 1, true, false, "reward/q/b");
        var graph = graph(recipe("child", List.of(input("seed", 1, true), input("a", 1, true)), 2),
                recipe("grandchild", List.of(input("child", 3, true)), 1),
                recipe("conflict", List.of(input("a", 1, true), input("b", 1, true)), 1),
                recipe("too_many", List.of(input("a", 1, true), input("a", 1, true)), 1));
        var quests = quest(false, List.of(reward("a", "choice"), reward("b", "choice")));
        var result = AuthoritativeAcquisitionClosure.resolve(before, after, Map.of(), Map.of("a", a, "b", b), graph, quests, new EvidenceSink());
        var child = result.progression().items().get("child");
        check(result.resources().get("child").reachable() && child.finite() && child.quantity() == 2,
                "Finite consumed stock remains finite through recipe output counts");
        check(child.exclusiveClaims().equals(a.exclusiveClaims()) && !child.renewable(), "Exclusive choices and nonrenewability survive descendants");
        check(!result.resources().get("grandchild").reachable(), "A recipe cannot consume more finite output than proved");
        check(!result.resources().get("conflict").reachable(), "Exclusive reward alternatives cannot be jointly consumed");
        check(!result.resources().get("too_many").reachable(), "Repeated ingredient slots share the same finite inventory bound");
    }
    private static void stageAndBounds() {
        var before = resources("seed", "child", "grandchild", "late");
        before.put("child", new ResourceEvidence("child", ProgressionBand.LATE, Availability.UNKNOWN, Automation.NONE,
                false, true, 100, .7, List.of(), List.of("unreachable_or_excluded")));
        var after = nativeSeed(before, SourceAvailability.Category.CONDITIONAL_RENEWABLE, true);
        var graph = graph(recipe("child", List.of(input("seed", 1, true)), 1), recipe("grandchild", List.of(input("child", 1, true)), 1));
        var sink = new EvidenceSink(); override(sink, "child", "stage", EvidenceFact.Value.text("LATE"));
        var result = resolve(before, after, Map.of(), graph, sink);
        check(result.resources().get("child").stage() == ProgressionBand.LATE
                        && result.resources().get("grandchild").stage() == ProgressionBand.LATE,
                "Human stage is applied before descendant stage propagation");
        override(sink, "child", "renewable", EvidenceFact.Value.flag(false));
        check(!resolve(before, after, Map.of(), graph, sink).progression().items().get("grandchild").renewable(),
                "Explicit nonrenewability propagates to consumed descendants");
        after.put("seed", new ResourceEvidence("seed", ProgressionBand.ENTRY, Availability.RENEWABLE_MANUAL, Automation.NONE,
                true, true, 0, .9, after.get("seed").sources(), List.of()));
        result = resolve(before, after, Map.of(), graph, new EvidenceSink());
        check(result.resources().get("child").reachable() && result.resources().get("child").economicValue() == 0,
                "A reachable zero-value input cannot invent a positive economic bound");
        var late = new AcquisitionProgressionGraph.Placement(ProgressionBand.APEX, Map.of(), 0, false, true, "ordinary_acquisition");
        after.put("late", resource("late", true, 1));
        var alternative = new ProductionGraph.Input(List.of("seed", "late"), 1, true);
        after.put("seed", nativeSeed(before, SourceAvailability.Category.CONDITIONAL_RENEWABLE, true).get("seed"));
        result = resolve(before, after, Map.of("late", late), graph(recipe("child", List.of(alternative), 1)), new EvidenceSink());
        check(result.resources().get("child").economicValue() == 8, "Cheaper later-stage alternatives cannot price an earlier proof");
        for (var availability : List.of(Availability.FINITE, Availability.ADMINISTRATIVE)) {
            var authoritative = new TreeMap<>(after);
            authoritative.put("child", new ResourceEvidence("child", ProgressionBand.ENTRY, availability, Automation.PASSIVE,
                    false, true, 100, .8, List.of(), List.of("unreachable_or_excluded")));
            var explicit = new EvidenceSink(); override(explicit, "child", "availability", EvidenceFact.Value.text(availability.name()));
            override(explicit, "child", "automation", EvidenceFact.Value.text("PASSIVE"));
            result = resolve(before, authoritative, Map.of(), graph, explicit);
            check(result.resources().get("child").availability() == availability
                            && result.resources().get("child").automation() == Automation.PASSIVE,
                    "Explicit availability/automation is preserved when a target becomes reachable");
            check(!result.progression().items().get("grandchild").renewable(),
                    "Explicit finite/administrative classification cannot become renewable in descendants");
        }
    }
    private static void questReplay() {
        var before = resources("seed", "reward", "child", "grandchild");
        var after = nativeSeed(before, SourceAvailability.Category.CONDITIONAL_RENEWABLE, true);
        var quests = quest(true, List.of(reward("reward", "")));
        var processes = new ArrayList<>(quests.processes());
        processes.add(recipe("child", List.of(input("reward", 1, true)), 1));
        processes.add(recipe("grandchild", List.of(input("child", 1, true)), 1));
        var graph = new ProductionGraph(processes, List.of());
        var result = AuthoritativeAcquisitionClosure.resolve(before, after, Map.of(), Map.of(), graph, quests, new EvidenceSink());
        check(result.resources().get("reward").reachable() && result.resources().get("grandchild").reachable(),
                "New native supply reopens the same quest graph and its complete recipe descendants");
        var initial = AcquisitionProgressionGraph.solve(Map.of(), graph, quests);
        var first = AcquisitionProgressionGraph.projectProduction(graph, initial, AcquisitionProgressionGraph.Constraints.NONE);
        var second = AcquisitionProgressionGraph.projectProduction(graph, result.progression(), result.constraints());
        String bundle = "essence_ascendance:quest-economy/q";
        check(first.processes().stream().filter(p -> p.id().equals(bundle)).noneMatch(ProductionGraph.Process::conservationComplete),
                "Unproved first-pass quest cannot supply conservation");
        var projected = second.processes().stream().filter(p -> p.id().equals(bundle)).findFirst().orElseThrow();
        check(projected.conservationComplete() && projected.acquisitionComplete()
                        && projected.metadata().get("renewability").equals("renewable"),
                "Second projection restores only the original audited repeatable bundle capability");
        check(second.processes().stream().filter(p -> p.id().equals("essence_ascendance:quest/q/reward"))
                        .noneMatch(ProductionGraph.Process::conservationComplete),
                "A reward opportunity never upgrades into an unaudited material conservation constraint");
        var prior = result.progression().items();
        var cached = new TreeMap<>(result.resources());
        for (boolean blockRecipe : List.of(false, true)) {
            var denied = new EvidenceSink();
            if (blockRecipe) denied.add(new EvidenceFact(EvidenceFact.Subject.RECIPE, "recipe/child", "disabled",
                    EvidenceFact.Value.flag(true), "human", EvidenceFact.Origin.OVERRIDE, 1, 1000, ProgressionBand.ENTRY, List.of(), "fixture"));
            else override(denied, "reward", "attainable", EvidenceFact.Value.flag(false));
            var blocked = AuthoritativeAcquisitionClosure.resolve(before, cached, Map.of(), prior, graph, quests, denied);
            check(!blocked.resources().get("child").reachable() && !blocked.resources().get("grandchild").reachable(),
                    "Cached quest-derived descendants cannot bypass a denied ancestor or production rule");
        }
        var deniedSeed = new EvidenceSink(); override(deniedSeed, "seed", "attainable", EvidenceFact.Value.flag(false));
        var blocked = AuthoritativeAcquisitionClosure.resolve(before, after, Map.of(), Map.of(), graph, quests, deniedSeed);
        check(!blocked.resources().get("reward").reachable() && !blocked.resources().get("child").reachable(),
                "Human-disabled source cannot satisfy a quest or seed its descendants");
        var deniedReward = new EvidenceSink();
        deniedReward.add(new EvidenceFact(EvidenceFact.Subject.RECIPE, "essence_ascendance:quest/q/reward", "disabled",
                EvidenceFact.Value.flag(true), "human", EvidenceFact.Origin.OVERRIDE, 1, 1000, ProgressionBand.ENTRY, List.of(), "fixture"));
        blocked = AuthoritativeAcquisitionClosure.resolve(before, after, Map.of(), Map.of(), graph, quests, deniedReward);
        check(!blocked.resources().get("reward").reachable(), "Explicit reward-process exclusion applies to the shared quest rule");
        var finiteQuests = quest(false, List.of(reward("reward", "")));
        var finiteGraph = new ProductionGraph(finiteQuests.processes(), List.of());
        var ordinary = Map.of("seed", ordinary(), "reward", AcquisitionProgressionGraph.Placement.ordinary(ProgressionBand.MID, true));
        var preferred = AcquisitionProgressionGraph.solve(ordinary, finiteGraph, finiteQuests);
        check(preferred.items().get("reward").stage() == ProgressionBand.ENTRY && preferred.items().get("reward").finite(),
                "Fixture first projection prefers an earlier finite quest to independent ordinary supply");
        var projectedResources = new TreeMap<>(before);
        projectedResources.put("seed", resource("seed", true, 8));
        projectedResources.put("reward", resource("reward", true, 10));
        var fallback = AuthoritativeAcquisitionClosure.resolve(projectedResources, projectedResources, ordinary,
                preferred.items(), finiteGraph, finiteQuests, deniedReward);
        check(fallback.resources().get("reward").reachable() && fallback.progression().items().get("reward").source().equals("ordinary_acquisition"),
                "Denied early reward falls back to the separately retained ordinary witness");
        check(fallback.resources().get("reward").stage() == ProgressionBand.MID
                        && fallback.resources().get("reward").availability() == Availability.RENEWABLE_MANUAL,
                "Fallback refreshes the formerly quest-derived resource stage and classification");
    }
    private static AuthoritativeAcquisitionClosure.Result resolve(Map<String, ResourceEvidence> before,
            Map<String, ResourceEvidence> after, Map<String, AcquisitionProgressionGraph.Placement> previous,
            ProductionGraph graph, EvidenceSink sink) {
        return AuthoritativeAcquisitionClosure.resolve(before, after, previous, previous, graph, QuestEvidence.EMPTY, sink);
    }
    private static void chanceOutputs() {
        var before = resources("seed", "tool", "random", "guaranteed", "compressed", "stock");
        before.put("tool", resource("tool", true, 100));
        var after = nativeSeed(before, SourceAvailability.Category.CONDITIONAL_RENEWABLE, true);
        var chance = new ProductionGraph.Process("recipe/chance", "crushing",
                List.of(input("seed", 1, true), input("tool", 1, false)),
                List.of(new ProductionGraph.Output("random", 1, .5, false),
                        new ProductionGraph.Output("guaranteed", 1, 1, true)), 0, 0, "fixture", .9, Map.of());
        var compress = recipe("compressed", List.of(input("random", 9, true)), 1);
        var result = resolve(before, after, Map.of("tool", ordinary()), graph(chance, compress), new EvidenceSink());
        check(result.resources().get("random").reachable() && result.resources().get("compressed").reachable()
                        && result.progression().items().get("compressed").renewable(),
                "Positive-chance output from renewable consumed supply supports downstream compression");
        check(result.resources().get("random").economicValue() == 16,
                "Chance-output paid value divides consumed cost by expected output quantity");
        check(result.resources().get("guaranteed").reachable(), "Guaranteed coproduct is retained beside a chance output");
        check(!resolve(before, after, Map.of(), graph(chance, compress), new EvidenceSink()).resources().get("random").reachable(),
                "Renewable chance production still requires obtainable reusable setup");
        var finiteChance = new ProductionGraph.Process(chance.id(), chance.family(),
                List.of(input("stock", 1, true), input("tool", 1, false)), chance.outputs(),
                0, 0, "fixture", .9, Map.of());
        var quests = quest(false, List.of(reward("stock", "")));
        result = AuthoritativeAcquisitionClosure.resolve(before, after, Map.of("tool", ordinary()), Map.of(),
                graph(finiteChance, compress), quests, new EvidenceSink());
        check(!result.resources().get("random").reachable() && !result.resources().get("compressed").reachable(),
                "Finite chance attempts do not create guaranteed random stock or compression access");
        check(result.resources().get("guaranteed").reachable() && result.progression().items().get("guaranteed").finite(),
                "Finite production retains its guaranteed coproduct without granting the chance output");
    }
    private static Map<String, ResourceEvidence> resources(String... ids) {
        Map<String, ResourceEvidence> result = new TreeMap<>(); for (String id : ids) result.put(id, resource(id, false, id.equals("seed") ? 8 : 100)); return result;
    }
    private static ResourceEvidence resource(String id, boolean reachable, double value) {
        return new ResourceEvidence(id, ProgressionBand.ENTRY, reachable ? Availability.FINITE : Availability.UNKNOWN,
                Automation.NONE, reachable, true, value, .8, List.of(), reachable ? List.of() : List.of("unreachable_or_excluded"));
    }
    private static Map<String, ResourceEvidence> nativeSeed(Map<String, ResourceEvidence> before, SourceAvailability.Category category, boolean renewable) {
        return nativeSeed(before, category, renewable, .5, 2, true);
    }
    private static Map<String, ResourceEvidence> nativeSeed(Map<String, ResourceEvidence> before, SourceAvailability.Category category,
                                                           boolean renewable, double chance, double count, boolean access) {
        var off = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
        var availability = new SourceAvailability("native:seed", category, SourceAvailability.Scope.SHARED, List.of(), List.of(),
                List.of("audited native conditional operation"), off, off, access, chance, count, List.of());
        var source = new AcquisitionSource("native:seed", AcquisitionSource.Kind.PLAYER_ACTION, ProgressionBand.ENTRY, count,
                renewable, false, 0, .9, List.of(), "fixture native witness", availability);
        var result = new TreeMap<>(before); var old = before.get("seed");
        result.put("seed", new ResourceEvidence("seed", ProgressionBand.ENTRY,
                renewable ? Availability.RENEWABLE_MANUAL : Availability.FINITE, Automation.PLAYER_GATED,
                true, true, old.economicValue(), .9, List.of(source), List.of())); return result;
    }
    private static QuestEvidence.Reward reward(String item, String group) {
        return new QuestEvidence.Reward(item, group.isEmpty() ? "item" : "choice", item, 1, 1, group, "team", "", List.of());
    }
    private static QuestEvidence quest(boolean repeatable, List<QuestEvidence.Reward> rewards) {
        var task = new QuestEvidence.Task("task", "item", List.of("seed"), 1, false, true, "", List.of());
        return new QuestEvidence(List.of(new QuestEvidence.Quest("q", List.of(), 0, true, "all_completed", repeatable,
                0, false, List.of(task), rewards, List.of())), "fixture", List.of());
    }
    private static AcquisitionProgressionGraph.Placement ordinary() { return AcquisitionProgressionGraph.Placement.ordinary(ProgressionBand.ENTRY, true); }
    private static ProductionGraph.Input input(String id, double count, boolean consumed) { return new ProductionGraph.Input(List.of(id), count, consumed); }
    private static ProductionGraph.Process recipe(String output, List<ProductionGraph.Input> inputs, double count) {
        return new ProductionGraph.Process("recipe/" + output, "crafting", inputs,
                List.of(new ProductionGraph.Output(output, count, 1, false)), 0, 0, "fixture", .9, Map.of());
    }
    private static ProductionGraph graph(ProductionGraph.Process... processes) { return new ProductionGraph(List.of(processes), List.of()); }
    private static void override(EvidenceSink sink, String id, String property, EvidenceFact.Value value) {
        sink.add(new EvidenceFact(EvidenceFact.Subject.ITEM, id, property, value, "human", EvidenceFact.Origin.OVERRIDE,
                1, 1000, ProgressionBand.ENTRY, List.of(), "fixture explicit override"));
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}

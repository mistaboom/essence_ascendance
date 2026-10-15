package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import java.util.*;

/** Bounded generic estimates and exact material constraints; no server, source inference or special mod fixtures. */
public final class MaterialValuationTest {
    private static int checks;
    private static final String GATHERING = "essence_ascendance:gathering", UTILITY = "essence_ascendance:utility";
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        fallbackAndAlternatives(); dependencyComposition(); exactDenominations(); recipesAndRemainders(); cosmeticAndOrder(); boundedWork();
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); nativeProperties();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("MaterialValuationTest: " + checks + " generic estimate/conservation checks PASS");
    }
    private static SimpleValuationSolver.Seed seed(long value, int priority) {
        return new SimpleValuationSolver.Seed(value, priority, Map.of(GATHERING, 3.0, UTILITY, 1.0));
    }
    private static ProductionGraph.Process recipe(String id, List<ProductionGraph.Input> input, List<ProductionGraph.Output> output, boolean complete) {
        return new ProductionGraph.Process("fixture:" + id, "minecraft:crafting", input, output, 0,0,"static_fixture",.9,
                Map.of("conservation_complete", Boolean.toString(complete), "acquisition_complete", "false"));
    }
    private static ProductionGraph.Process convert(String id, String from, long input, String to, long output) {
        return recipe(id,List.of(new ProductionGraph.Input(List.of(from),input,true)),List.of(new ProductionGraph.Output(to,output,1,false)),true);
    }
    private static SimpleValuationSolver.Result solve(Map<String, SimpleValuationSolver.Seed> seeds, ProductionGraph.Process... recipes) {
        return SimpleValuationSolver.solve(seeds,new ProductionGraph(List.of(recipes),List.of()));
    }
    private static void fallbackAndAlternatives() {
        var seeds = Map.of("coal",seed(64,4),"copper",seed(150,4),"unknown_tiny",seed(24,0),"unknown_nugget",seed(24,0));
        var r = solve(seeds,convert("coal", "unknown_tiny",8,"coal",1),convert("copper","unknown_nugget",9,"copper",1));
        check(r.values().get("coal").amount()==192 && r.values().get("copper").amount()==216,"Unclassified alternatives have positive estimates and cannot act as missing/free inputs");
        var missing = solve(seeds,recipe("missing",List.of(new ProductionGraph.Input(List.of("unrepresented","unknown_tiny"),1,true)),List.of(new ProductionGraph.Output("coal",1,1,false)),true));
        check(missing.values().get("coal").amount()==64 && missing.unresolvedProcesses()==1,"Unrepresented alternative is unresolved, not a zero cap");
        var nominal = solve(Map.of("ore",seed(100,4),"component",seed(24,0)),
                recipe("opaque",List.of(new ProductionGraph.Input(List.of("ore"),3,true)),List.of(new ProductionGraph.Output("component",2,1,false)),false));
        check(nominal.values().get("component").amount()==150 && nominal.unresolvedProcesses()==1,"Advisory recipe supports a composition estimate without becoming a hard constraint");
        check(solve(Map.of("unknown",seed(24,0))).values().get("unknown").amount()==24,"No recipe or source metadata retains the positive fallback");
    }
    private static void dependencyComposition() {
        var seeds = new TreeMap<String, SimpleValuationSolver.Seed>();
        seeds.put("raw",seed(180,4)); seeds.put("scrap",seed(24,0)); seeds.put("alloy",seed(150,4));
        seeds.put("gold",seed(153,4)); seeds.put("apple",seed(75,3)); seeds.put("food",seed(180,3));
        seeds.put("plate",seed(24,0)); seeds.put("block",seed(32,2)); seeds.put("gear",seed(280,3));
        var recipes = new ArrayList<ProductionGraph.Process>();
        recipes.add(convert("smelt","raw",1,"scrap",1));
        recipes.add(recipe("alloy",List.of(new ProductionGraph.Input(List.of("scrap"),4,true),new ProductionGraph.Input(List.of("gold"),4,true)),List.of(new ProductionGraph.Output("alloy",1,1,false)),true));
        recipes.add(convert("compress","alloy",9,"block",1)); recipes.add(convert("expand","block",1,"alloy",9));
        recipes.add(convert("plate","alloy",2,"plate",1)); recipes.add(convert("gear","plate",3,"gear",1));
        recipes.add(recipe("food",List.of(new ProductionGraph.Input(List.of("gold"),8,true),new ProductionGraph.Input(List.of("apple"),1,true)),List.of(new ProductionGraph.Output("food",1,1,false)),true));
        var result=SimpleValuationSolver.solve(seeds,new ProductionGraph(recipes,List.of()));
        check(result.values().get("alloy").amount()==1332 && result.values().get("block").amount()==11988,
                "Crafted material inherits its entire predecessor bill and all exact denominations follow");
        check(result.values().get("gear").amount()==7992 && result.values().get("food").amount()==1299,
                "High-priority equipment and food inherit bounded multi-stage material cost");
        var recycle=solve(Map.of("metal",seed(150,4),"gear",seed(280,3)),
                convert("craft","metal",4,"gear",1),convert("recycle","gear",1,"metal",1));
        check(recycle.values().get("metal").amount()==150 && recycle.values().get("gear").amount()==600,
                "Lossy recycling retains its material anchor without recursively pricing the material from gear");
        var component=solve(Map.of("paper",seed(24,0),"book",seed(24,0),
                        "stored",new SimpleValuationSolver.Seed(27,0,Map.of(UTILITY,1.0),Map.of("book",4L))),
                convert("bind","paper",4,"book",1));
        check(component.values().get("book").amount()==96 && component.values().get("stored").amount()==384,
                "Stored-effect ceiling retains the composed carrier plus a shared premium budget");
        Collections.reverse(recipes);
        check(result.equals(SimpleValuationSolver.solve(seeds,new ProductionGraph(recipes,List.of()))),
                "Multi-stage composition is deterministic across process order");
    }
    private static void exactDenominations() {
        for (int length : List.of(2,11,13,14)) {
            Map<String, SimpleValuationSolver.Seed> seeds = new TreeMap<>(); List<ProductionGraph.Process> recipes = new ArrayList<>();
            seeds.put("form0",seed(18,4));
            for (int i=1;i<=length;i++) {
                seeds.put("form"+i,seed(32,1));
                recipes.add(convert("compress"+i,"form"+(i-1),9,"form"+i,1));
                recipes.add(convert("expand"+i,"form"+i,1,"form"+(i-1),9));
            }
            var result=SimpleValuationSolver.solve(seeds,new ProductionGraph(recipes,List.of()));
            for(int i=1;i<=length;i++) {
                var a=result.values().get("form"+(i-1));var b=result.values().get("form"+i);
                check(b.amount()==9*a.amount(),"Every denomination keeps exact9:1, including wide/bounded families");
                for(String family:List.of(GATHERING,UTILITY)) check(b.route().getOrDefault(family,0L)==9*a.route().getOrDefault(family,0L),"Each routed category preserves exact ratio");
                check(b.amount()<=ProceduralValuationSettings.MAX_VALUE,"Whole family fits micro-ledger without per-form saturation");
            }
            check(result.values().get("form0").amount() == (length==14?0:length==13?3:18),"One primitive denomination chosen before scaling and bounded once");
            if(length==14)check(result.values().get("form0").status().startsWith("UNREPRESENTABLE"),"Unrepresentable family diagnosed explicitly");
        }
    }
    private static void recipesAndRemainders() {
        var carried = solve(Map.of("ingot", seed(150,4), "empty",seed(24,0),
                        "filled",new SimpleValuationSolver.Seed(24,0,Map.of(UTILITY,1.0),Map.of("empty",1L)), "meal",seed(24,0)),
                convert("empty_container","ingot",3,"empty",1),
                recipe("meal",List.of(new ProductionGraph.Input(List.of("filled"),1,true)),
                        List.of(new ProductionGraph.Output("empty",1,1,true),new ProductionGraph.Output("meal",1,1,false)),true));
        check(carried.values().get("filled").amount()==450 && carried.values().get("ingot").amount()==150,
                "Generic returned container raises a filled fallback before conservation, without devaluing its material family");
        var seeds=Map.of("filled",seed(120,3),"empty",seed(40,3),"meal",seed(100,3),"tool",seed(300,3));
        var process=recipe("container",List.of(new ProductionGraph.Input(List.of("filled"),1,true),new ProductionGraph.Input(List.of("tool"),1,false)),
                List.of(new ProductionGraph.Output("meal",2,1,false),new ProductionGraph.Output("empty",1,1,true)),true);
        var r=solve(seeds,process);
        check(2*r.values().get("meal").amount()+r.values().get("empty").amount()<=r.values().get("filled").amount(),"Output counts and returned containers jointly fit paid material");
        check(r.values().get("tool").amount()==300,"Reusable setup is not paid as consumed material");
        var cheapest=solve(Map.of("cheap",seed(7,3),"expensive",seed(80,3),"out",seed(50,3)),
                recipe("choice",List.of(new ProductionGraph.Input(List.of("expensive","cheap"),2,true)),List.of(new ProductionGraph.Output("out",1,1,false)),true));
        check(cheapest.values().get("out").amount()==14,"Every known alternative participates in conservation");
        var gain=solve(Map.of("a",seed(4,3),"b",seed(4,3)),convert("gain","a",1,"b",2),convert("return","b",1,"a",1));
        check(gain.values().get("a").amount()==0&&gain.values().get("b").amount()==0,"A proved material amplification loop cannot retain positive payout");
        check(gain.values().get("a").status().startsWith("ZERO_REQUIRED"),"Constraint zero is distinguished from absent source evidence");
    }
    private static void cosmeticAndOrder() {
        Map<String,SimpleValuationSolver.Seed> seeds=new LinkedHashMap<>();seeds.put("material",seed(72,4));
        List<ProductionGraph.Process> recipes=new ArrayList<>();
        for(int i=0;i<200;i++) { String variant="variant"+i;seeds.put(variant,seed(32,1));recipes.add(convert("v"+i,"material",1,variant,1));recipes.add(convert("r"+i,variant,1,"material",1)); }
        var result=SimpleValuationSolver.solve(seeds,new ProductionGraph(recipes,List.of()));
        check(result.values().values().stream().allMatch(x->x.amount()==72),"Two hundred cosmetic alternatives never inflate material value");
        Collections.reverse(recipes);var reversed=new TreeMap<String,SimpleValuationSolver.Seed>(Comparator.reverseOrder());reversed.putAll(seeds);
        check(result.equals(SimpleValuationSolver.solve(reversed,new ProductionGraph(recipes,List.of()))),"Recipe and item iteration order cannot change results");
    }
    private static void boundedWork() {
        Map<String,SimpleValuationSolver.Seed> seeds=new TreeMap<>();List<ProductionGraph.Process> recipes=new ArrayList<>();
        seeds.put("i0",seed(1,3));
        for(int i=1;i<40;i++){seeds.put("i"+i,seed(1000000,3));recipes.add(convert("p"+i,"i"+(i-1),1,"i"+i,1));}
        var result=SimpleValuationSolver.solve(seeds,new ProductionGraph(recipes,List.of()));
        check(result.passes()==ProceduralValuationSettings.MAX_ESTIMATE_PASSES&&result.unresolvedProcesses()>0,"Long chains stop at the fixed estimate-work bound and retain unresolved diagnostics");
        check(result.values().get("i39").status().contains("PASS_LIMIT"),"A bounded estimate is never presented as a complete conservation proof");
    }
    private static void nativeProperties() {
        var food=ProceduralValuationEngine.measure(Items.BREAD);var tool=ProceduralValuationEngine.measure(Items.IRON_PICKAXE);var weapon=ProceduralValuationEngine.measure(Items.IRON_SWORD);
        check(food.seed().route().containsKey("essence_ascendance:vitality"),"Food component supplies vitality identity");
        check(tool.seed().route().containsKey(GATHERING),"Native harvesting type supplies Gathering identity");
        check(weapon.seed().route().containsKey("essence_ascendance:offense"),"Native weapon type supplies Offense identity");
        check(ProceduralValuationEngine.measure(Items.ENCHANTED_BOOK).seed().retainedMaterials().get("minecraft:book")==4,
                "Native stored-enchantment component receives a carrier-relative effect budget");
        check(ProceduralValuationEngine.measure(Items.PAPER).seed().value()>0,"Plain unknown materials retain fallback");
        var ordinary = ProceduralValuationEngine.measure(Items.PAPER);
        var special = ProceduralValuationEngine.measure(Items.HEART_OF_THE_SEA);
        var glint = ProceduralValuationEngine.measure(Items.NETHER_STAR);
        var epic = ProceduralValuationEngine.measure(Items.HEAVY_CORE);
        check(special.seed().value() > 20 * ordinary.seed().value(),
                "Native declared rarity gives recipe-less special ingredients a tangible premium over a generic item");
        check(glint.seed().value() > special.seed().value()
                        && glint.signals().stream().anyMatch(s -> s.contains("explicit default glint")),
                "Explicit native default glint distinguishes special ingredients without an item-name rule");
        check(epic.seed().value() > glint.seed().value(),
                "Higher declared rarity remains distinct from an uncommon ingredient with intrinsic glint");
        check(glint.seed().route().equals(ordinary.seed().route()),
                "Rarity and glint do not invent gameplay function or change an unknown item's route");
        var priced = solve(Map.of("cheap", seed(7,3), "rare", epic.seed()),
                convert("cheap_rare", "cheap",2,"rare",1));
        check(priced.values().get("rare").amount() == 14,
                "A cheap published recipe still constrains a high-rarity property estimate; no post-solver rarity floor");
        var material = ProceduralValuationEngine.measure(Items.PAPER, 4.0);
        check(material.seed().value() > ordinary.seed().value(),
                "An explicit shared material factor reaches the seed before recipe solving");
        check(GeneratedYieldEligibility.decide(Items.PAPER.getDefaultInstance(),BuiltInRegistries.ITEM.getKey(Items.PAPER),24,false,false).eligible(),"Positive estimate allowed without falsely claiming acquisition");
        check(!GeneratedYieldEligibility.decide(Items.BARRIER.getDefaultInstance(),BuiltInRegistries.ITEM.getKey(Items.BARRIER),24,false,false).eligible(),"Native administrative items remain excluded");
    }
    private static void check(boolean test,String message){checks++;if(!test)throw new AssertionError(message);}
}

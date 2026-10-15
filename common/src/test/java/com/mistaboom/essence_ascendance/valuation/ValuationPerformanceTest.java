package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import java.util.*;

/** Work-bound and determinism replay. Timing is diagnostic, not a native-pack benchmark. */
public final class ValuationPerformanceTest {
    public static void main(String[] args) {
        Map<String,SimpleValuationSolver.Seed> seeds=new TreeMap<>();List<ProductionGraph.Process> recipes=new ArrayList<>();
        for(int i=0;i<10000;i++)seeds.put("fixture:item"+i,new SimpleValuationSolver.Seed(24+i%101,3,Map.of("essence_ascendance:utility",1.0)));
        for(int i=1;i<10000;i++)recipes.add(new ProductionGraph.Process("fixture:r"+i,"minecraft:crafting",
                List.of(new ProductionGraph.Input(List.of("fixture:item"+(i-1)),1,true)),
                List.of(new ProductionGraph.Output("fixture:item"+i,1,1,false)),0,0,"fixture",1,Map.of("conservation_complete","true")));
        long start=System.nanoTime();var forward=SimpleValuationSolver.solve(seeds,new ProductionGraph(recipes,List.of()));
        Collections.reverse(recipes);var reverse=new TreeMap<String,SimpleValuationSolver.Seed>(Comparator.reverseOrder());reverse.putAll(seeds);
        var backward=SimpleValuationSolver.solve(reverse,new ProductionGraph(recipes,List.of()));
        if(!forward.equals(backward)||forward.values().size()!=10000||forward.passes()>ProceduralValuationSettings.MAX_ESTIMATE_PASSES)
            throw new AssertionError("Bounded estimates changed with traversal order or lost items");
        System.out.println("ValuationPerformanceTest PASS: 10000 items, 9999 recipes, two orders; passes="+forward.passes()+"; elapsed ms="+(System.nanoTime()-start)/1000000+"; synthetic only");
    }
}

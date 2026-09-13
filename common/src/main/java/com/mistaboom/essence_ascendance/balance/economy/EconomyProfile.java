package com.mistaboom.essence_ascendance.balance.economy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record EconomyProfile(Map<String, ResourceValue> resources,
                             List<EconomyConservationSolver.Invariant> invariants,
                             List<ProductionGraph.Process> processes, List<String> warnings, int solverPasses,
                             EconomyProcessingPolicy processingPolicy) {
    public EconomyProfile {
        resources = Collections.unmodifiableMap(new TreeMap<>(resources));
        invariants = List.copyOf(invariants); processes = List.copyOf(processes);
        warnings = warnings.stream().sorted().distinct().toList();
        java.util.Objects.requireNonNull(processingPolicy, "Missing economy processing policy");
    }
    public record ResourceValue(EconomicValue economicValue, DissolutionYield dissolutionYield,
                                Map<String, Double> routedYields, List<String> warnings) {
        public ResourceValue {
            routedYields = Collections.unmodifiableMap(new TreeMap<>(routedYields));
            warnings = List.copyOf(warnings);
            long total = 0;
            for (double amount : routedYields.values()) total = Math.addExact(total, FractionalAmountService.units(amount));
            if (total != dissolutionYield.microUnits()) throw new IllegalArgumentException("Routing must exactly equal dissolution yield");
        }
    }
}

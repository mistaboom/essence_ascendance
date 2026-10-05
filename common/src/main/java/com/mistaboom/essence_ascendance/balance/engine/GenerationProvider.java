package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot;

/** Generation-only contract. Dependency discovery must not link absent optional classes. */
public interface GenerationProvider {
    String id();
    default int priority() { return 0; }
    /** Stable mod IDs only. The coordinator checks these before calling optional adapter code. */
    default java.util.List<String> dependencyModIds() { return java.util.List.of(); }
    /** Fail closed by default. Advisory adapters may explicitly opt out before emitting anything. */
    default boolean requiredForGeneration() { return true; }
    /** Probe once before either collection hook. AVAILABLE means the adapter accepts this data epoch. */
    default ProviderReadiness readiness(GenerationDataSnapshot inputs) { return ProviderReadiness.available(); }
}

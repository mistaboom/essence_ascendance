package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot;

/** Generation-only contract. Dependency discovery must not link absent optional classes. */
public interface GenerationProvider {
    String id();
    default int priority() { return 0; }
    /** Stable mod IDs only. The coordinator checks these before calling optional adapter code. */
    default java.util.List<String> dependencyModIds() { return java.util.List.of(); }
    /** Diagnostic scope of the adapter, not proof that a capability exists or is attainable.
     * Static enum values only; this declaration must not discover registries or optional APIs. */
    default java.util.Set<CapabilityAxis> capabilityAxes() { return java.util.Set.of(); }
    /** Quality/provenance requirement only. Optional compatibility never prevents generation or loading. */
    default boolean requiredForGeneration() { return true; }
    /** Probe once before either collection hook. AVAILABLE means the adapter accepts this data epoch. */
    default ProviderReadiness readiness(GenerationDataSnapshot inputs) { return ProviderReadiness.available(); }
}

package com.mistaboom.essence_ascendance.balance.engine;

/**
 * Optional integrations register before generation. Initial analysis runs when
 * the Overworld is available, before spawn chunks and other levels are created.
 * Providers must use loaded data, remain deterministic, and never request chunk
 * generation, mutate worlds, or depend on players or fully started dimensions.
 */
public interface PackEvidenceProvider extends GenerationProvider {
    /** Source, attainability, recipe and gate claims needed by acquisition belong in the staged sink.
     * Shared inputs are read-only; a failed hook discards the entire sink. */
    default void beforeAcquisition(com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot inputs,
                                   com.mistaboom.essence_ascendance.balance.config.BalanceSettings settings,
                                   EvidenceSink sink) { }
    /**
     * Flight Speed compatibility requires separate attributable axis.FLIGHT and
     * axis.ABILITIES_FLYING_SPEED facts for the same reachable source. The latter
     * asserts that the source actually responds to Abilities#flyingSpeed; FLIGHT
     * alone, GLIDING, item names, and ordinary speed attributes do not assert it.
     * Include acquisition stage, confidence and a factual reason. Providers must
     * never use current player ownership/equipment to determine profile access.
     */
    void collect(PackEvidenceContext context, EvidenceSink sink);
    /** Generation-only competitive facts. Never writes Essence parameters or the runtime frontier.
     * Supply attainable configurations with acquisition witnesses and explicit unknowns; bounds without a
     * realizable witness remain candidates. The coordinator guards optional classes and versions first. */
    default void collectCapabilities(PackEvidenceContext context, java.util.Map<String, ResourceEvidence> resources,
                                     CapabilitySink sink) { }
}

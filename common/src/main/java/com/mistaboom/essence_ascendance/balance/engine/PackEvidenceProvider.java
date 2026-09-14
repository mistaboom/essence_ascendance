package com.mistaboom.essence_ascendance.balance.engine;

/** Optional integrations register before generation. Providers must be deterministic and server-safe. */
public interface PackEvidenceProvider {
    String id();
    default int priority() { return 0; }
    /** Source, attainability, recipe and gate claims needed by the acquisition solver belong here. */
    default void beforeAcquisition(net.minecraft.server.MinecraftServer server,
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
}

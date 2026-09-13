package com.mistaboom.essence_ascendance.balance.engine;

/** Optional integrations register before generation. Providers must be deterministic and server-safe. */
public interface PackEvidenceProvider {
    String id();
    default int priority() { return 0; }
    /** Source, attainability, recipe and gate claims needed by the acquisition solver belong here. */
    default void beforeAcquisition(net.minecraft.server.MinecraftServer server,
                                   com.mistaboom.essence_ascendance.balance.config.BalanceSettings settings,
                                   EvidenceSink sink) { }
    void collect(PackEvidenceContext context, EvidenceSink sink);
}

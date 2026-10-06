package com.mistaboom.essence_ascendance.balance.engine;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Optional provider hooks publish only complete evidence; failed adapters are reported and excluded. */
public final class PackEvidenceProviders {
    private static final Map<String, PackEvidenceProvider> PROVIDERS = new TreeMap<>();
    static { register(new com.mistaboom.essence_ascendance.balance.capability.SkyblockBuilderStartingSourcesProvider()); }
    private PackEvidenceProviders() { }
    public static synchronized void register(PackEvidenceProvider provider) {
        if (provider == null || provider.id() == null || provider.id().isBlank()) throw new IllegalArgumentException("Provider needs a stable ID");
        if (PROVIDERS.putIfAbsent(provider.id(), provider) != null) throw new IllegalArgumentException("Duplicate evidence provider " + provider.id());
    }
    public static synchronized List<PackEvidenceProvider> all() { return List.copyOf(PROVIDERS.values()); }
}

package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import java.util.List;

/** Optional facade; reflective reader is reached only after dependency/version probing in generation. */
public final class LootrProvider implements GenerationProvider {
    public String id() { return "lootr"; }
    public List<String> dependencyModIds() { return List.of("lootr"); }
    public ProviderReadiness readiness(GenerationDataSnapshot inputs) {
        if (inputs.installedVersion("neoforge") == null) return new ProviderReadiness(ProviderReadiness.Status.UNSUPPORTED,
                inputs.installedVersion("lootr"), "Installed Lootr loader implementation is unaudited; NeoForge API only");
        return readiness(inputs.installedVersion("lootr"), LootrReader.ready(inputs));
    }
    public static ProviderReadiness readiness(String version, boolean ready) {
        if (version == null) return new ProviderReadiness(ProviderReadiness.Status.ABSENT, "unknown", "Optional Lootr absent");
        if (!"1.21.1-1.11.38.126".equals(version)) return new ProviderReadiness(ProviderReadiness.Status.UNSUPPORTED, version,
                "Audited NeoForge Lootr 1.21.1-1.11.38.126 only; other APIs require an audit");
        return new ProviderReadiness(ready ? ProviderReadiness.Status.PARTIALLY_SUPPORTED : ProviderReadiness.Status.NOT_READY, version,
                "Effective public settings and source scope only; eligible-container conversion and position-dependent rules remain conditional", ready);
    }
    public static void capture(GenerationDataSnapshot inputs, GenerationProviders runs, BalanceOverrides overrides) {
        LootrProvider provider = new LootrProvider();
        if (!runs.prepare("loot", provider, GenerationProviders.disabled(provider.id(), overrides))) return;
        com.mistaboom.essence_ascendance.balance.generated.BalancePerformance.increment("loot_settings_captures");
        inputs.lootr(runs.run("loot", provider, "settings", () -> LootrReader.capture(inputs)));
        runs.emitted("loot", provider, 1, 0, .8);
    }
    public static void recordSources(GenerationDataSnapshot inputs, GenerationProviders runs, ValuationEvidenceSnapshot evidence) {
        if (!inputs.lootr().installed()) return;
        long links = evidence.sources().values().stream().flatMap(List::stream).filter(source -> source.availability() != null
                && source.availability().scope() != SourceAvailability.Scope.SHARED).count();
        long proven = evidence.sources().values().stream().flatMap(List::stream).filter(source -> source.availability() != null
                && (source.availability().scope() == SourceAvailability.Scope.PLAYER || source.availability().scope() == SourceAvailability.Scope.TEAM)).count();
        com.mistaboom.essence_ascendance.balance.generated.BalancePerformance.count("lootr_source_links_with_rules", links);
        com.mistaboom.essence_ascendance.balance.generated.BalancePerformance.count("lootr_proven_personalized_source_links", proven);
        runs.emitted("loot", new LootrProvider(), 0, links, .8);
    }
}

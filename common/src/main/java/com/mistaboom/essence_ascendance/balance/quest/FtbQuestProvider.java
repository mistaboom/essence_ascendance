package com.mistaboom.essence_ascendance.balance.quest;

import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot;
import dev.architectury.platform.Platform;
import java.nio.file.*;
import java.util.List;

/** Optional facade: optional APIs are loaded only after exact installed-version checks. */
public final class FtbQuestProvider implements GenerationProvider {
    public String id() { return "ftbquests"; }
    public List<String> dependencyModIds() { return List.of("ftbquests", "ftblibrary", "ftbteams", "architectury"); }
    public ProviderReadiness readiness(GenerationDataSnapshot inputs) {
        String version = inputs.installedVersion("ftbquests");
        if (!supported(version, inputs.installedVersion("ftblibrary"), inputs.installedVersion("ftbteams"), inputs.installedVersion("architectury")))
            return new ProviderReadiness(ProviderReadiness.Status.UNSUPPORTED, version,
                    "Audited API tuples (Quests/Library/Teams): 2101.1.36/2101.1.36/2101.1.11 or 2101.1.30/2101.1.35/2101.1.10; Architectury 13.0.11");
        return FtbQuestReader.readiness(inputs.server(), Platform.getConfigFolder().resolve("ftbquests/quests"), version);
    }
    public static boolean supported(String quests, String library, String teams, String architectury) {
        return "13.0.11".equals(architectury) && (
                "2101.1.36".equals(quests) && "2101.1.36".equals(library) && "2101.1.11".equals(teams)
                || "2101.1.30".equals(quests) && "2101.1.35".equals(library) && "2101.1.10".equals(teams));
    }
    public static ProviderReadiness definitionReadiness(String version, boolean ownsServer, boolean loading,
                                                        boolean initialized, boolean definitionSourcePresent) {
        if (!ownsServer || loading) return new ProviderReadiness(ProviderReadiness.Status.NOT_READY, version,
                "Quest service loading, failed, or owned by another server; not authoritative empty data");
        if (!initialized && !definitionSourcePresent) return new ProviderReadiness(ProviderReadiness.Status.NOT_READY, version,
                "Uninitialized service without authoritative format-13 data.snbt");
        return new ProviderReadiness(ProviderReadiness.Status.PARTIALLY_SUPPORTED, version,
                (initialized ? "Loaded effective server definitions" : "Early definition-only server SNBT load-source snapshot")
                        + "; typed tasks/rewards supported; opaque behavior unresolved; no team/player access", true);
    }
    public static void capture(GenerationDataSnapshot inputs, GenerationProviders runs,
                               com.mistaboom.essence_ascendance.balance.config.BalanceOverrides overrides) {
        FtbQuestProvider provider = new FtbQuestProvider();
        if (!runs.prepare("quests", provider, GenerationProviders.disabled(provider.id(), overrides))) return;
        runs.run("quests", provider, "normalize", () ->
                FtbQuestReader.capture(inputs.server(), Platform.getConfigFolder().resolve("ftbquests/quests"), inputs.installedVersion("ftbquests"))).ifPresent(evidence -> {
        inputs.quests(evidence);
        runs.emitted("quests", provider, evidence.quests().stream().mapToLong(q -> q.tasks().size()).sum(),
                evidence.quests().stream().mapToLong(q -> q.rewards().size()).sum(), .85);
        });
    }
}

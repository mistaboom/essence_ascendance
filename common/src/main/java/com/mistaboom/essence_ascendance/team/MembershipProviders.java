package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.OptionalIntegration;
import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.function.BiConsumer;

/** Server-wide optional discovery. Only our registered adapters are reflectively constructed. */
public final class MembershipProviders {
    public record Entry(MembershipAuthority authority, String modId, String adapter, List<Map<String, String>> versions) { }
    public record Installed(ProviderReadiness readiness, MembershipProvider provider) { }
    private static final List<Entry> ENTRIES = List.of(
            new Entry(MembershipAuthority.FTB_TEAMS, "ftbteams",
                    "com.mistaboom.essence_ascendance.team.compat.FtbTeamsMembershipProvider",
                    List.of(Map.of("ftbteams", "2101.1.11", "ftblibrary", "2101.1.30"),
                            Map.of("ftbteams", "2101.1.11", "ftblibrary", "2101.1.36"),
                            Map.of("ftbteams", "2101.1.11", "ftblibrary", "2101.1.37"))));
    private MembershipProviders() { }
    public static List<Entry> entries() { return ENTRIES; }
    public static ProviderReadiness support(Entry entry, Map<String, String> mods, String loader) {
        if (!mods.containsKey(entry.modId())) return status(ProviderReadiness.Status.ABSENT, "absent", entry.authority().id() + " absent");
        String version = mods.get(entry.modId());
        if (!(loader.equals("fabric") || loader.equals("neoforge"))
                || entry.versions().stream().noneMatch(tuple -> tuple.entrySet().stream()
                        .allMatch(v -> v.getValue().equals(mods.get(v.getKey())))))
            return status(ProviderReadiness.Status.UNSUPPORTED, version,
                    "Installed " + entry.authority().id() + " unsupported on " + loader + ": " + mods
                            + "; audited Minecraft 1.21.1 requirements " + entry.versions());
        return status(ProviderReadiness.Status.NOT_READY, version, entry.authority().id() + " supported; awaiting server startup");
    }
    public static Map<MembershipAuthority, Installed> discover(Map<String, String> mods, String loader,
                                                              BiConsumer<MinecraftServer, MembershipAuthority> invalidate) {
        Map<MembershipAuthority, Installed> result = new EnumMap<>(MembershipAuthority.class);
        result.put(MembershipAuthority.NATIVE, new Installed(NativeMembershipService.READY, null));
        for (Entry entry : ENTRIES) {
            ProviderReadiness readiness = support(entry, mods, loader); MembershipProvider provider = null;
            if (readiness.status() == ProviderReadiness.Status.NOT_READY) {
                var attempt = OptionalIntegration.attempt(entry.authority().id(), "load adapter/register events", () -> {
                    try {
                        MembershipProvider adapter = (MembershipProvider) Class.forName(entry.adapter()).getConstructor().newInstance();
                        adapter.installInvalidation(server -> invalidate.accept(server, entry.authority())); return adapter;
                    } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unable to construct registered adapter", failure); }
                });
                if (attempt.succeeded()) provider = attempt.value().orElseThrow();
                else readiness = status(ProviderReadiness.Status.FAILED, readiness.version(), attempt.failure());
            }
            if (readiness.status() == ProviderReadiness.Status.UNSUPPORTED)
                OptionalIntegration.warn(entry.authority().id(), readiness.detail());
            result.put(entry.authority(), new Installed(readiness, provider));
        }
        return Map.copyOf(result);
    }
    public static Map<MembershipAuthority, ProviderReadiness> statuses(Map<MembershipAuthority, Installed> providers) {
        Map<MembershipAuthority, ProviderReadiness> result = new EnumMap<>(MembershipAuthority.class);
        providers.forEach((authority, installed) -> result.put(authority, installed.readiness())); return Map.copyOf(result);
    }
    private static ProviderReadiness status(ProviderReadiness.Status state, String version, String detail) {
        return new ProviderReadiness(state, version, detail);
    }
}

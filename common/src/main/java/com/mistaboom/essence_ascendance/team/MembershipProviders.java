package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.OptionalIntegration;
import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import net.minecraft.server.MinecraftServer;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** Server-wide optional discovery. Only our registered adapters are reflectively constructed. */
public final class MembershipProviders {
    public record Entry(MembershipAuthority authority, String modId, String adapter) { }
    public record Installed(ProviderReadiness readiness, MembershipProvider provider) { }
    private static final List<Entry> ENTRIES = List.of(
            new Entry(MembershipAuthority.FTB_TEAMS, "ftbteams",
                    "com.mistaboom.essence_ascendance.team.compat.FtbTeamsMembershipProvider"));
    private MembershipProviders() { }
    public static List<Entry> entries() { return ENTRIES; }
    public static ProviderReadiness support(Entry entry, Map<String, String> mods, String loader) {
        if (!mods.containsKey(entry.modId())) return status(ProviderReadiness.Status.ABSENT, "absent", entry.authority().id() + " absent");
        String version = Objects.requireNonNullElse(mods.get(entry.modId()), "unknown");
        return status(ProviderReadiness.Status.NOT_READY, version,
                entry.authority().id() + " installed on " + loader + "; awaiting live API and server readiness");
    }
    public static Map<MembershipAuthority, Installed> discover(Map<String, String> mods, String loader,
                                                              BiConsumer<MinecraftServer, MembershipAuthority> invalidate) {
        return discover(mods, loader, invalidate, MembershipProviders::construct);
    }
    static Map<MembershipAuthority, Installed> discover(Map<String, String> mods, String loader,
            BiConsumer<MinecraftServer, MembershipAuthority> invalidate,
            BiFunction<Entry, String, MembershipProvider> factory) {
        Map<MembershipAuthority, Installed> result = new EnumMap<>(MembershipAuthority.class);
        result.put(MembershipAuthority.NATIVE, new Installed(NativeMembershipService.READY, null));
        for (Entry entry : ENTRIES) {
            ProviderReadiness readiness = support(entry, mods, loader); MembershipProvider provider = null;
            if (readiness.status() == ProviderReadiness.Status.NOT_READY) {
                String version = readiness.version();
                var attempt = OptionalIntegration.attempt(entry.authority().id(), "load installed API adapter",
                        () -> factory.apply(entry, version));
                if (attempt.succeeded()) {
                    provider = attempt.value().orElseThrow();
                    MembershipProvider enabled = provider;
                    var events = OptionalIntegration.attempt(entry.authority().id(), "register optional membership events", () -> {
                        enabled.installInvalidation(server -> invalidate.accept(server, entry.authority()));
                        return Boolean.TRUE;
                    });
                    if (!events.succeeded()) readiness = status(ProviderReadiness.Status.NOT_READY, version,
                            "Membership event hooks failed: " + events.failure()
                                    + "; live authorization and periodic roster polling remain enabled");
                } else readiness = status(ProviderReadiness.Status.FAILED, version, attempt.failure());
            }
            result.put(entry.authority(), new Installed(readiness, provider));
        }
        return Map.copyOf(result);
    }
    private static MembershipProvider construct(Entry entry, String version) {
        try {
            return (MembershipProvider) Class.forName(entry.adapter()).getConstructor(String.class).newInstance(version);
        } catch (InvocationTargetException failure) {
            // Reflection must not turn a fatal VM error into a recoverable integration failure.
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Unable to construct registered adapter " + entry.adapter() + ": " + cause, cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to construct registered adapter " + entry.adapter() + ": " + failure, failure);
        }
    }
    public static Map<MembershipAuthority, ProviderReadiness> statuses(Map<MembershipAuthority, Installed> providers) {
        Map<MembershipAuthority, ProviderReadiness> result = new EnumMap<>(MembershipAuthority.class);
        providers.forEach((authority, installed) -> result.put(authority, installed.readiness())); return Map.copyOf(result);
    }
    private static ProviderReadiness status(ProviderReadiness.Status state, String version, String detail) {
        return new ProviderReadiness(state, version, detail);
    }
}

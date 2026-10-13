package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;

import java.util.Map;
import java.util.Objects;

/** One authority per world. A provider's unready result never means an empty roster. */
public enum MembershipAuthority {
    NATIVE, FTB_TEAMS, OPENPAC;

    public enum Selection { AUTO, NATIVE, FTB_TEAMS, OPENPAC }

    public String displayName() { return switch (this) { case NATIVE -> "Essence Ascendance"; case FTB_TEAMS -> "FTB Teams"; case OPENPAC -> "Open Parties and Claims"; }; }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public static Selection selection(String id) {
        try { return Selection.valueOf(id.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Unknown membership authority '" + id + "'; use auto, native, ftb_teams or openpac");
        }
    }

    public record Decision(MembershipAuthority authority, ProviderReadiness readiness,
                           boolean authorityChangeRequired) {
        public boolean usable() { return !authorityChangeRequired && ready(readiness); }
    }

    public static boolean ready(ProviderReadiness readiness) {
        return readiness.status() == ProviderReadiness.Status.AVAILABLE
                && readiness.requiredEvidenceComplete();
    }

    /** Selection is policy. Established authority is retained through absence or API failure. */
    public static Decision select(Selection requested, MembershipAuthority established,
                                  Map<MembershipAuthority, ProviderReadiness> providers) {
        Objects.requireNonNull(requested);
        Objects.requireNonNull(providers);
        MembershipAuthority selected;
        if (requested == Selection.AUTO && established != null) {
            selected = established;
        } else if (requested != Selection.AUTO) {
            selected = valueOf(requested.name());
        } else {
            // A supported provider that is still starting must not cause a native fallback.
            selected = NATIVE;
            for (MembershipAuthority candidate : new MembershipAuthority[]{FTB_TEAMS, OPENPAC}) {
                ProviderReadiness status = status(providers, candidate);
                if (status.status() == ProviderReadiness.Status.AVAILABLE || status.status() == ProviderReadiness.Status.NOT_READY
                        || status.status() == ProviderReadiness.Status.FAILED) {
                    selected = candidate;
                    break;
                }
            }
            // With no supported external authority, an installed unusable one blocks native AUTO.
            if (selected == NATIVE) for (MembershipAuthority candidate : new MembershipAuthority[]{FTB_TEAMS, OPENPAC}) {
                if (status(providers, candidate).status() != ProviderReadiness.Status.ABSENT) {
                    selected = candidate; break;
                }
            }
        }
        return new Decision(selected, status(providers, selected),
                established != null && selected != established);
    }

    private static ProviderReadiness status(Map<MembershipAuthority, ProviderReadiness> providers,
                                             MembershipAuthority authority) {
        return providers.getOrDefault(authority, new ProviderReadiness(
                ProviderReadiness.Status.ABSENT, "none", "No supported membership adapter available"));
    }
}

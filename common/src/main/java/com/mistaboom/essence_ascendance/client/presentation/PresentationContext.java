package com.mistaboom.essence_ascendance.client.presentation;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.client.ClientPacketDispatch;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable input boundary for documentation and other read-only presentation.
 * Bootstrap defaults are intentionally excluded: READY always means an
 * installed server/integrated-server runtime or synchronized player snapshot.
 */
public record PresentationContext(Runtime runtime, Player player, Revision revision) {
    public enum Availability { READY, LOADING, UNAVAILABLE, CONTEXT_REQUIRED }

    public PresentationContext {
        Objects.requireNonNull(runtime);
        Objects.requireNonNull(player);
        Objects.requireNonNull(revision);
    }

    public static PresentationContext capture() {
        RuntimeBalanceDefinition installed = EssenceConfigManager.clientRuntime();
        if (installed == null) installed = EssenceConfigManager.serverRuntime();
        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        Availability runtimeAvailability = installed == null ? Availability.LOADING : Availability.READY;
        if (installed != null && snapshot.ready() && snapshot.balanceProfileId() != null
                && !installed.config().balanceProfile().id().equals(snapshot.balanceProfileId())) {
            // Do not combine a newly synchronized player state with an old profile.
            installed = null;
            runtimeAvailability = Availability.LOADING;
        }
        Runtime runtime = new Runtime(runtimeAvailability, installed);
        Player player = snapshot.ready() ? Player.ready(snapshot) : Player.loading();
        return new PresentationContext(runtime, player, new Revision(
                System.identityHashCode(installed),
                snapshot.ready() ? snapshot.playerRevision() : -1,
                snapshot.ready(),
                ClientPacketDispatch.presentationEpoch(),
                System.identityHashCode(Language.getInstance())));
    }

    /** Static-catalog context for callers that deliberately have no player. */
    public static PresentationContext catalog(RuntimeBalanceDefinition runtime) {
        Objects.requireNonNull(runtime);
        return new PresentationContext(new Runtime(Availability.READY, runtime), Player.contextRequired(),
                new Revision(System.identityHashCode(runtime), -1, false, -1, 0));
    }

    public record Runtime(Availability availability, RuntimeBalanceDefinition definition) {
        public Runtime {
            Objects.requireNonNull(availability);
            if ((availability == Availability.READY) != (definition != null))
                throw new IllegalArgumentException("Runtime readiness and value disagree");
        }
        public boolean ready() { return availability == Availability.READY; }
    }

    public record Player(Availability availability, ClientEssenceState.Snapshot snapshot,
                         Map<ResourceLocation, Long> bonusTotals) {
        public Player {
            Objects.requireNonNull(availability);
            bonusTotals = Map.copyOf(bonusTotals);
            if ((availability == Availability.READY) != (snapshot != null && snapshot.ready()))
                throw new IllegalArgumentException("Player readiness and snapshot disagree");
        }

        public static Player ready(ClientEssenceState.Snapshot snapshot) {
            Map<ResourceLocation, Long> totals = new LinkedHashMap<>();
            snapshot.stats().forEach((statId, state) -> EssenceStatRegistry.get(statId).ifPresent(stat ->
                    totals.merge(stat.essenceType().id(), Math.max(0L, state.storedInvestment()),
                            (left, right) -> Math.addExact(left, right))));
            return new Player(Availability.READY, snapshot, totals);
        }

        public static Player loading() { return new Player(Availability.LOADING, null, Map.of()); }
        public static Player unavailable() { return new Player(Availability.UNAVAILABLE, null, Map.of()); }
        public static Player contextRequired() { return new Player(Availability.CONTEXT_REQUIRED, null, Map.of()); }
        public boolean ready() { return availability == Availability.READY; }
    }

    /** Cache key: runtime, player, connection and language all invalidate independently. */
    public record Revision(int runtimeIdentity, long playerRevision, boolean playerReady,
                           long connectionEpoch, int languageIdentity) { }
}

package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.function.Consumer;

/** Optional provider contract; no optional classes may appear in this always-loaded boundary. */
public interface MembershipProvider {
    record Observation(ProviderReadiness readiness, List<MembershipRoster> teams) {
        public Observation { Objects.requireNonNull(readiness); teams = List.copyOf(teams); }
    }
    Observation capture(MinecraftServer server);
    /** Cheap live authorization probe: catches unannounced role/roster changes before access. */
    boolean isCurrent(MinecraftServer server, UUID member, MembershipSnapshot settled);
    /** Display-only identity; unavailable names never change membership or authorization. */
    default Optional<String> teamName(MinecraftServer server, UUID team) { return Optional.empty(); }
    void installInvalidation(Consumer<MinecraftServer> invalidate);
}

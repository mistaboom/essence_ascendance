package com.mistaboom.essence_ascendance.machine;

import com.mistaboom.essence_ascendance.balance.engine.OptionalIntegration;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.team.*;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/** Human-readable identity only. UUIDs remain the authoritative ownership keys. */
public final class MachineOwnerDisplay {
    private MachineOwnerDisplay() { }
    public static String playerName(MinecraftServer server, UUID player) {
        return playerName(server, player, "Unknown player");
    }
    private static String playerName(MinecraftServer server, UUID player, String fallback) {
        if (player == null) return fallback;
        var online = server.getPlayerList().getPlayer(player);
        if (online != null) return online.getGameProfile().getName();
        return server.getProfileCache() == null ? fallback
                : server.getProfileCache().get(player).map(profile -> profile.getName()).orElse(fallback);
    }
    public static String teamName(MinecraftServer server, PoolId pool) {
        if (pool == null || pool.kind() != PoolId.Kind.TEAM) return "Unknown team";
        if (pool.authority() == MembershipAuthority.NATIVE) {
            String name = EssenceSavedData.get(server).nativeMembership().name(pool);
            return name.equals(pool.id().toString()) ? "Unnamed team" : name;
        }
        var installed = MembershipRuntime.provider(pool.authority());
        if (installed == null || installed.provider() == null) return "Unknown team";
        return OptionalIntegration.attempt(pool.authority().id(), "read team display name",
                () -> installed.provider().teamName(server, pool.id())).value()
                .flatMap(value -> value).orElse("Unknown team");
    }
    public static String ownerName(MinecraftServer server, MachineBinding binding, UUID placer, String placerName) {
        if (binding.pool() == null) return "Unbound";
        if (binding.pool().kind() == PoolId.Kind.TEAM) return teamName(server, binding.pool());
        String fallback = binding.personalOwner() != null && binding.personalOwner().equals(placer)
                && placerName != null && !placerName.isBlank() ? placerName : "Unknown player";
        return playerName(server, binding.personalOwner(), fallback);
    }
}

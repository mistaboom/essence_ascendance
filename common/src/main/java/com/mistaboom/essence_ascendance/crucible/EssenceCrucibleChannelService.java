package com.mistaboom.essence_ascendance.crucible;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/* Runtime-only ownership of active player -> Crucible channel links. */
public final class EssenceCrucibleChannelService {

    private static final Map<UUID, EssenceCrucibleBlockEntity> ACTIVE_BY_PLAYER =
            new HashMap<>();

    private EssenceCrucibleChannelService() {
    }

    static void claim(
            ServerPlayer player,
            EssenceCrucibleBlockEntity crucible
    ) {
        EssenceCrucibleBlockEntity previous =
                ACTIVE_BY_PLAYER.put(
                        player.getUUID(),
                        crucible
                );

        if (previous != null && previous != crucible) {
            previous.stopChanneling();
        }
    }

    static void release(
            UUID playerId,
            EssenceCrucibleBlockEntity crucible
    ) {
        ACTIVE_BY_PLAYER.remove(
                playerId,
                crucible
        );
    }

    public static void stopForPlayer(ServerPlayer player) {
        EssenceCrucibleBlockEntity crucible =
                ACTIVE_BY_PLAYER.remove(
                        player.getUUID()
                );

        if (crucible != null) {
            crucible.stopChanneling();
        }
    }
}

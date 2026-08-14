package com.mistaboom.essence_ascendance.equipment;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;

/*
 * Cross-loader event wiring for equipment gameplay effects.
 *
 * Architectury's common player tick runs on both Fabric and NeoForge. The
 * service itself rejects client-side player instances by requiring
 * ServerPlayer, so all authoritative attribute changes originate on the
 * logical server and vanilla attribute synchronization handles the client.
 */
public final class EquipmentGameplayEvents {

    private static boolean initialized = false;

    private EquipmentGameplayEvents() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        TickEvent.PLAYER_POST.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                EquipmentAttributeService.sync(serverPlayer);
            }
        });

        PlayerEvent.PLAYER_QUIT.register(
                player -> {
                    EquipmentAttributeService.forget(player);
                    EquipmentDamageService.forget(player);
                }
        );

        initialized = true;
    }
}

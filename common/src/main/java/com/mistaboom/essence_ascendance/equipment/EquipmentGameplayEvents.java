package com.mistaboom.essence_ascendance.equipment;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;

/*
 * Cross-loader event wiring for equipment gameplay effects.
 *
 * Architectury's common player tick runs on both Fabric and NeoForge. The
 * services reject client-side player instances by requiring ServerPlayer, so
 * authoritative attribute/vitality changes originate on the logical server.
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
                EquipmentVitalityService.tick(serverPlayer);
                EquipmentWeaponService.syncRangedVisualState(serverPlayer);
                EquipmentGatheringService.sync(serverPlayer);
            }
        });

        PlayerEvent.PLAYER_QUIT.register(
                player -> {
                    EquipmentAttributeService.forget(player);
                    EquipmentDamageService.forget(player);
                    EquipmentVitalityService.forget(player);
                    EquipmentWeaponService.forget(player);
                    EquipmentGatheringService.forget(player);
                }
        );

        initialized = true;
    }
}

package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappings;
import com.mistaboom.essence_ascendance.network.AscendanceNexusNetworkService;
import com.mistaboom.essence_ascendance.network.EssenceCrucibleNetworkService;
import com.mistaboom.essence_ascendance.network.EssencePylonNetworkService;
import com.mistaboom.essence_ascendance.network.ItemEssenceTooltipSyncService;
import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncService;
import com.mistaboom.essence_ascendance.network.SkillEffectHudSyncService;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
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

        ItemEssenceMappings.init();
        AscendanceNexusNetworkService.init();
        EssenceCrucibleNetworkService.init();
        EssencePylonNetworkService.init();
        ItemEssenceTooltipSyncService.init();
        PlayerEssenceSyncService.init();
        SkillEffectHudSyncService.init();
        com.mistaboom.essence_ascendance.network.AttunementMovementIntentService.init();
        com.mistaboom.essence_ascendance.network.MovementAbilityInputService.init();
        EquipmentTooltipSyncService.init();
        PlayerRuntimeLifecycleService.init();
        SoulboundEquipmentService.init();

        TickEvent.PLAYER_POST.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                SkillEffectRuntime.tick(serverPlayer);
                EquipmentDamageService.tickSkillInput(serverPlayer);
                EquipmentShieldService.tick(serverPlayer);
                EquipmentAttributeService.sync(serverPlayer);
                EquipmentMobilityService.sync(serverPlayer);
                EquipmentVitalityService.tick(serverPlayer);
                EquipmentWeaponService.syncWeaponVisualState(serverPlayer);
                EquipmentGatheringService.sync(serverPlayer);
                com.mistaboom.essence_ascendance.attunement.AttunementGameplay.tick(serverPlayer);
                EquipmentTooltipSyncService.sync(serverPlayer);
                // Send after the authoritative gameplay pass so the HUD sees
                // the same resolved state used by combat this tick.
                SkillEffectHudSyncService.syncIfNeeded(serverPlayer);
            }
        });

        initialized = true;
    }
}

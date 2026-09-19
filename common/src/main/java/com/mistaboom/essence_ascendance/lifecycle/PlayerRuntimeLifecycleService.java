package com.mistaboom.essence_ascendance.lifecycle;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleChannelService;
import com.mistaboom.essence_ascendance.equipment.EquipmentAttributeService;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService;
import com.mistaboom.essence_ascendance.equipment.EquipmentMobilityService;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.EquipmentTooltipSyncService;
import com.mistaboom.essence_ascendance.equipment.EquipmentVitalityService;
import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import com.mistaboom.essence_ascendance.network.EssenceCrucibleNetworkService;
import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncService;
import com.mistaboom.essence_ascendance.progression.DormantGuidebookService;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/*
 * Ordered lifecycle reconciliation for all runtime-only Essence Ascendance
 * player state.
 *
 * Saved progression remains in EssenceSavedData. This service owns only
 * transient gameplay caches/modifiers and client presentation snapshots.
 */
public final class PlayerRuntimeLifecycleService {

    private static boolean initialized = false;

    private PlayerRuntimeLifecycleService() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        PlayerEvent.PLAYER_JOIN.register(
                PlayerRuntimeLifecycleService::refresh
        );

        PlayerEvent.PLAYER_RESPAWN.register(
                (player, conqueredEnd, removalReason) ->
                        refresh(player)
        );

        PlayerEvent.CHANGE_DIMENSION.register(
                (player, oldLevel, newLevel) -> {
                    com.mistaboom.essence_ascendance.projectile.ProjectileRuntime.forgetOwner(player);
                    refresh(player);
                }
        );

        PlayerEvent.PLAYER_QUIT.register(
                PlayerRuntimeLifecycleService::forget
        );

        LifecycleEvent.SERVER_STARTING.register(server -> com.mistaboom.essence_ascendance.projectile.ProjectileRuntime.serverStarting());
        LifecycleEvent.SERVER_STOPPING.register(server -> com.mistaboom.essence_ascendance.projectile.ProjectileRuntime.serverStopping());
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            com.mistaboom.essence_ascendance.attunement.AttunementGameplay.clearAll();
            SkillEffectRuntime.clearAll();
            EquipmentDamageService.clearSkillInput();
            com.mistaboom.essence_ascendance.projectile.ProjectileRuntime.clearDiagnostics();
            CommittedSkillService.invalidateAll();
        });

        initialized = true;
    }

    /*
     * Clear anything that may have been copied/carried through the lifecycle
     * transition, then immediately rebuild the new authoritative state.
     */
    public static void refresh(
            ServerPlayer player
    ) {
        resetRuntime(
                player
        );

        refreshCurrentState(
                player
        );
    }

    /*
     * Reconcile every connected player after a server-wide balance/config
     * change. Config changes do NOT represent a player-identity transition, so
     * preserve existing equipment fractional carry. Skill combat timers and
     * modifiers reset so no value calculated under old tuning remains active.
     */
    public static void refreshAll(
            MinecraftServer server
    ) {
        CommittedSkillService.invalidateAll();
        for (ServerPlayer player :
                server.getPlayerList()
                        .getPlayers()) {

            // Skill timers/modifiers use provisional tuning; discard old
            // combat state before applying a newly loaded configuration.
            SkillEffectRuntime.reset(player);
            refreshCurrentState(
                    player
            );
        }
    }


    /**
     * Re-evaluate runtime gameplay effects and presentation immediately after
     * an authoritative progression mutation without clearing unrelated runtime
     * carry/state as a full lifecycle transition would.
     */
    public static void refreshProgressionState(
            ServerPlayer player
    ) {
        refreshCurrentState(player);
    }

    /**
     * Provider completion may happen while building a progression snapshot.
     * Refresh just skill gameplay here to avoid recursive network sends.
     */
    public static void refreshSkillState(ServerPlayer player) {
        CommittedSkillService.invalidate(player);
        SkillEffectRuntime.refresh(player);
    }

    /** Called by the authoritative death-completion hook, after cancellation. */
    public static void onDeath(ServerPlayer player) {
        com.mistaboom.essence_ascendance.vitality.VitalityDamageService.onDeath(player);
        com.mistaboom.essence_ascendance.attunement.AttunementGameplay.forget(player);
        com.mistaboom.essence_ascendance.projectile.ProjectileRuntime.forgetOwner(player);
        SkillEffectRuntime.reset(player);
        EquipmentDamageService.forgetSkillInput(player);
        CommittedSkillService.forget(player);
    }


    private static void refreshCurrentState(
            ServerPlayer player
    ) {
        DormantGuidebookService.deliverIfEligible(player);
        refreshSkillState(player);
        /*
         * Reapply the state that should be visible/usable immediately.
         *
         * Vitality's per-tick mechanics (regen, hunger/air accounting, status
         * duration accounting) intentionally resume on the normal player tick;
         * invoking tick() here would create an extra gameplay tick.
         */
        EquipmentAttributeService.sync(
                player
        );

        EquipmentMobilityService.sync(
                player
        );

        EquipmentWeaponService.syncWeaponVisualState(
                player
        );

        EquipmentShieldService.syncReadinessState(
                player
        );

        EquipmentGatheringService.sync(
                player
        );

        /*
         * Force both presentation snapshots now. If network negotiation is not
         * ready yet (possible during initial join), each service deliberately
         * leaves itself uncached and its normal periodic pass retries shortly.
         */
        PlayerEssenceSyncService.forceSync(
                player
        );

        EquipmentTooltipSyncService.forceSync(
                player
        );
    }

    /*
     * Remove/cancel all runtime state before rebuilding it.
     */
    private static void resetRuntime(
            ServerPlayer player
    ) {
        com.mistaboom.essence_ascendance.attunement.AttunementGameplay.forget(player);
        SkillEffectRuntime.reset(player);
        CommittedSkillService.forget(player);
        EquipmentAttributeService.resetTransientState(
                player
        );

        /*
         * Mobility forget() also restores the pre-Essence ability flight speed
         * and removes the mobility-owned transient attribute modifiers.
         */
        EquipmentMobilityService.forget(
                player
        );

        EquipmentDamageService.forget(
                player
        );

        EquipmentVitalityService.forget(
                player
        );

        EquipmentWeaponService.forget(
                player
        );

        EquipmentGatheringService.resetHeldState(
                player
        );

        EquipmentTooltipSyncService.forget(
                player
        );

        PlayerEssenceSyncService.forget(
                player
        );

        com.mistaboom.essence_ascendance.network.GatheringSurveySyncService.forget(player);

        EssenceCrucibleChannelService.stopForPlayer(
                player
        );

        EssenceCrucibleNetworkService.forget(
                player
        );
    }

    /*
     * Normal logout: discard runtime caches. Transient attributes are not
     * persisted, but restoring mobility's remembered flyingSpeed before the
     * entity disappears prevents a boosted ability baseline from leaking into
     * any later reuse/copy path.
     */
    public static void forget(
            ServerPlayer player
    ) {
        com.mistaboom.essence_ascendance.attunement.AttunementGameplay.forget(player);
        com.mistaboom.essence_ascendance.projectile.ProjectileRuntime.forgetOwner(player);
        SkillEffectRuntime.forget(player);
        CommittedSkillService.forget(player);
        EquipmentAttributeService.forget(
                player
        );

        EquipmentMobilityService.forget(
                player
        );

        EquipmentDamageService.forget(
                player
        );

        EquipmentVitalityService.forget(
                player
        );

        EquipmentWeaponService.forget(
                player
        );

        EquipmentGatheringService.forget(
                player
        );

        EquipmentTooltipSyncService.forget(
                player
        );

        PlayerEssenceSyncService.forget(
                player
        );

        com.mistaboom.essence_ascendance.network.GatheringSurveySyncService.forget(player);

        EssenceCrucibleChannelService.stopForPlayer(
                player
        );

        EssenceCrucibleNetworkService.forget(
                player
        );
    }
}

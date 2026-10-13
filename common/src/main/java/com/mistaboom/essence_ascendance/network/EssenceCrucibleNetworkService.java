package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleEssences;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleDissolutionMode;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleMenu;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureStats;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/*
 * Server-authoritative Crucible menu/channel networking.
 *
 * The C2S request deliberately contains no BlockPos. The server resolves the
 * block entity exclusively from the player's currently-open server menu, then
 * revalidates menu id, loaded block identity, ownership, and range.
 */
public final class EssenceCrucibleNetworkService {

    private static final int SCREEN_SYNC_INTERVAL_TICKS = 2;

    private static final Map<ServerPlayer, EssenceCrucibleStatePayload> LAST_SENT =
            new WeakHashMap<>();

    private static boolean initialized = false;

    private EssenceCrucibleNetworkService() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(
                    EssenceCrucibleStatePayload.TYPE,
                    EssenceCrucibleStatePayload.CODEC
            );
        }

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                EssenceCrucibleChannelPayload.TYPE,
                EssenceCrucibleChannelPayload.CODEC,
                (payload, context) ->
                        context.queue(
                                () -> {
                                    if (context.getPlayer() instanceof ServerPlayer player) {
                                        handleChannelRequest(player, payload);
                                    }
                                }
                        )
        );


        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                EssenceCrucibleStateRequestPayload.TYPE,
                EssenceCrucibleStateRequestPayload.CODEC,
                (payload, context) ->
                        context.queue(
                                () -> {
                                    if (context.getPlayer() instanceof ServerPlayer player) {
                                        handleStateRequest(player, payload);
                                    }
                                }
                        )
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                EssenceCrucibleVentPayload.TYPE,
                EssenceCrucibleVentPayload.CODEC,
                (payload, context) ->
                        context.queue(
                                () -> {
                                    if (context.getPlayer() instanceof ServerPlayer player) {
                                        handleVentRequest(player, payload);
                                    }
                                }
                        )
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                EssenceCrucibleDissolutionModePayload.TYPE,
                EssenceCrucibleDissolutionModePayload.CODEC,
                (payload, context) ->
                        context.queue(
                                () -> {
                                    if (context.getPlayer() instanceof ServerPlayer player) {
                                        handleDissolutionModeRequest(player, payload);
                                    }
                                }
                        )
        );

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, EssenceCrucibleSelectionPayload.TYPE,
                EssenceCrucibleSelectionPayload.CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) handleSelectionRequest(player, payload);
                }));

        TickEvent.PLAYER_POST.register(
                player -> {
                    if (player instanceof ServerPlayer serverPlayer
                            && serverPlayer.tickCount % SCREEN_SYNC_INTERVAL_TICKS == 0) {
                        syncOpenMenu(serverPlayer, false);
                    }
                }
        );

        initialized = true;
        EssenceAscendance.LOGGER.info(
                "Registered Essence Crucible channel/state/vent/dissolution-mode requests and S2C machine-state sync"
        );
    }

    public static void forceSync(ServerPlayer player) {
        syncOpenMenu(player, true);
    }

    public static void forget(ServerPlayer player) {
        LAST_SENT.remove(player);
    }


    private static void handleSelectionRequest(ServerPlayer player, EssenceCrucibleSelectionPayload payload) {
        var currentMenu = player.containerMenu;
        if (currentMenu.containerId != payload.menuId() || !ServerMenuAccess.isCurrent(player, currentMenu)
                || !com.mistaboom.essence_ascendance.crucible.CrucibleChannelSelection.validAction(payload.action())) return;
        if (!(currentMenu instanceof EssenceCrucibleMenu menu)) return;
        EssenceCrucibleBlockEntity crucible = menu.serverCrucible();
        if (crucible == null || !crucible.authorize(player, com.mistaboom.essence_ascendance.machine.MachineAction.SPEND)) return;
        var saved = EssenceSavedData.get(player.server);
        var data = saved.getPlayerData(player.getUUID());
        if (data.updateCrucibleChannelSelection(payload.expectedRevision(), payload.action())) saved.setDirty();
        forceSync(player);
    }

    private static void handleStateRequest(
            ServerPlayer player,
            EssenceCrucibleStateRequestPayload payload
    ) {
        if (player.containerMenu instanceof EssenceCrucibleMenu menu
                && menu.containerId == payload.menuId()
                && ServerMenuAccess.isCurrent(player, menu)) {
            /*
             * Force ignores LAST_SENT. This is important when JEI or another
             * temporary screen transition recreates the client screen while
             * the authoritative server menu remains open and unchanged.
             */
            forceSync(player);
        }
    }

    private static void handleVentRequest(
            ServerPlayer player,
            EssenceCrucibleVentPayload payload
    ) {
        if (!(player.containerMenu instanceof EssenceCrucibleMenu menu)
                || menu.containerId != payload.menuId()
                || !ServerMenuAccess.isCurrent(player, menu)) {
            return;
        }

        EssenceCrucibleBlockEntity crucible = menu.serverCrucible();
        if (crucible == null
                || crucible.getLevel() != player.serverLevel()
                || !player.serverLevel().hasChunkAt(crucible.getBlockPos())
                || player.serverLevel().getBlockEntity(crucible.getBlockPos()) != crucible
                || !crucible.canPlayerManage(player)) {
            return;
        }

        ResourceLocation essenceId = ResourceLocation.tryParse(payload.essenceId());
        if (essenceId == null) {
            return;
        }

        EssenceDefinition essence = EssenceRegistry.get(essenceId).orElse(null);
        if (essence == null) {
            return;
        }

        if (EssenceCrucibleEssences.indexOf(essence) < 0) {
            return;
        }

        if (!crucible.canPlayerUse(player) || crucible.ownerId() == null) {
            return;
        }

        java.util.UUID fundingActor = crucible.fundingActor();
        if (fundingActor == null) return;
        EssenceSavedData.get(player.server).clearReservoir(fundingActor, java.util.List.of(essence.id()));


        forceSync(player);
    }

    private static void handleDissolutionModeRequest(
            ServerPlayer player,
            EssenceCrucibleDissolutionModePayload payload
    ) {
        if (!(player.containerMenu instanceof EssenceCrucibleMenu menu)
                || menu.containerId != payload.menuId()
                || !ServerMenuAccess.isCurrent(player, menu)) {
            return;
        }

        EssenceCrucibleBlockEntity crucible = menu.serverCrucible();
        if (crucible == null
                || crucible.getLevel() != player.serverLevel()
                || !player.serverLevel().hasChunkAt(crucible.getBlockPos())
                || player.serverLevel().getBlockEntity(crucible.getBlockPos()) != crucible
                || !crucible.canPlayerConfigure(player)
                || menu.activeMachineSlots() <= 1) {
            return;
        }

        EssenceCrucibleDissolutionMode requested =
                EssenceCrucibleDissolutionMode.fromSerializedName(payload.mode());
        if (!requested.serializedName().equals(payload.mode())) {
            // The save-data parser has a default; malformed network actions do not.
            forceSync(player);
            return;
        }
        crucible.setDissolutionMode(requested);
        forceSync(player);
    }

    private static void handleChannelRequest(
            ServerPlayer player,
            EssenceCrucibleChannelPayload payload
    ) {
        if (!(player.containerMenu instanceof EssenceCrucibleMenu menu)
                || menu.containerId != payload.menuId()
                || !ServerMenuAccess.isCurrent(player, menu)) {
            return;
        }

        EssenceCrucibleBlockEntity crucible = menu.serverCrucible();
        if (crucible == null
                || crucible.getLevel() != player.serverLevel()
                || !player.serverLevel().hasChunkAt(crucible.getBlockPos())
                || player.serverLevel().getBlockEntity(crucible.getBlockPos()) != crucible
                || !crucible.canPlayerUse(player)
                || !crucible.inTransferRange(player)) {
            return;
        }

        crucible.setChannelingRequested(
                player,
                payload.active()
        );

        forceSync(player);
    }

    private static void syncOpenMenu(
            ServerPlayer player,
            boolean force
    ) {
        if (!(player.containerMenu instanceof EssenceCrucibleMenu menu)
                || !ServerMenuAccess.isCurrent(player, menu)) {
            LAST_SENT.remove(player);
            return;
        }

        EssenceCrucibleBlockEntity crucible = menu.serverCrucible();
        if (crucible == null
                || !NetworkManager.canPlayerReceive(
                        player,
                        EssenceCrucibleStatePayload.TYPE
                )) {
            return;
        }

        EssenceCrucibleStatePayload payload =
                buildPayload(
                        player,
                        menu,
                        crucible
                );

        if (!force && payload.equals(LAST_SENT.get(player))) {
            return;
        }

        NetworkManager.sendToPlayer(player, payload);
        LAST_SENT.put(player, payload);
    }

    private static EssenceCrucibleStatePayload buildPayload(
            ServerPlayer player,
            EssenceCrucibleMenu menu,
            EssenceCrucibleBlockEntity crucible
    ) {
        var balances = EssenceCrucibleEssences.ORDERED.stream().map(crucible::storedEssenceExact).toArray(java.math.BigInteger[]::new);
        EssenceCrucibleStructureStats stats = crucible.structureStats();

        var feedback = com.mistaboom.essence_ascendance.crucible.EssenceCrucibleChannelService.feedback(crucible, player.getUUID());
        int channelCount = feedback.registered();

        return new EssenceCrucibleStatePayload(
                EssenceCrucibleStatePayload.CURRENT_SCHEMA_VERSION,
                menu.containerId,
                crucible.getBlockPos().asLong(),
                crucible.ownerDisplayName(),
                crucible.accessMode().serializedName(),
                crucible.canPlayerUse(player),
                crucible.isChanneling(player.getUUID()),
                crucible.isDissolving(),
                channelCount,
                balances[0],
                balances[1],
                balances[2],
                balances[3],
                balances[4],
                balances[5],
                crucible.totalStoredEssence(),
                crucible.localFillLimit(),
                stats.transferRatePerSecond(),
                stats.transferRange(),
                stats.dissolutionTicksPerItem(),
                crucible.processingTicks(),
                crucible.dissolutionMode().serializedName(),
                stats.activePylonCount(),
                EssenceConfigManager.get().maxActivePylons(),
                EssenceConfigManager.get().pylonRadius(),
                stats.visualTransferStreams(),
                stats.simultaneousItemProcesses(),
                EssenceSavedData.get(player.server).getPlayerData(player.getUUID()).crucibleChannelSelection(),
                crucible.isChanneling(player.getUUID()), crucible.channelingPaused(player.getUUID()),
                crucible.authorize(player, com.mistaboom.essence_ascendance.machine.MachineAction.SPEND),
                EssenceSavedData.get(player.server).getPlayerData(player.getUUID()).crucibleChannelSelectionRevision(),
                feedback.consuming(), feedback.allocated(), feedback.actual(), feedback.reason().name(), crucible.canPlayerConfigure(player), crucible.canPlayerManage(player), crucible.accessState(player).name()
        );
    }
}

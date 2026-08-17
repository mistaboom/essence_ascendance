package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleEssences;
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
                "Registered Essence Crucible channel/state/vent requests and S2C machine-state sync"
        );
    }

    public static void forceSync(ServerPlayer player) {
        syncOpenMenu(player, true);
    }

    public static void forget(ServerPlayer player) {
        LAST_SENT.remove(player);
    }

    private static void handleStateRequest(
            ServerPlayer player,
            EssenceCrucibleStateRequestPayload payload
    ) {
        if (player.containerMenu instanceof EssenceCrucibleMenu menu
                && menu.containerId == payload.menuId()) {
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
                || menu.containerId != payload.menuId()) {
            return;
        }

        EssenceCrucibleBlockEntity crucible = menu.serverCrucible();
        if (crucible == null
                || crucible.getLevel() != player.serverLevel()
                || !player.serverLevel().hasChunkAt(crucible.getBlockPos())
                || player.serverLevel().getBlockEntity(crucible.getBlockPos()) != crucible
                || !crucible.canPlayerUse(player)) {
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

        boolean skillEssencesEnabled =
                EssenceConfigManager.get().skillEssencesEnabled();
        boolean enabled = EssenceCrucibleEssences
                .enabledOrdered(skillEssencesEnabled)
                .stream()
                .anyMatch(candidate -> candidate.id().equals(essence.id()));
        if (!enabled) {
            /* Disabled families remain preserved and cannot be vented invisibly. */
            return;
        }

        if (!crucible.bindOwner(player) || crucible.ownerId() == null) {
            return;
        }

        EssenceSavedData.get(player.server).removeCrucibleStored(
                crucible.ownerId(),
                essence,
                Long.MAX_VALUE
        );

        if (crucible.totalStoredEssence() <= 0L) {
            crucible.stopChanneling();
        }

        forceSync(player);
    }

    private static void handleChannelRequest(
            ServerPlayer player,
            EssenceCrucibleChannelPayload payload
    ) {
        if (!(player.containerMenu instanceof EssenceCrucibleMenu menu)
                || menu.containerId != payload.menuId()) {
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
        if (!(player.containerMenu instanceof EssenceCrucibleMenu menu)) {
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
        long[] balances = crucible.storedEssenceSnapshot();
        EssenceCrucibleStructureStats stats = crucible.structureStats();

        long[] skillBalances =
                crucible.storedSkillEssenceSnapshot();

        boolean skillEssencesEnabled =
                EssenceConfigManager.get().skillEssencesEnabled();

        String channelingPlayer = "";
        if (crucible.channelingPlayerId() != null) {
            ServerPlayer active = player.server.getPlayerList()
                    .getPlayer(crucible.channelingPlayerId());
            channelingPlayer = active == null
                    ? crucible.channelingPlayerId().toString()
                    : active.getGameProfile().getName();
        }

        return new EssenceCrucibleStatePayload(
                EssenceCrucibleStatePayload.CURRENT_SCHEMA_VERSION,
                menu.containerId,
                crucible.getBlockPos().asLong(),
                crucible.ownerDisplayName(),
                crucible.accessMode().serializedName(),
                crucible.canPlayerUse(player),
                crucible.isChanneling(),
                channelingPlayer,
                skillEssencesEnabled,
                balances[0],
                balances[1],
                balances[2],
                balances[3],
                balances[4],
                balances[5],
                skillBalances[0],
                skillBalances[1],
                skillBalances[2],
                skillBalances[3],
                skillBalances[4],
                skillBalances[5],
                skillBalances[6],
                skillBalances[7],
                skillBalances[8],
                crucible.totalStoredEssence(),
                crucible.effectiveReservoirCapacity(),
                stats.transferRatePerSecond(),
                stats.transferRange(),
                stats.dissolutionTicksPerItem(),
                crucible.processingTicks(),
                stats.activePylonCount(),
                EssenceConfigManager.get().maxActivePylons(),
                EssenceConfigManager.get().pylonRadius(),
                stats.visualTransferStreams(),
                stats.simultaneousItemProcesses()
        );
    }
}

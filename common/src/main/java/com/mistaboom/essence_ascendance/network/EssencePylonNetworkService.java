package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureSnapshot;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureStats;
import com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContribution;
import com.mistaboom.essence_ascendance.pylon.EssencePylonMenu;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

public final class EssencePylonNetworkService {

    private static final int SCREEN_SYNC_INTERVAL_TICKS = 2;
    private static final Map<ServerPlayer, EssencePylonStatePayload> LAST_SENT =
            new WeakHashMap<>();
    private static boolean initialized = false;

    private EssencePylonNetworkService() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(
                    EssencePylonStatePayload.TYPE,
                    EssencePylonStatePayload.CODEC
            );
        }

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                EssencePylonStateRequestPayload.TYPE,
                EssencePylonStateRequestPayload.CODEC,
                (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player
                            && player.containerMenu instanceof EssencePylonMenu menu
                            && menu.containerId == payload.menuId()) {
                        forceSync(player);
                    }
                })
        );

        TickEvent.PLAYER_POST.register(player -> {
            if (player instanceof ServerPlayer serverPlayer
                    && serverPlayer.tickCount % SCREEN_SYNC_INTERVAL_TICKS == 0) {
                syncOpenMenu(serverPlayer, false);
            }
        });

        initialized = true;
        EssenceAscendance.LOGGER.info(
                "Registered Essence Pylon state request and S2C menu synchronization"
        );
    }

    public static void forceSync(ServerPlayer player) {
        syncOpenMenu(player, true);
    }

    public static void forget(ServerPlayer player) {
        LAST_SENT.remove(player);
    }

    private static void syncOpenMenu(ServerPlayer player, boolean force) {
        if (!(player.containerMenu instanceof EssencePylonMenu menu)) {
            LAST_SENT.remove(player);
            return;
        }

        EssencePylonBlockEntity pylon = menu.serverPylon();
        if (pylon == null
                || !NetworkManager.canPlayerReceive(player, EssencePylonStatePayload.TYPE)) {
            return;
        }

        EssencePylonStatePayload payload = buildPayload(player, menu, pylon);
        if (!force && payload.equals(LAST_SENT.get(player))) {
            return;
        }

        NetworkManager.sendToPlayer(player, payload);
        LAST_SENT.put(player, payload);
    }

    private static EssencePylonStatePayload buildPayload(
            ServerPlayer player,
            EssencePylonMenu menu,
            EssencePylonBlockEntity pylon
    ) {
        pylon.refreshLink();

        EssencePylonContribution contribution = pylon.contribution();
        BlockPos linkedPos = pylon.linkedCruciblePos();
        boolean linked = false;
        boolean active = false;
        int linkedActivePylons = 0;
        long linkedReservoirCapacity = 0L;
        double linkedTransferRange = 0.0D;
        long linkedTransferRate = 0L;
        int linkedDissolutionTicks = 0;
        int linkedSimultaneousProcesses = 0;

        if (linkedPos != null
                && player.serverLevel().hasChunkAt(linkedPos)
                && player.serverLevel().getBlockEntity(linkedPos)
                        instanceof EssenceCrucibleBlockEntity crucible
                && crucible.ownerId() != null
                && crucible.ownerId().equals(pylon.ownerId())) {
            linked = true;
            EssenceCrucibleStructureSnapshot snapshot = crucible.structureSnapshot();
            EssenceCrucibleStructureStats stats = snapshot.stats();
            active = snapshot.containsPylon(pylon.getBlockPos());
            linkedActivePylons = stats.activePylonCount();
            linkedReservoirCapacity = crucible.effectiveReservoirCapacity();
            linkedTransferRange = stats.transferRange();
            linkedTransferRate = stats.transferRatePerSecond();
            linkedDissolutionTicks = stats.dissolutionTicksPerItem();
            linkedSimultaneousProcesses = stats.simultaneousItemProcesses();
        }

        return new EssencePylonStatePayload(
                EssencePylonStatePayload.CURRENT_SCHEMA_VERSION,
                menu.containerId,
                pylon.getBlockPos().asLong(),
                pylon.ownerDisplayName(),
                pylon.canPlayerUse(player),
                linked,
                linkedPos == null ? 0L : linkedPos.asLong(),
                active,
                pylon.focusTier() != null,
                pylon.focusDisplayName(),
                EssenceConfigManager.get().pylonRadius(),
                EssenceConfigManager.get().maxActivePylons(),
                contribution.reservoirCapacityBonus(),
                contribution.transferRangeBonus(),
                contribution.transferRatePerSecondBonus(),
                contribution.dissolutionSpeedBonus(),
                contribution.simultaneousItemProcessesBonus(),
                linkedActivePylons,
                linkedReservoirCapacity,
                linkedTransferRange,
                linkedTransferRate,
                linkedDissolutionTicks,
                linkedSimultaneousProcesses
        );
    }
}

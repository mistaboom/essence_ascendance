package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.nexus.AscendanceAllocationFeedback;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusMenu;
import com.mistaboom.essence_ascendance.nexus.AscendanceTierFeedback;
import com.mistaboom.essence_ascendance.progression.AscendanceAllocationService;
import com.mistaboom.essence_ascendance.progression.AscendanceAttemptResult;
import com.mistaboom.essence_ascendance.progression.AscendanceEngine;
import com.mistaboom.essence_ascendance.progression.AscendanceEvaluationResult;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** C2S Nexus allocation request handling and validation boundary. */
public final class AscendanceNexusNetworkService {

    private static final long REQUEST_COOLDOWN_TICKS = 4L;

    private static final Map<ServerPlayer, Long> LAST_REQUEST_TICK =
            new WeakHashMap<>();

    private static boolean initialized = false;

    private AscendanceNexusNetworkService() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                AscendanceAllocationPayload.TYPE,
                AscendanceAllocationPayload.CODEC,
                (payload, context) ->
                        context.queue(
                                () -> {
                                    if (context.getPlayer() instanceof ServerPlayer player) {
                                        handleAllocationRequest(
                                                player,
                                                payload
                                        );
                                    }
                                }
                        )
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                AscendanceAscendPayload.TYPE,
                AscendanceAscendPayload.CODEC,
                (payload, context) ->
                        context.queue(
                                () -> {
                                    if (context.getPlayer() instanceof ServerPlayer player) {
                                        handleAscendRequest(
                                                player,
                                                payload
                                        );
                                    }
                                }
                        )
        );

        initialized = true;

        EssenceAscendance.LOGGER.info(
                "Registered Ascendance Nexus allocation and Ascension requests"
        );
    }

    private static void handleAllocationRequest(
            ServerPlayer player,
            AscendanceAllocationPayload payload
    ) {
        if (!(player.containerMenu instanceof AscendanceNexusMenu menu)
                || menu.containerId != payload.menuId()
                || !menu.stillValid(player)) {
            reject(
                    player,
                    "Allocation rejected: the Ascendance Nexus is no longer valid.",
                    false
            );
            return;
        }

        if (!beginRequest(player)) {
            return;
        }

        if (payload.targets().isEmpty()
                || payload.targets().size() > AscendanceAllocationPayload.MAX_TARGETS) {
            reject(
                    player,
                    "Allocation rejected: invalid staged allocation.",
                    true
            );
            return;
        }

        ResourceLocation baseTierId =
                ResourceLocation.tryParse(payload.baseTierId());
        ResourceLocation baseBalanceProfileId =
                ResourceLocation.tryParse(payload.baseBalanceProfileId());

        if (baseTierId == null || baseBalanceProfileId == null) {
            reject(
                    player,
                    "Allocation rejected: invalid staged progression context.",
                    true
            );
            return;
        }

        Map<StatDefinition, AscendanceAllocationService.RequestedTarget> requestedTargets =
                new LinkedHashMap<>();

        for (AscendanceAllocationPayload.Target target : payload.targets()) {
            ResourceLocation statId =
                    ResourceLocation.tryParse(target.statId());

            if (statId == null) {
                reject(
                        player,
                        "Allocation rejected: invalid stat identifier.",
                        true
                );
                return;
            }

            StatDefinition stat =
                    EssenceStatRegistry.get(statId).orElse(null);

            if (stat == null) {
                reject(
                        player,
                        "Allocation rejected: unknown stat.",
                        true
                );
                return;
            }

            if (requestedTargets.put(
                    stat,
                    new AscendanceAllocationService.RequestedTarget(
                            target.baseInvestment(),
                            target.targetInvestment()
                    )
            ) != null) {
                reject(
                        player,
                        "Allocation rejected: duplicate stat target.",
                        true
                );
                return;
            }
        }

        AscendanceAllocationService.Result result =
                AscendanceAllocationService.apply(
                        player,
                        payload.basePlayerRevision(),
                        baseTierId,
                        baseBalanceProfileId,
                        requestedTargets
                );

        switch (result.status()) {
            case SUCCESS -> {
                /*
                 * Re-evaluate runtime effects and send the authoritative player
                 * snapshot immediately instead of waiting for periodic ticks.
                 */
                PlayerRuntimeLifecycleService.refreshProgressionState(player);

                /*
                 * Successful ALLOCATE is an explicit commit point. Close the
                 * Nexus, then play the intentionally minimal placeholder pulse.
                 */
                player.closeContainer();
                AscendanceAllocationFeedback.play(player);
            }
            case NO_CHANGES -> reject(
                    player,
                    result.message(),
                    true
            );
            case STALE, INVALID -> reject(
                    player,
                    result.message(),
                    true
            );
        }
    }

    private static void handleAscendRequest(
            ServerPlayer player,
            AscendanceAscendPayload payload
    ) {
        if (!(player.containerMenu instanceof AscendanceNexusMenu menu)
                || menu.containerId != payload.menuId()
                || !menu.stillValid(player)) {
            reject(
                    player,
                    "Ascension rejected: the Ascendance Nexus is no longer valid.",
                    false
            );
            return;
        }

        if (!beginRequest(player)) {
            return;
        }

        ResourceLocation baseTierId =
                ResourceLocation.tryParse(payload.baseTierId());

        if (baseTierId == null) {
            reject(
                    player,
                    "Ascension rejected: invalid tier context.",
                    true
            );
            return;
        }

        AscendanceEvaluationResult evaluation =
                AscendanceEngine.evaluate(player);

        if (!evaluation.currentTier().id().equals(baseTierId)) {
            reject(
                    player,
                    "Ascension rejected: your progression changed. Review the refreshed requirements.",
                    true
            );
            return;
        }

        AscendanceAttemptResult result =
                AscendanceEngine.ascend(player);

        switch (result.status()) {
            case SUCCESS -> {
                PlayerRuntimeLifecycleService.refreshProgressionState(player);
                player.closeContainer();
                AscendanceTierFeedback.play(player);
            }
            case NOT_READY -> reject(
                    player,
                    "Ascension requirements are not yet complete.",
                    true
            );
            case MAX_TIER -> reject(
                    player,
                    "You are already at maximum Ascendance.",
                    true
            );
            case CONFIGURATION_ERROR -> reject(
                    player,
                    "Ascension is unavailable because the progression configuration is invalid.",
                    true
            );
        }
    }

    private static boolean beginRequest(
            ServerPlayer player
    ) {
        long now = player.serverLevel().getGameTime();
        Long previous = LAST_REQUEST_TICK.get(player);

        if (previous != null
                && now - previous < REQUEST_COOLDOWN_TICKS) {
            return false;
        }

        LAST_REQUEST_TICK.put(player, now);
        return true;
    }

    private static void reject(
            ServerPlayer player,
            String message,
            boolean refreshState
    ) {
        player.displayClientMessage(
                Component.literal(message),
                true
        );

        if (refreshState) {
            PlayerEssenceSyncService.forceSync(player);
        }
    }
}

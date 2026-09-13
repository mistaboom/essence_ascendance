package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import com.mistaboom.essence_ascendance.nexus.AscendanceAllocationFeedback;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusMenu;
import com.mistaboom.essence_ascendance.nexus.AscendanceTierFeedback;
import com.mistaboom.essence_ascendance.progression.AscendanceNexusTransactionService;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.text.EssenceText;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/** C2S Nexus request handling, menu validation, and rate-limit boundary. */
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

        /*
         * Physical clients register the matching S2C receiver from client-only
         * initialization. A dedicated server still has to advertise the type.
         */
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(
                    AscendanceNexusTransactionResultPayload.TYPE,
                    AscendanceNexusTransactionResultPayload.CODEC
            );
        }

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                AscendanceNexusTransactionPayload.TYPE,
                AscendanceNexusTransactionPayload.CODEC,
                (payload, context) ->
                        context.queue(
                                () -> {
                                    if (context.getPlayer() instanceof ServerPlayer player) {
                                        handleTransactionRequest(player, payload);
                                    }
                                }
                        )
        );

        initialized = true;

        EssenceAscendance.LOGGER.info(
                "Registered atomic Ascendance Nexus transaction requests"
        );
    }

    private static void handleTransactionRequest(
            ServerPlayer player,
            AscendanceNexusTransactionPayload payload
    ) {
        if (!validNexusMenu(player, payload.menuId())) {
            finishTransaction(
                    player,
                    payload.menuId(),
                    payload.requestId(),
                    AscendanceNexusTransactionService.Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.INVALID_MENU,
                            currentRevision(player)
                    )
            );
            return;
        }

        if (!beginRequest(player)) {
            finishTransaction(
                    player,
                    payload.menuId(),
                    payload.requestId(),
                    AscendanceNexusTransactionService.Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.RATE_LIMITED,
                            currentRevision(player)
                    )
            );
            return;
        }

        ResourceLocation baseTierId =
                ResourceLocation.tryParse(payload.baseTierId());
        ResourceLocation baseBalanceProfileId =
                ResourceLocation.tryParse(payload.baseBalanceProfileId());

        if (baseTierId == null || baseBalanceProfileId == null) {
            finishTransaction(
                    player,
                    payload.menuId(),
                    payload.requestId(),
                    AscendanceNexusTransactionService.Result.failure(
                            AscendanceNexusTransactionResultPayload.Status.INVALID_CONTEXT,
                            currentRevision(player)
                    )
            );
            return;
        }

        Map<ResourceLocation, Long> bonusTargets = new LinkedHashMap<>();
        for (AscendanceNexusTransactionPayload.BonusTarget target :
                payload.bonusTargets()) {
            ResourceLocation statId = ResourceLocation.tryParse(target.statId());
            if (statId == null) {
                finishInvalidProposal(
                        player,
                        payload,
                        AscendanceNexusTransactionResultPayload.Status.UNKNOWN_STAT
                );
                return;
            }

            if (bonusTargets.put(statId, target.targetInvestment()) != null) {
                finishInvalidProposal(
                        player,
                        payload,
                        AscendanceNexusTransactionResultPayload.Status.INVALID_PROPOSAL
                );
                return;
            }
        }

        Map<ResourceLocation, Integer> purchases = new LinkedHashMap<>();
        for (var rankTarget : payload.skillPurchases()) {
            ResourceLocation skillId = ResourceLocation.tryParse(rankTarget.skillId());
            if (skillId == null || purchases.putIfAbsent(skillId, rankTarget.targetRank()) != null) {
                finishInvalidProposal(
                        player,
                        payload,
                        skillId == null
                                ? AscendanceNexusTransactionResultPayload.Status.UNKNOWN_SKILL
                                : AscendanceNexusTransactionResultPayload.Status.INVALID_PROPOSAL
                );
                return;
            }
        }

        Map<ResourceLocation, Optional<ResourceLocation>> loadoutChanges =
                new LinkedHashMap<>();
        for (AscendanceNexusTransactionPayload.LoadoutSelection encoded :
                payload.loadoutSelections()) {
            ResourceLocation groupId = ResourceLocation.tryParse(encoded.groupId());
            if (groupId == null) {
                finishInvalidProposal(
                        player,
                        payload,
                        AscendanceNexusTransactionResultPayload.Status.INVALID_LOADOUT
                );
                return;
            }

            Optional<ResourceLocation> selected;
            if (encoded.selectedSkillId().isBlank()) {
                selected = Optional.empty();
            } else {
                ResourceLocation skillId =
                        ResourceLocation.tryParse(encoded.selectedSkillId());
                if (skillId == null) {
                    finishInvalidProposal(
                            player,
                            payload,
                            AscendanceNexusTransactionResultPayload.Status.INVALID_LOADOUT
                    );
                    return;
                }
                selected = Optional.of(skillId);
            }

            if (loadoutChanges.put(groupId, selected) != null) {
                finishInvalidProposal(
                        player,
                        payload,
                        AscendanceNexusTransactionResultPayload.Status.INVALID_PROPOSAL
                );
                return;
            }
        }

        AscendanceNexusTransactionService.Result result =
                AscendanceNexusTransactionService.apply(
                        player,
                        payload.baseNexusRevision(),
                        baseTierId,
                        baseBalanceProfileId,
                        bonusTargets,
                        purchases,
                        loadoutChanges,
                        payload.ascend()
                );

        finishTransaction(
                player,
                payload.menuId(),
                payload.requestId(),
                result
        );
    }

    private static void finishInvalidProposal(
            ServerPlayer player,
            AscendanceNexusTransactionPayload payload,
            AscendanceNexusTransactionResultPayload.Status status
    ) {
        finishTransaction(
                player,
                payload.menuId(),
                payload.requestId(),
                AscendanceNexusTransactionService.Result.failure(
                        status,
                        currentRevision(player)
                )
        );
    }

    private static void finishTransaction(
            ServerPlayer player,
            int menuId,
            long requestId,
            AscendanceNexusTransactionService.Result result
    ) {
        if (result.accepted()) {
            PlayerRuntimeLifecycleService.refreshProgressionState(player);
            if (result.ascended()) {
                AscendanceTierFeedback.play(player);
            } else {
                AscendanceAllocationFeedback.play(player);
            }
        } else {
            /* Fresh authoritative state precedes the correlated failure ack. */
            PlayerEssenceSyncService.forceSync(player);
            player.displayClientMessage(
                    resultMessage(result.status()),
                    true
            );
        }

        if (NetworkManager.canPlayerReceive(
                player,
                AscendanceNexusTransactionResultPayload.TYPE
        )) {
            NetworkManager.sendToPlayer(
                    player,
                    new AscendanceNexusTransactionResultPayload(
                            menuId,
                            requestId,
                            result.nexusRevision(),
                            result.status(),
                            result.ascended()
                    )
            );
        }
    }

    private static boolean validNexusMenu(
            ServerPlayer player,
            int menuId
    ) {
        return player.containerMenu instanceof AscendanceNexusMenu menu
                && menu.containerId == menuId
                && ServerMenuAccess.isCurrent(player, menu);
    }

    private static PlayerEssenceData playerData(ServerPlayer player) {
        return EssenceSavedData
                .get(player.server)
                .getPlayerData(player.getUUID());
    }

    private static long currentRevision(ServerPlayer player) {
        return playerData(player).nexusRevision();
    }

    private static boolean beginRequest(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long previous = LAST_REQUEST_TICK.get(player);

        if (previous != null && now - previous < REQUEST_COOLDOWN_TICKS) {
            return false;
        }

        LAST_REQUEST_TICK.put(player, now);
        return true;
    }

    private static Component resultMessage(
            AscendanceNexusTransactionResultPayload.Status status
    ) {
        String fallback = switch (status) {
            case SUCCESS -> "Nexus changes applied.";
            case INVALID_MENU -> "The Ascendance Nexus is no longer valid.";
            case RATE_LIMITED -> "Please wait before submitting another Nexus request.";
            case STALE -> "Your progression changed. Review the refreshed Nexus state and try again.";
            case INVALID_CONTEXT -> "The staged Nexus context is invalid.";
            case INVALID_PROPOSAL -> "The staged Nexus proposal is invalid or contains no changes.";
            case INCOMPLETE_BONUS_STATE -> "The staged proposal is missing Bonus investments.";
            case UNKNOWN_STAT -> "The staged proposal contains an unknown Bonus stat.";
            case CAP_EXCEEDED -> "A staged Bonus investment exceeds its current tier cap.";
            case INSUFFICIENT_ESSENCE -> "There is not enough unallocated Essence for these changes.";
            case UNKNOWN_SKILL -> "The staged proposal contains an unknown skill.";
            case SKILL_MAX_RANK -> "A staged skill exceeds its maximum rank.";
            case SKILL_TIER_REQUIRED -> "Your current Ascendance tier cannot purchase a staged skill.";
            case SKILL_PREREQUISITE_REQUIRED -> "A staged skill prerequisite is not owned or staged.";
            case SKILL_REQUIREMENT_INCOMPLETE -> "A staged skill requirement is incomplete in the final proposal.";
            case INVALID_LOADOUT -> "A staged skill loadout selection is invalid.";
            case ASCENSION_NOT_READY -> "Ascension requirements are incomplete in the final proposal.";
            case MAX_TIER -> "You are already at maximum Ascendance.";
            case CONFIGURATION_ERROR -> "The Nexus cannot apply changes because its configuration is invalid.";
            case TRANSACTION_FAILED -> "The Nexus transaction could not be committed.";
        };

        return Component.translatableWithFallback(
                status.translationKey(),
                fallback
        );
    }

    private static void reject(
            ServerPlayer player,
            Component message,
            boolean refreshState
    ) {
        player.displayClientMessage(message, true);
        if (refreshState) {
            PlayerEssenceSyncService.forceSync(player);
        }
    }
}

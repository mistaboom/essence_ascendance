package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.EssenceServerConfig;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancementDefinition;
import com.mistaboom.essence_ascendance.progression.AscendanceEngine;
import com.mistaboom.essence_ascendance.progression.AscendanceEvaluationResult;
import com.mistaboom.essence_ascendance.progression.AscendanceProgressSnapshot;
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.progression.MilestoneProgress;
import com.mistaboom.essence_ascendance.progression.MilestoneRequirement;
import com.mistaboom.essence_ascendance.progression.StatScalingResult;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/*
 * General server -> client player progression synchronization.
 *
 * The client never reads EssenceSavedData and never recalculates authoritative
 * progression rules. The server sends a complete immutable presentation
 * snapshot whenever player progression or the active server config changes.
 */
public final class PlayerEssenceSyncService {

    /*
     * The normal tick path is only a cheap revision/config comparison.
     * Expensive stat/progression evaluation happens only when state changed.
     */
    private static final int CHECK_INTERVAL_TICKS = 5;

    private static final Map<ServerPlayer, LastSentState> LAST_SENT =
            new WeakHashMap<>();

    private static boolean initialized = false;

    private PlayerEssenceSyncService() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        /*
         * Architectury 1.21:
         * - dedicated server registers the S2C payload type here;
         * - physical clients register the S2C receiver in ClientEssenceState.
         *
         * On an integrated server both sides live in the same process.
         */
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(
                    PlayerEssenceSyncPayload.TYPE,
                    PlayerEssenceSyncPayload.CODEC
            );
        }

        /*
         * Join/respawn/dimension/quit lifecycle ownership lives in
         * PlayerRuntimeLifecycleService so gameplay cleanup and presentation
         * synchronization happen as one ordered operation.
         *
         * Advancement-backed Ascendance requirements can change without
         * mutating PlayerEssenceData, so advancement completion explicitly
         * invalidates/sends the progression snapshot.
         */
        PlayerEvent.PLAYER_ADVANCEMENT.register(
                (player, advancement) ->
                        forceSync(player)
        );

        TickEvent.PLAYER_POST.register(
                player -> {
                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return;
                    }

                    if (serverPlayer.tickCount
                            % CHECK_INTERVAL_TICKS
                            == 0) {
                        syncIfChanged(
                                serverPlayer
                        );
                    }
                }
        );

        initialized = true;
    }

    public static void forceSync(
            ServerPlayer player
    ) {
        send(
                player,
                true
        );
    }

    public static void syncIfChanged(
            ServerPlayer player
    ) {
        send(
                player,
                false
        );
    }

    public static void forget(
            ServerPlayer player
    ) {
        LAST_SENT.remove(
                player
        );
    }

    private static void send(
            ServerPlayer player,
            boolean force
    ) {
        if (!NetworkManager.canPlayerReceive(
                player,
                PlayerEssenceSyncPayload.TYPE
        )) {
            /*
             * Do not cache a failed attempt. The periodic check will send as
             * soon as network negotiation says the client can receive it.
             */
            return;
        }

        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(
                                player.getUUID()
                        );

        EssenceServerConfig config =
                EssenceConfigManager.get();

        LastSentState previous =
                LAST_SENT.get(
                        player
                );

        if (!force
                && previous != null
                && previous.playerRevision()
                == playerData.revision()
                && previous.configReference()
                == config) {
            return;
        }

        PlayerEssenceSyncPayload payload =
                buildPayload(
                        player,
                        playerData,
                        config
                );

        /*
         * A force-sync is intentional for login/respawn/dimension/advancement.
         * Otherwise, payload equality is a final guard against redundant sends.
         */
        if (!force
                && previous != null
                && payload.equals(
                        previous.payload()
                )) {

            LAST_SENT.put(
                    player,
                    new LastSentState(
                            playerData.revision(),
                            config,
                            payload
                    )
            );

            return;
        }

        NetworkManager.sendToPlayer(
                player,
                payload
        );

        LAST_SENT.put(
                player,
                new LastSentState(
                        playerData.revision(),
                        config,
                        payload
                )
        );
    }

    private static PlayerEssenceSyncPayload buildPayload(
            ServerPlayer player,
            PlayerEssenceData playerData,
            EssenceServerConfig config
    ) {
        List<PlayerEssenceSyncPayload.EssenceBalance> balances =
                new ArrayList<>();

        for (EssenceDefinition essence :
                EssenceRegistry.values()) {

            balances.add(
                    new PlayerEssenceSyncPayload.EssenceBalance(
                            essence.id().toString(),
                            playerData.getAvailable(
                                    essence
                            )
                    )
            );
        }

        List<PlayerEssenceSyncPayload.StatState> stats =
                new ArrayList<>();

        for (StatDefinition stat :
                EssenceStatRegistry.values()) {

            StatScalingResult scaling =
                    StatScalingService.evaluate(
                            playerData,
                            stat
                    );

            stats.add(
                    new PlayerEssenceSyncPayload.StatState(
                            stat.id().toString(),
                            scaling.storedInvestment(),
                            scaling.effectiveInvestment(),
                            scaling.currentInvestmentCap(),
                            scaling.progression(),
                            scaling.currentTierMaximumBonus(),
                            scaling.transcendentMaximumBonus(),
                            scaling.scaledBonus()
                    )
            );
        }

        List<String> completedMilestones =
                playerData
                        .getCompletedMilestones()
                        .stream()
                        .map(ResourceLocation::toString)
                        .sorted()
                        .toList();

        return new PlayerEssenceSyncPayload(
                PlayerEssenceSyncPayload.CURRENT_SCHEMA_VERSION,
                playerData.revision(),
                playerData.getTierId().toString(),
                config.balanceProfile()
                        .id()
                        .toString(),
                balances,
                stats,
                completedMilestones,
                buildProgress(
                        player
                )
        );
    }

    private static PlayerEssenceSyncPayload.ProgressState buildProgress(
            ServerPlayer player
    ) {
        AscendanceEvaluationResult evaluation =
                AscendanceEngine.evaluate(
                        player
                );

        return switch (evaluation.status()) {
            case AVAILABLE -> {
                AscendanceProgressSnapshot progress =
                        evaluation.progress();

                AscendanceAdvancementDefinition advancement =
                        EssenceConfigManager
                                .get()
                                .getAdvancementForTier(
                                        evaluation.currentTier().id()
                                )
                                .orElseThrow(
                                        () -> new IllegalStateException(
                                                "Missing synchronized Ascendance advancement for tier "
                                                        + evaluation.currentTier().id()
                                        )
                                );

                yield new PlayerEssenceSyncPayload.ProgressState(
                        PlayerEssenceSyncPayload.ProgressStatus.AVAILABLE,
                        evaluation.nextTier()
                                .id()
                                .toString(),
                        progress.effectiveInvestment(),
                        progress.requiredInvestment(),
                        progress.developedStats(),
                        progress.requiredDevelopedStats(),
                        progress.representedCategories(),
                        progress.requiredRepresentedCategories(),
                        advancement.developedStatThreshold(),
                        flattenWorldRequirements(
                                progress.worldProgress()
                        ),
                        progress.worldProgressComplete(),
                        progress.readyToAscend()
                );
            }

            case MAX_TIER ->
                    new PlayerEssenceSyncPayload.ProgressState(
                            PlayerEssenceSyncPayload.ProgressStatus.MAX_TIER,
                            "",
                            0L,
                            0L,
                            0,
                            0,
                            0,
                            0,
                            0.0D,
                            List.of(),
                            true,
                            false
                    );

            case CONFIGURATION_ERROR ->
                    new PlayerEssenceSyncPayload.ProgressState(
                            PlayerEssenceSyncPayload.ProgressStatus.CONFIGURATION_ERROR,
                            "",
                            0L,
                            0L,
                            0,
                            0,
                            0,
                            0,
                            0.0D,
                            List.of(),
                            false,
                            false
                    );
        };
    }

    private static List<PlayerEssenceSyncPayload.WorldRequirementState> flattenWorldRequirements(
            MilestoneProgress root
    ) {
        List<PlayerEssenceSyncPayload.WorldRequirementState> lines =
                new ArrayList<>();

        appendWorldRequirement(
                root,
                0,
                lines
        );

        return List.copyOf(lines);
    }

    private static void appendWorldRequirement(
            MilestoneProgress progress,
            int depth,
            List<PlayerEssenceSyncPayload.WorldRequirementState> lines
    ) {
        if (lines.size()
                >= PlayerEssenceSyncPayload.MAX_WORLD_REQUIREMENT_LINES) {
            return;
        }

        MilestoneRequirement requirement =
                progress.requirement();

        PlayerEssenceSyncPayload.WorldRequirementKind kind;
        String label;

        if (requirement instanceof MilestoneRequirement.Milestone milestone) {
            kind = PlayerEssenceSyncPayload.WorldRequirementKind.MILESTONE;
            label =
                    EssenceConfigManager
                            .get()
                            .getMilestone(milestone.milestoneId())
                            .map(MilestoneDefinition::displayName)
                            .orElse(milestone.milestoneId().toString());
        } else if (requirement instanceof MilestoneRequirement.AllOf) {
            kind = PlayerEssenceSyncPayload.WorldRequirementKind.ALL_OF;
            label = "Complete all of:";
        } else if (requirement instanceof MilestoneRequirement.AnyOf) {
            kind = PlayerEssenceSyncPayload.WorldRequirementKind.ANY_OF;
            label = "Complete any one of:";
        } else if (requirement instanceof MilestoneRequirement.Always) {
            kind = PlayerEssenceSyncPayload.WorldRequirementKind.ALWAYS;
            label = "No world milestone required";
        } else {
            throw new IllegalStateException(
                    "Unsupported milestone requirement type: "
                            + requirement.getClass().getName()
            );
        }

        lines.add(
                new PlayerEssenceSyncPayload.WorldRequirementState(
                        depth,
                        kind,
                        label,
                        progress.resolvable(),
                        progress.complete()
                )
        );

        for (MilestoneProgress child : progress.children()) {
            appendWorldRequirement(
                    child,
                    depth + 1,
                    lines
            );
        }
    }

    private record LastSentState(
            long playerRevision,
            EssenceServerConfig configReference,
            PlayerEssenceSyncPayload payload
    ) {
    }
}

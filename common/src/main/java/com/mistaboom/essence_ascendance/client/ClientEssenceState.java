package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncPayload;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/*
 * Read-only client cache of the local player's authoritative server state.
 *
 * Screens/tooltips must use this cache instead of attempting to access
 * EssenceSavedData on the client.
 */
public final class ClientEssenceState {

    private static volatile Snapshot snapshot =
            Snapshot.empty();

    private static boolean initialized = false;

    private ClientEssenceState() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PlayerEssenceSyncPayload.TYPE,
                PlayerEssenceSyncPayload.CODEC,
                (payload, context) ->
                        context.queue(
                                () -> accept(
                                        payload
                                )
                        )
        );

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(
                player ->
                        clear()
        );

        initialized = true;

        EssenceAscendance.LOGGER.info(
                "Registered Essence Ascendance player-state S2C receiver"
        );
    }

    public static boolean ready() {
        return snapshot.ready();
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static long available(
            EssenceDefinition essence
    ) {
        return available(
                essence.id()
        );
    }

    public static long available(
            ResourceLocation essenceId
    ) {
        return snapshot
                .availableEssence()
                .getOrDefault(
                        essenceId,
                        0L
                );
    }

    public static Optional<StatSnapshot> stat(
            StatDefinition stat
    ) {
        return stat(
                stat.id()
        );
    }

    public static Optional<StatSnapshot> stat(
            ResourceLocation statId
    ) {
        return Optional.ofNullable(
                snapshot
                        .stats()
                        .get(
                                statId
                        )
        );
    }

    public static boolean hasCompletedInternalMilestone(
            ResourceLocation milestoneId
    ) {
        return snapshot
                .completedMilestones()
                .contains(
                        milestoneId
                );
    }

    public static void clear() {
        snapshot =
                Snapshot.empty();
    }

    private static void accept(
            PlayerEssenceSyncPayload payload
    ) {
        if (payload.schemaVersion()
                != PlayerEssenceSyncPayload.CURRENT_SCHEMA_VERSION) {

            EssenceAscendance.LOGGER.error(
                    "Ignoring incompatible Essence Ascendance player sync payload schema {} (client expects {})",
                    payload.schemaVersion(),
                    PlayerEssenceSyncPayload.CURRENT_SCHEMA_VERSION
            );

            return;
        }

        ResourceLocation tierId =
                ResourceLocation.tryParse(
                        payload.tierId()
                );

        ResourceLocation profileId =
                ResourceLocation.tryParse(
                        payload.balanceProfileId()
                );

        if (tierId == null
                || profileId == null) {

            EssenceAscendance.LOGGER.error(
                    "Ignoring Essence Ascendance player sync payload with invalid tier/profile IDs: tier='{}', profile='{}'",
                    payload.tierId(),
                    payload.balanceProfileId()
            );

            return;
        }

        Map<ResourceLocation, Long> balances =
                new LinkedHashMap<>();

        for (PlayerEssenceSyncPayload.EssenceBalance balance :
                payload.essenceBalances()) {

            ResourceLocation essenceId =
                    ResourceLocation.tryParse(
                            balance.essenceId()
                    );

            if (essenceId != null) {
                balances.put(
                        essenceId,
                        balance.available()
                );
            }
        }

        Map<ResourceLocation, StatSnapshot> stats =
                new LinkedHashMap<>();

        for (PlayerEssenceSyncPayload.StatState state :
                payload.stats()) {

            ResourceLocation statId =
                    ResourceLocation.tryParse(
                            state.statId()
                    );

            if (statId == null) {
                continue;
            }

            stats.put(
                    statId,
                    new StatSnapshot(
                            statId,
                            state.storedInvestment(),
                            state.effectiveInvestment(),
                            state.currentInvestmentCap(),
                            state.progression(),
                            state.currentTierMaximumBonus(),
                            state.transcendentMaximumBonus(),
                            state.scaledBonus()
                    )
            );
        }

        Set<ResourceLocation> completedMilestones =
                new LinkedHashSet<>();

        for (String rawId :
                payload.completedMilestones()) {

            ResourceLocation milestoneId =
                    ResourceLocation.tryParse(
                            rawId
                    );

            if (milestoneId != null) {
                completedMilestones.add(
                        milestoneId
                );
            }
        }

        ProgressSnapshot progress =
                new ProgressSnapshot(
                        payload.progress().status(),
                        parseOptionalId(
                                payload.progress().nextTierId()
                        ),
                        payload.progress().effectiveInvestment(),
                        payload.progress().requiredInvestment(),
                        payload.progress().developedStats(),
                        payload.progress().requiredDevelopedStats(),
                        payload.progress().representedCategories(),
                        payload.progress().requiredRepresentedCategories(),
                        payload.progress().worldProgressComplete(),
                        payload.progress().readyToAscend()
                );

        boolean firstSnapshot =
                !snapshot.ready();

        snapshot =
                new Snapshot(
                        true,
                        payload.playerRevision(),
                        tierId,
                        profileId,
                        Map.copyOf(
                                balances
                        ),
                        Map.copyOf(
                                stats
                        ),
                        Set.copyOf(
                                completedMilestones
                        ),
                        progress
                );

        if (firstSnapshot) {
            EssenceAscendance.LOGGER.info(
                    "Synchronized Essence Ascendance player state: tier={}, profile={}, essences={}, stats={}",
                    tierId,
                    profileId,
                    balances.size(),
                    stats.size()
            );
        }
    }

    private static ResourceLocation parseOptionalId(
            String rawId
    ) {
        if (rawId == null
                || rawId.isBlank()) {
            return null;
        }

        return ResourceLocation.tryParse(
                rawId
        );
    }

    public record Snapshot(
            boolean ready,
            long playerRevision,
            ResourceLocation tierId,
            ResourceLocation balanceProfileId,
            Map<ResourceLocation, Long> availableEssence,
            Map<ResourceLocation, StatSnapshot> stats,
            Set<ResourceLocation> completedMilestones,
            ProgressSnapshot progress
    ) {
        public Snapshot {
            availableEssence =
                    Map.copyOf(
                            availableEssence
                    );

            stats =
                    Map.copyOf(
                            stats
                    );

            completedMilestones =
                    Set.copyOf(
                            completedMilestones
                    );
        }

        private static Snapshot empty() {
            return new Snapshot(
                    false,
                    0L,
                    null,
                    null,
                    Map.of(),
                    Map.of(),
                    Set.of(),
                    ProgressSnapshot.empty()
            );
        }
    }

    public record StatSnapshot(
            ResourceLocation statId,
            long storedInvestment,
            long effectiveInvestment,
            long currentInvestmentCap,
            double progression,
            double currentTierMaximumBonus,
            double transcendentMaximumBonus,
            double scaledBonus
    ) {
    }

    public record ProgressSnapshot(
            PlayerEssenceSyncPayload.ProgressStatus status,
            ResourceLocation nextTierId,
            long effectiveInvestment,
            long requiredInvestment,
            int developedStats,
            int requiredDevelopedStats,
            int representedCategories,
            int requiredRepresentedCategories,
            boolean worldProgressComplete,
            boolean readyToAscend
    ) {
        public boolean hasNextTier() {
            return nextTierId != null;
        }

        private static ProgressSnapshot empty() {
            return new ProgressSnapshot(
                    PlayerEssenceSyncPayload.ProgressStatus.CONFIGURATION_ERROR,
                    null,
                    0L,
                    0L,
                    0,
                    0,
                    0,
                    0,
                    false,
                    false
            );
        }
    }
}

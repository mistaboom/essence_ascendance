package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.attunement.AttunementSnapshot;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncPayload;
import com.mistaboom.essence_ascendance.network.BonusTrackSnapshot;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import dev.architectury.networking.NetworkManager;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
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
                        ClientPacketDispatch.queue(context,
                                () -> accept(
                                        payload
                                )
                        )
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

    public static boolean hasCompletedMilestone(
            ResourceLocation milestoneId
    ) {
        return snapshot
                .completedMilestones()
                .contains(
                        milestoneId
                );
    }

    /** Compatibility name retained for existing client presentation callers. */
    public static boolean hasCompletedInternalMilestone(
            ResourceLocation milestoneId
    ) {
        return hasCompletedMilestone(milestoneId);
    }

    public static boolean ownsSkill(
            ResourceLocation skillId
    ) {
        return snapshot
                .ownedSkills()
                .containsKey(
                        skillId
                );
    }

    public static Optional<SkillPurchaseSnapshot> skillPurchase(
            ResourceLocation skillId
    ) {
        return Optional.ofNullable(
                snapshot
                        .ownedSkills()
                        .get(
                                skillId
                        )
        );
    }

    public static Optional<ResourceLocation> loadoutSelection(
            ResourceLocation selectionId
    ) {
        return Optional.ofNullable(
                snapshot
                        .loadoutSelections()
                        .get(
                                selectionId
                        )
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

            if (essenceId != null
                    && EssenceRegistry.get(essenceId).isPresent()) {
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
                    StatSnapshot.from(state)
            );
        }

        Map<ResourceLocation, MilestoneSnapshot> skillMilestones =
                new LinkedHashMap<>();
        Set<ResourceLocation> completedMilestones =
                new LinkedHashSet<>();

        for (String rawId : payload.completedMilestones()) {
            ResourceLocation milestoneId = ResourceLocation.tryParse(rawId);
            if (milestoneId != null) {
                completedMilestones.add(milestoneId);
            }
        }

        for (PlayerEssenceSyncPayload.MilestoneState state :
                payload.skillMilestones()) {
            ResourceLocation milestoneId =
                    ResourceLocation.tryParse(state.milestoneId());

            if (milestoneId == null) {
                continue;
            }

            MilestoneSnapshot milestone = new MilestoneSnapshot(
                    milestoneId,
                    state.displayName(),
                    state.resolvable(),
                    state.complete()
            );
            skillMilestones.put(milestoneId, milestone);

            if (milestone.complete()) {
                completedMilestones.add(milestoneId);
            }
        }

        Map<ResourceLocation, SkillPurchaseSnapshot> ownedSkills =
                new LinkedHashMap<>();

        for (PlayerEssenceSyncPayload.OwnedSkillState state :
                payload.ownedSkills()) {
            ResourceLocation skillId =
                    ResourceLocation.tryParse(
                            state.skillId()
                    );

            ResourceLocation paidEssenceId =
                    ResourceLocation.tryParse(
                            state.paidEssenceId()
                    );

            if (skillId == null
                    || paidEssenceId == null) {
                continue;
            }

            ownedSkills.put(
                    skillId,
                    new SkillPurchaseSnapshot(
                            skillId,
                            paidEssenceId,
                            state.paidCosts(), state.hudEnabled()
                    )
            );
        }

        Map<ResourceLocation, ResourceLocation> loadoutSelections =
                new LinkedHashMap<>();

        for (PlayerEssenceSyncPayload.LoadoutSelection selection :
                payload.loadoutSelections()) {
            ResourceLocation selectionId =
                    ResourceLocation.tryParse(
                            selection.selectionId()
                    );

            ResourceLocation skillId =
                    ResourceLocation.tryParse(
                            selection.skillId()
                    );

            if (selectionId != null
                    && skillId != null) {
                loadoutSelections.put(
                        selectionId,
                        skillId
                );
            }
        }

        List<WorldRequirementSnapshot> worldRequirements =
                payload.progress()
                        .worldRequirements()
                        .stream()
                        .map(
                                requirement ->
                                        new WorldRequirementSnapshot(
                                                requirement.depth(),
                                                requirement.kind(),
                                                requirement.label(),
                                                requirement.resolvable(),
                                                requirement.complete()
                                        )
                        )
                        .toList();

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
                        payload.progress().developedStatThreshold(),
                        worldRequirements,
                        payload.progress().worldProgressComplete(),
                        payload.progress().readyToAscend()
                );

        Snapshot previousSnapshot = snapshot;
        boolean firstSnapshot = !previousSnapshot.ready();

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
                        Map.copyOf(
                                skillMilestones
                        ),
                        Set.copyOf(
                                completedMilestones
                        ),
                        Map.copyOf(
                                ownedSkills
                        ),
                        Map.copyOf(
                                loadoutSelections
                        ),
                        payload.attunement(),
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
            Map<ResourceLocation, MilestoneSnapshot> skillMilestones,
            Set<ResourceLocation> completedMilestones,
            Map<ResourceLocation, SkillPurchaseSnapshot> ownedSkills,
            Map<ResourceLocation, ResourceLocation> loadoutSelections,
            AttunementSnapshot attunement,
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

            skillMilestones =
                    Map.copyOf(
                            skillMilestones
                    );

            completedMilestones =
                    Set.copyOf(
                            completedMilestones
                    );

            ownedSkills =
                    Map.copyOf(
                            ownedSkills
                    );

            loadoutSelections =
                    Map.copyOf(
                            loadoutSelections
                    );


        }

        /** The synchronized player revision is the persisted Nexus revision. */
        public long nexusRevision() {
            return playerRevision;
        }

        private static Snapshot empty() {
            return new Snapshot(
                    false,
                    0L,
                    null,
                    null,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Set.of(),
                    Map.of(),
                    Map.of(),
                    AttunementSnapshot.empty(),
                    ProgressSnapshot.empty()
            );
        }
    }

    public record SkillPurchaseSnapshot(
            ResourceLocation skillId,
            ResourceLocation essenceId,
            java.util.List<Long> paidCosts,
            boolean hudEnabled
    ) {
        public SkillPurchaseSnapshot(ResourceLocation skillId, ResourceLocation essenceId, java.util.List<Long> paidCosts) {
            this(skillId, essenceId, paidCosts, true);
        }
        public SkillPurchaseSnapshot { paidCosts = java.util.List.copyOf(paidCosts); }
        public int rank() { return paidCosts.size(); }
        public long paidCost() {
            long total = 0L;
            for (long cost : paidCosts) total = Math.addExact(total, cost);
            return total;
        }
    }

    public record MilestoneSnapshot(
            ResourceLocation milestoneId,
            String displayName,
            boolean resolvable,
            boolean complete
    ) {
    }

    public record StatSnapshot(
            ResourceLocation statId,
            long storedInvestment,
            long effectiveInvestment,
            long currentInvestmentCap,
            double progression,
            double currentTierMaximumBonus,
            double transcendentMaximumBonus,
            double scaledBonus,
            BonusTrackSnapshot track
    ) {
        public StatSnapshot { Objects.requireNonNull(track, "Missing synchronized Bonus track"); }
        public static StatSnapshot from(PlayerEssenceSyncPayload.StatState state) {
            return new StatSnapshot(ResourceLocation.parse(state.statId()), state.storedInvestment(), state.effectiveInvestment(),
                    state.currentInvestmentCap(), state.progression(), state.currentTierMaximumBonus(),
                    state.transcendentMaximumBonus(), state.scaledBonus(), state.track());
        }
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
            double developedStatThreshold,
            List<WorldRequirementSnapshot> worldRequirements,
            boolean worldProgressComplete,
            boolean readyToAscend
    ) {
        public ProgressSnapshot {
            worldRequirements = List.copyOf(worldRequirements);
        }

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
                    0.0D,
                    List.of(),
                    false,
                    false
            );
        }
    }

    public record WorldRequirementSnapshot(
            int depth,
            PlayerEssenceSyncPayload.WorldRequirementKind kind,
            String label,
            boolean resolvable,
            boolean complete
    ) {
    }
}

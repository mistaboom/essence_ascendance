package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One client-only draft shared by every Nexus mode and Essence category.
 *
 * <p>The draft never writes authoritative values. Costs are retained only for
 * projected presentation; the transaction sent to the server contains IDs and
 * targets and the server derives every cost again.</p>
 */
public final class NexusDraft {
    private final Map<ResourceLocation, Long> bonusTargets =
            new LinkedHashMap<>();
    private final Map<ResourceLocation, SkillPurchaseDraft> purchases =
            new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> loadoutTargets =
            new LinkedHashMap<>();
    private final Set<ResourceLocation> clearedLoadoutGroups =
            new LinkedHashSet<>();

    private final Map<ResourceLocation, Long> baseStoredInvestments =
            new LinkedHashMap<>();
    private final Map<ResourceLocation, Long> baseInvestmentCaps =
            new LinkedHashMap<>();
    private final Map<ResourceLocation, ClientEssenceState.SkillPurchaseSnapshot>
            baseOwnedSkills = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> baseLoadouts =
            new LinkedHashMap<>();
    private final Set<ResourceLocation> baseAttunements =
            new LinkedHashSet<>();
    private final Set<ResourceLocation> baseMilestones =
            new LinkedHashSet<>();

    private long baseRevision = Long.MIN_VALUE;
    private ResourceLocation baseTierId;
    private ResourceLocation baseProfileId;
    private boolean invalidated;

    public SyncOutcome synchronize(ClientEssenceState.Snapshot snapshot) {
        if (!snapshot.ready()) {
            return SyncOutcome.NOT_READY;
        }

        if (baseRevision == Long.MIN_VALUE) {
            captureBaseline(snapshot);
            return SyncOutcome.CAPTURED;
        }

        if (baseRevision == snapshot.nexusRevision()
                && matchesBaseline(snapshot)) {
            return SyncOutcome.UNCHANGED;
        }

        if (matchesBaseline(snapshot)) {
            baseRevision = snapshot.nexusRevision();
            return SyncOutcome.REBASED_BALANCES;
        }

        if (!hasChanges(snapshot)) {
            clearAndCapture(snapshot);
            return SyncOutcome.CAPTURED;
        }

        invalidated = true;
        return SyncOutcome.INVALIDATED;
    }

    public void clearAndCapture(ClientEssenceState.Snapshot snapshot) {
        bonusTargets.clear();
        purchases.clear();
        loadoutTargets.clear();
        clearedLoadoutGroups.clear();
        invalidated = false;
        baseRevision = Long.MIN_VALUE;
        baseTierId = null;
        baseProfileId = null;
        baseStoredInvestments.clear();
        baseInvestmentCaps.clear();
        baseOwnedSkills.clear();
        baseLoadouts.clear();
        baseAttunements.clear();
        baseMilestones.clear();
        if (snapshot.ready()) {
            captureBaseline(snapshot);
        }
    }

    public long baseRevision() {
        return baseRevision;
    }

    public boolean invalidated() {
        return invalidated;
    }

    public void stageBonus(ResourceLocation statId, long target) {
        bonusTargets.put(statId, Math.max(0L, target));
    }

    public long bonusTarget(
            ResourceLocation statId,
            ClientEssenceState.Snapshot snapshot
    ) {
        ClientEssenceState.StatSnapshot stat = snapshot.stats().get(statId);
        long fallback = stat == null ? 0L : stat.storedInvestment();
        return Math.max(0L, bonusTargets.getOrDefault(statId, fallback));
    }

    public Map<ResourceLocation, Long> bonusTargets() {
        return Map.copyOf(bonusTargets);
    }

    public void stagePurchase(
            ResourceLocation skillId,
            ResourceLocation essenceId,
            long projectedCost
    ) {
        purchases.put(
                skillId,
                new SkillPurchaseDraft(
                        skillId,
                        essenceId,
                        Math.max(0L, projectedCost)
                )
        );
    }

    public void unstagePurchase(ResourceLocation skillId) {
        purchases.remove(skillId);
    }

    public boolean isPurchaseStaged(ResourceLocation skillId) {
        return purchases.containsKey(skillId);
    }

    public Set<ResourceLocation> stagedPurchaseIds() {
        return Set.copyOf(purchases.keySet());
    }

    public List<SkillPurchaseDraft> stagedPurchases() {
        return List.copyOf(purchases.values());
    }

    public boolean willOwn(
            ResourceLocation skillId,
            ClientEssenceState.Snapshot snapshot
    ) {
        return snapshot.ownedSkills().containsKey(skillId)
                || purchases.containsKey(skillId);
    }

    public void stageLoadout(
            ResourceLocation groupId,
            ResourceLocation selectedSkillId
    ) {
        if (selectedSkillId == null) {
            loadoutTargets.remove(groupId);
            clearedLoadoutGroups.add(groupId);
            return;
        }

        loadoutTargets.put(groupId, selectedSkillId);
        clearedLoadoutGroups.remove(groupId);
    }

    public ResourceLocation projectedLoadout(
            ResourceLocation groupId,
            ClientEssenceState.Snapshot snapshot
    ) {
        if (clearedLoadoutGroups.contains(groupId)) {
            return null;
        }
        return loadoutTargets.getOrDefault(
                groupId,
                snapshot.loadoutSelections().get(groupId)
        );
    }

    public Map<ResourceLocation, ResourceLocation> finalLoadouts(
            ClientEssenceState.Snapshot snapshot
    ) {
        Map<ResourceLocation, ResourceLocation> result =
                new LinkedHashMap<>(snapshot.loadoutSelections());
        for (ResourceLocation groupId : clearedLoadoutGroups) {
            result.remove(groupId);
        }
        result.putAll(loadoutTargets);
        return Map.copyOf(result);
    }

    public Set<ResourceLocation> clearedLoadoutGroups() {
        return Set.copyOf(clearedLoadoutGroups);
    }

    public Map<ResourceLocation, Long> finalBonusTargets(
            ClientEssenceState.Snapshot snapshot
    ) {
        Map<ResourceLocation, Long> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ClientEssenceState.StatSnapshot> entry :
                snapshot.stats().entrySet()) {
            result.put(
                    entry.getKey(),
                    Math.max(
                            0L,
                            bonusTargets.getOrDefault(
                                    entry.getKey(),
                                    entry.getValue().storedInvestment()
                            )
                    )
            );
        }
        return Map.copyOf(result);
    }

    public long projectedAvailable(
            ResourceLocation essenceId,
            ClientEssenceState.Snapshot snapshot,
            Map<ResourceLocation, ResourceLocation> statEssences
    ) {
        long projected = Math.max(
                0L,
                snapshot.availableEssence().getOrDefault(essenceId, 0L)
        );

        for (Map.Entry<ResourceLocation, Long> entry : bonusTargets.entrySet()) {
            if (!essenceId.equals(statEssences.get(entry.getKey()))) {
                continue;
            }
            ClientEssenceState.StatSnapshot state =
                    snapshot.stats().get(entry.getKey());
            if (state == null) {
                continue;
            }
            projected = safeSignedAdd(
                    projected,
                    state.storedInvestment() - Math.max(0L, entry.getValue())
            );
        }

        for (SkillPurchaseDraft purchase : purchases.values()) {
            if (essenceId.equals(purchase.essenceId())) {
                projected = safeSignedAdd(projected, -purchase.projectedCost());
            }
        }

        return projected;
    }

    public long projectedBonusInvestment(
            ResourceLocation essenceId,
            ClientEssenceState.Snapshot snapshot,
            Map<ResourceLocation, ResourceLocation> statEssences
    ) {
        long total = 0L;
        for (Map.Entry<ResourceLocation, ClientEssenceState.StatSnapshot> entry :
                snapshot.stats().entrySet()) {
            if (!essenceId.equals(statEssences.get(entry.getKey()))) {
                continue;
            }
            total = safeNonNegativeAdd(
                    total,
                    bonusTargets.getOrDefault(
                            entry.getKey(),
                            entry.getValue().storedInvestment()
                    )
            );
        }
        return total;
    }

    public boolean hasBonusChanges(ClientEssenceState.Snapshot snapshot) {
        for (Map.Entry<ResourceLocation, Long> entry : bonusTargets.entrySet()) {
            ClientEssenceState.StatSnapshot state = snapshot.stats().get(entry.getKey());
            if (state != null
                    && Math.max(0L, entry.getValue()) != state.storedInvestment()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPurchaseChanges() {
        return !purchases.isEmpty();
    }

    public boolean hasLoadoutChanges(ClientEssenceState.Snapshot snapshot) {
        Map<ResourceLocation, ResourceLocation> authoritative =
                snapshot.loadoutSelections();
        for (ResourceLocation groupId : clearedLoadoutGroups) {
            if (authoritative.containsKey(groupId)) {
                return true;
            }
        }
        for (Map.Entry<ResourceLocation, ResourceLocation> entry :
                loadoutTargets.entrySet()) {
            if (!Objects.equals(
                    authoritative.get(entry.getKey()),
                    entry.getValue()
            )) {
                return true;
            }
        }
        return false;
    }

    public boolean hasChanges(ClientEssenceState.Snapshot snapshot) {
        return hasBonusChanges(snapshot)
                || hasPurchaseChanges()
                || hasLoadoutChanges(snapshot);
    }

    public int bonusChangeCount(ClientEssenceState.Snapshot snapshot) {
        int count = 0;
        for (Map.Entry<ResourceLocation, Long> entry : bonusTargets.entrySet()) {
            ClientEssenceState.StatSnapshot state = snapshot.stats().get(entry.getKey());
            if (state != null
                    && Math.max(0L, entry.getValue()) != state.storedInvestment()) {
                count++;
            }
        }
        return count;
    }

    public int purchaseCount() {
        return purchases.size();
    }

    public int loadoutChangeCount(ClientEssenceState.Snapshot snapshot) {
        int count = 0;
        Set<ResourceLocation> groups = new LinkedHashSet<>();
        groups.addAll(loadoutTargets.keySet());
        groups.addAll(clearedLoadoutGroups);
        for (ResourceLocation groupId : groups) {
            if (!Objects.equals(
                    snapshot.loadoutSelections().get(groupId),
                    projectedLoadout(groupId, snapshot)
            )) {
                count++;
            }
        }
        return count;
    }

    public List<ResourceLocation> changedBonusIds(
            ClientEssenceState.Snapshot snapshot
    ) {
        List<ResourceLocation> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Long> entry : bonusTargets.entrySet()) {
            ClientEssenceState.StatSnapshot state = snapshot.stats().get(entry.getKey());
            if (state != null
                    && Math.max(0L, entry.getValue()) != state.storedInvestment()) {
                result.add(entry.getKey());
            }
        }
        return List.copyOf(result);
    }

    private void captureBaseline(ClientEssenceState.Snapshot snapshot) {
        baseRevision = snapshot.nexusRevision();
        baseTierId = snapshot.tierId();
        baseProfileId = snapshot.balanceProfileId();
        baseStoredInvestments.clear();
        baseInvestmentCaps.clear();
        for (Map.Entry<ResourceLocation, ClientEssenceState.StatSnapshot> entry :
                snapshot.stats().entrySet()) {
            baseStoredInvestments.put(
                    entry.getKey(),
                    entry.getValue().storedInvestment()
            );
            baseInvestmentCaps.put(
                    entry.getKey(),
                    entry.getValue().currentInvestmentCap()
            );
        }
        baseOwnedSkills.clear();
        baseOwnedSkills.putAll(snapshot.ownedSkills());
        baseLoadouts.clear();
        baseLoadouts.putAll(snapshot.loadoutSelections());
        baseAttunements.clear();
        baseAttunements.addAll(snapshot.completedAttunements());
        baseMilestones.clear();
        baseMilestones.addAll(snapshot.completedMilestones());
    }

    private boolean matchesBaseline(ClientEssenceState.Snapshot snapshot) {
        if (!Objects.equals(baseTierId, snapshot.tierId())
                || !Objects.equals(baseProfileId, snapshot.balanceProfileId())
                || !baseOwnedSkills.equals(snapshot.ownedSkills())
                || !baseLoadouts.equals(snapshot.loadoutSelections())
                || !baseAttunements.equals(snapshot.completedAttunements())
                || !baseMilestones.equals(snapshot.completedMilestones())
                || baseStoredInvestments.size() != snapshot.stats().size()
                || baseInvestmentCaps.size() != snapshot.stats().size()) {
            return false;
        }

        for (Map.Entry<ResourceLocation, ClientEssenceState.StatSnapshot> entry :
                snapshot.stats().entrySet()) {
            if (!Objects.equals(
                    baseStoredInvestments.get(entry.getKey()),
                    entry.getValue().storedInvestment()
            ) || !Objects.equals(
                    baseInvestmentCaps.get(entry.getKey()),
                    entry.getValue().currentInvestmentCap()
            )) {
                return false;
            }
        }
        return true;
    }

    private long safeSignedAdd(long left, long delta) {
        try {
            return Math.addExact(left, delta);
        } catch (ArithmeticException ignored) {
            return delta >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private long safeNonNegativeAdd(long left, long right) {
        long safeLeft = Math.max(0L, left);
        long safeRight = Math.max(0L, right);
        if (Long.MAX_VALUE - safeLeft < safeRight) {
            return Long.MAX_VALUE;
        }
        return safeLeft + safeRight;
    }

    public record SkillPurchaseDraft(
            ResourceLocation skillId,
            ResourceLocation essenceId,
            long projectedCost
    ) {
        public SkillPurchaseDraft {
            Objects.requireNonNull(skillId, "Skill ID cannot be null");
            Objects.requireNonNull(essenceId, "Essence ID cannot be null");
            if (projectedCost < 0L) {
                throw new IllegalArgumentException("Projected cost cannot be negative");
            }
        }
    }

    public enum SyncOutcome {
        NOT_READY,
        CAPTURED,
        UNCHANGED,
        REBASED_BALANCES,
        INVALIDATED
    }
}

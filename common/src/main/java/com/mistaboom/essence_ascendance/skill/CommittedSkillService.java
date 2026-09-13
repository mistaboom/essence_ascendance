package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.EssenceServerConfig;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.progression.PermanentMilestoneService;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server-only, committed skill evaluation shared by gameplay and diagnostics.
 *
 * <p>One immutable catalog evaluation is reused until authoritative progression
 * or configuration changes. No draft, client snapshot, equipment multiplier,
 * or skill-purchase spending is part of Bonus investment. Weak player keys and
 * explicit lifecycle eviction prevent this cache from retaining old entities.</p>
 *
 * <p>Called only on the server thread. Provider changes without a saved revision
 * flow through the existing milestone-state-change/lifecycle invalidation hook.</p>
 */
public final class CommittedSkillService {

    private static final Map<ServerPlayer, CachedState> CACHE = new WeakHashMap<>();
    private static long configGeneration;

    private CommittedSkillService() {
    }

    public static boolean isEffective(ServerPlayer player, ResourceLocation skillId) {
        Objects.requireNonNull(skillId, "Skill ID cannot be null");
        return state(player).effectiveIds().contains(skillId);
    }

    public static int effectiveRank(ServerPlayer player, ResourceLocation skillId) {
        return isEffective(player, skillId) ? state(player).context().authoritativeRank(skillId) : 0;
    }

    public static Set<ResourceLocation> effectiveIds(ServerPlayer player) {
        return state(player).effectiveIds();
    }

    public static SkillEvaluationContext context(ServerPlayer player) {
        return state(player).context();
    }

    public static Map<ResourceLocation, SkillEvaluationResult> evaluations(ServerPlayer player) {
        return state(player).evaluations();
    }

    public static SkillEvaluationResult evaluation(ServerPlayer player, ResourceLocation skillId) {
        SkillRegistry.require(Objects.requireNonNull(skillId, "Skill ID cannot be null"));
        return state(player).evaluations().get(skillId);
    }

    /** Progression/provider invalidation; the next query rebuilds once. */
    public static void invalidate(ServerPlayer player) {
        CACHE.remove(player);
    }

    public static void forget(ServerPlayer player) {
        invalidate(player);
    }

    /** Config reload and server shutdown also discard all old config contexts. */
    public static void invalidateAll() {
        CACHE.clear();
        configGeneration++;
    }

    private static CachedState state(ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        PlayerEssenceData data = EssenceSavedData.get(player.server)
                .getPlayerData(player.getUUID());
        EssenceServerConfig config = EssenceConfigManager.get();
        CachedState cached = CACHE.get(player);
        if (cached != null
                && cached.data() == data
                && cached.revision() == data.nexusRevision()
                && cached.config() == config
                && cached.configGeneration() == configGeneration) {
            return cached;
        }

        SkillEvaluationContext context = assembleContext(player, data);
        Map<ResourceLocation, SkillEvaluationResult> evaluations =
                Map.copyOf(SkillStateEvaluator.evaluateAll(context));
        Set<ResourceLocation> effectiveIds = new LinkedHashSet<>();
        evaluations.forEach((id, result) -> {
            if (result.effective()) {
                effectiveIds.add(id);
            }
        });
        CachedState updated = new CachedState(
                data, data.nexusRevision(), config, configGeneration,
                context, evaluations, Set.copyOf(effectiveIds)
        );
        CACHE.put(player, updated);
        return updated;
    }

    private static SkillEvaluationContext assembleContext(
            ServerPlayer player,
            PlayerEssenceData data
    ) {
        Map<ResourceLocation, Long> bonusTotals = new LinkedHashMap<>();
        for (StatDefinition stat : EssenceStatRegistry.values()) {
            ResourceLocation essenceId = stat.essenceType().id();
            long current = bonusTotals.getOrDefault(essenceId, 0L);
            long amount = Math.max(0L, data.getInvested(stat));
            bonusTotals.put(essenceId, amount > Long.MAX_VALUE - current
                    ? Long.MAX_VALUE : current + amount);
        }

        Set<ResourceLocation> completedMilestones = PermanentMilestoneService.completedIds(
                PermanentMilestoneService.resolveAll(
                        player, SkillRegistry.referencedPermanentMilestoneIds()
                )
        );
        return SkillEvaluationContext.committed(
                data.getTierId(),
                data.getSkillRanks(),
                data.getLoadoutSelections(),
                completedMilestones,
                Set.of(),
                bonusTotals
        );
    }

    private record CachedState(
            PlayerEssenceData data,
            long revision,
            EssenceServerConfig config,
            long configGeneration,
            SkillEvaluationContext context,
            Map<ResourceLocation, SkillEvaluationResult> evaluations,
            Set<ResourceLocation> effectiveIds
    ) {
    }
}

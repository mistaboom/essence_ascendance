package com.mistaboom.essence_ascendance.attunement;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.data.SkillPurchase;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncService;
import com.mistaboom.essence_ascendance.progression.AscendanceAttemptResult;
import com.mistaboom.essence_ascendance.progression.AscendanceEngine;
import com.mistaboom.essence_ascendance.progression.BonusDevelopment;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Sole server entry point. Eligibility belongs to observed outcomes, never client claims or skill IDs. */
public final class AttunementService {
    private static final Map<ServerPlayer, Long> LAST_SENT = new WeakHashMap<>();
    private AttunementService() {}
    public static AttunementContribution submit(ServerPlayer player, String root, String activity, String source, double units) {
        return submit(player, new AttunementEvent(root, List.of(AttunementEvent.Outcome.eligible(activity, source, units)))).getFirst();
    }
    public static List<AttunementContribution> submit(ServerPlayer player, AttunementEvent event) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Attunement outcomes must be observed on the server thread");
        var runtime = EssenceConfigManager.serverRuntime();
        if (runtime == null) return rejected(event, "profile_unavailable");
        EssenceSavedData saved = EssenceSavedData.get(player.server);
        PlayerEssenceData data = saved.getPlayerData(player.getUUID());
        AttunementProfile profile = runtime.attunement();
        AttunementProfile.Chapter chapter = profile.chapter(data.getTier().id().toString());
        if (chapter == null) return event.outcomes().stream().map(o -> new AttunementContribution(event.rootActionId(), o.activityId(), "", o.sourceSignature(), 0, 1, 1, 1, 0, isMaximumTier(data) ? "maximum_tier" : "unavailable")).toList();
        data.attunement().chapter(chapter.id());
        int before = completed(data.attunement(), chapter);
        List<AttunementContribution> result = new ArrayList<>();
        for (AttunementEvent.Outcome outcome : event.outcomes()) {
            AttunementActivity activity = AttunementActivityRegistry.get(outcome.activityId());
            long invested = activity == null ? 0 : investment(data, activity.categoryId());
            result.add(data.attunement().contribute(event.rootActionId(), outcome, chapter, profile.policy(), invested));
        }
        saved.setDirty();
        int after = completed(data.attunement(), chapter);
        if (shouldPromoteAutomatically(data, chapter)) {
            AscendanceAttemptResult promotion = AscendanceEngine.ascend(player);
            if (promotion.status() == AscendanceAttemptResult.Status.SUCCESS) {
                com.mistaboom.essence_ascendance.nexus.AscendanceTierFeedback.play(player, true);
                LAST_SENT.put(player, data.attunement().revision());
                return List.copyOf(result);
            }
        }
        if (after != before) {
            PlayerEssenceSyncService.forceSync(player); LAST_SENT.put(player, data.attunement().revision());
        }
        return List.copyOf(result);
    }
    private static List<AttunementContribution> rejected(AttunementEvent event, String reason) {
        return event.outcomes().stream().map(outcome -> {
            var activity = AttunementActivityRegistry.get(outcome.activityId());
            return new AttunementContribution(event.rootActionId(), outcome.activityId(),
                    activity == null ? "" : activity.categoryId(), outcome.sourceSignature(), 0, 1, 1, 1, 0, reason);
        }).toList();
    }
    /** Ordinary movement, hunger and outcome details synchronize at most once each second. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 0 || !EssenceConfigManager.authoritativeReady()) return;
        PlayerEssenceData data = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        AttunementProfile.Chapter chapter = EssenceConfigManager.serverRuntime().attunement()
                .chapter(data.getTier().id().toString());
        if (chapter != null && shouldPromoteAutomatically(data, chapter)
                && AscendanceEngine.ascend(player).status() == AscendanceAttemptResult.Status.SUCCESS) {
            com.mistaboom.essence_ascendance.nexus.AscendanceTierFeedback.play(player, true);
            LAST_SENT.put(player, data.attunement().revision());
            return;
        }
        long revision = data.attunement().revision();
        if (LAST_SENT.getOrDefault(player, -1L) != revision) {
            PlayerEssenceSyncService.forceSync(player); LAST_SENT.put(player, revision);
        }
    }
    public static long investment(PlayerEssenceData data, String categoryId) {
        return investment(data, categoryId, EssenceConfigManager.get().balanceProfile());
    }
    public static long investment(PlayerEssenceData data, String categoryId, BalanceProfileDefinition profile) {
        long total = BonusDevelopment.equivalentInvestment(data, categoryId, profile);
        // Historical receipt category and paid values remain authoritative even if a skill is later repriced.
        for (SkillPurchase purchase : data.getOwnedSkills().values()) if (purchase.essenceId().toString().equals(categoryId))
            for (long cost : purchase.paidCosts()) total = saturatedAdd(total, cost);
        return total;
    }
    private static long saturatedAdd(long a, long b) { return b > Long.MAX_VALUE-a ? Long.MAX_VALUE : a+b; }
    public static int completed(AttunementLedger ledger, AttunementProfile.Chapter chapter) {
        return (int) chapter.categories().keySet().stream().filter(c -> ledger.progress(c) >= AttunementLedger.SCALE).count();
    }
    public static boolean shouldPromoteAutomatically(PlayerEssenceData data, AttunementProfile.Chapter chapter) {
        return !data.getTier().grantsPower() && completed(data.attunement(), chapter) >= chapter.requiredCategories();
    }
    private static boolean isMaximumTier(PlayerEssenceData data) {
        return com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry.values().stream()
                .noneMatch(t -> t.order() > data.getTier().order());
    }
    public static AttunementSnapshot snapshot(PlayerEssenceData data) {
        var runtime = EssenceConfigManager.serverRuntime();
        return runtime == null ? AttunementSnapshot.empty() : snapshot(data, runtime.attunement());
    }
    public static AttunementSnapshot snapshot(PlayerEssenceData data, AttunementProfile profile) {
        AttunementProfile.Chapter chapter = profile.chapter(data.getTier().id().toString());
        boolean maximum = isMaximumTier(data);
        if (chapter == null && !maximum) return AttunementSnapshot.empty();
        if (!maximum) data.attunement().chapter(chapter.id());
        List<AttunementSnapshot.Category> categories = new ArrayList<>();
        for (var essence : EssenceRegistry.values()) {
            String id = essence.id().toString();
            AttunementProfile.Category category = maximum ? null : chapter.categories().get(id);
            List<AttunementSnapshot.Method> methods = AttunementActivityRegistry.values().stream().filter(a -> a.categoryId().equals(id))
                    .map(a -> new AttunementSnapshot.Method(a.id(), a.labelKey(), a.descriptionKey(), maximum ? AttunementLedger.SCALE : data.attunement().methodProgress(a.id()), maximum || chapter.activities().containsKey(a.id()))).toList();
            categories.add(new AttunementSnapshot.Category(id, maximum ? AttunementLedger.SCALE : data.attunement().progress(id),
                    category == null ? 0 : category.target(), category == null ? 1 : AttunementLedger.investmentMultiplier(investment(data,id), category.investmentReference(), profile.policy().maximumAcceleration()),
                    methods, data.attunement().recent(id)));
        }
        return new AttunementSnapshot(maximum ? "" : chapter.id(), maximum ? 0 : chapter.requiredCategories(),
                maximum ? categories.size() : completed(data.attunement(), chapter), maximum, categories);
    }
}

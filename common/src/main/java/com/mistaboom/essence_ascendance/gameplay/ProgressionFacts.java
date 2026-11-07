package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.attunement.*;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.server.level.ServerPlayer;

/** Small observations at the successful commit owner; no achievement predicates or durable history. */
public final class ProgressionFacts {
    private ProgressionFacts() { }

    public static String receipt(String root, String detail) {
        return AttunementGameplay.sourceSignature(root + ":" + detail);
    }

    /** Admin-set seal progress clears its method credit, so it cannot masquerade as earned work. */
    public static int earnedSeals(PlayerEssenceData data, AttunementProfile.Chapter chapter) {
        if (chapter == null || !data.attunement().chapterId().equals(chapter.id())) return 0;
        int earned = 0;
        for (String category : chapter.categories().keySet()) {
            if (data.attunement().progress(category) < AttunementLedger.SCALE) continue;
            long methods = 0;
            for (AttunementActivity activity : AttunementActivityRegistry.values()) {
                if (!activity.categoryId().equals(category)) continue;
                methods = Math.min(AttunementLedger.SCALE, methods + data.attunement().methodProgress(activity.id()));
            }
            if (methods >= AttunementLedger.SCALE) earned++;
        }
        return earned;
    }

    public record AscensionSnapshot(String chapterId, int earnedSeals, int requiredSeals,
                                     boolean night, boolean fullFunctionalArmor) { }

    /** Capture before setTier/applyNexusTransaction clears the departing chapter. */
    public static AscensionSnapshot beforeAscension(ServerPlayer player, PlayerEssenceData data) {
        var runtime = EssenceConfigManager.serverRuntime();
        var chapter = runtime == null ? null : runtime.attunement().chapter(data.getTierId().toString());
        // Native isNight/isDarkOutside use sky darkness, including weather/attribute changes.
        // This fact requires the nighttime clock interval in an actual cycling sky dimension.
        //? if >=26.1.2
        /*long clock = player.serverLevel().getDefaultClockTime();*/
        //? if <26.1.2
        long clock = player.serverLevel().getDayTime();
        boolean night = isNight(clock, player.serverLevel().dimensionType().hasSkyLight(),
                player.serverLevel().dimensionType().hasFixedTime());
        return new AscensionSnapshot(chapter == null ? "" : chapter.id(), earnedSeals(data, chapter),
                chapter == null ? Integer.MAX_VALUE : chapter.requiredCategories(),
                night, EquipmentSnapshots.capture(player).fullFunctionalArmor());
    }

    public static boolean isNight(long clock, boolean hasSkyLight, boolean hasFixedTime) {
        long time = Math.floorMod(clock, 24_000L);
        return hasSkyLight && !hasFixedTime && time >= 13_000L && time < 23_000L;
    }

    public static void ascended(ServerPlayer player, String action, AscendanceTierDefinition from,
                                AscendanceTierDefinition to, boolean manual, AscensionSnapshot before) {
        if (!AttunementGameplay.eligible(player) || before.earnedSeals() < before.requiredSeals()) return;
        ProgressionOutcomes.publish(player.server, new ProgressionOutcome.Ascended(action, player.getUUID(),
                player.server.overworld().getGameTime(), from.id(), to.id(), from.order(), to.order(),
                before.chapterId(), before.earnedSeals(), manual, before.night(), before.fullFunctionalArmor()));
    }

    /** Hold the tier fixed to isolate purchased development from an accompanying Ascension. */
    public static double purchasedBonusIncrease(PlayerEssenceData data, StatDefinition stat, long targetInvestment) {
        var profile = EssenceConfigManager.get().balanceProfile();
        var tier = data.getTier();
        long cap = profile.getInvestmentCap(tier, stat);
        double before = StatScalingService.realizedProgressionForInvestment(stat,
                Math.min(cap, data.getInvested(stat)), tier, profile);
        double after = StatScalingService.realizedProgressionForInvestment(stat,
                Math.min(cap, targetInvestment), tier, profile);
        return Math.max(0, EssenceConfigManager.get().statMaxBonus(stat) * (after - before));
    }
}

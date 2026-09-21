package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.data.SkillPurchase;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Real receipt/NBT/runtime parameter checks. Does not substitute for network or live server tests. */
public final class SkillRankLedgerTest {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init();
        var receipt = new SkillPurchase(EssenceTypes.OFFENSE.id(), 17).append(31).append(79);
        check(receipt.rank() == 3 && receipt.paidCost() == 127, "Rank ledger does not sum actual payments");
        check(receipt.refundAbove(1) == 110 && receipt.refundAbove(0) == 127, "Refund recomputed or lost a payment");
        check(receipt.retain(1).paidCosts().equals(List.of(17L)), "Retained rank rewrote earlier cost");
        rejects(() -> new SkillPurchase(EssenceTypes.OFFENSE.id(), List.of(Long.MAX_VALUE, 1L)));
        rejects(() -> new SkillPurchase(EssenceTypes.OFFENSE.id(), List.of(-1L)));
        var data = new PlayerEssenceData();
        check(data.recordSkillPurchase(SkillIds.FRENZY, receipt), "Initial receipt rejected");
        check(!data.recordSkillPurchase(SkillIds.FRENZY, new SkillPurchase(EssenceTypes.OFFENSE.id(), 1)), "Receipt overwritten");
        var restored = PlayerEssenceData.load(data.save());
        check(restored.skillRank(SkillIds.FRENZY) == 3 && restored.getOwnedSkills().equals(data.getOwnedSkills()),
                "NBT roundtrip changed rank receipts");
        check(restored.nexusRevision() == data.nexusRevision(), "NBT roundtrip changed transaction revision");

        var costs = new TreeMap<net.minecraft.resources.ResourceLocation, Long>();
        AscendanceTierRegistry.values().forEach(tier -> costs.put(tier.id(), 1000L * (tier.order() + 1)));
        var curves = SkillBalanceGenerator.generate(costs, 1.0);
        SkillBalanceRuntime.install(curves);
        var settings = SkillEffectBalanceSettings.defaults();
        check(SkillRankEffectScaling.apply(settings, Map.of(SkillIds.FRENZY, 1)).equals(settings),
                "Nominal first-state candidate must preserve its input values");
        var ranked = SkillRankEffectScaling.apply(settings,
                Map.of(SkillIds.FRENZY, 3, SkillIds.PIERCING_PROJECTILE, 3));
        check(ranked.frenzy().damageBonusPercentPerStack() > settings.frenzy().damageBonusPercentPerStack(),
                "Future rank curve has no actual damage effect");
        check(ranked.frenzy().maxStacks() == settings.frenzy().maxStacks(), "Scaling changed unrelated stack mechanics");
        check(ranked.projectiles().piercingDamageMultiplier() > settings.projectiles().piercingDamageMultiplier()
                && ranked.projectiles().piercingDamageMultiplier() < 1, "Projectile rank retention is unbounded/inert");
        var edited = new TreeMap<>(curves);
        var frenzy = curves.get(SkillIds.FRENZY.toString());
        var editedRanks = new java.util.ArrayList<>(frenzy.ranks());
        var third = editedRanks.get(2);
        editedRanks.set(2, new SkillBalanceRuntime.ResolvedRank(3, third.cost(), third.powerMultiplier() * 1.01));
        edited.put(SkillIds.FRENZY.toString(), new SkillBalanceRuntime.ResolvedSkill(frenzy.maximumRank(), editedRanks));
        SkillBalanceRuntime.install(edited);
        check(SkillRankEffectScaling.apply(settings, Map.of(SkillIds.FRENZY, 3)).frenzy().damageBonusPercentPerStack()
                > ranked.frenzy().damageBonusPercentPerStack(), "Resolved future rank edit is disconnected from gameplay tuning");
        rejects(() -> new SkillBalanceRuntime.ResolvedSkill(1,
                List.of(new SkillBalanceRuntime.ResolvedRank(1, 10, 2))));
        check(receipt.refundAbove(1) == 110, "Profile change altered historical refund");
        SkillBalanceRuntime.clear();
        System.out.println("SkillRankLedgerTest: rank receipts, exact refunds, NBT and actual rank-scaled effects passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException | ArithmeticException expected) { return; }
        throw new AssertionError("Invalid receipt/curve was accepted");
    }
}

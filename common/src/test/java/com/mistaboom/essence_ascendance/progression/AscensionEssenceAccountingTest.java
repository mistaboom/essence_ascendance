package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.data.SkillPurchase;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real account/receipt/NBT regression checks, without substituting for a live Nexus transaction. */
public final class AscensionEssenceAccountingTest {
    private static int assertions;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init();
        var offense = EssenceTypes.OFFENSE.id();
        var damage = EssenceStats.MELEE_DAMAGE.id();
        var speed = EssenceStats.MELEE_ATTACK_SPEED.id();
        var removed = ResourceLocation.fromNamespaceAndPath("test", "removed");

        var receipt = new SkillPurchase(offense, List.of(17L, 31L, 79L));
        check(total(Map.of(offense, 1000L), Map.of(), List.of()) == 1000L,
                "A player saving Essence must qualify without buying a Bonus or skill");
        check(total(Map.of(offense, 573L), Map.of(damage, 300L), List.of(receipt)) == 1000L,
                "Bonus and skill purchases counted twice or lost qualification");
        check(total(Map.of(offense, 573L), Map.of(damage, 120L, speed, 180L), List.of(receipt)) == 1000L,
                "Reallocating Bonus Essence changed qualification");
        check(total(Map.of(offense, 873L), Map.of(), List.of(receipt)) == 1000L,
                "Refunding Bonus Essence increased qualification");
        check(total(Map.of(offense, 573L + receipt.refundAbove(1)), Map.of(damage, 300L),
                List.of(receipt.retain(1))) == 1000L, "Partial rank refund counted historical payment twice");
        check(total(Map.of(offense, 700L), Map.of(damage, 300L), List.of()) == 1000L,
                "Complete rank refund changed qualification");
        check(total(Map.of(offense, 573L), Map.of(damage, 300L),
                List.of(receipt, new SkillPurchase(offense, 0L))) == 1000L,
                "A granted skill fabricated paid Essence");
        check(total(Map.of(offense, 573L, removed, Long.MAX_VALUE),
                Map.of(damage, 300L, removed, Long.MAX_VALUE),
                List.of(receipt, new SkillPurchase(removed, Long.MAX_VALUE))) == 1000L,
                "Unregistered Essence or Bonus IDs contributed to qualification");
        check(AscensionEssenceAccounting.paidSkills(List.of(receipt)) == 127L,
                "Rank receipt total was recalculated from a current price");
        check(AscensionEssenceAccounting.total(Map.of(offense, 573L), Map.of(damage, 300L), 127L) == 1000L,
                "Client scalar accounting differs from exact owned receipts");

        Map<ResourceLocation, Long> allEssences = new LinkedHashMap<>();
        EssenceRegistry.values().forEach(essence -> allEssences.put(essence.id(), 100L));
        check(total(allEssences, Map.of(), List.of()) == 600L, "Not all six core Essences qualify equally");
        check(total(Map.of(), Map.of(damage, Long.MAX_VALUE), List.of()) == Long.MAX_VALUE,
                "Stored Bonus investment was clamped to the current tier cap");
        check(total(Map.of(offense, Long.MAX_VALUE - 1L), Map.of(damage, 1L), List.of()) == Long.MAX_VALUE,
                "Exact long boundary was lost");
        check(total(Map.of(offense, Long.MAX_VALUE), Map.of(damage, Long.MAX_VALUE),
                List.of(receipt)) == Long.MAX_VALUE, "Aggregate overflow rejected or wrapped valid accounts");
        check(AscensionEssenceAccounting.paidSkills(List.of(new SkillPurchase(offense, Long.MAX_VALUE),
                receipt)) == Long.MAX_VALUE, "Multiple receipt totals overflowed instead of saturating");
        rejects(() -> total(Map.of(offense, -1L), Map.of(), List.of()));
        rejects(() -> total(Map.of(), Map.of(damage, -1L), List.of()));
        rejects(() -> AscensionEssenceAccounting.total(Map.of(), Map.of(), -1L));

        var data = new PlayerEssenceData();
        data.setAvailable(EssenceTypes.OFFENSE, 1000L);
        check(qualifying(data) == 1000L, "Available-only authoritative account failed");
        data.addCrucibleStored(EssenceTypes.OFFENSE, 500L);
        check(qualifying(data) == 1000L, "Unchanneled Crucible reservoir counted as available");
        check(data.invest(EssenceStats.MELEE_DAMAGE, 300L), "Valid Bonus purchase failed");
        check(qualifying(data) == 1000L, "Authoritative Bonus purchase changed qualification");
        var beforeOverspend = data.save();
        check(!data.invest(EssenceStats.MELEE_DAMAGE, 701L), "Unaffordable Bonus purchase succeeded");
        check(data.save().equals(beforeOverspend) && qualifying(data) == 1000L,
                "Failed purchase mutated data or manufactured qualification");

        data.setAvailable(EssenceTypes.OFFENSE, 573L);
        check(data.recordSkillPurchase(SkillIds.FRENZY, receipt), "Paid receipt registration failed");
        check(qualifying(data) == 1000L, "Authoritative skill purchase changed qualification");
        var restored = PlayerEssenceData.load(data.save());
        check(qualifying(restored) == 1000L && restored.getOwnedSkills().equals(data.getOwnedSkills()),
                "Save/load changed qualifying Essence or receipt prices");
        check(data.applyNexusTransaction(data.getAllInvested(), data.getAllAvailable(), data.getOwnedSkills(),
                data.getLoadoutSelections(), AscendanceTiers.AWAKENED.id()), "Tier-only commit failed");
        check(qualifying(data) == 1000L, "Ascending consumed the qualification balance");
        var beforeFailure = data.save();
        rejects(() -> data.applyNexusTransaction(Map.of(damage, 300L), Map.of(offense, 699L),
                Map.of(SkillIds.FRENZY, new SkillPurchase(offense, List.of(1L, 0L, 0L))),
                Map.of(), AscendanceTiers.RESONANT.id()));
        check(data.save().equals(beforeFailure) && qualifying(data) == 1000L,
                "Rejected receipt rewrite changed balances or tier");
        data.transferCrucibleToAvailable(EssenceTypes.OFFENSE, 100L);
        check(qualifying(data) == 1100L && data.getCrucibleStored(EssenceTypes.OFFENSE) == 400L,
                "Channeling did not count newly available Essence exactly once");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "AscensionEssenceAccountingTest: " + assertions
                        + " saved/invested/paid-receipt, conservation, overflow and NBT checks PASS");
    }

    private static long total(Map<ResourceLocation, Long> available,
                              Map<ResourceLocation, Long> investments, List<SkillPurchase> receipts) {
        return AscensionEssenceAccounting.total(available, investments, receipts);
    }
    private static long qualifying(PlayerEssenceData data) {
        return AscensionEssenceAccounting.total(data.getAllAvailable(), data.getAllInvested(),
                data.getOwnedSkills().values());
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
    private static void rejects(Runnable work) {
        try { work.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError("Invalid accounting state or transaction was accepted");
    }
}

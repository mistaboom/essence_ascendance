package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.data.SkillPurchase;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Essence currently owned, either available or held in Bonus and skill investments.
 * A purchase, refund or Bonus reallocation changes its location, never its total.
 * This is qualification for Ascension, not an additional payment or an earned counter.
 */
public final class AscensionEssenceAccounting {
    private AscensionEssenceAccounting() {}

    /** Complete current state or an already validated final transaction state. */
    public static long total(Map<ResourceLocation, Long> available,
                             Map<ResourceLocation, Long> bonusInvestments,
                             Collection<SkillPurchase> ownedSkillReceipts) {
        return total(available, bonusInvestments, paidSkills(ownedSkillReceipts));
    }

    /**
     * The scalar overload supports a client projection of actual paid receipts.
     * The caller must filter that scalar to registered Essence, just as paidSkills does.
     */
    public static long total(Map<ResourceLocation, Long> available,
                             Map<ResourceLocation, Long> bonusInvestments,
                             long registeredPaidSkillEssence) {
        Objects.requireNonNull(available, "Available Essence cannot be null");
        Objects.requireNonNull(bonusInvestments, "Bonus investments cannot be null");
        long total = nonnegative(registeredPaidSkillEssence);
        for (var essence : EssenceRegistry.values()) {
            total = saturatingAdd(total, stored(available, essence.id()));
        }
        for (var stat : EssenceStatRegistry.values()) {
            // Stored, not cap-clamped: a changed cap must not destroy qualification
            // or allow an over-cap refund to count as newly acquired Essence.
            total = saturatingAdd(total, stored(bonusInvestments, stat.id()));
        }
        return total;
    }

    /** Exact historical rank payments; zero-cost grants add nothing. */
    public static long paidSkills(Collection<SkillPurchase> ownedSkillReceipts) {
        Objects.requireNonNull(ownedSkillReceipts, "Owned skill receipts cannot be null");
        long total = 0L;
        for (SkillPurchase receipt : ownedSkillReceipts) {
            Objects.requireNonNull(receipt, "Owned skill receipt cannot be null");
            if (EssenceRegistry.get(receipt.essenceId()).isPresent()) {
                total = saturatingAdd(total, receipt.paidCost());
            }
        }
        return total;
    }

    /** Each account is a nonnegative long; only their combined display can exceed it. */
    private static long saturatingAdd(long left, long right) {
        nonnegative(right);
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long stored(Map<ResourceLocation, Long> values, ResourceLocation id) {
        Long amount = values.getOrDefault(id, 0L);
        if (amount == null) throw new IllegalArgumentException("Stored Essence cannot be null");
        return nonnegative(amount);
    }

    private static long nonnegative(long value) {
        if (value < 0L) throw new IllegalArgumentException("Qualifying Essence cannot be negative");
        return value;
    }
}

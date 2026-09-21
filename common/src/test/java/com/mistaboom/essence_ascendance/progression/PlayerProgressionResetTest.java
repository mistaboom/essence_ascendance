package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.attunement.AttunementLedger;
import com.mistaboom.essence_ascendance.attunement.AttunementAdminService;
import com.mistaboom.essence_ascendance.attunement.AttunementEvent;
import com.mistaboom.essence_ascendance.attunement.AttunementService;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.atomic.AtomicInteger;

/** Permanent player reset, receipt persistence and earned onboarding progression contracts. */
public final class PlayerProgressionResetTest {
    private static int assertions;
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EssenceTypes.init();
        EssenceStats.init();
        AscendanceTiers.init();
        MilestoneProviders.init();
        Milestones.init();
        com.mistaboom.essence_ascendance.skill.Skills.init();
        AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();

        var saved = new PlayerEssenceData().save();
        saved.putBoolean("dormant_guidebook_received", true);
        var data = PlayerEssenceData.load(saved);
        check(data.hasReceivedDormantGuidebook(), "Reset fixture requires a prior onboarding receipt");
        resetProgression(data);
        data.resetProgressionForAdmin();
        data = PlayerEssenceData.load(data.save());
        check(!data.hasReceivedDormantGuidebook(), "Reset onboarding receipt did not persist");
        var profile = RuntimeBalanceDefinition.bootstrap().attunement();
        var chapter = profile.chapter(data.getTierId().toString());
        String mobility = "essence_ascendance:mobility";
        AttunementAdminService.setPercent(data, profile, mobility, 99);
        check(!AttunementService.shouldPromoteAutomatically(data, chapter), "99% prematurely completed Latent");
        var rate = chapter.activities().get("run");
        double finishUnits = chapter.categories().get(mobility).target()
                / rate.contributionPerUnit() / profile.policy().repetitionFloor();
        data.attunement().contribute("earned_after_reset", AttunementEvent.Outcome.eligible("run", "test:running", finishUnits),
                chapter, profile.policy(), 0);
        check(AttunementService.shouldPromoteAutomatically(data, chapter), "Earned progress failed after reset and 99% setup");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("PlayerProgressionResetTest: " + assertions + " assertions PASS");
    }

    private static void resetProgression(PlayerEssenceData original) {
        CompoundTag saved = original.save();
        for (String field : java.util.List.of("available", "invested", "crucible_reservoir")) {
            CompoundTag values = new CompoundTag();
            String key = field.equals("invested") ? EssenceStatRegistry.values().iterator().next().id().toString()
                    : EssenceRegistry.values().iterator().next().id().toString();
            values.putLong(key, 100);
            saved.put(field, values);
        }
        CompoundTag milestones = new CompoundTag();
        milestones.putBoolean("test:milestone", true);
        saved.put("completed_milestones", milestones);
        var fixtureSkill = com.mistaboom.essence_ascendance.skill.SkillRegistry.values().stream()
                .filter(skill -> skill.activationPolicy() == com.mistaboom.essence_ascendance.skill.SkillActivationPolicy.TOGGLE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Reset fixture requires one real toggle skill"));
        CompoundTag receipt = new CompoundTag();
        receipt.putString("paid_essence", fixtureSkill.essenceId().toString());
        receipt.putLongArray("rank_paid_costs", new long[]{10});
        CompoundTag skills = new CompoundTag();
        skills.put(fixtureSkill.id().toString(), receipt);
        saved.put("skill_ranks", skills);
        CompoundTag selections = new CompoundTag();
        selections.putString(fixtureSkill.id().toString(), fixtureSkill.id().toString());
        saved.put("loadout_selections", selections);
        CompoundTag attunement = new AttunementLedger().save();
        attunement.putString("chapter", "test:chapter");
        attunement.getCompound("progress").putLong("test:seal", AttunementLedger.SCALE);
        attunement.getCompound("methods").putLong("test:action", AttunementLedger.SCALE);
        attunement.putLong("rejected_healing_micros", 123);
        attunement.putLongArray("discoveries", new long[]{123});
        CompoundTag source = new CompoundTag();
        source.putString("source", "test:source");
        source.putDouble("work", 12);
        ListTag history = new ListTag();
        history.add(source);
        attunement.getCompound("history").put("test:seal", history);
        saved.put("category_attunement", attunement);
        CompoundTag carry = new CompoundTag();
        carry.putDouble("test:cost", .5);
        saved.put("fractional_resource_cost_carry", carry);

        PlayerEssenceData data = PlayerEssenceData.load(saved);
        for (String field : java.util.List.of("available", "invested", "crucible_reservoir",
                "completed_milestones", "skill_ranks", "loadout_selections")) {
            check(!data.save().getCompound(field).isEmpty(), "Fixture did not populate " + field);
        }
        long revision = data.nexusRevision();
        var projectileLife = data.projectileLife();
        data.resetProgressionForAdmin();
        CompoundTag reset = data.save();
        for (String field : java.util.List.of("available", "invested", "crucible_reservoir",
                "completed_milestones", "skill_ranks", "loadout_selections")) {
            check(reset.getCompound(field).isEmpty(), "Reset retained " + field);
        }
        check(data.getTier().equals(AscendanceTiers.LATENT), "Reset did not return to Latent");
        check(data.attunement().save().equals(new AttunementLedger().save()),
                "Reset retained attunement progress, history, or auxiliary accounting");
        check(data.nexusRevision() == revision + 1, "Reset must invalidate Nexus drafts exactly once");
        check(!data.hasReceivedDormantGuidebook(), "Full progression reset failed to rearm the onboarding reward");
        check(data.projectileLife().equals(projectileLife)
                        && reset.getCompound("fractional_resource_cost_carry").equals(carry),
                "Reset modified unrelated equipment accounting");

        // The original bug returned early whenever all Essence wallets were empty.
        data.attunement().chapter("test:empty_wallet_progress");
        revision = data.nexusRevision();
        data.resetProgressionForAdmin();
        check(data.attunement().chapterId().isEmpty() && data.nexusRevision() == revision + 1,
                "Empty wallets prevented attunement reset");
        revision = data.nexusRevision();
        data.markAttunementChangedForAdmin();
        check(data.nexusRevision() == revision + 1, "Operator seal edit left stale Nexus drafts valid");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}

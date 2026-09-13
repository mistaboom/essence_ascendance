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

/** Real item/NBT checks for one-time onboarding delivery and complete operator progression resets. */
public final class DormantGuidebookTest {
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

        PlayerEssenceData data = new PlayerEssenceData();
        AtomicInteger handouts = new AtomicInteger();
        check(!DormantGuidebookService.deliverIfEligible(data, book -> {
            handouts.incrementAndGet();
            return true;
        }) && handouts.get() == 0, "Latent received the powered-tier reward");

        data.setTier(AscendanceTiers.DORMANT);
        check(!DormantGuidebookService.deliverIfEligible(data, book -> false)
                        && !data.hasReceivedDormantGuidebook(),
                "Failed delivery consumed the receipt");
        check(DormantGuidebookService.deliverIfEligible(data, book -> {
            check(book.is(Items.BOOK) && book.getCount() == 1, "Placeholder is not one ordinary book");
            check(book.get(DataComponents.ITEM_NAME).getContents() instanceof TranslatableContents,
                    "Guidebook name is not localized");
            check(book.get(DataComponents.LORE).lines().stream()
                            .allMatch(line -> line.getContents() instanceof TranslatableContents),
                    "Guidebook lore is not localized");
            handouts.incrementAndGet();
            return true;
        }) && data.hasReceivedDormantGuidebook(), "Dormant did not receive its book");

        PlayerEssenceData reconnect = PlayerEssenceData.load(data.save());
        for (var tier : java.util.List.of(AscendanceTiers.DORMANT, AscendanceTiers.AWAKENED,
                AscendanceTiers.LATENT, AscendanceTiers.DORMANT)) {
            reconnect.setTier(tier);
            check(!DormantGuidebookService.deliverIfEligible(reconnect, book -> {
                handouts.incrementAndGet();
                return true;
            }), "Reconnection or tier change handed out a second book");
        }
        check(handouts.get() == 1, "Duplicate reward was delivered");

        var skippedTier = new PlayerEssenceData();
        skippedTier.setTier(AscendanceTiers.TRANSCENDENT);
        check(DormantGuidebookService.deliverIfEligible(skippedTier, book -> true),
                "Operator progression skipping Dormant left the welcome reward unreachable");

        AtomicInteger drops = new AtomicInteger();
        ItemStack fits = DormantGuidebookService.createPlaceholder();
        Inventory inventory = new Inventory(null);
        check(DormantGuidebookService.placeBook(fits, inventory, book -> {
            drops.incrementAndGet();
            return true;
        }) == DormantGuidebookService.DeliveryResult.INVENTORY && drops.get() == 0,
                "A book already placed in inventory was also dropped");
        check(inventory.getItem(0).is(Items.BOOK) && inventory.getItem(0).getCount() == 1,
                "Inventory success did not insert the real book");
        for (int slot = 0; slot < inventory.items.size(); slot++) inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        ItemStack full = DormantGuidebookService.createPlaceholder();
        check(DormantGuidebookService.placeBook(full, inventory, book -> {
            check(book.is(Items.BOOK) && book.getCount() == 1, "Full inventory lost the book remainder");
            drops.incrementAndGet();
            return true;
        }) == DormantGuidebookService.DeliveryResult.DROPPED && drops.get() == 1,
                "Full inventory did not fall back to a drop");
        check(DormantGuidebookService.placeBook(DormantGuidebookService.createPlaceholder(),
                inventory, book -> false) == DormantGuidebookService.DeliveryResult.FAILED,
                "Canceled fallback drop counted as delivery");

        // Fail if insertion reaches Minecraft's Creative-specific discard path.
        Inventory creativeFull = new Inventory(null) {
            @Override public boolean add(int slot, ItemStack stack) {
                throw new AssertionError("Full Creative inventory must drop directly, never consume an unplaceable book");
            }
        };
        for (int slot = 0; slot < creativeFull.items.size(); slot++) creativeFull.setItem(slot, new ItemStack(Items.STONE, 64));
        check(DormantGuidebookService.placeBook(DormantGuidebookService.createPlaceholder(), creativeFull,
                        book -> !book.isEmpty()) == DormantGuidebookService.DeliveryResult.DROPPED,
                "Full Creative inventory lost its reward");

        ItemStack existing = DormantGuidebookService.createPlaceholder();
        existing.setCount(63);
        inventory.setItem(12, existing);
        check(DormantGuidebookService.placeBook(DormantGuidebookService.createPlaceholder(), inventory,
                        book -> { throw new AssertionError("Accepting stack should receive the book"); })
                        == DormantGuidebookService.DeliveryResult.INVENTORY && inventory.getItem(12).getCount() == 64,
                "A compatible existing stack was ignored");

        check(DormantGuidebookService.giveForAdmin(data, book -> DormantGuidebookService.DeliveryResult.INVENTORY)
                        == DormantGuidebookService.DeliveryResult.INVENTORY,
                "Explicit guide recovery was blocked by an existing receipt");
        PlayerEssenceData recovery = new PlayerEssenceData();
        check(DormantGuidebookService.giveForAdmin(recovery, book -> DormantGuidebookService.DeliveryResult.FAILED)
                        == DormantGuidebookService.DeliveryResult.FAILED && !recovery.hasReceivedDormantGuidebook(),
                "Failed operator recovery consumed the reward receipt");
        check(DormantGuidebookService.giveForAdmin(recovery, book -> DormantGuidebookService.DeliveryResult.DROPPED)
                        == DormantGuidebookService.DeliveryResult.DROPPED && recovery.hasReceivedDormantGuidebook(),
                "Successful operator recovery failed to record delivery");

        resetProgression(data);
        replayResetOnboarding(data);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "DormantGuidebookTest: " + assertions + " reward/reset assertions PASS");
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
        CompoundTag receipt = new CompoundTag();
        receipt.putString("paid_essence", "test:owned");
        receipt.putLongArray("rank_paid_costs", new long[]{10, 20});
        CompoundTag skills = new CompoundTag();
        skills.put("test:skill", receipt);
        saved.put("skill_ranks", skills);
        CompoundTag selections = new CompoundTag();
        selections.putString("test:slot", "test:skill");
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

    private static void replayResetOnboarding(PlayerEssenceData previouslyRewarded) {
        PlayerEssenceData data = PlayerEssenceData.load(previouslyRewarded.save());
        check(data.hasReceivedDormantGuidebook(), "Repro requires a previous delivery receipt");
        data.resetProgressionForAdmin();
        data = PlayerEssenceData.load(data.save());
        check(!data.hasReceivedDormantGuidebook(), "Reset receipt did not persist through save/reload");
        var profile = RuntimeBalanceDefinition.bootstrap().attunement();
        var chapter = profile.chapter(data.getTierId().toString());
        String mobility = "essence_ascendance:mobility";
        AttunementAdminService.setPercent(data, profile, mobility, 99);
        check(!AttunementService.shouldPromoteAutomatically(data, chapter), "99% Mobility prematurely completed Latent");
        check(!DormantGuidebookService.deliverIfEligible(data, book -> true), "99% Mobility delivered the book while still Latent");
        var rate = chapter.activities().get("run");
        double finishUnits = chapter.categories().get(mobility).target()
                / rate.contributionPerUnit() / profile.policy().repetitionFloor();
        data.attunement().contribute("earned_after_reset", AttunementEvent.Outcome.eligible("run", "test:running", finishUnits),
                chapter, profile.policy(), 0);
        check(AttunementService.shouldPromoteAutomatically(data, chapter), "Earned running did not complete Latent after an operator 99% setup");
        data.setTier(AscendanceTiers.DORMANT);
        AtomicInteger handouts = new AtomicInteger();
        check(DormantGuidebookService.deliverIfEligible(data, book -> { handouts.incrementAndGet(); return true; }),
                "Reset, 99% Mobility, and earned completion failed to deliver a new guidebook");
        check(!DormantGuidebookService.deliverIfEligible(PlayerEssenceData.load(data.save()),
                        book -> { handouts.incrementAndGet(); return true; }) && handouts.get() == 1,
                "Reconnect duplicated the newly rearmed reward");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}

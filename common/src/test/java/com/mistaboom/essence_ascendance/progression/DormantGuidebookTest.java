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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicInteger;

/** Real item/NBT checks for one-time onboarding delivery and re-delivery after operator reset (opt-in). */
public final class DormantGuidebookTest {
    private static int assertions;
    private static net.minecraft.world.item.Item ARCHIVE;

    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Standalone Minecraft bootstraps freeze the item registry before an
        // Architectury mod container exists. This registered vanilla singleton
        // exercises the delivery algorithm through the production factory seam;
        // Archive registration/model identity is covered by ArchiveFoundationTest.
        ARCHIVE = net.minecraft.world.item.Items.WRITABLE_BOOK;
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
        }, DormantGuidebookTest::archive) && handouts.get() == 0, "Latent received the powered-tier reward");

        data.setTier(AscendanceTiers.DORMANT);
        check(!DormantGuidebookService.deliverIfEligible(data, book -> false, DormantGuidebookTest::archive)
                        && !data.hasReceivedDormantGuidebook(),
                "Failed delivery consumed the receipt");
        check(DormantGuidebookService.deliverIfEligible(data, book -> {
            check(book.is(ARCHIVE)
                            && book.getCount() == 1,
                    "Delivery did not create one real Ascendance Archive item");
            handouts.incrementAndGet();
            return true;
        }, DormantGuidebookTest::archive) && data.hasReceivedDormantGuidebook(), "Dormant did not receive its book");

        PlayerEssenceData reconnect = PlayerEssenceData.load(data.save());
        for (var tier : java.util.List.of(AscendanceTiers.DORMANT, AscendanceTiers.AWAKENED,
                AscendanceTiers.LATENT, AscendanceTiers.DORMANT)) {
            reconnect.setTier(tier);
            check(!DormantGuidebookService.deliverIfEligible(reconnect, book -> {
                handouts.incrementAndGet();
                return true;
            }, DormantGuidebookTest::archive), "Reconnection or tier change handed out a second book");
        }
        check(handouts.get() == 1, "Duplicate reward was delivered");

        var skippedTier = new PlayerEssenceData();
        skippedTier.setTier(AscendanceTiers.TRANSCENDENT);
        check(DormantGuidebookService.deliverIfEligible(skippedTier, book -> true, DormantGuidebookTest::archive),
                "Operator progression skipping Dormant left the welcome reward unreachable");

        AtomicInteger drops = new AtomicInteger();
        ItemStack fits = archive();
        Inventory inventory = new Inventory(null);
        check(DormantGuidebookService.placeBook(fits, inventory, book -> {
            drops.incrementAndGet();
            return true;
        }) == DormantGuidebookService.DeliveryResult.INVENTORY && drops.get() == 0,
                "A book already placed in inventory was also dropped");
        check(inventory.getItem(0).is(ARCHIVE)
                        && inventory.getItem(0).getCount() == 1,
                "Inventory success did not insert the real Archive");
        for (int slot = 0; slot < inventory.items.size(); slot++) inventory.setItem(slot, new ItemStack(net.minecraft.world.item.Items.STONE, 64));
        ItemStack full = archive();
        check(DormantGuidebookService.placeBook(full, inventory, book -> {
            check(book.is(ARCHIVE)
                            && book.getCount() == 1, "Full inventory lost the Archive remainder");
            drops.incrementAndGet();
            return true;
        }) == DormantGuidebookService.DeliveryResult.DROPPED && drops.get() == 1,
                "Full inventory did not fall back to a drop");
        check(DormantGuidebookService.placeBook(archive(),
                inventory, book -> false) == DormantGuidebookService.DeliveryResult.FAILED,
                "Canceled fallback drop counted as delivery");

        // Fail if insertion reaches Minecraft's Creative-specific discard path.
        Inventory creativeFull = new Inventory(null) {
            @Override public boolean add(int slot, ItemStack stack) {
                throw new AssertionError("Full Creative inventory must drop directly, never consume an unplaceable book");
            }
        };
        for (int slot = 0; slot < creativeFull.items.size(); slot++) creativeFull.setItem(slot, new ItemStack(net.minecraft.world.item.Items.STONE, 64));
        check(DormantGuidebookService.placeBook(archive(), creativeFull,
                        book -> !book.isEmpty()) == DormantGuidebookService.DeliveryResult.DROPPED,
                "Full Creative inventory lost its reward");

        // The real Archive is intentionally non-stackable. Preserve coverage
        // of the generic compatible-stack insertion branch with ordinary books.
        ItemStack existing = new ItemStack(net.minecraft.world.item.Items.BOOK);
        existing.setCount(63);
        inventory.setItem(12, existing);
        check(DormantGuidebookService.placeBook(new ItemStack(net.minecraft.world.item.Items.BOOK), inventory,
                        book -> { throw new AssertionError("Accepting stack should receive the book"); })
                        == DormantGuidebookService.DeliveryResult.INVENTORY && inventory.getItem(12).getCount() == 64,
                "A compatible existing stack was ignored");

        check(DormantGuidebookService.giveForAdmin(data, book -> DormantGuidebookService.DeliveryResult.INVENTORY,
                        DormantGuidebookTest::archive)
                        == DormantGuidebookService.DeliveryResult.INVENTORY,
                "Explicit guide recovery was blocked by an existing receipt");
        PlayerEssenceData recovery = new PlayerEssenceData();
        check(DormantGuidebookService.giveForAdmin(recovery, book -> DormantGuidebookService.DeliveryResult.FAILED,
                        DormantGuidebookTest::archive)
                        == DormantGuidebookService.DeliveryResult.FAILED && !recovery.hasReceivedDormantGuidebook(),
                "Failed operator recovery consumed the reward receipt");
        check(DormantGuidebookService.giveForAdmin(recovery, book -> DormantGuidebookService.DeliveryResult.DROPPED,
                        DormantGuidebookTest::archive)
                        == DormantGuidebookService.DeliveryResult.DROPPED && recovery.hasReceivedDormantGuidebook(),
                "Successful operator recovery failed to record delivery");

        replayResetOnboarding(data);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "DormantGuidebookTest: " + assertions + " reward/reset assertions PASS");
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
        check(!DormantGuidebookService.deliverIfEligible(data, book -> true, DormantGuidebookTest::archive),
                "99% Mobility delivered the book while still Latent");
        var rate = chapter.activities().get("run");
        double finishUnits = chapter.categories().get(mobility).target()
                / rate.contributionPerUnit() / profile.policy().repetitionFloor();
        data.attunement().contribute("earned_after_reset", AttunementEvent.Outcome.eligible("run", "test:running", finishUnits),
                chapter, profile.policy(), 0);
        check(AttunementService.shouldPromoteAutomatically(data, chapter), "Earned running did not complete Latent after an operator 99% setup");
        data.setTier(AscendanceTiers.DORMANT);
        AtomicInteger handouts = new AtomicInteger();
        check(DormantGuidebookService.deliverIfEligible(data, book -> { handouts.incrementAndGet(); return true; },
                        DormantGuidebookTest::archive),
                "Reset, 99% Mobility, and earned completion failed to deliver a new guidebook");
        check(!DormantGuidebookService.deliverIfEligible(PlayerEssenceData.load(data.save()),
                        book -> { handouts.incrementAndGet(); return true; }, DormantGuidebookTest::archive)
                        && handouts.get() == 1,
                "Reconnect duplicated the newly rearmed reward");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    private static ItemStack archive() { return new ItemStack(ARCHIVE); }
}

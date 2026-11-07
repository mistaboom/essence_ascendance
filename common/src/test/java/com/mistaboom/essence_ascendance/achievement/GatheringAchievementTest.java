package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import com.mistaboom.essence_ascendance.gathering.GeneratedOreOrigin;
import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.GatheringOutcome;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Actual rule outcomes, origin proof codec and durable bounded dimension partials. Native producers have a separate fixture. */
public final class GatheringAchievementTest {
    private static int checks, next;
    private static final UUID ALICE = new UUID(7, 1), BOB = new UUID(7, 2);
    private static final long NOW = 100;
    private static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether", END = "minecraft:the_end";
    private static GatheringOutcome.Context c(UUID actor, String dimension) { return new GatheringOutcome.Context("gathering-test-" + ++next, actor, dimension, NOW); }
    private static GatheringOutcome.Context c() { return c(ALICE, OVERWORLD); }
    private static GatheringOutcome.Momentum momentum(UUID actor, String rhythm, double before, double after, double maximum, boolean eligibleOrigin) {
        return new GatheringOutcome.Momentum(c(actor, OVERWORLD), rhythm, before, after, maximum, eligibleOrigin);
    }
    private static final class Harness {
        final GatheringAchievementRules rules = new GatheringAchievementRules();
        AchievementProgress state = new AchievementProgress();
        final Set<Integer> awards = new HashSet<>();
        boolean consume(GatheringOutcome fact) { return rules.consume(fact, NOW, true, state, awards::add); }
    }
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        rhythm(); ore(); dimensions(); origins(); receipts(); persistence();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("Gathering achievement rules: " + checks + " checks passed");
    }
    private static void rhythm() {
        var h = new Harness();
        for (int i = 0; i < 30; i++) h.consume(momentum(ALICE, "unobserved", 1, 1, 1, true));
        check(h.awards.isEmpty(), "Already-capped snapshot without observed crossing cannot start continuation");
        h.consume(momentum(ALICE, "a", .9, 1, 1, true));
        for (int i = 0; i < 19; i++) h.consume(momentum(ALICE, "a", 1, 1, 1, true));
        check(h.awards.isEmpty(), "The maximum-reaching break is excluded from the 20 subsequent blocks");
        h.consume(momentum(BOB, "a", 1, 1, 1, true));
        check(h.awards.isEmpty(), "Another player's capped harvest cannot continue actor-scoped rhythm");
        var twentieth = momentum(ALICE, "a", 1, 1, 1, true); h.consume(twentieth);
        check(h.awards.equals(Set.of(122)), "Exactly 20 fresh capped harvests after crossing earn");
        h.awards.clear(); h.consume(twentieth); h.consume(momentum(ALICE, "a", 1, 1, 1, true));
        check(h.awards.isEmpty(), "Duplicate and saturated continuation cannot replay revoke");
        h.consume(momentum(ALICE, "b", .9, 1, 1, true));
        for (int i = 0; i < 19; i++) h.consume(momentum(ALICE, "b", 1, 1, 1, true));
        h.consume(momentum(ALICE, "c", 1, 1, 1, true));
        check(h.awards.isEmpty(), "A skill/lifecycle reset creates another rhythm that cannot inherit 19 blocks");
        h.rules.forget(ALICE); h.consume(momentum(ALICE, "b", 1, 1, 1, true));
        check(h.awards.isEmpty(), "Explicit actual runtime reset clears continuation");
        for (double speed : new double[]{0, 1}) for (boolean origin : new boolean[]{false, true}) {
            var negative = new Harness(); negative.consume(momentum(ALICE, "settings", .9, 1, speed, origin));
            for (int i = 0; i < 20; i++) negative.consume(momentum(ALICE, "settings", 1, 1, speed, origin));
            check(negative.awards.contains(122) == (speed > 0 && origin), "Zero effective speed or creative/admin origin cannot fabricate maximum rhythm");
        }
        var broken = new Harness(); broken.consume(momentum(ALICE, "break", .9, 1, 1, true));
        for (int i = 0; i < 10; i++) broken.consume(momentum(ALICE, "break", 1, 1, 1, true));
        broken.consume(momentum(ALICE, "break", 1, .5, 1, true));
        for (int i = 0; i < 20; i++) broken.consume(momentum(ALICE, "break", 1, 1, 1, true));
        check(broken.awards.isEmpty(), "Losing actual maximum invalidates continuation until a new crossing");
    }
    private static void ore() {
        for (boolean generated : new boolean[]{false,true}) for (boolean tool : new boolean[]{false,true})
            for (boolean underwater : new boolean[]{false,true}) for (boolean aquatic : new boolean[]{false,true}) {
                var h = new Harness(); h.consume(new GatheringOutcome.Harvest(c(), "essence_ascendance:adaptive_latent_ore", generated, tool, underwater, aquatic));
                check(h.awards.contains(126) == (generated && tool && underwater && aquatic), "Affirmative generation, actual correct harvest, underwater and effective Aquatic Body all required");
            }
        var h = new Harness(); h.consume(new GatheringOutcome.Harvest(c(), "minecraft:diamond_ore", false, true, true, true));
        check(h.awards.isEmpty(), "Ore identity alone never proves unknown/placed vanilla ore naturally generated");
    }
    private static void dimensions() {
        var h = new Harness();
        h.consume(new GatheringOutcome.BonusOre(c(ALICE, OVERWORLD), "minecraft:coal_ore", 1));
        h.consume(new GatheringOutcome.BonusOre(c(ALICE, NETHER), "minecraft:nether_quartz_ore", 1));
        h.consume(new GatheringOutcome.BonusOre(c(ALICE, END), "essence_ascendance:adaptive_latent_ore", 0));
        check(h.awards.isEmpty(), "Nonempty candidate list or failed/disabled native drop is not an End receipt");
        h.consume(new GatheringOutcome.BonusOre(c(ALICE, "pack:moon"), "pack:moon_ore", 3));
        check(h.awards.isEmpty(), "Custom dimensions cannot substitute for the canonical End");
        h.state = AchievementProgress.load(h.state.save()); h.state.realDeath();
        h.consume(new GatheringOutcome.BonusOre(c(ALICE, END), "essence_ascendance:adaptive_latent_ore", 1));
        check(h.awards.equals(Set.of(182)), "Three actual dimension receipts survive restart and real death");
        h.awards.clear(); h.consume(new GatheringOutcome.BonusOre(c(ALICE, END), "essence_ascendance:adaptive_latent_ore", 1));
        check(h.awards.isEmpty(), "Saturated lifetime collection does not replay revoked completion");
        var empty = new AchievementProgress(); check(!empty.naturalBonusDimension("pack:moon") && empty.empty(), "Unavailable/noncanonical dimension writes no false progress");
    }
    private static void origins() {
        var position = new BlockPos(3, 40, 8); var proof = GeneratedOreOrigin.created(OVERWORLD, position);
        check(GeneratedOreOrigin.matches(proof, OVERWORLD, position), "Positive generated-origin codec matches exact address");
        check(!GeneratedOreOrigin.matches(proof, END, position), "Moved cross-dimension ore cannot retain natural proof");
        check(!GeneratedOreOrigin.matches(proof, OVERWORLD, position.above()), "Moved/clone-position ore cannot retain proof");
        check(GeneratedOreOrigin.atAddress(proof, OVERWORLD, position) == proof
                && GeneratedOreOrigin.atAddress(proof, null, position) == proof, "Correct loaded or detached address retains exact current proof");
        var moved = GeneratedOreOrigin.atAddress(proof, null, position.above());
        check(moved == null && GeneratedOreOrigin.atAddress(moved, OVERWORLD, position) == null,
                "Detached A-to-B-to-A serialization cannot revive discarded original-position proof");
        check(GeneratedOreOrigin.atAddress(proof, END, position) == null, "Cross-dimension copy discards current proof before serialization");
        check(!GeneratedOreOrigin.matches(null, OVERWORLD, position) && !GeneratedOreOrigin.matches(new CompoundTag(), OVERWORLD, position), "Old and player-placed missing proof fails closed");
        proof.putInt("version", 2); check(!GeneratedOreOrigin.matches(proof, OVERWORLD, position), "Future origin version never grants from unknown semantics");
        check(GeneratedOreOrigin.atAddress(proof, END, position.above()) == proof, "Future origin metadata remains opaque without interpreting or awarding it");
        proof.putInt("version", 1); proof.putString("position", "wrong"); check(!GeneratedOreOrigin.matches(proof, OVERWORLD, position), "Malformed origin field rejects locally");
    }
    private static void receipts() {
        var h = new Harness(); var fact = new GatheringOutcome.Harvest(c(), "essence_ascendance:adaptive_latent_ore", true, true, true, true);
        check(h.consume(fact), "Valid committed receipt accepted"); h.awards.clear();
        check(!h.consume(fact) && h.awards.isEmpty(), "Duplicate native delivery cannot regrant");
        check(!h.rules.consume(new GatheringOutcome.Harvest(c(), "essence_ascendance:adaptive_latent_ore", true,true,true,true), NOW,false,h.state,h.awards::add), "Creative/admin/client scope cannot publish eligibility");
        var expired = new GatheringOutcome.Harvest(new GatheringOutcome.Context("expired",ALICE,OVERWORLD,0),"essence_ascendance:adaptive_latent_ore",true,true,true,true);
        check(!h.rules.consume(expired, CommittedReceiptWindow.LIFETIME_TICKS,true,h.state,h.awards::add), "Expired operation cannot replay after restart/reload");
        var future = new GatheringOutcome.Harvest(new GatheringOutcome.Context("future",ALICE,OVERWORLD,NOW+1),"essence_ascendance:adaptive_latent_ore",true,true,true,true);
        check(!h.consume(future), "Future callback rejected");
    }
    private static void persistence() {
        var state = new AchievementProgress(); state.naturalBonusDimension(OVERWORLD);
        var original = state.save(); var loaded = AchievementProgress.load(original);
        check(loaded.save().equals(original), "Version1 dimension partial roundtrips exactly");
        var entries = NbtVersionCompat.getCompound(original, "entries"); var entry = NbtVersionCompat.getCompound(entries,"a182");
        entry.putInt("mask",8); var malformed=AchievementProgress.load(original);
        check(!malformed.naturalBonusDimension(END), "Out of range dimension mask rejects instead of completing");
        entry.putInt("version",2); entry.putString("future", "preserve"); var future=AchievementProgress.load(original);
        check(!future.naturalBonusDimension(END) && future.save().equals(original), "Future rule remains opaque and preserved");
        original.putInt("version",2);var envelope=AchievementProgress.load(original);
        check(!envelope.naturalBonusDimension(END) && envelope.save().equals(original), "Future envelope preserved without writes");
    }
    private static void check(boolean condition,String message) { checks++; if (!condition) throw new AssertionError(message); }
}

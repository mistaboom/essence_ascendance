package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.ProgressionOutcome;
import com.mistaboom.essence_ascendance.gameplay.ProgressionFacts;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Executable actual rule/state/codec outcomes; native producer operations have a separate disposable-world fixture. */
public final class ProgressionAchievementTest {
    private static int checks;
    private static final UUID ALICE = new UUID(1, 1), BOB = new UUID(2, 2);
    private static final ResourceLocation UTILITY = id("utility"), COMBAT = id("combat");
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");
    private static final String CATEGORY = "essence_ascendance:combat";

    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        com.mistaboom.essence_ascendance.essence.EssenceTypes.init();
        com.mistaboom.essence_ascendance.tier.AscendanceTiers.init();
        com.mistaboom.essence_ascendance.stat.EssenceStats.init();
        committedRules(); receiptBoundaries(); activityCollections(); utilityCollection();
        ascensionRules(); equipmentCheckpoints(); persistence(); invalidFacts();
        earnedSealEvidence();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("Progression achievement rules: " + checks + " checks passed");
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("essence_ascendance", path); }
    private static ProgressionOutcome.SkillPurchased skill(String action, UUID actor, long tick, String skill, ResourceLocation category, int oldRank) {
        return new ProgressionOutcome.SkillPurchased(action, actor, tick, id(skill), category, oldRank, oldRank + 1);
    }
    private static ProgressionOutcome.ActivityContributed activity(String action, long tick, String chapter, String category, String method) {
        return new ProgressionOutcome.ActivityContributed(action, ALICE, tick, chapter, category, method, 1);
    }
    private static ProgressionOutcome.BonusPurchased bonus(String action, ResourceLocation stat, double oldHealth, double newHealth, double delta) {
        return new ProgressionOutcome.BonusPurchased(action, ALICE, 10, stat, 0, 1, oldHealth, newHealth, delta);
    }
    private static ProgressionOutcome.Ascended ascended(String action, int from, int to, int seals, boolean manual, boolean night, boolean armor) {
        return new ProgressionOutcome.Ascended(action, ALICE, 10, id("tier_" + from), id("tier_" + to), from, to,
                "chapter_" + from, seals, manual, night, armor);
    }
    private static Set<Integer> awards(ProgressionOutcome fact) {
        Set<Integer> earned = new HashSet<>();
        check(new ProgressionAchievementRules().consume(fact, fact.committedTick(), true, new AchievementProgress(), earned::add),
                "Valid committed fact consumed");
        return earned;
    }

    private static void committedRules() {
        check(awards(new ProgressionOutcome.NexusUsed("own", ALICE, 10, DIMENSION, 4, ALICE)).equals(Set.of(19)), "Own built Nexus then use earns 19");
        check(awards(new ProgressionOutcome.NexusUsed("other", ALICE, 10, DIMENSION, 4, BOB)).isEmpty(), "Teammate builder cannot satisfy player sequence");
        check(awards(new ProgressionOutcome.NexusUsed("unknown", ALICE, 10, DIMENSION, 4, null)).isEmpty(), "Unknown legacy Nexus builder is not reconstructed");
        check(awards(skill("first", ALICE, 10, "first_skill", COMBAT, 0)).equals(Set.of(26)), "First committed skill unlock earns 26");
        check(awards(skill("rank", ALICE, 10, "first_skill", COMBAT, 1)).isEmpty(), "Rank increase is not a first skill unlock");
        check(awards(new ProgressionOutcome.Crafted("matrix", ALICE, 10, id("ascendance_matrix"), 1)).equals(Set.of(28)), "Actual Matrix craft earns 28");
        check(awards(new ProgressionOutcome.Crafted("other", ALICE, 10, id("ascendance_archive"), 1)).isEmpty(), "Another committed item craft cannot earn Matrix");
        check(awards(bonus("health", id("max_health"), 20, 22, 2)).equals(Set.of(169)), "Vitality purchase increasing actual and isolated health earns 169");
        check(awards(bonus("unrelated", id("melee_damage"), 20, 22, 2)).isEmpty(), "Unrelated stat purchase cannot earn Vitality");
        check(awards(bonus("same-health", id("max_health"), 20, 20, 2)).isEmpty(), "Capped/ineffective actual maximum health purchase rejects");
        check(awards(bonus("lost-health", id("max_health"), 22, 20, 2)).isEmpty(), "Actual health loss cannot earn health increase");
        check(awards(bonus("tier-health", id("max_health"), 20, 22, 0)).isEmpty(), "Concurrent tier increase alone is not purchased bonus growth");
        check(awards(bonus("fraction", id("max_health"), 20, Math.nextUp(20.0), Double.MIN_NORMAL)).contains(169), "Any positive effective purchased maximum increase counts");
    }

    private static void receiptBoundaries() {
        var rules = new ProgressionAchievementRules(); var state = new AchievementProgress();
        List<Integer> earned = new ArrayList<>();
        var first = skill("shared-root", ALICE, 10, "one", UTILITY, 0);
        check(rules.consume(first, 10, true, state, earned::add), "First typed receipt accepted");
        check(!rules.consume(first, 10, true, state, earned::add), "Duplicate native/loader delivery ignored");
        check(earned.equals(List.of(26)), "Duplicate receipt has no award side effect");
        earned.clear(); // Native revoke removes completion only; does not clear committed receipt history.
        check(!rules.consume(first, 11, true, state, earned::add) && earned.isEmpty(), "Revoked achievement is not regranted by old receipt");
        check(rules.consume(skill("fresh", ALICE, 11, "two", UTILITY, 0), 11, true, state, earned::add) && earned.contains(26), "Fresh qualifying operation can reearn");
        earned.clear();
        check(rules.consume(skill("shared-root", ALICE, 10, "other-skill", UTILITY, 0), 11, true, state, earned::add), "Distinct skill detail under one root action is independent");
        check(rules.consume(bonus("shared-root", id("max_health"), 20, 22, 2), 11, true, state, earned::add), "Distinct typed facts under one root action are independent");
        var bob = new AchievementProgress();
        check(rules.consume(skill("shared-root", BOB, 10, "one", UTILITY, 0), 11, true, bob, earned::add), "Actor UUID scopes duplicate guard");
        check(!rules.consume(null, 11, true, state, earned::add), "Absent committed operation rejects");
        check(!rules.consume(skill("ineligible", ALICE, 11, "creative", UTILITY, 0), 11, false, state, earned::add), "Ineligible actor cannot consume historical purchase");
        check(!rules.consume(skill("future", ALICE, 12, "future", UTILITY, 0), 11, true, state, earned::add), "Future receipt rejects");
        check(rules.consume(skill("last-live", ALICE, 0, "last_live", COMBAT, 0), 199, true, state, earned::add), "Age 199 is live");
        check(!rules.consume(skill("expiry", ALICE, 0, "expiry", COMBAT, 0), 200, true, state, earned::add), "Exact age 200 expires");
        check(!rules.consume(skill("old", ALICE, 0, "old", COMBAT, 0), 201, true, state, earned::add), "Older receipt rejects");
        var independentWorld = new ProgressionAchievementRules();
        check(independentWorld.consume(first, 10, true, new AchievementProgress(), ignored -> {}), "Other world/server session does not share actor receipt history");
        var bounded = new ProgressionAchievementRules();
        for (int i = 0; i < CommittedReceiptWindow.MAX_RECEIPTS; i++)
            check(bounded.consume(new ProgressionOutcome.Crafted("capacity-" + i, ALICE, 0, id("other"), 1), 0, true, state, ignored -> {}), "Receipt fits bounded live window");
        check(!bounded.consume(new ProgressionOutcome.Crafted("overflow", ALICE, 0, id("other"), 1), 0, true, state, ignored -> {}), "Full window rejects without eviction");
        check(!bounded.consume(new ProgressionOutcome.Crafted("capacity-0", ALICE, 0, id("other"), 1), 0, true, state, ignored -> {}), "Capacity exhaustion cannot reopen prior receipt");
        check(bounded.consume(new ProgressionOutcome.Crafted("later", ALICE, 200, id("other"), 1), 200, true, state, ignored -> {}), "Expired receipt capacity is cleaned on delivery");
    }

    private static void activityCollections() {
        var rules = new ProgressionAchievementRules(); var state = new AchievementProgress(); var earned = new ArrayList<Integer>();
        check(rules.consume(activity("same-root", 10, "chapter", CATEGORY, "one"), 10, true, state, earned::add), "Positive chapter contribution accepted");
        check(rules.consume(activity("same-root", 10, "chapter", CATEGORY, "two"), 10, true, state, earned::add), "Different method in same root action accepted");
        check(!rules.consume(activity("same-root", 10, "chapter", CATEGORY, "two"), 10, true, state, earned::add), "Same method receipt deduplicates");
        check(rules.consume(activity("repeat", 10, "chapter", CATEGORY, "two"), 10, true, state, earned::add) && earned.isEmpty(), "Repeated action of one activity cannot satisfy distinct count");
        rules.consume(activity("other-cat", 10, "chapter", "essence_ascendance:mobility", "three"), 10, true, state, earned::add);
        check(earned.isEmpty(), "Activity types from another category cannot be pooled");
        rules.consume(activity("finish", 10, "chapter", CATEGORY, "three"), 10, true, state, earned::add);
        check(earned.equals(List.of(30)), "Exactly three positive distinct methods in one category earn 30");
        rules.consume(activity("fourth", 10, "chapter", CATEGORY, "four"), 10, true, state, earned::add);
        check(earned.equals(List.of(30)), "Collection caps at three without repeat award");
        earned.clear(); // Native revoke leaves the verified distinct collection intact.
        rules.consume(activity("after-revoke", 10, "chapter", CATEGORY, "five"), 10, true, state, earned::add);
        check(earned.isEmpty(), "Saturated same-chapter collection does not reconstruct an award after revoke");
        state.utilityPurchase("essence_ascendance:retained");
        var restored = AchievementProgress.load(state.save());
        check(!restored.activity("next", CATEGORY, "three"), "New chapter discards previous chapter's methods");
        check(!restored.activity("next", CATEGORY, "four"), "New chapter starts at one/two methods");
        check(restored.activity("next", CATEGORY, "five"), "New chapter earns only after three new methods");
        check(!restored.utilityPurchase("essence_ascendance:retained"), "Chapter reset retains lifetime purchase facts");
        var capped = new AchievementProgress();
        for (int category = 0; category < 6; category++) {
            for (int method = 0; method < 3; method++)
                check(capped.activity("cap", "test:category_" + category, "test:method_" + method) == (method == 2), "Six bounded categories each collect three methods");
        }
        var before = capped.save();
        check(!capped.activity("cap", "test:seventh", "test:one") && capped.save().equals(before), "Seventh category rejects without changing save");
        check(!capped.activity("cap", "test:category_0", "test:fourth") && capped.save().equals(before), "Fourth method rejects without changing save");
        check(!capped.activity("", CATEGORY, "one"), "Blank chapter rejects");
        check(!capped.activity("chapter", "Bad Identifier", "one"), "Malformed category rejects");
        check(!capped.activity("chapter", CATEGORY, "Bad Identifier"), "Malformed method rejects");
        check(!capped.activity("x".repeat(129), CATEGORY, "one"), "Chapter text is bounded");
    }

    private static void utilityCollection() {
        var rules = new ProgressionAchievementRules(); var state = new AchievementProgress(); var earned = new HashSet<Integer>();
        for (int rank = 0; rank < 5; rank++) rules.consume(skill("rank-" + rank, ALICE, 10, "same", UTILITY, rank), 10, true, state, earned::add);
        check(!earned.contains(208), "Five ranks of one skill do not earn five distinct skills");
        state = AchievementProgress.load(state.save()); // Logout/restart/refund/respec retain successful lifetime purchases.
        for (int n = 1; n < 4; n++) rules.consume(skill("distinct-" + n, ALICE, 10, "skill_" + n, UTILITY, 0), 10, true, state, earned::add);
        check(!earned.contains(208), "Four lifetime distinct utility skills are below threshold");
        rules.consume(skill("wrong-category", ALICE, 10, "combat", COMBAT, 0), 10, true, state, earned::add);
        check(!earned.contains(208), "Nonutility purchase does not count");
        rules.consume(skill("fifth", ALICE, 10, "fifth", UTILITY, 0), 10, true, state, earned::add);
        check(earned.contains(208), "Fifth distinct successful utility purchase earns 208");
        var saved = state.save();
        check(!state.utilityPurchase("essence_ascendance:sixth") && state.save().equals(saved), "Lifetime set remains capped at five");
        earned.clear(); // Native revoke never erases verified lifetime purchase facts.
        rules.consume(skill("refund-repurchase", ALICE, 11, "fifth", UTILITY, 0), 11, true, state, earned::add);
        check(!earned.contains(208), "Refund/repurchase after revoke cannot fabricate another distinct lifetime fact");
        check(!state.utilityPurchase("Invalid Skill"), "Malformed persisted skill identity rejects");
        var bob = new AchievementProgress();
        check(!bob.utilityPurchase("essence_ascendance:fifth"), "Another actor's partial state is independent");
    }

    private static void ascensionRules() {
        check(!ProgressionFacts.isNight(12_999, true, false), "Last daytime tick rejects night Ascension");
        check(ProgressionFacts.isNight(13_000, true, false), "Night starts at inclusive 13000");
        check(ProgressionFacts.isNight(22_999, true, false), "Last night tick qualifies");
        check(!ProgressionFacts.isNight(23_000, true, false), "Night ends at exclusive 23000");
        check(ProgressionFacts.isNight(37_000, true, false) && ProgressionFacts.isNight(-11_000, true, false), "Night uses calendar floor modulus across days");
        check(!ProgressionFacts.isNight(18_000, false, false) && !ProgressionFacts.isNight(18_000, true, true), "No-sky or fixed-time dimension cannot fabricate natural night");
        for (int tier = 0; tier <= 5; tier++) {
            var list = new ArrayList<Integer>(); ProgressionAchievementRules.tierMilestones(tier, list::add);
            check(list.size() == tier, "All reached tier milestones retained: " + tier);
            for (int n = 0; n < tier; n++) check(list.get(n) == 31 + n, "Milestones retain each stable tier ID");
        }
        var capped = new ArrayList<Integer>(); ProgressionAchievementRules.tierMilestones(99, capped::add);
        check(capped.equals(List.of(31, 32, 33, 34, 35)), "Tier backfill has exactly the five approved milestones");
        check(awards(ascended("latent", 0, 1, 6, false, false, false)).equals(Set.of(31)), "Latent automatic promotion is tier31 and never six-seal36");
        for (int seals = 0; seals <= 6; seals++) for (boolean manual : new boolean[]{false, true}) {
            var earned = awards(ascended("manual", 1, 2, seals, manual, false, false));
            check(earned.contains(36) == (manual && seals == 6), "Only manual post-Latent all-six checkpoint earns 36");
        }
        check(awards(new ProgressionOutcome.SealsCompleted("six", ALICE, 10, "chapter", 1, 6)).equals(Set.of(36)), "Six seals award before manual Ascension clears them");
        check(awards(new ProgressionOutcome.SealsCompleted("latent-six", ALICE, 10, "chapter", 0, 6)).isEmpty(), "Latent seal completion is outside post-Latent challenge");
        for (boolean night : new boolean[]{false, true}) for (boolean armor : new boolean[]{false, true}) {
            var earned = awards(ascended("moon", 0, 1, 0, false, night, armor));
            check(earned.contains(205) == (night && armor), "Night and full armor both captured at committed Ascension");
        }
        var state = new AchievementProgress();
        state.activity("old", CATEGORY, "one"); state.activity("old", CATEGORY, "two");
        for (int n = 0; n < 4; n++) state.utilityPurchase("test:utility_" + n);
        new ProgressionAchievementRules().consume(ascended("reset", 1, 2, 6, true, false, false), 10, true, state, ignored -> {});
        check(!state.activity("new", CATEGORY, "three"), "Ascension resets chapter activity collection");
        check(state.utilityPurchase("test:utility_five"), "Ascension preserves lifetime utility purchases");
        check(state.observeTier(2) == 2, "Committed Ascension checkpoints current tier for controlled saved-state reconciliation");
    }

    private static void equipmentCheckpoints() {
        for (String id : List.of("a061", "a072", "a139", "a166")) {
            var state = new AchievementProgress();
            check(!state.observeEquipment(id, false, true), "Nonmatching state cannot award");
            check(state.observeEquipment(id, true, true), "First valid equip transition awards");
            check(!state.observeEquipment(id, true, true), "Unchanged matching state does not duplicate");
            state = AchievementProgress.load(state.save());
            check(!state.observeEquipment(id, true, true), "Native revoke remains revoked across login/restart unchanged snapshot");
            check(!state.observeEquipment(id, false, true), "Unequip records false without award");
            check(state.observeEquipment(id, true, true), "A new legitimate matching equip transition can reearn");
            var creative = new AchievementProgress();
            check(!creative.observeEquipment(id, true, false), "Creative/spectator equipment is baseline only");
            check(!creative.observeEquipment(id, true, true), "Switching to survival with unchanged creative loadout cannot fabricate equip");
            creative.observeEquipment(id, false, true);
            check(creative.observeEquipment(id, true, true), "Later eligible actual equip can earn");
        }
        rejects(() -> new AchievementProgress().observeEquipment("a999", true, true));
        var tier = new AchievementProgress();
        check(!tier.tierObserved(), "Fresh existing-world record has no prior tier checkpoint");
        check(tier.observeTier(5) == 0, "Old world has an explicit first legitimate saved-tier reconciliation");
        check(tier.tierObserved(), "Actual tier observation closes first-install reconciliation");
        tier = AchievementProgress.load(tier.save());
        check(tier.observeTier(5) == 5, "Unchanged saved-tier checkpoint suppresses revoke/rejoin regrant");
        check(tier.observeTier(1) == 5 && tier.observeTier(2) == 1, "Tier reset/respec then new ascent is a real transition");
        var before = tier.save();
        check(tier.observeTier(-1) == -1 && tier.observeTier(6) == -1 && tier.save().equals(before), "Unknown tiers cannot corrupt checkpoint");
    }

    private static CompoundTag rule() { CompoundTag rule = new CompoundTag(); rule.putInt("version", 1); return rule; }
    private static CompoundTag envelope(String id, CompoundTag entry) {
        CompoundTag entries = new CompoundTag(); entries.put(id, entry);
        CompoundTag root = new CompoundTag(); root.putInt("version", 1); root.put("entries", entries); return root;
    }
    private static CompoundTag entries(AchievementProgress state) { return NbtVersionCompat.getCompound(state.save(), "entries"); }
    private static void persistence() {
        var empty = new AchievementProgress();
        check(empty.empty(), "Absent progress begins empty with no historical action fabrication");
        var oldPlayer = new PlayerEssenceData();
        check(!oldPlayer.save().contains("achievement_progress"), "Untouched old-world record does not gain empty ledger");
        var emptyExtended = new CompoundTag(); emptyExtended.putInt("version", 1);
        emptyExtended.put("entries", new CompoundTag()); emptyExtended.putString("extension", "retain-without-known-entries");
        var offlinePlayer = oldPlayer.save(); offlinePlayer.put("achievement_progress", emptyExtended);
        var offlineRoundTrip = PlayerEssenceData.load(offlinePlayer).save();
        check(NbtVersionCompat.getCompound(offlineRoundTrip, "achievement_progress").equals(emptyExtended),
                "Untouched offline player retains unknown fields in an empty known-version envelope");
        var state = new AchievementProgress();
        state.activity("chapter", CATEGORY, "one"); state.activity("chapter", CATEGORY, "two");
        state.utilityPurchase("test:one"); state.observeTier(3); state.observeEquipment("a061", true, false);
        CompoundTag saved = state.save(); var restored = AchievementProgress.load(saved);
        check(restored.save().equals(saved), "Known partial schema round-trips exactly");
        check(restored.activity("chapter", CATEGORY, "three"), "Chapter partial survives save/rejoin/restart");
        check(!restored.utilityPurchase("test:one"), "Distinct lifetime identity survives save/rejoin/restart");
        saved.putString("detached", "mutation");
        check(!restored.save().contains("detached"), "Save/load boundaries defensively copy mutable NBT");
        oldPlayer.achievementProgress().utilityPurchase("test:actual_purchase");
        var playerRoundTrip = PlayerEssenceData.load(oldPlayer.save());
        check(!playerRoundTrip.achievementProgress().utilityPurchase("test:actual_purchase"), "Existing UUID player record persists lifetime partials");
        playerRoundTrip.resetProgressionForAdmin();
        check(!playerRoundTrip.achievementProgress().utilityPurchase("test:actual_purchase"), "Actual admin progression reset preserves lifetime purchase facts");
        CompoundTag future = new CompoundTag(); future.putInt("version", 42); future.putString("future", "opaque");
        var futureState = AchievementProgress.load(future);
        check(futureState.tierObserved(), "Unknown envelope suppresses first-install tier backfill");
        check(!futureState.activity("chapter", CATEGORY, "one") && !futureState.utilityPurchase("test:one")
                && futureState.observeTier(3) == -1 && !futureState.observeEquipment("a061", true, true), "Unknown envelope cannot be interpreted or overwritten");
        check(futureState.save().equals(future), "Unknown envelope remains opaque");
        var futurePlayerRoot = oldPlayer.save(); futurePlayerRoot.put("achievement_progress", future);
        check(NbtVersionCompat.getCompound(PlayerEssenceData.load(futurePlayerRoot).save(), "achievement_progress").equals(future), "Player record retains unknown future envelope opaque");
        var malformedOuter = oldPlayer.save(); malformedOuter.putString("achievement_progress", "future-or-malformed-opaque");
        var malformedPlayer = PlayerEssenceData.load(malformedOuter);
        check(!malformedPlayer.achievementProgress().utilityPurchase("test:new") && malformedPlayer.achievementProgress().tierObserved(), "Noncompound player envelope disables interpretation and backfill");
        check(NbtVersionCompat.getString(malformedPlayer.save(), "achievement_progress").equals("future-or-malformed-opaque"), "Noncompound player envelope round-trips opaque");
        CompoundTag newerRule = rule(); newerRule.putInt("version", 2); newerRule.putString("future_rule", "opaque");
        var futureRule = AchievementProgress.load(envelope("a208", newerRule));
        check(!futureRule.utilityPurchase("test:one"), "Unknown rule version cannot be interpreted");
        check(NbtVersionCompat.getCompound(entries(futureRule), "a208").equals(newerRule), "Unknown rule version remains opaque");
        check(futureRule.observeTier(1) == 0, "Unknown rule does not block unrelated known entries");
        var futureTierRule = AchievementProgress.load(envelope("a031", newerRule));
        check(futureTierRule.tierObserved() && futureTierRule.observeTier(2) == -1, "Unknown tier rule suppresses reconciliation without resetting its version");
        CompoundTag unknown = rule(); unknown.putString("custom", "retained");
        var mixedEnvelope = envelope("a999", unknown); mixedEnvelope.putString("extension", "retained");
        var mixed = AchievementProgress.load(mixedEnvelope); mixed.utilityPurchase("test:one");
        check(NbtVersionCompat.getCompound(entries(mixed), "a999").equals(unknown)
                && NbtVersionCompat.getString(mixed.save(), "extension").equals("retained"), "Unknown entries/envelope fields survive known-state mutations");
        var decorated = state.save(); var decoratedEntries = NbtVersionCompat.getCompound(decorated, "entries");
        var purchase = NbtVersionCompat.getCompound(decoratedEntries, "a208"); purchase.putString("extension", "retained");
        var decoratedState = AchievementProgress.load(decorated); decoratedState.utilityPurchase("test:two");
        check(NbtVersionCompat.getString(NbtVersionCompat.getCompound(entries(decoratedState), "a208"), "extension").equals("retained"), "Unknown fields in known rule survive updates");
        malformedEntries();
    }

    private static void malformedEntries() {
        CompoundTag missing = new CompoundTag(); malformed("a208", missing);
        CompoundTag badSkill = rule(); CompoundTag badIds = new CompoundTag(); badIds.putBoolean("INVALID ID", true); badSkill.put("skills", badIds); malformed("a208", badSkill);
        CompoundTag falseId = rule(); CompoundTag falseIds = new CompoundTag(); falseIds.putBoolean("test:skill", false); falseId.put("skills", falseIds); malformed("a208", falseId);
        CompoundTag many = rule(); CompoundTag six = new CompoundTag(); for (int n = 0; n < 6; n++) six.putBoolean("test:s" + n, true); many.put("skills", six); malformed("a208", many);
        CompoundTag tier = rule(); tier.putInt("tier", 6); malformed("a031", tier);
        CompoundTag matching = rule(); matching.putInt("matching", 1); malformed("a061", matching);
        CompoundTag categories = rule(); categories.putString("chapter", "old"); CompoundTag collection = new CompoundTag();
        CompoundTag methods = new CompoundTag(); for (int n = 0; n < 4; n++) methods.putBoolean("test:m" + n, true);
        collection.put(CATEGORY, methods); categories.put("categories", collection); malformed("a030", categories);
        CompoundTag manyCategories = rule(); manyCategories.putString("chapter", "old"); CompoundTag seven = new CompoundTag();
        for (int n = 0; n < 7; n++) seven.put("test:c" + n, new CompoundTag()); manyCategories.put("categories", seven); malformed("a030", manyCategories);
        CompoundTag longChapter = rule(); longChapter.putString("chapter", "x".repeat(129)); longChapter.put("categories", new CompoundTag()); malformed("a030", longChapter);
        var scalarRoot = envelope("a208", rule()); NbtVersionCompat.getCompound(scalarRoot, "entries").putInt("a208", 1);
        var scalar = AchievementProgress.load(scalarRoot);
        check(!scalar.utilityPurchase("test:new") && scalar.observeTier(2) == 0, "Wrong known-entry type rejects locally without erasing other facts");
    }
    private static void malformed(String id, CompoundTag malformed) {
        var state = AchievementProgress.load(envelope(id, malformed));
        boolean mutation = switch (id) {
            case "a208" -> state.utilityPurchase("test:new");
            case "a030" -> state.activity("new", CATEGORY, "one");
            case "a031" -> state.observeTier(1) != -1;
            default -> state.observeEquipment(id, true, true);
        };
        check(!mutation, "Malformed known rule rejects its local writer: " + id);
        check(NbtVersionCompat.getCompound(entries(state), id).equals(malformed), "Malformed local payload is preserved without silent weakening");
        check(state.observeEquipment("a166", true, true), "Malformed local rule does not discard unrelated player progress");
    }

    private static void invalidFacts() {
        rejects(() -> skill("", ALICE, 0, "one", UTILITY, 0));
        rejects(() -> skill("x".repeat(257), ALICE, 0, "one", UTILITY, 0));
        rejects(() -> skill("null-actor", null, 0, "one", UTILITY, 0));
        rejects(() -> skill("negative-tick", ALICE, -1, "one", UTILITY, 0));
        rejects(() -> new ProgressionOutcome.SkillPurchased("bad-rank", ALICE, 0, id("one"), UTILITY, 1, 1));
        rejects(() -> new ProgressionOutcome.SkillPurchased("negative-rank", ALICE, 0, id("one"), UTILITY, -1, 0));
        rejects(() -> new ProgressionOutcome.Crafted("zero", ALICE, 0, id("ascendance_matrix"), 0));
        check(awards(new ProgressionOutcome.Crafted("large-output", ALICE, 0, id("ascendance_matrix"), 100)).equals(Set.of(28)), "Larger modded crafting output remains a positive committed Matrix craft");
        rejects(() -> new ProgressionOutcome.ActivityContributed("zero", ALICE, 0, "chapter", CATEGORY, "one", 0));
        rejects(() -> new ProgressionOutcome.ActivityContributed("excess", ALICE, 0, "chapter", CATEGORY, "one", 1_000_000_001L));
        rejects(() -> activity("blank", 0, "", CATEGORY, "one"));
        rejects(() -> new ProgressionOutcome.SealsCompleted("five", ALICE, 0, "chapter", 1, 5));
        rejects(() -> ascended("no-transition", 2, 2, 6, true, false, false));
        rejects(() -> ascended("reduction", 2, 1, 6, true, false, false));
        rejects(() -> ascended("seven-seals", 1, 2, 7, true, false, false));
        rejects(() -> bonus("nan", id("max_health"), 20, Double.NaN, 1));
        rejects(() -> bonus("infinite", id("max_health"), 20, Double.POSITIVE_INFINITY, 1));
        rejects(() -> bonus("negative-bonus", id("max_health"), 20, 22, -1));
        rejects(() -> bonus("nan-bonus", id("max_health"), 20, 22, Double.NaN));
        rejects(() -> new ProgressionOutcome.BonusPurchased("no-payment", ALICE, 0, id("max_health"), 1, 1, 20, 22, 1));
    }
    private static void earnedSealEvidence() {
        var categories = new java.util.LinkedHashMap<String, com.mistaboom.essence_ascendance.attunement.AttunementProfile.Category>();
        var rates = new java.util.LinkedHashMap<String, com.mistaboom.essence_ascendance.attunement.AttunementProfile.Rate>();
        for (var activity : com.mistaboom.essence_ascendance.attunement.AttunementActivityRegistry.values()) {
            categories.putIfAbsent(activity.categoryId(), new com.mistaboom.essence_ascendance.attunement.AttunementProfile.Category(activity.categoryId(), 100, 100));
            rates.put(activity.id(), new com.mistaboom.essence_ascendance.attunement.AttunementProfile.Rate(activity.id(), activity.categoryId(), 1, 1, "units", "test actual ledger contribution"));
        }
        var chapter = new com.mistaboom.essence_ascendance.attunement.AttunementProfile.Chapter("test:chapter", "essence_ascendance:dormant",
                "essence_ascendance:awakened", 1, categories, rates);
        var policy = new com.mistaboom.essence_ascendance.attunement.AttunementProfile.Policy(0, 1, 0, 8);
        var data = new PlayerEssenceData(); data.attunement().chapter(chapter.id());
        check(ProgressionFacts.earnedSeals(data, chapter) == 0, "Empty chapter has no earned seal evidence");
        for (String category : categories.keySet()) data.attunement().setProgressForAdmin(category, com.mistaboom.essence_ascendance.attunement.AttunementLedger.SCALE);
        check(ProgressionFacts.earnedSeals(data, chapter) == 0, "Admin-filled bars have no affirmative activity method evidence");
        int earned = 0;
        for (String category : categories.keySet()) {
            data.attunement().setProgressForAdmin(category, 0);
            var activity = com.mistaboom.essence_ascendance.attunement.AttunementActivityRegistry.values().stream()
                    .filter(a -> a.categoryId().equals(category)).findFirst().orElseThrow();
            var contribution = data.attunement().contribute("actual-" + category,
                    com.mistaboom.essence_ascendance.attunement.AttunementEvent.Outcome.eligible(activity.id(), "test:source", 100), chapter, policy, 0);
            check(contribution.finalContribution() > 0, "Real successful ledger contribution supplies earned method credit");
            check(ProgressionFacts.earnedSeals(data, chapter) == ++earned, "Only genuinely completed category method evidence counts");
        }
        check(earned == 6 && ProgressionFacts.earnedSeals(data, chapter) == 6, "All six real category seals qualify before chapter clear");
        String first = categories.keySet().iterator().next();
        data.attunement().setProgressForAdmin(first, com.mistaboom.essence_ascendance.attunement.AttunementLedger.SCALE);
        check(ProgressionFacts.earnedSeals(data, chapter) == 5, "Admin overwrite removes earned evidence for that seal only");
        data.attunement().chapter("test:next");
        check(ProgressionFacts.earnedSeals(data, chapter) == 0, "Departed chapter cannot supply seal evidence");
        check(ProgressionFacts.earnedSeals(data, null) == 0, "Capped/no chapter has no manufactured seal completion");
    }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid committed fact accepted"); }
        catch (IllegalArgumentException | NullPointerException expected) { checks++; }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}

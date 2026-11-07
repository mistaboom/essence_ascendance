package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.gameplay.CombatOutcome;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Actual consumers and versioned partial transitions; native action attribution is tested separately. */
public final class CombatAchievementTest {
    private static int checks, next;
    private static final long TICK = 50;
    private static final UUID ALICE = new UUID(1, 1), BOB = new UUID(2, 2);
    private static final String OVERWORLD = "minecraft:overworld";
    private static final CombatOutcome.Weapon SWORD = weapon("sword", 1, "MELEE");
    private static final CombatOutcome.Weapon STAB = weapon("glaive", 1, "STAB");
    private static final CombatOutcome.Weapon SWIPE = weapon("glaive", 1, "SWIPE");
    private static final CombatOutcome.Weapon OTHER = new CombatOutcome.Weapon("minecraft:diamond_sword", "", 0, "MELEE");

    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        com.mistaboom.essence_ascendance.essence.EssenceTypes.init();
        com.mistaboom.essence_ascendance.tier.AscendanceTiers.init();
        distance(); kills(); freezeAndPacify(); receipts(); barefoot(); ledger(); desperation(); persistence(); malformed(); invalidFacts();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("Combat achievement rules: " + checks + " checks passed");
    }
    private static CombatOutcome.Weapon weapon(String profile, int tier, String mode) {
        return new CombatOutcome.Weapon("essence_ascendance:ascendance_" + profile, "essence_ascendance:" + profile, tier, mode);
    }
    private static CombatOutcome.Context context(UUID actor, String type, boolean hostile, String dimension, CombatOutcome.Weapon weapon) {
        int identity = ++next;
        return new CombatOutcome.Context("action-" + identity, actor, new UUID(10, identity), type, hostile, dimension, TICK, weapon);
    }
    private static CombatOutcome.Damage damage(double distance, boolean hostile, CombatOutcome.Weapon weapon) {
        return new CombatOutcome.Damage(context(ALICE, hostile ? "minecraft:zombie" : "minecraft:cow", hostile, OVERWORLD, weapon), distance, 1);
    }
    private static CombatOutcome.Kill kill(UUID actor, String type, boolean hostile, String dimension, CombatOutcome.Weapon weapon,
                                           double actorY, boolean noBoots, boolean desperation, double health, boolean ledger, int count) {
        return new CombatOutcome.Kill(context(actor, type, hostile, dimension, weapon), -1, actorY, noBoots, desperation, health, ledger, count);
    }
    private static CombatOutcome.Kill hostile(CombatOutcome.Weapon weapon, int count) {
        return kill(ALICE, "minecraft:zombie", true, OVERWORLD, weapon, 64, false, false, 1, false, count);
    }
    private static Set<Integer> awards(CombatOutcome fact) {
        var result = new HashSet<Integer>();
        check(new CombatAchievementRules().consume(fact, TICK, true, new AchievementProgress(), result::add), "Accepted typed combat observation");
        return result;
    }
    private static void distance() {
        check(awards(damage(4, true, STAB)).isEmpty(), "Strict four-block boundary rejects equality");
        check(awards(damage(Math.nextUp(4d), true, STAB)).equals(Set.of(136)), "Strictly beyond four accepted at Dormant");
        check(awards(damage(4.5, true, weapon("glaive", 0, "STAB"))).isEmpty(), "Latent tier cannot substitute old upgrade receipt");
        check(awards(damage(Math.nextDown(8d), true, STAB)).equals(Set.of(136)), "Below eight does not earn inclusive-eight rule");
        check(awards(damage(8, true, STAB)).equals(Set.of(136, 206)), "Eight-block boundary is inclusive");
        check(awards(damage(9, true, STAB)).equals(Set.of(136, 206)), "Nine-block boundary is strict");
        check(awards(damage(Math.nextUp(9d), true, STAB)).equals(Set.of(136, 138, 206)), "Strictly beyond nine hostile damage earns all distance rules");
        check(awards(damage(10, false, STAB)).equals(Set.of(136, 206)), "Only nine-block rule requires hostile victim");
        check(awards(damage(10, true, SWORD)).isEmpty(), "Sword cannot earn glaive distance achievements");
        check(awards(damage(10, true, weapon("glaive", 0, "STAB"))).equals(Set.of(138, 206)), "Other distance rules do not invent Dormant requirement");
        check(!awards(damage(1, true, STAB)).contains(76), "Confirmed damage is still not a confirmed death");
        check(awards(damage(Double.NaN, true, STAB)).isEmpty(), "Unknown blade distance cannot satisfy any range rule");
    }
    private static void kills() {
        check(awards(hostile(STAB, 1)).equals(Set.of(76)), "Confirmed hostile stab death earns first stab");
        check(awards(hostile(SWIPE, 1)).isEmpty(), "Single swipe death is not stab or triple");
        check(awards(hostile(SWIPE, 2)).isEmpty(), "Two confirmed deaths remain below triple threshold");
        check(awards(hostile(SWIPE, 3)).equals(Set.of(77)), "Third distinct death in same causal action earns triple");
        check(awards(hostile(SWIPE, 4)).isEmpty(), "Fourth same-action death cannot replay revoked threshold");
        check(awards(hostile(STAB, 3)).equals(Set.of(76)), "Three stab deaths cannot masquerade as a swipe");
        check(awards(kill(ALICE, "minecraft:cow", false, OVERWORLD, STAB, 64, false, false, 1, false, 1)).isEmpty(), "Passive stab death rejects hostile requirement");
        var rules = new CombatAchievementRules(); var state = new AchievementProgress(); var earned = new HashSet<Integer>();
        for (int i = 0; i < 3; i++) rules.consume(hostile(SWIPE, 1), TICK, true, state, earned::add);
        check(earned.isEmpty(), "Three separate swipes never merge in award consumer");
    }
    private static void freezeAndPacify() {
        check(awards(new CombatOutcome.Freeze(context(ALICE, "minecraft:zombie", true, OVERWORLD, STAB))).equals(Set.of(85)), "Confirmed full Frostbite freeze earns85");
        check(awards(new CombatOutcome.Freeze(context(ALICE, "minecraft:cow", false, OVERWORLD, STAB))).isEmpty(), "Full passive freeze not hostile");
        check(awards(new CombatOutcome.Pacified(context(ALICE, "minecraft:pillager", true, OVERWORLD, OTHER), true, true)).equals(Set.of(207)), "Successful Sanctuary target with no encounter damage earns207");
        check(awards(new CombatOutcome.Pacified(context(ALICE, "minecraft:pillager", true, OVERWORLD, OTHER), false, true)).isEmpty(), "Another player's targeting pillager cannot count");
        check(awards(new CombatOutcome.Pacified(context(ALICE, "minecraft:pillager", true, OVERWORLD, OTHER), true, false)).isEmpty(), "Own accepted encounter damage rejects207");
        check(awards(new CombatOutcome.Pacified(context(ALICE, "minecraft:zombie", true, OVERWORLD, OTHER), true, true)).isEmpty(), "Specific pillager type required");
    }
    private static void receipts() {
        var rules = new CombatAchievementRules(); var state = new AchievementProgress(); var earned = new HashSet<Integer>();
        var fact = hostile(STAB, 1);
        check(rules.consume(fact, TICK, true, state, earned::add), "First confirmed death receipt accepted");
        earned.clear();
        check(!rules.consume(fact, TICK, true, state, earned::add) && earned.isEmpty(), "Repeated callback cannot regrant revoked completion");
        var c = fact.context();
        var otherVictim = new CombatOutcome.Context(c.actionId(), ALICE, new UUID(20, 1), c.victimType(), true, OVERWORLD, TICK, STAB);
        check(rules.consume(new CombatOutcome.Kill(otherVictim, 64, 64, false, false, 1, false, 2), TICK, true, state, earned::add), "Same-tick second victim has separate receipt");
        var otherPlayer = new CombatOutcome.Context(c.actionId(), BOB, c.victim(), c.victimType(), true, OVERWORLD, TICK, STAB);
        check(rules.consume(new CombatOutcome.Kill(otherPlayer, 64, 64, false, false, 1, false, 1), TICK, true, new AchievementProgress(), earned::add), "Same action ID scoped by actor");
        check(rules.consume(new CombatOutcome.Damage(c, 5, 1), TICK, true, state, earned::add), "Damage/death kinds have distinct receipts");
        check(!rules.consume(hostile(STAB, 1), TICK, false, state, earned::add), "Creative/spectator/ineligible facts cannot award");
        check(!rules.consume(hostile(STAB, 1), TICK - 1, true, state, earned::add), "Future receipt rejected");
        check(!rules.consume(hostile(STAB, 1), TICK + 200, true, state, earned::add), "Expired receipt rejected");
        check(!rules.consume(null, TICK, true, state, earned::add), "Canceled/failed action without fact cannot count");
        check(new CombatAchievementRules().consume(fact, TICK, true, new AchievementProgress(), earned::add), "Independent world session has independent receipt scope");
    }
    private static void barefoot() {
        var rules = new CombatAchievementRules(); var state = new AchievementProgress(); var earned = new HashSet<Integer>();
        for (int i = 0; i < 12; i++) {
            rules.consume(kill(ALICE,"minecraft:zombie",true,"minecraft:the_nether",SWORD,-1,true,false,1,false,1),TICK,true,state,earned::add);
            rules.consume(kill(ALICE,"minecraft:zombie",true,OVERWORLD,SWORD,0,true,false,1,false,1),TICK,true,state,earned::add);
            rules.consume(new CombatOutcome.Kill(context(ALICE,"minecraft:zombie",true,OVERWORLD,SWORD),0,-1,true,false,1,false,1),TICK,true,state,earned::add);
            rules.consume(kill(ALICE,"minecraft:zombie",true,OVERWORLD,SWORD,-1,false,false,1,false,1),TICK,true,state,earned::add);
            rules.consume(kill(ALICE,"minecraft:zombie",true,OVERWORLD,OTHER,-1,true,false,1,false,1),TICK,true,state,earned::add);
            rules.consume(kill(ALICE,"minecraft:cow",false,OVERWORLD,SWORD,-1,true,false,1,false,1),TICK,true,state,earned::add);
        }
        check(earned.isEmpty(), "Wrong dimension/actor elevation/victim elevation/boots/weapon/hostility never adds barefoot credit");
        for (int i = 1; i <= 10; i++) {
            rules.consume(kill(ALICE,"minecraft:zombie",true,OVERWORLD,SWORD,-0.001,true,false,1,false,1),TICK,true,state,earned::add);
            check(earned.contains(120) == (i == 10), "Only tenth qualifying barefoot kill earns");
        }
        earned.clear();
        rules.consume(kill(ALICE,"minecraft:zombie",true,OVERWORLD,SWORD,-1,true,false,1,false,1),TICK,true,state,earned::add);
        check(earned.isEmpty(), "Saturated lifetime barefoot counter cannot restore native revoke");
        check(count(state,"a120") == 10, "Barefoot partial remains bounded at ten");
    }
    private static void ledger() {
        var rules = new CombatAchievementRules(); var alice = new AchievementProgress(); var bob = new AchievementProgress();
        var earned = new HashSet<Integer>();
        for (int i = 0; i < 6; i++) {
            rules.consume(kill(ALICE,"minecraft:zombie",true,OVERWORLD,OTHER,64,false,false,1,false,1),TICK,true,alice,earned::add);
            rules.consume(kill(ALICE,"minecraft:cow",false,OVERWORLD,OTHER,64,false,false,1,true,1),TICK,true,alice,earned::add);
        }
        check(earned.isEmpty(), "Untracked or passive deaths cannot substitute Ledger membership");
        for (int i = 1; i <= 4; i++) rules.consume(kill(ALICE,"minecraft:zombie",true,OVERWORLD,OTHER,64,false,false,1,true,1),TICK,true,alice,earned::add);
        rules.consume(kill(BOB,"minecraft:zombie",true,OVERWORLD,OTHER,64,false,false,1,true,1),TICK,true,bob,earned::add);
        check(earned.isEmpty() && count(alice,"a215") == 4 && count(bob,"a215") == 1, "Two players retain separate lifetime counters");
        var fifth = kill(ALICE,"minecraft:zombie",true,OVERWORLD,OTHER,64,false,false,1,true,1);
        rules.consume(fifth,TICK,true,alice,earned::add);
        check(earned.equals(Set.of(215)), "Fifth authoritative tracked hostile kill earns215 without Ascendance weapon requirement");
        earned.clear(); rules.consume(fifth,TICK,true,alice,earned::add);
        rules.consume(kill(ALICE,"minecraft:zombie",true,OVERWORLD,OTHER,64,false,false,1,true,1),TICK,true,alice,earned::add);
        check(earned.isEmpty() && count(alice,"a215") == 5, "Duplicate and saturated new kills cannot replay Ledger completion");
    }
    private static void desperation() {
        check(awards(kill(ALICE,"minecraft:wither",true,OVERWORLD,OTHER,64,false,true,Math.nextDown(0.1),false,1)).equals(Set.of(184)), "Wither final blow strictly below ten percent while Desperation active");
        check(awards(kill(ALICE,"minecraft:wither",true,OVERWORLD,OTHER,64,false,true,0.1,false,1)).isEmpty(), "Exactly ten percent rejects");
        check(awards(kill(ALICE,"minecraft:wither",true,OVERWORLD,OTHER,64,false,false,0.01,false,1)).isEmpty(), "Low health alone does not prove active Desperation");
        check(awards(kill(ALICE,"minecraft:zombie",true,OVERWORLD,OTHER,64,false,true,0.01,false,1)).isEmpty(), "Other mob cannot substitute Wither final blow");
    }
    private static void persistence() {
        var player = new PlayerEssenceData(); var state = player.achievementProgress();
        for (int i = 0; i < 9; i++) state.barefootUndergroundKill();
        for (int i = 0; i < 4; i++) state.ledgerTrackedKill();
        var restored = PlayerEssenceData.load(player.save()).achievementProgress();
        check(restored.save().equals(state.save()), "Real player save roundtrips both lifetime combat counters");
        check(restored.barefootUndergroundKill() && restored.ledgerTrackedKill(), "Fresh qualifying final kills complete restored partials");
        var saturated = AchievementProgress.load(restored.save());
        check(!saturated.barefootUndergroundKill() && !saturated.ledgerTrackedKill(), "Restart preserves saturation after native revoke");
        check(AchievementProgress.load(new CompoundTag()).empty(), "Legacy absence gives no reconstructed kills");
    }
    private static void malformed() {
        for (String id : new String[]{"a120", "a215"}) {
            var seed = new AchievementProgress(); seed.barefootUndergroundKill(); seed.ledgerTrackedKill();
            var root = seed.save(); var entry = entry(root,id); entry.putInt("version",2); entry.putString("future","preserved");
            var future = AchievementProgress.load(root);
            check(!(id.equals("a120") ? future.barefootUndergroundKill() : future.ledgerTrackedKill()) && future.save().equals(root), "Future counter version retained opaque");
            for (int value : new int[]{-1, id.equals("a120") ? 11 : 6}) {
                root = seed.save(); entry(root,id).putInt("count",value);
                var rejected = AchievementProgress.load(root);
                check(!(id.equals("a120") ? rejected.barefootUndergroundKill() : rejected.ledgerTrackedKill()), "Malformed out-of-range counter fails closed");
                check(rejected.save().equals(root), "Malformed known entry remains preserved");
            }
            root = seed.save(); entry(root,id).putString("count","1");
            var rejected = AchievementProgress.load(root);
            check(!(id.equals("a120") ? rejected.barefootUndergroundKill() : rejected.ledgerTrackedKill()), "Wrong counter tag type rejected locally");
            root = seed.save(); entry(root,id).putString("future_field","keep");
            var extended = AchievementProgress.load(root);
            if(id.equals("a120")) extended.barefootUndergroundKill(); else extended.ledgerTrackedKill();
            check(NbtVersionCompat.getString(entry(extended.save(),id),"future_field").equals("keep"), "Unknown rule fields survive bounded updates");
        }
    }
    private static CompoundTag entry(CompoundTag root, String id) { return NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(root,"entries"),id); }
    private static void invalidFacts() {
        var context = context(ALICE,"minecraft:wither",true,OVERWORLD,STAB);
        for (double loss : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            rejects(() -> new CombatOutcome.Damage(context,10,loss));
        for (double distance : new double[]{-1,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            rejects(() -> new CombatOutcome.Damage(context,distance,1));
        for (double fraction : new double[]{-0.01,Double.NaN,Double.POSITIVE_INFINITY})
            rejects(() -> new CombatOutcome.Kill(context,-1,-1,true,true,fraction,false,1));
        rejects(() -> new CombatOutcome.Kill(context,Double.NaN,-1,true,true,0.05,false,1));
        rejects(() -> new CombatOutcome.Kill(context,-1,Double.NaN,true,true,0.05,false,1));
        rejects(() -> new CombatOutcome.Kill(context,-1,-1,true,true,0.05,false,-1));
        rejects(() -> new CombatOutcome.Context("",ALICE,new UUID(4,4),"minecraft:zombie",true,OVERWORLD,TICK,STAB));
        rejects(() -> new CombatOutcome.Context("a",null,new UUID(4,4),"minecraft:zombie",true,OVERWORLD,TICK,STAB));
        rejects(() -> new CombatOutcome.Context("a",ALICE,null,"minecraft:zombie",true,OVERWORLD,TICK,STAB));
        rejects(() -> new CombatOutcome.Context("a",ALICE,new UUID(4,4),"",true,OVERWORLD,TICK,STAB));
        rejects(() -> new CombatOutcome.Context("a",ALICE,new UUID(4,4),"minecraft:zombie",true,"",TICK,STAB));
        rejects(() -> new CombatOutcome.Context("a",ALICE,new UUID(4,4),"minecraft:zombie",true,OVERWORLD,-1,STAB));
        rejects(() -> new CombatOutcome.Cause("x".repeat(257),ALICE,STAB));
        rejects(() -> new CombatOutcome.Cause("valid",ALICE,null));
        rejects(() -> new CombatOutcome.Weapon("minecraft:stick","",0," "));
        rejects(() -> new CombatOutcome.Weapon("essence_ascendance:ascendance_glaive","essence_ascendance:glaive",-1,"SWIPE"));
        check(CombatOutcome.Weapon.UNKNOWN.profileId().isEmpty(), "Unknown weapon sentinel remains representable");
    }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Malformed combat fact accepted"); }
        catch (IllegalArgumentException | NullPointerException expected) { checks++; }
    }
    private static int count(AchievementProgress state,String id) { return NbtVersionCompat.getInt(entry(state.save(),id),"count"); }
    private static void check(boolean condition,String message) { checks++; if(!condition) throw new AssertionError(message); }
}

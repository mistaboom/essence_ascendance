package com.mistaboom.essence_ascendance.crucible;

import java.util.*;

/** Real shared allocator with transactional test receivers; no world/provider scan or alternate allocator. */
public final class ConcurrentChannelSchedulerTest {
    private static int checks;
    private static final class Receiver implements ConcurrentChannelScheduler.Consumer {
        long stock = Long.MAX_VALUE, received, cap = -1;
        boolean valid = true, blocked, fail;
        public boolean valid() { return valid; }
        public long demand() { return blocked ? 0 : stock; }
        public long ratePerSecond() { return cap; }
        public long deliver(long grant) {
            if (fail) return 0;
            long amount = Math.min(grant, stock); stock -= amount; received += amount; return amount;
        }
    }
    private static ConcurrentChannelScheduler<String, String> scheduler(long rate) {
        var scheduler = new ConcurrentChannelScheduler<String, String>();
        scheduler.source("a", scheduler, () -> rate, () -> true); return scheduler;
    }
    private static Receiver add(ConcurrentChannelScheduler<String, String> scheduler, String source, String name) {
        var receiver = new Receiver(); scheduler.register(source, name, receiver, 0, receiver); return receiver;
    }
    private static void run(ConcurrentChannelScheduler<String, String> scheduler, int start, int ticks) {
        for (int t = start; t < start + ticks; t++) scheduler.tick(t);
    }
    public static void main(String[] args) throws ReflectiveOperationException {
        for (int count : new int[]{2, 3, 4, 5, 7, 64, 257}) {
            var scheduler = scheduler(120); List<Receiver> receivers = new ArrayList<>();
            for (int i = 0; i < count; i++) receivers.add(add(scheduler, "a", "receiver" + i));
            run(scheduler, 1, 20 * count);
            for (var receiver : receivers) check(receiver.received == 120, "Equal share for dynamic count " + count);
            check(receivers.stream().mapToLong(r -> r.received).sum() == 120L * count, "Conserved source bandwidth");
        }
        var scheduler = scheduler(120);
        var capped = add(scheduler, "a", "capped"); capped.cap = 20;
        var a = add(scheduler, "a", "a"); var b = add(scheduler, "a", "b"); run(scheduler, 1, 20);
        check(capped.received == 20 && a.received == 50 && b.received == 50, "20 / 50 / 50 water filling");
        capped.blocked = true; run(scheduler, 21, 20);
        check(a.received == 110 && b.received == 110, "Blocked demand does not reserve bandwidth");
        capped.blocked = false; capped.fail = true; run(scheduler, 41, 20);
        check(a.received == 170 && b.received == 170, "Failed commit redistributes grant");
        scheduler = scheduler(5); var rejected = add(scheduler,"a","rejected");rejected.fail=true;
        var fallback = add(scheduler,"a","fallback");run(scheduler,1,20);
        check(rejected.received==0 && fallback.received==5,"A failed one-unit grant redistributes to initially zero-granted usable receivers");
        scheduler = scheduler(1); var tiny = new ArrayList<Receiver>();
        for (int i = 0; i < 7; i++) tiny.add(add(scheduler, "a", "tiny" + i));
        run(scheduler, 1, 140); for (var r : tiny) check(r.received == 1, "Low rate rotates exact remainders");
        scheduler = scheduler(1); var five = new ArrayList<Receiver>();
        for (int i=0;i<5;i++) five.add(add(scheduler,"a","five"+i));
        run(scheduler,1,100); for(var r:five) check(r.received==1,"No batching-period/receiver-count alias starvation");
        scheduler = scheduler(7); a = add(scheduler, "a", "one");
        var original = scheduler.sessions().getFirst();
        for (int tick = 1; tick <= 200; tick++) {
            check(scheduler.register("a", "one", a, 0, a).equals(original), "Duplicate Start keeps current generation");
            scheduler.tick(tick); scheduler.tick(tick);
        }
        check(a.received == 70, "Duplicate registration/tick cannot multiply throughput");
        scheduler.remove(original); run(scheduler, 201, 200);
        var restarted = scheduler.register("a", "one", a, 1, a);
        scheduler.remove(original); check(scheduler.current(restarted), "Stale Stop cannot remove replacement generation");
        run(scheduler, 401, 20); check(a.received == 77, "No unlimited idle credits");
        scheduler.source("b", b, () -> 7, () -> true);
        var other = scheduler.register("b", "one", a, 1, a); run(scheduler, 421, 20);
        check(a.received == 91, "Multiple source links add physical throughput");
        scheduler.remove(restarted); run(scheduler, 441, 20);
        check(a.received == 98 && scheduler.current(other), "Stop is link specific");
        scheduler = scheduler(120); scheduler.source("b", b, () -> 120, () -> true);
        a = add(scheduler, "a", "shared"); a.cap = 20;
        scheduler.register("b", "shared", a, 0, a); run(scheduler, 1, 20);
        check(a.received == 20, "Receiver processing cap applies across all sources/grants");
        scheduler = scheduler(120); var sharedStock = new long[]{0}; var received = new long[2];
        scheduler.source("b", b, () -> 120, () -> true);
        for (int i = 0; i < 2; i++) {
            final int index = i;
            var receiver = new ConcurrentChannelScheduler.Consumer() {
                public boolean valid() { return true; }
                public long demand() { return sharedStock[0]; }
                public long ratePerSecond() { return -1; }
                public long deliver(long grant) { long amount = Math.min(grant, sharedStock[0]); sharedStock[0] -= amount; received[index] += amount; return amount; }
            };
            scheduler.register(i == 0 ? "a" : "b", "scarce" + i, receiver, 0, receiver);
        }
        for (int tick = 1; tick <= 400; tick++) {
            if (tick % 4 == 0) sharedStock[0] = 1;
            scheduler.tick(tick);
        }
        check(received[0] == 50 && received[1] == 50 && sharedStock[0] == 0, "Scarce communal stock rotates stations without double spend");
        scheduler = scheduler(120); var lowStock = new long[]{1}; var lowReceived = new long[5];
        for (int i=0;i<5;i++) {
            final int index=i; String station="station"+i;
            scheduler.source(station,station,()->120,()->true);
            var receiver=new ConcurrentChannelScheduler.Consumer() {
                public boolean valid() { return true; }
                public long demand() { return lowStock[0]; }
                public long ratePerSecond() { return 1; }
                public long deliver(long grant) { long amount=Math.min(grant,lowStock[0]);lowStock[0]-=amount;lowReceived[index]+=amount;return amount; }
            };
            scheduler.register(station,station,receiver,0,receiver);
        }
        for(int tick=1;tick<=100;tick++){if(tick%4==0)lowStock[0]=1;scheduler.tick(tick);}
        for(long receivedAmount:lowReceived)check(receivedAmount==1,"Low cap release periods cannot pin scarce stock to the first station");
        scheduler = scheduler(Long.MAX_VALUE); a = add(scheduler, "a", "large");
        run(scheduler, 1, 20); check(a.received == Long.MAX_VALUE, "Huge rates remain exact without overflow");
        scheduler = scheduler(120); a = add(scheduler, "a", "a");
        original = scheduler.sessions().getFirst();
        scheduler.source("a", new Object(), () -> 120, () -> true);
        check(!scheduler.current(original) && scheduler.sessions().isEmpty(), "Source replacement invalidates grants");
        var replacement = scheduler.register("a", "a", a, 2, a); scheduler.remove(original);
        check(scheduler.current(replacement), "Source generation protects replacement channel");
        var oldIdentity = a; var newIdentity = new Receiver();
        var fresh = scheduler.register("a","a",newIdentity,2,newIdentity);
        scheduler.removeReceiver("a",oldIdentity);
        check(scheduler.current(fresh),"An old receiver's logout/unload cannot remove the replacement object");
        a = newIdentity;
        a.valid = false; scheduler.tick(1); check(scheduler.sessions().isEmpty(), "Unauthorized/dead/unloaded receiver cleanup");
        scheduler.clear(); check(scheduler.sessions().isEmpty(), "Server restart retains no runtime sessions");
        scheduler = scheduler(120); a = add(scheduler, "a", "player"); b = add(scheduler, "a", "infuser");
        run(scheduler, 1, 4); var playerSession = scheduler.sessions().getFirst(); var feedback = scheduler.feedback(playerSession);
        check(feedback.allocated() == 12 && feedback.transferred() == 12 && feedback.consuming(), "Feedback reports real four-tick allocation and commit independently");
        a.blocked = true;
        check(!scheduler.feedback(playerSession).consuming(), "A None/unavailable-type registration does not claim consuming capacity");
        run(scheduler, 5, 4); check(scheduler.feedback(playerSession).allocated() == 0 && scheduler.feedback(playerSession).transferred() == 0, "Waiting presentation clears old batch throughput");
        a.blocked = false; a.fail = true; run(scheduler, 9, 4); feedback = scheduler.feedback(playerSession);
        check(feedback.allocated() > 0 && feedback.transferred() == 0, "Rejected allocation is not presented as actual transfer");
        exclusivePlayerSource();
        receiverBudgetLifecycle();
        // Independent conservation/limit oracle over random demands; catches arithmetic and redistribution errors.
        Random random = new Random(6);
        for (int test = 0; test < 1000; test++) {
            long[] demands = new long[1 + random.nextInt(80)]; long total = 0;
            for (int i = 0; i < demands.length; i++) { demands[i] = random.nextInt(500); total += demands[i]; }
            long budget = random.nextInt(5000); long[] grants = ConcurrentChannelScheduler.allocate(budget, demands, random.nextInt(demands.length));
            check(Arrays.stream(grants).sum() == Math.min(budget, total), "Exact usable conservation");
            for (int i = 0; i < demands.length; i++) check(grants[i] >= 0 && grants[i] <= demands[i], "Demand bound");
        }
        System.out.println("ConcurrentChannelSchedulerTest: " + checks + " assertions passed");
    }
    private static void exclusivePlayerSource() {
        var scheduler = scheduler(120);
        var player = new Receiver();
        var first = scheduler.registerExclusive("a", "player", player, 1, player);
        check(first != null && scheduler.registerExclusive("a", "player", player, 1, player).equals(first),
                "Exclusive duplicate Start retains the current registration generation");
        for (int i = 0; i < 20; i++) {
            String station = "dimension" + (i % 2) + ":station" + i;
            scheduler.source(station, station, () -> 120, () -> true);
            scheduler.registerExclusive(station, "player", player, 1, player);
            check(scheduler.sessions().size() == 1, "One player cannot accumulate sources across dimensions");
        }
        run(scheduler, 1, 20);
        check(player.received == 120, "Twenty source Starts still give one physical-source budget");
        var current = scheduler.sessions().getFirst();
        var invalid = new Receiver(); invalid.valid = false;
        check(scheduler.registerExclusive("a", "player", invalid, 2, invalid) == null && scheduler.current(current),
                "An invalid destination receiver cannot replace a valid active source");
        scheduler.source("unloaded", scheduler, () -> 120, () -> false);
        check(scheduler.registerExclusive("unloaded", "player", player, 1, player) == null && scheduler.current(current),
                "An unavailable destination source cannot replace the active source");
        scheduler.remove(first);
        check(scheduler.current(current), "A stale Stop from the replaced source cannot cancel the current source");
        for (int tick = 21; tick <= 100; tick++) {
            scheduler.registerExclusive("dimension" + (tick % 2) + ":station" + (tick % 20), "player", player, 1, player);
            scheduler.tick(tick);
        }
        check(player.received == 600 && scheduler.sessions().size() == 1,
                "Switching source every tick cannot collect more than one source's interval budget");
        var teammate = new Receiver();
        var infuser = new Receiver();
        scheduler.registerExclusive("a", "player", player, 1, player);
        scheduler.registerExclusive("a", "teammate", teammate, 1, teammate);
        scheduler.register("a", "infuser", infuser, 1, infuser);
        long before = player.received;
        run(scheduler, 101, 20);
        check(player.received - before == 40 && teammate.received == 40 && infuser.received == 40,
                "Exclusive player sources preserve sharing with other players and Infusers");
        var replacementPlayer = new Receiver();
        var replacement = scheduler.registerExclusive("dimension1:station1", "player", replacementPlayer, 1, replacementPlayer);
        scheduler.removeReceiver("player", player);
        check(scheduler.current(replacement) && scheduler.sessions().size() == 3,
                "A replaced player object and stale logout cannot leave duplicates or remove the new connection");
    }
    private static void receiverBudgetLifecycle() throws ReflectiveOperationException {
        var scheduler = scheduler(120);
        var live = add(scheduler, "a", "live"); live.cap = 1;
        var budgetsField = ConcurrentChannelScheduler.class.getDeclaredField("receivers");
        budgetsField.setAccessible(true);
        for (int tick = 1; tick <= 100; tick++) {
            var departed = add(scheduler, "a", "departed" + tick); departed.cap = 20;
            var session = scheduler.sessions().getLast();
            if (tick % 3 == 0) departed.valid = false;
            else scheduler.removeReceiver(session.key().receiver(), departed);
            scheduler.tick(tick);
        }
        check(((Map<?, ?>) budgetsField.get(scheduler)).size() == 1,
                "Removed and invalid consumers release their receiver budget state");
        check(live.received == 5, "Cleanup preserves an active receiver's fractional cap carry");
        live.cap = -1; run(scheduler, 101, 4);
        check(((Map<?, ?>) budgetsField.get(scheduler)).isEmpty(),
                "Uncapped consumers retain no unused receiver budget");
        long before = live.received;
        live.cap = 20; run(scheduler, 105, 20);
        check(live.received - before == 20, "A live receiver can gain a cap after uncapped ticks");
        scheduler.removeSource("a", scheduler); scheduler.tick(125);
        check(((Map<?, ?>) budgetsField.get(scheduler)).isEmpty(),
                "Removing a source releases its last receiver's budget");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}

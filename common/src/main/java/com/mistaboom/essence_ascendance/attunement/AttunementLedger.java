package com.mistaboom.essence_ascendance.attunement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.*;

/** Exact bounded normalized accounting. Regeneration changes future rates, never earned seal fractions. */
public final class AttunementLedger {
    public static final long SCALE = 1_000_000_000L;
    public static final int MAX_HISTORY = 256;
    private String chapterId = "";
    private final Map<String, Long> progress = new LinkedHashMap<>();
    private final Map<String, Long> methods = new LinkedHashMap<>();
    private final Map<String, AttunementSourceHistory> history = new LinkedHashMap<>();
    private final LinkedHashMap<String, Applied> roots = new LinkedHashMap<>();
    private final Map<String, ArrayDeque<AttunementContribution>> recent = new LinkedHashMap<>();
    private final BitSet discoveries = new BitSet(8192);
    private long rejectedHealingMicros;
    private long revision;
    private record Applied(String method, long credit, long accounted, double work, double fresh, double other) {}

    public String chapterId() { return chapterId; }
    public long revision() { return revision; }
    public long progress(String category) { return progress.getOrDefault(category, 0L); }
    public long methodProgress(String method) { return methods.getOrDefault(method, 0L); }
    public List<AttunementContribution> recent() { return recent.values().stream().flatMap(Collection::stream).toList(); }
    public List<AttunementContribution> recent(String category) { return List.copyOf(recent.getOrDefault(category, new ArrayDeque<>())); }
    public long rejectedHealingMicros() { return rejectedHealingMicros; }
    public void setRejectedHealingMicros(long value) { rejectedHealingMicros = Math.max(0, value); }
    public void chapter(String id) {
        if (chapterId.equals(id)) return;
        chapterId = id; progress.clear(); methods.clear(); history.clear(); roots.clear(); recent.clear(); discoveries.clear(); revision++;
    }
    /** Explicit operator override. Method credit and repetition remain truthful for the selected seal. */
    public void setProgressForAdmin(String category, long amount) {
        if (amount < 0 || amount > SCALE) throw new IllegalArgumentException("Attunement progress must fit one seal");
        if (amount == 0) progress.remove(category); else progress.put(category, amount);
        methods.keySet().removeIf(id -> {
            var activity = AttunementActivityRegistry.get(id);
            return activity != null && activity.categoryId().equals(category);
        });
        history.remove(category); recent.remove(category);
        roots.keySet().removeIf(id -> id.endsWith("|" + category));
        if (AttunementActivityRegistry.values().stream().anyMatch(a -> a.categoryId().equals(category)
                && a.calibrationFamily().equals("exploration"))) discoveries.clear();
        var healing = AttunementActivityRegistry.get("heal_health");
        if (healing != null && healing.categoryId().equals(category)) rejectedHealingMicros = 0;
        revision++;
    }
    /** Discoveries reset each chapter; a fixed Bloom filter bounds saves even in dimension-heavy packs. */
    public boolean discover(String signature) {
        int a = Math.floorMod(signature.hashCode(), 8192);
        int b = Math.floorMod((signature + ":chapter").hashCode(), 8192);
        int c = Math.floorMod(Integer.rotateLeft(signature.hashCode(), 13), 8192);
        boolean known = discoveries.get(a) && discoveries.get(b) && discoveries.get(c);
        discoveries.set(a); discoveries.set(b); discoveries.set(c);
        return !known;
    }
    public static double investmentMultiplier(long invested, long reference, double maximum) {
        if (invested < 0 || reference <= 0 || !Double.isFinite(maximum) || maximum < 0)
            throw new IllegalArgumentException("Invalid generated investment reference");
        return 1 + maximum * Math.sqrt(Math.min(1, (double) invested / reference));
    }
    public AttunementContribution contribute(String root, AttunementEvent.Outcome outcome,
            AttunementProfile.Chapter chapter, AttunementProfile.Policy policy, long invested) {
        AttunementActivity activity = AttunementActivityRegistry.get(outcome.activityId());
        AttunementProfile.Rate rate = chapter.activities().get(outcome.activityId());
        String category = activity == null ? "" : activity.categoryId();
        AttunementProfile.Category target = chapter.categories().get(category);
        String rejection = !outcome.eligible() ? (outcome.rejectionReason().isBlank() ? "rejected" : outcome.rejectionReason()) : "";
        if (activity == null || rate == null || target == null) rejection = "unavailable";
        if (!Double.isFinite(outcome.units()) || outcome.units() <= 0) {
            if (rejection.isEmpty()) rejection = "no_real_change";
        }
        if (!rejection.isEmpty()) return remember(new AttunementContribution(root, outcome.activityId(), category,
                outcome.sourceSignature(), 0, 1, 1, 1, 0, rejection));
        double investment = investmentMultiplier(invested, target.investmentReference(), policy.maximumAcceleration());
        String rootKey = root + "|" + category;
        Applied previous = roots.get(rootKey);
        var sources = history.computeIfAbsent(category, key -> new AttunementSourceHistory());
        int window = Math.max(1, Math.min(MAX_HISTORY, policy.historyWindow()));
        double work = Math.min(Double.MAX_VALUE, outcome.units() / rate.referenceUnits());
        double fresh = previous == null ? sources.freshFraction(outcome.sourceSignature(), window) : previous.fresh();
        double repetition = AttunementSourceHistory.averageEfficiency(fresh, work, window, policy.repetitionFloor());
        double other = previous == null ? sources.otherFraction(outcome.sourceSignature(), window) : previous.other();
        double variety = AttunementSourceHistory.averageVariety(fresh, other, work, window, policy.repetitionFloor(), policy.varietyStrength());
        // Saturate before conversion: modded extremes never overflow integer progress.
        double base = Math.min(Double.MAX_VALUE, outcome.units() * rate.contributionPerUnit());
        double normalized = base / target.target() * SCALE;
        // One billionth is the accounting quantum. Even microscopic legitimate modded outcomes
        // remain productive. Legitimate large outcomes retain their full value; only the seal's
        // remaining normalized progress bounds accepted credit.
        long candidate = Math.max(1L, (long) Math.floor(Math.min(SCALE, normalized * investment * repetition * variety)));
        long old = previous == null ? 0 : previous.credit();
        long delta = Math.max(0, candidate - old);
        long accepted = Math.min(delta, SCALE - progress(category));
        if (candidate > old) {
            progress.put(category, progress(category) + accepted);
            if (previous != null && !previous.method().equals(outcome.activityId())) {
                long transferred = previous.accounted();
                methods.put(previous.method(), methodProgress(previous.method()) - transferred);
                methods.put(outcome.activityId(), Math.min(SCALE, methodProgress(outcome.activityId()) + transferred));
            }
            methods.put(outcome.activityId(), Math.min(SCALE, methodProgress(outcome.activityId()) + accepted));
            roots.put(rootKey, new Applied(outcome.activityId(), candidate, (previous == null ? 0 : previous.accounted()) + accepted, work, fresh, other));
        } else if (previous == null) roots.put(rootKey, new Applied(outcome.activityId(), candidate, accepted, work, fresh, other));
        if (previous == null || work > previous.work()) {
            sources.add(outcome.sourceSignature(), work - (previous == null ? 0 : previous.work()), window);
        }
        while (roots.size() > MAX_HISTORY * 4) roots.remove(roots.keySet().iterator().next());
        String reason = previous != null && delta == 0 ? "duplicate_action"
                : accepted == 0 && progress(category) >= SCALE ? "seal_complete"
                : accepted == 0 ? "below_precision" : "";
        return remember(new AttunementContribution(root, outcome.activityId(), category, outcome.sourceSignature(),
                base, investment, repetition, variety, accepted, reason));
    }
    private AttunementContribution remember(AttunementContribution contribution) {
        var categoryRecent = recent.computeIfAbsent(contribution.categoryId(), key -> new ArrayDeque<>());
        categoryRecent.addFirst(contribution); while (categoryRecent.size() > 32) categoryRecent.removeLast(); revision++;
        return contribution;
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putString("chapter", chapterId); tag.putLong("revision", revision);
        tag.putLong("rejected_healing_micros", rejectedHealingMicros); tag.putLongArray("discoveries", discoveries.toLongArray());
        CompoundTag totals = new CompoundTag(); progress.forEach(totals::putLong); tag.put("progress", totals);
        CompoundTag methodTags = new CompoundTag(); methods.forEach(methodTags::putLong); tag.put("methods", methodTags);
        CompoundTag histories = new CompoundTag();
        history.forEach((category, sources) -> histories.put(category, sources.save()));
        tag.put("history", histories);
        ListTag actions = new ListTag();
        roots.forEach((key, value) -> { CompoundTag a = new CompoundTag(); a.putString("id", key); a.putString("method", value.method()); a.putLong("credit", value.credit()); a.putLong("accounted", value.accounted()); a.putDouble("work", value.work()); a.putDouble("fresh", value.fresh()); a.putDouble("other", value.other()); actions.add(a); });
        tag.put("roots", actions);
        CompoundTag audit = new CompoundTag();
        recent.forEach((category, rows) -> {
            ListTag list = new ListTag();
            for (var row : rows) {
                CompoundTag a = new CompoundTag(); a.putString("action", row.actionId()); a.putString("method", row.activityId());
                a.putString("source", row.sourceSignature()); a.putDouble("base", row.baseValue()); a.putDouble("investment", row.investmentMultiplier());
                a.putDouble("repetition", row.repetitionMultiplier()); a.putDouble("variety", row.varietyMultiplier());
                a.putLong("gain", row.finalContribution()); a.putString("reason", row.rejectionReason()); list.add(a);
            }
            audit.put(category, list);
        });
        tag.put("recent", audit); return tag;
    }
    public static AttunementLedger load(CompoundTag tag) {
        AttunementLedger ledger = new AttunementLedger(); ledger.chapterId = tag.getString("chapter");
        ledger.revision = Math.max(0, tag.getLong("revision")); ledger.rejectedHealingMicros = Math.max(0, tag.getLong("rejected_healing_micros"));
        long[] bits = tag.getLongArray("discoveries"); ledger.discoveries.or(BitSet.valueOf(Arrays.copyOf(bits, Math.min(bits.length, 128))));
        for (String kind : List.of("progress", "methods")) {
            CompoundTag totals = tag.getCompound(kind); Map<String, Long> map = kind.equals("progress") ? ledger.progress : ledger.methods;
            for (String key : totals.getAllKeys().stream().sorted().limit(512).toList()) map.put(key, Math.clamp(totals.getLong(key), 0, SCALE));
        }
        CompoundTag histories = tag.getCompound("history");
        for (String category : histories.getAllKeys().stream().sorted().limit(128).toList()) {
            ledger.history.put(category, AttunementSourceHistory.load(histories.getList(category, Tag.TAG_COMPOUND)));
        }
        ListTag actions = tag.getList("roots", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, actions.size()-MAX_HISTORY*4); i < actions.size(); i++) {
            CompoundTag a = actions.getCompound(i); double f = a.getDouble("fresh"), v = a.getDouble("other"), w = a.getDouble("work");
            if (a.getString("id").length() <= 512 && Double.isFinite(f) && f >= 0 && f <= 1 && Double.isFinite(w) && w > 0 && Double.isFinite(v) && v >= 0 && v <= 1)
                ledger.roots.put(a.getString("id"), new Applied(a.getString("method"), Math.clamp(a.getLong("credit"), 0, SCALE), Math.clamp(a.getLong("accounted"), 0, Math.clamp(a.getLong("credit"), 0, SCALE)), w, f, v));
        }
        CompoundTag audit = tag.getCompound("recent");
        for (String category : audit.getAllKeys().stream().sorted().limit(128).toList()) {
            ListTag list = audit.getList(category, Tag.TAG_COMPOUND); var queue = new ArrayDeque<AttunementContribution>();
            for (int i = 0; i < Math.min(list.size(), 32); i++) {
                CompoundTag a = list.getCompound(i);
                try { queue.add(new AttunementContribution(a.getString("action"), a.getString("method"), category, a.getString("source"),
                        a.getDouble("base"), a.getDouble("investment"), a.getDouble("repetition"), a.getDouble("variety"), a.getLong("gain"), a.getString("reason"))); }
                catch (IllegalArgumentException invalid) { /* Ignore malformed diagnostic rows, never earned progress. */ }
            }
            ledger.recent.put(category, queue);
        }
        return ledger;
    }
}

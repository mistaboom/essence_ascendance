package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;
import java.util.HashSet;

/** Bounded partial facts and reconciliation checkpoints, never a second completion authority. */
public final class AchievementProgress {
    private static final Set<String> OBSERVATIONS = Set.of("a061", "a072", "a139", "a166");
    private final CompoundTag root;
    private final Set<String> rejected = new HashSet<>();
    private final boolean writable;

    public AchievementProgress() { this(new CompoundTag()); }
    private AchievementProgress(CompoundTag original) {
        root = original.copy();
        if (root.isEmpty()) { root.putInt("version", 1); root.put("entries", new CompoundTag()); }
        writable = NbtVersionCompat.contains(root, "version", Tag.TAG_INT)
                && NbtVersionCompat.getInt(root, "version") == 1
                && NbtVersionCompat.contains(root, "entries", Tag.TAG_COMPOUND);
        if (!writable) return; // Future/unknown envelopes round-trip opaque.
        var entries = entries();
        for (String id : entries.getAllKeys()) {
            if (!known(id)) continue;
            if (!(entries.get(id) instanceof CompoundTag entry)) { reject(id); continue; }
            if (!NbtVersionCompat.contains(entry, "version", Tag.TAG_INT)) { reject(id); continue; }
            if (NbtVersionCompat.getInt(entry, "version") != 1) continue;
            boolean valid = switch (id) {
                case "a030" -> validCollection(entry, "categories", 6, 3) && text(entry, "chapter", 128);
                case "a208" -> validIds(NbtVersionCompat.getCompound(entry, "skills"), 5)
                        && NbtVersionCompat.contains(entry, "skills", Tag.TAG_COMPOUND);
                case "a031" -> NbtVersionCompat.contains(entry, "tier", Tag.TAG_INT)
                        && NbtVersionCompat.getInt(entry, "tier") >= 0 && NbtVersionCompat.getInt(entry, "tier") <= 5;
                case "a051" -> validMask(entry, 63);
                case "a197" -> validMask(entry, 3);
                case "a182" -> validMask(entry, 7);
                case "a119" -> validCat(entry);
                case "a128" -> NbtVersionCompat.contains(entry, "crops", Tag.TAG_COMPOUND)
                        && validRegistryCollection(NbtVersionCompat.getCompound(entry, "crops"), 5);
                case "a120" -> validCounter(entry, 10);
                case "a215" -> validCounter(entry, 5);
                case "a177" -> validCounter(entry, 2);
                case "a193" -> NbtVersionCompat.contains(entry, "active", Tag.TAG_BYTE);
                case "a209" -> NbtVersionCompat.contains(entry, "foods", Tag.TAG_COMPOUND)
                        && validFoodIds(NbtVersionCompat.getCompound(entry, "foods"));
                case "a199" -> NbtVersionCompat.contains(entry, "items", Tag.TAG_COMPOUND)
                        && validMachineItems(NbtVersionCompat.getCompound(entry, "items"));
                case "a056" -> validMachineGroups(entry, true);
                case "a190" -> NbtVersionCompat.contains(entry, "day_time", Tag.TAG_LONG) && validMachineGroups(entry, false);
                default -> NbtVersionCompat.contains(entry, "matching", Tag.TAG_BYTE);
            };
            if (!valid) reject(id);
        }
    }
    private void reject(String id) {
        rejected.add(id);
        EssenceAscendance.LOGGER.warn("Rejecting malformed achievement partial entry {}; preserving other player data", id);
    }
    private static boolean known(String id) { return OBSERVATIONS.contains(id) || Set.of("a030", "a031", "a208", "a051", "a056", "a190", "a197", "a199", "a120", "a215", "a177", "a193", "a209", "a119", "a128", "a182").contains(id); }
    private CompoundTag entries() { return NbtVersionCompat.getCompound(root, "entries"); }
    private CompoundTag entry(String id) {
        if (!writable || rejected.contains(id)) return null;
        var entries = entries();
        if (!entries.contains(id)) {
            CompoundTag entry = new CompoundTag(); entry.putInt("version", 1); entries.put(id, entry); return entry;
        }
        var entry = NbtVersionCompat.getCompound(entries, id);
        return NbtVersionCompat.getInt(entry, "version") == 1 ? entry : null;
    }
    /** Returns previous observed tier; -1 means no safe reconciliation is possible. */
    public boolean tierObserved() {
        if (!writable || rejected.contains("a031")) return true;
        if (!entries().contains("a031")) return false;
        var entry = NbtVersionCompat.getCompound(entries(), "a031");
        return NbtVersionCompat.getInt(entry, "version") != 1 || entry.contains("tier");
    }
    public int observeTier(int tier) {
        if (tier < 0 || tier > 5) return -1;
        var entry = entry("a031"); if (entry == null) return -1;
        int previous = entry.contains("tier") ? NbtVersionCompat.getInt(entry, "tier") : 0;
        entry.putInt("tier", tier); return previous;
    }
    public boolean observeEquipment(String id, boolean matching, boolean eligible) {
        if (!OBSERVATIONS.contains(id)) throw new IllegalArgumentException("Not an equipment checkpoint");
        var entry = entry(id); if (entry == null) return false;
        boolean previous = NbtVersionCompat.getBoolean(entry, "matching");
        entry.putBoolean("matching", matching);
        return eligible && matching && !previous;
    }
    public void chapter(String chapter) {
        if (chapter == null || chapter.length() > 128) return;
        var entry = entry("a030"); if (entry == null) return;
        if (!entry.contains("chapter") || !chapter.equals(NbtVersionCompat.getString(entry, "chapter"))) {
            entry.putString("chapter", chapter); entry.put("categories", new CompoundTag());
        }
    }
    /** Only three distinct positive methods per category are needed; six categories maximum. */
    public boolean activity(String chapter, String category, String method) {
        if (chapter == null || chapter.isBlank() || chapter.length() > 128 || !identifier(category) || !identifier(method)) return false;
        chapter(chapter); var entry = entry("a030"); if (entry == null) return false;
        var categories = NbtVersionCompat.getCompound(entry, "categories");
        if (!categories.contains(category) && categories.size() >= 6) return false;
        var methods = NbtVersionCompat.getCompound(categories, category);
        if (methods.contains(method) || methods.size() >= 3) return false;
        methods.putBoolean(method, true); categories.put(category, methods); entry.put("categories", categories);
        return methods.size() == 3;
    }
    /** Lifetime distinct receipts survive refunds/respecs. Current skill ownership is never backfill. */
    public boolean utilityPurchase(String skill) {
        if (!identifier(skill)) return false;
        return collectDistinct("a208", "skills", skill, 5);
    }
    /** Same-life confirmed Second Wind procs; prevention itself never advances a life boundary. */
    public boolean secondWind() { return incrementCounter("a177", 2); }
    /** Own Damage Ceiling debt only. Attribute growth cannot complete an armed debt episode. */
    public boolean ceilingDebt(double before, double after, boolean capacityReduced) {
        if (!Double.isFinite(before) || !Double.isFinite(after) || before < 0 || after < 0) return false;
        var entry = entry("a193"); if (entry == null) return false;
        boolean active = NbtVersionCompat.getBoolean(entry, "active");
        boolean recovered = active && before > 0 && after == 0;
        if (after > before && capacityReduced) active = true;
        if (after == 0) active = false;
        entry.putBoolean("active", active);
        return recovered;
    }
    /** Ineligible own-debt mutations invalidate prior survival evidence without creating a fresh episode. */
    public boolean disarmCeilingDebt() {
        if (!writable || !entries().contains("a193")) return false;
        var entry = entry("a193");
        if (entry == null || !NbtVersionCompat.getBoolean(entry, "active")) return false;
        entry.putBoolean("active", false); return true;
    }
    /** Five distinct foods with confirmed positive equipment repair; callers supply location eligibility. */
    public boolean repairedFood(String food) {
        if (!machineItemId(food)) return false;
        return collectDistinct("a209", "foods", food, 5);
    }
    /** Actual skill-caused growth only; no passive proximity, maturity or crop-owner history. */
    public boolean verdantCrop(String crop) {
        if (!machineItemId(crop)) return false;
        return collectDistinct("a128", "crops", crop, 5);
    }
    /** Real delivered bonus receipts, never inferred from dimension visits or current inventory. */
    public boolean naturalBonusDimension(String dimension) {
        if (dimension == null) return false;
        return collectMask("a182", switch (dimension) {
            case "minecraft:overworld" -> 1;
            case "minecraft:the_nether" -> 2;
            case "minecraft:the_end" -> 4;
            default -> 0;
        }, 7);
    }
    /** Identity bound by the first qualifying kill; read-only and safe for unknown future entries. */
    public java.util.UUID selectedCat() {
        if (!writable || rejected.contains("a119") || !entries().contains("a119")) return null;
        var entry = NbtVersionCompat.getCompound(entries(), "a119");
        if (NbtVersionCompat.getInt(entry, "version") != 1 || !entry.contains("cat")) return null;
        return java.util.UUID.fromString(NbtVersionCompat.getString(entry, "cat"));
    }
    public boolean catKill(java.util.UUID cat) {
        if (cat == null) return false;
        var entry = entry("a119"); if (entry == null) return false;
        if (entry.contains("cat") && !cat.toString().equals(NbtVersionCompat.getString(entry, "cat"))) return false;
        int previous = NbtVersionCompat.getInt(entry, "count");
        if (previous >= 5) return false;
        entry.putString("cat", cat.toString()); entry.putInt("count", previous + 1);
        return previous + 1 == 5;
    }
    /** Damage, death or lost ownership ends this collection, including between encounters/offline. */
    public boolean invalidateCat(java.util.UUID cat) {
        if (cat == null || !cat.equals(selectedCat())) return false;
        var entry = entry("a119");
        entry.remove("cat"); entry.putInt("count", 0); return true;
    }
    private boolean collectDistinct(String id, String field, String value, int cap) {
        var entry = entry(id); if (entry == null) return false;
        var values = NbtVersionCompat.getCompound(entry, field);
        if (values.contains(value) || values.size() >= cap) return false;
        values.putBoolean(value, true); entry.put(field, values); return values.size() == cap;
    }
    /** Actual committed native death alone resets durable same-life partials; no new empty entries. */
    public boolean realDeath() {
        if (!writable) return false;
        boolean changed = false;
        if (entries().contains("a177")) {
            var entry = entry("a177");
            if (entry != null && NbtVersionCompat.getInt(entry, "count") != 0) {
                entry.putInt("count", 0); changed = true;
            }
        }
        if (entries().contains("a193")) {
            var entry = entry("a193");
            if (entry != null && NbtVersionCompat.getBoolean(entry, "active")) {
                entry.putBoolean("active", false); changed = true;
            }
        }
        return changed;
    }
    private static boolean validFoodIds(CompoundTag foods) {
        return validRegistryCollection(foods, 5);
    }
    private static boolean validRegistryCollection(CompoundTag values, int cap) {
        return values.size() <= cap && values.getAllKeys().stream().allMatch(id -> machineItemId(id)
                && NbtVersionCompat.contains(values, id, Tag.TAG_BYTE) && NbtVersionCompat.getBoolean(values, id));
    }
    private static boolean validCat(CompoundTag entry) {
        if (!validCounter(entry, 5)) return false;
        return entry.contains("cat")
                ? NbtVersionCompat.contains(entry, "cat", Tag.TAG_STRING) && uuid(NbtVersionCompat.getString(entry, "cat"))
                : NbtVersionCompat.getInt(entry, "count") == 0;
    }
    /** Lifetime confirmed kills only; saturated counters cannot replay a revoked completion. */
    public boolean barefootUndergroundKill() { return incrementCounter("a120", 10); }
    public boolean ledgerTrackedKill() { return incrementCounter("a215", 5); }
    private boolean incrementCounter(String id, int maximum) {
        var entry = entry(id); if (entry == null) return false;
        int previous = NbtVersionCompat.getInt(entry, "count");
        if (previous >= maximum) return false;
        entry.putInt("count", previous + 1); return previous + 1 == maximum;
    }
    private static boolean validCounter(CompoundTag entry, int maximum) {
        return NbtVersionCompat.contains(entry, "count", Tag.TAG_INT)
                && NbtVersionCompat.getInt(entry, "count") >= 0 && NbtVersionCompat.getInt(entry, "count") <= maximum;
    }
    /** Six category bits in the canonical EssenceTypes.ORDERED order. */
    public boolean machineCategories(int mask) { return collectMask("a051", mask, 63); }
    public boolean machineDimension(String dimension) {
        return collectMask("a197", switch (dimension) { case "minecraft:overworld" -> 1; case "minecraft:the_nether" -> 2; default -> 0; }, 3);
    }
    private boolean collectMask(String id, int addition, int target) {
        if (addition <= 0 || (addition & ~target) != 0) return false;
        var entry = entry(id); if (entry == null) return false;
        int old = NbtVersionCompat.getInt(entry, "mask"), next = old | addition;
        entry.putInt("mask", next); return old != next && next == target;
    }
    /** Actual consumed item registry identities only, capped at the challenge threshold. */
    public boolean dissolvedItems(Set<String> additions) {
        if (additions == null || additions.isEmpty() || additions.size() > 64 || !additions.stream().allMatch(AchievementProgress::machineItemId)) return false;
        var entry = entry("a199"); if (entry == null) return false;
        var items = NbtVersionCompat.getCompound(entry, "items"); int old = items.size();
        additions.stream().sorted().forEach(item -> { if (items.size() < 42) items.putBoolean(item, true); });
        entry.put("items", items); return old < 42 && items.size() == 42;
    }
    /** At most 64 recently used physical sources, each retaining at most three physical Infusers. */
    public boolean infuserSource(java.util.UUID source, java.util.UUID infuser) {
        if (source == null || infuser == null) return false;
        var entry = entry("a056"); if (entry == null) return false;
        var groups = NbtVersionCompat.getCompound(entry, "groups");
        var group = machineGroup(entry, groups, source);
        var items = NbtVersionCompat.getCompound(group, "infusers");
        boolean added = !items.contains(infuser.toString()) && items.size() < 3;
        if (added) items.putBoolean(infuser.toString(), true);
        group.put("infusers", items); groups.put(source.toString(), group); entry.put("groups", groups);
        return added && items.size() == 3;
    }
    /** Calendar bucket, shared overworld clock. Rewinds invalidate every active machine bucket. */
    public boolean dissolvedDay(java.util.UUID machine, long dayTime, int mask) {
        if (machine == null || mask <= 0 || mask > 63) return false;
        var entry = entry("a190"); if (entry == null) return false;
        if (!entry.contains("day_time") || !com.mistaboom.essence_ascendance.gameplay.OutcomePolicies.sameMinecraftDay(
                NbtVersionCompat.getLong(entry, "day_time"), dayTime)) entry.put("groups", new CompoundTag());
        entry.putLong("day_time", dayTime);
        var groups = NbtVersionCompat.getCompound(entry, "groups"); var group = machineGroup(entry, groups, machine);
        int old = NbtVersionCompat.getInt(group, "mask"), next = old | mask;
        group.putInt("mask", next); groups.put(machine.toString(), group); entry.put("groups", groups);
        return old != next && next == 63;
    }
    public boolean invalidateMachineDay() {
        if (!writable || rejected.contains("a190") || !entries().contains("a190")) return false;
        var entry = entry("a190"); if (entry == null) return false;
        entry.put("groups", new CompoundTag()); return true;
    }
    private static CompoundTag machineGroup(CompoundTag entry, CompoundTag groups, java.util.UUID machine) {
        String key = machine.toString();
        if (!groups.contains(key) && groups.size() >= 64) {
            String oldest = groups.getAllKeys().stream().min(java.util.Comparator
                    .comparingLong((String id) -> NbtVersionCompat.getLong(NbtVersionCompat.getCompound(groups, id), "order"))
                    .thenComparing(java.util.Comparator.naturalOrder())).orElseThrow();
            groups.remove(oldest);
        }
        long sequence = NbtVersionCompat.getLong(entry, "sequence");
        if (sequence == Long.MAX_VALUE) {
            // Rebase the bounded recency order rather than overflow a save counter.
            var ordered = groups.getAllKeys().stream().sorted(java.util.Comparator.comparingLong(
                    (String id) -> NbtVersionCompat.getLong(NbtVersionCompat.getCompound(groups, id), "order"))).toList();
            sequence = 0;
            for (String id : ordered) NbtVersionCompat.getCompound(groups, id).putLong("order", ++sequence);
        }
        var group = NbtVersionCompat.getCompound(groups, key); group.putLong("order", ++sequence);
        entry.putLong("sequence", sequence); return group;
    }
    private static boolean validMask(CompoundTag entry, int maximum) {
        return NbtVersionCompat.contains(entry, "mask", Tag.TAG_INT)
                && NbtVersionCompat.getInt(entry, "mask") >= 0 && NbtVersionCompat.getInt(entry, "mask") <= maximum;
    }
    private static boolean machineItemId(String value) {
        if (value == null || value.isBlank() || value.length() > 256) return false;
        var id = ResourceLocation.tryParse(value);
        return id != null && !id.getPath().isEmpty() && value.equals(id.toString());
    }
    private static boolean validMachineItems(CompoundTag items) {
        return items.size() <= 42 && items.getAllKeys().stream().allMatch(id -> machineItemId(id)
                && NbtVersionCompat.contains(items, id, Tag.TAG_BYTE) && NbtVersionCompat.getBoolean(items, id));
    }
    private static boolean uuid(String value) {
        try { return java.util.UUID.fromString(value).toString().equals(value); } catch (IllegalArgumentException failure) { return false; }
    }
    private static boolean validMachineGroups(CompoundTag entry, boolean infusers) {
        if (!NbtVersionCompat.contains(entry, "groups", Tag.TAG_COMPOUND)
                || !NbtVersionCompat.contains(entry, "sequence", Tag.TAG_LONG)
                || NbtVersionCompat.getLong(entry, "sequence") < 0) return false;
        var groups = NbtVersionCompat.getCompound(entry, "groups");
        return groups.size() <= 64 && groups.getAllKeys().stream().allMatch(id -> {
            if (!uuid(id) || !NbtVersionCompat.contains(groups, id, Tag.TAG_COMPOUND)) return false;
            var group = NbtVersionCompat.getCompound(groups, id);
            if (!NbtVersionCompat.contains(group, "order", Tag.TAG_LONG) || NbtVersionCompat.getLong(group, "order") < 0
                    || NbtVersionCompat.getLong(group, "order") > NbtVersionCompat.getLong(entry, "sequence")) return false;
            if (!infusers) return validMask(group, 63);
            if (!NbtVersionCompat.contains(group, "infusers", Tag.TAG_COMPOUND)) return false;
            var items = NbtVersionCompat.getCompound(group, "infusers");
            return items.size() <= 3 && items.getAllKeys().stream().allMatch(key -> uuid(key)
                    && NbtVersionCompat.contains(items, key, Tag.TAG_BYTE) && NbtVersionCompat.getBoolean(items, key));
        });
    }
    public CompoundTag save() { return root.copy(); }
    public static AchievementProgress load(CompoundTag tag) { return new AchievementProgress(tag); }
    public boolean empty() {
        // Omit only the untouched canonical envelope. An empty entries map does not make
        // unknown envelope fields expendable when an offline player's record is saved.
        return writable && entries().isEmpty() && root.getAllKeys().equals(Set.of("version", "entries"));
    }
    private static boolean identifier(String value) { return value != null && value.length() <= 128 && ResourceLocation.tryParse(value) != null; }
    private static boolean text(CompoundTag tag, String key, int cap) {
        return NbtVersionCompat.contains(tag, key, Tag.TAG_STRING) && NbtVersionCompat.getString(tag, key).length() <= cap;
    }
    private static boolean validIds(CompoundTag ids, int cap) {
        return ids.size() <= cap && ids.getAllKeys().stream().allMatch(id -> identifier(id)
                && NbtVersionCompat.contains(ids, id, Tag.TAG_BYTE) && NbtVersionCompat.getBoolean(ids, id));
    }
    private static boolean validCollection(CompoundTag entry, String key, int cap, int perCategory) {
        if (!NbtVersionCompat.contains(entry, key, Tag.TAG_COMPOUND)) return false;
        var categories = NbtVersionCompat.getCompound(entry, key);
        return categories.size() <= cap && categories.getAllKeys().stream().allMatch(id -> identifier(id)
                && NbtVersionCompat.contains(categories, id, Tag.TAG_COMPOUND)
                && validIds(NbtVersionCompat.getCompound(categories, id), perCategory));
    }
}

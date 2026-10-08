package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Constructive examples from the native table algorithm, not an assertion that
 * a registered maximum is obtainable. No world, entity, menu or player is created.
 * Seeds are hypothetical possible offers; no success rate or bounded reroll bill is inferred. */
public final class NativeEnchanting {
    public static final String PROVIDER = "native_enchanting_access";
    private static final int SEEDS = 128;
    private NativeEnchanting() { }

    public record Offer(String item, String enchantment, int level, int bookshelves, int seed, int slot,
                        int requiredPlayerLevel, int spentLevels, int lapis, int initialXp,
                        Map<String, Integer> accompanyingEnchantments) { }
    public record XpSupply(ConfigurationAccess.Proof access, String entity, double observedXpPerKill, String contract) { }
    private record Witness(Offer offer, ConfigurationAccess.Proof access) { }

    /** Native Player XP-level curve. Initial level requirement differs from levels consumed. */
    public static int xpForLevel(int level) {
        if (level < 0 || level > 1000) throw new IllegalArgumentException("Unsupported experience level");
        int xp = 0;
        for (int l = 0; l < level; l++) xp = Math.addExact(xp, l >= 30 ? 112 + (l - 30) * 9 : l >= 15 ? 37 + (l - 15) * 5 : 7 + l * 2);
        return xp;
    }
    /** Registration/attribute data alone does not establish any encounter or XP supply. */
    public static XpSupply xpSupply(List<EnemyReference> enemies, Map<String, ResourceEvidence> resources, boolean mobXpEnabled) {
        if (!mobXpEnabled) return new XpSupply(unknown("Native recurring mob-XP path requires non-peaceful difficulty, doMobLoot and doMobSpawning; other XP paths not inferred"), "", 0, "");
        var supplies = new ArrayList<XpSupply>();
        for (var enemy : enemies) {
            double xp = enemy.axes().getOrDefault(CapabilityAxis.EXPERIENCE, 0.0);
            if (!enemy.entityId().startsWith("minecraft:") || !enemy.included() || enemy.encounter() != EnemyReference.Encounter.ROUTINE
                    || enemy.confidence() < .5 || !(xp > 0) || !Double.isFinite(xp)) continue;
            for (var resource : resources.values()) {
                if (!resource.external() || !resource.reachable()) continue;
                for (var source : resource.sources()) {
                    var availability = source.availability();
                    if (source.kind() != AcquisitionSource.Kind.MOB_DROP || !source.id().equals(enemy.entityId()) || !source.renewable()
                            || source.confidence() < .5 || availability == null || !availability.accessProven()
                            || availability.category() != SourceAvailability.Category.CONDITIONAL_RENEWABLE || !availability.uncertainty().isEmpty()) continue;
                    var stage = ProgressionBand.at(Math.max(enemy.stage().ordinal(), source.stage().ordinal()));
                    var proof = new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(stage, true,
                            Math.min(enemy.confidence(), source.confidence()), List.of(source)), List.of(enemy.entityId()), List.of());
                    supplies.add(new XpSupply(proof, enemy.entityId(), xp,
                            "Conditional active player kills of supported recurring native encounter; collect its XP orbs; no spawn/kill rate or guaranteed survival"));
                }
            }
        }
        return supplies.stream().min(Comparator.comparing((XpSupply s) -> s.access().placement().stage())
                .thenComparing(s -> -s.access().placement().confidence()).thenComparing(XpSupply::entity))
                .orElseGet(() -> new XpSupply(unknown("No independently accessible repeatable native XP encounter; registration is not supply"), "", 0, ""));
    }

    public static boolean mobXpEnabled(Difficulty difficulty, boolean mobLoot, boolean mobSpawning) {
        return difficulty != Difficulty.PEACEFUL && mobLoot && mobSpawning;
    }

    /** Same seed, cost rolls and seed+slot selection as EnchantmentMenu. Individual
     * examples include their real accompanying enchantments, never an incompatible combined maximum. */
    public static List<Offer> offers(ItemStack stack, List<Holder<Enchantment>> tableEntries, int shelves, int seedCount) {
        return offers(stack, tableEntries, shelves, seedCount, false);
    }
    /** A stored book is a distinct intermediate; callers must prove its anvil application. */
    public static List<Offer> bookOffers(List<Holder<Enchantment>> tableEntries, int shelves, int seedCount) {
        return offers(new ItemStack(Items.BOOK), tableEntries, shelves, seedCount, true);
    }
    private static List<Offer> offers(ItemStack stack, List<Holder<Enchantment>> tableEntries, int shelves, int seedCount, boolean book) {
        if (shelves < 0 || shelves > 15 || seedCount < 1 || seedCount > SEEDS) throw new IllegalArgumentException("Offer search bounds");
        if (!nativeItem(stack.getItem()) || stack.is(Items.BOOK) != book || !stack.isEnchantable()) return List.of();
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var found = new TreeMap<String, Offer>();
        for (int seed = 0; seed < seedCount; seed++) {
            var random = RandomSource.create(seed); int[] costs = new int[3];
            for (int slot = 0; slot < 3; slot++) costs[slot] = EnchantmentHelper.getEnchantmentCost(random, slot, shelves, stack);
            for (int slot = 0; slot < 3; slot++) {
                if (costs[slot] < slot + 1) continue;
                var selectionRandom = RandomSource.create((long)seed + slot);
                var selected = EnchantmentHelper.selectEnchantment(selectionRandom, stack, costs[slot], tableEntries.stream());
                // EnchantmentMenu removes one random entry from a multi-enchantment book.
                if (book && selected.size() > 1) selected.remove(selectionRandom.nextInt(selected.size()));
                var accompanying = new TreeMap<String, Integer>();
                for (var enchantment : selected) accompanying.put(enchantment.enchantment.unwrapKey().orElseThrow().location().toString(), enchantment.level);
                for (var e : accompanying.entrySet()) {
                    var offer = new Offer(item, e.getKey(), e.getValue(), shelves, seed, slot, costs[slot], slot + 1,
                            slot + 1, xpForLevel(costs[slot]), Collections.unmodifiableMap(accompanying));
                    found.merge(e.getKey() + "/" + e.getValue(), offer, (old, next) -> offerOrder().compare(old, next) <= 0 ? old : next);
                }
            }
        }
        return List.copyOf(found.values());
    }
    private static Comparator<Offer> offerOrder() {
        return Comparator.comparingInt(Offer::bookshelves).thenComparingInt(Offer::initialXp).thenComparingInt(Offer::lapis)
                .thenComparing(Offer::item).thenComparingInt(Offer::seed).thenComparingInt(Offer::slot);
    }
    static boolean nativeItem(Item item) {
        // Exclude custom item callbacks, including native-looking registry names. Books need an anvil/application proof.
        return item.getClass().getPackageName().equals("net.minecraft.world.item")
                && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft");
    }
    public static List<com.google.gson.JsonObject> bill(Offer offer) {
        var result = new ArrayList<com.google.gson.JsonObject>();
        var equipment = NativeConsumables.request(offer.item(), 1);
        equipment.add("components", new com.google.gson.JsonObject()); // Exact native default, no pre-existing enchantments.
        result.add(equipment);
        result.add(NativeConsumables.request("minecraft:enchanting_table", 1));
        result.add(NativeConsumables.request("minecraft:lapis_lazuli", offer.lapis()));
        if (offer.bookshelves() > 0) result.add(NativeConsumables.request("minecraft:bookshelf", offer.bookshelves()));
        return List.copyOf(result);
    }

    public static void collect(PackEvidenceContext context, Map<String, ResourceEvidence> resources, List<EnemyReference> enemies, CapabilitySink sink) {
        var captured = OptionalIntegration.attempt(PROVIDER, "native table and XP setup", () -> {
            var staged = new CapabilitySink();
            var registry = context.server().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            List<Holder<Enchantment>> entries = registry.getTag(EnchantmentTags.IN_ENCHANTING_TABLE)
                    .map(tag -> tag.stream().toList()).orElse(List.of());
            var xp = xpSupply(enemies, resources, mobXpEnabled(context.server().getWorldData().getDifficulty(),
                    context.server().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT),
                    context.server().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)));
            staged.definition(PROVIDER, "xp_supply", BalanceDocument.GSON.toJsonTree(xp));
            var search = new com.google.gson.JsonObject(); search.addProperty("seedsPerItemAndShelfCount", SEEDS);
            search.addProperty("scope", "Constructive finite native offer witnesses, not exhaustive absence/maximality, seed control, success probability or reroll cost; direct equipment only, no book/anvil inference");
            staged.definition(PROVIDER, "search", search);
            if (!xp.access().placement().reachable()) {
                staged.candidate(PROVIDER, PROVIDER, String.join("; ", xp.access().unknown()), Set.of(), CapabilitySink.Reason.ACCESS_UNPROVEN); return staged;
            }
            boolean shelvesSupported = Blocks.BOOKSHELF.defaultBlockState().is(BlockTags.ENCHANTMENT_POWER_PROVIDER)
                    && Blocks.AIR.defaultBlockState().is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER);
            var recipeOnly = new HashSet<String>();
            recipeOnly.add("minecraft:book"); // Item-only enchanted loot cannot certify a plain crafting book.
            for (var item : context.inputs().items()) if (nativeItem(item) && !new ItemStack(item).is(Items.BOOK) && new ItemStack(item).isEnchantable())
                recipeOnly.add(BuiltInRegistries.ITEM.getKey(item).toString());
            // A loot/trade item-only row cannot certify that this particular tool is unenchanted.
            var access = ConfiguredRecipeAccess.nativeCrafting(context, resources, recipeOnly);
            var table = access.requireExploration(List.of(NativeConsumables.request("minecraft:enchanting_table", 1),
                    NativeConsumables.request("minecraft:lapis_lazuli", 1))).access();
            staged.definition(PROVIDER, "table_setup", BalanceDocument.GSON.toJsonTree(table));
            if (!table.placement().reachable()) {
                staged.candidate(PROVIDER, PROVIDER, "Table/lapis setup unproven: " + table.unknown(), Set.of(), CapabilitySink.Reason.ACCESS_UNPROVEN);
                return staged;
            }
            var accepted = new TreeMap<String, Witness>(); var bills = new HashMap<String, ConfigurationAccess.Proof>();
            for (var item : context.inputs().items()) {
                if (!nativeItem(item)) continue;
                var attempt = OptionalIntegration.attempt(PROVIDER, BuiltInRegistries.ITEM.getKey(item).toString(), () -> {
                    var stack = new ItemStack(item); if (stack.is(Items.BOOK) || !stack.isEnchantable()) return List.<Witness>of();
                    var baseBill = new ArrayList<com.google.gson.JsonObject>();
                    var equipment = NativeConsumables.request(BuiltInRegistries.ITEM.getKey(item).toString(), 1);
                    equipment.add("components", new com.google.gson.JsonObject()); baseBill.add(equipment);
                    baseBill.add(NativeConsumables.request("minecraft:enchanting_table", 1)); baseBill.add(NativeConsumables.request("minecraft:lapis_lazuli", 1));
                    if (!access.requireExploration(baseBill).access().placement().reachable()) return List.<Witness>of();
                    var witnesses = new ArrayList<Witness>();
                    for (int shelves = 0; shelves <= (shelvesSupported ? 15 : 0); shelves++) {
                        for (var offer : offers(stack, entries, shelves, SEEDS)) {
                            String key = offer.item() + "/" + shelves + "/" + offer.lapis();
                            var materials = bills.computeIfAbsent(key, ignored -> access.requireExploration(bill(offer)).access());
                            var proof = ConfiguredRecipeAccess.combine(List.of(materials, xp.access()),
                                    "Conditional native enchanting outcome; hold " + offer.requiredPlayerLevel() + " levels (" + offer.initialXp()
                                            + " XP from zero); spend " + offer.spentLevels() + " levels and " + offer.lapis()
                                            + " lapis per attempt; clear valid shelf arrangement; no guaranteed result or reroll count", .9);
                            if (proof.placement().reachable()) witnesses.add(new Witness(offer, proof));
                        }
                    }
                    return witnesses;
                });
                attempt.value().ifPresentOrElse(witnesses -> witnesses.forEach(w -> accepted.merge(w.offer().enchantment() + "/" + w.offer().level(), w,
                        (old, next) -> witnessOrder().compare(old, next) <= 0 ? old : next)),
                        () -> staged.candidate(BuiltInRegistries.ITEM.getKey(item).toString(), PROVIDER, attempt.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED));
            }
            for (var witness : accepted.values()) {
                var offer = witness.offer();
                staged.definition(PROVIDER, offer.enchantment() + "/" + offer.level(), BalanceDocument.GSON.toJsonTree(witness));
                var enchantment = registry.get(net.minecraft.resources.ResourceLocation.parse(offer.enchantment()));
                NativeCapabilityReader.readEnchantment(offer.item(), "table/" + offer.enchantment(), enchantment, offer.level(),
                        witness.access().placement(), true, staged);
            }
            for (var holder : registry.holders().toList()) if (accepted.keySet().stream().noneMatch(k -> k.startsWith(holder.key().location() + "/")))
                staged.candidate(holder.key().location().toString(), PROVIDER,
                        "No proven direct table witness in bounded search; treasure/trade/loot/book/anvil paths are not inferred", Set.of(), CapabilitySink.Reason.ACCESS_UNPROVEN);
            return staged;
        });
        captured.value().ifPresentOrElse(sink::merge, () -> sink.candidate(PROVIDER, PROVIDER, captured.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED));
    }
    private static Comparator<Witness> witnessOrder() {
        return Comparator.comparing((Witness w) -> w.access().placement().stage())
                .thenComparing(w -> -w.access().placement().confidence()).thenComparing(Witness::offer, offerOrder());
    }
    private static ConfigurationAccess.Proof unknown(String reason) {
        return new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.APEX, false, 0, List.of()), List.of(), List.of(reason));
    }
}

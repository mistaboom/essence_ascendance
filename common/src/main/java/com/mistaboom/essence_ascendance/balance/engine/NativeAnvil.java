package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Native survival anvil contract for a fresh item and a fresh enchanted book.
 * No menu/player/world mutation. Prior-work merging, renaming and custom callbacks are excluded. */
public final class NativeAnvil {
    public static final String PROVIDER = "native_anvil_access";
    private NativeAnvil() { }
    public record Application(String item, Map<String, Integer> applied, int requiredAndSpentLevels,
                              int xpFromZero, int resultingRepairCost) { }
    private record Witness(NativeEnchanting.Offer book, Application application, ConfigurationAccess.Proof access) { }
    private record TradeWitness(String trade, NativeTradeProgression.Plan plan, String jobsite, Application application, ConfigurationAccess.Proof access) { }
    static boolean target(Item item, java.util.stream.Stream<Enchantment> enchantments) {
        return item != Items.AIR && NativeEnchanting.nativeItem(item) && item != Items.BOOK && item != Items.ENCHANTED_BOOK
                && enchantments.anyMatch(e -> e.canEnchant(new ItemStack(item)));
    }

    public static Optional<Application> apply(ItemStack target, ItemStack book) {
        if (!NativeEnchanting.nativeItem(target.getItem()) || target.is(Items.BOOK) || target.is(Items.ENCHANTED_BOOK)
                || target.getCount() != 1 || !target.getComponents().equals(new ItemStack(target.getItem()).getComponents())
                || target.getOrDefault(DataComponents.REPAIR_COST, 0) != 0 || target.has(DataComponents.CUSTOM_NAME) || target.isDamaged()
                || !EnchantmentHelper.canStoreEnchantments(target) || !EnchantmentHelper.getEnchantmentsForCrafting(target).isEmpty()
                || !book.is(Items.ENCHANTED_BOOK) || book.getCount() != 1
                || book.getOrDefault(DataComponents.REPAIR_COST, 0) != 0
                || book.getComponentsPatch().entrySet().stream().anyMatch(e -> e.getKey() != DataComponents.STORED_ENCHANTMENTS
                    && e.getKey() != DataComponents.REPAIR_COST)) return Optional.empty();
        var stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        var entries = stored.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().unwrapKey().orElseThrow().location())).toList();
        // Incompatible books have iteration-dependent partial application. Do not invent a combined configuration.
        for (int i = 0; i < entries.size(); i++) for (int j = i + 1; j < entries.size(); j++)
            if (!Enchantment.areCompatible(entries.get(i).getKey(), entries.get(j).getKey())) return Optional.empty();
        var applied = new TreeMap<String, Integer>(); long cost = 0;
        for (var entry : entries) {
            var enchantment = entry.getKey().value();
            if (!enchantment.canEnchant(target)) continue;
            int level = Math.min(entry.getIntValue(), enchantment.getMaxLevel());
            if (level < 1 || enchantment.getAnvilCost() < 0) return Optional.empty();
            cost += (long)Math.max(1, enchantment.getAnvilCost() / 2) * level;
            applied.put(entry.getKey().unwrapKey().orElseThrow().location().toString(), level);
        }
        if (applied.isEmpty() || cost < 1 || cost >= 40) return Optional.empty();
        return Optional.of(new Application(BuiltInRegistries.ITEM.getKey(target.getItem()).toString(),
                Collections.unmodifiableMap(applied), (int)cost, NativeEnchanting.xpForLevel((int)cost), 1));
    }

    static ItemStack book(NativeEnchanting.Offer offer, net.minecraft.core.Registry<Enchantment> registry) {
        var stack = new ItemStack(Items.ENCHANTED_BOOK); var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        offer.accompanyingEnchantments().forEach((id, level) -> enchantments.set(registry.getHolder(ResourceLocation.parse(id)).orElseThrow(), level));
        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable()); return stack;
    }

    /** Single quantity ledger includes book manufacture, table and shelves, lapis, tool and anvil. */
    public static List<JsonObject> bill(NativeEnchanting.Offer book, Application application) {
        var bill = new ArrayList<>(NativeEnchanting.bill(book));
        var target = NativeConsumables.request(application.item(), 1); target.add("components", new JsonObject());
        bill.add(target); bill.add(NativeConsumables.request("minecraft:anvil", 1)); return List.copyOf(bill);
    }

    public static void collect(PackEvidenceContext context, Map<String, ResourceEvidence> resources,
                               List<EnemyReference> enemies, List<NativeTradeProgression.AcquiredBook> trades, CapabilitySink sink) {
        var attempt = OptionalIntegration.attempt(PROVIDER, "fresh book application", () -> {
            var staged = new CapabilitySink();
            var registry = context.server().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var entries = registry.getTag(EnchantmentTags.IN_ENCHANTING_TABLE).map(t -> t.stream().toList()).orElse(List.of());
            var xp = NativeEnchanting.xpSupply(enemies, resources, NativeEnchanting.mobXpEnabled(context.server().getWorldData().getDifficulty(),
                    context.server().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT),
                    context.server().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)));
            staged.definition(PROVIDER, "xp_supply", BalanceDocument.GSON.toJsonTree(xp));
            var scope = new JsonObject();
            scope.addProperty("scope", "128 possible table seeds per bookshelf count; exact book-removal RNG; fresh native target/book only; one finite anvil use, no upkeep/reroll guarantee or cumulative prior-work merges. Table XP is spent first; independently re-earn anvil XP, never reuse consumed levels.");
            staged.definition(PROVIDER, "scope", scope);
            if (!xp.access().placement().reachable()) { staged.candidate(PROVIDER, PROVIDER, xp.access().unknown().toString(), Set.of(), CapabilitySink.Reason.ACCESS_UNPROVEN); return staged; }
            var items = context.inputs().items().stream().filter(i -> target(i, registry.stream())).toList();
            var constrained = new HashSet<String>(); constrained.add("minecraft:book");
            items.forEach(i -> constrained.add(BuiltInRegistries.ITEM.getKey(i).toString()));
            var access = ConfiguredRecipeAccess.nativeCrafting(context, resources, constrained);
            var setup = access.requireExploration(List.of(NativeConsumables.request("minecraft:anvil", 1),
                    NativeConsumables.request("minecraft:enchanting_table", 1), NativeConsumables.request("minecraft:lapis_lazuli", 1))).access();
            staged.definition(PROVIDER, "setup", BalanceDocument.GSON.toJsonTree(setup));
            // No book path can contribute yet. Avoid probing every tool's failed craft
            // tree when there are neither acquired trade books nor a proven table setup.
            if (trades.isEmpty() && !setup.placement().reachable()) {
                staged.candidate(PROVIDER, PROVIDER, setup.unknown().toString(), Set.of(), CapabilitySink.Reason.ACCESS_UNPROVEN); return staged;
            }
            // Reject unattainable targets once, before trying all books against expensive failed craft trees.
            items = items.stream().filter(i -> {
                var target = NativeConsumables.request(BuiltInRegistries.ITEM.getKey(i).toString(), 1); target.add("components", new JsonObject());
                return access.requireExploration(List.of(target, NativeConsumables.request("minecraft:anvil", 1))).access().placement().reachable();
            }).toList();
            var tradeBills = new HashMap<String, ConfigurationAccess.Proof>();
            var tradeWitnesses = new TreeMap<String, TradeWitness>();
            var tradeOrder = Comparator.comparing((TradeWitness w) -> w.access().placement().stage()).thenComparing(w -> -w.access().placement().confidence())
                    .thenComparingInt(w -> w.application().requiredAndSpentLevels()).thenComparing(w -> w.application().item()).thenComparing(TradeWitness::trade);
            for (var trade : trades) for (var item : items) {
                var application = apply(new ItemStack(item), trade.source().nativeOffer().getResult()).orElse(null); if (application == null) continue;
                var bill = new ArrayList<>(trade.materials()); bill.add(NativeConsumables.request("minecraft:anvil", 1));
                var target = NativeConsumables.request(application.item(), 1); target.add("components", new JsonObject()); bill.add(target);
                String billKey = bill.toString();
                var materials = tradeBills.computeIfAbsent(billKey, ignored -> access.requireExploration(bill).access()); if (!materials.placement().reachable()) continue;
                var proof = ConfiguredRecipeAccess.combine(List.of(materials, trade.actor().access(), xp.access()),
                        "Finite trade sequence " + trade.source().id() + "; all level-up inputs, earned outputs, jobsite, tool and anvil share one material bill. Independently earn and spend "
                                + application.requiredAndSpentLevels() + " player levels (" + application.xpFromZero() + " XP from zero); villager XP is not player XP; conditional selections/discovery, no restock or discount assumed", .9);
                var witness = new TradeWitness(trade.source().id(), trade.plan(), trade.jobsite(), application, proof);
                application.applied().forEach((enchantment, level) -> tradeWitnesses.merge(enchantment + "/" + level, witness,
                        (old, next) -> tradeOrder.compare(old, next) <= 0 ? old : next));
            }
            for (var row : tradeWitnesses.entrySet()) {
                var witness = row.getValue(); String enchantment = row.getKey().substring(0, row.getKey().lastIndexOf('/'));
                staged.definition(PROVIDER, "trade/" + row.getKey(), BalanceDocument.GSON.toJsonTree(witness));
                NativeCapabilityReader.readEnchantment(witness.application().item(), "trade_anvil/" + enchantment, registry.get(ResourceLocation.parse(enchantment)),
                        witness.application().applied().get(enchantment), witness.access().placement(), true, staged);
            }
            if (!setup.placement().reachable()) { staged.candidate(PROVIDER, PROVIDER, setup.unknown().toString(), Set.of(), CapabilitySink.Reason.ACCESS_UNPROVEN); return staged; }
            boolean shelves = Blocks.BOOKSHELF.defaultBlockState().is(BlockTags.ENCHANTMENT_POWER_PROVIDER)
                    && Blocks.AIR.defaultBlockState().is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER);
            var accepted = new TreeMap<String, Witness>(); var bills = new HashMap<String, ConfigurationAccess.Proof>();
            for (int count = 0; count <= (shelves ? 15 : 0); count++) for (var offer : NativeEnchanting.bookOffers(entries, count, 128)) {
                var stack = book(offer, registry);
                for (var item : items) {
                    var application = apply(new ItemStack(item), stack).orElse(null);
                    // This offer was retained for a specific surviving book enchantment. Other effects stay in its actual configuration.
                    if (application == null || !application.applied().containsKey(offer.enchantment())) continue;
                    String billKey = application.item() + "/" + count + "/" + offer.lapis();
                    var materials = bills.computeIfAbsent(billKey, ignored -> access.requireExploration(bill(offer, application)).access());
                    if (!materials.placement().reachable()) continue;
                    var proof = ConfiguredRecipeAccess.combine(List.of(materials, xp.access()),
                            "Conditional table book at " + offer.requiredPlayerLevel() + " levels; spends " + offer.spentLevels()
                                    + " levels and " + offer.lapis() + " lapis; then re-earn and spend " + application.requiredAndSpentLevels()
                                    + " anvil levels (" + application.xpFromZero() + " XP from zero). All accompanying book effects retained; one fresh anvil use; no reroll rate", .9);
                    var witness = new Witness(offer, application, proof);
                    accepted.merge(offer.enchantment() + "/" + application.applied().get(offer.enchantment()), witness,
                            (old, next) -> order().compare(old, next) <= 0 ? old : next);
                }
            }
            for (var row : accepted.entrySet()) {
                var witness = row.getValue(); var offer = witness.book();
                staged.definition(PROVIDER, row.getKey(), BalanceDocument.GSON.toJsonTree(witness));
                NativeCapabilityReader.readEnchantment(witness.application().item(), "anvil/" + offer.enchantment(),
                        registry.get(ResourceLocation.parse(offer.enchantment())), witness.application().applied().get(offer.enchantment()),
                        witness.access().placement(), true, staged);
            }
            return staged;
        });
        attempt.value().ifPresentOrElse(sink::merge, () -> sink.candidate(PROVIDER, PROVIDER, attempt.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED));
    }
    private static Comparator<Witness> order() {
        return Comparator.comparing((Witness w) -> w.access().placement().stage()).thenComparing(w -> -w.access().placement().confidence())
                .thenComparingInt(w -> w.book().bookshelves()).thenComparingInt(w -> w.book().initialXp() + w.application().xpFromZero())
                .thenComparing(w -> w.application().item()).thenComparingInt(w -> w.book().seed()).thenComparingInt(w -> w.book().slot());
    }
}

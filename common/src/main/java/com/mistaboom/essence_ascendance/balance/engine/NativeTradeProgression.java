package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import java.util.*;

/** Bounded finite-stock trade sequences. A sequence is a material/XP witness, NOT NPC access.
 * Each level chooses one offer, so mutually exclusive factory selections are never pooled.
 * Costs use neutral initial prices; restocks, discounts, player XP and future outputs are not credit. */
public final class NativeTradeProgression {
    public static final String PROVIDER = "native_trade_progression";
    private static final int MAX_PLANS = 128, MAX_OFFERS_PER_LEVEL = 128, MAX_USES = 1024;
    private static final Set<String> NATIVE_FACTORIES = Set.of("net.minecraft.world.entity.npc.VillagerTrades$EmeraldForItems",
            "net.minecraft.world.entity.npc.VillagerTrades$ItemsForEmeralds", "net.minecraft.world.entity.npc.VillagerTrades$EnchantBookForEmeralds");
    private NativeTradeProgression() { }
    public record Offer(String id, String profession, String villagerType, int level, MerchantOffer nativeOffer) { }
    public record Step(String offer, int unlockedLevel, int uses, int villagerXpBefore, int villagerXpAfter) { }
    public record Plan(List<Step> steps, Map<String, Long> externalInputs, Map<String, Long> defaultSurplus, int villagerXp) { }
    public record Search(List<Plan> plans, boolean bounded, List<String> unknown) { }
    public record AcquiredBook(Offer source, Plan plan, String jobsite, NativeVillagerAccess.Opportunity actor) {
        public List<JsonObject> materials() { var result = new ArrayList<>(bill(plan)); result.add(NativeConsumables.request(jobsite, 1)); return result; }
    }
    private record Analysis(CapabilitySink sink, List<AcquiredBook> books) { }

    private static final class Ledger {
        final Map<String, Long> inputs = new TreeMap<>(), stock = new TreeMap<>();
        final List<Step> steps = new ArrayList<>(); int xp;
        Ledger copy() { var next = new Ledger(); next.inputs.putAll(inputs); next.stock.putAll(stock); next.steps.addAll(steps); next.xp = xp; return next; }
        void pay(ItemStack stack, int uses) {
            if (stack.isEmpty()) return;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            long needed = Math.multiplyExact((long)stack.getCount(), uses), held = stock.getOrDefault(id, 0L), taken = Math.min(held, needed);
            if (taken > 0) { if (taken == held) stock.remove(id); else stock.put(id, held - taken); }
            if (needed > taken) inputs.merge(id, needed - taken, Math::addExact);
        }
        void execute(Offer source, int uses) {
            var offer = source.nativeOffer(); int before = xp;
            pay(offer.getCostA(), uses); pay(offer.getCostB(), uses);
            // Only exact default outputs can fund later default payment requests. A configured result is never plain stock.
            var result = offer.getResult();
            if (ItemStack.isSameItemSameComponents(result, new ItemStack(result.getItem())))
                stock.merge(BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), Math.multiplyExact((long)result.getCount(), uses), Math::addExact);
            xp = Math.addExact(xp, Math.multiplyExact(offer.getXp(), uses));
            steps.add(new Step(source.id(), source.level(), uses, before, xp));
        }
        Plan finish() { return new Plan(List.copyOf(steps), Map.copyOf(inputs), Map.copyOf(stock), xp); }
    }

    /** Native ItemCost predicates are checked against the actual default input, never discarded.
     * Component-bearing payments and modified/used offers need another provider contract. */
    public static boolean supported(Offer source) {
        var offer = source.nativeOffer();
        if (source.level() < 1 || source.level() > 5 || !source.profession().startsWith("minecraft:")
                || offer == null || offer.getUses() != 0 || offer.getDemand() != 0 || offer.getSpecialPriceDiff() != 0
                || offer.getMaxUses() < 1 || offer.getMaxUses() > MAX_USES || offer.getXp() < 0 || offer.getXp() > 10000
                || offer.getResult().isEmpty() || !NativeEnchanting.nativeItem(offer.getResult().getItem())) return false;
        var a = new ItemStack(offer.getItemCostA().item(), offer.getCostA().getCount());
        var b = offer.getItemCostB().map(cost -> new ItemStack(cost.item(), cost.count())).orElse(ItemStack.EMPTY);
        return NativeEnchanting.nativeItem(a.getItem()) && (b.isEmpty() || NativeEnchanting.nativeItem(b.getItem()))
                && ItemStack.isSameItemSameComponents(a, offer.getCostA())
                && (b.isEmpty() || ItemStack.isSameItemSameComponents(b, offer.getCostB())) && offer.satisfiedBy(a, b);
    }

    public static Search plans(List<Offer> offers, Offer target) {
        if (!supported(target)) return new Search(List.of(), false, List.of("Target offer requires unsupported price, stock, input predicate or native item semantics"));
        List<Ledger> states = List.of(new Ledger()); boolean bounded = false;
        for (int level = 1; level < target.level(); level++) {
            int current = level, threshold = VillagerData.getMaxXpPerLevel(level);
            var candidates = offers.stream().filter(NativeTradeProgression::supported)
                    .filter(o -> o.level() == current && o.profession().equals(target.profession()) && o.villagerType().equals(target.villagerType())
                            && o.nativeOffer().getXp() > 0).sorted(Comparator.comparing(Offer::id)).toList();
            if (candidates.size() > MAX_OFFERS_PER_LEVEL) bounded = true;
            var next = new ArrayList<Ledger>();
            for (var state : states) for (var candidate : candidates.stream().limit(MAX_OFFERS_PER_LEVEL).toList()) {
                // Even with an overshot threshold the native villager advances only after another trade at the next level.
                int xpPerTrade = candidate.nativeOffer().getXp();
                int uses = Math.max(1, (Math.max(0, threshold - state.xp) + xpPerTrade - 1) / xpPerTrade);
                if (uses > candidate.nativeOffer().getMaxUses()) continue;
                if (next.size() == MAX_PLANS) { bounded = true; break; }
                var branch = state.copy(); branch.execute(candidate, uses); next.add(branch);
            }
            states = next;
            if (states.isEmpty()) return new Search(List.of(), bounded,
                    List.of("No witnessed one-offer finite-stock promotion from villager level " + level + "; multi-offer/restock paths not inferred"));
        }
        var result = new ArrayList<Plan>();
        for (var state : states) { state.execute(target, 1); result.add(state.finish()); }
        return new Search(List.copyOf(result), bounded, List.of());
    }

    public static List<JsonObject> bill(Plan plan) {
        return plan.externalInputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> {
            var request = new JsonObject(); request.addProperty("id", e.getKey()); request.addProperty("count", e.getValue());
            request.add("components", new JsonObject()); return request;
        }).toList();
    }

    /** Conditional NPC discovery and a paid finite sequence; output still needs an anvil application proof. */
    public static List<AcquiredBook> collect(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
        var attempt = OptionalIntegration.attempt(PROVIDER, "book trade progression", () -> {
            var staged = new CapabilitySink(); var offers = new ArrayList<Offer>();
            var actors = NativeVillagerAccess.collect(context, staged); var acquired = new ArrayList<AcquiredBook>();
            var ops = RegistryOps.create(JsonOps.INSTANCE, context.server().registryAccess());
            for (var process : com.mistaboom.essence_ascendance.valuation.ProductionGraphAdapter.observedTrades(context.server())) {
                if (!process.family().equals("minecraft:trading") || !process.metadata().containsKey("observed_offer_definition")
                        || !process.metadata().getOrDefault("stock_kind", "").equals("restocking_villager")) continue;
                if (!NATIVE_FACTORIES.contains(process.metadata().get("listing_class"))) {
                    staged.candidate(process.id(), PROVIDER, "Offer factory selection/context semantics unsupported: " + process.metadata().get("listing_class"), Set.of(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR); continue;
                }
                var parsed = OptionalIntegration.attempt(PROVIDER, process.id(), () -> new Offer(process.id(), process.metadata().get("trader"),
                        process.metadata().get("sampled_villager_type"), Integer.parseInt(process.metadata().get("level")),
                        MerchantOffer.CODEC.parse(ops, JsonParser.parseString(process.metadata().get("observed_offer_definition"))).getOrThrow()));
                parsed.value().ifPresentOrElse(offers::add, () -> staged.candidate(process.id(), PROVIDER, parsed.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED));
            }
            var access = ConfiguredRecipeAccess.nativeCrafting(context, resources, Set.of("minecraft:book"));
            var poiRegistry = context.server().registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE);
            for (var target : offers) {
                if (!target.nativeOffer().getResult().is(Items.ENCHANTED_BOOK)) continue;
                var read = OptionalIntegration.attempt(PROVIDER, target.id(), () -> {
                    var search = plans(offers, target); var row = new JsonObject();
                    row.addProperty("profession", target.profession()); row.addProperty("villagerType", target.villagerType()); row.addProperty("requiredLevel", target.level());
                    row.add("offer", MerchantOffer.CODEC.encodeStart(ops, target.nativeOffer()).getOrThrow());
                    row.add("search", BalanceDocument.GSON.toJsonTree(search));
                    var profession = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(ResourceLocation.parse(target.profession())).orElseThrow();
                    var jobsites = poiRegistry.holders().filter(profession.acquirableJobSite())
                            .flatMap(p -> p.value().matchingStates().stream()).map(s -> s.getBlock().asItem())
                            .filter(i -> i != Items.AIR && NativeEnchanting.nativeItem(i)).map(i -> BuiltInRegistries.ITEM.getKey(i).toString()).distinct().sorted().toList();
                    row.add("jobsiteItems", BalanceDocument.GSON.toJsonTree(jobsites));
                    var materialWitnesses = new JsonArray(); var accepted = new ArrayList<AcquiredBook>();
                    var actor = actors.stream().filter(a -> a.villagerType().equals(target.villagerType())).findFirst();
                    // Without the required actor no material search can produce an acquired
                    // book or affect calibration. Retain the raw plans and exact missing gate;
                    // do not spend hundreds of joint bill searches on unused partial witnesses.
                    if (actor.isEmpty()) row.addProperty("materialSearch", "Not evaluated: no matching supported NPC opportunity; raw plans retained, no acquisition or absence inferred");
                    if (actor.isPresent()) for (var plan : search.plans()) for (String jobsite : jobsites) {
                        var bill = new ArrayList<>(bill(plan)); bill.add(NativeConsumables.request(jobsite, 1));
                        var proof = access.requireExploration(bill).access();
                        if (!proof.placement().reachable()) continue;
                        var witness = new JsonObject(); witness.addProperty("jobsite", jobsite);
                        witness.add("plan", BalanceDocument.GSON.toJsonTree(plan)); witness.add("materials", BalanceDocument.GSON.toJsonTree(proof));
                        actor.ifPresent(npc -> accepted.add(new AcquiredBook(target, plan, jobsite, npc)));
                        materialWitnesses.add(witness); break;
                    }
                    row.add("materialWitnesses", materialWitnesses);
                    row.addProperty("accessProven", !accepted.isEmpty());
                    actor.ifPresent(npc -> row.add("actor", BalanceDocument.GSON.toJsonTree(npc)));
                    row.addProperty("contract", "Conditional successful NPC discovery and native offer selections, one selected offer per level, neutral initial prices, wait for each level-up; no player XP, restocks, discounts, population replenishment or reroll guarantee credited. Book must still be applied at an anvil.");
                    row.addProperty("remainingGate", accepted.isEmpty() ? "No joint material and matching native NPC opportunity witness; missing/unsupported paths are not absence" : "Exact book acquisition only; anvil, target equipment and independent player XP still required");
                    return Map.entry(row, List.copyOf(accepted));
                });
                read.value().ifPresentOrElse(result -> {
                    var row = result.getKey(); acquired.addAll(result.getValue());
                    staged.definition(PROVIDER, target.id(), row);
                    if (result.getValue().isEmpty()) staged.candidate(target.id(), PROVIDER, row.get("remainingGate").getAsString(), Set.of(), CapabilitySink.Reason.ACCESS_UNPROVEN);
                }, () -> staged.candidate(target.id(), PROVIDER, read.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED));
            }
            return new Analysis(staged, List.copyOf(acquired));
        });
        attempt.value().ifPresentOrElse(result -> sink.merge(result.sink()), () -> sink.candidate(PROVIDER, PROVIDER, attempt.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED));
        return attempt.value().map(Analysis::books).orElse(List.of());
    }
}

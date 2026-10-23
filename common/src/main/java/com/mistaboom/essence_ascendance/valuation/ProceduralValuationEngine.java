package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.BaseRailBlock;
import java.util.*;

/** Shared item-property/standard-recipe estimates. No source search, graph recursion or demand uplift. */
public final class ProceduralValuationEngine {
    private record RarityPolicy(double multiplier, long minimum) { }
    private static final RarityPolicy UNKNOWN_RARITY_POLICY = new RarityPolicy(1, 1);
    private static final Map<String, RarityPolicy> RARITY_POLICIES = Map.of(
            Rarity.COMMON.name(), new RarityPolicy(1, 1),
            Rarity.UNCOMMON.name(), new RarityPolicy(ProceduralValuationSettings.UNCOMMON_MULTIPLIER, ProceduralValuationSettings.UNCOMMON_MINIMUM),
            Rarity.RARE.name(), new RarityPolicy(ProceduralValuationSettings.RARE_MULTIPLIER, ProceduralValuationSettings.RARE_MINIMUM),
            Rarity.EPIC.name(), new RarityPolicy(ProceduralValuationSettings.EPIC_MULTIPLIER, ProceduralValuationSettings.EPIC_MINIMUM));
    private static final Object INDEX_LOCK = new Object();
    private static MinecraftServer indexedServer;
    private static ProceduralValuationIndex index;
    private static GenerationDataSnapshot generationData;
    private static List<ProceduralValuationResult> cachedResults;
    private static Map<ResourceLocation, ProceduralValuationResult> byId = Map.of();
    private ProceduralValuationEngine() { }

    public static ProceduralValuationResult evaluate(MinecraftServer server, ItemStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("ItemStack cannot be empty");
        evaluateAll(server);
        var value = byId.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (value == null) throw new IllegalArgumentException("Unregistered item");
        return value;
    }
    public static List<ProceduralValuationResult> evaluateAll(MinecraftServer server) {
        if (server == null) throw new IllegalArgumentException("Server cannot be null");
        synchronized (INDEX_LOCK) {
            var snapshot = ensureIndex(server);
            if (cachedResults != null) { BalancePerformance.increment("valuation_result_cache_hits"); return cachedResults; }
            Map<String, SimpleValuationSolver.Seed> seeds = new TreeMap<>();
            Map<String, Intrinsic> measured = new TreeMap<>();
            List<Item> items = generationData(server).items().stream().filter(item -> item != Items.AIR)
                    .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
            var materialFactors = MaterialPropertyEstimates.collect(items, generationData(server).itemTags());
            for (Item item : items) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString(); Intrinsic intrinsic = measure(item, materialFactors.get(id));
                measured.put(id, intrinsic);
                if (ValuationGenerationInputs.itemAllowed(item)) seeds.put(id, intrinsic.seed());
            }
            SimpleValuationSolver.Result solved;
            try (var phase = BalancePerformance.phase("bounded_recipe_estimates")) { solved = SimpleValuationSolver.solve(seeds, snapshot.production()); }
            List<ProceduralValuationResult> results = new ArrayList<>(items.size());
            Map<ResourceLocation, ProceduralValuationResult> lookup = new HashMap<>();
            for (Item item : items) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item); Intrinsic intrinsic = measured.get(id.toString());
                var value = solved.values().get(id.toString());
                long amount = value == null ? 0 : value.amount();
                Map<EssenceDefinition, Long> routed = new LinkedHashMap<>();
                if (value != null) for (var family : EssenceTypes.ORDERED) {
                    long quantity = value.route().getOrDefault(family.id().toString(), 0L); if (quantity > 0) routed.put(family, quantity);
                }
                List<String> factors = new ArrayList<>(intrinsic.signals());
                factors.add("Policy estimate from native item properties/common tags and bounded standard recipes; acquisition, renewability and farm rate unproved");
                factors.add("Recipe passes=" + solved.passes() + "; unresolved/incomplete processes=" + solved.unresolvedProcesses());
                String status = value == null ? "EXCLUDED_BY_EXPLICIT_OVERRIDE" : value.status();
                factors.add(status);
                Optional<ProceduralValuationResult.RecipeChoice> recipe = Optional.empty();
                if (value != null && !value.recipe().isEmpty()) {
                    var selected = snapshot.recipesProducing(item).stream().filter(r -> r.id().toString().equals(value.recipe())).findFirst();
                    if (selected.isPresent()) {
                        var r = selected.get(); recipe = Optional.of(new ProceduralValuationResult.RecipeChoice(r.id(), r.production().family(), amount,
                                r.outputCount(), r.production().conservationComplete()));
                    }
                }
                double confidence = intrinsic.seed().priority() >= 3 ? .6 : .3;
                var result = new ProceduralValuationResult(id, amount, intrinsic.seed().value(), recipe, confidence, routed,
                        snapshot.recipesProducing(item).size(), factors,
                        new ProceduralValuationResult.RoutingDiagnostics(intrinsic.signals(),
                                intrinsic.seed().priority() >= 3 ? ProceduralValuationResult.ConfidenceBand.MEDIUM : ProceduralValuationResult.ConfidenceBand.LOW),
                        ProceduralValuationResult.ValueEvidence.PROPERTY_RECIPE_ESTIMATE, status);
                results.add(result); lookup.put(id, result);
            }
            cachedResults = List.copyOf(results); byId = Map.copyOf(lookup);
            BalancePerformance.count("valuation_items", items.size());
            BalancePerformance.count("valuation_estimate_passes", solved.passes());
            BalancePerformance.count("valuation_unresolved_recipes", solved.unresolvedProcesses());
            return cachedResults;
        }
    }

    /** Reads static default components and common semantics; never calls custom gameplay methods. */
    static Intrinsic measure(Item item) {
        return measure(item, null);
    }
    static Intrinsic measure(Item item, Double materialFactor) {
        ItemStack stack = new ItemStack(item); long base = item instanceof BlockItem ? ProceduralValuationSettings.BLOCK_BASE : ProceduralValuationSettings.DEFAULT_BASE;
        int priority = item instanceof BlockItem ? 1 : 0; Map<String, Double> route = new TreeMap<>(); List<String> signals = new ArrayList<>();
        if (stack.is(ProceduralValuationTags.NUGGETS)) { base = ProceduralValuationSettings.NUGGET_BASE; priority = 4; vote(route,"gathering",3); signals.add("common nugget tag"); }
        else if (stack.is(ProceduralValuationTags.INGOTS)) { base = ProceduralValuationSettings.INGOT_BASE; priority = 4; vote(route,"gathering",3); signals.add("common ingot tag"); }
        else if (stack.is(ProceduralValuationTags.RAW_MATERIALS)) { base = ProceduralValuationSettings.RAW_MATERIAL_BASE; priority = 4; vote(route,"gathering",3); signals.add("common raw material tag"); }
        else if (stack.is(ProceduralValuationTags.GEMS)) { base = ProceduralValuationSettings.GEM_BASE; priority = 4; vote(route,"gathering",3); signals.add("common gem tag"); }
        else if (stack.is(ProceduralValuationTags.ORES)) { base = ProceduralValuationSettings.ORE_BASE; priority = 3; vote(route,"gathering",3); signals.add("common ore tag"); }
        else if (stack.is(ProceduralValuationTags.STORAGE_BLOCKS)) { base = ProceduralValuationSettings.STORAGE_BLOCK_BASE; priority = 2; vote(route,"gathering",3); signals.add("common storage material tag; conversion form estimate"); }
        if (stack.is(ProceduralValuationTags.COALS)) { base = Math.max(base, ProceduralValuationSettings.FUEL_BASE); priority = Math.max(priority, 4); vote(route,"utility",3); signals.add("common fuel tag"); }
        if (materialFactor != null) {
            base = Math.min(ProceduralValuationSettings.MAX_VALUE, Math.max(1, Math.round(base * materialFactor)));
            signals.add("common material mining/hardness/yield-band policy multiplier=" + materialFactor + "; no measured drop rate or acquisition proof");
        }
        if (stack.is(ProceduralValuationTags.CROPS) || stack.is(ProceduralValuationTags.SEEDS) || stack.is(ProceduralValuationTags.SAPLINGS)) { vote(route,"gathering",2); vote(route,"vitality",1); priority = Math.max(priority, 3); signals.add("plant material tag; growth/access not inferred"); }
        if (stack.is(ProceduralValuationTags.LOGS) || stack.is(ProceduralValuationTags.LEAVES)) { vote(route,"gathering",2); vote(route,"utility",1); priority = Math.max(priority, 3); signals.add("wood/leaf material tag"); }
        if (stack.has(DataComponents.FOOD)) { var food = stack.get(DataComponents.FOOD); base = Math.max(base, Math.round(24 + 8 * food.nutrition() + 8 * food.saturation())); vote(route,"vitality",4); priority = Math.max(priority, 3); signals.add("native food component"); }
        //? if >=26.1.2
        /*if (stack.has(DataComponents.TOOL) && !stack.is(net.minecraft.tags.ItemTags.SWORDS) || item instanceof ShearsItem || item instanceof BrushItem || item instanceof FishingRodItem || stack.is(ProceduralValuationTags.MINING_TOOLS)) {*/
        //? if <26.1.2
        if (item instanceof DiggerItem || item instanceof ShearsItem || item instanceof BrushItem || item instanceof FishingRodItem || stack.is(ProceduralValuationTags.MINING_TOOLS)) {
            base = Math.max(base, ProceduralValuationSettings.TOOL_BASE); vote(route,"gathering",4); priority = Math.max(priority,3); signals.add("harvesting tool type/tag");
        }
        //? if >=26.1.2
        /*if (stack.is(net.minecraft.tags.ItemTags.SWORDS) || stack.has(DataComponents.WEAPON) && !stack.has(DataComponents.TOOL) || item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem || item instanceof MaceItem || item instanceof ArrowItem || stack.is(ProceduralValuationTags.MELEE_WEAPONS) || stack.is(ProceduralValuationTags.RANGED_WEAPONS)) {*/
        //? if <26.1.2
        if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem || item instanceof MaceItem || item instanceof ArrowItem || stack.is(ProceduralValuationTags.MELEE_WEAPONS) || stack.is(ProceduralValuationTags.RANGED_WEAPONS)) {
            base = Math.max(base, ProceduralValuationSettings.WEAPON_BASE); vote(route,"offense",4); priority = Math.max(priority,3); signals.add("weapon type/tag");
        }
        //? if >=26.1.2
        /*if (stack.is(net.minecraft.tags.ItemTags.HEAD_ARMOR) || stack.is(net.minecraft.tags.ItemTags.CHEST_ARMOR) || stack.is(net.minecraft.tags.ItemTags.LEG_ARMOR) || stack.is(net.minecraft.tags.ItemTags.FOOT_ARMOR) || stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream().anyMatch(modifier -> modifier.attribute().equals(Attributes.ARMOR) || modifier.attribute().equals(Attributes.ARMOR_TOUGHNESS)) || stack.has(DataComponents.BLOCKS_ATTACKS) || stack.is(ProceduralValuationTags.ARMORS)) {*/
        //? if <26.1.2
        if (item instanceof ArmorItem || item instanceof ShieldItem || stack.is(ProceduralValuationTags.ARMORS)) {
            base = Math.max(base, ProceduralValuationSettings.ARMOR_BASE); vote(route,"defense",4); priority = Math.max(priority,3); signals.add("armor type/tag");
        }
        //? if >=26.1.2
        /*if (item instanceof BoatItem || item instanceof MinecartItem || stack.has(DataComponents.GLIDER) || item instanceof EnderpearlItem || item instanceof FireworkRocketItem*/
        //? if <26.1.2
        if (item instanceof BoatItem || item instanceof MinecartItem || item instanceof ElytraItem || item instanceof EnderpearlItem || item instanceof FireworkRocketItem
                || item instanceof BlockItem block && (block.getBlock() instanceof BaseRailBlock || block.getBlock().defaultBlockState().is(ProceduralValuationTags.CLIMBABLE))) {
            base = Math.max(base, ProceduralValuationSettings.TRANSPORT_BASE); vote(route,"mobility",4); priority = Math.max(priority,3); signals.add("transport/climbable type/tag");
        }
        int durability = stack.getOrDefault(DataComponents.MAX_DAMAGE, 0);
        if (durability > 0) { base += Math.round(Math.min(256, Math.sqrt(durability) * 2)); signals.add("native durability component"); }
        var modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (var entry : modifiers.modifiers()) {
            double amount = entry.modifier().amount(); if (!Double.isFinite(amount) || amount <= 0) continue;
            String category = entry.attribute().equals(Attributes.ATTACK_DAMAGE) ? "offense"
                    : entry.attribute().equals(Attributes.ARMOR) || entry.attribute().equals(Attributes.ARMOR_TOUGHNESS) ? "defense"
                    : entry.attribute().equals(Attributes.MOVEMENT_SPEED) ? "mobility"
                    : entry.attribute().equals(Attributes.MAX_HEALTH) ? "vitality" : null;
            if (category != null) { vote(route,category,2); base += Math.round(Math.min(128, 12 * Math.sqrt(amount))); priority = Math.max(priority,3); signals.add("native attribute component: " + category); }
        }
        base = applyRarityPolicy(base, stack.getRarity().name(), signals);
        if (Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) {
            base = Math.min(ProceduralValuationSettings.MAX_VALUE, Math.round(base * ProceduralValuationSettings.INTRINSIC_GLINT_MULTIPLIER));
            signals.add("explicit default glint component; bounded pre-recipe premium, not enchantment or function inference");
        }
        Double override = ValuationGenerationInputs.economicValue(item);
        if (override != null && Double.isFinite(override) && override > 0) { base = Math.min(ProceduralValuationSettings.MAX_VALUE, Math.max(1, Math.round(override))); priority = 5; signals.add("explicit resource-value override"); }
        if (route.isEmpty()) { vote(route,"utility",1); signals.add("generic positive fallback; item name and namespace unused"); }
        //? if >=26.1.2
        /*Item remainder = item.getCraftingRemainder() == null ? null : item.getCraftingRemainder().item().value();*/
        //? if <26.1.2
        Item remainder = item.getCraftingRemainingItem();
        Map<String, Long> retained = new TreeMap<>();
        if (remainder != null) { retained.put(BuiltInRegistries.ITEM.getKey(remainder).toString(), 1L); signals.add("native crafting container retained in material estimate"); }
        //? if >=26.1.2
        /*if (item == Items.ENCHANTED_BOOK || stack.has(DataComponents.STORED_ENCHANTMENTS)) {*/
        //? if <26.1.2
        if (item instanceof EnchantedBookItem || stack.has(DataComponents.STORED_ENCHANTMENTS)) {
            retained.merge(BuiltInRegistries.ITEM.getKey(Items.BOOK).toString(), ProceduralValuationSettings.STORED_ENCHANTMENT_CARRIER_BUDGET, Math::max);
            signals.add("native stored-enchantment carrier plus shared premium ceiling; actual effects and levels determine realized yield");
        }
        return new Intrinsic(new SimpleValuationSolver.Seed(base, priority, route, retained), List.copyOf(signals));
    }
    /** Exact native identities only: loader-added enum constants have no inferred premium or ordering. */
    static long applyRarityPolicy(long base, String declaredRarity, List<String> signals) {
        var policy = RARITY_POLICIES.get(declaredRarity);
        if (policy == null) {
            policy = UNKNOWN_RARITY_POLICY;
            signals.add("native declared rarity " + declaredRarity
                    + "; unrecognized rarity premium semantics; neutral multiplier=1.0 and pre-recipe policy floor=1");
        } else if (!declaredRarity.equals(Rarity.COMMON.name())) {
            signals.add("native declared rarity " + declaredRarity + "; pre-recipe policy floor=" + policy.minimum());
        }
        return Math.min(ProceduralValuationSettings.MAX_VALUE, Math.max(policy.minimum(), Math.round(base * policy.multiplier())));
    }
    private static void vote(Map<String, Double> route, String category, double weight) { route.merge("essence_ascendance:" + category, weight, Double::sum); }
    record Intrinsic(SimpleValuationSolver.Seed seed, List<String> signals) { }

    public static void clear() {
        synchronized (INDEX_LOCK) { indexedServer = null; index = null; cachedResults = null; byId = Map.of();
            if (generationData != null) generationData.close(); generationData = null; ValuationGenerationInputs.clear(); }
    }
    public static void prepareGeneration(com.mistaboom.essence_ascendance.balance.config.BalanceOverrides overrides, GenerationDataSnapshot inputs) {
        synchronized (INDEX_LOCK) { clear(); generationData = inputs; ValuationGenerationInputs.configure(overrides); }
    }
    static GenerationDataSnapshot generationData(MinecraftServer server) {
        synchronized (INDEX_LOCK) {
            if (generationData == null || indexedServer != null && indexedServer != server) { clear(); generationData = GenerationDataSnapshot.capture(server); }
            generationData.requireCurrent(server); return generationData;
        }
    }
    static ProceduralValuationIndex generationIndex(MinecraftServer server) { synchronized (INDEX_LOCK) { return ensureIndex(server); } }
    private static ProceduralValuationIndex ensureIndex(MinecraftServer server) {
        if (index != null && indexedServer == server) { generationData.requireCurrent(server); return index; }
        index = ProceduralValuationIndex.build(server, generationData(server)); indexedServer = server;
        cachedResults = null; byId = Map.of(); BalancePerformance.increment("valuation_index_builds"); return index;
    }
    public static IndexSummary rebuild(MinecraftServer server) { synchronized (INDEX_LOCK) { index = null; cachedResults = null; return summary(server); } }
    public static IndexSummary summary(MinecraftServer server) {
        synchronized (INDEX_LOCK) { var s = ensureIndex(server).summary(); return new IndexSummary(s.recipeCount(), s.skippedRecipeCount(), s.outputItemCount(), s.ingredientLinkCount()); }
    }
    public record IndexSummary(int recipeCount, int skippedRecipeCount, int outputItemCount, int ingredientLinkCount) { }
}
